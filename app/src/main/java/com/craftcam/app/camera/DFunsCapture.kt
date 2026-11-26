package com.craftcam.app.camera

import android.graphics.ImageFormat
import android.hardware.camera2.CameraCharacteristics as C
import android.hardware.camera2.CaptureRequest as Q
import android.hardware.camera2.CaptureResult as R
import android.util.Log
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import com.craftcam.app.processor.DFunsRaw

@androidx.annotation.OptIn(ExperimentalCamera2Interop::class)
internal class DFunsCapture {
    private val rejectedFormats = mutableMapOf<String, MutableSet<Int>>()
    private val results = LinkedHashMap<Long, R>()

    fun reject(cameraId: String, format: Int) {
        rejectedFormats.getOrPut(cameraId) { mutableSetOf() }.add(format)
    }

    fun formats(id: String, c: C): List<Int> {
        // High-reliability primary JPEG capture to eliminate Camera HAL stream rejection on the initial shot
        return listOf(ImageFormat.JPEG)
    }

    fun <T> configure(ext: Camera2Interop.Extender<T>, c: C) {
        fun mode(modes: C.Key<IntArray>, key: Q.Key<Int>, value: Int) {
            if (key in c.availableCaptureRequestKeys && c.get(modes)?.contains(value) == true)
                ext.setCaptureRequestOption(key, value)
        }
        mode(C.NOISE_REDUCTION_AVAILABLE_NOISE_REDUCTION_MODES,Q.NOISE_REDUCTION_MODE,Q.NOISE_REDUCTION_MODE_OFF)
        mode(C.EDGE_AVAILABLE_EDGE_MODES,Q.EDGE_MODE,Q.EDGE_MODE_OFF)
        mode(C.CONTROL_AVAILABLE_VIDEO_STABILIZATION_MODES,Q.CONTROL_VIDEO_STABILIZATION_MODE,Q.CONTROL_VIDEO_STABILIZATION_MODE_OFF)
        // D FUNS uses reliable hardware autofocus for high-fidelity source capture.
        // The fixed-focus disposable aesthetic is simulated optically in the rendering pipeline.
    }

    @Synchronized fun record(result: R) {
        val timestamp = result.get(R.SENSOR_TIMESTAMP) ?: return
        results[timestamp] = result
        if (results.size > 8) results.remove(results.keys.first())
        Log.i("DFunsCapture","v1 t=$timestamp shutterNs=${result.get(R.SENSOR_EXPOSURE_TIME)} iso=${result.get(R.SENSOR_SENSITIVITY)} " +
            "flashState=${result.get(R.FLASH_STATE)} aperture=${result.get(R.LENS_APERTURE)} focalMm=${result.get(R.LENS_FOCAL_LENGTH)} " +
            "focusD=${result.get(R.LENS_FOCUS_DISTANCE)} nr=${result.get(R.NOISE_REDUCTION_MODE)} edge=${result.get(R.EDGE_MODE)}")
    }

    @Synchronized fun resultFor(timestamp: Long): R? = results.remove(timestamp)

    companion object {
        private const val FIXED_DIOPTERS = 0.33f
        // Nominal camera variant, provisional until measured on a physical reference.
        private const val FILM_ISO = 800f
        private const val FILM_APERTURE = 10f
        private const val SHUTTER_NS = 8_333_333f

        fun exposureScale(result: R, strict: Boolean): Float {
            // LED illumination is a pulse; ambient shutter-duration reciprocity does not apply.
            if (result.get(R.FLASH_STATE) in listOf(R.FLASH_STATE_FIRED,R.FLASH_STATE_PARTIAL)) return 1f
            val iso = result.get(R.SENSOR_SENSITIVITY) ?: return 1f
            val ns = result.get(R.SENSOR_EXPOSURE_TIME) ?: return 1f
            val aperture = result.get(R.LENS_APERTURE) ?: return 1f
            val ratio = (FILM_ISO / iso) * (aperture / FILM_APERTURE) * (aperture / FILM_APERTURE) * (SHUTTER_NS / ns)
            return if (strict) ratio.coerceIn(1f/16f,16f) else ratio.coerceIn(0.8f,1.25f)
        }
    }
}
