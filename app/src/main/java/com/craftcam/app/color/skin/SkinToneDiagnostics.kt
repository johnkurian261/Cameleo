package com.craftcam.app.color.skin

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import java.io.File
import java.io.FileOutputStream

/**
 * Developer Diagnostics & Stage Exporter for Skin Tone Intelligence (Section 17).
 *
 * Exports intermediate stages when developer diagnostics are enabled:
 * - 01_original_source.jpg
 * - 02_color_probability_mask.png (grayscale confidence: BLACK = 0.0, WHITE = 1.0)
 * - 03_spatial_anatomy_prior.png
 * - 04_refined_skin_mask.png
 * - 05_final_skin_render.jpg
 * - skin_tone_diagnostics.txt
 */
object SkinToneDiagnostics {

    private const val TAG = "SkinToneDiagnostics"

    fun exportDebugStages(
        context: Context?,
        sourceBmp: Bitmap,
        colorProb: FloatArray,
        spatialPrior: FloatArray,
        refinedMask: FloatArray,
        finalBmp: Bitmap,
        result: SkinProcessingResult
    ) {
        if (context == null) return
        try {
            val debugDir = File(context.getExternalFilesDir(null) ?: context.cacheDir, "skin_tone_debug")
            debugDir.mkdirs()

            val w = sourceBmp.width
            val h = sourceBmp.height

            // 1. Source image
            File(debugDir, "01_original_source.jpg").outputStream().use {
                sourceBmp.compress(Bitmap.CompressFormat.JPEG, 92, it)
            }

            // 2. Color Probability Mask (Grayscale)
            val colorBmp = createMaskBitmap(colorProb, w, h)
            File(debugDir, "02_color_probability_mask.png").outputStream().use {
                colorBmp.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            colorBmp.recycle()

            // 3. Spatial Anatomy Prior Mask (Grayscale)
            val spatialBmp = createMaskBitmap(spatialPrior, w, h)
            File(debugDir, "03_spatial_anatomy_prior.png").outputStream().use {
                spatialBmp.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            spatialBmp.recycle()

            // 4. Refined Skin Mask (Grayscale)
            val refinedBmp = createMaskBitmap(refinedMask, w, h)
            File(debugDir, "04_refined_skin_mask.png").outputStream().use {
                refinedBmp.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            refinedBmp.recycle()

            // 5. Final Rendered Image
            File(debugDir, "05_final_skin_render.jpg").outputStream().use {
                finalBmp.compress(Bitmap.CompressFormat.JPEG, 95, it)
            }

            // 6. Diagnostics Text Report
            File(debugDir, "skin_tone_diagnostics.txt").writeText(
                """
                === CAMELEO SKIN TONE INTELLIGENCE DIAGNOSTICS ===
                Active Profile: ${result.profileApplied.name} (${result.profileApplied.id})
                Estimated Illuminant: ${result.illuminant.name}
                Detected Faces Count: ${result.faceCount}
                Detected Skin Pixels: ${result.skinPixelCount} (${(result.skinPixelCount.toFloat() / (w * h) * 100f).toInt()}%)
                Average Skin Confidence: ${"%.3f".format(result.averageSkinConfidence)}
                Average Skin Luminance: ${"%.1f".format(result.averageSkinLuma)} / 255.0
                Target Hue Bias: ${result.profileApplied.targetHueBias}°
                Profile Skin Gains: [${result.profileApplied.skinGains.joinToString()}]
                Max Allowed Saturation: ${result.profileApplied.maxSkinSaturation}
                Skin Highlight Knee: ${result.profileApplied.skinHighlightKnee}
                Texture Dampening: ${result.profileApplied.textureDampening}
                """.trimIndent()
            )

            Log.i(TAG, "Successfully exported skin tone diagnostics to: ${debugDir.absolutePath}")
        } catch (t: Throwable) {
            Log.w(TAG, "Failed exporting skin tone debug stages", t)
        }
    }

    private fun createMaskBitmap(mask: FloatArray, width: Int, height: Int): Bitmap {
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(width * height)
        for (i in pixels.indices) {
            val v = (mask.getOrElse(i) { 0f }.coerceIn(0f, 1f) * 255f).toInt()
            pixels[i] = (0xFF shl 24) or (v shl 16) or (v shl 8) or v
        }
        bmp.setPixels(pixels, 0, width, 0, 0, width, height)
        return bmp
    }
}
