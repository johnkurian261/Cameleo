package com.craftcam.app.model

import android.hardware.camera2.params.RggbChannelVector
import kotlin.math.*

enum class ExposureMode {
    AUTO,
    MANUAL
}

enum class IsoMode {
    AUTO,
    MANUAL
}

enum class ShutterMode {
    AUTO,
    MANUAL
}

enum class FocusMode {
    AUTO,
    MANUAL
}

enum class WhiteBalanceMode {
    AUTO,
    MANUAL
}

enum class PeakingColor(val displayName: String, val r: Float, val g: Float, val b: Float) {
    GREEN("Green", 0.0f, 1.0f, 0.2f),
    RED("Red", 1.0f, 0.2f, 0.2f),
    BLUE("Blue", 0.2f, 0.6f, 1.0f),
    YELLOW("Yellow", 1.0f, 0.9f, 0.1f)
}

enum class ProControlTab {
    ISO,
    SHUTTER,
    EV,
    WB,
    FOCUS,
    TOOLS
}

/**
 * Pro Camera manual settings and exposure assistant tools configuration.
 */
data class ProCameraConfig(
    val exposureMode: ExposureMode = ExposureMode.AUTO,
    val isoMode: IsoMode = IsoMode.AUTO,
    val shutterMode: ShutterMode = ShutterMode.AUTO,
    val focusMode: FocusMode = FocusMode.AUTO,
    val wbMode: WhiteBalanceMode = WhiteBalanceMode.AUTO,

    // Manual sensor parameters
    val iso: Int = 100,
    val exposureTimeNs: Long = 10_000_000L, // 1/100s default
    val focusDistance: Float = 0.0f,        // 0.0f = infinity diopters, max = minimum focus distance
    val kelvin: Int = 5500,                 // 2500K to 9000K
    val tint: Float = 0.0f,                 // -0.5f (green) to +0.5f (magenta)
    val evCompensation: Float = 0.0f,       // in EV

    // Exposure & Focus Assistants
    val focusPeakingEnabled: Boolean = false,
    val peakingColor: PeakingColor = PeakingColor.GREEN,
    val peakingThreshold: Float = 0.20f,

    val zebraEnabled: Boolean = false,
    val zebraThreshold: Float = 0.80f,      // 0.70f (70%) to 1.00f (100%)

    val histogramEnabled: Boolean = false,

    // Currently expanded interactive control panel
    val activeControl: ProControlTab? = null
)

/**
 * Hardware sensor capabilities queried dynamically from CameraCharacteristics.
 */
data class CameraHardwareCapabilities(
    val cameraId: String = "0",
    val hardwareLevel: Int = 0,
    val minIso: Int = 50,
    val maxIso: Int = 3200,
    val minShutterNs: Long = 100_000L,          // e.g. 1/10000s
    val maxShutterNs: Long = 15_000_000_000L,   // e.g. 15s
    val supportsManualExposure: Boolean = true,
    val minFocusDistance: Float = 10.0f,        // in diopters (> 0 means AF/MF lens present)
    val supportsManualFocus: Boolean = true,
    val minEv: Int = -4,
    val maxEv: Int = 4,
    val evStep: Float = 0.5f,
    val supportsManualWb: Boolean = true,
    val supportsColorGains: Boolean = true
)

object ProCameraFormatters {

    val STANDARD_ISO_STOPS = listOf(
        50, 100, 125, 160, 200, 250, 320, 400, 500, 640, 800, 1000, 1250, 1600, 2000, 2500, 3200, 6400
    )

    // Photographic fractions: (numerator to denominator in seconds)
    val STANDARD_SHUTTER_FRACTIONS = listOf(
        1L to 12000L,
        1L to 8000L,
        1L to 4000L,
        1L to 2000L,
        1L to 1000L,
        1L to 500L,
        1L to 250L,
        1L to 125L,
        1L to 60L,
        1L to 30L,
        1L to 15L,
        1L to 8L,
        1L to 4L,
        1L to 2L,
        1L to 1L,
        2L to 1L,
        4L to 1L,
        8L to 1L,
        15L to 1L
    )

    fun shutterFractionToNanos(numerator: Long, denominator: Long): Long {
        return (numerator * 1_000_000_000.0 / denominator).roundToLong()
    }

    fun formatShutterSpeed(nanos: Long): String {
        if (nanos <= 0L) return "AUTO"
        val seconds = nanos / 1_000_000_000.0
        return if (seconds >= 0.95) {
            val s = seconds.roundToInt()
            "${s}s"
        } else {
            val denom = (1.0 / seconds).roundToInt()
            "1/$denom"
        }
    }

    fun formatFocusDistance(diopters: Float, maxDiopters: Float): String {
        if (diopters < 0.05f) return "∞ (Far)"
        if (diopters >= maxDiopters - 0.1f) return "Near (Macro)"
        val distMeters = 1.0f / diopters
        return if (distMeters < 1.0f) {
            val cm = (distMeters * 100f).roundToInt()
            "${cm}cm"
        } else {
            String.format(java.util.Locale.US, "%.1fm", distMeters)
        }
    }

    /**
     * Converts color temperature (Kelvin) and tint into Camera2 RGGB channel gains.
     * Uses Planckian blackbody radiator model inverted to determine channel multipliers.
     */
    fun kelvinToRggbGains(kelvin: Int, tint: Float): RggbChannelVector {
        val temp = kelvin.toFloat() / 100.0f

        val red = if (temp <= 66f) {
            255f
        } else {
            (329.698727446f * (temp - 60f).pow(-0.1332047592f)).coerceIn(0f, 255f)
        }

        val green = if (temp <= 66f) {
            (99.4708025861f * ln(temp.coerceAtLeast(1f)) - 161.1195681661f).coerceIn(0f, 255f)
        } else {
            (288.1221695283f * (temp - 60f).pow(-0.0755148492f)).coerceIn(0f, 255f)
        }

        val blue = if (temp >= 66f) {
            255f
        } else if (temp <= 19f) {
            0f
        } else {
            (138.5177312231f * ln((temp - 10f).coerceAtLeast(1f)) - 305.0447927307f).coerceIn(0f, 255f)
        }

        val rFactor = 255f / red.coerceAtLeast(1f)
        val gFactor = 255f / green.coerceAtLeast(1f)
        val bFactor = 255f / blue.coerceAtLeast(1f)

        val baseGreen = 1.0f
        val baseRed = 2.0f
        val baseBlue = 2.0f

        val tintGreenMult = 1.0f - (tint * 0.35f)
        val tintMagentaMult = 1.0f + (tint * 0.35f)

        val rGain = (baseRed * (rFactor / gFactor) * tintMagentaMult).coerceIn(1.0f, 4.0f)
        val gGain = (baseGreen * tintGreenMult).coerceIn(0.8f, 2.5f)
        val bGain = (baseBlue * (bFactor / gFactor) * tintMagentaMult).coerceIn(1.0f, 4.0f)

        return RggbChannelVector(rGain, gGain, gGain, bGain)
    }
}

