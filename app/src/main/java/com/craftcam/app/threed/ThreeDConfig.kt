package com.craftcam.app.threed

/**
 * Configuration parameters for the CAMELEO 3D Stereoscopic Wigglegram pipeline.
 *
 * Inspired by vintage multi-lens stereoscopic film cameras (e.g. Nimslo 3D, Nishika N8000)
 * that captured simultaneous horizontal viewpoints on 35mm film negatives.
 */
data class ThreeDConfig(
    /** Number of distinct parallax viewpoints generated across the oscillation cycle. */
    val frameCount: Int = 12,

    /** Maximum horizontal disparity displacement as a fraction of image width (e.g. 0.022 = 2.2%). */
    val maxParallaxDisplacement: Float = 0.022f,

    /** Normalized disparity of the convergence / in-focus subject plane (0.0 far, 1.0 near). */
    val pivotDisparity: Float = 0.40f,

    /** Transverse chromatic / RGB channel misalignment in pixels (subtle optical aberration). */
    val rgbSeparationPx: Float = 1.2f,

    /** Mechanical frame registration jitter amplitude in pixels (film gate tolerance). */
    val mechanicalJitterPx: Float = 0.4f,

    /** Frame-to-frame leaf shutter exposure variation multiplier amplitude (±1.5%). */
    val exposureVariance: Float = 0.015f,

    /** Authentic analog 35mm film grain blending intensity. */
    val grainIntensity: Float = 0.16f,

    /** Subtle optical lens softness diffusion radius. */
    val softnessRadius: Float = 0.6f,

    /** Frame rate of the output animation in FPS. */
    val fps: Int = 15,

    /** Number of complete sinusoidal oscillation loops in the exported MP4 video. */
    val loopCount: Int = 3,

    /** Whether to inject subtle vintage corner light leaks. */
    val lightLeakEnabled: Boolean = true,

    /** Light leak opacity / strength. */
    val lightLeakIntensity: Float = 0.18f
)

