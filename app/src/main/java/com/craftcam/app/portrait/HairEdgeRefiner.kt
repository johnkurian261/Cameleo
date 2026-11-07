package com.craftcam.app.portrait

import android.graphics.Bitmap
import android.graphics.RectF
import kotlin.math.*

/**
 * Hair Boundary Refiner for CAMELEO Portrait Engine.
 *
 * Specializes in photographic hair contour preservation:
 * 1. Analyzes high-frequency hair strand textures along head perimeters.
 * 2. Samples hair color core vs surrounding background luminance.
 * 3. Applies localized alpha matting to replace harsh binary cutouts with soft natural hair roll-off.
 * 4. Prevents halo artifacts around fine curly, straight, or loose hair flyaways.
 */
class HairEdgeRefiner {

    fun refineHairEdges(
        mask: SubjectMask,
        source: Bitmap,
        faces: List<FaceRegion>,
        depthMap: DepthMap
    ): SubjectMask {
        if (faces.isEmpty()) return mask

        val width = mask.width
        val height = mask.height
        val total = width * height

        // Scale bitmap to mask dimensions if needed
        val scaled = if (source.width == width && source.height == height) {
            source
        } else {
            Bitmap.createScaledBitmap(source, width, height, true)
        }

        val pixels = IntArray(total)
        scaled.getPixels(pixels, 0, width, 0, 0, width, height)

        val refinedConfidence = FloatArray(total)
        System.arraycopy(mask.confidence, 0, refinedConfidence, 0, total)

        val luma = FloatArray(total)
        for (i in 0 until total) {
            val p = pixels[i]
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            luma[i] = (0.299f * r + 0.587f * g + 0.114f * b) / 255f
        }

        for (face in faces) {
            val pixelFace = face.toPixelRect(width, height)
            val faceW = pixelFace.width()
            val faceH = pixelFace.height()

            // Define hair analysis zone: above face and around upper ears/temples
            val hairZone = RectF(
                pixelFace.left - faceW * 0.35f,
                pixelFace.top - faceH * 0.45f,
                pixelFace.right + faceW * 0.35f,
                pixelFace.top + faceH * 0.40f
            )

            val x0 = hairZone.left.toInt().coerceIn(1, width - 2)
            val x1 = hairZone.right.toInt().coerceIn(1, width - 2)
            val y0 = hairZone.top.toInt().coerceIn(1, height - 2)
            val y1 = hairZone.bottom.toInt().coerceIn(1, height - 2)

            // 1. Sample hair core color (top 15% of face bounding box)
            var hairLumaSum = 0f
            var hairCount = 0
            val hx0 = (pixelFace.left + faceW * 0.25f).toInt().coerceIn(0, width - 1)
            val hx1 = (pixelFace.right - faceW * 0.25f).toInt().coerceIn(0, width - 1)
            val hy0 = (pixelFace.top - faceH * 0.15f).toInt().coerceIn(0, height - 1)
            val hy1 = (pixelFace.top + faceH * 0.10f).toInt().coerceIn(0, height - 1)

            for (y in hy0..hy1) {
                val row = y * width
                for (x in hx0..hx1) {
                    hairLumaSum += luma[row + x]
                    hairCount++
                }
            }
            val refHairLuma = if (hairCount > 0) hairLumaSum / hairCount else 0.20f

            // 2. Sample background reference outside the hair zone
            var bgLumaSum = 0f
            var bgCount = 0
            val bgY0 = (hairZone.top - faceH * 0.15f).toInt().coerceIn(0, height - 1)
            val bgY1 = hairZone.top.toInt().coerceIn(0, height - 1)

            for (y in bgY0 until bgY1) {
                val row = y * width
                for (x in x0..x1) {
                    bgLumaSum += luma[row + x]
                    bgCount++
                }
            }
            val refBgLuma = if (bgCount > 0) bgLumaSum / bgCount else 0.70f

            // 3. Local Alpha Matting within Hair Transition Periphery
            for (y in y0..y1) {
                val row = y * width
                val upRow = (y - 1) * width
                val downRow = (y + 1) * width

                for (x in x0..x1) {
                    val idx = row + x
                    val conf = refinedConfidence[idx]

                    // Apply refinement only in ambiguous boundary zones
                    if (conf in 0.10f..0.85f) {
                        val currentLuma = luma[idx]

                        // Local edge texture energy (Laplacian variance)
                        val laplacian = abs(
                            4.0f * currentLuma -
                            (luma[idx - 1] + luma[idx + 1] + luma[upRow + x] + luma[downRow + x])
                        )

                        // Alpha matting estimate based on luma distance to hair vs background
                        val lumaSpan = abs(refHairLuma - refBgLuma).coerceAtLeast(0.12f)
                        val hairMatte = (1.0f - abs(currentLuma - refHairLuma) / lumaSpan).coerceIn(0f, 1f)

                        // If high-frequency texture is present (fine strands), reinforce matte
                        val textureBoost = (laplacian * 3.5f).coerceIn(0f, 0.35f)
                        val softAlpha = (hairMatte * 0.65f + conf * 0.35f + textureBoost).coerceIn(0f, 1f)

                        // Depth consistency check
                        val depthVal = depthMap.values[idx]
                        val depthFalloff = if (depthVal > 0.45f) (1.0f - (depthVal - 0.45f) * 2.0f).coerceIn(0f, 1f) else 1.0f

                        refinedConfidence[idx] = softAlpha * depthFalloff
                    }
                }
            }
        }

        if (scaled != source) {
            scaled.recycle()
        }

        return SubjectMask(width, height, refinedConfidence)
    }
}

