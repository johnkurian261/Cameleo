package com.craftcam.app.camera.profile

import com.craftcam.app.camera.AspectRatioMode
import com.craftcam.app.gl.shaders.ShaderConstants
import com.craftcam.app.model.CameraPreset

object CameraProfileCatalog {

    val FXN_R = CameleoCameraProfile(
        id = "fxn_r",
        name = "FXNR",
        subtitle = "Warm Negative",
        presetId = "analog_fxn_r",
        focalLengths = listOf(
            FocalLengthOption("35mm", 35f),
            FocalLengthOption("50mm", 50f)
        ),
        defaultFocalLengthMm = 35f,
        supportedLenses = listOf(
            LensType.NORMAL,
            LensType.FISHEYE_F,
            LensType.FISHEYE_W,
            LensType.PRISM,
            LensType.STARBURST,
            LensType.COLOR_FLASH
        ),
        defaultLens = LensType.NORMAL,
        defaultRatio = AspectRatioMode.RATIO_4_3,
        filmProfile = FilmProfile(
            id = "film_fxn_r",
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
        ),
        grainProfile = GrainProfile(
            id = "grain_fxnr",
            mode = GrainMode.SCANNED,
            assetPath = "grains/grain_ou.jpg",
            intensity = 0.22f,
            scale = 1.0f,
            luminanceWeightingEnabled = true,
            luminanceWeightingPower = 1.0f
        )
    )

    val D_CLASSIC = CameleoCameraProfile(
        id = "d_classic",
        name = "D Classic",
        subtitle = "Universal Negative",
        presetId = "analog_35mm_classic",
        focalLengths = listOf(
            FocalLengthOption("37mm", 37f),
            FocalLengthOption("50mm", 50f)
        ),
        defaultFocalLengthMm = 37f,
        supportedLenses = listOf(
            LensType.NORMAL,
            LensType.FISHEYE_F,
            LensType.FISHEYE_W,
            LensType.PRISM,
            LensType.STARBURST
        ),
        defaultLens = LensType.NORMAL,
        defaultRatio = AspectRatioMode.RATIO_3_2,
        filmProfile = FilmProfile.Classic35,
        grainProfile = GrainProfile.Fine35mm
    )

    val D_EXP = CameleoCameraProfile(
        id = "d_exp",
        name = "D Exp",
        subtitle = "Double Exposure",
        presetId = "analog_vintage_color",
        focalLengths = listOf(
            FocalLengthOption("28mm", 28f),
            FocalLengthOption("35mm", 35f)
        ),
        defaultFocalLengthMm = 35f,
        supportedLenses = listOf(
            LensType.NORMAL,
            LensType.PRISM,
            LensType.ND
        ),
        defaultLens = LensType.NORMAL,
        defaultRatio = AspectRatioMode.RATIO_3_2,
        filmProfile = FilmProfile(
            id = "film_d_exp",
            name = "Double Exposure",
            lutAssetPath = "lut_analog_vintage_color.png",
            contrast = 1.15f,
            saturation = 1.08f,
            temperature = 0.14f,
            tint = -0.06f,
            toeLift = 0.045f,
            shoulderClip = 0.950f,
            highlightRollOff = 0.76f,
            shadowTintR = 0.03f,
            shadowTintG = 0.04f,
            shadowTintB = 0.01f,
            halationThreshold = 0.76f,
            halationIntensity = 0.26f,
            halationColorTintR = 1.0f,
            halationColorTintG = 0.30f,
            halationColorTintB = 0.08f
        ),
        grainProfile = GrainProfile(
            id = "grain_d_exp",
            mode = GrainMode.SCANNED,
            assetPath = "grains/grain_dexp.jpg",
            intensity = 0.26f,
            scale = 1.15f,
            luminanceWeightingEnabled = true,
            luminanceWeightingPower = 0.85f
        )
    )

    val CCD_DIGITAL = CameleoCameraProfile(
        id = "ccd_digital",
        name = "CCD R",
        subtitle = "2000s Digicam",
        presetId = "ccd_r",
        focalLengths = listOf(
            FocalLengthOption("35mm", 35f),
            FocalLengthOption("50mm", 50f)
        ),
        defaultFocalLengthMm = 35f,
        supportedLenses = listOf(
            LensType.NORMAL,
            LensType.STARBURST,
            LensType.ND
        ),
        defaultLens = LensType.NORMAL,
        defaultRatio = AspectRatioMode.RATIO_4_3,
        filmProfile = FilmProfile(
            id = "film_ccd_digital",
            name = "CCD Digicam Sensor",
            lutAssetPath = "lut_ccd_r.png",
            contrast = 1.20f,
            saturation = 1.18f,
            toeLift = 0.015f,
            shoulderClip = 0.992f,
            highlightRollOff = 0.94f,
            halationThreshold = 0.92f,
            halationIntensity = 0.08f,
            halationColorTintR = 0.85f,
            halationColorTintG = 0.85f,
            halationColorTintB = 1.0f
        ),
        grainProfile = GrainProfile(
            id = "grain_ccd_digital",
            mode = GrainMode.PROCEDURAL,
            intensity = 0.12f,
            scale = 0.85f,
            isColorNoise = false,
            luminanceWeightingEnabled = false,
            luminanceWeightingPower = 0.0f
        )
    )

    val INSTANT_CAMERA = CameleoCameraProfile(
        id = "instant_polaroid",
        name = "Instant V",
        subtitle = "Peel-Apart Film",
        presetId = "inst_vintage",
        focalLengths = listOf(
            FocalLengthOption("35mm", 35f)
        ),
        defaultFocalLengthMm = 35f,
        supportedLenses = listOf(
            LensType.NORMAL,
            LensType.PRISM,
            LensType.COLOR_FLASH
        ),
        defaultLens = LensType.NORMAL,
        defaultRatio = AspectRatioMode.RATIO_1_1,
        filmProfile = FilmProfile(
            id = "film_instant_v",
            name = "Instant Peel-Apart",
            lutAssetPath = "lut_inst_vintage.png",
            contrast = 1.08f,
            saturation = 0.98f,
            temperature = 0.12f,
            tint = -0.12f,
            toeLift = 0.085f,
            shoulderClip = 0.920f,
            highlightRollOff = 0.72f,
            shadowTintR = -0.02f,
            shadowTintG = 0.05f,
            shadowTintB = 0.04f,
            halationThreshold = 0.70f,
            halationIntensity = 0.30f,
            halationColorTintR = 1.0f,
            halationColorTintG = 0.40f,
            halationColorTintB = 0.15f
        ),
        grainProfile = GrainProfile(
            id = "grain_instant_v",
            mode = GrainMode.SCANNED,
            assetPath = "grains/grain_instc.jpg",
            intensity = 0.30f,
            scale = 1.25f,
            luminanceWeightingEnabled = true,
            luminanceWeightingPower = 1.0f
        )
    )

    val FISHEYE_CAMERA = CameleoCameraProfile(
        id = "fisheye_camera",
        name = "Fisheye",
        subtitle = "Curvilinear Ultra-Wide",
        presetId = "analog_fisheye_f",
        focalLengths = listOf(
            FocalLengthOption("13mm", 13f, isPhysicalLens = true),
            FocalLengthOption("26mm", 26f, isPhysicalLens = true)
        ),
        defaultFocalLengthMm = 13f,
        supportedLenses = listOf(
            LensType.FISHEYE_F,
            LensType.FISHEYE_W
        ),
        defaultLens = LensType.FISHEYE_F,
        defaultRatio = AspectRatioMode.RATIO_4_3,
        opticalProfile = OpticalProfile.FisheyeFull,
        filmProfile = FilmProfile(
            id = "film_fisheye_f",
            name = "Fisheye Emulsion",
            lutAssetPath = "lut_analog_35mm_classic.png",
            contrast = 1.15f,
            saturation = 1.10f,
            toeLift = 0.025f,
            shoulderClip = 0.980f,
            highlightRollOff = 0.82f,
            halationThreshold = 0.82f,
            halationIntensity = 0.20f,
            halationColorTintR = 1.0f,
            halationColorTintG = 0.25f,
            halationColorTintB = 0.05f
        ),
        grainProfile = GrainProfile(
            id = "grain_fisheye",
            mode = GrainMode.SCANNED,
            assetPath = "grains/grain_dclassic.jpg",
            intensity = 0.20f,
            scale = 1.0f,
            luminanceWeightingEnabled = true,
            luminanceWeightingPower = 1.0f
        )
    )

    val SOFT_FILM = CameleoCameraProfile(
        id = "soft_film_ct2f",
        name = "Soft Film",
        subtitle = "Low-Contrast Pastel",
        presetId = "analog_soft_film",
        focalLengths = listOf(
            FocalLengthOption("35mm", 35f),
            FocalLengthOption("50mm", 50f)
        ),
        defaultFocalLengthMm = 35f,
        supportedLenses = listOf(
            LensType.NORMAL,
            LensType.FISHEYE_F,
            LensType.FISHEYE_W,
            LensType.ND
        ),
        defaultLens = LensType.NORMAL,
        defaultRatio = AspectRatioMode.RATIO_3_2,
        filmProfile = FilmProfile(
            id = "film_soft_film",
            name = "Pastel Low Contrast",
            lutAssetPath = "lut_analog_soft_film.png",
            contrast = 0.94f,
            saturation = 0.92f,
            toeLift = 0.055f,
            shoulderClip = 0.955f,
            highlightRollOff = 0.75f,
            clarityStrength = -0.15f,
            halationThreshold = 0.74f,
            halationIntensity = 0.22f,
            halationColorTintR = 1.0f,
            halationColorTintG = 0.42f,
            halationColorTintB = 0.20f
        ),
        grainProfile = GrainProfile(
            id = "grain_soft_film",
            mode = GrainMode.SCANNED,
            assetPath = "grains/grain_s67.jpg",
            intensity = 0.22f,
            scale = 0.95f,
            luminanceWeightingEnabled = true,
            luminanceWeightingPower = 1.0f
        )
    )

    val WARM_FILM = CameleoCameraProfile(
        id = "warm_film_gr",
        name = "Warm Film",
        subtitle = "Amber Tungsten",
        presetId = "analog_warm_film",
        focalLengths = listOf(
            FocalLengthOption("28mm", 28f),
            FocalLengthOption("50mm", 50f)
        ),
        defaultFocalLengthMm = 28f,
        supportedLenses = listOf(
            LensType.NORMAL,
            LensType.FISHEYE_F,
            LensType.FISHEYE_W,
            LensType.STARBURST
        ),
        defaultLens = LensType.NORMAL,
        defaultRatio = AspectRatioMode.RATIO_4_3,
        filmProfile = FilmProfile(
            id = "film_warm_film",
            name = "Amber Tungsten",
            lutAssetPath = "lut_analog_warm_film.png",
            contrast = 1.12f,
            saturation = 1.08f,
            temperature = 0.22f,
            tint = 0.04f,
            toeLift = 0.038f,
            shoulderClip = 0.968f,
            highlightRollOff = 0.78f,
            halationThreshold = 0.78f,
            halationIntensity = 0.26f,
            halationColorTintR = 1.0f,
            halationColorTintG = 0.35f,
            halationColorTintB = 0.08f
        ),
        grainProfile = GrainProfile(
            id = "grain_warm_film",
            mode = GrainMode.SCANNED,
            assetPath = "grains/grain_ou.jpg",
            intensity = 0.24f,
            scale = 1.05f,
            luminanceWeightingEnabled = true,
            luminanceWeightingPower = 1.0f
        )
    )

    val DISPOSABLE_FUNS = CameleoCameraProfile(
        id = "disposable_d_funs",
        name = "D FunS",
        subtitle = "Single-Use 35mm",
        presetId = "disp_classic",
        focalLengths = listOf(
            FocalLengthOption("32mm", 32f)
        ),
        defaultFocalLengthMm = 32f,
        supportedLenses = listOf(
            LensType.NORMAL,
            LensType.COLOR_FLASH
        ),
        defaultLens = LensType.NORMAL,
        defaultRatio = AspectRatioMode.RATIO_4_3,
        filmProfile = FilmProfile(
            id = "film_disposable_funs",
            name = "Single-Use 35mm",
            lutAssetPath = "lut_disp_classic.png",
            contrast = 1.20f,
            saturation = 1.14f,
            temperature = 0.14f,
            toeLift = 0.050f,
            shoulderClip = 0.955f,
            highlightRollOff = 0.74f,
            shadowTintR = -0.01f,
            shadowTintG = 0.03f,
            shadowTintB = 0.04f,
            halationThreshold = 0.72f,
            halationIntensity = 0.32f,
            halationColorTintR = 1.0f,
            halationColorTintG = 0.38f,
            halationColorTintB = 0.10f
        ),
        grainProfile = GrainProfile(
            id = "grain_disposable_funs",
            mode = GrainMode.SCANNED,
            assetPath = "grains/grain_dfuns.jpg",
            intensity = 0.32f,
            scale = 1.35f,
            luminanceWeightingEnabled = true,
            luminanceWeightingPower = 1.0f
        )
    )

    val ONE_THREE_FIVE_NE = CameleoCameraProfile(
        id = "135ne",
        name = "135 NE",
        subtitle = "Color Negative Strip",
        presetId = "analog_135ne",
        focalLengths = listOf(
            FocalLengthOption("35mm", 35f),
            FocalLengthOption("50mm", 50f)
        ),
        defaultFocalLengthMm = 35f,
        supportedLenses = listOf(
            LensType.NORMAL,
            LensType.FISHEYE_F,
            LensType.FISHEYE_W,
            LensType.PRISM
        ),
        defaultLens = LensType.NORMAL,
        defaultRatio = AspectRatioMode.RATIO_3_2,
        filmProfile = FilmProfile(
            id = "film_135ne",
            name = "135 Negative Emulsion",
            lutAssetPath = "lut_135ne.png",
            contrast = 1.14f,
            saturation = 1.08f,
            temperature = 0.14f,
            tint = 0.04f,
            toeLift = 0.038f,
            shoulderClip = 0.968f,
            highlightRollOff = 0.78f,
            shadowTintR = 0.04f,
            shadowTintG = 0.03f,
            shadowTintB = 0.01f,
            halationThreshold = 0.76f,
            halationIntensity = 0.24f,
            halationColorTintR = 1.0f,
            halationColorTintG = 0.32f,
            halationColorTintB = 0.08f
        ),
        grainProfile = GrainProfile(
            id = "grain_135ne",
            mode = GrainMode.SCANNED,
            assetPath = "grains/grain_135sr.jpg",
            intensity = 0.26f,
            scale = 1.10f,
            luminanceWeightingEnabled = true,
            luminanceWeightingPower = 1.0f
        )
    )

    val ZERO_CAMERA = CameleoCameraProfile(
        id = "profile_zero",
        name = "Zero",
        subtitle = "Natural Photographic Sensor",
        presetId = "zero",
        focalLengths = listOf(
            FocalLengthOption("24mm", 24f),
            FocalLengthOption("28mm", 28f),
            FocalLengthOption("35mm", 35f),
            FocalLengthOption("50mm", 50f)
        ),
        defaultFocalLengthMm = 28f,
        supportedLenses = listOf(
            LensType.NORMAL,
            LensType.WIDE,
            LensType.FISHEYE_F,
            LensType.FISHEYE_W,
            LensType.PRISM,
            LensType.STARBURST,
            LensType.COLOR_FLASH
        ),
        defaultLens = LensType.NORMAL,
        defaultRatio = AspectRatioMode.RATIO_4_3,
        filmProfile = FilmProfile(
            id = "sensor_zero",
            name = "Zero Photographic Reference",
            lutAssetPath = "lut_zero.png",
            lutInputSpace = LutInputSpace.CUSTOM_PRETRANSFORM,
            contrast = 1.04f,
            saturation = 1.02f,
            temperature = 0.02f,
            tint = -0.01f,
            toeLift = 0.028f,
            shoulderClip = 0.965f,
            highlightRollOff = 0.94f,
            clarityStrength = -0.06f,
            skinProtection = 0.88f,
            shadowTintR = 0.0f,
            shadowTintG = 0.0f,
            shadowTintB = 0.0f,
            halationThreshold = 1.0f,
            halationIntensity = 0.0f,
            halationColorTintR = 1.0f,
            halationColorTintG = 0.30f,
            halationColorTintB = 0.10f
        ),
        grainProfile = GrainProfile(
            id = "grain_zero",
            mode = GrainMode.SCANNED,
            assetPath = "grains/grain_dclassic.jpg",
            intensity = 0.06f,
            scale = 0.75f,
            luminanceWeightingEnabled = true,
            luminanceWeightingPower = 1.2f
        )
    )

    val allProfiles = listOf(
        FXN_R,
        D_CLASSIC,
        D_EXP,
        CCD_DIGITAL,
        INSTANT_CAMERA,
        FISHEYE_CAMERA,
        SOFT_FILM,
        WARM_FILM,
        DISPOSABLE_FUNS,
        ONE_THREE_FIVE_NE,
        ZERO_CAMERA
    )

    /**
     * Resolves a CameleoCameraProfile for any given CameraPreset.
     * Automatically maps canonical presets to explicit profiles, falling back to dynamic creation.
     */
    fun getProfileForPreset(preset: CameraPreset): CameleoCameraProfile {
        when (preset.id) {
            "zero" -> return ZERO_CAMERA
            "analog_fxn_r" -> return FXN_R
            "analog_35mm_classic" -> return D_CLASSIC
            "analog_vintage_color" -> return D_EXP
            "ccd_r", "ccd_y2k_digital", "ccd_retro_compact" -> return CCD_DIGITAL
            "inst_vintage" -> return INSTANT_CAMERA
            "disp_classic", "disp_flash", "ccd_flash", "disp_warm", "disp_night" -> return DISPOSABLE_FUNS
            "analog_135ne", "135ne" -> return ONE_THREE_FIVE_NE
            "analog_fisheye_f" -> return FISHEYE_CAMERA
            "analog_soft_film" -> return SOFT_FILM
            "analog_warm_film" -> return WARM_FILM
        }

        allProfiles.firstOrNull { it.presetId == preset.id }?.let { return it }

        // Dynamic fallback for any other preset
        return CameleoCameraProfile(
            id = "profile_${preset.id}",
            name = preset.name,
            subtitle = preset.subtitle,
            presetId = preset.id,
            focalLengths = listOf(
                FocalLengthOption("35mm", 35f),
                FocalLengthOption("50mm", 50f)
            ),
            defaultFocalLengthMm = 35f,
            supportedLenses = listOf(
                LensType.NORMAL,
                LensType.FISHEYE_F,
                LensType.FISHEYE_W,
                LensType.PRISM
            ),
            defaultLens = LensType.NORMAL,
            defaultRatio = when (preset.border?.style) {
                com.craftcam.app.model.BorderStyle.POLAROID_WHITE_CHIN,
                com.craftcam.app.model.BorderStyle.SQUARE_INSTANT_WHITE -> AspectRatioMode.RATIO_1_1
                com.craftcam.app.model.BorderStyle.CLASSIC_35MM_RANGEFINDER,
                com.craftcam.app.model.BorderStyle.FILM_STRIP_135 -> AspectRatioMode.RATIO_3_2
                else -> AspectRatioMode.RATIO_4_3
            },
            filmProfile = FilmProfile(
                id = "dynamic_${preset.id}",
                name = preset.name,
                lutAssetPath = ShaderConstants.getLutAssetForPreset(preset.id),
                contrast = preset.colorGrading.contrast,
                saturation = preset.colorGrading.saturation,
                temperature = preset.colorGrading.temperature,
                tint = preset.colorGrading.tint,
                fade = preset.colorGrading.fade,
                toeLift = 0.030f,
                shoulderClip = 0.975f,
                highlightRollOff = preset.colorGrading.highlightRollOff,
                shadowTintR = preset.colorGrading.shadowTintR,
                shadowTintG = preset.colorGrading.shadowTintG,
                shadowTintB = preset.colorGrading.shadowTintB,
                halationThreshold = preset.halation?.threshold ?: 0.80f,
                halationIntensity = preset.halation?.intensity ?: 0.25f,
                halationColorTintR = 1.0f,
                halationColorTintG = 0.32f,
                halationColorTintB = 0.10f
            ),
            grainProfile = GrainProfile(
                id = "grain_${preset.id}",
                mode = if (preset.grain.assetPath != null) GrainMode.SCANNED else GrainMode.PROCEDURAL,
                assetPath = preset.grain.assetPath ?: ShaderConstants.getGrainAssetForPreset(preset.id, preset.category == com.craftcam.app.model.CameraCategory.CAMCORDER),
                intensity = preset.grain.intensity,
                scale = preset.grain.scale,
                blendMode = preset.grain.blendMode,
                shadowStrength = preset.grain.shadowStrength,
                highlightStrength = preset.grain.highlightStrength,
                isColorNoise = preset.grain.isColorNoise,
                luminanceWeightingEnabled = true,
                luminanceWeightingPower = 1.0f
            )
        )
    }

    fun getUnifiedProfile(preset: CameraPreset): CameraProfile =
        getProfileForPreset(preset).toUnifiedCameraProfile()
}

