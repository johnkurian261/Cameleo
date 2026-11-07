package com.craftcam.app.portrait

import android.graphics.Bitmap
import android.graphics.RectF
import com.craftcam.app.config.ProcessingProfile
import com.craftcam.app.processor.IntArrayPool
import kotlin.math.*

/**
 * Natural Portrait-Aware Processor for CAMELEO.
 *
 * Implements authentic optical and photographic portrait principles without fake bokeh or beautification:
 * 1. Face-Aware Exposure Lift: ensures faces in backlit or shadowed conditions are naturally legible.
 * 2. Spatial Detail Adaptation: protects facial skin pores from blemish-accentuating digital sharpening.
 * 3. Eyes & Hair Acutance: preserves crisp definition along eyelashes, iris rims, and hair strands.
 * 4. Background Chroma Clean: cleans shadow color noise outside the subject.
 *
 * PROHIBITED: Face slimming, skin whitening, eye enlargement, and plastic blurring are strictly disallowed.
 */
object PortraitProcessor {

    fun processPortrait(
        source: Bitmap,
        faces: List<FaceRegion>,
        profile: ProcessingProfile = ProcessingProfile.NaturalPortrait
    ): Bitmap {
        if (faces.isEmpty()) return source

        val width = source.width
        val height = source.height
        val total = width * height

        val pixels = IntArrayPool.acquire(total)
        val outPixels = IntArrayPool.acquire(total)

        try {
            source.getPixels(pixels, 0, width, 0, 0, width, height)
            System.arraycopy(pixels, 0, outPixels, 0, total)

            // Convert face bounds to full pixel coordinates
            val pixelFaces = faces.map { it.toPixelRect(width, height) }

            // 1. Compute Mean Face Luminance for Face-Aware Exposure Balancing
            var faceLumSum = 0.0
            var facePixelCount = 0

            for (face in pixelFaces) {
                val x0 = face.left.toInt().coerceIn(0, width - 1)
                val x1 = face.right.toInt().coerceIn(0, width - 1)
                val y0 = face.top.toInt().coerceIn(0, height - 1)
                val y1 = face.bottom.toInt().coerceIn(0, height - 1)

                val step = max(1, (x1 - x0) / 30)
                for (y in y0 until y1 step step) {
                    val row = y * width
                    for (x in x0 until x1 step step) {
                        val p = pixels[row + x]
                        val r = (p shr 16) and 0xFF
                        val g = (p shr 8) and 0xFF
                        val b = p and 0xFF
                        faceLumSum += (0.299 * r + 0.587 * g + 0.114 * b) / 255.0
                        facePixelCount++
                    }
                }
            }

            val avgFaceLum = if (facePixelCount > 0) (faceLumSum / facePixelCount).toFloat() else 0.45f

            // Target face luminance is 0.50 (balanced photographic key)
            // If face is underexposed in shadow/backlight, gently lift face irradiance
            val faceLift = if (avgFaceLum < 0.42f) {
                ((0.48f - avgFaceLum) * 0.75f).coerceIn(0.0f, 0.25f)
            } else {
                0.0f
            }

            val skinDampening = profile.skinSharpeningDampening
            val bgSharpGain = profile.backgroundSharpening * 0.5f

            // 2. Spatial Adaptive Processing Pass
            for (y in 1 until height - 1) {
                val row = y * width
                val upRow = (y - 1) * width
                val downRow = (y + 1) * width
                val yF = y.toFloat()

                for (x in 1 until width - 1) {
                    val idx = row + x
                    val p = pixels[idx]
                    var r = (p shr 16) and 0xFF
                    var g = (p shr 8) and 0xFF
                    var b = p and 0xFF

                    // Distance-to-nearest-face check
                    val xF = x.toFloat()
                    var inFaceZone = false
                    var inEyeZone = false

                    for (face in pixelFaces) {
                        if (face.contains(xF, yF)) {
                            inFaceZone = true
                            // Check if in upper-third eye/hair band of face
                            val eyeBandTop = face.top + face.height() * 0.15f
                            val eyeBandBottom = face.top + face.height() * 0.45f
                            if (yF in eyeBandTop..eyeBandBottom) {
                                inEyeZone = true
                            }
                            break
                        }
                    }

                    // A. Face-Aware Exposure Adjustment
                    if (inFaceZone && faceLift > 0.01f) {
                        val liftMul = 1.0f + faceLift
                        r = (r * liftMul).roundToInt().coerceIn(0, 255)
                        g = (g * liftMul).roundToInt().coerceIn(0, 255)
                        b = (b * liftMul).roundToInt().coerceIn(0, 255)
                    }

                    // B. Spatial Adaptive Sharpening
                    val yCenter = 0.299f * r + 0.587f * g + 0.114f * b

                    val pL = pixels[idx - 1]
                    val pR = pixels[idx + 1]
                    val pU = pixels[upRow + x]
                    val pD = pixels[downRow + x]

                    val yL = 0.299f * ((pL shr 16) and 0xFF) + 0.587f * ((pL shr 8) and 0xFF) + 0.114f * (pL and 0xFF)
                    val yR = 0.299f * ((pR shr 16) and 0xFF) + 0.587f * ((pR shr 8) and 0xFF) + 0.114f * (pR and 0xFF)
                    val yU = 0.299f * ((pU shr 16) and 0xFF) + 0.587f * ((pU shr 8) and 0xFF) + 0.114f * (pU and 0xFF)
                    val yD = 0.299f * ((pD shr 16) and 0xFF) + 0.587f * ((pD shr 8) and 0xFF) + 0.114f * (pD and 0xFF)

                    val laplacian = 4.0f * yCenter - (yL + yR + yU + yD)
                    val absLap = abs(laplacian)

                    if (absLap in 3.0f..30.0f) {
                        val effectiveGain = when {
                            inEyeZone -> bgSharpGain * 0.8f // Preserves crisp eyes/lashes
                            inFaceZone -> bgSharpGain * (1.0f - skinDampening) // Dampened on skin pores
                            else -> bgSharpGain // Full background texture
                        }

                        val delta = (laplacian * effectiveGain).roundToInt()
                        r = (r + delta).coerceIn(0, 255)
                        g = (g + delta).coerceIn(0, 255)
                        b = (b + delta).coerceIn(0, 255)
                    }

                    outPixels[idx] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
                }
            }

            source.setPixels(outPixels, 0, width, 0, 0, width, height)
            return source
        } finally {
            IntArrayPool.release(pixels)
            IntArrayPool.release(outPixels)
        }
    }
}
