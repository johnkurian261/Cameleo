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

enum class GrainBlendMode {
    OVERLAY,
    SCREEN,
    MULTIPLY,
    NORMAL
}

enum class GrainSourceMode {
    SCANNED_ONLY,
    SCANNED_PLUS_PROCEDURAL,
    PROCEDURAL_ONLY
}

data class GrainConfig(
    val enabled: Boolean = true,
    val intensity: Float = 0.12f,
    val scale: Float = 1.0f,
    val isColorNoise: Boolean = false,
    val assetPath: String? = null,
    val blendMode: GrainBlendMode = GrainBlendMode.OVERLAY,
    val shadowStrength: Float = 0.28f,
    val highlightStrength: Float = 0.12f,
    val rotation: Float = 0.0f,
    val sourceMode: GrainSourceMode = GrainSourceMode.SCANNED_ONLY
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

data class DustConfig(
    val enabled: Boolean = true,
    val assetPath: String = "dust_135.png",
    val opacity: Float = 0.60f
)

data class FlashOverlayConfig(
    val enabled: Boolean = false,
    val assetPath: String = "flashc_01.jpg",
    val opacity: Float = 0.65f
)

enum class BorderStyle {
    NONE,
    GOLF_FRAME,               // golf_border_*.png / golf_frame.jpg
    HALF_FRAME,               // half_border.png
    CLASSIC_35MM_RANGEFINDER, // dbalck_frame_in.png & dbalck_frame_out_*.png
    FILM_STRIP_135,           // 135ne_frame_cut1.png
    MEDIUM_FORMAT_67,         // s67_frame_t1.png & s67_frame_b_01.jpg
    SLIDE_MOUNT,              // border_slidep1.jpg..4.jpg
    POLAROID_WHITE_CHIN,      // vinst_border1.png..3.png
    SQUARE_INSTANT_WHITE,     // Square instant white frame
    ROUNDED_16MM_GATE,        // mask_16mm_01_r.jpg
    CAMCORDER_FRAME,          // CRT scanline frame with REC indicator
    CLASSIC_35MM_BLACK,       // Generic 35mm rebate
    ROUNDED_8MM               // Legacy 8mm
}

data class BorderConfig(
    val style: BorderStyle = BorderStyle.NONE,
    val assetPath: String? = null,
    val showInViewfinder: Boolean = false
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
    val dust: DustConfig? = null,
    val flashOverlay: FlashOverlayConfig? = null,
    val dateStamp: DateStampConfig? = DateStampConfig(),
    val border: BorderConfig? = BorderConfig(),
    val d3dProfile: com.craftcam.app.camera.profile.D3DProfile? = null,
    val sampleResName: String = "sample_${id}",
    val iconResName: String = "cam_classic"
)

