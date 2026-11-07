package com.craftcam.app.color

/**
 * Standard preset profile selectors.
 */
enum class ColorProfileType(val displayName: String) {
    NATURAL("CAMELEO Natural (Calibrated)"),
    PORTRAIT("CAMELEO Portrait (Skin-Aware)"),
    NEUTRAL("CAMELEO Neutral (Linear Baseline)"),
    WARM("CAMELEO Warm (Golden Hour)"),
    COOL("CAMELEO Cool (Blue Hour)")
}

/**
 * Configuration and User Tuning parameters for CAMELEO Color Science Engine.
 */
data class ColorScienceConfig(
    val activeProfileType: ColorProfileType = ColorProfileType.NATURAL,
    val targetColorSpace: ColorSpaceType = ColorSpaceType.SRGB,
    val enableSensorMatrix: Boolean = true,
    val enableToneCurve: Boolean = true,
    val enableHighlightRollOff: Boolean = true,
    val enableSkinProtection: Boolean = true,
    val enableVibrance: Boolean = true,
    val enableChromaNoiseReduction: Boolean = true,
    val enableDithering: Boolean = true,
    val customKelvin: Int = 5500,
    val customTint: Float = 0.0f,
    val saturationOffset: Float = 0.0f,
    val vibranceOffset: Float = 0.0f,
    val contrastOffset: Float = 0.0f
) {
    val activeProfile: ColorProfile
        get() = when (activeProfileType) {
            ColorProfileType.NATURAL -> ColorProfile.Natural
            ColorProfileType.PORTRAIT -> ColorProfile.Portrait
            ColorProfileType.NEUTRAL -> ColorProfile.Neutral
            ColorProfileType.WARM -> ColorProfile.Warm
            ColorProfileType.COOL -> ColorProfile.Cool
        }
}

