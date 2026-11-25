package com.craftcam.app.color.skin

import android.graphics.RectF
import com.craftcam.app.portrait.FaceRegion
import kotlin.math.*

/**
 * Layer 3: Anatomical Spatial Prior & Confidence-Aware Fusion Engine.
 *
 * Combines hardware face localization with human anatomical proportions to solve the
 * primary weakness of colour-based skin detection: false positives on background objects
 * (wooden furniture, teak floors, terracotta walls, leather jackets, food, and clothing).
 *
 * Anatomical Proportions & Rules:
 * 1. Facial Core: Confirmed face bounds have 100% spatial confidence.
 * 2. Neck & Collarbone: Extends vertically below the face (0.70× face height).
 * 3. Torso & Upper Arms: Extends laterally and downward with smooth decay.
 * 4. Ambient Background: Suppressed by ~80% when humans are present in the scene.
 * 5. Conservative Headless Fallback: When no faces are detected (e.g. photos of hands or objects),
 *    the spatial prior returns a conservative baseline (0.50f) rather than a naive 1.0f,
 *    preventing background wood and amber rooms from receiving 100% spatial confidence.
 * 6. High-Confidence Skin Protection: Pixels with very high multi-space color probability (c > 0.65f)
 *    on arms and hands retain protective coverage even outside the facial bounding box.
 */
class SkinAnatomyPrior {

    companion object {
        private fun smoothstep(edge0: Float, edge1: Float, x: Float): Float {
            val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0.0f, 1.0f)
            return t * t * (3.0f - 2.0f * t)
        }
    }

    /**
     * Generates a spatial confidence prior map [0.0f, 1.0f] for [width] x [height].
     *
     * @param width Image width.
     * @param height Image height.
     * @param faces List of detected faces (normalized [0.0, 1.0]).
     * @param backgroundSuppression Suppression factor for non-human regions (default 0.80).
     * @return Spatial prior FloatArray in [0.0f, 1.0f].
     */
    fun generateSpatialPrior(
        width: Int,
        height: Int,
        faces: List<FaceRegion>,
        backgroundSuppression: Float = 0.80f
    ): FloatArray {
        val total = width * height
        val prior = FloatArray(total)

        // Conservative Headless Fallback:
        // When no faces are detected (e.g. headless photos, arms/legs, or still life),
        // use a conservative baseline (~0.50) with subtle center weighting,
        // rather than 1.0 everywhere which allowed background wood to pass unchecked.
        if (faces.isEmpty()) {
            val invW = if (width > 1) 1.0f / (width - 1) else 1.0f
            val invH = if (height > 1) 1.0f / (height - 1) else 1.0f
            for (y in 0 until height) {
                val ny = y * invH - 0.5f
                val row = y * width
                for (x in 0 until width) {
                    val nx = x * invW - 0.5f
                    val distFromCenter = sqrt(nx * nx + ny * ny) * 1.414f // [0.0, 1.0]
                    // Center-weighted conservative prior in [0.40, 0.55]
                    prior[row + x] = (0.55f - distFromCenter * 0.10f).coerceIn(0.40f, 0.55f)
                }
            }
            return prior
        }

        val baseAmbient = (1.0f - backgroundSuppression).coerceIn(0.10f, 0.40f)
        prior.fill(baseAmbient)

        // Pure coordinate box independent of Android framework stubs for JVM unit test parity
        data class Box(val left: Float, val top: Float, val right: Float, val bottom: Float) {
            val width: Float get() = max(0f, right - left)
            val height: Float get() = max(0f, bottom - top)
            fun contains(x: Float, y: Float): Boolean = x in left..right && y in top..bottom
        }

        data class AnatomyZones(
            val face: Box,
            val neck: Box,
            val torso: Box
        )

        val zones = faces.map { f ->
            val fLeft = f.bounds.left * width
            val fTop = f.bounds.top * height
            val fRight = f.bounds.right * width
            val fBottom = f.bounds.bottom * height
            val fw = max(1f, fRight - fLeft)
            val fh = max(1f, fBottom - fTop)

            val faceBox = Box(fLeft, fTop, fRight, fBottom)
            val neckBox = Box(
                (fLeft - fw * 0.15f).coerceAtLeast(0f),
                fBottom,
                (fRight + fw * 0.15f).coerceAtMost(width.toFloat()),
                (fBottom + fh * 0.70f).coerceAtMost(height.toFloat())
            )
            val torsoBox = Box(
                (fLeft - fw * 0.65f).coerceAtLeast(0f),
                fBottom + fh * 0.35f,
                (fRight + fw * 0.65f).coerceAtMost(width.toFloat()),
                (fBottom + fh * 2.2f).coerceAtMost(height.toFloat())
            )

            AnatomyZones(faceBox, neckBox, torsoBox)
        }

        for (y in 0 until height) {
            val row = y * width
            val yF = y.toFloat()

            for (x in 0 until width) {
                val idx = row + x
                val xF = x.toFloat()

                var maxSpatial = baseAmbient

                for (z in zones) {
                    // A. Facial core (highest priority)
                    if (z.face.contains(xF, yF)) {
                        maxSpatial = max(maxSpatial, 1.0f)
                        continue
                    }

                    // B. Neck and collarbone
                    if (z.neck.contains(xF, yF)) {
                        val decay = 1.0f - ((yF - z.neck.top) / z.neck.height) * 0.20f
                        maxSpatial = max(maxSpatial, 0.90f * decay)
                        continue
                    }

                    // C. Torso and upper arms
                    if (z.torso.contains(xF, yF)) {
                        val cx = (z.torso.left + z.torso.right) * 0.5f
                        val distFromCenterNorm = abs(xF - cx) / (z.torso.width * 0.5f)
                        val lateralDecay = (1.0f - distFromCenterNorm * 0.40f).coerceIn(0.40f, 1.0f)
                        val vertDecay = 1.0f - ((yF - z.torso.top) / z.torso.height) * 0.45f
                        maxSpatial = max(maxSpatial, 0.75f * lateralDecay * vertDecay)
                        continue
                    }

                    // D. Soft perimeter falloff around face boundaries (ears, hairline, chin)
                    val dx = max(0f, max(z.face.left - xF, xF - z.face.right))
                    val dy = max(0f, max(z.face.top - yF, yF - z.face.bottom))
                    val dPerimeter2 = (dx * dx + dy * dy)
                    val faceDim = max(z.face.width, z.face.height)
                    val falloffRadius2 = (faceDim * 0.35f) * (faceDim * 0.35f)

                    if (dPerimeter2 < falloffRadius2) {
                        val edgeWeight = 1.0f - (dPerimeter2 / falloffRadius2)
                        maxSpatial = max(maxSpatial, baseAmbient + (0.80f - baseAmbient) * edgeWeight)
                    }
                }

                prior[idx] = maxSpatial.coerceIn(0.0f, 1.0f)
            }
        }

        return prior
    }

    /**
     * Confidence-aware fusion combining color probability with anatomical spatial prior.
     * High-confidence skin pixels (c > 0.65f) are granted self-supporting spatial confidence
     * to protect hands and arms while firmly suppressing background objects.
     */
    fun fuseConfidence(
        colorProb: FloatArray,
        spatialPrior: FloatArray,
        wSpatial: Float = 0.65f,
        wColor: Float = 0.35f
    ): FloatArray {
        val total = colorProb.size
        val fused = FloatArray(total)

        for (i in 0 until total) {
            val c = colorProb[i]
            val s = spatialPrior[i]

            // If color probability is zero, never hallucinate skin
            if (c <= 0.001f) {
                fused[i] = 0.0f
                continue
            }

            // High color confidence boost on hands/arms outside core facial zones
            val highConfidenceBoost = smoothstep(0.60f, 0.85f, c) * 0.55f
            val effSpatial = max(s, highConfidenceBoost)

            // High spatial confidence strongly gates color probability to reject background wood/walls
            fused[i] = (c * effSpatial * 1.15f).coerceIn(0.0f, 1.0f)
        }

        return fused
    }
}
