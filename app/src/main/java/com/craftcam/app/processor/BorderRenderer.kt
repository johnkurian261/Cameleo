package com.craftcam.app.processor

import android.content.Context
import android.graphics.*
import com.craftcam.app.model.BorderConfig
import com.craftcam.app.model.BorderStyle

object BorderRenderer {

    fun applyBorder(sourceBitmap: Bitmap, config: BorderConfig, context: Context? = null): Bitmap {
        if (config.style == BorderStyle.NONE) return sourceBitmap

        val srcW = sourceBitmap.width
        val srcH = sourceBitmap.height

        return when (config.style) {
            BorderStyle.POLAROID_WHITE_CHIN -> {
                // Check if authentic Dazz instant frame asset exists
                val borderTex = context?.let { loadAssetBitmap(it, "vinst_border1.png") }
                if (borderTex != null) {
                    val sideMargin = (srcW * 0.07f).toInt()
                    val topMargin = (srcH * 0.07f).toInt()
                    val bottomMargin = (srcH * 0.24f).toInt()

                    val targetW = srcW + sideMargin * 2
                    val targetH = srcH + topMargin + bottomMargin

                    val output = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
                    val canvas = Canvas(output)
                    // Draw photograph in center window
                    canvas.drawBitmap(sourceBitmap, sideMargin.toFloat(), topMargin.toFloat(), null)
                    // Draw authentic Dazz Polaroid frame border on top
                    val scaledBorder = Bitmap.createScaledBitmap(borderTex, targetW, targetH, true)
                    canvas.drawBitmap(scaledBorder, 0f, 0f, null)
                    output
                } else {
                    // Procedural fallback
                    val sideMargin = (srcW * 0.06f).toInt()
                    val topMargin = sideMargin
                    val bottomMargin = (srcH * 0.22f).toInt()

                    val targetW = srcW + sideMargin * 2
                    val targetH = srcH + topMargin + bottomMargin

                    val output = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
                    val canvas = Canvas(output)
                    canvas.drawColor(Color.rgb(248, 246, 242))

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
                    canvas.drawBitmap(sourceBitmap, sideMargin.toFloat(), topMargin.toFloat(), null)
                    output
                }
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
                // Rounded corner film gate with Dazz authentic 8mm mask
                val output = Bitmap.createBitmap(srcW, srcH, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(output)
                val paint = Paint(Paint.ANTI_ALIAS_FLAG)

                val rect = RectF(0f, 0f, srcW.toFloat(), srcH.toFloat())
                val cornerRadius = srcW * 0.08f

                val path = Path().apply {
                    addRoundRect(rect, cornerRadius, cornerRadius, Path.Direction.CW)
                }
                canvas.clipPath(path)
                canvas.drawBitmap(sourceBitmap, 0f, 0f, paint)

                // Overlay authentic 8mm frame texture if present
                context?.let { ctx ->
                    loadAssetBitmap(ctx, "8mm_frame1.png")?.let { frameTex ->
                        val scaled = Bitmap.createScaledBitmap(frameTex, srcW, srcH, true)
                        canvas.drawBitmap(scaled, 0f, 0f, null)
                    }
                }

                output
            }

            BorderStyle.CAMCORDER_FRAME -> {
                val output = sourceBitmap.copy(Bitmap.Config.ARGB_8888, true)
                val canvas = Canvas(output)
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.RED
                    style = Paint.Style.FILL
                }
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

    private fun loadAssetBitmap(context: Context, filename: String): Bitmap? {
        return try {
            context.assets.open(filename).use { input ->
                BitmapFactory.decodeStream(input)
            }
        } catch (e: Exception) {
            null
        }
    }
}
