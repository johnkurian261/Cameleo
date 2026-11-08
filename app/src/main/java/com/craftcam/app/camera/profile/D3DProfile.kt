package com.craftcam.app.camera.profile

/**
 * Configuration parameters for the CAMELEO D3D single-image 3D photography filter.
 *
 * Transforms a single captured photograph into an authentic stereoscopic 3D image
 * via depth estimation, depth-dependent parallax displacement, virtual stereo synthesis,
 * chromatic separation, and 3D compositing.
 */
data class D3DProfile(
    /** Overall depth effect strength multiplier (0.0 to 1.0, default 0.40 = 40%). */
    val depthStrength: Float = 0.40f,

    /** Maximum horizontal parallax displacement as fraction of image width (0.015 = 1.5%). */
    val parallaxStrength: Float = 0.015f,

    /** Depth-dependent transverse chromatic separation amplitude as fraction of image width. */
    val rgbSeparation: Float = 0.0035f,

    /** Normalized foreground depth plane threshold [0.0..1.0]. */
    val foregroundDepth: Float = 0.75f,

    /** Normalized background depth plane threshold [0.0..1.0]. */
    val backgroundDepth: Float = 0.25f,

    /** Cross-bilateral edge boundary protection weight to eliminate depth halos around objects. */
    val edgeProtection: Float = 0.85f,

    /** Face geometry protection factor (0.0 = full warp, 1.0 = face anchored with 0 distortion). */
    val faceProtection: Float = 0.90f,

    /** Cross-bilateral spatial depth smoothing radius. */
    val depthBlur: Float = 1.5f,

    /** Subtle optical lens softness (0.0 = tack sharp). */
    val opticalSoftness: Float = 0.0f,

    /** Subtle peripheral optical vignette falloff. */
    val vignetteStrength: Float = 0.20f,

    /** Anaglyph blend strength (0.0 = D3D Classic color depth, 1.0 = Full Red/Cyan stereoscopic 3D). */
    val anaglyphStrength: Float = 0.0f,

    /** Whether the filter outputs in stereoscopic Red/Cyan Anaglyph mode. */
    val isAnaglyphMode: Boolean = false
) {
    companion object {
        val Default = D3DProfile()
        val Anaglyph = D3DProfile(
            anaglyphStrength = 1.0f,
            isAnaglyphMode = true,
            rgbSeparation = 0.006f
        )
    }
}

