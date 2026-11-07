package com.craftcam.app.model

/**
 * CAMELEO Natural Camera Profile
 *
 * Configures the flagship-style computational photography pipeline:
 * - Highlight-aware exposure bias to prevent sensor clipping
 * - Radiometric linear color matrix and neutral white balance
 * - Asymptotic highlight shoulder roll-off
 * - Soft HSL skin-tone protection
 * - Natural saturation (skin/sky/foliage controlled)
 * - Gentle shadow lift without graying deep blacks
 * - Chroma noise reduction
 * - Edge-aware restrained sharpening and microcontrast
 */
data class NaturalCameraProfile(
    val exposureBias: Float = -0.3f,        // -1.0 to +0.3 EV (default -0.3 EV)
    val temperature: Float = 0.0f,          // -0.5 (cool) to +0.5 (warm)
    val tint: Float = 0.0f,                 // -0.5 (green) to +0.5 (magenta)
    val saturation: Float = 1.02f,          // 0.8 to 1.3
    val contrast: Float = 1.04f,            // 0.8 to 1.3
    val shadowLift: Float = 0.12f,          // 0.0 to 0.35 (elevates shadow details)
    val highlightRollOff: Float = 0.85f,    // 0.5 to 1.0 (asymptotic shoulder strength)
    val noiseReduction: Float = 0.25f,      // 0.0 to 1.0 (chroma-focused noise reduction)
    val sharpening: Float = 0.20f,          // 0.0 to 0.8 (edge-aware, skin-dampened)
    val localContrast: Float = 0.10f,       // 0.0 to 0.4 (subtle microcontrast)
    val skinProtection: Float = 0.80f       // 0.0 to 1.0 (soft HSL skin mask protection)
) {
    companion object {
        val DEFAULT = NaturalCameraProfile()

        // Calibrated baseline for Samsung Galaxy S21 5G (Mali-G78 / 12.2MP Isocell sensor)
        val S21_PROFILE = NaturalCameraProfile(
            exposureBias = -0.3f,
            temperature = 0.0f,
            tint = 0.0f,
            saturation = 1.00f, // Tames Samsung default oversaturation
            contrast = 1.03f,
            shadowLift = 0.14f,
            highlightRollOff = 0.88f,
            noiseReduction = 0.28f,
            sharpening = 0.18f,
            localContrast = 0.10f,
            skinProtection = 0.85f
        )
    }
}

data class NaturalColorConfig(
    val temperature: Float = 0.0f,
    val tint: Float = 0.0f,
    val saturation: Float = 1.0f,
    val contrast: Float = 1.0f
)

enum class CameraEngineMode(val displayName: String) {
    NATURAL("NATURAL"),
    FILM("FILM"),
    PRO("PRO")
}

