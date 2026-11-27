package com.craftcam.app.thermal

/**
 * Selectable false-color palettes for CAMELEO's simulated thermal vision camera.
 *
 * Notice: CAMELEO estimates relative apparent thermal contrast visually from visible-light
 * RGB cues and optional human segmentation. It does not measure physical temperatures.
 */
enum class ThermalPalette(
    val index: Int,
    val id: String,
    val displayName: String,
    val shortLabel: String,
    val swatchColorsArgb: List<Long>
) {
    RAINBOW_THERMAL(
        index = 0,
        id = "rainbow_thermal",
        displayName = "Rainbow Thermal",
        shortLabel = "RAINBOW",
        swatchColorsArgb = listOf(0xFF12052CL, 0xFF232EA8L, 0xFF0AA8DCL, 0xFF19C65EL, 0xFFF4E022L, 0xFFD91818L)
    ),
    IRONBOW(
        index = 1,
        id = "ironbow",
        displayName = "Ironbow",
        shortLabel = "IRONBOW",
        swatchColorsArgb = listOf(0xFF06030EL, 0xFF3E0C6EL, 0xFF9E1E5CL, 0xFFDC4622L, 0xFFFA920AL, 0xFFFFF79CL)
    ),
    RED_HOT(
        index = 2,
        id = "red_hot",
        displayName = "Red Hot",
        shortLabel = "RED HOT",
        swatchColorsArgb = listOf(0xFF071538L, 0xFF15458FL, 0xFF8D2652L, 0xFFE32419L, 0xFFFF6A45L)
    ),
    WHITE_HOT(
        index = 3,
        id = "white_hot",
        displayName = "White Hot",
        shortLabel = "WHT HOT",
        swatchColorsArgb = listOf(0xFF07080AL, 0xFF454950L, 0xFFA8ADB5L, 0xFFF8FAFCL)
    ),
    BLACK_HOT(
        index = 4,
        id = "black_hot",
        displayName = "Black Hot",
        shortLabel = "BLK HOT",
        swatchColorsArgb = listOf(0xFFF8FAFCL, 0xFFA8ADB5L, 0xFF454950L, 0xFF07080AL)
    );

    companion object {
        fun fromId(id: String?): ThermalPalette =
            entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: RAINBOW_THERMAL

        fun fromIndex(index: Int): ThermalPalette =
            entries.firstOrNull { it.index == index } ?: RAINBOW_THERMAL
    }
}

/**
 * Real-time user and sensor configuration for simulated thermal imaging (`thermal_vision`).
 */
data class ThermalVisionConfig(
    val palette: ThermalPalette = ThermalPalette.RAINBOW_THERMAL,
    val sensitivity: Float = 1.0f, // 0.50f .. 2.00f simulated thermal gain
    val contrast: Float = 1.15f,    // 0.60f .. 1.80f thermal span contrast
    val inverted: Boolean = false,
    val sensorSoftness: Float = 0.30f,       // 0.0f .. 1.0f microbolometer blur blend
    val noiseIntensity: Float = 0.045f,      // 0.0f .. 0.12f fine electronic bolometer noise
    val adaptiveContrastEnabled: Boolean = true,
    val hudEnabled: Boolean = true,
    val monochromeHud: Boolean = false,
    val crosshairEnabled: Boolean = true,
    val segmentationEnabled: Boolean = true
)

/**
 * UI & live telemetry state for Thermal Vision mode. Never exposes fabricated degree readings.
 */
data class ThermalUiState(
    val config: ThermalVisionConfig = ThermalVisionConfig(),
    val isHumanLockActive: Boolean = false,
    val detectedSubjectCount: Int = 0,
    val isMaskActive: Boolean = false,
    val adaptiveMinLuma: Float = 0.05f,
    val adaptiveMaxLuma: Float = 0.92f
)
