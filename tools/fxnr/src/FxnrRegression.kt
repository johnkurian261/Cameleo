package com.craftcam.probe

import android.app.Activity
import android.app.Instrumentation
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.hardware.camera2.CameraCharacteristics as C
import android.hardware.camera2.CameraManager
import android.os.Bundle
import android.os.SystemClock
import com.craftcam.app.camera.UserTuningConfig
import com.craftcam.app.camera.profile.CameraProfileCatalog
import com.craftcam.app.presets.PresetCatalog
import com.craftcam.app.processor.FilmStageObserver
import com.craftcam.app.processor.ImageProcessor
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.math.roundToInt

class FxnrRegression : Instrumentation() {
    private lateinit var out: File
    private val preset = PresetCatalog.allPresets.first { it.id == "analog_fxn_r" }
    private val legacy = CameraProfileCatalog.FXN_R_LEGACY.filmProfile
    private val v2 = CameraProfileCatalog.FXN_R.filmProfile

    private fun stock(film: com.craftcam.app.camera.profile.FilmProfile) =
        if (film == legacy) CameraProfileCatalog.FXN_R_LEGACY else CameraProfileCatalog.FXN_R.copy(filmProfile=film)

    private var live = false
    override fun onCreate(arguments: Bundle?) {
        super.onCreate(arguments)
        live = arguments?.getString("live") == "true"
        start()
    }

    private fun save(image: Bitmap, name: String) {
        val file = File(out, name)
        file.parentFile?.mkdirs()
        file.outputStream().use { check(image.compress(Bitmap.CompressFormat.PNG, 100, it)) }
    }

    private fun render(name: String, src: Bitmap) = runBlocking {
        save(src, "$name/source.png")
        val variants = mapOf("legacy" to legacy, "gentle" to v2.copy(lowerMidtoneStrength=0.015f),
            "v2" to v2, "strong" to v2.copy(lowerMidtoneStrength=0.065f))
        val keys = listOf("conditioned","lut","white-balance","toe","contrast","midtone","saturation-gamma",
            "shadow-tint","shoulder","fade","vignette","grain")
        val chart = name == "chart"
        for ((version, film) in variants) {
            val trace = name in listOf("s21","scene","chart") && version in listOf("legacy","v2")
            val stages = if (trace) keys.associateWith { IntArray(src.width * src.height) } else emptyMap()
            val observer = if (trace) FilmStageObserver { stage, i, r, g, b ->
                fun code(v: Float) = (v.coerceIn(0f,1f)*255).roundToInt()
                stages.getValue(stage)[i] = Color.rgb(code(r),code(g),code(b))
            } else null
            val work = src.copy(Bitmap.Config.ARGB_8888,true)
            val result = ImageProcessor.applyPresetEffects(targetContext, work, preset,
                cameraId="0", stock=stock(film), grainSeed=7321L, observer=observer)
            save(result,"$name/$version.png")
            File(out,"$name/$version.jpg").outputStream().use {
                check(result.compress(Bitmap.CompressFormat.JPEG,93,it))
            }
            result.recycle()
            for ((stage, data) in stages) {
                val snap = Bitmap.createBitmap(data,src.width,src.height,Bitmap.Config.ARGB_8888)
                save(snap,"$name/$version/$stage.png")
                snap.recycle()
            }
            if (chart) {
                val tuning = UserTuningConfig(debugStageGrain=false,debugStageOptics=false,
                    debugStageHalation=false,debugStageVignette=false)
                for (camera in listOf("0","1")) {
                    val bare = ImageProcessor.applyPresetEffects(targetContext,src.copy(Bitmap.Config.ARGB_8888,true),
                        preset,tuning,cameraId=camera,stock=stock(film),grainSeed=7321L)
                    save(bare,"chart/$version-flat-$camera.png")
                    bare.recycle()
                }
            }
        }
    }

    private fun benchmark() = runBlocking {
        val bitmap = Bitmap.createBitmap(4032,3024,Bitmap.Config.ARGB_8888)
        val src = BitmapFactory.decodeFile(File(out,"scene/source.png").path)
        val timings = File(out,"benchmark.csv")
        timings.writeText("iteration,profile,milliseconds,process_hwm_kb\n")
        for (iteration in 0..4) {
            val profiles = if (iteration % 2 == 0) listOf(legacy,v2) else listOf(v2,legacy)
            for (film in profiles) {
                android.graphics.Canvas(bitmap).drawBitmap(src,null,android.graphics.Rect(0,0,4032,3024),null)
                val start = SystemClock.elapsedRealtime()
                ImageProcessor.applyPresetEffects(targetContext,bitmap,preset,cameraId="0",stock=stock(film), grainSeed=7321L)
                val elapsed = SystemClock.elapsedRealtime()-start
                val peak = File("/proc/self/status").readLines().first { it.startsWith("VmHWM:") }.filter { it.isDigit() }
                timings.appendText("$iteration,${film.id},$elapsed,$peak\n")
            }
        }
        bitmap.recycle()
        src.recycle()
    }

    override fun onStart() {
        try {
            out = File(targetContext.getExternalFilesDir(null),"fxnr-results").apply { mkdirs() }
            if (live) {
                checkLenses(out)
                finish(Activity.RESULT_OK, Bundle())
                return
            }
            val cameras = targetContext.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            File(out,"capabilities.txt").printWriter().use { writer ->
                for (id in cameras.cameraIdList) {
                    val c = cameras.getCameraCharacteristics(id)
                    writer.println("id=$id facing=${c[C.LENS_FACING]} level=${c[C.INFO_SUPPORTED_HARDWARE_LEVEL]} caps=${c[C.REQUEST_AVAILABLE_CAPABILITIES]?.contentToString()} exposure=${c[C.SENSOR_INFO_EXPOSURE_TIME_RANGE]} ISO=${c[C.SENSOR_INFO_SENSITIVITY_RANGE]} AWB=${c[C.CONTROL_AWB_AVAILABLE_MODES]?.contentToString()} NR=${c[C.NOISE_REDUCTION_AVAILABLE_NOISE_REDUCTION_MODES]?.contentToString()} edge=${c[C.EDGE_AVAILABLE_EDGE_MODES]?.contentToString()} formats=${c[C.SCALER_STREAM_CONFIGURATION_MAP]?.outputFormats?.contentToString()}")
                }
            }
            val inputs = File(targetContext.getExternalFilesDir(null),"fxnr-inputs")
            inputs.mkdirs()
            for (name in context.assets.list("").orEmpty().filter { it.endsWith(".png") }) {
                context.assets.open(name).use { input -> File(inputs,name).outputStream().use { input.copyTo(it) } }
            }
            val files = inputs.listFiles().orEmpty().filter { it.extension=="png" }.sortedBy { it.name }
            check(files.isNotEmpty()) { "Missing fixtures in $inputs" }
            for (f in files) {
                val src = BitmapFactory.decodeFile(f.path)
                render(f.nameWithoutExtension,src)
                src.recycle()
            }
            benchmark()
            val chart = BitmapFactory.decodeFile(File(inputs,"chart.png").path)
            renderPreview(targetContext,chart) { image, front -> save(image,"chart/preview-$front.png") }
            chart.recycle()
            finish(Activity.RESULT_OK, Bundle())
        } catch (e: Throwable) {
            finish(Activity.RESULT_CANCELED,Bundle().apply { putString("stream",android.util.Log.getStackTraceString(e)) })
        }
    }
}

