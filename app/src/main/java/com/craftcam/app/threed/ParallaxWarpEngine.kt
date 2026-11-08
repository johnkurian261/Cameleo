package com.craftcam.app.threed

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import kotlin.math.*

/**
 * Depth-aware horizontal parallax warping engine with robust occlusion handling.
 *
 * Implements:
 * 1. Depth-dependent horizontal disparity: foreground moves with high amplitude,
 *    subject plane acts as optical pivot, background exhibits subtle counter-parallax.
 * 2. Continuous sinusoidal oscillation: P(t) = sin(t) ensuring seamless looping
 *    (Center -> Right -> Center -> Left -> Center).
 * 3. Z-buffered forward projection + horizontal background bilateral hole inpainting
 *    to eliminate rubber-sheet stretching, black gaps, and edge tearing.
 */
class ParallaxWarpEngine(private val config: ThreeDConfig = ThreeDConfig()) {

    /**
     * Warps [sourceBitmap] into [frameCount] distinct stereoscopic viewpoint frames
     * using the estimated [disparityMap].
     *
     * @return List of generated viewpoint Bitmaps.
     */
    fun generateParallaxFrames(
        sourceBitmap: Bitmap,
        disparityMap: DisparityMap
    ): List<Bitmap> {
        val width = sourceBitmap.width
        val height = sourceBitmap.height
        val totalPixels = width * height

        val srcPixels = IntArray(totalPixels)
        sourceBitmap.getPixels(srcPixels, 0, width, 0, 0, width, height)

        // Resample disparity map to match sourceBitmap dimensions
        val dispGrid = FloatArray(totalPixels)
        val dispW = disparityMap.width
        val dispH = disparityMap.height
        val xRatio = (dispW - 1).toFloat() / (width - 1).coerceAtLeast(1)
        val yRatio = (dispH - 1).toFloat() / (height - 1).coerceAtLeast(1)

        for (y in 0 until height) {
            val srcY = y * yRatio
            val y0 = floor(srcY).toInt()
            val y1 = (y0 + 1).coerceAtMost(dispH - 1)
            val ty = srcY - y0
            val rowOffset = y * width

            for (x in 0 until width) {
                val srcX = x * xRatio
                val x0 = floor(srcX).toInt()
                val x1 = (x0 + 1).coerceAtMost(dispW - 1)
                val tx = srcX - x0

                val d00 = disparityMap.values[y0 * dispW + x0]
                val d10 = disparityMap.values[y0 * dispW + x1]
                val d01 = disparityMap.values[y1 * dispW + x0]
                val d11 = disparityMap.values[y1 * dispW + x1]

                val top = d00 * (1f - tx) + d10 * tx
                val bottom = d01 * (1f - tx) + d11 * tx
                dispGrid[rowOffset + x] = top * (1f - ty) + bottom * ty
            }
        }

        val maxDisplacementPx = width * config.maxParallaxDisplacement
        val pivot = config.pivotDisparity
        val frames = ArrayList<Bitmap>(config.frameCount)

        // Generate each frame across the sinusoidal cycle
        for (frameIdx in 0 until config.frameCount) {
            val t = (2.0 * Math.PI * frameIdx / config.frameCount).toFloat()
            // Sinusoidal oscillation: 0 -> +1 -> 0 -> -1 -> 0
            val phase = sin(t)
            val frameDisplacement = maxDisplacementPx * phase

            val outPixels = IntArray(totalPixels)
            val zBuffer = FloatArray(totalPixels) { -1.0f }

            // 1. Z-Buffered Forward Projection
            for (y in 0 until height) {
                val rowOffset = y * width
                for (x in 0 until width) {
                    val idx = rowOffset + x
                    val d = dispGrid[idx]
                    val pixelColor = srcPixels[idx]

                    // Differential disparity shift relative to convergence pivot
                    // Foreground (d > pivot) moves forward; background (d < pivot) moves counter
                    val dx = (d - pivot) * frameDisplacement
                    val targetX = (x + dx).roundToInt()

                    if (targetX in 0 until width) {
                        val targetIdx = rowOffset + targetX
                        // Closer objects (higher disparity) occlude further objects
                        if (d >= zBuffer[targetIdx]) {
                            zBuffer[targetIdx] = d
                            outPixels[targetIdx] = pixelColor
                        }
                    }
                }
            }

            // 2. Scanline Bilateral Hole Inpainting (Occlusion Fill)
            // Disoccluded gaps revealed by moving foreground are filled by extending the background plane
            for (y in 0 until height) {
                val rowOffset = y * width
                var x = 0
                while (x < width) {
                    val idx = rowOffset + x
                    if (zBuffer[idx] < 0f) {
                        // Found a hole start
                        val holeStart = x
                        while (x < width && zBuffer[rowOffset + x] < 0f) {
                            x++
                        }
                        val holeEnd = x - 1

                        // Find background donor pixels on the left and right of the hole
                        val leftColor = if (holeStart > 0) outPixels[rowOffset + holeStart - 1] else 0
                        val rightColor = if (holeEnd < width - 1) outPixels[rowOffset + holeEnd + 1] else 0

                        val leftValid = holeStart > 0
                        val rightValid = holeEnd < width - 1

                        // Fill the hole with smooth background interpolation
                        val holeLen = (holeEnd - holeStart + 1).coerceAtLeast(1)
                        for (hx in holeStart..holeEnd) {
                            val targetIdx = rowOffset + hx
                            val filled = when {
                                leftValid && rightValid -> {
                                    val factor = (hx - holeStart).toFloat() / holeLen
                                    interpolateColor(leftColor, rightColor, factor)
                                }
                                leftValid -> leftColor
                                rightValid -> rightColor
                                else -> 0xFF000000.toInt()
                            }
                            outPixels[targetIdx] = filled
                        }
                    } else {
                        x++
                    }
                }
            }

            val frameBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            frameBitmap.setPixels(outPixels, 0, width, 0, 0, width, height)
            frames.add(frameBitmap)
        }

        return frames
    }

    private fun interpolateColor(c1: Int, c2: Int, t: Float): Int {
        val a1 = (c1 shr 24) and 0xFF
        val r1 = (c1 shr 16) and 0xFF
        val g1 = (c1 shr 8) and 0xFF
        val b1 = c1 and 0xFF

        val a2 = (c2 shr 24) and 0xFF
        val r2 = (c2 shr 16) and 0xFF
        val g2 = (c2 shr 8) and 0xFF
        val b2 = c2 and 0xFF

        val a = (a1 + (a2 - a1) * t).roundToInt().coerceIn(0, 255)
        val r = (r1 + (r2 - r1) * t).roundToInt().coerceIn(0, 255)
        val g = (g1 + (g2 - g1) * t).roundToInt().coerceIn(0, 255)
        val b = (b1 + (b2 - b1) * t).roundToInt().coerceIn(0, 255)

        return (a shl 24) or (r shl 16) or (g shl 8) or b
    }
}

