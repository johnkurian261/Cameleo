package com.craftcam.app.camera.profile

import com.craftcam.app.camera.AspectRatioMode

/**
 * Camera focal length option (e.g. "35mm", 35.0f).
 */
data class FocalLengthOption(
    val label: String,
    val focalLengthMm: Float,
    val isPhysicalLens: Boolean = false // True if matches physical hardware module on S21 (13mm, 26mm, 70mm)
)

/**
 * Data-driven Camera Body Profile.
 * Encapsulates the complete simulated photographic system:
 * - Supported focal lengths
 * - Supported lens accessories (hides unsupported accessories from the UI)
 * - Default lens and default focal length
 * - Native camera aspect ratio (e.g. 3:2 for 35mm rangefinders, 1:1 for instant, 4:3 for compacts)
 * - Associated preset ID in PresetCatalog for color/film science
 */
data class CameleoCameraProfile(
    val id: String,
    val name: String,
    val subtitle: String,
    val presetId: String,
    val focalLengths: List<FocalLengthOption>,
    val defaultFocalLengthMm: Float,
    val supportedLenses: List<LensType>,
    val defaultLens: LensType = LensType.NORMAL,
    val supportedRatios: List<AspectRatioMode> = listOf(
        AspectRatioMode.RATIO_4_3,
        AspectRatioMode.RATIO_3_2,
        AspectRatioMode.RATIO_1_1,
        AspectRatioMode.RATIO_16_9
    ),
    val defaultRatio: AspectRatioMode = AspectRatioMode.RATIO_4_3,
    val baseOpticalMm: Float = 26.0f,
    val hasOpticalViewfinderZoom: Boolean = true,
    val filmProfile: FilmProfile = FilmProfile.Default,
    val grainProfile: GrainProfile = GrainProfile.Fine35mm,
    val opticalProfile: OpticalProfile = OpticalProfile.Standard,
    val exposureProfile: ExposureProfile = ExposureProfile.Standard,
    val computationalProfile: ComputationalProfile = ComputationalProfile.SingleShot,
    val d3dProfile: D3DProfile? = null
) {
    fun getDefaultFocalOption(): FocalLengthOption =
        focalLengths.firstOrNull { it.focalLengthMm == defaultFocalLengthMm }
            ?: focalLengths.firstOrNull()
            ?: FocalLengthOption("35mm", 35f)

    fun toUnifiedCameraProfile(): CameraProfile =
        CameraProfile(
            id = id,
            name = name,
            subtitle = subtitle,
            presetId = presetId,
            focalLengths = focalLengths,
            defaultFocalLengthMm = defaultFocalLengthMm,
            supportedLenses = supportedLenses,
            defaultLens = defaultLens,
            lensProfile = LensProfile.forType(defaultLens),
            filmProfile = filmProfile,
            grainProfile = grainProfile,
            opticalProfile = opticalProfile,
            exposureProfile = exposureProfile,
            computationalProfile = computationalProfile,
            d3dProfile = d3dProfile,
            supportedRatios = supportedRatios,
            defaultRatio = defaultRatio,
            baseOpticalMm = baseOpticalMm
        )
}

