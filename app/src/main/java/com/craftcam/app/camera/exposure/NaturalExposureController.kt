package com.craftcam.app.camera.exposure

import com.craftcam.app.portrait.FaceRegion
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Natural Exposure Controller for CAMELEO Zero / Natural Camera Mode.
 *
 * Implements intelligent scene-adaptive exposure metering to protect highlights
 * and balance dynamic range without crude permanent negative EV offsets.
 *
 * Analysis dimensions:
 * 1. Average scene luminance (from 256-bin preview histogram).
 * 2. Highlight clipping ratio (specular white saturation in bins 252..255).
 * 3. High-highlight distribution (near-white content in bins 220..251 e.g. bright sky/clouds).
 * 4. Deep shadow clipping (black crush in bins 0..3).
 * 5. Subject presence (face regions / backlit human subjects).
 *
 * Output:
 * Smoothly interpolated Camera2 EV compensation index to prevent frame-to-frame exposure flutter.
 */
class NaturalExposureController {

    private var smoothedTargetEv: Float = 0.0f
    private var lastAppliedIndex: Int = 0

    data class ExposureDecision(
        val targetEv: Float,
        val targetIndex: Int,
        val sceneTag: String,
        val whiteClipRatio: Float,
        val highHighlightRatio: Float,
        val meanLuminance: Float
    )

    /**
     * Evaluates current scene metrics and computes optimal exposure compensation.
     *
     * @param histogram 256-bin luminance histogram from GLViewfinderRenderer.
     * @param sensorIso Current sensor sensitivity.
     * @param exposureTimeNs Current sensor exposure time in nanoseconds.
     * @param faces Detected face regions from HardwareFaceDetector.
     * @param step Hardware exposure compensation step (e.g. 0.333f or 0.5f).
     * @param minIndex Minimum allowed hardware EV compensation index.
     * @param maxIndex Maximum allowed hardware EV compensation index.
     * @return ExposureDecision with target EV and quantized hardware index.
     */
    fun evaluate(
        histogram: IntArray,
        sensorIso: Int,
        exposureTimeNs: Long,
        faces: List<FaceRegion>,
        step: Float,
        minIndex: Int,
        maxIndex: Int
    ): ExposureDecision {
        if (histogram.size != 256) {
            return ExposureDecision(0f, 0, "DEFAULT_BALANCED", 0f, 0f, 0.5f)
        }

        var totalPixels = 0L
        var weightedLumaSum = 0L
        for (i in histogram.indices) {
            val count = histogram[i]
            totalPixels += count
            weightedLumaSum += i.toLong() * count
        }

        if (totalPixels <= 0L) {
            return ExposureDecision(0f, 0, "NO_DATA", 0f, 0f, 0.5f)
        }

        val totalD = totalPixels.toDouble()
        val meanLuma = (weightedLumaSum / (totalD * 255.0)).toFloat()

        // 1. Extreme specular white clipping: bins 252..255
        var whiteClipCount = 0L
        for (i in 252..255) whiteClipCount += histogram[i]
        val whiteClipRatio = (whiteClipCount / totalD).toFloat()

        // 2. High highlights (bright sky, sunlit clouds, light fixtures): bins 220..251
        var highHighlightCount = 0L
        for (i in 220..251) highHighlightCount += histogram[i]
        val highHighlightRatio = (highHighlightCount / totalD).toFloat()

        // 3. Deep shadow / black clipping: bins 0..3
        var blackClipCount = 0L
        for (i in 0..3) blackClipCount += histogram[i]
        val blackClipRatio = (blackClipCount / totalD).toFloat()

        // 4. Shadows mass: bins 4..63
        var shadowCount = 0L
        for (i in 4..63) shadowCount += histogram[i]
        val shadowRatio = (shadowCount / totalD).toFloat()

        // 5. Intelligent Exposure Decision Matrix
        val rawEv: Float
        val sceneTag: String

        val hasFace = faces.isNotEmpty()
        val isBacklit = (highHighlightRatio + whiteClipRatio > 0.18f) && (shadowRatio + blackClipRatio > 0.35f)

        when {
            // Scenario A: Severe Highlight Clipping / Extreme Bright Sun
            whiteClipRatio > 0.04f || (whiteClipRatio > 0.02f && highHighlightRatio > 0.25f) -> {
                rawEv = -0.66f
                sceneTag = "EXTREME_HIGHLIGHT_PROTECTION"
            }

            // Scenario B: Backlit Portrait (Face present with bright background)
            hasFace && (isBacklit || highHighlightRatio > 0.15f) -> {
                // Keep exposure balanced so face is readable while highlight roll-off protects background
                rawEv = -0.20f
                sceneTag = "BACKLIT_PORTRAIT_BALANCED"
            }

            // Scenario C: Bright Sky & High Dynamic Range (preserve cloud texture and sky blue)
            highHighlightRatio > 0.16f && shadowRatio > 0.20f -> {
                rawEv = -0.33f
                sceneTag = "SKY_AND_HDR_PRESERVATION"
            }

            // Scenario D: Dark / Low-Light Scene (prevent excessive dark crush and noise amplification)
            meanLuma < 0.22f || (sensorIso > 800 && meanLuma < 0.35f) -> {
                // In dark scenes, do NOT apply negative EV; allow neutral or gentle boost
                rawEv = 0.0f
                sceneTag = "LOW_LIGHT_CLEAN"
            }

            // Scenario E: Normal Daylight / Balanced Scene
            else -> {
                rawEv = 0.0f
                sceneTag = "NORMAL_DAYLIGHT_NEUTRAL"
            }
        }

        // Apply exponential moving average (EMA) smoothing to eliminate stepping/flutter
        // 0.85 previous + 0.15 current gives smooth, imperceptible transitions across 1-2 seconds
        smoothedTargetEv = smoothedTargetEv * 0.82f + rawEv * 0.18f

        val safeStep = if (step > 0.05f) step else 0.5f
        val calculatedIndex = (smoothedTargetEv / safeStep).roundToInt().coerceIn(minIndex, maxIndex)
        lastAppliedIndex = calculatedIndex

        return ExposureDecision(
            targetEv = smoothedTargetEv,
            targetIndex = calculatedIndex,
            sceneTag = sceneTag,
            whiteClipRatio = whiteClipRatio,
            highHighlightRatio = highHighlightRatio,
            meanLuminance = meanLuma
        )
    }

    /**
     * Resets internal temporal smoothing state (e.g. upon camera rebind or preset switch).
     */
    fun reset() {
        smoothedTargetEv = 0.0f
        lastAppliedIndex = 0
    }
}
