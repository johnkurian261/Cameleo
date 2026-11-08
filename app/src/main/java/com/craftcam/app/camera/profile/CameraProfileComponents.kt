package com.craftcam.app.camera.profile

import com.craftcam.app.camera.AspectRatioMode
import com.craftcam.app.color.ColorProfile
import com.craftcam.app.model.GrainBlendMode

/**
 * Color space mode for input feeding into the 3D LUT.
 * Inspired by VSCO's 1D inverse contrast curve and Lightroom's scene-linear color pipeline.
 */
enum class LutInputSpace {
    SRGB,                    // Standard sRGB gamma input
    LINEAR,                  // Linearized scene irradiance
    CUSTOM_PRETRANSFORM      // Pre-LUT contrast flattening / linearization
}

/**
 * Film Stock / Color Chemistry Profile.
 * Encapsulates the film look independently from camera bodies and optical lenses.
 */
data class FilmProfile(
    val id: String,
    val name: String,
    val lutAssetPath: String? = null,
    val secondaryLutAssetPath: String? = null,
    val dualLutBlendFactor: Float = 0.0f,
    val lutInputSpace: LutInputSpace = LutInputSpace.CUSTOM_PRETRANSFORM,
    val lutStrength: Float = 1.0f,
    val contrast: Float = 1.0f,
    val saturation: Float = 1.0f,
    val temperature: Float = 0.0f,
    val tint: Float = 0.0f,
    val gamma: Float = 1.0f,
    val fade: Float = 0.0f,
    val toeLift: Float = 0.0f,
    val shoulderClip: Float = 1.0f,
    val highlightRollOff: Float = 0.82f,
    val shadowTintR: Float = 0.0f,
    val shadowTintG: Float = 0.0f,
    val shadowTintB: Float = 0.0f,
    val clarityStrength: Float = 0.0f,
    val skinProtection: Float = 0.85f,
    val halationThreshold: Float = 0.80f,
    val halationIntensity: Float = 0.25f,
    val halationColorTintR: Float = 1.0f,
    val halationColorTintG: Float = 0.32f,
    val halationColorTintB: Float = 0.10f,
    val toneCurve: com.craftcam.app.color.ToneCurve? = null
) {
    companion object {
        val Default = FilmProfile(id = "film_default", name = "Standard Color")
        val Classic35 = FilmProfile(
            id = "film_35mm_classic",
            name = "Classic 35mm",
            lutAssetPath = "lut_analog_35mm_classic.png",
            contrast = 1.10f,
            saturation = 1.04f,
            temperature = 0.08f,
            toeLift = 0.035f,
            shoulderClip = 0.972f,
            highlightRollOff = 0.84f,
            halationThreshold = 0.78f,
            halationIntensity = 0.22f,
            halationColorTintR = 1.0f,
            halationColorTintG = 0.28f,
            halationColorTintB = 0.06f
        )
        val WarmNegative = FilmProfile(
            id = "film_warm_negative",
            name = "Warm Negative",
            lutAssetPath = "lut_fxn_r.png",
            contrast = 1.06f,
            saturation = 1.04f,
            temperature = 0.18f,
            tint = 0.03f,
            toeLift = 0.040f,
            shoulderClip = 0.965f,
            highlightRollOff = 0.78f,
            shadowTintR = 0.054f,
            shadowTintG = 0.024f,
            shadowTintB = 0.008f,
            halationThreshold = 0.75f,
            halationIntensity = 0.28f,
            halationColorTintR = 1.0f,
            halationColorTintG = 0.35f,
            halationColorTintB = 0.08f
        )
    }
}

/**
 * Optical Aberration and Lens Artifact Profile.
 * Governs geometric distortion, dispersion, flares, and prism refraction.
 */
data class OpticalProfile(
    val id: String = "optical_standard",
    val distortionStrength: Float = 0.0f,       // Fisheye / barrel curvature [-1.0..1.0]
    val edgeCurvature: Float = 0.0f,            // 4th order radial distortion
    val chromaticAberration: Float = 0.0f,      // Center-distance dependent color fringe
    val vignetteStrength: Float = 0.0f,         // Peripheral light falloff
    val vignetteRadius: Float = 0.88f,          // Falloff starting knee
    val edgeSoftness: Float = 0.0f,             // Perimeter spherical softness
    val prismDispersion: Float = 0.0f,          // Refraction displacement
    val starburstStrength: Float = 0.0f,        // Specular diffraction rays
    val starburstThreshold: Float = 0.85f,       // Luminance threshold for star rays
    val flareStrength: Float = 0.0f             // Optical veiling flare
) {
    companion object {
        val Standard = OpticalProfile()
        val FisheyeFull = OpticalProfile(
            id = "optical_fisheye_f",
            distortionStrength = 0.92f,
            edgeCurvature = 0.45f,
            vignetteStrength = 0.65f,
            chromaticAberration = 0.022f,
            edgeSoftness = 0.20f
        )
        val FisheyeWide = OpticalProfile(
            id = "optical_fisheye_w",
            distortionStrength = 0.72f,
            edgeCurvature = 0.25f,
            vignetteStrength = 0.30f,
            chromaticAberration = 0.012f,
            edgeSoftness = 0.10f
        )
        val PrismRefraction = OpticalProfile(
            id = "optical_prism",
            prismDispersion = 0.85f,
            chromaticAberration = 0.012f,
            flareStrength = 0.25f,
            edgeSoftness = 0.14f
        )
    }
}

/**
 * Authoritative mode for film grain delivery.
 */
enum class GrainMode {
    SCANNED,            // Use authenticated high-resolution scanned halide plate
    PROCEDURAL,         // Use golden-ratio GPU procedural noise
    HYBRID              // Scanned structural base + high-frequency procedural jitter
}

/**
 * Grain Profile defining tactile analog film texture.
 */
data class GrainProfile(
    val id: String = "grain_standard",
    val mode: GrainMode = GrainMode.SCANNED,
    val assetPath: String? = "grains/grain_dclassic.jpg",
    val intensity: Float = 0.22f,
    val scale: Float = 1.0f,
    val blendMode: GrainBlendMode = GrainBlendMode.OVERLAY,
    val shadowStrength: Float = 0.28f,
    val highlightStrength: Float = 0.14f,
    val isColorNoise: Boolean = false,
    val luminanceWeightingEnabled: Boolean = true,
    val luminanceWeightingPower: Float = 1.0f // 1.0 = standard parabolic 4L(1-L) modulation, 0.0 = uniform
) {
    companion object {
        val None = GrainProfile(id = "grain_none", intensity = 0.0f)
        val Fine35mm = GrainProfile(
            id = "grain_35mm_fine",
            mode = GrainMode.SCANNED,
            assetPath = "grains/grain_dclassic.jpg",
            intensity = 0.22f,
            scale = 1.0f
        )
        val Gritty16mm = GrainProfile(
            id = "grain_16mm_gritty",
            mode = GrainMode.SCANNED,
            assetPath = "grains/grain_16mm.jpg",
            intensity = 0.38f,
            scale = 1.45f
        )
    }
}

/**
 * Camera Exposure and Physical Attenuation Profile.
 */
data class ExposureProfile(
    val evCompensation: Float = 0.0f,
    val ndExposureReductionStops: Float = 0.0f,
    val ndTransmissionFactor: Float = 1.0f,
    val isoCap: Int? = null,
    val shutterSpeedMinNanos: Long? = null
) {
    companion object {
        val Standard = ExposureProfile()
        val ND4 = ExposureProfile(
            ndExposureReductionStops = 2.0f,
            ndTransmissionFactor = 0.25f
        )
    }
}

/**
 * Multi-frame Computational Photography Profile.
 */
data class ComputationalProfile(
    val enabled: Boolean = false,
    val burstFrameCount: Int = 3,
    val bracketEvOffsets: List<Float> = listOf(-1.0f, 0.0f, 1.0f),
    val noiseReductionStrength: Float = 0.35f,
    val deghostingConfidenceThreshold: Float = 0.65f,
    val toneMappingShoulder: Float = 0.75f
) {
    companion object {
        val SingleShot = ComputationalProfile(enabled = false)
        val BracketedHDR = ComputationalProfile(
            enabled = true,
            burstFrameCount = 3,
            bracketEvOffsets = listOf(-1.0f, 0.0f, 1.0f)
        )
    }
}

/**
 * The Master Unified Camera Profile.
 * Completely distinguishes:
 * CAMERA != LENS != OPTICAL EFFECT != COLOR PROFILE != FILM STOCK != CAPTURE FRAME != COMPUTATIONAL PROCESSING
 */
data class CameraProfile(
    val id: String,
    val name: String,
    val subtitle: String,
    val presetId: String,
    val focalLengths: List<FocalLengthOption>,
    val defaultFocalLengthMm: Float,
    val supportedLenses: List<LensType>,
    val defaultLens: LensType = LensType.NORMAL,
    val lensProfile: LensProfile = LensProfile.NORMAL,
    val colorProfile: ColorProfile = ColorProfile.Natural,
    val filmProfile: FilmProfile = FilmProfile.Default,
    val opticalProfile: OpticalProfile = OpticalProfile.Standard,
    val grainProfile: GrainProfile = GrainProfile.Fine35mm,
    val exposureProfile: ExposureProfile = ExposureProfile.Standard,
    val computationalProfile: ComputationalProfile = ComputationalProfile.SingleShot,
    val d3dProfile: D3DProfile? = null,
    val supportedRatios: List<AspectRatioMode> = listOf(
        AspectRatioMode.RATIO_4_3,
        AspectRatioMode.RATIO_3_2,
        AspectRatioMode.RATIO_1_1,
        AspectRatioMode.RATIO_16_9
    ),
    val defaultRatio: AspectRatioMode = AspectRatioMode.RATIO_4_3,
    val baseOpticalMm: Float = 26.0f
)

