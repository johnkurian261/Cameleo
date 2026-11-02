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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
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
        mirrorFrontCamera: Boolean,
        saveNegative: Boolean
    ): Uri? = withContext(Dispatchers.Default) {
        try {
            // 1. Convert ImageProxy -> Bitmap
            val rawBitmap = imageProxyToBitmap(imageProxy)
            val rotation = imageProxy.imageInfo.rotationDegrees

            // 2. Rotate & mirror to produce upright photo
            val matrix = Matrix()
            if (rotation != 0) matrix.postRotate(rotation.toFloat())
            if (mirrorFrontCamera) matrix.postScale(-1f, 1f)
            val orientedBitmap = Bitmap.createBitmap(rawBitmap, 0, 0, rawBitmap.width, rawBitmap.height, matrix, true)

            // 3. Aspect Ratio Cropping
            val croppedBitmap = cropToAspectRatio(orientedBitmap, aspectRatio)

            if (saveNegative) {
                saveNegativeToCache(context, croppedBitmap)
            }

            // 4. True Film-Science Emulsion & Optics Processing with Real Dazz Assets
            val processedBitmap = applyPresetEffects(context, croppedBitmap, preset, tuning)

            // 5. Apply Retro Date Stamp
            val stampedBitmap = if (tuning.dateStampEnabled) {
                preset.dateStamp?.let {
                    DateStampRenderer.applyDateStamp(processedBitmap, it, tuning.customYear)
                } ?: processedBitmap
            } else {
                processedBitmap
            }

            // 6. Apply Analog Film Border
            val borderedBitmap = preset.border?.let {
                BorderRenderer.applyBorder(stampedBitmap, it)
            } ?: stampedBitmap

            // 7. Save to MediaStore (DCIM/Cameleo)
            saveToGallery(context, borderedBitmap, preset.name)
        } catch (e: Exception) {
            Log.e(TAG, "Error in image processing pipeline", e)
            null
        } finally {
            imageProxy.close()
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
    private fun applyPresetEffects(
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

        // Micro-tuning offsets
        val tempShift = (grading.temperature + tuning.temperatureOffset).coerceIn(-1.0f, 1.0f)
        val tintShift = (grading.tint + tuning.tintOffset).coerceIn(-1.0f, 1.0f)
        val satBoost  = (grading.saturation + tuning.saturationOffset).coerceIn(0.0f, 2.0f)
        val contBoost = (grading.contrast + tuning.contrastOffset).coerceIn(0.5f, 2.0f)
        val fade      = grading.fade

        val halationEnabled = tuning.halationEnabled && halation?.enabled == true && halation.intensity > 0.01f
        val halationThresh = halation?.threshold ?: 0.80f
        val halationInt = halation?.intensity ?: 0.35f

        // ── Step 1: Pixel Color Grading via Hald CLUT with Trilinear Sampling ──
        for (i in pixels.indices) {
            val px = pixels[i]
            val ri = (px shr 16) and 0xFF
            val gi = (px shr 8) and 0xFF
            val bi = px and 0xFF

            var r: Float
            var g: Float
            var b: Float

            if (lut != null) {
                // Trilinear sampling in 512x512 Hald CLUT 8
                val bScaled = bi * 63.0f / 255.0f
                val b0 = bScaled.toInt().coerceIn(0, 63)
                val b1 = (b0 + 1).coerceIn(0, 63)
                val bFrac = bScaled - b0

                val rScaled = ri * 63.0f / 255.0f
                val r0 = rScaled.toInt().coerceIn(0, 63)
                val r1 = (r0 + 1).coerceIn(0, 63)
                val rFrac = rScaled - r0

                val gScaled = gi * 63.0f / 255.0f
                val g0 = gScaled.toInt().coerceIn(0, 63)
                val g1 = (g0 + 1).coerceIn(0, 63)
                val gFrac = gScaled - g0

                val c000 = sampleLut(lut, r0, g0, b0)
                val c100 = sampleLut(lut, r1, g0, b0)
                val c010 = sampleLut(lut, r0, g1, b0)
                val c110 = sampleLut(lut, r1, g1, b0)
                val c001 = sampleLut(lut, r0, g0, b1)
                val c101 = sampleLut(lut, r1, g0, b1)
                val c011 = sampleLut(lut, r0, g1, b1)
                val c111 = sampleLut(lut, r1, g1, b1)

                r = trilinear(
                    ((c000 shr 16) and 0xFF) / 255.0f,
                    ((c100 shr 16) and 0xFF) / 255.0f,
                    ((c010 shr 16) and 0xFF) / 255.0f,
                    ((c110 shr 16) and 0xFF) / 255.0f,
                    ((c001 shr 16) and 0xFF) / 255.0f,
                    ((c101 shr 16) and 0xFF) / 255.0f,
                    ((c011 shr 16) and 0xFF) / 255.0f,
                    ((c111 shr 16) and 0xFF) / 255.0f,
                    rFrac, gFrac, bFrac
                )
                g = trilinear(
                    ((c000 shr 8) and 0xFF) / 255.0f,
                    ((c100 shr 8) and 0xFF) / 255.0f,
                    ((c010 shr 8) and 0xFF) / 255.0f,
                    ((c110 shr 8) and 0xFF) / 255.0f,
                    ((c001 shr 8) and 0xFF) / 255.0f,
                    ((c101 shr 8) and 0xFF) / 255.0f,
                    ((c011 shr 8) and 0xFF) / 255.0f,
                    ((c111 shr 8) and 0xFF) / 255.0f,
                    rFrac, gFrac, bFrac
                )
                b = trilinear(
                    (c000 and 0xFF) / 255.0f,
                    (c100 and 0xFF) / 255.0f,
                    (c010 and 0xFF) / 255.0f,
                    (c110 and 0xFF) / 255.0f,
                    (c001 and 0xFF) / 255.0f,
                    (c101 and 0xFF) / 255.0f,
                    (c011 and 0xFF) / 255.0f,
                    (c111 and 0xFF) / 255.0f,
                    rFrac, gFrac, bFrac
                )
            } else {
                r = applyFilmicCurve(ri / 255.0f, contBoost, fade)
                g = applyFilmicCurve(gi / 255.0f, contBoost, fade)
                b = applyFilmicCurve(bi / 255.0f, contBoost, fade)
            }

            // User temperature / tint fine adjustment
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

            // User saturation fine adjustment
            if (abs(satBoost - 1.0f) > 0.01f) {
                val newLuma = 0.2126f * r + 0.7152f * g + 0.0722f * b
                r = newLuma + satBoost * (r - newLuma)
                g = newLuma + satBoost * (g - newLuma)
                b = newLuma + satBoost * (b - newLuma)
            }

            // Film halation bloom around specular highlights
            if (halationEnabled) {
                val origLuma = (0.2126f * ri + 0.7152f * gi + 0.0722f * bi) / 255.0f
                if (origLuma > halationThresh) {
                    val halo = ((origLuma - halationThresh) / (1.0f - halationThresh)) * halationInt
                    r += 1.0f * halo
                    g += 0.20f * halo
                    b += 0.03f * halo
                }
            }

            // Camcorder scanline modulation
            if (isCamcorder) {
                val yCoord = i / width
                val scanline = (sin(yCoord.toFloat() / height * 600.0 * PI) * 0.5 + 0.5).toFloat()
                val sf = 0.88f + 0.12f * scanline
                r *= sf; g *= sf; b *= sf
            }

            val ir2 = (r.coerceIn(0.0f, 1.0f) * 255.0f).toInt()
            val ig2 = (g.coerceIn(0.0f, 1.0f) * 255.0f).toInt()
            val ib2 = (b.coerceIn(0.0f, 1.0f) * 255.0f).toInt()
            pixels[i] = (0xFF shl 24) or (ir2 shl 16) or (ig2 shl 8) or ib2
        }

        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        result.setPixels(pixels, 0, width, 0, 0, width, height)

        // ── Step 2: High-Performance Canvas Blending with Real Dazz Cam Textures ─
        val canvas = Canvas(result)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // 2a. Real Photographic Vignette (Multiply blend)
        val vigAsset = selectVignetteForPreset(preset.id)
        val vigIntensity = (lens.vignetteIntensity * tuning.vignetteBoost).coerceIn(0.0f, 1.0f)
        if (vigIntensity > 0.03f) {
            if (vigAsset != null) {
                loadTexture(context, vigAsset)?.let { tex ->
                    val scaled = Bitmap.createScaledBitmap(tex, width, height, true)
                    paint.reset()
                    paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.MULTIPLY)
                    paint.alpha = (vigIntensity * 255.0f).toInt().coerceIn(0, 255)
                    canvas.drawBitmap(scaled, 0f, 0f, paint)
                }
            } else {
                applyMathVignette(result, vigIntensity, lens.vignetteRadius)
            }
        }

        // 2b. Real Scanned Silver Halide Grain Texture (Screen / Soft Light Overlay)
        val grainAsset = selectGrainForPreset(preset.id, isCamcorder)
        val effectiveGrain = (grain.intensity * tuning.grainBoost).coerceIn(0.0f, 1.0f)
        if (grain.enabled && effectiveGrain > 0.01f && grainAsset != null) {
            loadTexture(context, grainAsset)?.let { tex ->
                val tilePaint = Paint()
                tilePaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SCREEN)
                // Opacity tuned to authentic Dazz Cam grain subtlety
                tilePaint.alpha = (effectiveGrain * 120.0f).toInt().coerceIn(15, 130)

                var ty = 0
                while (ty < height) {
                    var tx = 0
                    while (tx < width) {
                        canvas.drawBitmap(tex, tx.toFloat(), ty.toFloat(), tilePaint)
                        tx += tex.width
                    }
                    ty += tex.height
                }
            }
        }

        // 2c. Real Scanned Dust & Scratches Overlay
        val dustAsset = selectDustForPreset(preset.id)
        if (dustAsset != null) {
            loadTexture(context, dustAsset)?.let { tex ->
                val scaled = Bitmap.createScaledBitmap(tex, width, height, true)
                paint.reset()
                paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.MULTIPLY)
                paint.alpha = 32 // ~12% opacity
                canvas.drawBitmap(scaled, 0f, 0f, paint)
            }
        }

        // 2d. Real Photographic Light Leaks (Screen blend)
        val leakEnabled = tuning.lightLeakEnabled && leak?.enabled == true && leak.intensity > 0.01f
        if (leakEnabled) {
            val leakAsset = selectLightLeakForPreset(preset.id)
            loadTexture(context, leakAsset)?.let { tex ->
                val scaled = Bitmap.createScaledBitmap(tex, width, height, true)
                paint.reset()
                paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SCREEN)
                paint.alpha = ((leak!!.intensity * 0.40f) * 255.0f).toInt().coerceIn(0, 180)
                canvas.drawBitmap(scaled, 0f, 0f, paint)
            }
        }

        // 2e. Real Paper Fade Texture (Multiply blend)
        val fadeAsset = selectFadeForPreset(preset.id)
        if (fadeAsset != null) {
            loadTexture(context, fadeAsset)?.let { tex ->
                val scaled = Bitmap.createScaledBitmap(tex, width, height, true)
                paint.reset()
                paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.MULTIPLY)
                paint.alpha = 50
                canvas.drawBitmap(scaled, 0f, 0f, paint)
            }
        }

        // 2f. Direct Flash Party Illumination Overlay
        val flashAsset = selectFlashForPreset(preset.id)
        if (flashAsset != null) {
            loadTexture(context, flashAsset)?.let { tex ->
                val scaled = Bitmap.createScaledBitmap(tex, width, height, true)
                paint.reset()
                paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SCREEN)
                paint.alpha = 55
                canvas.drawBitmap(scaled, 0f, 0f, paint)
            }
        }

        return result
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
        "analog_35mm_classic"  -> "lut_dclassic.png"
        "analog_warm_film"     -> "lut_dclassic.png"
        "analog_soft_film"     -> "lut_ct2f.png"
        "analog_vintage_color" -> "lut_dexp.png"
        "analog_faded_film"    -> "lut_nt16.png"
        "analog_cinema_film"   -> "lut_d3d.png"
        "ccd_classic"          -> "lut_135ne.png"
        "ccd_flash"            -> "lut_dfuns.png"
        "ccd_cool_digital"     -> "lut_ir.png"
        "ccd_cyber_y2k"        -> "lut_135ne.png"
        "disp_waterproof"      -> "lut_dfuns.png"
        "disp_half_frame"      -> "lut_dclassic.png"
        "disp_instant_party"   -> "lut_dfuns.png"
        "disp_flash"           -> "lut_dfuns.png"
        "inst_square"          -> "lut_instant.png"
        "inst_mini"            -> "lut_instant.png"
        "inst_wide"            -> "lut_instant.png"
        "cam_hi8"              -> "lut_vhs.png"
        "cam_vhs_c"            -> "lut_vhs.png"
        "cam_mini_dv"          -> "lut_vhs.png"
        "cam_retro_8mm"        -> "lut_vhs.png"
        else                   -> "lut_dclassic.png"
    }

    private fun selectGrainForPreset(presetId: String, isCamcorder: Boolean): String = when {
        isCamcorder                            -> "vhs_grain01.jpg"
        presetId == "analog_35mm_classic"      -> "grain_dclassic.jpg"
        presetId == "ccd_cyber_y2k"            -> "grain_classicu.jpg"
        presetId == "analog_vintage_color"     -> "grain_dexp.jpg"
        presetId == "disp_flash"               -> "grain_cpm35.jpg"
        presetId == "inst_square"              -> "grain_135sr.jpg"
        presetId == "ccd_flash"                -> "grain_dfuns.jpg"
        presetId == "disp_waterproof"          -> "grain_dqs.jpg"
        presetId == "ccd_cool_digital"         -> "grain_IR.jpg"
        presetId.startsWith("inst")            -> "grain_instc.jpg"
        presetId == "analog_cinema_film"       -> "grain_d3d1.jpg"
        presetId.startsWith("ccd")             -> "grain_ccdr.jpg"
        presetId.startsWith("disp")            -> "grain540_01.jpg"
        presetId == "analog_soft_film"         -> "grain_s67.jpg"
        presetId == "analog_faded_film"        -> "grain_dexp2.jpg"
        else                                   -> "grain_dclassic.jpg"
    }

    private fun selectVignetteForPreset(presetId: String): String? = when (presetId) {
        "analog_35mm_classic"  -> "vignette_dclassic.jpg"
        "analog_soft_film"     -> "vignette_ct2f.jpg"
        "analog_vintage_color" -> "vignette_dexp.jpg"
        "analog_faded_film"    -> "vignette_nt16.jpg"
        "disp_flash"           -> "vignette_cpm35.jpg"
        "inst_square"          -> "135sr_vig.jpg"
        "ccd_flash"            -> "sclassic_vintage.jpg"
        else                   -> null
    }

    private fun selectDustForPreset(presetId: String): String? = when {
        presetId.startsWith("inst")            -> "dust_instc.png"
        presetId == "ccd_classic" ||
        presetId == "inst_square" ||
        presetId == "analog_35mm_classic"      -> "135_dust.png"
        presetId.startsWith("cam")             -> "8mm_dust.png"
        presetId.startsWith("analog")          -> "dust_black.png"
        presetId.startsWith("disp")            -> "dust_black.png"
        else                                   -> null
    }

    private fun selectFadeForPreset(presetId: String): String? = when (presetId) {
        "analog_vintage_color" -> "fade_dexp.jpg"
        "analog_faded_film"    -> "fade_nt16.jpg"
        else                   -> null
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

    // ─── Mathematical Fallbacks ──────────────────────────────────────────────
    private fun applyMathVignette(bmp: Bitmap, intensity: Float, radius: Float) {
        val w = bmp.width; val h = bmp.height
        val cx = w * 0.5f; val cy = h * 0.5f
        val maxD = sqrt(cx * cx + cy * cy)
        val px = IntArray(w * h)
        bmp.getPixels(px, 0, w, 0, 0, w, h)
        for (y in 0 until h) {
            val dy = y - cy
            for (x in 0 until w) {
                val dx = x - cx
                val dn = (sqrt(dx * dx + dy * dy) / maxD) / radius
                val v = (1.0f - intensity * dn * dn).coerceIn(0.2f, 1.0f)
                val i = y * w + x
                val c = px[i]
                val r = ((c shr 16 and 0xFF) * v).toInt()
                val g = ((c shr 8 and 0xFF) * v).toInt()
                val b = ((c and 0xFF) * v).toInt()
                px[i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            }
        }
        bmp.setPixels(px, 0, w, 0, 0, w, h)
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
        val buffer: ByteBuffer = image.planes[0].buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }

    private fun cropToAspectRatio(bitmap: Bitmap, mode: AspectRatioMode): Bitmap {
        val srcW = bitmap.width; val srcH = bitmap.height
        val srcRatio = srcW.toFloat() / srcH
        val targetRatio = if (srcW > srcH) mode.ratio else (1.0f / mode.ratio)
        val cropW: Int; val cropH: Int
        if (srcRatio > targetRatio) {
            cropH = srcH; cropW = (srcH * targetRatio).toInt().coerceAtMost(srcW)
        } else {
            cropW = srcW; cropH = (srcW / targetRatio).toInt().coerceAtMost(srcH)
        }
        val startX = (srcW - cropW) / 2
        val startY = (srcH - cropH) / 2
        return Bitmap.createBitmap(bitmap, startX, startY, cropW, cropH)
    }

    private fun saveNegativeToCache(context: Context, bitmap: Bitmap) {
        try {
            val cacheDir = File(context.cacheDir, "cameleo_negatives")
            if (!cacheDir.exists()) cacheDir.mkdirs()
            val file = File(cacheDir, "NEG_${System.currentTimeMillis()}.jpg")
            FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it) }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to save negative", e)
        }
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
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 96, it)
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
