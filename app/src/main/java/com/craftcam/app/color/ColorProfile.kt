package com.craftcam.app.color

/**
 * Color Profile definition for CAMELEO Advanced Color Science.
 *
 * Encapsulates:
 * 1. 3x3 Sensor Calibration Matrix.
 * 2. Reference Target White Point.
 * 3. Parametric Tone Curve.
 * 4. Saturation, Vibrance, and Contrast gains.
 * 5. Highlight Roll-off parameters.
 * 6. Skin Tone Protection weighting.
 */
data class ColorProfile(
    val name: String,
    val version: String = "1.0.0",
    val colorMatrix: ColorMatrix3x3 = ColorMatrix3x3.IDENTITY,
    val whitePoint: WhitePoint = WhitePoint.D65,
    val toneCurve: ToneCurve = ToneCurve.Natural,
    val saturation: Float = 1.0f,
    val vibrance: Float = 0.20f,
    val contrast: Float = 1.0f,
    val highlightRollOffKnee: Float = 0.65f,
    val highlightRollOffFactor: Float = 0.35f,
    val skinProtectionStrength: Float = 0.85f
) {
    companion object {
        /**
         * CAMELEO Natural: Calibrated flagship color science.
         * Neutralizes Samsung S21 green-yellow sensor bias, lifts shadows with preserved true black,
         * softens specular highlights via asymptotic shoulder, and protects diverse skin tones.
         */
        val Natural = ColorProfile(
            name = "CAMELEO Natural",
            version = "1.0.0",
            colorMatrix = ColorMatrix3x3(
                1.025f, -0.015f, -0.010f,
               -0.012f,  1.018f, -0.006f,
               -0.008f,  0.015f,  0.993f
            ),
            whitePoint = WhitePoint.D65,
            toneCurve = ToneCurve.Natural,
            saturation = 1.02f,
            vibrance = 0.18f,
            contrast = 1.02f,
            highlightRollOffKnee = 0.65f,
            highlightRollOffFactor = 0.35f,
            skinProtectionStrength = 0.85f
        )

        /**
         * CAMELEO Neutral: Unshaped laboratory baseline.
         * Identity matrix, linear tone curve, zero saturation boost. Used for Delta E benchmarks.
         */
        val Neutral = ColorProfile(
            name = "CAMELEO Neutral",
            version = "1.0.0",
            colorMatrix = ColorMatrix3x3.IDENTITY,
            whitePoint = WhitePoint.D65,
            toneCurve = ToneCurve.Neutral,
            saturation = 1.0f,
            vibrance = 0.0f,
            contrast = 1.0f,
            highlightRollOffKnee = 0.95f,
            highlightRollOffFactor = 0.05f,
            skinProtectionStrength = 0.0f
        )

        /**
         * CAMELEO Portrait: Optimized for human skin clarity, eye definition, and gentle midtone contrast.
         */
        val Portrait = ColorProfile(
            name = "CAMELEO Portrait",
            version = "1.0.0",
            colorMatrix = ColorMatrix3x3(
                1.020f, -0.012f, -0.008f,
               -0.010f,  1.015f, -0.005f,
               -0.006f,  0.010f,  0.996f
            ),
            whitePoint = WhitePoint.D65,
            toneCurve = ToneCurve.Portrait,
            saturation = 0.98f,
            vibrance = 0.15f,
            contrast = 0.98f,
            highlightRollOffKnee = 0.62f,
            highlightRollOffFactor = 0.40f,
            skinProtectionStrength = 1.0f
        )

        /**
         * CAMELEO Warm: Subtle golden-hour atmospheric rendering.
         */
        val Warm = ColorProfile(
            name = "CAMELEO Warm",
            version = "1.0.0",
            colorMatrix = ColorMatrix3x3(
                1.045f, -0.010f, -0.005f,
               -0.005f,  1.012f, -0.007f,
               -0.015f,  0.008f,  0.965f
            ),
            whitePoint = WhitePoint.D50,
            toneCurve = ToneCurve.Natural,
            saturation = 1.05f,
            vibrance = 0.22f,
            contrast = 1.03f,
            highlightRollOffKnee = 0.65f,
            highlightRollOffFactor = 0.35f,
            skinProtectionStrength = 0.85f
        )

        /**
         * CAMELEO Cool: Subtle blue-hour/editorial atmospheric rendering.
         */
        val Cool = ColorProfile(
            name = "CAMELEO Cool",
            version = "1.0.0",
            colorMatrix = ColorMatrix3x3(
                0.975f, -0.005f,  0.010f,
               -0.008f,  0.995f,  0.015f,
                0.005f, -0.010f,  1.040f
            ),
            whitePoint = WhitePoint.D65,
            toneCurve = ToneCurve.Natural,
            saturation = 1.02f,
            vibrance = 0.20f,
            contrast = 1.02f,
            highlightRollOffKnee = 0.65f,
            highlightRollOffFactor = 0.35f,
            skinProtectionStrength = 0.85f
        )
    }
}

