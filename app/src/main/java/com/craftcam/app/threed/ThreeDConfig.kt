package com.craftcam.app.threed

/**
 * Configuration parameters for the CAMELEO 3D Stereoscopic Wigglegram pipeline.
 *
 * Inspired by vintage multi-lens stereoscopic film cameras (e.g. Nimslo 3D, Nishika N8000)
 * that captured simultaneous horizontal viewpoints on 35mm film negatives.
 */
data class ThreeDConfig(
    /** Number of distinct physical lens viewpoints (4 for Nishika N8000 / Nimslo 3D quad-lens). */
    val frameCount: Int = 4,

    /** Configurable D3D parallax displacement parameter as a fraction of image width (0.032 = 3.2%). */
    val d3dParallaxStrength: Float = 0.032f,

    /** Maximum horizontal disparity displacement as a fraction of image width. Defaults to [d3dParallaxStrength]. */
    val maxParallaxDisplacement: Float = d3dParallaxStrength,

    /** Default fallback normalized disparity of the convergence / in-focus subject plane. */
    val pivotDisparity: Float = 0.50f,

    /** Transverse chromatic / RGB channel misalignment in pixels (subtle optical aberration). */
    val rgbSeparationPx: Float = 0.5f,

    /** Mechanical frame registration jitter amplitude in pixels (film gate tolerance). */
    val mechanicalJitterPx: Float = 0.25f,

    /** Frame-to-frame leaf shutter exposure variation multiplier amplitude (±0.8%). */
    val exposureVariance: Float = 0.008f,

    /** Authentic analog 35mm film grain blending intensity. */
    val grainIntensity: Float = 0.18f,

    /** Subtle optical lens softness diffusion radius (0.0 = tack sharp). */
    val softnessRadius: Float = 0.0f,

    /** Frame rate of the output animation in FPS (10 fps gives snappy authentic 4-lens snap). */
    val fps: Int = 10,

    /** Number of complete bounce loops in the exported MP4 video. */
    val loopCount: Int = 4,

    /** Whether to inject subtle vintage corner light leaks. */
    val lightLeakEnabled: Boolean = false,

    /** Light leak opacity / strength. */
    val lightLeakIntensity: Float = 0.12f,

    /** Whether to export debug depth, disparity, and viewpoint maps for verification. */
    val exportDebugMaps: Boolean = true
)

