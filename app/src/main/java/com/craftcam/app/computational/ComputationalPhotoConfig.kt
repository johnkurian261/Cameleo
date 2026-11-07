package com.craftcam.app.computational

/**
 * Configuration and operational parameters for CAMELEO's Computational Photography Engine.
 * All dynamic range, exposure bracketing, motion thresholds, and tone curve parameters
 * are centralized here to allow adaptive runtime tuning.
 */
data class ComputationalPhotoConfig(
    /** Number of frames captured in a computational burst (2, 3, or 5). Default is 3 for optimal stability and latency. */
    val burstFrameCount: Int = 3,

    /** Exposure value offsets for bracketing relative to metered exposure (in EV). */
    val evOffsets: FloatArray = floatArrayOf(-1.0f, 0.0f, +1.0f),

    /** Debug switch to force multi-frame HDR even in low-contrast daylight scenes. */
    val forceMultiFrameHdr: Boolean = false,

    /** Enables motion estimation and deghosting. Moving regions fall back to the sharpest single frame. */
    val enableMotionDeghosting: Boolean = true,

    /** Experimental low-light computational mode with short-exposure stacking and temporal denoising. */
    val enableLowLightMode: Boolean = false,

    /** ISO threshold above which low-light computational stacking is automatically engaged. */
    val lowLightIsoThreshold: Int = 640,

    /** Scene dynamic range threshold (relative luminance spread) to trigger automatic HDR fusion. */
    val dynamicRangeThreshold: Float = 0.32f,

    /** Minimum Laplacian variance for a frame to be considered sharp. */
    val sharpnessThreshold: Float = 12.0f,

    /** Maximum global motion percentage before abandoning multi-frame fusion and falling back to single frame. */
    val motionRejectionThreshold: Float = 0.60f,

    /** Sensitivity threshold for local motion / residual difference detection (0.05 to 0.30). */
    val ghostingSensitivity: Float = 0.12f,

    /** Linear knee point where asymptotic highlight shoulder compression begins (0.60 to 0.85). */
    val highlightKnee: Float = 0.68f,

    /** Rational toe factor for gently opening deep shadows without elevating true black (0.0 to 0.5). */
    val shadowToeLift: Float = 0.22f,

    /** Restrained edge-aware microcontrast gain (0.0 to 0.30). Avoids smartphone over-clarity halos. */
    val microContrastGain: Float = 0.12f,

    /** Fraction by which microcontrast sharpening is dampened over human skin regions (0.0 to 1.0). */
    val skinSharpnessDampening: Float = 0.60f
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ComputationalPhotoConfig

        if (burstFrameCount != other.burstFrameCount) return false
        if (!evOffsets.contentEquals(other.evOffsets)) return false
        if (forceMultiFrameHdr != other.forceMultiFrameHdr) return false
        if (enableMotionDeghosting != other.enableMotionDeghosting) return false
        if (enableLowLightMode != other.enableLowLightMode) return false
        if (lowLightIsoThreshold != other.lowLightIsoThreshold) return false
        if (dynamicRangeThreshold != other.dynamicRangeThreshold) return false
        if (sharpnessThreshold != other.sharpnessThreshold) return false
        if (motionRejectionThreshold != other.motionRejectionThreshold) return false
        if (ghostingSensitivity != other.ghostingSensitivity) return false
        if (highlightKnee != other.highlightKnee) return false
        if (shadowToeLift != other.shadowToeLift) return false
        if (microContrastGain != other.microContrastGain) return false
        if (skinSharpnessDampening != other.skinSharpnessDampening) return false

        return true
    }

    override fun hashCode(): Int {
        var result = burstFrameCount
        result = 31 * result + evOffsets.contentHashCode()
        result = 31 * result + forceMultiFrameHdr.hashCode()
        result = 31 * result + enableMotionDeghosting.hashCode()
        result = 31 * result + enableLowLightMode.hashCode()
        result = 31 * result + lowLightIsoThreshold
        result = 31 * result + dynamicRangeThreshold.hashCode()
        result = 31 * result + sharpnessThreshold.hashCode()
        result = 31 * result + motionRejectionThreshold.hashCode()
        result = 31 * result + ghostingSensitivity.hashCode()
        result = 31 * result + highlightKnee.hashCode()
        result = 31 * result + shadowToeLift.hashCode()
        result = 31 * result + microContrastGain.hashCode()
        result = 31 * result + skinSharpnessDampening.hashCode()
        return result
    }
}

/**
 * Diagnostics and telemetry metrics captured during computational photo processing.
 * Exposed to developer debug panel and benchmark reports.
 */
data class ComputationalStats(
    val frameCount: Int = 0,
    val sensorIso: Int = 100,
    val exposureTimeNs: Long = 10_000_000L,
    val captureLatencyMs: Long = 0L,
    val processingLatencyMs: Long = 0L,
    val alignDx: Float = 0f,
    val alignDy: Float = 0f,
    val motionPercentage: Float = 0f,
    val isHdrFused: Boolean = false,
    val isLowLightStack: Boolean = false,
    val fusionWeightsSummary: String = "",
    val peakMemoryMb: Float = 0f,
    val fallbackReason: String? = null
)

