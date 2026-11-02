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
            // 1. Convert ImageProxy to Bitmap
            val rawBitmap = imageProxyToBitmap(imageProxy)
            val rotation = imageProxy.imageInfo.rotationDegrees

            // 2. Rotate & mirror to produce upright photo
            val matrix = Matrix()
            if (rotation != 0) {
                matrix.postRotate(rotation.toFloat())
            }
            if (mirrorFrontCamera) {
                matrix.postScale(-1f, 1f)
            }
            val orientedBitmap = Bitmap.createBitmap(rawBitmap, 0, 0, rawBitmap.width, rawBitmap.height, matrix, true)

            // 3. Aspect Ratio Cropping
            val croppedBitmap = cropToAspectRatio(orientedBitmap, aspectRatio)

            // Optional: Save raw negative
            if (saveNegative) {
                saveNegativeToCache(context, croppedBitmap)
            }

            // 4. True Film-Science Emulsion & Optics Processing with User Tuning
            val processedBitmap = applyPresetEffects(croppedBitmap, preset, tuning)

            // 5. Apply Retro Date Stamp with custom year override
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
            val savedUri = saveToGallery(context, borderedBitmap, preset.name)
            savedUri
        } catch (e: Exception) {
            Log.e(TAG, "Error in image processing pipeline", e)
            null
        } finally {
            imageProxy.close()
        }
    }

    private fun imageProxyToBitmap(image: ImageProxy): Bitmap {
        val buffer: ByteBuffer = image.planes[0].buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }

    private fun cropToAspectRatio(bitmap: Bitmap, mode: AspectRatioMode): Bitmap {
        val srcW = bitmap.width
        val srcH = bitmap.height
        val srcRatio = srcW.toFloat() / srcH.toFloat()
        val targetRatio = if (srcW > srcH) mode.ratio else (1f / mode.ratio)

        val cropW: Int
        val cropH: Int
        if (srcRatio > targetRatio) {
            cropH = srcH
            cropW = (srcH * targetRatio).toInt().coerceAtMost(srcW)
        } else {
            cropW = srcW
            cropH = (srcW / targetRatio).toInt().coerceAtMost(srcH)
        }

        val startX = (srcW - cropW) / 2
        val startY = (srcH - cropH) / 2

        return Bitmap.createBitmap(bitmap, startX, startY, cropW, cropH)
    }

    /**
     * High-fidelity photographic film simulation engine operating on RGB pixel buffers.
     * Incorporates Hurter & Driffield sensitometric S-curves, multi-scale organic halide grain,
     * luminance-separated split toning, and spatial halation bloom.
     */
    private fun applyPresetEffects(source: Bitmap, preset: CameraPreset, tuning: UserTuningConfig = UserTuningConfig()): Bitmap {
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

        val centerX = width * 0.5f
        val centerY = height * 0.5f
        val maxDist = sqrt(centerX * centerX + centerY * centerY)

        // Blend preset defaults with user color tuning offsets
        val contrast = (grading.contrast + tuning.contrastOffset).coerceIn(0.5f, 2.0f)
        val saturation = (grading.saturation + tuning.saturationOffset).coerceIn(0.0f, 2.0f)
        val temp = (grading.temperature + tuning.temperatureOffset).coerceIn(-1.0f, 1.0f)
        val tint = (grading.tint + tuning.tintOffset).coerceIn(-1.0f, 1.0f)
        val gamma = grading.gamma
        val invGamma = if (gamma > 0.01f) 1.0f / gamma else 1.0f
        val fade = grading.fade
        val rollOff = grading.highlightRollOff
        val shadowTintR = grading.shadowTintR
        val shadowTintG = grading.shadowTintG
        val shadowTintB = grading.shadowTintB

        val vigIntensity = (lens.vignetteIntensity * tuning.vignetteBoost).coerceIn(0.0f, 1.0f)
        val vigRadius = lens.vignetteRadius

        val grainEnabled = grain.enabled && grain.intensity > 0.005f && tuning.grainBoost > 0.01f
        val grainIntensity = grain.intensity * 1.5f * tuning.grainBoost
        val isColorNoise = grain.isColorNoise
        val random = Random(42) // consistent texture

        val halationEnabled = tuning.halationEnabled && halation?.enabled == true && halation.intensity > 0.01f
        val halationThreshold = halation?.threshold ?: 0.80f
        val halationIntensity = halation?.intensity ?: 0f

        val leakEnabled = tuning.lightLeakEnabled && leak?.enabled == true && leak.intensity > 0.01f
        val leakIntensity = leak?.intensity ?: 0f

        // Process pixels in a cache-friendly scanline loop
        for (y in 0 until height) {
            val yOffset = y * width
            val dy = y - centerY
            val dy2 = dy * dy
            val normY = y.toFloat() / height

            for (x in 0 until width) {
                val index = yOffset + x
                val pixel = pixels[index]

                var r = (pixel shr 16 and 0xFF) / 255.0f
                var g = (pixel shr 8 and 0xFF) / 255.0f
                var b = (pixel and 0xFF) / 255.0f

                val origLuma = 0.2126f * r + 0.7152f * g + 0.0722f * b

                // 1. Sensitometric Film H&D S-Curve (Milky shadows, smooth highlight cushion)
                r = applyFilmicCurve(r, contrast, fade, invGamma, rollOff)
                g = applyFilmicCurve(g, contrast, fade, invGamma, rollOff)
                b = applyFilmicCurve(b, contrast, fade, invGamma, rollOff)

                // 2. Luminance-Separated Split Toning (Golden midtones, cool shadows, ivory highlights)
                val luma = 0.2126f * r + 0.7152f * g + 0.0722f * b

                // Midtone golden hour warmth (concentrated around skin tones 0.3 to 0.75)
                val midtoneWeight = (1.0f - abs(luma - 0.50f) * 2.2f).coerceIn(0.0f, 1.0f)
                r += 0.16f * temp * midtoneWeight
                g += 0.06f * temp * midtoneWeight
                b -= 0.12f * temp * midtoneWeight

                // Tint (green / magenta dye shift)
                g += 0.08f * tint * midtoneWeight
                r -= 0.04f * tint * midtoneWeight
                b -= 0.04f * tint * midtoneWeight

                // Shadow cooling (film dye bias in deep folds)
                val shadowWeight = (1.0f - luma * 3.0f).coerceIn(0.0f, 1.0f)
                r += shadowTintR * shadowWeight
                g += (shadowTintG + 0.02f * temp.coerceAtMost(0f)) * shadowWeight
                b += (shadowTintB - 0.04f * temp.coerceAtLeast(0f)) * shadowWeight

                // Highlight roll-off into warm ivory/cream
                if (luma > 0.75f && temp > 0.05f) {
                    val hiWeight = (luma - 0.75f) * 4.0f
                    r += 0.05f * temp * hiWeight
                    g += 0.03f * temp * hiWeight
                }

                // 3. Rich Film Saturation (Preserves skin chroma without neon clipping)
                val newLuma = 0.2126f * r + 0.7152f * g + 0.0722f * b
                r = newLuma + saturation * (r - newLuma)
                g = newLuma + saturation * (g - newLuma)
                b = newLuma + saturation * (b - newLuma)

                // 4. Halation (Film red glow around bright specular highlights)
                if (halationEnabled && origLuma > halationThreshold) {
                    val haloAmount = (origLuma - halationThreshold) / (1.0f - halationThreshold)
                    val halo = haloAmount * halationIntensity
                    r += 1.0f * halo
                    g += 0.22f * halo
                    b += 0.04f * halo
                }

                // 5. Volumetric Organic Light Leak Emulation
                if (leakEnabled) {
                    val leakDist = sqrt((x - width * 0.95f).pow(2) + (y - height * 0.05f).pow(2)) / maxDist
                    val leakMask = (1.0f - leakDist * 1.4f).coerceIn(0.0f, 1.0f)
                    val leakR = 1.0f * leakMask * leakIntensity
                    val leakG = 0.45f * leakMask * leakIntensity
                    val leakB = 0.08f * leakMask * leakIntensity
                    // Screen blend
                    r = 1.0f - (1.0f - r) * (1.0f - leakR)
                    g = 1.0f - (1.0f - g) * (1.0f - leakG)
                    b = 1.0f - (1.0f - b) * (1.0f - leakB)
                }

                // 6. Natural Lens Vignette (Cosine-fourth optical falloff)
                if (vigIntensity > 0.01f) {
                    val dx = x - centerX
                    val distNorm = (sqrt(dx * dx + dy2) / maxDist) / vigRadius
                    val vignette = (1.0f - vigIntensity * (distNorm * distNorm)).coerceIn(0.25f, 1.0f)
                    r *= vignette
                    g *= vignette
                    b *= vignette
                }

                // 7. Multi-Scale Organic Silver Halide Grain (Luminance masked, 2x2 clustered)
                if (grainEnabled) {
                    // Halide crystals cluster heavily in shadows and midtones, vanish in pure white
                    val grainMask = (4.0f * newLuma * (1.0f - newLuma)).coerceIn(0.0f, 1.0f)
                    // Clustered block noise
                    val cluster = (((x / 2 * 37 + y / 2 * 17 + (x * y) % 19) % 256) / 255.0f - 0.5f)
                    val micro = (random.nextFloat() - 0.5f)
                    val organicNoise = (cluster * 0.65f + micro * 0.35f) * grainMask * grainIntensity

                    if (isColorNoise) {
                        r += organicNoise
                        g += organicNoise * 0.8f + (random.nextFloat() - 0.5f) * grainMask * 0.04f
                        b += organicNoise * 1.1f + (random.nextFloat() - 0.5f) * grainMask * 0.05f
                    } else {
                        r += organicNoise
                        g += organicNoise
                        b += organicNoise
                    }
                }

                // 8. Camcorder Scanlines
                if (isCamcorder) {
                    val scanline = (sin(normY * 600.0 * PI) * 0.5 + 0.5).toFloat()
                    val scanFactor = 0.85f + 0.15f * scanline
                    r *= scanFactor
                    g *= scanFactor
                    b *= scanFactor
                }

                val ir = (r.coerceIn(0.0f, 1.0f) * 255.0f).toInt()
                val ig = (g.coerceIn(0.0f, 1.0f) * 255.0f).toInt()
                val ib = (b.coerceIn(0.0f, 1.0f) * 255.0f).toInt()

                pixels[index] = (0xFF shl 24) or (ir shl 16) or (ig shl 8) or ib
            }
        }

        val resultBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        resultBitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        return resultBitmap
    }

    /**
     * Applies Hurter & Driffield non-linear sensitometric film response curve.
     */
    private fun applyFilmicCurve(v: Float, contrast: Float, fade: Float, invGamma: Float, rollOff: Float): Float {
        var x = v.coerceIn(0.0f, 1.0f)

        // Gamma expansion
        if (invGamma != 1.0f) {
            x = x.pow(invGamma)
        }

        // Smooth cubic S-curve
        val s = if (x < 0.5f) {
            0.5f * (2.0f * x).pow(contrast)
        } else {
            1.0f - 0.5f * (2.0f * (1.0f - x)).pow(contrast)
        }

        // Highlight cushion / shoulder roll-off
        var out = s
        if (out > rollOff) {
            val delta = out - rollOff
            out = rollOff + delta * 0.45f
        }

        // Lifted blacks (milky film toe, never pure digital black)
        if (fade > 0.001f) {
            val liftedBlack = fade * 0.12f
            out = out * (1.0f - liftedBlack) + liftedBlack
        }

        return out.coerceIn(0.0f, 1.0f)
    }

    private fun saveNegativeToCache(context: Context, bitmap: Bitmap) {
        try {
            val cacheDir = File(context.cacheDir, "cameleo_negatives")
            if (!cacheDir.exists()) cacheDir.mkdirs()
            val file = File(cacheDir, "NEG_${System.currentTimeMillis()}.jpg")
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
            }
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
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 96, out)
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    contentValues.clear()
                    contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                    context.contentResolver.update(uri, contentValues, null, null)
                }

                // Write EXIF
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
