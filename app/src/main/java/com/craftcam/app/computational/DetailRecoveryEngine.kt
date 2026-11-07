package com.craftcam.app.computational

import android.graphics.Bitmap
import android.graphics.RectF
import com.craftcam.app.processor.IntArrayPool
import kotlin.math.*

/**
 * Extension point for hardware/ML face region providers.
 */
interface FaceRegionProvider {
    fun getFaceRegions(width: Int, height: Int): List<RectF>
}

/**
 * Default no-op face provider.
 */
object DefaultFaceRegionProvider : FaceRegionProvider {
    override fun getFaceRegions(width: Int, height: Int): List<RectF> = emptyList()
}

/**
 * Edge-Aware Detail Recovery and Restrained Microcontrast Engine for CAMELEO.
 * Restores natural micro-texture lost during optical capture and denoising without
 * generating white halo artifacts or accentuating human facial skin blemishes.
 */
object DetailRecoveryEngine {

    fun recoverDetail(
        source: Bitmap,
        microContrastGain: Float = 0.12f,
        skinDampening: Float = 0.60f,
        faceProvider: FaceRegionProvider = DefaultFaceRegionProvider
    ): Bitmap {
        if (microContrastGain <= 0.01f) return source

        val width = source.width
        val height = source.height
        val total = width * height

        val pixels = IntArrayPool.acquire(total)
        val outPixels = IntArrayPool.acquire(total)

        try {
            source.getPixels(pixels, 0, width, 0, 0, width, height)
            System.arraycopy(pixels, 0, outPixels, 0, total)

            val faceRects = faceProvider.getFaceRegions(width, height)
            val gain = microContrastGain.coerceIn(0.0f, 0.40f)

            // Noise threshold: ignore micro-variations below this level
            val noiseThreshold = 3.0f
            // Halo suppression threshold: clamp extreme contrast transitions
            val haloClamp = 35.0f

            for (y in 1 until height - 1) {
                val row = y * width
                val upRow = (y - 1) * width
                val downRow = (y + 1) * width

                for (x in 1 until width - 1) {
                    val idx = row + x
                    val p = pixels[idx]
                    val r = (p shr 16) and 0xFF
                    val g = (p shr 8) and 0xFF
                    val b = p and 0xFF

                    // Standard luminance Y
                    val yCenter = 0.299f * r + 0.587f * g + 0.114f * b

                    // 4-neighbor cross luminance
                    val pL = pixels[idx - 1]
                    val pR = pixels[idx + 1]
                    val pU = pixels[upRow + x]
                    val pD = pixels[downRow + x]

                    val yL = 0.299f * ((pL shr 16) and 0xFF) + 0.587f * ((pL shr 8) and 0xFF) + 0.114f * (pL and 0xFF)
                    val yR = 0.299f * ((pR shr 16) and 0xFF) + 0.587f * ((pR shr 8) and 0xFF) + 0.114f * (pR and 0xFF)
                    val yU = 0.299f * ((pU shr 16) and 0xFF) + 0.587f * ((pU shr 8) and 0xFF) + 0.114f * (pU and 0xFF)
                    val yD = 0.299f * ((pD shr 16) and 0xFF) + 0.587f * ((pD shr 8) and 0xFF) + 0.114f * (pD and 0xFF)

                    // Discrete 2D Laplacian high-pass signal: 4*C - (L + R + U + D)
                    val laplacian = 4.0f * yCenter - (yL + yR + yU + yD)
                    val absLap = abs(laplacian)

                    // Suppress sensor noise and clamp large contrast boundaries
                    if (absLap < noiseThreshold) continue
                    val clampedLap = (laplacian / absLap) * min(absLap - noiseThreshold, haloClamp)

                    // Skin-tone mask check (protect facial skin from harsh sharpening)
                    var isSkin = isSkinTone(r, g, b)
                    if (!isSkin && faceRects.isNotEmpty()) {
                        for (rect in faceRects) {
                            if (rect.contains(x.toFloat(), y.toFloat())) {
                                isSkin = true
                                break
                            }
                        }
                    }

                    val effectiveDampening = if (isSkin) (1.0f - skinDampening) else 1.0f
                    val delta = (clampedLap * gain * effectiveDampening).roundToInt()

                    if (delta != 0) {
                        val rOut = (r + delta).coerceIn(0, 255)
                        val gOut = (g + delta).coerceIn(0, 255)
                        val bOut = (b + delta).coerceIn(0, 255)
                        outPixels[idx] = (0xFF shl 24) or (rOut shl 16) or (gOut shl 8) or bOut
                    }
                }
            }

            source.setPixels(outPixels, 0, width, 0, 0, width, height)
            return source
        } finally {
            IntArrayPool.release(pixels)
            IntArrayPool.release(outPixels)
        }
    }

    /**
     * Calibrated RGB skin-tone detector in normalized chromaticity space.
     * Covers Fitzpatrick types I through VI while rejecting foliage and blue/gray tones.
     */
    private fun isSkinTone(r: Int, g: Int, b: Int): Boolean {
        if (r <= g || g <= b) return false
        val sum = (r + g + b).toFloat()
        if (sum < 60f) return false // Ignore deep black shadows

        val rNorm = r / sum
        val gNorm = g / sum

        // Skin chromaticity boundary
        return (rNorm in 0.36f..0.58f) && (gNorm in 0.26f..0.38f) && ((r - g) in 12..85)
    }
}

