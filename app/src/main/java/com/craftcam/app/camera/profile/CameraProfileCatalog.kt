package com.craftcam.app.camera.profile

import com.craftcam.app.camera.AspectRatioMode
import com.craftcam.app.gl.shaders.ShaderConstants
import com.craftcam.app.model.CameraPreset
import com.craftcam.app.model.GrainBlendMode

object CameraProfileCatalog {

    val FXN_R = CameleoCameraProfile(
        id = "fxn_r",
        name = "FXNR",
        subtitle = "Classic Negative",
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
            name = "Classic Negative",
            lutAssetPath = "lut_fxn_r.png",
            contrast = 1.08f,
            saturation = 1.02f,
            temperature = 0.18f,
            tint = 0.02f,
            toeLift = 0.040f,
            shoulderClip = 0.965f,
            highlightRollOff = 0.78f,
            shadowTintR = 0.048f,
            shadowTintG = 0.022f,
            shadowTintB = 0.008f,
            halationThreshold = 0.78f,
            halationIntensity = 0.26f,
            halationColorTintR = 1.0f,
            halationColorTintG = 0.35f,
            halationColorTintB = 0.08f
        ),
        grainProfile = GrainProfile(
            id = "grain_fxnr",
            mode = GrainMode.SCANNED,
            assetPath = "grains/grain_ou.jpg",
            intensity = 0.20f,
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
            FocalLengthOption("13mm", 28f, isPhysicalLens = true),
            FocalLengthOption("26mm", 26f, isPhysicalLens = true)
        ),
        defaultFocalLengthMm = 28f,
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
        name = "D3D",
        subtitle = "Single-Image 3D Photography",
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
        d3dProfile = D3DProfile.Default,
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

    val VSCO_A6 = CameleoCameraProfile(
        id = "vsco_a6",
        name = "A6",
        subtitle = "Analog Portra Warm",
        presetId = "vsco_a6",
        focalLengths = listOf(FocalLengthOption("35mm", 35f), FocalLengthOption("50mm", 50f)),
        defaultFocalLengthMm = 35f,
        supportedLenses = listOf(LensType.NORMAL, LensType.FISHEYE_F, LensType.FISHEYE_W, LensType.PRISM, LensType.STARBURST),
        defaultLens = LensType.NORMAL,
        defaultRatio = AspectRatioMode.RATIO_4_3,
        filmProfile = FilmProfile(
            id = "film_vsco_a6",
            name = "VSCO A6 Analog",
            lutAssetPath = "lut_vsco_a6.png",
            contrast = 1.12f,
            saturation = 1.04f,
            temperature = 0.14f,
            tint = 0.02f,
            toeLift = 0.038f,
            shoulderClip = 0.965f,
            highlightRollOff = 0.82f,
            shadowTintR = 0.025f,
            shadowTintG = 0.012f,
            shadowTintB = 0.0f,
            halationThreshold = 0.78f,
            halationIntensity = 0.22f,
            halationColorTintR = 1.0f,
            halationColorTintG = 0.35f,
            halationColorTintB = 0.08f
        ),
        grainProfile = GrainProfile(
            id = "grain_vsco_a6",
            mode = GrainMode.SCANNED,
            assetPath = "grains/grain_ou.jpg",
            intensity = 0.20f,
            scale = 1.0f,
            luminanceWeightingEnabled = true,
            luminanceWeightingPower = 1.0f
        ),
        opticalProfile = OpticalProfile(
            id = "optical_vsco_a6",
            vignetteStrength = 0.25f,
            vignetteRadius = 0.85f,
            chromaticAberration = 0.002f
        )
    )

    val VSCO_C1 = CameleoCameraProfile(
        id = "vsco_c1",
        name = "C1",
        subtitle = "Vibrant Color Negative",
        presetId = "vsco_c1",
        focalLengths = listOf(FocalLengthOption("35mm", 35f), FocalLengthOption("50mm", 50f)),
        defaultFocalLengthMm = 35f,
        supportedLenses = listOf(LensType.NORMAL, LensType.FISHEYE_F, LensType.FISHEYE_W, LensType.PRISM),
        defaultLens = LensType.NORMAL,
        defaultRatio = AspectRatioMode.RATIO_4_3,
        filmProfile = FilmProfile(
            id = "film_vsco_c1",
            name = "VSCO C1 Vibrant",
            lutAssetPath = "lut_vsco_c1.png",
            contrast = 1.18f,
            saturation = 1.16f,
            temperature = -0.02f,
            tint = 0.01f,
            toeLift = 0.025f,
            shoulderClip = 0.980f,
            highlightRollOff = 0.80f
        ),
        grainProfile = GrainProfile(
            id = "grain_vsco_c1",
            mode = GrainMode.SCANNED,
            assetPath = "grains/grain_dclassic.jpg",
            intensity = 0.18f,
            scale = 0.95f,
            luminanceWeightingEnabled = true
        )
    )

    val VSCO_F2 = CameleoCameraProfile(
        id = "vsco_f2",
        name = "F2",
        subtitle = "Mellow Moody Film",
        presetId = "vsco_f2",
        focalLengths = listOf(FocalLengthOption("35mm", 35f), FocalLengthOption("50mm", 50f)),
        defaultFocalLengthMm = 35f,
        supportedLenses = listOf(LensType.NORMAL, LensType.FISHEYE_F, LensType.FISHEYE_W, LensType.PRISM),
        defaultLens = LensType.NORMAL,
        defaultRatio = AspectRatioMode.RATIO_4_3,
        filmProfile = FilmProfile(
            id = "film_vsco_f2",
            name = "VSCO F2 Mellow",
            lutAssetPath = "lut_vsco_f2.png",
            contrast = 1.05f,
            saturation = 0.88f,
            temperature = 0.08f,
            tint = 0.02f,
            toeLift = 0.055f,
            shoulderClip = 0.940f,
            highlightRollOff = 0.76f,
            shadowTintB = 0.04f
        ),
        grainProfile = GrainProfile(
            id = "grain_vsco_f2",
            mode = GrainMode.SCANNED,
            assetPath = "grains/grain_s67.jpg",
            intensity = 0.22f,
            scale = 1.05f,
            luminanceWeightingEnabled = true
        )
    )

    val VSCO_G3 = CameleoCameraProfile(
        id = "vsco_g3",
        name = "G3",
        subtitle = "Luminous Portrait Glow",
        presetId = "vsco_g3",
        focalLengths = listOf(FocalLengthOption("50mm", 50f), FocalLengthOption("85mm", 85f)),
        defaultFocalLengthMm = 50f,
        supportedLenses = listOf(LensType.NORMAL, LensType.FISHEYE_F, LensType.FISHEYE_W, LensType.STARBURST),
        defaultLens = LensType.NORMAL,
        defaultRatio = AspectRatioMode.RATIO_4_3,
        filmProfile = FilmProfile(
            id = "film_vsco_g3",
            name = "VSCO G3 Portraits",
            lutAssetPath = "lut_vsco_g3.png",
            contrast = 1.02f,
            saturation = 1.06f,
            temperature = 0.16f,
            tint = 0.03f,
            toeLift = 0.030f,
            shoulderClip = 0.958f,
            highlightRollOff = 0.84f,
            shadowTintR = 0.03f,
            shadowTintG = 0.015f
        ),
        grainProfile = GrainProfile(
            id = "grain_vsco_g3",
            mode = GrainMode.SCANNED,
            assetPath = "grains/grain_ou.jpg",
            intensity = 0.16f,
            scale = 0.90f,
            luminanceWeightingEnabled = true
        )
    )

    val VSCO_M5 = CameleoCameraProfile(
        id = "vsco_m5",
        name = "M5",
        subtitle = "1970s Vintage Mood",
        presetId = "vsco_m5",
        focalLengths = listOf(FocalLengthOption("35mm", 35f), FocalLengthOption("50mm", 50f)),
        defaultFocalLengthMm = 35f,
        supportedLenses = listOf(LensType.NORMAL, LensType.FISHEYE_F, LensType.FISHEYE_W, LensType.PRISM),
        defaultLens = LensType.NORMAL,
        defaultRatio = AspectRatioMode.RATIO_3_2,
        filmProfile = FilmProfile(
            id = "film_vsco_m5",
            name = "VSCO M5 Vintage Mood",
            lutAssetPath = "lut_vsco_m5.png",
            contrast = 1.10f,
            saturation = 0.96f,
            temperature = 0.14f,
            tint = -0.04f,
            toeLift = 0.065f,
            shoulderClip = 0.935f,
            highlightRollOff = 0.74f,
            shadowTintR = 0.055f,
            shadowTintG = 0.030f,
            shadowTintB = 0.008f
        ),
        grainProfile = GrainProfile(
            id = "grain_vsco_m5",
            mode = GrainMode.SCANNED,
            assetPath = "grains/grain_dexp.jpg",
            intensity = 0.26f,
            scale = 1.15f,
            luminanceWeightingEnabled = true
        )
    )

    val VSCO_HB1 = CameleoCameraProfile(
        id = "vsco_hb1",
        name = "HB1",
        subtitle = "Hypebeast Street Film",
        presetId = "vsco_hb1",
        focalLengths = listOf(FocalLengthOption("28mm", 28f), FocalLengthOption("35mm", 35f)),
        defaultFocalLengthMm = 28f,
        supportedLenses = listOf(LensType.NORMAL, LensType.FISHEYE_F, LensType.FISHEYE_W),
        defaultLens = LensType.NORMAL,
        defaultRatio = AspectRatioMode.RATIO_4_3,
        filmProfile = FilmProfile(
            id = "film_vsco_hb1",
            name = "VSCO HB1 Street",
            lutAssetPath = "lut_vsco_hb1.png",
            contrast = 1.26f,
            saturation = 0.94f,
            temperature = -0.06f,
            tint = 0.01f,
            toeLift = 0.012f,
            shoulderClip = 0.985f,
            highlightRollOff = 0.82f,
            shadowTintR = -0.02f,
            shadowTintG = 0.01f,
            shadowTintB = 0.03f
        ),
        grainProfile = GrainProfile(
            id = "grain_vsco_hb1",
            mode = GrainMode.SCANNED,
            assetPath = "grains/grain_ou.jpg",
            intensity = 0.22f,
            scale = 1.0f,
            luminanceWeightingEnabled = true
        )
    )

    val VSCO_B1 = CameleoCameraProfile(
        id = "vsco_b1",
        name = "B1",
        subtitle = "B&W Classic Negative",
        presetId = "vsco_b1",
        focalLengths = listOf(FocalLengthOption("35mm", 35f), FocalLengthOption("50mm", 50f)),
        defaultFocalLengthMm = 35f,
        supportedLenses = listOf(LensType.NORMAL, LensType.FISHEYE_F, LensType.FISHEYE_W, LensType.PRISM),
        defaultLens = LensType.NORMAL,
        defaultRatio = AspectRatioMode.RATIO_3_2,
        filmProfile = FilmProfile(
            id = "film_vsco_b1",
            name = "VSCO B1 Classic B&W",
            lutAssetPath = "lut_vsco_b1.png",
            contrast = 1.22f,
            saturation = 0.0f,
            toeLift = 0.035f,
            shoulderClip = 0.970f,
            highlightRollOff = 0.78f
        ),
        grainProfile = GrainProfile(
            id = "grain_vsco_b1",
            mode = GrainMode.SCANNED,
            assetPath = "grains/grain_dclassic.jpg",
            intensity = 0.28f,
            scale = 1.10f,
            luminanceWeightingEnabled = true
        )
    )

    val VSCO_X1 = CameleoCameraProfile(
        id = "vsco_x1",
        name = "X1",
        subtitle = "B&W Matte Fade",
        presetId = "vsco_x1",
        focalLengths = listOf(FocalLengthOption("35mm", 35f), FocalLengthOption("50mm", 50f)),
        defaultFocalLengthMm = 35f,
        supportedLenses = listOf(LensType.NORMAL, LensType.FISHEYE_F, LensType.FISHEYE_W, LensType.PRISM),
        defaultLens = LensType.NORMAL,
        defaultRatio = AspectRatioMode.RATIO_3_2,
        filmProfile = FilmProfile(
            id = "film_vsco_x1",
            name = "VSCO X1 Matte B&W",
            lutAssetPath = "lut_vsco_x1.png",
            contrast = 1.08f,
            saturation = 0.0f,
            toeLift = 0.080f,
            shoulderClip = 0.925f,
            highlightRollOff = 0.72f
        ),
        grainProfile = GrainProfile(
            id = "grain_vsco_x1",
            mode = GrainMode.SCANNED,
            assetPath = "grains/grain_dexp.jpg",
            intensity = 0.25f,
            scale = 1.05f,
            luminanceWeightingEnabled = true
        )
    )

    val VSCO_HANDYCAM = CameleoCameraProfile(
        id = "vsco_handycam",
        name = "Handycam 85",
        subtitle = "1985 Video8 Camcorder",
        presetId = "vsco_handycam",
        focalLengths = listOf(FocalLengthOption("38mm", 38f), FocalLengthOption("50mm", 50f)),
        defaultFocalLengthMm = 38f,
        supportedLenses = listOf(LensType.NORMAL, LensType.FISHEYE_F, LensType.FISHEYE_W, LensType.WIDE, LensType.PRISM),
        defaultLens = LensType.NORMAL,
        defaultRatio = AspectRatioMode.RATIO_4_3,
        filmProfile = FilmProfile(
            id = "film_vsco_handycam",
            name = "VSCO Handycam 85",
            lutAssetPath = "lut_vsco_handycam.png",
            contrast = 1.12f,
            saturation = 0.92f,
            temperature = -0.04f,
            tint = 0.03f,
            toeLift = 0.045f,
            shoulderClip = 0.950f,
            highlightRollOff = 0.80f
        ),
        grainProfile = GrainProfile(
            id = "grain_vsco_handycam",
            mode = GrainMode.SCANNED,
            assetPath = "grains/grain8mm_01.jpg",
            intensity = 0.30f,
            scale = 1.30f,
            isColorNoise = true
        ),
        opticalProfile = OpticalProfile(
            id = "optical_vsco_handycam",
            vignetteStrength = 0.35f,
            vignetteRadius = 0.80f,
            chromaticAberration = 0.0075f
        )
    )

    val S_67 = CameleoCameraProfile(
        id = "s_67",
        name = "S67",
        subtitle = "6x7 Medium Format Roll Film",
        presetId = "analog_s67",
        focalLengths = listOf(FocalLengthOption("90mm", 90f), FocalLengthOption("105mm", 105f)),
        defaultFocalLengthMm = 90f,
        supportedLenses = listOf(LensType.NORMAL, LensType.FISHEYE_F, LensType.FISHEYE_W, LensType.PRISM, LensType.STARBURST),
        defaultLens = LensType.NORMAL,
        defaultRatio = AspectRatioMode.RATIO_4_3,
        filmProfile = FilmProfile(
            id = "film_s67",
            name = "6x7 Medium Format Emulsion",
            lutAssetPath = "lut_analog_35mm_classic.png",
            contrast = 1.08f,
            saturation = 1.10f,
            temperature = 0.06f,
            tint = 0.01f,
            fade = 0.035f,
            toeLift = 0.035f,
            shoulderClip = 0.965f,
            highlightRollOff = 0.88f,
            halationThreshold = 0.78f,
            halationIntensity = 0.24f,
            halationColorTintR = 1.0f,
            halationColorTintG = 0.30f,
            halationColorTintB = 0.08f
        ),
        grainProfile = GrainProfile(
            id = "grain_s67",
            mode = GrainMode.SCANNED,
            assetPath = "grains/grain_s67.jpg",
            intensity = 0.16f,
            scale = 0.85f,
            luminanceWeightingEnabled = true,
            luminanceWeightingPower = 1.0f
        ),
        opticalProfile = OpticalProfile(
            id = "optical_s67",
            vignetteStrength = 0.24f,
            vignetteRadius = 0.84f,
            chromaticAberration = 0.002f
        )
    )

    val D_BLACK = CameleoCameraProfile(
        id = "d_black",
        name = "D Black",
        subtitle = "Monochrome Rangefinder",
        presetId = "analog_dblack",
        focalLengths = listOf(FocalLengthOption("35mm", 35f), FocalLengthOption("50mm", 50f)),
        defaultFocalLengthMm = 35f,
        supportedLenses = listOf(LensType.NORMAL, LensType.FISHEYE_F, LensType.FISHEYE_W, LensType.PRISM),
        defaultLens = LensType.NORMAL,
        defaultRatio = AspectRatioMode.RATIO_4_3,
        filmProfile = FilmProfile(
            id = "film_dblack",
            name = "Tri-X 400 Monochrome",
            lutAssetPath = "lut_bw.png",
            contrast = 1.18f,
            saturation = 0.0f,
            temperature = 0.0f,
            tint = 0.0f,
            fade = 0.02f,
            toeLift = 0.025f,
            shoulderClip = 0.970f,
            highlightRollOff = 0.88f,
            halationThreshold = 0.82f,
            halationIntensity = 0.18f,
            halationColorTintR = 0.90f,
            halationColorTintG = 0.90f,
            halationColorTintB = 0.90f
        ),
        grainProfile = GrainProfile(
            id = "grain_dblack",
            mode = GrainMode.SCANNED,
            assetPath = "grains/grain_dexp.jpg",
            intensity = 0.32f,
            scale = 1.25f,
            shadowStrength = 0.45f,
            highlightStrength = 0.20f,
            luminanceWeightingEnabled = true,
            luminanceWeightingPower = 1.0f
        ),
        opticalProfile = OpticalProfile(
            id = "optical_dblack",
            vignetteStrength = 0.26f,
            vignetteRadius = 0.82f,
            chromaticAberration = 0.0f
        )
    )

    val NT_16 = CameleoCameraProfile(
        id = "nt_16",
        name = "NT16",
        subtitle = "Natura 1600 Street Night",
        presetId = "analog_nt16",
        focalLengths = listOf(FocalLengthOption("28mm", 28f), FocalLengthOption("35mm", 35f)),
        defaultFocalLengthMm = 28f,
        supportedLenses = listOf(LensType.NORMAL, LensType.FISHEYE_F, LensType.FISHEYE_W, LensType.PRISM, LensType.STARBURST),
        defaultLens = LensType.NORMAL,
        defaultRatio = AspectRatioMode.RATIO_4_3,
        filmProfile = FilmProfile(
            id = "film_nt16",
            name = "Natura 1600 High-Speed Negative",
            lutAssetPath = "lut_nt16.png",
            contrast = 1.10f,
            saturation = 1.08f,
            temperature = -0.08f,
            tint = 0.03f,
            fade = 0.03f,
            toeLift = 0.035f,
            shoulderClip = 0.960f,
            highlightRollOff = 0.88f,
            shadowTintR = 0.0f,
            shadowTintG = 0.03f,
            shadowTintB = 0.05f,
            halationThreshold = 0.74f,
            halationIntensity = 0.32f,
            halationColorTintR = 1.0f,
            halationColorTintG = 0.28f,
            halationColorTintB = 0.06f
        ),
        grainProfile = GrainProfile(
            id = "grain_nt16",
            mode = GrainMode.SCANNED,
            assetPath = "grains/grain_ou_lowlight.jpg",
            intensity = 0.28f,
            scale = 1.15f,
            shadowStrength = 0.40f,
            highlightStrength = 0.16f,
            luminanceWeightingEnabled = true,
            luminanceWeightingPower = 1.0f
        ),
        opticalProfile = OpticalProfile(
            id = "optical_nt16",
            vignetteStrength = 0.24f,
            vignetteRadius = 0.84f,
            chromaticAberration = 0.004f
        )
    )

    val RETRO_VHS = CameleoCameraProfile(
        id = "cam_retro_vhs",
        name = "Retro VHS",
        subtitle = "Tape Distortion & OSD",
        presetId = "cam_retro_vhs",
        focalLengths = listOf(FocalLengthOption("12mm", 12f), FocalLengthOption("24mm", 24f)),
        defaultFocalLengthMm = 12f,
        supportedLenses = listOf(LensType.NORMAL, LensType.FISHEYE_F, LensType.FISHEYE_W, LensType.PRISM),
        defaultLens = LensType.NORMAL,
        defaultRatio = AspectRatioMode.RATIO_4_3,
        filmProfile = FilmProfile(
            id = "film_retro_vhs",
            name = "1988 VHS Magnetic Tape",
            lutAssetPath = "lut_cam_retro_vhs.png",
            contrast = 1.08f,
            saturation = 0.95f,
            temperature = 0.04f,
            tint = 0.08f,
            fade = 0.08f,
            toeLift = 0.05f,
            shoulderClip = 0.950f,
            highlightRollOff = 0.88f,
            shadowTintR = 0.04f,
            shadowTintG = 0.01f,
            shadowTintB = 0.05f,
            halationThreshold = 0.82f,
            halationIntensity = 0.20f,
            halationColorTintR = 0.85f,
            halationColorTintG = 0.85f,
            halationColorTintB = 1.0f
        ),
        grainProfile = GrainProfile(
            id = "grain_retro_vhs",
            mode = GrainMode.SCANNED,
            assetPath = "grains/vhs_grain01.jpg",
            intensity = 0.32f,
            scale = 1.40f,
            isColorNoise = true,
            blendMode = GrainBlendMode.SCREEN,
            shadowStrength = 0.40f,
            highlightStrength = 0.20f
        ),
        opticalProfile = OpticalProfile(
            id = "optical_retro_vhs",
            vignetteStrength = 0.25f,
            vignetteRadius = 0.84f,
            chromaticAberration = 0.012f,
            edgeSoftness = 0.25f
        )
    )

    val ANALOG_VIDEO = CameleoCameraProfile(
        id = "cam_analog_video",
        name = "Analog Video",
        subtitle = "8mm Home Video Tape",
        presetId = "cam_analog_video",
        focalLengths = listOf(FocalLengthOption("10mm", 10f), FocalLengthOption("20mm", 20f)),
        defaultFocalLengthMm = 10f,
        supportedLenses = listOf(LensType.NORMAL, LensType.FISHEYE_F, LensType.FISHEYE_W),
        defaultLens = LensType.NORMAL,
        defaultRatio = AspectRatioMode.RATIO_4_3,
        filmProfile = FilmProfile(
            id = "film_analog_video",
            name = "8mm Home Video",
            lutAssetPath = "lut_cam_analog_video.png",
            contrast = 1.08f,
            saturation = 1.02f,
            temperature = 0.14f,
            tint = 0.02f,
            fade = 0.05f,
            toeLift = 0.04f,
            shoulderClip = 0.960f,
            highlightRollOff = 0.88f
        ),
        grainProfile = GrainProfile(
            id = "grain_analog_video",
            mode = GrainMode.SCANNED,
            assetPath = "grains/grain_16mm.jpg",
            intensity = 0.24f,
            scale = 1.30f
        ),
        opticalProfile = OpticalProfile(
            id = "optical_analog_video",
            vignetteStrength = 0.26f,
            vignetteRadius = 0.85f,
            chromaticAberration = 0.004f,
            edgeSoftness = 0.15f
        )
    )

    val NINETIES_CAMCORDER = CameleoCameraProfile(
        id = "cam_90s_camcorder",
        name = "90s Camcorder",
        subtitle = "Handheld CCD Video",
        presetId = "cam_90s_camcorder",
        focalLengths = listOf(FocalLengthOption("8mm", 8f), FocalLengthOption("16mm", 16f)),
        defaultFocalLengthMm = 8f,
        supportedLenses = listOf(LensType.NORMAL, LensType.FISHEYE_F, LensType.FISHEYE_W),
        defaultLens = LensType.NORMAL,
        defaultRatio = AspectRatioMode.RATIO_4_3,
        filmProfile = FilmProfile(
            id = "film_90s_camcorder",
            name = "1997 MiniDV Handheld",
            lutAssetPath = "lut_cam_90s_camcorder.png",
            contrast = 1.15f,
            saturation = 1.08f,
            temperature = -0.06f,
            tint = 0.04f,
            fade = 0.03f,
            toeLift = 0.02f,
            shoulderClip = 0.980f,
            highlightRollOff = 0.90f
        ),
        grainProfile = GrainProfile(
            id = "grain_90s_camcorder",
            mode = GrainMode.SCANNED,
            assetPath = "grains/grain_dcr_01.jpg",
            intensity = 0.22f,
            scale = 1.20f,
            isColorNoise = true
        ),
        opticalProfile = OpticalProfile(
            id = "optical_90s_camcorder",
            vignetteStrength = 0.24f,
            vignetteRadius = 0.88f,
            chromaticAberration = 0.003f
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
        ZERO_CAMERA,
        S_67,
        D_BLACK,
        NT_16,
        RETRO_VHS,
        ANALOG_VIDEO,
        NINETIES_CAMCORDER,
        VSCO_A6,
        VSCO_C1,
        VSCO_F2,
        VSCO_G3,
        VSCO_M5,
        VSCO_HB1,
        VSCO_B1,
        VSCO_X1,
        VSCO_HANDYCAM
    )

    /**
     * Resolves a CameleoCameraProfile for any given CameraPreset.
     * Automatically maps canonical presets to explicit profiles, falling back to dynamic creation.
     */
    fun getProfileForPreset(preset: CameraPreset): CameleoCameraProfile {
        when (preset.id) {
            "zero" -> return ZERO_CAMERA
            "analog_fxn_r", "fxnr", "fxn_r" -> return FXN_R
            "analog_35mm_classic", "d_classic", "dclassic", "d classic", "classic_35mm" -> return D_CLASSIC
            "analog_cinema_film", "d3d", "d_3d", "d 3d" -> return D_3D
            "analog_vintage_color", "dexp", "d exp" -> return D_EXP
            "ccd_r", "ccdr", "ccd r", "ccd-r", "ccd_classic" -> return CCD_DIGITAL
            "ccd_flash", "d_funs", "dfuns" -> return CCD_FLASH
            "ccd_y2k_digital", "classic_u", "classicu" -> return CLASSIC_U
            "dqs", "ccd_retro_compact" -> return DQS_CAMERA
            "inst_vintage", "inst_c", "inst_paf", "paf" -> return INSTANT_CAMERA
            "disp_classic", "disp_golf", "disp_flash", "disp_warm" -> return DISPOSABLE_FUNS
            "analog_135ne", "135ne", "135 ne" -> return ONE_THREE_FIVE_NE
            "analog_fisheye_f", "fisheye" -> return FISHEYE_CAMERA
            "analog_soft_film", "ct2f" -> return SOFT_FILM
            "analog_warm_film", "grd", "gr d" -> return WARM_FILM
            "analog_s67", "s67", "s_67" -> return S_67
            "analog_dblack", "dblack", "d_black" -> return D_BLACK
            "analog_nt16", "nt16", "nt_16" -> return NT_16
            "cam_retro_vhs", "retro_vhs", "vhs" -> return RETRO_VHS
            "cam_analog_video", "analog_video" -> return ANALOG_VIDEO
            "cam_90s_camcorder", "90s_camcorder" -> return NINETIES_CAMCORDER
            "vsco_a6", "a6" -> return VSCO_A6
            "vsco_c1", "c1" -> return VSCO_C1
            "vsco_f2", "f2" -> return VSCO_F2
            "vsco_g3", "g3" -> return VSCO_G3
            "vsco_m5", "m5" -> return VSCO_M5
            "vsco_hb1", "hb1" -> return VSCO_HB1
            "vsco_b1", "b1" -> return VSCO_B1
            "vsco_x1", "x1" -> return VSCO_X1
            "vsco_handycam", "handycam", "handycam85" -> return VSCO_HANDYCAM
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

