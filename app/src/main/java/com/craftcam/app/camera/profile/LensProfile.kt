package com.craftcam.app.camera.profile

import androidx.compose.ui.graphics.Color

/**
 * Optical lens accessories and physical glass variations available for simulated CAMELEO cameras.
 */
enum class LensType(val displayName: String, val iconDescription: String) {
    NORMAL("Normal", "Standard optical prime glass"),
    WIDE("Wide", "Wide-angle optical conversion element"),
    FISHEYE_F("Fisheye F", "Full-frame aggressive curvilinear fisheye"),
    FISHEYE_W("Fisheye W", "Moderate wide-angle curvilinear fisheye"),
    PRISM("Prism", "Multi-faceted optical refraction and chromatic splitting"),
    STARBURST("Star", "Diffraction star rays around specular highlights"),
    ND("ND", "Neutral density light attenuation filter (-2 EV)"),
    COLOR_FLASH("Color Flash", "Tinted xenon flash gelatin filter")
}

enum class ColorFlashTint(val displayName: String, val rgb: Triple<Float, Float, Float>, val uiColor: Color) {
    NONE("Neutral", Triple(1.0f, 1.0f, 1.0f), Color.White),
    WARM("Warm Amber", Triple(1.0f, 0.72f, 0.40f), Color(0xFFFFB366)),
    COOL("Cool Blue", Triple(0.55f, 0.75f, 1.0f), Color(0xFF8CC5FF)),
    RED("Ruby Red", Triple(1.0f, 0.35f, 0.35f), Color(0xFFFF5555)),
    GREEN("Neon Green", Triple(0.40f, 1.0f, 0.55f), Color(0xFF66FF88))
}

/**
 * Concrete optical rendering profile for a specific lens accessory.
 * Controls GPU shader transformation in live preview and full-sensor capture rendering.
 */
data class LensProfile(
    val id: String,
    val name: String,
    val type: LensType,
    val distortionStrength: Float = 0.0f,      // Barrel/Fisheye coefficient
    val edgeCurvature: Float = 0.0f,           // 4th order polynomial expansion
    val vignetteStrength: Float = 0.0f,        // Peripheral darkening
    val chromaticAberration: Float = 0.0f,     // Cauchy spectral dispersion
    val edgeSoftness: Float = 0.0f,            // Perimeter optical aberration
    val centerSharpness: Float = 1.0f,         // Central subject retention
    val flareStrength: Float = 0.0f,           // Optical veiling flare
    val prismDispersion: Float = 0.0f,         // Multi-copy refraction displacement
    val starburstStrength: Float = 0.0f,       // Highlight ray intensity
    val starburstThreshold: Float = 0.85f,      // Minimum luminance for star rays
    val ndExposureReduction: Float = 0.0f,     // EV stops reduction (e.g. 2.0f for ND4)
    val ndTransmissionFactor: Float = 1.0f,    // Linear light multiplier (0.28 for ~2 stops)
    val colorFlashTint: ColorFlashTint = ColorFlashTint.NONE
) {
    companion object {
        val NORMAL = LensProfile(
            id = "lens_normal",
            name = "Normal",
            type = LensType.NORMAL,
            centerSharpness = 1.0f
        )

        val WIDE = LensProfile(
            id = "lens_wide",
            name = "Wide",
            type = LensType.WIDE,
            distortionStrength = 0.12f,
            vignetteStrength = 0.18f,
            chromaticAberration = 0.003f,
            edgeSoftness = 0.05f
        )

        val FISHEYE_F = LensProfile(
            id = "lens_fisheye_f",
            name = "Fisheye F",
            type = LensType.FISHEYE_F,
            distortionStrength = 0.92f,
            edgeCurvature = 0.45f,
            vignetteStrength = 0.15f,
            chromaticAberration = 0.022f,
            edgeSoftness = 0.20f,
            centerSharpness = 1.0f
        )

        val FISHEYE_W = LensProfile(
            id = "lens_fisheye_w",
            name = "Fisheye W",
            type = LensType.FISHEYE_W,
            distortionStrength = 0.72f,
            edgeCurvature = 0.25f,
            vignetteStrength = 0.30f,
            chromaticAberration = 0.012f,
            edgeSoftness = 0.10f,
            centerSharpness = 1.0f
        )

        val PRISM = LensProfile(
            id = "lens_prism",
            name = "Prism",
            type = LensType.PRISM,
            prismDispersion = 0.85f,
            chromaticAberration = 0.012f,
            edgeSoftness = 0.14f,
            flareStrength = 0.25f,
            centerSharpness = 0.95f
        )

        val STARBURST = LensProfile(
            id = "lens_starburst",
            name = "Star",
            type = LensType.STARBURST,
            starburstStrength = 0.65f,
            starburstThreshold = 0.84f,
            flareStrength = 0.20f
        )

        val ND = LensProfile(
            id = "lens_nd",
            name = "ND",
            type = LensType.ND,
            ndExposureReduction = 2.0f,
            ndTransmissionFactor = 0.28f,
            vignetteStrength = 0.12f
        )

        val COLOR_FLASH_WARM = LensProfile(
            id = "lens_color_flash_warm",
            name = "Warm Flash",
            type = LensType.COLOR_FLASH,
            colorFlashTint = ColorFlashTint.WARM
        )

        fun forColorFlash(tint: ColorFlashTint): LensProfile = LensProfile(
            id = "lens_color_flash_${tint.name.lowercase()}",
            name = "${tint.displayName} Flash",
            type = LensType.COLOR_FLASH,
            colorFlashTint = tint
        )

        fun forType(type: LensType): LensProfile = when (type) {
            LensType.NORMAL -> NORMAL
            LensType.WIDE -> WIDE
            LensType.FISHEYE_F -> FISHEYE_F
            LensType.FISHEYE_W -> FISHEYE_W
            LensType.PRISM -> PRISM
            LensType.STARBURST -> STARBURST
            LensType.ND -> ND
            LensType.COLOR_FLASH -> COLOR_FLASH_WARM
        }
    }
}

