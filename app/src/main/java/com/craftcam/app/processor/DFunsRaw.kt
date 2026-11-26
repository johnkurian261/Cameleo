package com.craftcam.app.processor

import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CaptureResult
import androidx.camera.core.ImageProxy
import java.nio.ByteOrder
import kotlin.math.pow
import kotlin.math.roundToInt

/** Single-frame Bayer development for the D Funs capture route. */
internal object DFunsRaw {
    val formats = listOf(ImageFormat.RAW_SENSOR, ImageFormat.RAW10, ImageFormat.RAW12)
    private val encoding = IntArray(8193) { i ->
        val linear = i / 8192f
        val srgb = if (linear <= 0.0031308f) 12.92f * linear else 1.055f * linear.pow(1f / 2.4f) - 0.055f
        (255f * srgb).roundToInt().coerceIn(0, 255)
    }

    fun decode(image: ImageProxy, chars: CameraCharacteristics, result: CaptureResult, exposureScale: Float): Bitmap {
        val w = image.width
        val h = image.height
        require(w >= 2 && h >= 2)
        val plane = image.planes.single()
        val bytes = plane.buffer.duplicate().order(ByteOrder.nativeOrder())
        val origin = bytes.position()
        val format = image.format
        val pixelStride = plane.pixelStride
        val cfa = chars.get(CameraCharacteristics.SENSOR_INFO_COLOR_FILTER_ARRANGEMENT)
        // Values index the R, G-even, G-odd, B white balance gains.
        val sites = when (cfa) {
            CameraCharacteristics.SENSOR_INFO_COLOR_FILTER_ARRANGEMENT_RGGB -> intArrayOf(0, 1, 2, 3)
            CameraCharacteristics.SENSOR_INFO_COLOR_FILTER_ARRANGEMENT_GRBG -> intArrayOf(1, 0, 3, 2)
            CameraCharacteristics.SENSOR_INFO_COLOR_FILTER_ARRANGEMENT_GBRG -> intArrayOf(1, 3, 0, 2)
            CameraCharacteristics.SENSOR_INFO_COLOR_FILTER_ARRANGEMENT_BGGR -> intArrayOf(3, 1, 2, 0)
            else -> error("Unsupported Bayer arrangement: $cfa")
        }
        val wb = requireNotNull(result.get(CaptureResult.COLOR_CORRECTION_GAINS))
        val gains = floatArrayOf(wb.red, wb.greenEven, wb.greenOdd, wb.blue)
        val transform = requireNotNull(result.get(CaptureResult.COLOR_CORRECTION_TRANSFORM))
        val matrix = FloatArray(9) { transform.getElement(it % 3, it / 3).toFloat() }
        val staticBlack = requireNotNull(chars.get(CameraCharacteristics.SENSOR_BLACK_LEVEL_PATTERN))
        val dynamicBlack = result.get(CaptureResult.SENSOR_DYNAMIC_BLACK_LEVEL)
        val white = result.get(CaptureResult.SENSOR_DYNAMIC_WHITE_LEVEL)
            ?: requireNotNull(chars.get(CameraCharacteristics.SENSOR_INFO_WHITE_LEVEL))
        // Dynamic black levels use CFA layout order, like the static BlackLevelPattern.
        val black = FloatArray(4) { dynamicBlack?.get(it) ?: staticBlack.getOffsetForIndex(it % 2, it / 2).toFloat() }
        val scale = FloatArray(4) { gains[sites[it]] * exposureScale / (white - black[it]).coerceAtLeast(1f) }
        val rows = Array(3) { FloatArray(w) }
        fun loadRow(y: Int) {
            val row = rows[y % 3]
            val offset = origin + y * plane.rowStride
            for (x in 0 until w) {
                val site = (y and 1) * 2 + (x and 1)
                val value = when (format) {
                    ImageFormat.RAW10 -> {
                        val group = offset + (x / 4) * 5
                        ((bytes[group + x % 4].toInt() and 255) shl 2) or
                            ((bytes[group + 4].toInt() ushr (2 * (x % 4))) and 3)
                    }
                    ImageFormat.RAW12 -> {
                        val group = offset + (x / 2) * 3
                        ((bytes[group + x % 2].toInt() and 255) shl 4) or
                            ((bytes[group + 2].toInt() ushr (4 * (x % 2))) and 15)
                    }
                    else -> bytes.getShort(offset + x * pixelStride).toInt() and 65535
                }
                row[x] = ((value - black[site]) * scale[site]).coerceAtLeast(0f)
            }
        }

        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val output = IntArray(w)
        loadRow(0)
        loadRow(1)
        for (y in 0 until h) {
            if (y > 0 && y + 1 < h) loadRow(y + 1)
            val mid = rows[y % 3]
            val up = rows[(if (y == 0) 1 else y - 1) % 3]
            val down = rows[(if (y == h - 1) h - 2 else y + 1) % 3]
            for (x in 0 until w) {
                val left = if (x == 0) 1 else x - 1
                val right = if (x == w - 1) w - 2 else x + 1
                val site = (y and 1) * 2 + (x and 1)
                val horizontal = (mid[left] + mid[right]) * 0.5f
                val vertical = (up[x] + down[x]) * 0.5f
                val r: Float
                val g: Float
                val b: Float
                if (sites[site] == 0 || sites[site] == 3) {
                    val diagonal = (up[left] + up[right] + down[left] + down[right]) * 0.25f
                    r = if (sites[site] == 0) mid[x] else diagonal
                    g = (horizontal + vertical) * 0.5f
                    b = if (sites[site] == 3) mid[x] else diagonal
                } else {
                    val redHorizontal = sites[site xor 1] == 0
                    r = if (redHorizontal) horizontal else vertical
                    g = mid[x]
                    b = if (redHorizontal) vertical else horizontal
                }
                val rr = matrix[0]*r + matrix[1]*g + matrix[2]*b
                val gg = matrix[3]*r + matrix[4]*g + matrix[5]*b
                val bb = matrix[6]*r + matrix[7]*g + matrix[8]*b
                val peak = maxOf(rr, gg, bb)
                val shoulder = if (peak > .8f) (.8f + .2f*(peak-.8f)/(peak-.6f))/peak else 1f
                fun code(v: Float) = encoding[(8192f*v*shoulder).roundToInt().coerceIn(0,8192)]
                output[x] = (255 shl 24) or (code(rr) shl 16) or (code(gg) shl 8) or code(bb)
            }
            bitmap.setPixels(output,0,w,0,y,w,1)
        }
        return bitmap
    }
}
