package com.craftcam.app.camera

import android.content.Context
import android.graphics.*
import android.os.Handler
import android.os.Looper
import androidx.camera.core.CameraEffect
import androidx.camera.effects.OverlayEffect
import androidx.core.util.Consumer
import com.craftcam.app.model.CameraCategory
import com.craftcam.app.model.CameraPreset
import java.text.SimpleDateFormat
import java.util.*

object VideoEffectManager {

    fun createVideoOverlayEffect(
        context: Context,
        preset: CameraPreset,
        recordingStartTimeMs: Long = System.currentTimeMillis()
    ): OverlayEffect {
        val targets = CameraEffect.VIDEO_CAPTURE
        val handler = Handler(Looper.getMainLooper())
        val errorListener = Consumer<Throwable> { throwable ->
            android.util.Log.e("VideoEffectManager", "Error in video overlay effect", throwable)
        }

        val isCamcorder = preset.category == CameraCategory.CAMCORDER
        val is8mm = preset.id.contains("8mm") || preset.id.contains("16mm")
        val isBw = preset.id.contains("bw") || preset.id.contains("black")

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 36f
            typeface = Typeface.MONOSPACE
            setShadowLayer(4f, 2f, 2f, Color.BLACK)
        }

        val recPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.RED
            style = Paint.Style.FILL
        }

        val scanlinePaint = Paint().apply {
            color = Color.argb(30, 0, 0, 0)
            strokeWidth = 2f
        }

        val tintPaint = Paint().apply {
            if (isCamcorder) {
                // Authentic warm VHS tape tone
                color = Color.argb(25, 255, 195, 90)
            } else if (is8mm) {
                // Golden Kodachrome 8mm wash
                color = Color.argb(32, 245, 185, 75)
            } else if (isBw) {
                // Monochrome neutral shading
                color = Color.argb(20, 128, 128, 128)
            } else {
                // Classic 35mm warm analog emulsion tone
                color = Color.argb(20, 255, 215, 150)
            }
        }

        val vigPaint = Paint(Paint.ANTI_ALIAS_FLAG)

        val overlay = OverlayEffect(targets, 0, handler, errorListener)

        overlay.setOnDrawListener { frame ->
            val canvas = frame.overlayCanvas
            val w = canvas.width.toFloat()
            val h = canvas.height.toFloat()

            // 1. Film / Vintage Color Wash
            canvas.drawRect(0f, 0f, w, h, tintPaint)

            // 2. Optical Lens Vignette Shading
            if (vigPaint.shader == null) {
                val cx = w / 2f
                val cy = h / 2f
                val radius = Math.max(w, h) * 0.72f
                vigPaint.shader = RadialGradient(
                    cx, cy, radius,
                    intArrayOf(Color.TRANSPARENT, Color.argb(35, 0, 0, 0), Color.argb(150, 0, 0, 0)),
                    floatArrayOf(0.0f, 0.65f, 1.0f),
                    Shader.TileMode.CLAMP
                )
            }
            canvas.drawRect(0f, 0f, w, h, vigPaint)

            // 3. Category-Specific OSD and Overlays
            if (isCamcorder) {
                val elapsedSec = ((System.currentTimeMillis() - recordingStartTimeMs) / 1000).toInt()
                val minutes = elapsedSec / 60
                val seconds = elapsedSec % 60

                // Blinking REC dot
                val blink = (System.currentTimeMillis() / 500) % 2 == 0L
                if (blink) {
                    canvas.drawCircle(54f, 62f, 12f, recPaint)
                }
                canvas.drawText("REC", 76f, 72f, textPaint)
                canvas.drawText("SP", w - 110f, 72f, textPaint)

                // Running Timecode
                val tc = String.format(Locale.US, "%02d:%02d:%02d", minutes / 60, minutes % 60, seconds)
                canvas.drawText(tc, 54f, h - 54f, textPaint)

                // Vintage Date Stamp
                val dateStr = SimpleDateFormat("MMM. dd yyyy", Locale.US).format(Date()).uppercase()
                canvas.drawText(dateStr, w - 340f, h - 54f, textPaint)

                // CRT Scanlines
                var y = 0f
                while (y < h) {
                    canvas.drawLine(0f, y, w, y, scanlinePaint)
                    y += 6f
                }
            } else if (is8mm) {
                // Amber 7-Segment LED Date Stamp for 8mm / 16mm
                val dateStr = SimpleDateFormat("yy  M  d", Locale.US).format(Date())
                val datePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.rgb(255, 140, 0)
                    textSize = 42f
                    typeface = Typeface.MONOSPACE
                    setShadowLayer(6f, 0f, 0f, Color.RED)
                }
                canvas.drawText(dateStr, w - 260f, h - 50f, datePaint)
            }

            true
        }

        return overlay
    }
}
