package com.craftcam.app.portrait

import android.graphics.Bitmap
import android.graphics.RectF
import kotlin.math.*

/**
 * Subject mask representing segmented foreground human subjects.
 *
 * Confidence values range in [0.0f, 1.0f]:
 * - 0.0f = definite background.
 * - 1.0f = definite in-focus subject.
 * - Intermediate values represent soft boundary transitions (hair, edges, clothing borders).
 */
data class SubjectMask(
    val width: Int,
    val height: Int,
    val confidence: FloatArray
) {
    init {
        require(confidence.size == width * height) {
            "SubjectMask confidence size (${confidence.size}) must match width * height ($width * $height = ${width * height})"
        }
    }

    fun getConfidence(x: Int, y: Int): Float {
        val cx = x.coerceIn(0, width - 1)
        val cy = y.coerceIn(0, height - 1)
        return confidence[cy * width + cx]
    }
}

/**
 * On-device photographic subject segmentation engine for CAMELEO.
 *
 * Implements multi-signal photographic fusion:
 * 1. Facial Landmark & Core Anchor (100% preserved face geometry).
 * 2. Anatomical Body Projection (neck, shoulders, chest, and arms extension).
 * 3. Color & Texture Concordance (skin and clothing chromaticity in YCbCr space).
 * 4. Depth Concordance (filters background dropouts based on DepthMap).
 * 5. Multi-Subject Union (supports 1, 2, or group portraits without dropping subjects).
 */
class SubjectSegmenter {

    fun segmentSubject(
        source: Bitmap,
        faces: List<FaceRegion>,
        depthMap: DepthMap,
        config: PortraitConfig
    ): SubjectMask {
        val targetW = depthMap.width
        val targetH = depthMap.height
        val total = targetW * targetH

        // Scale bitmap to working segmentation resolution if needed
        val scaled = if (source.width == targetW && source.height == targetH) {
            source
        } else {
            Bitmap.createScaledBitmap(source, targetW, targetH, true)
        }

        val pixels = IntArray(total)
        scaled.getPixels(pixels, 0, targetW, 0, 0, targetW, targetH)

        // Convert pixels to YCbCr components for color clustering
        val cbArray = FloatArray(total)
        val crArray = FloatArray(total)
        val lumaArray = FloatArray(total)

        for (i in 0 until total) {
            val p = pixels[i]
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF

            lumaArray[i] = (0.299f * r + 0.587f * g + 0.114f * b) / 255f
            cbArray[i] = (-0.168736f * r - 0.331264f * g + 0.5f * b + 128f) / 255f
            crArray[i] = (0.5f * r - 0.418688f * g - 0.081312f * b + 128f) / 255f
        }

        val finalMask = FloatArray(total)

        if (faces.isEmpty()) {
            // When no faces are present, synthesize a conservative central subject prior
            val cx = targetW * 0.5f
            val cy = targetH * 0.55f
            val rx = targetW * 0.28f
            val ry = targetH * 0.38f

            for (y in 0 until targetH) {
                val row = y * targetW
                val dy = (y - cy) / ry
                for (x in 0 until targetW) {
                    val idx = row + x
                    val dx = (x - cx) / rx
                    val r2 = dx * dx + dy * dy
                    val depthWeight = (1.0f - depthMap.values[idx] * 1.5f).coerceIn(0f, 1f)
                    val spatialWeight = (1.0f - r2).coerceIn(0f, 1f)
                    finalMask[idx] = (spatialWeight * 0.7f + depthWeight * 0.3f).coerceIn(0f, 1f)
                }
            }
        } else {
            // Multi-Person Segmentation: evaluate each person and take continuous union
            for (face in faces) {
                val pixelFace = face.toPixelRect(targetW, targetH)
                val personMask = evaluatePersonMask(
                    targetW, targetH, pixelFace,
                    lumaArray, cbArray, crArray, depthMap
                )

                // Union into finalMask: max(finalMask[i], personMask[i])
                for (i in 0 until total) {
                    if (personMask[i] > finalMask[i]) {
                        finalMask[i] = personMask[i]
                    }
                }
            }
        }

        if (scaled != source) {
            scaled.recycle()
        }

        return SubjectMask(targetW, targetH, finalMask)
    }

    private fun evaluatePersonMask(
        width: Int,
        height: Int,
        face: RectF,
        luma: FloatArray,
        cb: FloatArray,
        cr: FloatArray,
        depthMap: DepthMap
    ): FloatArray {
        val total = width * height
        val mask = FloatArray(total)

        val faceW = face.width()
        val faceH = face.height()
        val faceCX = face.centerX()
        val faceCY = face.centerY()

        // 1. Sample Face Core Chromaticity & Torso Clothing Chromaticity
        var skinCbSum = 0f
        var skinCrSum = 0f
        var skinCount = 0

        val fx0 = (face.left + faceW * 0.2f).toInt().coerceIn(0, width - 1)
        val fx1 = (face.right - faceW * 0.2f).toInt().coerceIn(0, width - 1)
        val fy0 = (face.top + faceH * 0.25f).toInt().coerceIn(0, height - 1)
        val fy1 = (face.bottom - faceH * 0.2f).toInt().coerceIn(0, height - 1)

        for (y in fy0..fy1) {
            val row = y * width
            for (x in fx0..fx1) {
                val idx = row + x
                skinCbSum += cb[idx]
                skinCrSum += cr[idx]
                skinCount++
            }
        }

        val refSkinCb = if (skinCount > 0) skinCbSum / skinCount else 0.45f
        val refSkinCr = if (skinCount > 0) skinCrSum / skinCount else 0.58f

        // Sample torso clothing anchor (below neck)
        var torsoCbSum = 0f
        var torsoCrSum = 0f
        var torsoCount = 0

        val tx0 = (faceCX - faceW * 0.4f).toInt().coerceIn(0, width - 1)
        val tx1 = (faceCX + faceW * 0.4f).toInt().coerceIn(0, width - 1)
        val ty0 = (face.bottom + faceH * 0.3f).toInt().coerceIn(0, height - 1)
        val ty1 = (face.bottom + faceH * 0.9f).toInt().coerceIn(0, height - 1)

        for (y in ty0..ty1) {
            val row = y * width
            for (x in tx0..tx1) {
                val idx = row + x
                torsoCbSum += cb[idx]
                torsoCrSum += cr[idx]
                torsoCount++
            }
        }

        val refTorsoCb = if (torsoCount > 0) torsoCbSum / torsoCount else refSkinCb
        val refTorsoCr = if (torsoCount > 0) torsoCrSum / torsoCount else refSkinCr

        // 2. Anatomical Geometry Parameters
        // Neck extends downwards ~0.35 faceH
        val neckBottom = face.bottom + faceH * 0.35f
        // Shoulders extend laterally ~2.4 faceW
        val shoulderHalfWidth = faceW * 1.25f
        val bodyBottom = (face.bottom + faceH * 3.5f).coerceAtMost(height.toFloat())

        for (y in 0 until height) {
            val row = y * width
            val yF = y.toFloat()

            for (x in 0 until width) {
                val idx = row + x
                val xF = x.toFloat()
                val depthVal = depthMap.values[idx]

                // A. Face Core Priority Check
                if (face.contains(xF, yF)) {
                    // Inside face box: guaranteed 1.0 confidence
                    mask[idx] = 1.0f
                    continue
                }

                // Check head perimeter (hair & ears zone)
                val headDistX = abs(xF - faceCX) / (faceW * 0.65f)
                val headDistY = abs(yF - faceCY) / (faceH * 0.65f)
                val headRadius2 = headDistX * headDistX + headDistY * headDistY

                if (headRadius2 <= 1.0f) {
                    // Smooth transition around face periphery
                    mask[idx] = 1.0f - (headRadius2 - 0.7f).coerceAtLeast(0f) / 0.3f
                    continue
                }

                // B. Neck & Upper Body Anatomical Prior
                var bodyWeight = 0.0f
                if (yF in face.bottom..bodyBottom) {
                    // Lateral shoulder expansion trapezoid
                    val yProgress = ((yF - face.bottom) / (neckBottom - face.bottom).coerceAtLeast(1f)).coerceIn(0f, 1f)
                    val currentHalfW = faceW * 0.55f + yProgress * (shoulderHalfWidth - faceW * 0.55f)
                    val dxFromSpine = abs(xF - faceCX)

                    if (dxFromSpine <= currentHalfW) {
                        val lateralFalloff = 1.0f - (dxFromSpine / currentHalfW)
                        bodyWeight = (0.5f + 0.5f * lateralFalloff).coerceIn(0f, 1f)
                    }
                }

                if (bodyWeight > 0.05f) {
                    // C. Color Concordance (skin vs clothing similarity)
                    val dSkinCb = cb[idx] - refSkinCb
                    val dSkinCr = cr[idx] - refSkinCr
                    val distSkin = sqrt(dSkinCb * dSkinCb + dSkinCr * dSkinCr)

                    val dTorsoCb = cb[idx] - refTorsoCb
                    val dTorsoCr = cr[idx] - refTorsoCr
                    val distTorso = sqrt(dTorsoCb * dTorsoCb + dTorsoCr * dTorsoCr)

                    val bestColorDist = min(distSkin, distTorso)
                    val colorConfidence = exp(-bestColorDist * 8.0f)

                    // D. Depth Concordance (reject pixels that recede into far depth)
                    val depthConfidence = (1.0f - (depthVal - 0.20f) * 2.0f).coerceIn(0f, 1f)

                    val fused = bodyWeight * 0.5f + colorConfidence * 0.25f + depthConfidence * 0.25f
                    mask[idx] = fused.coerceIn(0f, 1f)
                } else {
                    // Outside anatomical body: check if skin tone (e.g. hands or arms)
                    val dSkinCb = cb[idx] - refSkinCb
                    val dSkinCr = cr[idx] - refSkinCr
                    val distSkin = sqrt(dSkinCb * dSkinCb + dSkinCr * dSkinCr)

                    if (distSkin < 0.08f && depthVal < 0.35f && yF > face.bottom) {
                        // Hand or arm gesture near body
                        mask[idx] = (1.0f - distSkin / 0.08f) * 0.75f
                    } else {
                        mask[idx] = 0.0f
                    }
                }
            }
        }

        return mask
    }
}

