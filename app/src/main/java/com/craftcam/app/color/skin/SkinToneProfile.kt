package com.craftcam.app.color.skin

import com.craftcam.app.color.digicam.DigicamIlluminant
import com.craftcam.app.model.CameraCategory

/**
 * Camera-profile-specific skin tone response and protection configuration (Layer 4).
 *
 * Each camera profile reproduces human skin according to its authentic historical/photographic identity:
 * - D FUNS: Disposable 35mm film negative response, warm flash highlights, exposure-dependent saturation.
 * - CPM35: Golden early digital compact, rich amber warmth, soft optical detail, rich lower-midtones.
 * - CCD: 2000s pocket digicam, golden daylight skin, high flash contrast, yellow-green artificial tolerance.
 * - FXNR: Fine 35mm film dye clouds, pastel highlights, gentle contrast, natural teals.
 * - NATURAL: Unfiltered reference multi-complexion fidelity across Fitzpatrick Types I through VI.
 */
data class SkinToneProfile(
    val id: String,
    val name: String,
    val config: SkinToneConfig = SkinToneConfig(),

    /** Desired skin hue bias shift in degrees (positive = warm golden, negative = pink/cool; capped by maximumHueShift). */
    val targetHueBias: Float = 0.0f,

    /** Warm golden skin tint gain multipliers [R, G, B] applied proportionally to skin confidence. */
    val skinGains: FloatArray = floatArrayOf(1.0f, 1.0f, 1.0f),

    /** Lower-midtone lift specifically for human skin (prevents harsh facial shadows). */
    val skinMidtoneLift: Float = 0.0f,

    /** Highlight rolloff knee for skin highlights (prevents specular blowout on foreheads and cheeks). */
    val skinHighlightKnee: Float = 0.85f,

    /** Maximum allowed saturation for skin in this profile. */
    val maxSkinSaturation: Float = 0.65f,

    /** Amount of film grain / electronic noise dampening on smooth facial skin. */
    val textureDampening: Float = 0.20f
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as SkinToneProfile
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()
}

object SkinToneProfileCatalog {

    /**
     * D FUNS: Single-use 35mm disposable camera.
     * Organic film negative response, warm direct-flash falloff, natural flash-lit skin, believable film highlights.
     */
    val D_FUNS = SkinToneProfile(
        id = "skin_dfuns",
        name = "D Funs Disposable Film",
        config = SkinToneConfig(
            warmthStrength = 0.18f,
            saturationProtection = 0.88f,
            maxAllowedSkinSaturation = 0.68f,
            highlightProtection = 0.90f,
            shadowProtection = 0.80f,
            maximumHueShiftDegrees = 4.0f
        ),
        targetHueBias = 2.0f, // Subtle warm golden-amber film shift
        skinGains = floatArrayOf(1.06f, 0.98f, 0.90f),
        skinMidtoneLift = 0.035f,
        skinHighlightKnee = 0.82f,
        maxSkinSaturation = 0.68f,
        textureDampening = 0.25f // Preserves organic film grain while preventing excessive noise on faces
    )

    /**
     * CPM35: Golden early digital compact.
     * Reference-calibrated against Refs 1, 2, 3, 6:
     * Golden illuminated skin, amber tungsten retention, rich lower-midtones, soft optical detail.
     */
    val CPM35 = SkinToneProfile(
        id = "skin_cpm35",
        name = "CPM35 Golden Compact",
        config = SkinToneConfig(
            warmthStrength = 0.22f,
            saturationProtection = 0.85f,
            maxAllowedSkinSaturation = 0.70f,
            highlightProtection = 0.86f,
            shadowProtection = 0.85f,
            maximumHueShiftDegrees = 5.0f
        ),
        targetHueBias = 3.5f, // Radiant golden-amber cast matching references
        skinGains = floatArrayOf(1.08f, 0.97f, 0.86f),
        skinMidtoneLift = 0.045f,
        skinHighlightKnee = 0.80f,
        maxSkinSaturation = 0.70f,
        textureDampening = 0.20f
    )

    /**
     * CCD: 2000s pocket digicam.
     * Reference-calibrated against Refs 4, 5, 7, 8:
     * Golden daylight skin, neutral highlights, direct-flash contrast, yellow-green artificial tolerance, crisp texture.
     */
    val CCD = SkinToneProfile(
        id = "skin_ccd",
        name = "CCD Y2K Digicam",
        config = SkinToneConfig(
            warmthStrength = 0.12f,
            saturationProtection = 0.86f,
            maxAllowedSkinSaturation = 0.66f,
            highlightProtection = 0.92f,
            shadowProtection = 0.80f,
            maximumHueShiftDegrees = 3.5f
        ),
        targetHueBias = 1.0f, // Balanced clean golden daylight
        skinGains = floatArrayOf(1.03f, 1.00f, 0.94f),
        skinMidtoneLift = 0.020f,
        skinHighlightKnee = 0.88f,
        maxSkinSaturation = 0.66f,
        textureDampening = 0.15f
    )

    /**
     * FXNR: Classic 35mm fine-grain color negative.
     * Natural portrait skin tones, pastel greens, clean teals, and subtle contrast rolloff.
     */
    val FXNR = SkinToneProfile(
        id = "skin_fxnr",
        name = "FXNR Classic Negative",
        config = SkinToneConfig(
            warmthStrength = 0.14f,
            saturationProtection = 0.88f,
            maxAllowedSkinSaturation = 0.64f,
            highlightProtection = 0.88f,
            shadowProtection = 0.82f,
            maximumHueShiftDegrees = 3.0f
        ),
        targetHueBias = 0.5f,
        skinGains = floatArrayOf(1.02f, 0.99f, 0.96f),
        skinMidtoneLift = 0.025f,
        skinHighlightKnee = 0.86f,
        maxSkinSaturation = 0.64f,
        textureDampening = 0.22f
    )

    /**
     * NATURAL: Reference flagship computational photography.
     * Strict multi-complexion neutral fidelity across Fitzpatrick Types I to VI.
     */
    val NATURAL = SkinToneProfile(
        id = "skin_natural",
        name = "Natural Flagship Portrait",
        config = SkinToneConfig(
            warmthStrength = 0.05f,
            saturationProtection = 0.90f,
            maxAllowedSkinSaturation = 0.62f,
            highlightProtection = 0.92f,
            shadowProtection = 0.85f,
            maximumHueShiftDegrees = 2.0f
        ),
        targetHueBias = 0.0f,
        skinGains = floatArrayOf(1.00f, 1.00f, 1.00f),
        skinMidtoneLift = 0.015f,
        skinHighlightKnee = 0.90f,
        maxSkinSaturation = 0.62f,
        textureDampening = 0.35f
    )

    fun getProfileForPreset(presetId: String, category: CameraCategory? = null): SkinToneProfile {
        return when {
            presetId == "ccd_flash" || presetId == "d_funs" -> D_FUNS
            presetId == "disp_flash" || presetId == "disp_cpm35" || presetId.contains("cpm", ignoreCase = true) -> CPM35
            presetId == "ccd_r" || presetId == "ccd_retro_compact" || category == CameraCategory.CCD_DIGITAL -> CCD
            presetId == "analog_fxn_r" || presetId == "fxn_r" || category == CameraCategory.ANALOG_FILM -> FXNR
            presetId == "zero" -> NATURAL
            else -> FXNR
        }
    }
}
