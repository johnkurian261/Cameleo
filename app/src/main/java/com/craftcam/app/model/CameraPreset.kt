package com.craftcam.app.model

enum class CameraCategory(val displayName: String, val description: String) {
    ANALOG_FILM("Analog Film", "Warm chemical emulsion, organic silver halide grain and natural highlight roll-off"),
    CCD_DIGITAL("CCD Digital", "Crisp 2000s sensor aesthetic with punchy primary colors and digital clarity"),
    DISPOSABLE("Disposable", "High contrast, plastic lens aberrations and vintage party flash falloff"),
    INSTANT("Instant", "Soft pastel tones, lifted black point and iconic white paper borders"),
    CAMCORDER("Camcorder", "Magnetic tape scanlines, horizontal color bleeding and retro OSD stamps")
}

data class ColorGradingConfig(
    val contrast: Float = 1.0f,
    val saturation: Float = 1.0f,
    val temperature: Float = 0.0f, // -1.0 to 1.0 (cool to warm)
    val tint: Float = 0.0f,        // -1.0 to 1.0 (green to magenta)
    val gamma: Float = 1.0f,
    val fade: Float = 0.0f,        // 0.0 to 0.3 (lifted blacks)
    val highlightRollOff: Float = 0.85f,
    val shadowTintR: Float = 0.0f,
    val shadowTintG: Float = 0.0f,
    val shadowTintB: Float = 0.0f
)

data class GrainConfig(
    val enabled: Boolean = true,
    val intensity: Float = 0.12f,
    val scale: Float = 1.0f,
    val isColorNoise: Boolean = false
)

data class LensConfig(
    val vignetteIntensity: Float = 0.45f,
    val vignetteRadius: Float = 0.75f,
    val chromaticAberration: Float = 0.005f,
    val barrelDistortion: Float = 0.0f, // negative for barrel, positive for pincushion
    val softness: Float = 0.0f
)

data class LightLeakConfig(
    val enabled: Boolean = false,
    val intensity: Float = 0.4f,
    val warmColorBias: Float = 0.8f // 1.0 for deep red/orange
)

data class HalationConfig(
    val enabled: Boolean = false,
    val threshold: Float = 0.82f,
    val intensity: Float = 0.35f,
    val radius: Float = 16.0f,
    val haloRed: Float = 1.0f,
    val haloGreen: Float = 0.22f,
    val haloBlue: Float = 0.04f
)

enum class DateStampStyle {
    SEVEN_SEGMENT_ORANGE,
    DIGITAL_LCD_GREEN,
    MONOSPACE_DOT_MATRIX,
    MINIMAL_WHITE
}

data class DateStampConfig(
    val enabled: Boolean = true,
    val style: DateStampStyle = DateStampStyle.SEVEN_SEGMENT_ORANGE,
    val customYearOffset: Int = 0 // e.g. -28 to show 1998
)

enum class BorderStyle {
    NONE,
    CLASSIC_35MM_BLACK,
    POLAROID_WHITE_CHIN,
    SQUARE_INSTANT_WHITE,
    ROUNDED_8MM,
    CAMCORDER_FRAME
}

data class BorderConfig(
    val style: BorderStyle = BorderStyle.NONE
)

data class CameraPreset(
    val id: String,
    val name: String,
    val subtitle: String,
    val category: CameraCategory,
    val year: String,
    val description: String,
    val colorGrading: ColorGradingConfig,
    val grain: GrainConfig = GrainConfig(),
    val lens: LensConfig = LensConfig(),
    val lightLeak: LightLeakConfig? = null,
    val halation: HalationConfig? = null,
    val dateStamp: DateStampConfig? = DateStampConfig(),
    val border: BorderConfig? = BorderConfig(),
    val sampleResName: String = "sample_${id}",
    val iconResName: String = "cam_classic"
)

