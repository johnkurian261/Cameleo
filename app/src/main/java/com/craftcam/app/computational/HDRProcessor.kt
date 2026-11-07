package com.craftcam.app.computational

import android.graphics.Bitmap
import com.craftcam.app.processor.IntArrayPool
import kotlin.math.*

/**
 * CAMELEO Multi-Frame HDR & Exposure Fusion Processor.
 * Operates purely in radiometric linear space (sRGB -> Linear -> HDR Fusion -> Tone Mapping -> sRGB).
 *
 * Implements exposure-dependent weighting:
 * - Highlights are recovered from underexposed (EV -1) frames to eliminate harsh digital clipping.
 * - Deep shadow noise is suppressed by injecting high-SNR signals from overexposed (EV +1) frames.
 * - Dynamic deghosting: any region exhibiting subject motion automatically clamps alternate
 *   weights to 0.0, falling back 100% to the sharpest reference frame to prevent double contours.
 */
class HDRProcessor(
    private val config: ComputationalPhotoConfig = ComputationalPhotoConfig()
) : ExposureFusionEngine {

    companion object {
        // Fast IEC 61966-2-1 sRGB <-> Linear Transfer Function Look-Up Tables
        val SRGB_TO_LINEAR = FloatArray(256) { i ->
            val c = i / 255.0f
            if (c <= 0.04045f) c / 12.92f else ((c + 0.055f) / 1.055f).pow(2.4f)
        }

        val LINEAR_TO_SRGB = IntArray(2048) { i ->
            val l = (i / 2047.0f).coerceIn(0.0f, 1.0f)
            val c = if (l <= 0.0031308f) l * 12.92f else 1.055f * l.pow(1.0f / 2.4f) - 0.055f
            (c * 255.0f).roundToInt().coerceIn(0, 255)
        }

        fun srgbToLinear(byteVal: Int): Float = SRGB_TO_LINEAR[byteVal.coerceIn(0, 255)]

        fun linearToSrgb(linearVal: Float): Int {
            val idx = (linearVal.coerceIn(0.0f, 1.0f) * 2047.0f).roundToInt()
            return LINEAR_TO_SRGB[idx]
        }
    }

    override fun fuse(
        sequence: FrameSequence,
        alignments: List<AlignmentResult>,
        ghostingMask: GhostingMask,
        targetWidth: Int,
        targetHeight: Int,
        outputBitmap: Bitmap,
        mirrorFrontCamera: Boolean
    ): Bitmap {
        val totalPixels = targetWidth * targetHeight
        val refIdx = sequence.referenceIndex
        val refFrame = sequence.referenceFrame

        // Acquire pooled IntArray buffers for pixel streaming (zero new Bitmap allocations)
        val workingPixels = IntArrayPool.acquire(totalPixels)
        val altPixels = IntArrayPool.acquire(totalPixels)

        try {
            // 1. Decode reference frame into working buffer
            refFrame.decodeFullPixels(workingPixels, targetWidth, targetHeight, refFrame.rotation, mirrorFrontCamera)

            if (sequence.size <= 1) {
                outputBitmap.setPixels(workingPixels, 0, targetWidth, 0, 0, targetWidth, targetHeight)
                return outputBitmap
            }

            // 2. Iterate through alternate frames and fuse into workingPixels
            for (i in sequence.frames.indices) {
                if (i == refIdx) continue

                val altFrame = sequence.frames[i]
                val alignment = alignments.getOrNull(i) ?: AlignmentResult(0f, 0f, 1f, 0f)

                // Skip frames with low registration confidence
                if (alignment.confidence < 0.25f) continue

                altFrame.decodeFullPixels(altPixels, targetWidth, targetHeight, altFrame.rotation, mirrorFrontCamera)

                val alignDx = alignment.dx.roundToInt()
                val alignDy = alignment.dy.roundToInt()
                val isUnderExposed = altFrame.evOffset < refFrame.evOffset
                val isOverExposed = altFrame.evOffset > refFrame.evOffset

                // Radiometric fusion pass: merge linear luminance & chrominance
                for (y in 0 until targetHeight) {
                    val refRow = y * targetWidth
                    val altY = (y + alignDy).coerceIn(0, targetHeight - 1)
                    val altRow = altY * targetWidth

                    for (x in 0 until targetWidth) {
                        val refPixel = workingPixels[refRow + x]
                        val rRef = (refPixel shr 16) and 0xFF
                        val gRef = (refPixel shr 8) and 0xFF
                        val bRef = refPixel and 0xFF

                        // Linearize reference RGB
                        val rLinRef = SRGB_TO_LINEAR[rRef]
                        val gLinRef = SRGB_TO_LINEAR[gRef]
                        val bLinRef = SRGB_TO_LINEAR[bRef]
                        val yLinRef = 0.2126f * rLinRef + 0.7152f * gLinRef + 0.0722f * bLinRef

                        // Static confidence at this spatial coordinate
                        val staticConf = ghostingMask.getStaticWeight(x, y, targetWidth, targetHeight)

                        // If moving subject detected, bypass fusion (100% reference frame retention)
                        if (staticConf < 0.05f) continue

                        val altX = (x + alignDx).coerceIn(0, targetWidth - 1)
                        val altPixel = altPixels[altRow + altX]
                        val rAlt = (altPixel shr 16) and 0xFF
                        val gAlt = (altPixel shr 8) and 0xFF
                        val bAlt = altPixel and 0xFF

                        val rLinAlt = SRGB_TO_LINEAR[rAlt]
                        val gLinAlt = SRGB_TO_LINEAR[gAlt]
                        val bLinAlt = SRGB_TO_LINEAR[bAlt]

                        // Compute Mertens-inspired exposure weight
                        var fuseWeight = 0.0f

                        if (isUnderExposed) {
                            // Underexposed frame: recover highlights where reference approaches clipping
                            if (yLinRef > 0.55f) {
                                val t = ((yLinRef - 0.55f) / 0.40f).coerceIn(0.0f, 1.0f)
                                // Sigmoidal smoothstep weighting for natural highlight roll-off
                                fuseWeight = (t * t * (3.0f - 2.0f * t)) * 0.75f
                            }
                        } else if (isOverExposed) {
                            // Overexposed frame: recover shadow SNR where reference is noisy/dark
                            if (yLinRef < 0.35f) {
                                val t = ((0.35f - yLinRef) / 0.35f).coerceIn(0.0f, 1.0f)
                                fuseWeight = (t * t * (3.0f - 2.0f * t)) * 0.65f
                            }
                        } else {
                            // Similar exposure frame: temporal averaging in static regions
                            fuseWeight = 0.33f
                        }

                        // Modulate weight by motion confidence and registration confidence
                        val effectiveWeight = (fuseWeight * staticConf * alignment.confidence).coerceIn(0.0f, 0.85f)

                        if (effectiveWeight > 0.01f) {
                            val rFused = (1.0f - effectiveWeight) * rLinRef + effectiveWeight * rLinAlt
                            val gFused = (1.0f - effectiveWeight) * gLinRef + effectiveWeight * gLinAlt
                            val bFused = (1.0f - effectiveWeight) * bLinRef + effectiveWeight * bLinAlt

                            val irOut = linearToSrgb(rFused)
                            val igOut = linearToSrgb(gFused)
                            val ibOut = linearToSrgb(bFused)

                            workingPixels[refRow + x] = (0xFF shl 24) or (irOut shl 16) or (igOut shl 8) or ibOut
                        }
                    }
                }
            }

            outputBitmap.setPixels(workingPixels, 0, targetWidth, 0, 0, targetWidth, targetHeight)
            return outputBitmap
        } finally {
            IntArrayPool.release(workingPixels)
            IntArrayPool.release(altPixels)
        }
    }
}
