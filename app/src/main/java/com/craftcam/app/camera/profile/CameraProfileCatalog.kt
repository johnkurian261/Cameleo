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
        filmProfile = FilmProfile(
            id = "film_35mm_classic",
            name = "Classic 35mm Universal Negative",
            lutAssetPath = "lut_analog_35mm_classic.png",
            contrast = 1.14f,
            saturation = 1.10f,
            temperature = 0.12f,
            tint = -0.01f,
            toeLift = 0.035f,
            shoulderClip = 0.965f,
            highlightRollOff = 0.80f,
            halationThreshold = 0.74f,
            halationIntensity = 0.26f,
            halationColorTintR = 1.0f,
            halationColorTintG = 0.28f,
            halationColorTintB = 0.06f
        ),
        grainProfile = GrainProfile(
            id = "grain_35mm_fine",
            mode = GrainMode.SCANNED,
            assetPath = "grains/grain_dclassic.jpg",
            intensity = 0.22f,
            scale = 0.95f,
            luminanceWeightingEnabled = true,
            luminanceWeightingPower = 0.70f
        ),
        opticalProfile = OpticalProfile(
            id = "optical_dclassic",
            vignetteStrength = 0.35f,
            vignetteRadius = 0.80f,
            chromaticAberration = 0.0025f,
            edgeSoftness = 0.08f,
            distortionStrength = 0.0f
        )
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
            mode = GrainMode.SCANNED,
            assetPath = "grains/grain_ccdr.jpg",
            intensity = 0.24f,
            scale = 0.90f,
            isColorNoise = false,
            luminanceWeightingEnabled = true,
            luminanceWeightingPower = 0.5f
        ),
        opticalProfile = OpticalProfile(
            id = "optical_ccd_r",
            vignetteStrength = 0.28f,
            vignetteRadius = 0.88f,
            chromaticAberration = 0.002f,
            distortionStrength = 0.0f
        )
    )

    val DQS_CAMERA = CameleoCameraProfile(
        id = "ccd_dqs",
        name = "DQS",
        subtitle = "CCD Digital Compact",
        presetId = "ccd_retro_compact",
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
            id = "film_dqs",
            name = "DQS Compact Sensor",
            lutAssetPath = "lut_ccd_retro_compact.png",
            contrast = 1.15f,
            saturation = 1.12f,
            toeLift = 0.015f,
            shoulderClip = 0.990f,
            highlightRollOff = 0.88f,
            halationThreshold = 0.90f,
            halationIntensity = 0.10f,
            halationColorTintR = 0.85f,
            halationColorTintG = 0.85f,
            halationColorTintB = 1.0f
        ),
        grainProfile = GrainProfile(
            id = "grain_dqs",
            mode = GrainMode.SCANNED,
            assetPath = "grains/grain_dqs.jpg",
            intensity = 0.18f,
            scale = 0.85f,
            isColorNoise = false,
            luminanceWeightingEnabled = true,
            luminanceWeightingPower = 0.5f
        ),
        opticalProfile = OpticalProfile(
            id = "optical_dqs",
            vignetteStrength = 0.20f,
            vignetteRadius = 0.90f,
            chromaticAberration = 0.002f,
            distortionStrength = 0.0f
        )
    )

    val CLASSIC_U = CameleoCameraProfile(
        id = "ccd_classic_u",
        name = "Classic U",
        subtitle = "MySpace Era Digicam",
        presetId = "ccd_y2k_digital",
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
            id = "film_classic_u",
            name = "Classic U CCD Sensor",
            lutAssetPath = "lut_ccd_y2k_digital.png",
            contrast = 1.18f,
            saturation = 1.16f,
            temperature = 0.06f,
            tint = 0.10f,
            toeLift = 0.020f,
            shoulderClip = 0.990f,
            highlightRollOff = 0.86f,
            halationThreshold = 0.88f,
            halationIntensity = 0.12f,
            halationColorTintR = 1.0f,
            halationColorTintG = 0.80f,
            halationColorTintB = 0.90f
        ),
        grainProfile = GrainProfile(
            id = "grain_classic_u",
            mode = GrainMode.SCANNED,
            assetPath = "grains/grain_classicu.jpg",
            intensity = 0.22f,
            scale = 0.90f,
            isColorNoise = false,
            luminanceWeightingEnabled = true,
            luminanceWeightingPower = 0.5f
        ),
        opticalProfile = OpticalProfile(
            id = "optical_classic_u",
            vignetteStrength = 0.24f,
            vignetteRadius = 0.88f,
            chromaticAberration = 0.002f,
            distortionStrength = 0.0f
        )
    )

    val CCD_FLASH = CameleoCameraProfile(
        id = "ccd_flash",
        name = "CCD Flash",
        subtitle = "Night Party Flash Look",
        presetId = "ccd_flash",
        focalLengths = listOf(
            FocalLengthOption("32mm", 32f),
            FocalLengthOption("50mm", 50f)
        ),
        defaultFocalLengthMm = 32f,
        supportedLenses = listOf(
            LensType.NORMAL,
            LensType.COLOR_FLASH,
            LensType.STARBURST
        ),
        defaultLens = LensType.NORMAL,
        defaultRatio = AspectRatioMode.RATIO_4_3,
        filmProfile = FilmProfile(
            id = "film_ccd_flash",
            name = "CCD Direct Flash Sensor",
            lutAssetPath = "lut_ccd_flash.png",
            contrast = 1.30f,
            saturation = 1.18f,
            temperature = 0.08f,
            tint = 0.02f,
            toeLift = 0.020f,
            shoulderClip = 0.985f,
            highlightRollOff = 0.88f,
            halationThreshold = 0.84f,
            halationIntensity = 0.22f,
            halationColorTintR = 1.0f,
            halationColorTintG = 0.88f,
            halationColorTintB = 0.92f
        ),
        grainProfile = GrainProfile(
            id = "grain_ccd_flash",
            mode = GrainMode.SCANNED,
            assetPath = "grains/grain_dfuns.jpg",
            intensity = 0.22f,
            scale = 0.90f,
            isColorNoise = false,
            luminanceWeightingEnabled = true,
            luminanceWeightingPower = 0.5f
        ),
        opticalProfile = OpticalProfile(
            id = "optical_ccd_flash",
            vignetteStrength = 0.45f,
            vignetteRadius = 0.72f,
            chromaticAberration = 0.003f,
            distortionStrength = 0.0f
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
            FocalLengthOption("26mm", 26f, isPhysicalLens = true),
            FocalLengthOption("28mm", 28f),
            FocalLengthOption("35mm", 35f),
            FocalLengthOption("50mm", 50f)
        ),
        defaultFocalLengthMm = 26f,
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
        // Zero Natural Camera Mode: 100% clean, bypasses all film simulation & LUT
        filmProfile = FilmProfile(
            id = "natural_zero",
            name = "Zero Natural Sensor",
            lutAssetPath = null,
            lutStrength = 0.0f,
            contrast = 1.0f,
            saturation = 1.0f,
            temperature = 0.0f,
            tint = 0.0f,
            toeLift = 0.0f,
            shoulderClip = 1.0f,
            highlightRollOff = 0.92f,
            shadowTintR = 0.0f,
            shadowTintG = 0.0f,
            shadowTintB = 0.0f,
            halationThreshold = 1.0f,
            halationIntensity = 0.0f
        ),
        grainProfile = GrainProfile.None,
        opticalProfile = OpticalProfile.Standard,
        exposureProfile = ExposureProfile.Standard,
        computationalProfile = ComputationalProfile.SingleShot
    )

    val D_3D = CameleoCameraProfile(
        id = "d_3d",
        name = "D 3D",
        subtitle = "Stereoscopic 3D",
        presetId = "analog_cinema_film",
        focalLengths = listOf(
            FocalLengthOption("30mm", 30f),
            FocalLengthOption("35mm", 35f)
        ),
        defaultFocalLengthMm = 30f,
        supportedLenses = listOf(
            LensType.NORMAL,
            LensType.FISHEYE_F,
            LensType.FISHEYE_W,
            LensType.PRISM,
            LensType.STARBURST
        ),
        defaultLens = LensType.NORMAL,
        defaultRatio = AspectRatioMode.RATIO_4_3,
        filmProfile = FilmProfile(
            id = "film_d3d",
            name = "Stereoscopic Motion Picture",
            lutAssetPath = "lut_analog_cinema_film.png",
            contrast = 1.14f,
            saturation = 1.08f,
            temperature = -0.08f,
            tint = 0.02f,
            toeLift = 0.03f,
            shoulderClip = 0.965f,
            highlightRollOff = 0.78f,
            halationThreshold = 0.80f,
            halationIntensity = 0.42f,
            halationColorTintR = 1.0f,
            halationColorTintG = 0.25f,
            halationColorTintB = 0.05f
        ),
        grainProfile = GrainProfile(
            id = "grain_d3d",
            mode = GrainMode.SCANNED,
            assetPath = "grains/grain_d3d1.jpg",
            intensity = 0.28f,
            scale = 1.05f,
            luminanceWeightingEnabled = true,
            luminanceWeightingPower = 1.0f
        )
    )

    val allProfiles = listOf(
        FXN_R,
        D_CLASSIC,
        D_EXP,
        D_3D,
        CCD_DIGITAL,
        CCD_FLASH,
        CLASSIC_U,
        DQS_CAMERA,
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
            "analog_35mm_classic", "d_classic", "dclassic", "d classic", "classic_35mm" -> return D_CLASSIC
            "analog_cinema_film", "d3d", "d_3d", "d 3d" -> return D_3D
            "analog_vintage_color" -> return D_EXP
            "ccd_r", "ccdr", "ccd r", "ccd-r", "ccd_classic" -> return CCD_DIGITAL
            "ccd_flash", "d_funs", "dfuns" -> return CCD_FLASH
            "ccd_y2k_digital", "classic_u", "classicu" -> return CLASSIC_U
            "dqs", "ccd_retro_compact" -> return DQS_CAMERA
            "inst_vintage" -> return INSTANT_CAMERA
            "disp_classic", "disp_flash", "disp_warm", "disp_night" -> return DISPOSABLE_FUNS
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

