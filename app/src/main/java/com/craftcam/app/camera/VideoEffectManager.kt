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
        val is8mm = preset.id.contains("8mm") || preset.id.contains("analog_video") || preset.id.contains("16mm")
        val isCcd = preset.category == CameraCategory.CCD_DIGITAL
        val isDisposable = preset.category == CameraCategory.DISPOSABLE
        val isBw = preset.id.contains("bw") || preset.id.contains("black")

        val vcrTypeface = try {
            Typeface.createFromAsset(context.assets, "fonts/VCR_OSD_MONO.ttf")
        } catch (e: Exception) {
            Typeface.MONOSPACE
        }

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (isCamcorder) Color.rgb(80, 255, 120) else Color.WHITE
            textSize = 38f
            typeface = vcrTypeface
            setShadowLayer(4f, 2f, 2f, Color.BLACK)
        }

        val datePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = when {
                isCamcorder -> Color.rgb(80, 255, 120) // Glowing green OSD
                is8mm       -> Color.rgb(255, 140, 20)  // Amber 7-segment
                isCcd       -> Color.rgb(255, 180, 30)  // Digicam orange
                else        -> Color.rgb(255, 130, 20)  // Classic film date stamp
            }
            textSize = 40f
            typeface = vcrTypeface
            setShadowLayer(6f, 0f, 0f, Color.argb(180, 200, 40, 0))
        }

        val recPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.RED
            style = Paint.Style.FILL
        }

        val scanlinePaint = Paint().apply {
            color = Color.argb(35, 0, 0, 0)
            strokeWidth = 2.5f
        }

        // Category-calibrated bold photographic vintage wash
        val tintPaint = Paint().apply {
            color = when {
                isCamcorder  -> Color.argb(68, 255, 205, 95)  // Warm magnetic VHS tape
                is8mm        -> Color.argb(75, 250, 185, 60)  // Golden Kodachrome 8mm
                isCcd        -> Color.argb(50, 30, 100, 180)  // Cool metallic CCD
                isDisposable -> Color.argb(65, 255, 175, 75)  // Flash single-use amber
                isBw         -> Color.argb(65, 20, 20, 25)    // Deep contrast B&W
                else         -> Color.argb(55, 255, 215, 130) // Warm 35mm Portra gold
            }
        }

        val vigPaint = Paint(Paint.ANTI_ALIAS_FLAG)

        val overlay = OverlayEffect(targets, 0, handler, errorListener)

        overlay.setOnDrawListener { frame ->
            val canvas = frame.overlayCanvas
            val w = canvas.width.toFloat()
            val h = canvas.height.toFloat()

            // 1. Film / Vintage Color Emulsion Wash
            canvas.drawRect(0f, 0f, w, h, tintPaint)

            // 2. Optical Lens Vignette Shading
            if (vigPaint.shader == null) {
                val cx = w / 2f
                val cy = h / 2f
                val radius = Math.max(w, h) * (if (isDisposable || is8mm) 0.65f else 0.75f)
                val edgeAlpha = if (isDisposable || is8mm) 180 else 135
                vigPaint.shader = RadialGradient(
                    cx, cy, radius,
                    intArrayOf(Color.TRANSPARENT, Color.argb(40, 0, 0, 0), Color.argb(edgeAlpha, 0, 0, 0)),
                    floatArrayOf(0.0f, 0.60f, 1.0f),
                    Shader.TileMode.CLAMP
                )
            }
            canvas.drawRect(0f, 0f, w, h, vigPaint)

            // 3. Category-Specific Vintage On-Screen Display (OSD)
            val elapsedSec = ((System.currentTimeMillis() - recordingStartTimeMs) / 1000).toInt()
            val minutes = elapsedSec / 60
            val seconds = elapsedSec % 60

            if (isCamcorder) {
                // Blinking REC dot
                val blink = (System.currentTimeMillis() / 500) % 2 == 0L
                if (blink) {
                    canvas.drawCircle(60f, 65f, 14f, recPaint)
                }
                canvas.drawText("REC", 86f, 78f, textPaint)
                canvas.drawText("SP", w - 120f, 78f, textPaint)
                canvas.drawText("[■■■]", w - 240f, 78f, textPaint)

                // Running Timecode
                val tc = String.format(Locale.US, "%02d:%02d:%02d", minutes / 60, minutes % 60, seconds)
                canvas.drawText(tc, 60f, h - 60f, textPaint)

                // Vintage Date Stamp
                val dateStr = SimpleDateFormat("MMM. dd yyyy", Locale.US).format(Date()).uppercase()
                canvas.drawText(dateStr, w - 380f, h - 60f, datePaint)

                // CRT Scanlines
                var y = 0f
                while (y < h) {
                    canvas.drawLine(0f, y, w, y, scanlinePaint)
                    y += 6f
                }
            } else if (is8mm) {
                // Amber 7-Segment LED Date Stamp for 8mm / 16mm
                val cal = Calendar.getInstance()
                val yy = cal.get(Calendar.YEAR) % 100 - 30
                val mm = cal.get(Calendar.MONTH) + 1
                val dd = cal.get(Calendar.DAY_OF_MONTH)
                val dateStr = String.format(Locale.US, "'%02d  %2d  %2d", yy, mm, dd)
                canvas.drawText(dateStr, w - 320f, h - 55f, datePaint)
            } else if (preset.dateStamp?.enabled == true) {
                // Photochemically imprinted orange date stamp for Analog / CCD / Disposable
                val cal = Calendar.getInstance()
                val offset = preset.dateStamp?.customYearOffset ?: 0
                val yy = (cal.get(Calendar.YEAR) + offset) % 100
                val mm = cal.get(Calendar.MONTH) + 1
                val dd = cal.get(Calendar.DAY_OF_MONTH)
                val dateStr = String.format(Locale.US, "'%02d  %2d  %2d", yy, mm, dd)
                canvas.drawText(dateStr, w - 320f, h - 55f, datePaint)
            }

            true
        }

        return overlay
    }
}
