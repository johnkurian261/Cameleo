package com.craftcam.app.processor

import android.content.Context
import android.graphics.*
import com.craftcam.app.model.BorderConfig
import com.craftcam.app.model.BorderStyle
import com.craftcam.app.model.DustConfig

object BorderRenderer {

    private val borderCache = HashMap<String, Bitmap>()

    fun loadAssetBitmap(context: Context, filename: String): Bitmap? {
        synchronized(borderCache) {
            borderCache[filename]?.let { return it }
            return try {
                context.assets.open(filename).use { input ->
                    val bmp = BitmapFactory.decodeStream(input)
                    if (bmp != null) {
                        borderCache[filename] = bmp
                    }
                    bmp
                }
            } catch (e: Exception) {
                null
            }
        }
    }

    /**
     * Draws an asset bitmap covering the specified destination rectangle [dstLeft, dstTop, dstRight, dstBottom].
     * If the orientation of the asset (portrait vs landscape) does not match the destination rect,
     * it automatically applies an exact 90-degree corner mapping so the frame fits naturally without distortion.
     */
    fun drawAssetCover(
        canvas: Canvas,
        bitmap: Bitmap,
        dstLeft: Float,
        dstTop: Float,
        dstRight: Float,
        dstBottom: Float,
        paint: Paint? = null
    ) {
        val dstW = dstRight - dstLeft
        val dstH = dstBottom - dstTop
        if (dstW <= 0f || dstH <= 0f) return

        val srcW = bitmap.width.toFloat()
        val srcH = bitmap.height.toFloat()

        val dstIsPortrait = dstW < dstH
        val srcIsPortrait = srcW < srcH
        val needsRotate = (dstIsPortrait != srcIsPortrait) && (bitmap.width != bitmap.height)

        val matrix = Matrix()
        if (needsRotate) {
            // Map 4 corners with 90° clockwise rotation
            val srcPts = floatArrayOf(
                0f, 0f,
                srcW, 0f,
                srcW, srcH,
                0f, srcH
            )
            val dstPts = floatArrayOf(
                dstRight, dstTop,
                dstRight, dstBottom,
                dstLeft, dstBottom,
                dstLeft, dstTop
            )
            matrix.setPolyToPoly(srcPts, 0, dstPts, 0, 4)
        } else {
            val srcRect = RectF(0f, 0f, srcW, srcH)
            val dstRect = RectF(dstLeft, dstTop, dstRight, dstBottom)
            matrix.setRectToRect(srcRect, dstRect, Matrix.ScaleToFit.FILL)
        }

        canvas.drawBitmap(bitmap, matrix, paint)
    }

    /**
     * Renders authentic frame cutouts directly on any Android Canvas (used by ViewfinderOverlay and ImageProcessor).
     */
    fun renderBorderOnCanvas(
        canvas: Canvas,
        style: BorderStyle,
        assetPath: String?,
        dstLeft: Float,
        dstTop: Float,
        dstRight: Float,
        dstBottom: Float,
        context: Context
    ) {
        if (style == BorderStyle.NONE) return

        val filterPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

        when (style) {
            BorderStyle.GOLF_FRAME -> {
                // Authentic Dazz Golf Disposable Camera 4-piece plastic chassis
                val borders = listOf("golf_border_t.png", "golf_border_b.png", "golf_border_l.png", "golf_border_r.png")
                for (bFile in borders) {
                    loadAssetBitmap(context, bFile)?.let { bTex ->
                        drawAssetCover(canvas, bTex, dstLeft, dstTop, dstRight, dstBottom, filterPaint)
                    }
                }
            }

            BorderStyle.HALF_FRAME -> {
                // Authentic Olympus PEN / D-Half split frame cutout
                val halfTex = loadAssetBitmap(context, assetPath ?: "half_border.png")
                halfTex?.let {
                    drawAssetCover(canvas, it, dstLeft, dstTop, dstRight, dstBottom, filterPaint)
                }
            }

            BorderStyle.CLASSIC_35MM_RANGEFINDER -> {
                // Authentic D-Classic rangefinder frame cutout (inner optical frame bevel)
                val frameAsset = assetPath ?: "dbalck_frame_in.png"
                loadAssetBitmap(context, frameAsset)?.let { inTex ->
                    drawAssetCover(canvas, inTex, dstLeft, dstTop, dstRight, dstBottom, filterPaint)
                }
            }

            BorderStyle.FILM_STRIP_135 -> {
                // Authentic 35mm negative film strip with sprocket hole perforations
                val cutAsset = assetPath ?: "135ne_frame_cut1.png"
                loadAssetBitmap(context, cutAsset)?.let { cutTex ->
                    drawAssetCover(canvas, cutTex, dstLeft, dstTop, dstRight, dstBottom, filterPaint)
                }
            }

            BorderStyle.MEDIUM_FORMAT_67 -> {
                // Authentic Makina / Pentax 67 medium format camera frame mask
                val tAsset = assetPath ?: "s67_frame_t1.png"
                loadAssetBitmap(context, tAsset)?.let { tTex ->
                    drawAssetCover(canvas, tTex, dstLeft, dstTop, dstRight, dstBottom, filterPaint)
                }
            }

            BorderStyle.SLIDE_MOUNT -> {
                // Authentic 35mm slide projection frame mount
                val sAsset = assetPath ?: "border_slidep1.jpg"
                loadAssetBitmap(context, sAsset)?.let { sTex ->
                    val mulPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
                        xfermode = PorterDuffXfermode(PorterDuff.Mode.MULTIPLY)
                    }
                    drawAssetCover(canvas, sTex, dstLeft, dstTop, dstRight, dstBottom, mulPaint)
                }
            }

            BorderStyle.POLAROID_WHITE_CHIN -> {
                // Authentic Dazz instant film frame with iconic white paper writing chin
                loadAssetBitmap(context, assetPath ?: "vinst_border1.png")?.let { vTex ->
                    drawAssetCover(canvas, vTex, dstLeft, dstTop, dstRight, dstBottom, filterPaint)
                }
            }

            BorderStyle.ROUNDED_16MM_GATE -> {
                // Authentic rounded 16mm motion picture gate mask
                val gateAsset = assetPath ?: "mask_16mm_01_r.jpg"
                loadAssetBitmap(context, gateAsset)?.let { maskTex ->
                    val mulPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
                        xfermode = PorterDuffXfermode(PorterDuff.Mode.MULTIPLY)
                    }
                    drawAssetCover(canvas, maskTex, dstLeft, dstTop, dstRight, dstBottom, mulPaint)
                }
            }

            BorderStyle.ROUNDED_8MM -> {
                loadAssetBitmap(context, assetPath ?: "mask_16mm_02_r.jpg")?.let { maskTex ->
                    val mulPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
                        xfermode = PorterDuffXfermode(PorterDuff.Mode.MULTIPLY)
                    }
                    drawAssetCover(canvas, maskTex, dstLeft, dstTop, dstRight, dstBottom, mulPaint)
                }
            }

            BorderStyle.CAMCORDER_FRAME -> {
                val dstW = dstRight - dstLeft
                val dstH = dstBottom - dstTop
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.RED
                    this.style = Paint.Style.FILL
                }
                val radius = dstW * 0.015f
                canvas.drawCircle(dstRight - dstW * 0.12f, dstTop + dstH * 0.08f, radius, paint)

                val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.WHITE
                    textSize = dstH * 0.035f
                    typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
                }
                canvas.drawText("REC", dstRight - dstW * 0.09f, dstTop + dstH * 0.09f, textPaint)
            }

            BorderStyle.CLASSIC_35MM_BLACK, BorderStyle.SQUARE_INSTANT_WHITE, BorderStyle.NONE -> {}
        }
    }

    /**
     * Renders authentic photographic film dust, lint, and hairs directly on Canvas.
     */
    fun renderDustOnCanvas(
        canvas: Canvas,
        dustConfig: DustConfig?,
        dstLeft: Float,
        dstTop: Float,
        dstRight: Float,
        dstBottom: Float,
        context: Context,
        opacityMultiplier: Float = 1.0f
    ) {
        val dustAsset = dustConfig?.assetPath ?: "dust_135.png"
        loadAssetBitmap(context, dustAsset)?.let { dustBmp ->
            val baseOpacity = dustConfig?.opacity ?: 0.65f
            val alphaVal = ((baseOpacity * opacityMultiplier) * 255f).toInt().coerceIn(140, 255)
            val dustPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
                alpha = alphaVal
            }
            drawAssetCover(canvas, dustBmp, dstLeft, dstTop, dstRight, dstBottom, dustPaint)
        }
    }

    fun applyBorder(sourceBitmap: Bitmap, config: BorderConfig, context: Context? = null): Bitmap {
        if (config.style == BorderStyle.NONE || context == null) return sourceBitmap

        val srcW = sourceBitmap.width
        val srcH = sourceBitmap.height

        return when (config.style) {
            BorderStyle.POLAROID_WHITE_CHIN -> {
                // Authentic Dazz instant film frame with iconic white paper writing chin
                val borderTex = loadAssetBitmap(context, config.assetPath ?: "vinst_border1.png")
                if (borderTex != null) {
                    val sideMargin = (srcW * 0.107f / 0.787f).toInt()
                    val topMargin = (srcH * 0.111f / 0.684f).toInt()
                    val bottomMargin = (srcH * 0.206f / 0.684f).toInt()

                    val targetW = srcW + sideMargin * 2
                    val targetH = srcH + topMargin + bottomMargin

                    val output = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
                    val canvas = Canvas(output)
                    // Draw photograph in center window
                    canvas.drawBitmap(sourceBitmap, sideMargin.toFloat(), topMargin.toFloat(), null)
                    // Draw authentic Dazz Polaroid frame border on top
                    val filterPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
                    val srcRect = Rect(0, 0, borderTex.width, borderTex.height)
                    val dstRect = Rect(0, 0, targetW, targetH)
                    canvas.drawBitmap(borderTex, srcRect, dstRect, filterPaint)
                    output
                } else {
                    sourceBitmap
                }
            }

            BorderStyle.CLASSIC_35MM_BLACK -> {
                val margin = (srcW * 0.04f).toInt()
                val targetW = srcW + margin * 2
                val targetH = srcH + margin * 2

                val output = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(output)
                canvas.drawColor(Color.rgb(12, 12, 14))

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

            else -> {
                // For all other frame styles (GOLF_FRAME, HALF_FRAME, CLASSIC_35MM_RANGEFINDER,
                // FILM_STRIP_135, MEDIUM_FORMAT_67, SLIDE_MOUNT, ROUNDED_16MM_GATE, CAMCORDER_FRAME),
                // render directly into sourceBitmap (if mutable) or mutable copy
                val output = if (sourceBitmap.isMutable) sourceBitmap else sourceBitmap.copy(Bitmap.Config.ARGB_8888, true)
                val canvas = Canvas(output)
                renderBorderOnCanvas(
                    canvas = canvas,
                    style = config.style,
                    assetPath = config.assetPath,
                    dstLeft = 0f,
                    dstTop = 0f,
                    dstRight = srcW.toFloat(),
                    dstBottom = srcH.toFloat(),
                    context = context
                )
                output
            }
        }
    }
}
