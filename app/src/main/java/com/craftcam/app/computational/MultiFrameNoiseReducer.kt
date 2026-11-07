package com.craftcam.app.computational

import android.graphics.Bitmap
import com.craftcam.app.processor.IntArrayPool
import kotlin.math.*

/**
 * Adaptive Multi-Frame & Spatial Noise Reducer for CAMELEO.
 * Modulates filtering power based on sensor ISO, motion confidence, and local edge gradients:
 * - High ISO flat shadows & skies receive thorough chroma blotch reduction.
 * - Edges, fine hair, foliage, and skin textures are protected via gradient thresholding.
 * - Moving subjects receive zero spatial smearing to preserve edge crispness.
 */
object MultiFrameNoiseReducer {

    fun reduceNoise(
        source: Bitmap,
        sensorIso: Int,
        ghostingMask: GhostingMask?,
        adaptiveStrength: Float = 1.0f
    ): Bitmap {
        // At low sensor ISO (<200), camera sensor has clean SNR; minimize post-processing
        if (sensorIso < 200 && adaptiveStrength < 0.2f) return source

        val width = source.width
        val height = source.height
        val total = width * height

        val pixels = IntArrayPool.acquire(total)
        val tempPixels = IntArrayPool.acquire(total)

        try {
            source.getPixels(pixels, 0, width, 0, 0, width, height)
            System.arraycopy(pixels, 0, tempPixels, 0, total)

            // ISO-dependent filtering radius & edge threshold
            val isoNorm = (sensorIso.coerceIn(50, 3200) / 1600.0f).coerceIn(0.1f, 2.0f)
            val edgeThreshold = (32.0f / isoNorm).coerceIn(12.0f, 40.0f)
            val filterWeight = (0.35f * isoNorm * adaptiveStrength).coerceIn(0.1f, 0.75f)

            // Horizontal & vertical edge-preserving bilateral-style chroma/luma pass
            val step = 1
            for (y in 1 until height - 1 step step) {
                val row = y * width
                for (x in 1 until width - 1 step step) {
                    val idx = row + x

                    // Static weight from ghosting detector
                    val staticWeight = ghostingMask?.getStaticWeight(x, y, width, height) ?: 1.0f
                    if (staticWeight < 0.2f) continue // Skip moving regions to prevent motion blur

                    val c = pixels[idx]
                    val cr = (c shr 16) and 0xFF
                    val cg = (c shr 8) and 0xFF
                    val cb = c and 0xFF
                    val cy = 0.299f * cr + 0.587f * cg + 0.114f * cb

                    // Check horizontal and vertical gradients
                    val left = pixels[idx - 1]
                    val right = pixels[idx + 1]
                    val up = pixels[idx - width]
                    val down = pixels[idx + width]

                    val yL = 0.299f * ((left shr 16) and 0xFF) + 0.587f * ((left shr 8) and 0xFF) + 0.114f * (left and 0xFF)
                    val yR = 0.299f * ((right shr 16) and 0xFF) + 0.587f * ((right shr 8) and 0xFF) + 0.114f * (right and 0xFF)
                    val yU = 0.299f * ((up shr 16) and 0xFF) + 0.587f * ((up shr 8) and 0xFF) + 0.114f * (up and 0xFF)
                    val yD = 0.299f * ((down shr 16) and 0xFF) + 0.587f * ((down shr 8) and 0xFF) + 0.114f * (down and 0xFF)

                    val gradH = abs(yR - yL)
                    val gradV = abs(yD - yU)
                    val maxGrad = max(gradH, gradV)

                    // If sharp edge detected (texture, hair, boundary), do not filter
                    if (maxGrad > edgeThreshold) continue

                    // In low-gradient regions (shadows, flat surfaces), smooth chroma and high-frequency noise
                    val rAvg = (((left shr 16) and 0xFF) + ((right shr 16) and 0xFF) + ((up shr 16) and 0xFF) + ((down shr 16) and 0xFF)) * 0.25f
                    val gAvg = (((left shr 8) and 0xFF) + ((right shr 8) and 0xFF) + ((up shr 8) and 0xFF) + ((down shr 8) and 0xFF)) * 0.25f
                    val bAvg = ((left and 0xFF) + (right and 0xFF) + (up and 0xFF) + (down and 0xFF)) * 0.25f

                    val edgeDamp = (1.0f - (maxGrad / edgeThreshold)).coerceIn(0.0f, 1.0f)
                    val blend = filterWeight * edgeDamp * staticWeight

                    val rOut = ((1.0f - blend) * cr + blend * rAvg).roundToInt().coerceIn(0, 255)
                    val gOut = ((1.0f - blend) * cg + blend * gAvg).roundToInt().coerceIn(0, 255)
                    val bOut = ((1.0f - blend) * cb + blend * bAvg).roundToInt().coerceIn(0, 255)

                    tempPixels[idx] = (0xFF shl 24) or (rOut shl 16) or (gOut shl 8) or bOut
                }
            }

            source.setPixels(tempPixels, 0, width, 0, 0, width, height)
            return source
        } finally {
            IntArrayPool.release(pixels)
            IntArrayPool.release(tempPixels)
        }
    }
}

