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
        isRecordingProvider: () -> Boolean = { false },
        recordingDurationSecProvider: () -> Int = { 0 }
    ): OverlayEffect {
        // Target BOTH live preview viewfinder and recorded video stream
        val targets = CameraEffect.PREVIEW or CameraEffect.VIDEO_CAPTURE
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

        val osdPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
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
            color = Color.argb(28, 0, 0, 0)
            strokeWidth = 2.0f
        }

        val gatePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(10, 10, 12)
            style = Paint.Style.FILL
        }

        // Subtle non-destructive photographic color wash (preserves contrast & deep blacks)
        val tintPaint = Paint().apply {
            color = when {
                isCamcorder  -> Color.argb(28, 255, 215, 105) // Warm analog NTSC tape
                is8mm        -> Color.argb(32, 250, 195, 80)  // Golden Kodachrome 8mm
                isCcd        -> Color.argb(22, 50, 110, 190)  // Cool metallic CCD
                isDisposable -> Color.argb(30, 255, 185, 90)  // Warm flash disposable
                isBw         -> Color.argb(30, 20, 20, 25)    // Contrast B&W shading
                else         -> Color.argb(25, 255, 220, 140) // Warm 35mm Portra gold
            }
        }

        val vigPaint = Paint(Paint.ANTI_ALIAS_FLAG)

        val overlay = OverlayEffect(targets, 0, handler, errorListener)

        overlay.setOnDrawListener { frame ->
            val canvas = frame.overlayCanvas
            val w = canvas.width.toFloat()
            val h = canvas.height.toFloat()

            // 1. Film / Vintage Color Tone Wash
            canvas.drawRect(0f, 0f, w, h, tintPaint)

            // 2. Optical Lens Vignette Shading
            if (vigPaint.shader == null) {
                val cx = w / 2f
                val cy = h / 2f
                val radius = Math.max(w, h) * (if (isDisposable || is8mm) 0.65f else 0.72f)
                val edgeAlpha = if (isDisposable || is8mm) 190 else 145
                vigPaint.shader = RadialGradient(
                    cx, cy, radius,
                    intArrayOf(Color.TRANSPARENT, Color.argb(40, 0, 0, 0), Color.argb(edgeAlpha, 0, 0, 0)),
                    floatArrayOf(0.0f, 0.55f, 1.0f),
                    Shader.TileMode.CLAMP
                )
            }
            canvas.drawRect(0f, 0f, w, h, vigPaint)

            // 3. 8mm / 16mm Projector Rounded Film Gate
            if (is8mm) {
                val cornerRadius = w * 0.065f
                val gatePath = Path().apply {
                    addRoundRect(RectF(0f, 0f, w, h), cornerRadius, cornerRadius, Path.Direction.CW)
                }
                val outerPath = Path().apply {
                    addRect(0f, 0f, w, h, Path.Direction.CW)
                    op(gatePath, Path.Op.DIFFERENCE)
                }
                canvas.drawPath(outerPath, gatePaint)
            }

            // 4. Live Recording Status & Timecode
            val isRecording = isRecordingProvider()
            val elapsedSec = recordingDurationSecProvider()
            val minutes = elapsedSec / 60
            val seconds = elapsedSec % 60

            if (isCamcorder) {
                // Blinking REC dot when recording, or STBY when standby
                if (isRecording) {
                    val blink = (System.currentTimeMillis() / 500) % 2 == 0L
                    if (blink) {
                        canvas.drawCircle(60f, 65f, 13f, recPaint)
                    }
                    canvas.drawText("REC", 86f, 78f, osdPaint)
                } else {
                    canvas.drawText("STBY", 60f, 78f, osdPaint)
                }

                canvas.drawText("SP", w - 120f, 78f, osdPaint)
                canvas.drawText("[■■■]", w - 240f, 78f, osdPaint)

                // Running Timecode
                val tc = String.format(Locale.US, "%02d:%02d:%02d", minutes / 60, minutes % 60, seconds)
                canvas.drawText(tc, 60f, h - 60f, osdPaint)

                // Vintage Date Stamp
                val dateStr = SimpleDateFormat("MMM. dd yyyy", Locale.US).format(Date()).uppercase(Locale.US)
                canvas.drawText(dateStr, w - 380f, h - 60f, datePaint)

                // CRT Scanlines
                var y = 0f
                while (y < h) {
                    canvas.drawLine(0f, y, w, y, scanlinePaint)
                    y += 5f
                }
            } else if (is8mm) {
                // Amber 7-Segment LED Date Stamp for 8mm / 16mm
                val cal = Calendar.getInstance()
                val yy = (cal.get(Calendar.YEAR) - 30) % 100
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
