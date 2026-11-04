package com.craftcam.app.processor

import android.content.Context
import android.graphics.*
import com.craftcam.app.model.DateStampConfig
import com.craftcam.app.model.DateStampStyle
import java.text.SimpleDateFormat
import java.util.*

object DateStampRenderer {

    fun applyDateStamp(
        bitmap: Bitmap,
        config: DateStampConfig,
        customYearText: String? = null,
        context: Context? = null
    ): Bitmap {
        if (!config.enabled) return bitmap

        val mutableBitmap = if (bitmap.isMutable) bitmap else bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(mutableBitmap)

        val calendar = Calendar.getInstance()
        if (config.customYearOffset != 0) {
            calendar.add(Calendar.YEAR, config.customYearOffset)
        }

        val date = calendar.time
        val (rawText, textColor, glowColor) = when (config.style) {
            DateStampStyle.SEVEN_SEGMENT_ORANGE -> {
                val sdf = SimpleDateFormat("''yy  M d", Locale.US)
                Triple(sdf.format(date), Color.rgb(255, 120, 10), Color.argb(160, 200, 50, 0))
            }
            DateStampStyle.DIGITAL_LCD_GREEN -> {
                val sdf = SimpleDateFormat("MMM.dd yyyy HH:mm", Locale.US)
                Triple(sdf.format(date).uppercase(Locale.US), Color.rgb(57, 255, 20), Color.argb(140, 20, 160, 10))
            }
            DateStampStyle.MONOSPACE_DOT_MATRIX -> {
                val sdf = SimpleDateFormat("yyyy.MM.dd", Locale.US)
                Triple(sdf.format(date), Color.rgb(255, 180, 20), Color.argb(150, 180, 80, 0))
            }
            DateStampStyle.MINIMAL_WHITE -> {
                val sdf = SimpleDateFormat("yyyy / MM / dd", Locale.US)
                Triple(sdf.format(date), Color.rgb(240, 240, 240), Color.argb(120, 20, 20, 20))
            }
        }

        // Apply custom year override if provided
        val text = if (!customYearText.isNullOrBlank()) {
            val yr = if (customYearText.startsWith("'")) customYearText else "'$customYearText"
            val m = SimpleDateFormat("M d", Locale.US).format(date)
            "$yr  $m"
        } else {
            rawText
        }

        val width = mutableBitmap.width.toFloat()
        val height = mutableBitmap.height.toFloat()

        // Size proportional to image dimensions
        val textSize = height * 0.034f
        val marginX = width * 0.05f
        val marginY = height * 0.05f

        val vcrTypeface = context?.let {
            try {
                Typeface.createFromAsset(it.assets, "fonts/VCR_OSD_MONO.ttf")
            } catch (e: Exception) {
                null
            }
        } ?: Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.textSize = textSize
            this.typeface = vcrTypeface
            textAlign = Paint.Align.RIGHT
        }

        // 1. Draw glowing drop-shadow / soft photochemical bleed
        paint.color = glowColor
        paint.maskFilter = BlurMaskFilter(textSize * 0.22f, BlurMaskFilter.Blur.NORMAL)
        canvas.drawText(text, width - marginX, height - marginY, paint)

        // 2. Draw sharp primary text
        paint.maskFilter = null
        paint.color = textColor
        canvas.drawText(text, width - marginX, height - marginY, paint)

        return mutableBitmap
    }
}

