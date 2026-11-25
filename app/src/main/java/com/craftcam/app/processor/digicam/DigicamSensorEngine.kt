package com.craftcam.app.processor.digicam

import kotlin.math.*

/**
 * Electronic CCD Sensor Noise and Virtual Resolution Engine (Phase 2 Upgrade).
 *
 * Implements:
 * 1. Physical Signal-Dependent Noise Model (Poisson-Gaussian variance):
 *    - Variance(Y) = a * Y + b, where 'a' represents photon shot noise gain and 'b' represents analog read noise.
 *    - Noise amplitude scales with ISO sensitivity (clean in bright sunlight, prominent in dim interiors).
 *    - Differentiates authentic 2000s CCD electronic noise from 35mm silver halide film grain.
 * 2. Spatially Correlated Chrominance Noise:
 *    - Simulates 2000s Bayer CFA demosaicing artifacts with correlated 2x2 chroma fluctuations rather
 *      than modern single-pixel salt-and-pepper noise.
 * 3. Virtual Sensor Resolution Downsampling / Upsampling:
 *    - Simulates ~5MP - 8MP compact camera optical MTF from modern 12MP sensors.
 */
class DigicamSensorEngine {

    companion object {
        // High-speed deterministic pseudo-random hash generator
        private fun hash(x: Int, y: Int, seed: Int): Float {
            var n = x * 374761393 + y * 668265263 + seed
            n = (n xor (n shr 13)) * 1274126177
            n = n xor (n shr 16)
            return (n and 0x7FFFFFFF).toFloat() / 0x7FFFFFFF.toFloat()
        }
    }

    /**
     * Injects signal-dependent Poisson-Gaussian CCD electronic noise into [pixels].
     *
     * @param pixels ARGB_8888 buffer (modified in-place).
     * @param width Image width.
     * @param height Image height.
     * @param iso Sensor sensitivity.
     * @param noiseMultiplier Profile multiplier.
     * @param seed Random seed for reproducible frame texture.
     */
    fun applyElectronicNoise(
        pixels: IntArray,
        width: Int,
        height: Int,
        iso: Int = 100,
        noiseMultiplier: Float = 1.0f,
        seed: Int = 42
    ) {
        if (noiseMultiplier <= 0.01f) return

        val total = width * height
        val isoNorm = (iso / 100f).coerceIn(0.8f, 16.0f)
        val isoScale = sqrt(isoNorm)

        // Poisson-Gaussian parameters:
        // Shot noise parameter 'a' scales with ISO gain
        val aShot = 0.0006f * isoScale * noiseMultiplier
        // Read noise floor 'b' is constant in dark regions
        val bRead = 0.00025f * noiseMultiplier

        val chromaScale = 0.45f

        for (y in 0 until height) {
            val rowOffset = y * width
            // 2x2 coordinate for Bayer demosaic spatial correlation
            val by = y shr 1

            for (x in 0 until width) {
                val idx = rowOffset + x
                val p = pixels[idx]
                val r = (p shr 16) and 0xFF
                val g = (p shr 8) and 0xFF
                val b = p and 0xFF

                val luma = (0.299f * r + 0.587f * g + 0.114f * b) / 255f

                // Poisson-Gaussian variance: Var(Y) = a * Y + b
                val variance = aShot * luma + bRead
                val sigma = sqrt(max(0.00001f, variance)) * 255f * 2.8f

                // High-frequency luminance random perturbation
                val randLuma = (hash(x, y, seed) - 0.5f) * 2f
                val deltaLuma = randLuma * sigma

                // Spatially correlated 2x2 chroma fluctuations (Bayer demosaicing signature)
                val bx = x shr 1
                val randChromaR = (hash(bx + 11, by + 17, seed + 101) - 0.5f) * 2f
                val randChromaB = (hash(bx + 23, by + 31, seed + 203) - 0.5f) * 2f

                val deltaChromaR = randChromaR * sigma * chromaScale
                val deltaChromaB = randChromaB * sigma * chromaScale

                val outR = (r + deltaLuma + deltaChromaR).roundToInt().coerceIn(0, 255)
                val outG = (g + deltaLuma).roundToInt().coerceIn(0, 255)
                val outB = (b + deltaLuma + deltaChromaB).roundToInt().coerceIn(0, 255)

                pixels[idx] = (0xFF shl 24) or (outR shl 16) or (outG shl 8) or outB
            }
        }
    }

    /**
     * Simulates 2000s compact camera optical MTF by applying anti-aliasing pre-filtering
     * to soften modern smartphone 12MP over-resolved micro-textures.
     */
    fun applyVirtualSensorPrefilter(
        pixels: IntArray,
        width: Int,
        height: Int,
        strength: Float = 0.20f
    ) {
        if (strength <= 0.01f) return
        val total = width * height
        val temp = pixels.clone()

        val w1 = strength * 0.12f
        val w0 = 1.0f - 4f * w1

        for (y in 1 until height - 1) {
            val rowOffset = y * width
            for (x in 1 until width - 1) {
                val idx = rowOffset + x
                val p = temp[idx]
                val pL = temp[idx - 1]
                val pR = temp[idx + 1]
                val pU = temp[idx - width]
                val pD = temp[idx + width]

                val r = (((p shr 16) and 0xFF) * w0 +
                        (((pL shr 16) and 0xFF) + ((pR shr 16) and 0xFF) +
                                ((pU shr 16) and 0xFF) + ((pD shr 16) and 0xFF)) * w1).roundToInt().coerceIn(0, 255)
                val g = (((p shr 8) and 0xFF) * w0 +
                        (((pL shr 8) and 0xFF) + ((pR shr 8) and 0xFF) +
                                ((pU shr 8) and 0xFF) + ((pD shr 8) and 0xFF)) * w1).roundToInt().coerceIn(0, 255)
                val b = ((p and 0xFF) * w0 +
                        ((pL and 0xFF) + (pR and 0xFF) +
                                (pU and 0xFF) + (pD and 0xFF)) * w1).roundToInt().coerceIn(0, 255)

                pixels[idx] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            }
        }
    }
}
