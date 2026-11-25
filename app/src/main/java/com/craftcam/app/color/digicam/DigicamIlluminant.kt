package com.craftcam.app.color.digicam

/**
 * The four reference-calibrated lighting regimes for Y2K compact digital cameras:
 *
 * 1. [GOLDEN_DAYLIGHT]: Natural golden sunlight, preserved neutral whites, warm illuminated skin, natural greens (Refs 4, 5).
 * 2. [AMBER_TUNGSTEN]: Strong amber indoor lighting, warm yellow skin, attenuated blue channel, warm brown shadows (Refs 1, 3, 6).
 * 3. [GOLDEN_DIRECT_FLASH]: Bright foreground skin, golden facial highlights, deep shadows, rich saturated red reproduction (Refs 2, 5).
 * 4. [YELLOW_GREEN_ARTIFICIAL]: Yellow-green/olive environmental cast, warm yellow skin, muted blues, glowing artificial lights (Refs 7, 8).
 */
enum class DigicamIlluminant {
    GOLDEN_DAYLIGHT,
    AMBER_TUNGSTEN,
    GOLDEN_DIRECT_FLASH,
    YELLOW_GREEN_ARTIFICIAL
}

/**
 * Camera identity selection between CPM35 and CCD:
 *
 * - [CPM35]: Golden nostalgic digital compact (Refs 1, 2, 3, 6) with rich amber warmth, golden skin, rich reds, softer optical detail, subtle amber date stamp.
 * - [CCD]: Classic pocket digicam (Refs 4, 5, 7, 8) with golden sunlight, yellow-green artificial lighting, punchy flash contrast, limited dynamic range, classic yellow date stamp.
 */
enum class DigicamModel {
    CPM35,
    CCD
}

/**
 * Calibrated parameters for an illuminant profile.
 */
data class IlluminantParameters(
    val illuminant: DigicamIlluminant,
    /** Primary channel multipliers [R, G, B] in linear scene space. */
    val gains: FloatArray,
    /** Shadow color tint offsets [R, G, B]. */
    val shadowTint: FloatArray,
    /** Environmental color retention ratio (1.0 = full ambient cast, 0.0 = fully neutralized white balance). */
    val retentionRatio: Float,
    /** Boost multiplier specifically for pure scarlet/crimson reds (hue 345° to 15°). */
    val redSaturationBoost: Float,
    /** Tone curve contrast multiplier. */
    val contrast: Float,
    /** Saturation multiplier across midtones. */
    val saturation: Float,
    /** Dynamic range highlight rolloff shoulder knee (0.0 to 1.0). */
    val highlightRollOff: Float,
    /** Toe lift for shadow detail preservation (0.0 to 0.1). */
    val toeLift: Float,
    /** Optical scatter / glow intensity around bright lights. */
    val opticalScatterIntensity: Float,
    /** Electronic noise level multiplier. */
    val noiseMultiplier: Float
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is IlluminantParameters) return false
        return illuminant == other.illuminant &&
                gains.contentEquals(other.gains) &&
                shadowTint.contentEquals(other.shadowTint) &&
                retentionRatio == other.retentionRatio &&
                redSaturationBoost == other.redSaturationBoost &&
                contrast == other.contrast &&
                saturation == other.saturation &&
                highlightRollOff == other.highlightRollOff &&
                toeLift == other.toeLift &&
                opticalScatterIntensity == other.opticalScatterIntensity &&
                noiseMultiplier == other.noiseMultiplier
    }

    override fun hashCode(): Int {
        var result = illuminant.hashCode()
        result = 31 * result + gains.contentHashCode()
        result = 31 * result + shadowTint.contentHashCode()
        result = 31 * result + retentionRatio.hashCode()
        result = 31 * result + redSaturationBoost.hashCode()
        result = 31 * result + contrast.hashCode()
        result = 31 * result + saturation.hashCode()
        result = 31 * result + highlightRollOff.hashCode()
        result = 31 * result + toeLift.hashCode()
        result = 31 * result + opticalScatterIntensity.hashCode()
        result = 31 * result + noiseMultiplier.hashCode()
        return result
    }
}
