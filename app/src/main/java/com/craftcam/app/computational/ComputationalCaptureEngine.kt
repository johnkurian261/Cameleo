package com.craftcam.app.computational

import android.graphics.Bitmap
import android.os.SystemClock
import android.util.Log
import com.craftcam.app.config.ProcessingProfile
import com.craftcam.app.model.NaturalCameraProfile
import com.craftcam.app.portrait.FaceRegion
import com.craftcam.app.portrait.PortraitConfig
import com.craftcam.app.portrait.PortraitDepthEngine
import com.craftcam.app.portrait.PortraitProcessor
import com.craftcam.app.processor.BitmapPool
import com.craftcam.app.processor.NaturalPhotoProcessor

/**
 * High-level coordinator for CAMELEO's Computational Photography Engine.
 * Integrates Scene Intelligence and Portrait-Aware Processing:
 * 1. Frame Quality Analysis -> selects sharpest reference anchor frame.
 * 2. Handheld Subpixel Alignment -> computes translation vectors on downscaled luma.
 * 3. Motion Estimation & Deghosting -> generates spatial confidence masks; clamps moving areas to 100% reference frame.
 * 4. High-Motion Safety Fallback -> if scene is excessively dynamic (>60% motion), falls back cleanly to single sharpest frame.
 * 5. Radiometric Exposure Fusion -> combines underexposed highlights and overexposed shadows in linear space.
 * 6. Adaptive Temporal/Spatial Noise Reduction -> eliminates chroma blotches while preserving fine hair/texture.
 * 7. Conservative Tone Mapping -> asymptotic highlight shoulder roll-off and true-black anchored shadow toe.
 * 8. Edge-Aware Detail Recovery -> subtle microcontrast with dampening on human facial skin tones.
 * 9. Portrait-Aware Spatial Processing -> face exposure balance, eye/hair acutance, and skin care.
 * 10. Natural Color Processing -> calibrated sensor matrix and natural saturation balance.
 */
class ComputationalCaptureEngine(
    private val config: ComputationalPhotoConfig = ComputationalPhotoConfig()
) {
    companion object {
        private const val TAG = "ComputationalEngine"
    }

    private val hdrProcessor = HDRProcessor(config)
    private val portraitDepthEngine = PortraitDepthEngine()

    /**
     * Executes the computational photography pipeline on [sequence].
     * Returns a developed [Bitmap] from [BitmapPool]. Guarantees safe single-frame fallback on any failure.
     */
    fun processSequence(
        sequence: FrameSequence,
        targetWidth: Int,
        targetHeight: Int,
        profile: NaturalCameraProfile,
        mirrorFrontCamera: Boolean = false,
        compareWithOriginal: Boolean = false,
        captureLatencyMs: Long = 0L,
        processingProfile: ProcessingProfile = ProcessingProfile.NaturalDefault,
        faces: List<FaceRegion> = emptyList(),
        portraitConfig: PortraitConfig = PortraitConfig(),
        colorConfig: com.craftcam.app.color.ColorScienceConfig = com.craftcam.app.color.ColorScienceConfig(),
        cameraId: String = "0",
        onStatsUpdated: ((ComputationalStats) -> Unit)? = null
    ): Bitmap {
        val startTime = SystemClock.elapsedRealtime()

        // 1. Fallback if sequence is empty
        if (sequence.size == 0) {
            Log.w(TAG, "Empty frame sequence, returning empty pooled buffer")
            val fallback = BitmapPool.acquire(targetWidth, targetHeight)
            return fallback
        }

        // 2. Select optimal reference frame
        val refIdx = FrameQualityAnalyzer.selectReferenceFrame(sequence)
        sequence.referenceIndex = refIdx
        val refFrame = sequence.referenceFrame

        // 3. Fallback to single frame if only 1 frame captured
        if (sequence.size == 1) {
            Log.d(TAG, "Single frame sequence, bypassing multi-frame fusion")
            var singleBmp = BitmapPool.acquire(targetWidth, targetHeight)
            val pixels = com.craftcam.app.processor.IntArrayPool.acquire(targetWidth * targetHeight)
            try {
                refFrame.decodeFullPixels(pixels, targetWidth, targetHeight, refFrame.rotation, mirrorFrontCamera)
                singleBmp.setPixels(pixels, 0, targetWidth, 0, 0, targetWidth, targetHeight)
            } finally {
                com.craftcam.app.processor.IntArrayPool.release(pixels)
            }

            if (faces.isNotEmpty() || portraitConfig.portraitMode == com.craftcam.app.portrait.PortraitMode.ON) {
                singleBmp = portraitDepthEngine.processPortraitCapture(
                    source = singleBmp,
                    faces = faces,
                    config = portraitConfig,
                    profile = processingProfile
                )
            }

            val developed = NaturalPhotoProcessor.process(
                source = singleBmp,
                profile = profile.copy(
                    highlightRollOff = processingProfile.highlightRollOff,
                    shadowLift = processingProfile.shadowLift,
                    localContrast = processingProfile.localContrast
                ),
                compareWithOriginal = compareWithOriginal,
                colorConfig = colorConfig,
                faces = faces,
                cameraId = cameraId
            )
            val totalLatency = SystemClock.elapsedRealtime() - startTime

            onStatsUpdated?.invoke(
                ComputationalStats(
                    frameCount = 1,
                    sensorIso = refFrame.iso,
                    exposureTimeNs = refFrame.exposureTimeNs,
                    captureLatencyMs = captureLatencyMs,
                    processingLatencyMs = totalLatency,
                    alignDx = 0f,
                    alignDy = 0f,
                    motionPercentage = 0f,
                    isHdrFused = false,
                    isLowLightStack = false,
                    fusionWeightsSummary = "100% Single Frame (${processingProfile.name})",
                    peakMemoryMb = estimatePeakMemoryMb(targetWidth, targetHeight, 1),
                    fallbackReason = "Single frame burst"
                )
            )
            return developed
        }

        try {
            // 4. Align alternate frames onto reference frame
            val alignments = ArrayList<AlignmentResult>(sequence.size)
            val motionMaps = ArrayList<MotionMap>(sequence.size - 1)

            var maxDx = 0f
            var maxDy = 0f

            for (i in sequence.frames.indices) {
                if (i == refIdx) {
                    alignments.add(AlignmentResult(0f, 0f, 1.0f, 0f))
                } else {
                    val altFrame = sequence.frames[i]
                    val align = FrameAligner.align(refFrame, altFrame)
                    alignments.add(align)

                    if (kotlin.math.abs(align.dx) > kotlin.math.abs(maxDx)) maxDx = align.dx
                    if (kotlin.math.abs(align.dy) > kotlin.math.abs(maxDy)) maxDy = align.dy

                    // Estimate motion for alternate frame
                    if (config.enableMotionDeghosting) {
                        val mMap = MotionEstimator.estimateMotion(
                            reference = refFrame,
                            target = altFrame,
                            alignment = align,
                            ghostingSensitivity = config.ghostingSensitivity
                        )
                        motionMaps.add(mMap)
                    }
                }
            }

            // 5. Construct master Ghosting Mask
            val ghostingMask = GhostingDetector.createGhostingMask(
                motionMaps = motionMaps,
                width = refFrame.downsampledWidth,
                height = refFrame.downsampledHeight
            )

            // 6. Motion safety check: if scene has heavy motion (>60%), fallback to sharpest reference frame
            val motionFraction = ghostingMask.maxMotionFraction
            if (motionFraction > config.motionRejectionThreshold) {
                Log.w(TAG, "Excessive motion detected (${(motionFraction * 100).toInt()}%), safely falling back to single frame")
                var fallbackBmp = BitmapPool.acquire(targetWidth, targetHeight)
                val pixels = com.craftcam.app.processor.IntArrayPool.acquire(targetWidth * targetHeight)
                try {
                    refFrame.decodeFullPixels(pixels, targetWidth, targetHeight, refFrame.rotation, mirrorFrontCamera)
                    fallbackBmp.setPixels(pixels, 0, targetWidth, 0, 0, targetWidth, targetHeight)
                } finally {
                    com.craftcam.app.processor.IntArrayPool.release(pixels)
                }

                if (faces.isNotEmpty() || portraitConfig.portraitMode == com.craftcam.app.portrait.PortraitMode.ON) {
                    fallbackBmp = portraitDepthEngine.processPortraitCapture(
                        source = fallbackBmp,
                        faces = faces,
                        config = portraitConfig,
                        profile = processingProfile
                    )
                }

                val developed = NaturalPhotoProcessor.process(
                    source = fallbackBmp,
                    profile = profile.copy(
                        highlightRollOff = processingProfile.highlightRollOff,
                        shadowLift = processingProfile.shadowLift,
                        localContrast = processingProfile.localContrast
                    ),
                    compareWithOriginal = compareWithOriginal,
                    colorConfig = colorConfig,
                    faces = faces,
                    cameraId = cameraId
                )
                val processingLatency = SystemClock.elapsedRealtime() - startTime

                onStatsUpdated?.invoke(
                    ComputationalStats(
                        frameCount = sequence.size,
                        sensorIso = refFrame.iso,
                        exposureTimeNs = refFrame.exposureTimeNs,
                        captureLatencyMs = captureLatencyMs,
                        processingLatencyMs = processingLatency,
                        alignDx = maxDx,
                        alignDy = maxDy,
                        motionPercentage = motionFraction * 100f,
                        isHdrFused = false,
                        isLowLightStack = false,
                        fusionWeightsSummary = "Bypassed (High Scene Motion)",
                        peakMemoryMb = estimatePeakMemoryMb(targetWidth, targetHeight, 2),
                        fallbackReason = "Excessive motion (${(motionFraction * 100).toInt()}%)"
                    )
                )
                return developed
            }

            // 7. Radiometric Multi-Frame Exposure Fusion & HDR
            var workingBitmap = BitmapPool.acquire(targetWidth, targetHeight)
            workingBitmap = hdrProcessor.fuse(
                sequence = sequence,
                alignments = alignments,
                ghostingMask = ghostingMask,
                targetWidth = targetWidth,
                targetHeight = targetHeight,
                outputBitmap = workingBitmap,
                mirrorFrontCamera = mirrorFrontCamera
            )

            // 8. Adaptive Temporal & Spatial Noise Reduction
            val isLowLight = refFrame.iso >= config.lowLightIsoThreshold || processingProfile.enableLowLightStack
            workingBitmap = MultiFrameNoiseReducer.reduceNoise(
                source = workingBitmap,
                sensorIso = refFrame.iso,
                ghostingMask = ghostingMask,
                adaptiveStrength = if (isLowLight) 1.2f else processingProfile.backgroundNoiseReduction
            )

            // 9. Conservative Tone Mapping (Asymptotic shoulder + true black shadow toe)
            workingBitmap = ToneMapper.applyToneMapping(
                source = workingBitmap,
                highlightKnee = (1.0f - processingProfile.highlightRollOff * 0.40f).coerceIn(0.60f, 0.85f),
                shadowLift = processingProfile.shadowLift
            )

            // 10. Edge-Aware Detail Recovery with Skin Protection
            workingBitmap = DetailRecoveryEngine.recoverDetail(
                source = workingBitmap,
                microContrastGain = processingProfile.microContrastGain,
                skinDampening = processingProfile.skinSharpeningDampening
            )

            // 11. Portrait-Aware Spatial Processing & Computational Bokeh
            if (faces.isNotEmpty() || portraitConfig.portraitMode == com.craftcam.app.portrait.PortraitMode.ON) {
                workingBitmap = portraitDepthEngine.processPortraitCapture(
                    source = workingBitmap,
                    faces = faces,
                    config = portraitConfig,
                    profile = processingProfile
                )
            }

            // 12. Final Natural Color Processing & Color Matrix (Phase 7)
            workingBitmap = NaturalPhotoProcessor.process(
                source = workingBitmap,
                profile = profile.copy(
                    highlightRollOff = processingProfile.highlightRollOff,
                    shadowLift = processingProfile.shadowLift,
                    localContrast = processingProfile.localContrast
                ),
                compareWithOriginal = compareWithOriginal,
                colorConfig = colorConfig,
                faces = faces,
                cameraId = cameraId
            )

            val processingLatency = SystemClock.elapsedRealtime() - startTime

            onStatsUpdated?.invoke(
                ComputationalStats(
                    frameCount = sequence.size,
                    sensorIso = refFrame.iso,
                    exposureTimeNs = refFrame.exposureTimeNs,
                    captureLatencyMs = captureLatencyMs,
                    processingLatencyMs = processingLatency,
                    alignDx = maxDx,
                    alignDy = maxDy,
                    motionPercentage = motionFraction * 100f,
                    isHdrFused = true,
                    isLowLightStack = isLowLight,
                    fusionWeightsSummary = "Mertens Radiometric Fusion • ${processingProfile.name}",
                    peakMemoryMb = estimatePeakMemoryMb(targetWidth, targetHeight, 2),
                    fallbackReason = null
                )
            )

            return workingBitmap

        } catch (t: Throwable) {
            Log.e(TAG, "Error in computational pipeline, falling back to safe single frame", t)
            var fallbackBmp = BitmapPool.acquire(targetWidth, targetHeight)
            val pixels = com.craftcam.app.processor.IntArrayPool.acquire(targetWidth * targetHeight)
            try {
                refFrame.decodeFullPixels(pixels, targetWidth, targetHeight, refFrame.rotation, mirrorFrontCamera)
                fallbackBmp.setPixels(pixels, 0, targetWidth, 0, 0, targetWidth, targetHeight)
            } finally {
                com.craftcam.app.processor.IntArrayPool.release(pixels)
            }

            if (faces.isNotEmpty()) {
                fallbackBmp = PortraitProcessor.processPortrait(fallbackBmp, faces, processingProfile)
            }

            val developed = NaturalPhotoProcessor.process(
                fallbackBmp,
                profile.copy(
                    highlightRollOff = processingProfile.highlightRollOff,
                    shadowLift = processingProfile.shadowLift,
                    localContrast = processingProfile.localContrast
                ),
                compareWithOriginal
            )
            val processingLatency = SystemClock.elapsedRealtime() - startTime

            onStatsUpdated?.invoke(
                ComputationalStats(
                    frameCount = 1,
                    sensorIso = refFrame.iso,
                    exposureTimeNs = refFrame.exposureTimeNs,
                    captureLatencyMs = captureLatencyMs,
                    processingLatencyMs = processingLatency,
                    alignDx = 0f,
                    alignDy = 0f,
                    motionPercentage = 0f,
                    isHdrFused = false,
                    isLowLightStack = false,
                    fusionWeightsSummary = "Fallback on Error (${processingProfile.name})",
                    peakMemoryMb = estimatePeakMemoryMb(targetWidth, targetHeight, 1),
                    fallbackReason = "Exception: ${t.message}"
                )
            )

            return developed
        }
    }

    private fun estimatePeakMemoryMb(width: Int, height: Int, intArraysCount: Int): Float {
        val pixels = width * height
        val workingBytes = pixels * 4L // Bitmap buffer
        val arrayBytes = intArraysCount * pixels * 4L
        return ((workingBytes + arrayBytes) / (1024f * 1024f))
    }
}
