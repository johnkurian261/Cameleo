package com.craftcam.probe

import android.app.Instrumentation
import android.content.Intent
import android.graphics.Bitmap
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import com.craftcam.app.MainActivity
import com.craftcam.app.presets.PresetCatalog
import com.craftcam.app.processor.ImageProcessor
import kotlinx.coroutines.runBlocking
import java.io.File
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

/** Physical ISP smoke test. Keep front and rear images in memory only. */
internal fun Instrumentation.checkLenses(out: File) {
    val ctx = targetContext
    val activity = startActivitySync(Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
    waitForIdleSync()
    val provider = ProcessCameraProvider.getInstance(ctx).get(15,TimeUnit.SECONDS)
    val executor = ContextCompat.getMainExecutor(ctx)
    val preset = PresetCatalog.allPresets.first { it.id=="analog_fxn_r" }
    try {
        File(out,"lens-smoke.txt").printWriter().use { writer ->
            for ((name, selector) in listOf("rear" to CameraSelector.DEFAULT_BACK_CAMERA, "front" to CameraSelector.DEFAULT_FRONT_CAMERA)) {
                if (!provider.hasCamera(selector)) { writer.println("$name unavailable"); continue }
                val shot = ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).setJpegQuality(95).build()
                runOnMainSync {
                    provider.unbindAll()
                    provider.bindToLifecycle(activity, selector, shot)
                }
                val result = CompletableFuture<ImageProxy>()
                shot.takePicture(executor,object : ImageCapture.OnImageCapturedCallback() {
                    override fun onCaptureSuccess(image: ImageProxy) { result.complete(image) }
                    override fun onError(exception: ImageCaptureException) { result.completeExceptionally(exception) }
                })
                val image = result.get(20,TimeUnit.SECONDS)
                val raw = try { image.toBitmap() } finally { image.close() }
                val work = if (raw.isMutable) raw else raw.copy(Bitmap.Config.ARGB_8888,true).also { raw.recycle() }
                try {
                    runBlocking { ImageProcessor.applyPresetEffects(ctx,work,preset) }
                    writer.println("$name FXNR V2 rendered ${work.width} x ${work.height}")
                    writer.flush()
                } finally { work.recycle() }
            }
        }
    } finally {
        runOnMainSync { provider.unbindAll(); activity.finish() }
    }
}
