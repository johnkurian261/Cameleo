package com.craftcam.app.processor

import android.graphics.*
import com.craftcam.app.model.BorderConfig
import com.craftcam.app.model.BorderStyle

object BorderRenderer {

    fun applyBorder(sourceBitmap: Bitmap, config: BorderConfig): Bitmap {
        if (config.style == BorderStyle.NONE) return sourceBitmap

        val srcW = sourceBitmap.width
        val srcH = sourceBitmap.height

        return when (config.style) {
            BorderStyle.POLAROID_WHITE_CHIN -> {
                // Classic Polaroid format: Equal thin top & sides, wide bottom chin
                val sideMargin = (srcW * 0.06f).toInt()
                val topMargin = sideMargin
                val bottomMargin = (srcH * 0.22f).toInt()

                val targetW = srcW + sideMargin * 2
                val targetH = srcH + topMargin + bottomMargin

                val output = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(output)

                // Paper white textured background
                canvas.drawColor(Color.rgb(248, 246, 242))

                // Subtle inner shadow for photograph depression
                val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.argb(40, 0, 0, 0)
                }
                canvas.drawRect(
                    (sideMargin - 2).toFloat(),
                    (topMargin - 2).toFloat(),
                    (sideMargin + srcW + 2).toFloat(),
                    (topMargin + srcH + 2).toFloat(),
                    shadowPaint
                )

                // Draw source image
                canvas.drawBitmap(sourceBitmap, sideMargin.toFloat(), topMargin.toFloat(), null)

                output
            }

            BorderStyle.SQUARE_INSTANT_WHITE -> {
                val sideMargin = (srcW * 0.08f).toInt()
                val bottomMargin = (srcH * 0.18f).toInt()

                val targetW = srcW + sideMargin * 2
                val targetH = srcH + sideMargin + bottomMargin

                val output = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(output)
                canvas.drawColor(Color.rgb(250, 248, 245))

                canvas.drawBitmap(sourceBitmap, sideMargin.toFloat(), sideMargin.toFloat(), null)
                output
            }

            BorderStyle.CLASSIC_35MM_BLACK -> {
                val margin = (srcW * 0.04f).toInt()
                val targetW = srcW + margin * 2
                val targetH = srcH + margin * 2

                val output = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(output)
                canvas.drawColor(Color.rgb(12, 12, 14))

                // Film rebate markings
                val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.rgb(220, 160, 40)
                    textSize = margin * 0.45f
                    typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
                }
                canvas.drawText("▶ 24A", margin.toFloat(), margin * 0.75f, textPaint)
                canvas.drawText("SAFETY FILM", targetW * 0.45f, margin * 0.75f, textPaint)

                canvas.drawBitmap(sourceBitmap, margin.toFloat(), margin.toFloat(), null)
                output
            }

            BorderStyle.ROUNDED_8MM -> {
                // Rounded corner film gate
                val output = Bitmap.createBitmap(srcW, srcH, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(output)
                val paint = Paint(Paint.ANTI_ALIAS_FLAG)

                val rect = RectF(0f, 0f, srcW.toFloat(), srcH.toFloat())
                val cornerRadius = srcW * 0.08f

                // Mask with rounded rect
                val path = Path().apply {
                    addRoundRect(rect, cornerRadius, cornerRadius, Path.Direction.CW)
                }
                canvas.clipPath(path)
                canvas.drawBitmap(sourceBitmap, 0f, 0f, paint)

                output
            }

            BorderStyle.CAMCORDER_FRAME -> {
                val output = sourceBitmap.copy(Bitmap.Config.ARGB_8888, true)
                val canvas = Canvas(output)
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.RED
                    style = Paint.Style.FILL
                }
                // Draw small REC dot in top right
                val radius = srcW * 0.015f
                canvas.drawCircle(srcW * 0.90f, srcH * 0.08f, radius, paint)

                val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.WHITE
                    textSize = srcH * 0.035f
                    typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
                }
                canvas.drawText("REC", srcW * 0.93f, srcH * 0.09f, textPaint)
                output
            }

            BorderStyle.NONE -> sourceBitmap
        }
    }
}

