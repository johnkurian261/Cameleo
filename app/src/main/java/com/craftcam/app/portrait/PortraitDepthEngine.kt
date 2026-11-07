package com.craftcam.app.portrait

import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log
import com.craftcam.app.config.ProcessingProfile
import kotlin.math.abs

/**
 * Master Portrait Depth & Computational Bokeh Engine for CAMELEO.
 *
 * Coordinates the full end-to-end computational portrait pipeline:
 * Camera Frame -> Face Detection -> Depth Estimation -> Subject Segmentation
 *   -> Mask Refinement (Hair & Glasses) -> Depth-Aware Blur -> Bokeh Rendering
 *   -> Natural Color & Skin Protection.
 *
 * 100% on-device, privacy-preserving, zero-cloud transmission.
 */
class PortraitDepthEngine(
    private val depthEstimator: DepthEstimator = MonocularDepthEstimator(),
    private val subjectSegmenter: SubjectSegmenter = SubjectSegmenter(),
    private val maskRefiner: PortraitMaskRefiner = PortraitMaskRefiner(),
    private val depthAwareBlur: DepthAwareBlur = DepthAwareBlur(),
    private val bokehRenderer: BokehRenderer = BokehRenderer()
) {
    companion object {
        private const val TAG = "PortraitDepthEngine"
    }

    /**
     * Executes the complete portrait depth pipeline on a captured photo.
     */
    fun processPortraitCapture(
        source: Bitmap,
        faces: List<FaceRegion>,
        config: PortraitConfig,
        profile: ProcessingProfile = ProcessingProfile.NaturalPortrait
    ): Bitmap {
        // 1. Portrait Engagement Decision
        val shouldEngage = when (config.portraitMode) {
            PortraitMode.OFF -> false
            PortraitMode.AUTO -> faces.isNotEmpty() && faces.any { it.confidence >= 0.40f || it.areaFraction >= 0.03f }
            PortraitMode.ON -> true
        }

        if (!shouldEngage) {
            Log.d(TAG, "Portrait bokeh bypassed (mode=${config.portraitMode}, faces=${faces.size})")
            return if (faces.isNotEmpty()) {
                PortraitProcessor.processPortrait(source, faces, profile)
            } else {
                source
            }
        }

        val width = source.width
        val height = source.height
        Log.i(TAG, "Executing Portrait Depth Pipeline on ${width}x${height} image with ${faces.size} faces")

        return try {
            // 2. Depth Estimation (at analysis resolution, e.g. 256x192)
            val depthMap = depthEstimator.estimateDepth(source, faces, config)

            // 3. Photographic Subject Segmentation
            val rawMask = subjectSegmenter.segmentSubject(source, faces, depthMap, config)

            // 4. Boundary Refinement (Hair matting, glasses preservation, interior closing)
            val refinedMask = maskRefiner.refineMask(rawMask, source, faces, depthMap, config)

            // 5. Check for Degenerate Mask Fallback
            var subjectArea = 0f
            for (v in refinedMask.confidence) subjectArea += v
            val subjectFraction = subjectArea / refinedMask.confidence.size

            if (subjectFraction < 0.01f || subjectFraction > 0.95f) {
                Log.w(TAG, "Degenerate portrait mask detected (fraction=$subjectFraction). Falling back safely.")
                return PortraitProcessor.processPortrait(source, faces, profile)
            }

            // 6. High-Resolution Guided Mask & Depth Upsampling
            val fullMask = maskRefiner.upsampleMaskGuided(refinedMask, source, width, height)
            val fullDepth = depthMap.upsampleTo(width, height)

            // 7. Compute Depth-Aware Blur Map
            val blurMap = depthAwareBlur.computeBlurMap(
                fullDepth, fullMask, width, height, config.activeBokehProfile
            )

            // 8. Development Diagnostic Mode Handling
            if (config.debugMaskMode != DebugMaskMode.NONE) {
                return renderDiagnosticView(source, fullDepth, rawMask, fullMask, blurMap, width, height, config.debugMaskMode)
            }

            // 9. Prepare Anti-Color-Bleed Background Layer
            val antiBleedBg = if (config.antiColorBleed) {
                depthAwareBlur.prepareAntiBleedBackground(source, fullMask, width, height)
            } else {
                source
            }

            // 10. Depth-Aware Bokeh Pyramid Rendering & Alpha-Composite
            val bokehComposite = bokehRenderer.renderBokeh(
                source, antiBleedBg, fullMask, blurMap, config
            )

            if (antiBleedBg != source) {
                antiBleedBg.recycle()
            }

            // 11. Final Portrait Skin Protection & Face Exposure Lift
            if (faces.isNotEmpty()) {
                PortraitProcessor.processPortrait(bokehComposite, faces, profile)
            } else {
                bokehComposite
            }
        } catch (e: Exception) {
            Log.e(TAG, "Portrait depth processing failed with error, falling back gracefully: ${e.message}", e)
            PortraitProcessor.processPortrait(source, faces, profile)
        }
    }

    /**
     * Renders diagnostic mask visualizations for development and calibration.
     */
    private fun renderDiagnosticView(
        source: Bitmap,
        fullDepth: FloatArray,
        rawMask: SubjectMask,
        fullMask: FloatArray,
        blurMap: FloatArray,
        width: Int,
        height: Int,
        mode: DebugMaskMode
    ): Bitmap {
        val total = width * height
        val outPixels = IntArray(total)

        when (mode) {
            DebugMaskMode.DEPTH -> {
                for (i in 0 until total) {
                    val gray = (fullDepth[i].coerceIn(0f, 1f) * 255f).toInt()
                    outPixels[i] = (0xFF shl 24) or (gray shl 16) or (gray shl 8) or gray
                }
            }
            DebugMaskMode.SEGMENTATION -> {
                val upRaw = FloatArray(total)
                val xRatio = (rawMask.width - 1).toFloat() / (width - 1).coerceAtLeast(1)
                val yRatio = (rawMask.height - 1).toFloat() / (height - 1).coerceAtLeast(1)
                for (y in 0 until height) {
                    val sy = (y * yRatio).toInt().coerceIn(0, rawMask.height - 1)
                    val rOffset = y * width
                    for (x in 0 until width) {
                        val sx = (x * xRatio).toInt().coerceIn(0, rawMask.width - 1)
                        val v = rawMask.confidence[sy * rawMask.width + sx]
                        val gray = (v * 255f).toInt().coerceIn(0, 255)
                        outPixels[rOffset + x] = (0xFF shl 24) or (gray shl 16) or (gray shl 8) or gray
                    }
                }
            }
            DebugMaskMode.FINAL_MASK -> {
                for (i in 0 until total) {
                    val gray = (fullMask[i].coerceIn(0f, 1f) * 255f).toInt()
                    outPixels[i] = (0xFF shl 24) or (gray shl 16) or (gray shl 8) or gray
                }
            }
            DebugMaskMode.BLUR_MAP -> {
                for (i in 0 until total) {
                    // False-color blur map: Blue (near/0) -> Green -> Red (far/full blur)
                    val b = blurMap[i].coerceIn(0f, 1f)
                    val red = (b * 255f).toInt()
                    val green = ((1f - abs(b - 0.5f) * 2f) * 255f).toInt()
                    val blue = ((1f - b) * 255f).toInt()
                    outPixels[i] = (0xFF shl 24) or (red shl 16) or (green shl 8) or blue
                }
            }
            DebugMaskMode.NONE -> {
                source.getPixels(outPixels, 0, width, 0, 0, width, height)
            }
        }

        val outBmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        outBmp.setPixels(outPixels, 0, width, 0, 0, width, height)
        return outBmp
    }
}
