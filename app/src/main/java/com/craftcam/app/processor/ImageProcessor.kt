package com.craftcam.app.processor

import android.content.ContentValues
import android.content.Context
import android.graphics.*
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.camera.core.ImageProxy
import androidx.exifinterface.media.ExifInterface
import com.craftcam.app.camera.AspectRatioMode
import com.craftcam.app.camera.UserTuningConfig
import com.craftcam.app.model.CameraCategory
import com.craftcam.app.model.CameraPreset
import kotlinx.coroutines.*
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.*

object ImageProcessor {

    private const val TAG = "ImageProcessor"

    // ─── Fast Memory Caches ─────────────────────────────────────────────────
    private val lutCache = mutableMapOf<String, IntArray>()
    private val textureCache = mutableMapOf<String, Bitmap?>()

    // ─── Main Processing & Saving Pipeline ──────────────────────────────────
    suspend fun processAndSaveImage(
        context: Context,
        imageProxy: ImageProxy,
        preset: CameraPreset,
        tuning: UserTuningConfig = UserTuningConfig(),
        aspectRatio: AspectRatioMode,
        mirrorFrontCamera: Boolean
    ): Uri? = withContext(Dispatchers.Default) {
        val rawBitmap: Bitmap
        val rotation: Int
        try {
            rotation = imageProxy.imageInfo.rotationDegrees
            rawBitmap = imageProxyToBitmap(imageProxy)
        } finally {
            // Instantly free the hardware buffer so CameraX never drops subsequent shots
            imageProxy.close()
        }

        Log.i(TAG, "Native full-sensor photo captured: ${rawBitmap.width}x${rawBitmap.height}, rotation=$rotation, preset=${preset.id}")
        var workingBitmap: Bitmap = rawBitmap
        try {
            // 1. Maintain full native camera sensor resolution (cap at 4096px only for ultra-high megapixel sensors to protect heap)
            if (workingBitmap.width > 4096 || workingBitmap.height > 4096) {
                val scale = 4096f / maxOf(workingBitmap.width, workingBitmap.height)
                val targetW = (workingBitmap.width * scale).toInt().coerceAtLeast(1)
                val targetH = (workingBitmap.height * scale).toInt().coerceAtLeast(1)
                val scaled = Bitmap.createScaledBitmap(workingBitmap, targetW, targetH, true)
                if (scaled != workingBitmap) {
                    workingBitmap.recycle()
                    workingBitmap = scaled
                }
            }

            // 2. Rotate & mirror to produce upright photo
            val matrix = Matrix()
            if (rotation != 0) matrix.postRotate(rotation.toFloat())
            if (mirrorFrontCamera) matrix.postScale(-1f, 1f)
            if (rotation != 0 || mirrorFrontCamera) {
                val oriented = Bitmap.createBitmap(workingBitmap, 0, 0, workingBitmap.width, workingBitmap.height, matrix, true)
                if (oriented != workingBitmap) {
                    workingBitmap.recycle()
                    workingBitmap = oriented
                }
            }

            // 3. Aspect Ratio Cropping
            val cropped = cropToAspectRatio(workingBitmap, aspectRatio)
            if (cropped != workingBitmap) {
                workingBitmap.recycle()
                workingBitmap = cropped
            }

            // 4. True Film-Science Emulsion & Optics Processing with Real Dazz Assets
            val processed = applyPresetEffects(context, workingBitmap, preset, tuning)
            if (processed != workingBitmap) {
                workingBitmap.recycle()
                workingBitmap = processed
            }

            // 5. Apply Retro Date Stamp
            if (tuning.dateStampEnabled && preset.dateStamp != null) {
                val stamped = DateStampRenderer.applyDateStamp(workingBitmap, preset.dateStamp, tuning.customYear, context)
                if (stamped != workingBitmap) {
                    workingBitmap.recycle()
                    workingBitmap = stamped
                }
            }

            // 6. Apply Analog Film Border
            if (preset.border != null) {
                val bordered = BorderRenderer.applyBorder(workingBitmap, preset.border, context)
                if (bordered != workingBitmap) {
                    workingBitmap.recycle()
                    workingBitmap = bordered
                }
            }

            // 7. Save to MediaStore (DCIM/Cameleo)
            val uri = saveToGallery(context, workingBitmap, preset.name)
            workingBitmap.recycle()
            uri
        } catch (t: Throwable) {
            Log.e(TAG, "Error in image processing pipeline", t)
            if (!workingBitmap.isRecycled) {
                workingBitmap.recycle()
            }
            null
        }
    }

    // ─── Fast Asset & LUT Loading ───────────────────────────────────────────
    private fun getOrLoadLut(context: Context, lutFileName: String): IntArray? {
        synchronized(lutCache) {
            lutCache[lutFileName]?.let { return it }
            return try {
                context.assets.open(lutFileName).use { input ->
                    val bmp = BitmapFactory.decodeStream(input)
                    if (bmp != null) {
                        val lut512 = if (bmp.width == 512 && bmp.height == 512) bmp
                        else Bitmap.createScaledBitmap(bmp, 512, 512, true)
                        val pixels = IntArray(512 * 512)
                        lut512.getPixels(pixels, 0, 512, 0, 0, 512, 512)
                        lutCache[lutFileName] = pixels
                        Log.d(TAG, "Cached 3D LUT: $lutFileName (512x512)")
                        pixels
                    } else null
                }
            } catch (e: Exception) {
                Log.w(TAG, "Could not load LUT: $lutFileName", e)
                null
            }
        }
    }

    private fun loadTexture(context: Context, name: String): Bitmap? {
        synchronized(textureCache) {
            if (textureCache.containsKey(name)) {
                return textureCache[name]
            }
            return try {
                context.assets.open(name).use { input ->
                    val bmp = BitmapFactory.decodeStream(input)
                    textureCache[name] = bmp
                    bmp
                }
            } catch (e: Exception) {
                Log.w(TAG, "Texture not found: $name")
                textureCache[name] = null
                null
            }
        }
    }

    // ─── True Film Emulsion & Dazz Cam Processing Engine ─────────────────────
    private suspend fun applyPresetEffects(
        context: Context,
        source: Bitmap,
        preset: CameraPreset,
        tuning: UserTuningConfig = UserTuningConfig()
    ): Bitmap {
        val width = source.width
        val height = source.height
        val pixels = IntArray(width * height)
        source.getPixels(pixels, 0, width, 0, 0, width, height)

        val grading = preset.colorGrading
        val lens = preset.lens
        val grain = preset.grain
        val halation = preset.halation
        val leak = preset.lightLeak
        val isCamcorder = preset.category == CameraCategory.CAMCORDER

        // 1. Identify camera-specific 3D LUT
        val lutFile = selectLutForPreset(preset.id)
        val lut = getOrLoadLut(context, lutFile)

        // Micro-tuning user offsets
        val tempShift = tuning.temperatureOffset.coerceIn(-1.0f, 1.0f)
        val tintShift = tuning.tintOffset.coerceIn(-1.0f, 1.0f)
        val satBoost  = (1.0f + tuning.saturationOffset).coerceIn(0.0f, 2.0f)
        val contBoost = (1.0f + tuning.contrastOffset).coerceIn(0.5f, 2.0f)

        // ── Step 1: High-Performance Parallel 3D Trilinear LUT Interpolation ──
        val numCores = Runtime.getRuntime().availableProcessors().coerceIn(2, 8)
        val chunkSize = (pixels.size + numCores - 1) / numCores

        coroutineScope {
            (0 until numCores).map { coreIndex ->
                async(Dispatchers.Default) {
                    val start = coreIndex * chunkSize
                    val end = minOf(pixels.size, start + chunkSize)

                    for (i in start until end) {
                        val px = pixels[i]
                        val ri = (px shr 16) and 0xFF
                        val gi = (px shr 8) and 0xFF
                        val bi = px and 0xFF

                        var r: Float
                        var g: Float
                        var b: Float

                        if (lut != null) {
                            // Smooth 3D trilinear sampling (continuous floating coordinates in 64^3 cube)
                            val rf = ri * (63.0f / 255.0f)
                            val gf = gi * (63.0f / 255.0f)
                            val bf = bi * (63.0f / 255.0f)

                            val r0 = rf.toInt()
                            val r1 = if (r0 < 63) r0 + 1 else 63
                            val rx = rf - r0

                            val g0 = gf.toInt()
                            val g1 = if (g0 < 63) g0 + 1 else 63
                            val gy = gf - g0

                            val b0 = bf.toInt()
                            val b1 = if (b0 < 63) b0 + 1 else 63
                            val bz = bf - b0

                            val tX0 = (b0 and 7) shl 6
                            val tY0 = (b0 shr 3) shl 6
                            val tX1 = (b1 and 7) shl 6
                            val tY1 = (b1 shr 3) shl 6

                            val row0_0 = (tY0 + g0) * 512 + tX0
                            val row0_1 = (tY0 + g1) * 512 + tX0
                            val row1_0 = (tY1 + g0) * 512 + tX1
                            val row1_1 = (tY1 + g1) * 512 + tX1

                            val c000 = lut[row0_0 + r0]
                            val c100 = lut[row0_0 + r1]
                            val c010 = lut[row0_1 + r0]
                            val c110 = lut[row0_1 + r1]
                            val c001 = lut[row1_0 + r0]
                            val c101 = lut[row1_0 + r1]
                            val c011 = lut[row1_1 + r0]
                            val c111 = lut[row1_1 + r1]

                            // R Channel Trilinear Lerp
                            val r00 = ((c000 shr 16) and 0xFF) * (1f - rx) + ((c100 shr 16) and 0xFF) * rx
                            val r10 = ((c010 shr 16) and 0xFF) * (1f - rx) + ((c110 shr 16) and 0xFF) * rx
                            val r01 = ((c001 shr 16) and 0xFF) * (1f - rx) + ((c101 shr 16) and 0xFF) * rx
                            val r11 = ((c011 shr 16) and 0xFF) * (1f - rx) + ((c111 shr 16) and 0xFF) * rx
                            val r0_y = r00 * (1f - gy) + r10 * gy
                            val r1_y = r01 * (1f - gy) + r11 * gy
                            r = (r0_y * (1f - bz) + r1_y * bz) * (1.0f / 255.0f)

                            // G Channel Trilinear Lerp
                            val g00 = ((c000 shr 8) and 0xFF) * (1f - rx) + ((c100 shr 8) and 0xFF) * rx
                            val g10 = ((c010 shr 8) and 0xFF) * (1f - rx) + ((c110 shr 8) and 0xFF) * rx
                            val g01 = ((c001 shr 8) and 0xFF) * (1f - rx) + ((c101 shr 8) and 0xFF) * rx
                            val g11 = ((c011 shr 8) and 0xFF) * (1f - rx) + ((c111 shr 8) and 0xFF) * rx
                            val g0_y = g00 * (1f - gy) + g10 * gy
                            val g1_y = g01 * (1f - gy) + g11 * gy
                            g = (g0_y * (1f - bz) + g1_y * bz) * (1.0f / 255.0f)

                            // B Channel Trilinear Lerp
                            val b00 = (c000 and 0xFF) * (1f - rx) + (c100 and 0xFF) * rx
                            val b10 = (c010 and 0xFF) * (1f - rx) + (c110 and 0xFF) * rx
                            val b01 = (c001 and 0xFF) * (1f - rx) + (c101 and 0xFF) * rx
                            val b11 = (c011 and 0xFF) * (1f - rx) + (c111 and 0xFF) * rx
                            val b0_y = b00 * (1f - gy) + b10 * gy
                            val b1_y = b01 * (1f - gy) + b11 * gy
                            b = (b0_y * (1f - bz) + b1_y * bz) * (1.0f / 255.0f)
                        } else {
                            r = applyFilmicCurve(ri / 255.0f, grading.contrast, grading.fade)
                            g = applyFilmicCurve(gi / 255.0f, grading.contrast, grading.fade)
                            b = applyFilmicCurve(bi / 255.0f, grading.contrast, grading.fade)
                        }

                        // Flattering Skin Tone Radiance (Dazz Cam HighPassSkinSmoothing parity)
                        if (r > g && g > b && r > 0.22f && r < 0.90f) {
                            val skinFactor = ((r - b) * 2.2f).coerceIn(0.0f, 1.0f)
                            val midLuma = (0.2126f * r + 0.7152f * g + 0.0722f * b)
                            val peachWeight = (1.0f - abs(midLuma - 0.55f) * 2.2f).coerceIn(0.0f, 1.0f) * skinFactor
                            r = (r + 0.025f * peachWeight).coerceIn(0f, 1f)
                            g = (g + 0.010f * peachWeight).coerceIn(0f, 1f)
                        }

                        // User contrast fine-tuning (if slider moved)
                        if (abs(contBoost - 1.0f) > 0.02f) {
                            r = applyFilmicCurve(r, contBoost, 0.0f)
                            g = applyFilmicCurve(g, contBoost, 0.0f)
                            b = applyFilmicCurve(b, contBoost, 0.0f)
                        }

                        // User temperature / tint fine adjustment (if slider moved)
                        if (abs(tempShift) > 0.01f || abs(tintShift) > 0.01f) {
                            val luma = 0.2126f * r + 0.7152f * g + 0.0722f * b
                            val midW = (1.0f - abs(luma - 0.5f) * 2.2f).coerceIn(0.0f, 1.0f)
                            r += 0.06f * tempShift * midW
                            g += 0.02f * tempShift * midW
                            b -= 0.05f * tempShift * midW

                            g += 0.04f * tintShift * midW
                            r -= 0.02f * tintShift * midW
                            b -= 0.02f * tintShift * midW
                        }

                        // User saturation fine adjustment (if slider moved)
                        if (abs(satBoost - 1.0f) > 0.01f) {
                            val newLuma = 0.2126f * r + 0.7152f * g + 0.0722f * b
                            r = newLuma + satBoost * (r - newLuma)
                            g = newLuma + satBoost * (g - newLuma)
                            b = newLuma + satBoost * (b - newLuma)
                        }

                        // Camcorder CRT scanline modulation
                        if (isCamcorder) {
                            val yCoord = i / width
                            val scanline = (sin(yCoord.toFloat() / height * 600.0 * PI) * 0.5 + 0.5).toFloat()
                            val sf = 0.90f + 0.10f * scanline
                            r *= sf; g *= sf; b *= sf
                        }

                        val ir2 = (r.coerceIn(0.0f, 1.0f) * 255.0f).toInt()
                        val ig2 = (g.coerceIn(0.0f, 1.0f) * 255.0f).toInt()
                        val ib2 = (b.coerceIn(0.0f, 1.0f) * 255.0f).toInt()
                        pixels[i] = (0xFF shl 24) or (ir2 shl 16) or (ig2 shl 8) or ib2
                    }
                }
            }.awaitAll()
        }

        // ── Step 2: Optical Lens Vignette Shading (Clean Mathematical Falloff) ─
        val vigIntensity = (lens.vignetteIntensity * tuning.vignetteBoost).coerceIn(0.0f, 1.0f)
        if (vigIntensity > 0.03f) {
            applyMathVignetteToPixels(pixels, width, height, vigIntensity, lens.vignetteRadius)
        }

        // ── Step 3: Scanned Film Halide Grain Texture (Subtle Organic Emulsion) ─
        val grainAsset = selectGrainForPreset(preset.id, isCamcorder)
        val effectiveGrain = (grain.intensity * tuning.grainBoost).coerceIn(0.0f, 1.0f)
        if (grain.enabled && effectiveGrain > 0.01f) {
            loadTexture(context, grainAsset)?.let { tex ->
                applyGrainTexture(pixels, width, height, tex, effectiveGrain)
            }
        }

        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        result.setPixels(pixels, 0, width, 0, 0, width, height)

        // ── Step 4: Canvas Layer Overlays ────────────────────────────────────
        val canvas = Canvas(result)

        // 4a. Authentic Optical Halation (Red-Orange Photochemical Highlight Bloom)
        val halationEnabled = tuning.halationEnabled && halation?.enabled == true && halation.intensity > 0.01f
        if (halationEnabled) {
            val halationThresh = halation?.threshold ?: 0.80f
            val halationInt = halation?.intensity ?: 0.35f
            applyAuthenticHalation(pixels, width, height, canvas, halationThresh, halationInt)
        }

        // 4b. Real Scanned Dust & Scratches (Subtle Film Patina for Distressed Cameras)
        val dustAsset = selectDustForPreset(preset.id)
        if (dustAsset != null) {
            loadTexture(context, dustAsset)?.let { tex ->
                val srcRect = Rect(0, 0, tex.width, tex.height)
                val dstRect = Rect(0, 0, width, height)
                val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
                    alpha = 28 // Delicate organic film patina, NEVER opaque black spots
                }
                canvas.drawBitmap(tex, srcRect, dstRect, paint)
            }
        }

        // 4c. Real Photographic Light Leaks
        val leakEnabled = tuning.lightLeakEnabled && leak?.enabled == true && leak.intensity > 0.01f
        if (leakEnabled) {
            val leakAsset = selectLightLeakForPreset(preset.id)
            loadTexture(context, leakAsset)?.let { tex ->
                val srcRect = Rect(0, 0, tex.width, tex.height)
                val dstRect = Rect(0, 0, width, height)
                val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
                    xfermode = PorterDuffXfermode(PorterDuff.Mode.SCREEN)
                    alpha = ((leak.intensity * 0.65f) * 255.0f).toInt().coerceIn(0, 255)
                }
                canvas.drawBitmap(tex, srcRect, dstRect, paint)
            }
        }

        // 4d. Direct Flash Illumination Overlay
        val flashAsset = selectFlashForPreset(preset.id)
        if (flashAsset != null) {
            loadTexture(context, flashAsset)?.let { tex ->
                val srcRect = Rect(0, 0, tex.width, tex.height)
                val dstRect = Rect(0, 0, width, height)
                val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
                    xfermode = PorterDuffXfermode(PorterDuff.Mode.SCREEN)
                    alpha = 55
                }
                canvas.drawBitmap(tex, srcRect, dstRect, paint)
            }
        }

        return result
    }

    // ─── Authentic Optical Film Halation Bloom ───────────────────────────────
    private fun applyAuthenticHalation(
        pixels: IntArray,
        width: Int,
        height: Int,
        canvas: Canvas,
        threshold: Float,
        intensity: Float
    ) {
        val bw = maxOf(1, width / 4)
        val bh = maxOf(1, height / 4)
        val bloomPixels = IntArray(bw * bh)

        val thresh255 = (threshold * 255f).toInt()
        val range = (255 - thresh255).coerceAtLeast(1)

        for (by in 0 until bh) {
            val sy = (by * height) / bh
            val sRow = sy * width
            val bRow = by * bw
            for (bx in 0 until bw) {
                val sx = (bx * width) / bw
                val px = pixels[sRow + sx]
                val r = (px shr 16) and 0xFF
                val g = (px shr 8) and 0xFF
                val b = px and 0xFF
                val luma = (54 * r + 183 * g + 19 * b) shr 8
                if (luma > thresh255) {
                    val excess = (luma - thresh255).toFloat() / range
                    val a = (excess * intensity * 255f).toInt().coerceIn(0, 255)
                    val hr = 255
                    val hg = (65 * excess).toInt().coerceIn(0, 120)
                    val hb = (15 * excess).toInt().coerceIn(0, 40)
                    bloomPixels[bRow + bx] = (a shl 24) or (hr shl 16) or (hg shl 8) or hb
                } else {
                    bloomPixels[bRow + bx] = 0
                }
            }
        }

        fastBoxBlur(bloomPixels, bw, bh, 8)

        val bloomBitmap = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
        bloomBitmap.setPixels(bloomPixels, 0, bw, 0, 0, bw, bh)

        val srcRect = Rect(0, 0, bw, bh)
        val dstRect = Rect(0, 0, width, height)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.SCREEN)
            alpha = 255
        }
        canvas.drawBitmap(bloomBitmap, srcRect, dstRect, paint)

        bloomBitmap.recycle()
    }

    private fun fastBoxBlur(pixels: IntArray, w: Int, h: Int, r: Int) {
        val temp = IntArray(w * h)
        val div = 2 * r + 1

        for (y in 0 until h) {
            val row = y * w
            var aSum = 0; var rSum = 0; var gSum = 0; var bSum = 0
            for (i in -r..r) {
                val px = pixels[row + i.coerceIn(0, w - 1)]
                aSum += (px ushr 24)
                rSum += (px shr 16 and 0xFF)
                gSum += (px shr 8 and 0xFF)
                bSum += (px and 0xFF)
            }
            for (x in 0 until w) {
                temp[row + x] = ((aSum / div) shl 24) or ((rSum / div) shl 16) or ((gSum / div) shl 8) or (bSum / div)
                val leftPx = pixels[row + (x - r).coerceIn(0, w - 1)]
                val rightPx = pixels[row + (x + r + 1).coerceIn(0, w - 1)]
                aSum += (rightPx ushr 24) - (leftPx ushr 24)
                rSum += (rightPx shr 16 and 0xFF) - (leftPx shr 16 and 0xFF)
                gSum += (rightPx shr 8 and 0xFF) - (leftPx shr 8 and 0xFF)
                bSum += (rightPx and 0xFF) - (leftPx and 0xFF)
            }
        }

        for (x in 0 until w) {
            var aSum = 0; var rSum = 0; var gSum = 0; var bSum = 0
            for (i in -r..r) {
                val px = temp[i.coerceIn(0, h - 1) * w + x]
                aSum += (px ushr 24)
                rSum += (px shr 16 and 0xFF)
                gSum += (px shr 8 and 0xFF)
                bSum += (px and 0xFF)
            }
            for (y in 0 until h) {
                val idx = y * w + x
                pixels[idx] = ((aSum / div) shl 24) or ((rSum / div) shl 16) or ((gSum / div) shl 8) or (bSum / div)
                val topPx = temp[(y - r).coerceIn(0, h - 1) * w + x]
                val btmPx = temp[(y + r + 1).coerceIn(0, h - 1) * w + x]
                aSum += (btmPx ushr 24) - (topPx ushr 24)
                rSum += (btmPx shr 16 and 0xFF) - (topPx shr 16 and 0xFF)
                gSum += (btmPx shr 8 and 0xFF) - (topPx shr 8 and 0xFF)
                bSum += (btmPx and 0xFF) - (topPx and 0xFF)
            }
        }
    }

    // ─── Optical Vignette Shading ────────────────────────────────────────────
    private fun applyTextureVignette(
        pixels: IntArray,
        width: Int,
        height: Int,
        vigTex: Bitmap,
        intensity: Float
    ) {
        val vw = vigTex.width
        val vh = vigTex.height
        val vigPixels = IntArray(vw * vh)
        vigTex.getPixels(vigPixels, 0, vw, 0, 0, vw, vh)

        // Find min and max luminance of the texture mask for proper photographic normalization
        var minL = 255f
        var maxL = 0f
        for (vp in vigPixels) {
            val l = (vp shr 16 and 0xFF) * 0.299f + (vp shr 8 and 0xFF) * 0.587f + (vp and 0xFF) * 0.114f
            if (l < minL) minL = l
            if (l > maxL) maxL = l
        }
        val range = (maxL - minL).coerceAtLeast(1.0f)

        for (y in 0 until height) {
            val vy = (y * vh) / height
            val vRow = vy * vw
            val row = y * width
            for (x in 0 until width) {
                val vx = (x * vw) / width
                val vp = vigPixels[vRow + vx]
                val vl = (vp shr 16 and 0xFF) * 0.299f + (vp shr 8 and 0xFF) * 0.587f + (vp and 0xFF) * 0.114f
                val norm = ((vl - minL) / range).coerceIn(0.0f, 1.0f)
                val factor = (1.0f - intensity * (1.0f - norm)).coerceIn(0.20f, 1.0f)

                val i = row + x
                val cp = pixels[i]
                val a = cp ushr 24
                val r = (((cp shr 16 and 0xFF) * factor).toInt()).coerceIn(0, 255)
                val g = (((cp shr 8 and 0xFF) * factor).toInt()).coerceIn(0, 255)
                val b = (((cp and 0xFF) * factor).toInt()).coerceIn(0, 255)
                pixels[i] = (a shl 24) or (r shl 16) or (g shl 8) or b
            }
        }
    }

    private fun applyMathVignetteToPixels(
        pixels: IntArray,
        width: Int,
        height: Int,
        intensity: Float,
        radius: Float
    ) {
        val cx = width * 0.5f
        val cy = height * 0.5f
        val maxD = sqrt(cx * cx + cy * cy)

        for (y in 0 until height) {
            val dy = y - cy
            val row = y * width
            for (x in 0 until width) {
                val dx = x - cx
                val dn = (sqrt(dx * dx + dy * dy) / maxD) / radius
                val v = (1.0f - intensity * dn * dn).coerceIn(0.25f, 1.0f)

                val i = row + x
                val cp = pixels[i]
                val a = cp ushr 24
                val r = (((cp shr 16 and 0xFF) * v).toInt()).coerceIn(0, 255)
                val g = (((cp shr 8 and 0xFF) * v).toInt()).coerceIn(0, 255)
                val b = (((cp and 0xFF) * v).toInt()).coerceIn(0, 255)
                pixels[i] = (a shl 24) or (r shl 16) or (g shl 8) or b
            }
        }
    }

    // ─── Scanned Film Halide Grain Texture ───────────────────────────────────
    private fun applyGrainTexture(
        pixels: IntArray,
        width: Int,
        height: Int,
        grainTex: Bitmap,
        intensity: Float
    ) {
        val gw = grainTex.width
        val gh = grainTex.height
        val grainPixels = IntArray(gw * gh)
        grainTex.getPixels(grainPixels, 0, gw, 0, 0, gw, gh)

        // Delicate organic silver halide variation (+/- 3-4 levels in midtones, never coarse noise)
        val strength = intensity * 24.0f

        for (y in 0 until height) {
            val gy = (y % gh) * gw
            val row = y * width
            for (x in 0 until width) {
                val gx = x % gw
                val gp = grainPixels[gy + gx]
                val gl = (((gp shr 16 and 0xFF) * 0.299f + (gp shr 8 and 0xFF) * 0.587f + (gp and 0xFF) * 0.114f) - 128f) / 128f

                val i = row + x
                val cp = pixels[i]
                val cr = (cp shr 16 and 0xFF)
                val cg = (cp shr 8 and 0xFF)
                val cb = (cp and 0xFF)
                val luma = cr * 0.299f + cg * 0.587f + cb * 0.114f
                val midWeight = 1.0f - (kotlin.math.abs(luma - 128f) / 128f).coerceIn(0.0f, 0.85f)
                val delta = (gl * strength * midWeight).toInt()

                val a = cp ushr 24
                val r = (cr + delta).coerceIn(0, 255)
                val g = (cg + delta).coerceIn(0, 255)
                val b = (cb + delta).coerceIn(0, 255)
                pixels[i] = (a shl 24) or (r shl 16) or (g shl 8) or b
            }
        }
    }

    // ─── Hald CLUT Sampling Math ─────────────────────────────────────────────
    private fun sampleLut(lut: IntArray, r64: Int, g64: Int, b64: Int): Int {
        val tileX = (b64 % 8) * 64
        val tileY = (b64 / 8) * 64
        val lutX = tileX + r64
        val lutY = tileY + g64
        return lut[lutY * 512 + lutX]
    }

    private fun trilinear(
        c000: Float, c100: Float, c010: Float, c110: Float,
        c001: Float, c101: Float, c011: Float, c111: Float,
        xf: Float, yf: Float, zf: Float
    ): Float {
        val c00 = c000 * (1.0f - xf) + c100 * xf
        val c10 = c010 * (1.0f - xf) + c110 * xf
        val c01 = c001 * (1.0f - xf) + c101 * xf
        val c11 = c011 * (1.0f - xf) + c111 * xf
        val c0 = c00 * (1.0f - yf) + c10 * yf
        val c1 = c01 * (1.0f - yf) + c11 * yf
        return (c0 * (1.0f - zf) + c1 * zf).coerceIn(0.0f, 1.0f)
    }

    // ─── Preset to Asset Routing Tables ──────────────────────────────────────
    private fun selectLutForPreset(presetId: String): String = when (presetId) {
        "analog_35mm_classic"  -> "lut_analog_35mm_classic.png"
        "analog_warm_film"     -> "lut_analog_warm_film.png"
        "analog_soft_film"     -> "lut_analog_soft_film.png"
        "analog_vintage_color" -> "lut_analog_vintage_color.png"
        "analog_faded_film"    -> "lut_analog_faded_film.png"
        "analog_cinema_film"   -> "lut_analog_cinema_film.png"
        "ccd_classic"          -> "lut_ccd_classic.png"
        "ccd_flash"            -> "lut_ccd_flash.png"
        "ccd_cool_digital"     -> "lut_ccd_cool_digital.png"
        "ccd_y2k_digital"      -> "lut_ccd_y2k_digital.png"
        "ccd_retro_compact"    -> "lut_ccd_retro_compact.png"
        "disp_classic"         -> "lut_disp_classic.png"
        "disp_flash"           -> "lut_disp_flash.png"
        "disp_warm"            -> "lut_disp_warm.png"
        "disp_night"           -> "lut_disp_night.png"
        "inst_classic"         -> "lut_inst_classic.png"
        "inst_soft_polaroid"   -> "lut_inst_soft_polaroid.png"
        "inst_vintage"         -> "lut_inst_vintage.png"
        "cam_retro_vhs"        -> "lut_cam_retro_vhs.png"
        "cam_analog_video"     -> "lut_cam_analog_video.png"
        "cam_90s_camcorder"    -> "lut_cam_90s_camcorder.png"
        else                   -> "lut_analog_35mm_classic.png"
    }

    private fun selectGrainForPreset(presetId: String, isCamcorder: Boolean): String = when {
        isCamcorder                            -> "vhs_grain01.jpg"
        presetId == "analog_35mm_classic"      -> "grain_dclassic.jpg"
        presetId == "analog_warm_film"         -> "grain_dclassic.jpg"
        presetId == "analog_soft_film"         -> "grain_s67.jpg"
        presetId == "analog_vintage_color"     -> "grain_dexp.jpg"
        presetId == "analog_faded_film"        -> "grain_dexp2.jpg"
        presetId == "analog_cinema_film"       -> "grain_d3d1.jpg"
        presetId == "ccd_classic"              -> "grain_ccdr.jpg"
        presetId == "ccd_flash"                -> "grain_dfuns.jpg"
        presetId == "ccd_cool_digital"         -> "grain_IR.jpg"
        presetId == "ccd_y2k_digital"          -> "grain_classicu.jpg"
        presetId == "ccd_retro_compact"        -> "grain_dqs.jpg"
        presetId == "disp_classic"             -> "grain540_01.jpg"
        presetId == "disp_flash"               -> "grain_cpm35.jpg"
        presetId == "disp_warm"                -> "grain540_01.jpg"
        presetId == "disp_night"               -> "half_grain.jpg"
        presetId == "inst_classic"             -> "grain_instc.jpg"
        presetId == "inst_soft_polaroid"       -> "grain_instc.jpg"
        presetId == "inst_vintage"             -> "grain_135sr.jpg"
        else                                   -> "grain_dclassic.jpg"
    }

    private fun selectVignetteForPreset(presetId: String): String? = when (presetId) {
        "analog_35mm_classic", "analog_warm_film" -> "vignette_dclassic.jpg"
        "analog_soft_film"                        -> "vignette_ct2f.jpg"
        "analog_vintage_color"                    -> "vignette_dexp.jpg"
        "analog_faded_film"                       -> "vignette_nt16.jpg"
        "analog_cinema_film"                      -> "vignette_dclassic.jpg"
        "disp_flash"                              -> "vignette_cpm35.jpg"
        "disp_warm"                               -> "135sr_vig.jpg"
        "inst_classic", "inst_vintage"            -> "135sr_vig.jpg"
        "inst_soft_polaroid"                      -> "vignette_ct2f.jpg"
        "ccd_flash", "ccd_y2k_digital"            -> "sclassic_vintage.jpg"
        else                                      -> null
    }

    private fun selectDustForPreset(presetId: String): String? = when (presetId) {
        "inst_vintage"      -> "dust_instc.png"
        "cam_retro_vhs"     -> "8mm_dust.png"
        "cam_analog_video"  -> "8mm_dust.png"
        "analog_faded_film" -> "135_dust.png"
        else                -> null // All standard portrait and classic cameras stay clean & pristine
    }

    private fun selectFlashForPreset(presetId: String): String? = when (presetId) {
        "disp_flash", "ccd_flash" -> "flashc_01.jpg"
        else                      -> null
    }

    private fun selectLightLeakForPreset(presetId: String): String {
        return if (presetId.startsWith("inst")) {
            val idx = (System.currentTimeMillis() % 10).toInt() + 1
            "lightleaks_inst_%02d.jpg".format(idx)
        } else {
            val idx = (System.currentTimeMillis() % 17).toInt() + 1
            "lightleaks%d.jpg".format(idx)
        }
    }

    private fun applyFilmicCurve(v: Float, contrast: Float, fade: Float): Float {
        val x = v.coerceIn(0.0f, 1.0f)
        val s = if (x < 0.5f) 0.5f * (2.0f * x).pow(contrast)
                else 1.0f - 0.5f * (2.0f * (1.0f - x)).pow(contrast)
        return if (fade > 0.001f) {
            val lift = fade * 0.12f
            (s * (1.0f - lift) + lift).coerceIn(0.0f, 1.0f)
        } else s.coerceIn(0.0f, 1.0f)
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────
    private fun imageProxyToBitmap(image: ImageProxy): Bitmap {
        try {
            return image.toBitmap()
        } catch (e: Throwable) {
            Log.w(TAG, "image.toBitmap() failed, falling back to planes buffer decode", e)
        }
        val buffer: ByteBuffer = image.planes[0].buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            ?: throw IllegalStateException("Failed to decode ImageProxy to Bitmap")
    }

    private fun cropToAspectRatio(bitmap: Bitmap, mode: AspectRatioMode): Bitmap {
        val srcW = bitmap.width
        val srcH = bitmap.height
        val srcRatio = srcW.toFloat() / srcH
        val targetRatio = if (srcW > srcH) mode.ratio else (1.0f / mode.ratio)
        val cropW: Int
        val cropH: Int
        if (srcRatio > targetRatio) {
            cropH = srcH
            cropW = (srcH * targetRatio).toInt().coerceIn(1, srcW)
        } else {
            cropW = srcW
            cropH = (srcW / targetRatio).toInt().coerceIn(1, srcH)
        }
        val startX = (srcW - cropW) / 2
        val startY = (srcH - cropH) / 2
        return Bitmap.createBitmap(bitmap, startX, startY, cropW, cropH)
    }

    private fun saveToGallery(context: Context, bitmap: Bitmap, cameraName: String): Uri? {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val fileName = "CAMELEO_${timeStamp}.jpg"

        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_DCIM}/Cameleo")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }

        val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
        if (uri != null) {
            try {
                context.contentResolver.openOutputStream(uri)?.use {
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 98, it)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    contentValues.clear()
                    contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                    context.contentResolver.update(uri, contentValues, null, null)
                }
                try {
                    context.contentResolver.openFileDescriptor(uri, "rw")?.use { pfd ->
                        val exif = ExifInterface(pfd.fileDescriptor)
                        exif.setAttribute(ExifInterface.TAG_MAKE, "Cameleo")
                        exif.setAttribute(ExifInterface.TAG_MODEL, cameraName)
                        exif.setAttribute(ExifInterface.TAG_SOFTWARE, "Cameleo Vintage Camera 1.0")
                        exif.saveAttributes()
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to write EXIF attributes", e)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to write image data to MediaStore", e)
                return null
            }
        }
        return uri
    }
}
