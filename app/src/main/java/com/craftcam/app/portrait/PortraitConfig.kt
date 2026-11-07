package com.craftcam.app.portrait

/**
 * Operational mode for CAMELEO computational portrait processing.
 */
enum class PortraitMode {
    /** Portrait depth and bokeh strictly disabled. */
    OFF,
    /** Engages portrait depth & bokeh automatically when faces are detected with high confidence. */
    AUTO,
    /** Forces portrait depth processing regardless of automatic confidence threshold. */
    ON
}

/**
 * User-facing bokeh strength presets.
 */
enum class BokehStrength {
    /** Gentle optical roll-off simulating f/4.0 aperture. */
    NATURAL,
    /** Balanced, natural portrait depth-of-field simulating f/2.8 aperture. (Default) */
    SUBTLE,
    /** Pronounced subject isolation simulating f/1.8 fast prime lens. */
    STRONG
}

/**
 * Technical optical parameters for the depth-aware blur engine.
 */
data class BokehProfile(
    val maxBlurRadius: Float,
    val depthFalloff: Float,
    val transitionWidth: Float
) {
    companion object {
        val Natural = BokehProfile(
            maxBlurRadius = 10f,
            depthFalloff = 1.6f,
            transitionWidth = 0.12f
        )

        val Subtle = BokehProfile(
            maxBlurRadius = 20f,
            depthFalloff = 2.2f,
            transitionWidth = 0.18f
        )

        val Strong = BokehProfile(
            maxBlurRadius = 36f,
            depthFalloff = 2.8f,
            transitionWidth = 0.24f
        )

        fun forStrength(strength: BokehStrength): BokehProfile = when (strength) {
            BokehStrength.NATURAL -> Natural
            BokehStrength.SUBTLE -> Subtle
            BokehStrength.STRONG -> Strong
        }
    }
}

/**
 * Diagnostic visualization modes for development and calibration.
 */
enum class DebugMaskMode {
    /** Normal final portrait rendering with bokeh. */
    NONE,
    /** Displays raw relative depth map (grayscale: 0.0 black near, 1.0 white far). */
    DEPTH,
    /** Displays initial raw subject segmentation mask before edge refinement. */
    SEGMENTATION,
    /** Displays final refined edge-matted portrait subject mask. */
    FINAL_MASK,
    /** Displays continuous depth-dependent blur weighting map. */
    BLUR_MAP
}

/**
 * Global configuration parameters for CAMELEO Portrait Depth Engine.
 */
data class PortraitConfig(
    val portraitMode: PortraitMode = PortraitMode.AUTO,
    val bokehStrength: BokehStrength = BokehStrength.SUBTLE,
    val depthWidth: Int = 256,
    val depthHeight: Int = 192,
    val maskThreshold: Float = 0.45f,
    val edgeRefinementRadius: Int = 3,
    val bokehHighlightThreshold: Float = 0.78f,
    val bokehHighlightBoost: Float = 1.35f,
    val previewUpdateRateHz: Int = 5,
    val preserveGlasses: Boolean = true,
    val hairRefinement: Boolean = true,
    val antiColorBleed: Boolean = true,
    val debugMaskMode: DebugMaskMode = DebugMaskMode.NONE
) {
    val activeBokehProfile: BokehProfile
        get() = BokehProfile.forStrength(bokehStrength)
}

