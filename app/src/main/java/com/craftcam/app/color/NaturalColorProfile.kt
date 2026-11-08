package com.craftcam.app.color

/**
 * Dedicated Natural Color Profile for CAMELEO Zero / Natural Camera Mode.
 *
 * Designed to outperform standard smartphone processing:
 * - Neutralizes sensor color bias (especially Samsung ISOCELL green-yellow cast).
 * - Restrained natural saturation (avoids neon foliage, orange skin, and crushed blues).
 * - Smooth continuous asymptotic highlight shoulder (prevents hard white clipping in skies/windows).
 * - Adaptive shadow toe lifting (lifts shadow details while anchoring true black at 0.0).
 * - Edge-aware chroma noise reduction (cleans color blotches in shadows without edge bleeding).
 * - Multi-scale micro/macro local contrast (subtle depth without HDR halo artifacts).
 * - Soft multi-complexion skin protection (Fitzpatrick Types I to VI, dampens sharpening on faces).
 * - Sub-LSB dithering (breaks 8-bit sky banding without artificial film grain).
 *
 * 100% independent CAMELEO clean-room implementation. Zero Film LUT or film emulation reuse.
 */
data class NaturalColorProfile(
    val name: String = "CAMELEO Zero Natural Reference",
    val version: String = "2.0.0",

    // 1. Sensor Color Calibration (3x3 linear radiometric matrix)
    val colorMatrix: ColorMatrix3x3 = S21_SENSOR_MATRIX,

    // 2. Global Tonal & Color Adjustments
    val saturation: Float = 1.00f,              // Conservative neutral saturation (0.8 .. 1.2)
    val vibrance: Float = 0.12f,                // Gentle low-saturation boost (0.0 .. 0.3)
    val contrast: Float = 1.02f,                // Subtle midtone contrast (0.9 .. 1.1)

    // 3. Highlight Shoulder Roll-off & Shadow Recovery
    val highlightRollOffKnee: Float = 0.68f,    // Linear irradiance threshold where roll-off begins
    val highlightRollOffFactor: Float = 0.32f,  // Asymptotic curvature factor (lower = softer)
    val shadowLift: Float = 0.14f,              // Toe lifting factor (peaks at Y=0.15, zero at true black)

    // 4. Selective Color Tuning
    val greenTamingFactor: Float = 0.90f,       // Tames neon foliage/grass saturation (85..155 deg)
    val skyCobaltFactor: Float = 1.02f,         // Preserves deep cobalt sky without magenta tilt (195..235 deg)
    val highlightDesaturationThreshold: Float = 0.85f, // Desaturates hot specular peaks to clean white

    // 5. Skin Tone Protection & Red Clipping Guard
    val skinProtectionStrength: Float = 0.88f,  // Soft multi-complexion protection weight (0.0 .. 1.0)
    val skinHueCenter: Float = 26.0f,           // Natural peach/skin hue center in degrees
    val skinHueTolerance: Float = 24.0f,        // Hue tolerance in degrees
    val redClipGuardThreshold: Float = 0.86f,   // Prevents hot red channel blowout on skin/sunsets

    // 6. Noise Reduction & Detail Preservation
    val noiseReductionChroma: Float = 0.32f,    // Edge-gated Cb/Cr bilateral denoising strength
    val noiseReductionLumaShadow: Float = 0.18f,// Deep shadow luminance smoothing strength
    val sharpeningStrength: Float = 0.18f,      // Restrained edge-aware sharpening (0.0 .. 0.5)
    val sharpeningSkinDampener: Float = 0.35f,  // Multiplier for sharpening in skin zones (prevents crunchy pores)

    // 7. Local Contrast
    val localContrastMicro: Float = 0.10f,      // High-frequency texture definition
    val localContrastMacro: Float = 0.08f,      // Mid-frequency subject separation (no halos)

    // 8. Output Quality
    val enableDithering: Boolean = true         // High-frequency sub-LSB dither to prevent gradient banding
) {
    companion object {
        /**
         * Calibrated 3x3 linear radiometric matrix for Samsung Galaxy S21 / ISOCELL 12.2MP sensor.
         * Neutralizes native sensor yellow-green cast and aligns red/blue response to Rec.709 / sRGB.
         */
        val S21_SENSOR_MATRIX = ColorMatrix3x3(
            1.024f, -0.016f, -0.008f,
           -0.012f,  1.016f, -0.004f,
           -0.006f,  0.012f,  0.994f
        )

        /**
         * Universal calibrated baseline for modern smartphone camera sensors.
         */
        val S21_CALIBRATED = NaturalColorProfile()

        /**
         * Neutral identity baseline (used for objective benchmark verification).
         */
        val NEUTRAL_IDENTITY = NaturalColorProfile(
            name = "CAMELEO Zero Neutral Identity",
            colorMatrix = ColorMatrix3x3.IDENTITY,
            saturation = 1.00f,
            vibrance = 0.0f,
            contrast = 1.00f,
            shadowLift = 0.0f,
            highlightRollOffKnee = 0.95f,
            highlightRollOffFactor = 0.05f,
            greenTamingFactor = 1.0f,
            skyCobaltFactor = 1.0f,
            skinProtectionStrength = 0.0f,
            noiseReductionChroma = 0.0f,
            noiseReductionLumaShadow = 0.0f,
            sharpeningStrength = 0.0f,
            localContrastMicro = 0.0f,
            localContrastMacro = 0.0f,
            enableDithering = false
        )
    }
}

