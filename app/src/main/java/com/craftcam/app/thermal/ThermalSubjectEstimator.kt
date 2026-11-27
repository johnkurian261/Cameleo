package com.craftcam.app.thermal

import android.graphics.RectF
import com.craftcam.app.portrait.FaceRegion
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

/**
 * Immutable snapshot of the latest reduced-resolution thermal guidance mask & adaptive span.
 * Safe to share lock-free across live preview, GPU video recording, and telemetry UI.
 */
class ThermalMaskSnapshot(
    val width: Int,
    val height: Int,
    val humanMask: FloatArray,
    val skinMask: FloatArray,
    val adaptiveMinLuma: Float,
    val adaptiveMaxLuma: Float,
    val detectedSubjectCount: Int,
    val rgbaBuffer: ByteBuffer
)

/**
 * Lightweight, allocation-free reduced-resolution (128x96) human subject & radiant contrast estimator
 * for CAMELEO's artistic thermal vision mode.
 *
 * Design goals:
 * - Zero dynamic allocations after warmup on live 10 Hz background analysis path.
 * - Multi-signal thermal guidance:
 *   1) Hardware face anchors (zero-CPU Camera2 HAL metadata) + neck/shoulder/torso anatomical projection.
 *   2) YCbCr skin/hair/clothing chromaticity concordance within body perimeter.
 *   3) Separable boundary feathering to prevent crisp artificial cutouts.
 *   4) Temporal EMA smoothing across frames so masks remain flicker-free under motion.
 *   5) Adaptive scene luma bounds (`adaptiveMinLuma`, `adaptiveMaxLuma`) from histogram quantiles.
 */
class ThermalSubjectEstimator(
    private val maskW: Int = DEFAULT_MASK_WIDTH,
    private val maskH: Int = DEFAULT_MASK_HEIGHT
) {
    companion object {
        const val DEFAULT_MASK_WIDTH = 128
        const val DEFAULT_MASK_HEIGHT = 96
        private const val TEMPORAL_ALPHA_MASK = 0.28f
        private const val TEMPORAL_ALPHA_BOUNDS = 0.18f
    }

    private val total = maskW * maskH
    private val lumaArr = FloatArray(total)
    private val cbArr = FloatArray(total)
    private val crArr = FloatArray(total)

    private val rawHuman = FloatArray(total)
    private val rawSkin = FloatArray(total)
    private val blurTmp = FloatArray(total)

    private val smoothHuman = FloatArray(total)
    private val smoothSkin = FloatArray(total)
    private val histBins = IntArray(64)

    // Double-buffered RGBA byte arrays to avoid stalling GL texture uploads
    private val rgbaBuffers = arrayOf(
        ByteBuffer.allocateDirect(total * 4).order(ByteOrder.nativeOrder()),
        ByteBuffer.allocateDirect(total * 4).order(ByteOrder.nativeOrder())
    )
    private var activeBufIdx = 0

    private var hasHistory = false
    private var smoothMinLuma = 0.05f
    private var smoothMaxLuma = 0.92f

    fun resetTemporalState() {
        smoothHuman.fill(0f)
        smoothSkin.fill(0f)
        smoothMinLuma = 0.05f
        smoothMaxLuma = 0.92f
        hasHistory = false
    }

    /**
     * Analyzes a reduced-resolution RGBA buffer (`maskW x maskH`) and returns an updated
     * temporally stabilized [ThermalMaskSnapshot] without allocating arrays during steady state.
     */
    fun analyzeRgbaFrame(
        rgbaBytes: ByteBuffer,
        width: Int,
        height: Int,
        faces: List<FaceRegion>,
        segmentationEnabled: Boolean,
        temporalSmoothing: Boolean = true
    ): ThermalMaskSnapshot {
        val count = min(total, width * height)
        rgbaBytes.rewind()
        histBins.fill(0)

        for (i in 0 until count) {
            val r = rgbaBytes.get().toInt() and 0xFF
            val g = rgbaBytes.get().toInt() and 0xFF
            val b = rgbaBytes.get().toInt() and 0xFF
            rgbaBytes.get() // skip alpha

            val luma = (0.2126f * r + 0.7152f * g + 0.0722f * b) / 255f
            lumaArr[i] = luma
            cbArr[i] = (-0.168736f * r - 0.331264f * g + 0.5f * b + 128f) / 255f
            crArr[i] = (0.5f * r - 0.418688f * g - 0.081312f * b + 128f) / 255f

            val bin = (luma * 63f).toInt().coerceIn(0, 63)
            histBins[bin]++
        }

        return computeSnapshotInternal(
            w = width.coerceAtMost(maskW),
            h = height.coerceAtMost(maskH),
            faces = faces,
            segmentationEnabled = segmentationEnabled,
            temporalSmoothing = temporalSmoothing
        )
    }

    /**
     * Overload for ARGB `IntArray` bitmaps (used for single-frame still capture, import & redevelop).
     */
    fun analyzeArgbPixels(
        argbPixels: IntArray,
        width: Int,
        height: Int,
        faces: List<FaceRegion>,
        segmentationEnabled: Boolean,
        temporalSmoothing: Boolean = false
    ): ThermalMaskSnapshot {
        val count = min(total, min(argbPixels.size, width * height))
        histBins.fill(0)

        for (i in 0 until count) {
            val c = argbPixels[i]
            val r = (c ushr 16) and 0xFF
            val g = (c ushr 8) and 0xFF
            val b = c and 0xFF

            val luma = (0.2126f * r + 0.7152f * g + 0.0722f * b) / 255f
            lumaArr[i] = luma
            cbArr[i] = (-0.168736f * r - 0.331264f * g + 0.5f * b + 128f) / 255f
            crArr[i] = (0.5f * r - 0.418688f * g - 0.081312f * b + 128f) / 255f

            val bin = (luma * 63f).toInt().coerceIn(0, 63)
            histBins[bin]++
        }

        return computeSnapshotInternal(
            w = width.coerceAtMost(maskW),
            h = height.coerceAtMost(maskH),
            faces = faces,
            segmentationEnabled = segmentationEnabled,
            temporalSmoothing = temporalSmoothing
        )
    }

    private fun computeSnapshotInternal(
        w: Int,
        h: Int,
        faces: List<FaceRegion>,
        segmentationEnabled: Boolean,
        temporalSmoothing: Boolean
    ): ThermalMaskSnapshot {
        val n = w * h

        // 1. Adaptive scene dynamic range estimation via P05 .. P96 quantiles
        val pLowTarget = (n * 0.05f).toInt()
        val pHighTarget = (n * 0.96f).toInt()
        var accum = 0
        var minBin = 0
        var maxBin = 63
        var foundMin = false
        for (b in 0..63) {
            accum += histBins[b]
            if (!foundMin && accum >= pLowTarget) {
                minBin = b
                foundMin = true
            }
            if (accum >= pHighTarget) {
                maxBin = b
                break
            }
        }
        val rawMinLuma = (minBin / 63f).coerceIn(0.01f, 0.40f)
        val rawMaxLuma = (maxBin / 63f).coerceAtLeast(rawMinLuma + 0.25f).coerceIn(0.35f, 0.99f)

        if (temporalSmoothing && hasHistory) {
            smoothMinLuma = smoothMinLuma * (1f - TEMPORAL_ALPHA_BOUNDS) + rawMinLuma * TEMPORAL_ALPHA_BOUNDS
            smoothMaxLuma = smoothMaxLuma * (1f - TEMPORAL_ALPHA_BOUNDS) + rawMaxLuma * TEMPORAL_ALPHA_BOUNDS
        } else {
            smoothMinLuma = rawMinLuma
            smoothMaxLuma = rawMaxLuma
        }

        rawHuman.fill(0f, 0, n)
        rawSkin.fill(0f, 0, n)

        if (segmentationEnabled) {
            if (faces.isNotEmpty()) {
                for (face in faces) {
                    accumulatePersonHeatMask(w, h, face)
                }
            } else {
                // Conservative visible-skin & foreground warmth prior when no face lock exists
                estimateBackgroundFreeSkinWarmth(w, h)
            }

            // Separable smooth boundary feathering (~3px radius on 128x96)
            separableBoxBlur(rawHuman, blurTmp, w, h, radius = 3)
            separableBoxBlur(rawHuman, blurTmp, w, h, radius = 2)
            separableBoxBlur(rawSkin, blurTmp, w, h, radius = 2)
        }

        val bufIdx = activeBufIdx
        activeBufIdx = 1 - activeBufIdx
        val rgbaOut = rgbaBuffers[bufIdx]
        rgbaOut.clear()

        for (i in 0 until n) {
            val hVal = rawHuman[i].coerceIn(0f, 1f)
            val sVal = rawSkin[i].coerceIn(0f, 1f)
            if (temporalSmoothing && hasHistory) {
                smoothHuman[i] = smoothHuman[i] * (1f - TEMPORAL_ALPHA_MASK) + hVal * TEMPORAL_ALPHA_MASK
                smoothSkin[i] = smoothSkin[i] * (1f - TEMPORAL_ALPHA_MASK) + sVal * TEMPORAL_ALPHA_MASK
            } else {
                smoothHuman[i] = hVal
                smoothSkin[i] = sVal
            }
            val rByte = (smoothHuman[i] * 255f + 0.5f).toInt().coerceIn(0, 255).toByte()
            val gByte = (smoothSkin[i] * 255f + 0.5f).toInt().coerceIn(0, 255).toByte()
            val bByte = (lumaArr[i] * 255f + 0.5f).toInt().coerceIn(0, 255).toByte()
            rgbaOut.put(rByte).put(gByte).put(bByte).put(0xFF.toByte())
        }
        rgbaOut.flip()
        hasHistory = temporalSmoothing

        return ThermalMaskSnapshot(
            width = w,
            height = h,
            humanMask = smoothHuman.copyOf(n),
            skinMask = smoothSkin.copyOf(n),
            adaptiveMinLuma = smoothMinLuma,
            adaptiveMaxLuma = smoothMaxLuma,
            detectedSubjectCount = faces.size,
            rgbaBuffer = rgbaOut
        )
    }

    private fun accumulatePersonHeatMask(w: Int, h: Int, face: FaceRegion) {
        val left = face.bounds.left.coerceIn(0f, 1f) * w
        val top = face.bounds.top.coerceIn(0f, 1f) * h
        val right = face.bounds.right.coerceIn(0f, 1f) * w
        val bottom = face.bounds.bottom.coerceIn(0f, 1f) * h
        val faceW = max(6f, right - left)
        val faceH = max(8f, bottom - top)
        val faceCx = (left + right) * 0.5f
        val faceCy = (top + bottom) * 0.5f

        // 1. Sample interior face skin chromaticity & below-neck torso clothing chromaticity
        var skinCbSum = 0f
        var skinCrSum = 0f
        var skinCount = 0
        val fx0 = (left + faceW * 0.22f).toInt().coerceIn(0, w - 1)
        val fx1 = (right - faceW * 0.22f).toInt().coerceIn(0, w - 1)
        val fy0 = (top + faceH * 0.22f).toInt().coerceIn(0, h - 1)
        val fy1 = (bottom - faceH * 0.18f).toInt().coerceIn(0, h - 1)
        for (y in fy0..fy1) {
            val row = y * w
            for (x in fx0..fx1) {
                skinCbSum += cbArr[row + x]
                skinCrSum += crArr[row + x]
                skinCount++
            }
        }
        val refSkinCb = if (skinCount > 0) skinCbSum / skinCount else 0.44f
        val refSkinCr = if (skinCount > 0) skinCrSum / skinCount else 0.58f

        var torsoCbSum = 0f
        var torsoCrSum = 0f
        var torsoCount = 0
        val tx0 = (faceCx - faceW * 0.55f).toInt().coerceIn(0, w - 1)
        val tx1 = (faceCx + faceW * 0.55f).toInt().coerceIn(0, w - 1)
        val ty0 = (bottom + faceH * 0.35f).toInt().coerceIn(0, h - 1)
        val ty1 = (bottom + faceH * 1.15f).toInt().coerceIn(0, h - 1)
        for (y in ty0..ty1) {
            val row = y * w
            for (x in tx0..tx1) {
                torsoCbSum += cbArr[row + x]
                torsoCrSum += crArr[row + x]
                torsoCount++
            }
        }
        val refTorsoCb = if (torsoCount > 0) torsoCbSum / torsoCount else refSkinCb
        val refTorsoCr = if (torsoCount > 0) torsoCrSum / torsoCount else refSkinCr

        // 2. Project head ellipse + shoulders/torso thermal body silhouette
        val headRx = faceW * 0.72f
        val headRy = faceH * 0.80f
        val shoulderCy = bottom + faceH * 1.65f
        val shoulderRx = faceW * 1.85f
        val shoulderRy = faceH * 2.35f

        val minX = max(0, (faceCx - shoulderRx * 1.15f).toInt())
        val maxX = min(w - 1, (faceCx + shoulderRx * 1.15f).toInt())
        val minY = max(0, (faceCy - headRy * 1.25f).toInt())
        val maxY = min(h - 1, (shoulderCy + shoulderRy).toInt())

        for (y in minY..maxY) {
            val row = y * w
            val dyHead = (y - faceCy) / headRy
            val dyTorso = (y - shoulderCy) / shoulderRy

            for (x in minX..maxX) {
                val idx = row + x
                val dxHead = (x - faceCx) / headRx
                val r2Head = dxHead * dxHead + dyHead * dyHead

                val dxTorso = (x - faceCx) / shoulderRx
                val r2Torso = dxTorso * dxTorso + dyTorso * dyTorso

                val headPrior = if (r2Head < 1.35f) exp(-r2Head * r2Head * 0.85f) else 0f
                val torsoSpatial = if (y >= bottom - faceH * 0.15f && r2Torso < 1.15f) {
                    exp(-r2Torso * 1.25f)
                } else 0f

                val cb = cbArr[idx]
                val cr = crArr[idx]
                val dSkin = (cb - refSkinCb) * (cb - refSkinCb) + (cr - refSkinCr) * (cr - refSkinCr)
                val dTorso = (cb - refTorsoCb) * (cb - refTorsoCb) + (cr - refTorsoCr) * (cr - refTorsoCr)

                val skinMatch = exp(-dSkin * 165f)
                val bodyMatch = max(skinMatch, exp(-dTorso * 110f))

                val bodyConf = max(
                    headPrior * (0.68f + 0.32f * skinMatch),
                    torsoSpatial * (0.42f + 0.58f * bodyMatch)
                ).coerceIn(0f, 1f)

                val skinCoreConf = (skinMatch * max(headPrior, torsoSpatial * 0.72f)).coerceIn(0f, 1f)

                if (bodyConf > rawHuman[idx]) rawHuman[idx] = bodyConf
                if (skinCoreConf > rawSkin[idx]) rawSkin[idx] = skinCoreConf
            }
        }
    }

    private fun estimateBackgroundFreeSkinWarmth(w: Int, h: Int) {
        val invW = 1f / max(1, w)
        val invH = 1f / max(1, h)
        for (y in 0 until h) {
            val row = y * w
            val dy = (y * invH - 0.5f) / 0.42f
            for (x in 0 until w) {
                val idx = row + x
                val dx = (x * invW - 0.5f) / 0.42f
                val centerWeight = exp(-(dx * dx + dy * dy) * 0.45f)

                val cb = cbArr[idx]
                val cr = crArr[idx]
                // Conservative human skin locus in YCbCr (Cb ~ 0.30..0.50, Cr ~ 0.52..0.68)
                val cbDist = abs(cb - 0.42f) / 0.08f
                val crDist = abs(cr - 0.59f) / 0.075f
                val skinLikely = if (lumaArr[idx] > 0.14f) {
                    exp(-(cbDist * cbDist + crDist * crDist) * 0.85f)
                } else 0f

                // Keep background-free elevation restrained (<= 0.45) to avoid false red walls
                rawHuman[idx] = (skinLikely * 0.45f * (0.4f + 0.6f * centerWeight)).coerceIn(0f, 0.45f)
                rawSkin[idx] = (skinLikely * 0.55f * centerWeight).coerceIn(0f, 0.55f)
            }
        }
    }

    private fun separableBoxBlur(data: FloatArray, scratch: FloatArray, w: Int, h: Int, radius: Int) {
        if (radius <= 0 || w <= 1 || h <= 1) return
        val window = radius * 2 + 1
        val invWin = 1f / window

        // Horizontal pass
        for (y in 0 until h) {
            val row = y * w
            var sum = 0f
            for (k in -radius..radius) {
                sum += data[row + k.coerceIn(0, w - 1)]
            }
            for (x in 0 until w) {
                scratch[row + x] = sum * invWin
                val addIdx = (x + radius + 1).coerceIn(0, w - 1)
                val subIdx = (x - radius).coerceIn(0, w - 1)
                sum += data[row + addIdx] - data[row + subIdx]
            }
        }

        // Vertical pass
        for (x in 0 until w) {
            var sum = 0f
            for (k in -radius..radius) {
                sum += scratch[k.coerceIn(0, h - 1) * w + x]
            }
            for (y in 0 until h) {
                data[y * w + x] = sum * invWin
                val addRow = (y + radius + 1).coerceIn(0, h - 1) * w + x
                val subRow = (y - radius).coerceIn(0, h - 1) * w + x
                sum += scratch[addRow] - scratch[subRow]
            }
        }
    }
}
