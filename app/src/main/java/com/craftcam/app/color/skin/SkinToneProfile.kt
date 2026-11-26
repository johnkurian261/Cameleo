package com.craftcam.app.color.skin

import com.craftcam.app.color.digicam.DigicamIlluminant
import com.craftcam.app.model.CameraCategory

/**
 * Camera-profile-specific skin tone response and protection configuration (Layer 4).
 *
 * Each camera profile reproduces human skin according to its authentic historical/photographic identity:
 * - D FUNS: Disposable 35mm film negative response, warm flash highlights, organic exposure falloff.
 * - CPM35: Golden early digital compact, rich amber warmth, soft optical detail, deep contrast shadows.
 * - CCD: 2000s pocket digicam, golden daylight skin, high flash contrast, yellow-green artificial tolerance.
 * - FXNR: Fine 35mm film dye clouds, pastel highlights, gentle contrast, natural teals.
 * - NATURAL: Unfiltered reference multi-complexion fidelity across all human complexions.
 */
data class SkinToneProfile(
    val id: String,
    val name: String,
    val config: SkinToneConfig = SkinToneConfig(),

    /** Desired skin hue bias shift in degrees (positive = warm golden, negative = pink/cool; capped by maximumHueShift). */
    val targetHueBias: Float = 0.0f,

    /** Warm golden skin tint gain multipliers [R, G, B] applied proportionally to skin confidence. */
    val skinGains: FloatArray = floatArrayOf(1.0f, 1.0f, 1.0f),

    /** Restrained lower-midtone lift specifically for human skin (prevents harsh facial shadows without flattening contrast). */
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
     * Organic colour negative film response, soft highlight compression, preserved blue channel,
     * and natural red-orange skin relationships (calibrated against Kodak FunSaver / Fujifilm QuickSnap).
     */
    val D_FUNS = SkinToneProfile(
        id = "skin_dfuns",
        name = "D Funs Disposable Film",
        config = SkinToneConfig(
            warmthStrength = 0.12f,
            saturationProtection = 0.90f,
            maxAllowedSkinSaturation = 0.65f,
            highlightProtection = 0.92f,
            shadowProtection = 0.82f,
            maximumHueShiftDegrees = 3.5f,
            yellowCastProtectionEnabled = true,
            yellowCastProtectionStrength = 0.80f
        ),
        targetHueBias = -1.2f, // Steers towards organic red-orange (h ~ 48..55 deg), away from golden yellow
        skinGains = floatArrayOf(1.02f, 0.99f, 0.98f), // Preserves blue channel (no blue starvation!)
        skinMidtoneLift = 0.008f, // Subtle film toe lift
        skinHighlightKnee = 0.88f, // Soft negative film shoulder compression
        maxSkinSaturation = 0.65f,
        textureDampening = 0.22f // Preserves organic film grain
    )

    /**
     * CPM35: Golden early digital compact.
     * Calibrated against verified digicam references:
     * Golden illuminated skin, amber tungsten retention, deep contrast shadows, soft optical detail.
     * Crisp digital direct flash highlights with source-relative protection.
     */
    val CPM35 = SkinToneProfile(
        id = "skin_cpm35",
        name = "CPM35 Golden Compact",
        config = SkinToneConfig(
            warmthStrength = 0.20f,
            saturationProtection = 0.85f,
            maxAllowedSkinSaturation = 0.70f,
            highlightProtection = 0.86f,
            shadowProtection = 0.85f,
            maximumHueShiftDegrees = 4.5f,
            yellowCastProtectionEnabled = true,
            yellowCastProtectionStrength = 0.60f // Guards against mustard under daylight; retains amber bloom under tungsten
        ),
        targetHueBias = 3.5f, // Radiant golden-amber cast matching references
        skinGains = floatArrayOf(1.04f, 0.98f, 0.93f),
        skinMidtoneLift = 0.008f, // Restrained to preserve deep shadows and authentic digicam punch
        skinHighlightKnee = 0.96f, // Crisp digital direct flash highlights (harder digital clipping)
        maxSkinSaturation = 0.70f,
        textureDampening = 0.12f
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
            warmthStrength = 0.10f,
            saturationProtection = 0.88f,
            maxAllowedSkinSaturation = 0.66f,
            highlightProtection = 0.94f,
            shadowProtection = 0.80f,
            maximumHueShiftDegrees = 3.0f,
            yellowCastProtectionEnabled = true,
            yellowCastProtectionStrength = 0.85f
        ),
        targetHueBias = 1.0f, // Balanced clean golden daylight, punchy flash
        skinGains = floatArrayOf(1.02f, 1.00f, 0.96f),
        skinMidtoneLift = 0.003f, // Minimal midtone lift to preserve authentic digicam contrast
        skinHighlightKnee = 0.96f, // Crisp digital direct flash highlights (harder digital clipping)
        maxSkinSaturation = 0.66f,
        textureDampening = 0.08f // Sharp digital electronic texture
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
        skinMidtoneLift = 0.015f,
        skinHighlightKnee = 0.86f,
        maxSkinSaturation = 0.64f,
        textureDampening = 0.22f
    )

    /**
     * D3D: Motion picture single-image 3D photography (Kodak Vision3 500T).
     * Reference-calibrated against genuine D3D portraits:
     * Balanced red-orange warmth, natural skin reflectance, blue channel preservation,
     * and targeted yellow-cast protection (prevents mustard skin without pink overcorrection).
     */
    val D3D = SkinToneProfile(
        id = "skin_d3d",
        name = "D3D Motion Picture 3D",
        config = SkinToneConfig(
            warmthStrength = 0.08f,
            saturationProtection = 0.90f,
            maxAllowedSkinSaturation = 0.65f,
            highlightProtection = 0.92f,
            shadowProtection = 0.85f,
            maximumHueShiftDegrees = 3.0f,
            yellowCastProtectionEnabled = true,
            yellowCastProtectionStrength = 0.85f
        ),
        targetHueBias = 0.0f,
        skinGains = floatArrayOf(1.00f, 1.00f, 1.00f),
        skinMidtoneLift = 0.008f,
        skinHighlightKnee = 0.95f,
        maxSkinSaturation = 0.65f,
        textureDampening = 0.10f
    )

    /**
     * NATURAL: Reference flagship computational photography.
     * Strict multi-complexion neutral fidelity across all human complexions.
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
        skinMidtoneLift = 0.008f,
        skinHighlightKnee = 0.90f,
        maxSkinSaturation = 0.62f,
        textureDampening = 0.35f
    )

    fun getProfileForPreset(presetId: String, category: CameraCategory? = null): SkinToneProfile {
        return when {
            presetId == "analog_cinema_film" || presetId == "d3d" || presetId == "d_3d" || presetId.equals("d 3d", ignoreCase = true) || presetId.contains("cinema", ignoreCase = true) -> D3D
            presetId == "ccd_flash" || presetId == "d_funs" -> D_FUNS
            presetId == "disp_flash" || presetId == "disp_cpm35" || presetId.contains("cpm", ignoreCase = true) -> CPM35
            presetId == "ccd_r" || presetId == "ccd_retro_compact" || category == CameraCategory.CCD_DIGITAL -> CCD
            presetId == "analog_fxn_r" || presetId == "fxn_r" || category == CameraCategory.ANALOG_FILM -> FXNR
            presetId == "zero" -> NATURAL
            else -> FXNR
        }
    }
}
