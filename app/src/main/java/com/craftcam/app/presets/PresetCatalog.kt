package com.craftcam.app.presets

import com.craftcam.app.model.*

object PresetCatalog {

    val allPresets: List<CameraPreset> = listOf(
        // ==================== A. ANALOG FILM CAMERAS ====================
        CameraPreset(
            id = "analog_35mm_classic",
            iconResName = "cam_d_classic",
            name = "35mm Classic",
            subtitle = "Universal Negative Film",
            category = CameraCategory.ANALOG_FILM,
            year = "1984",
            description = "Natural balanced skin tones, gentle highlight roll-off and authentic silver halide grain.",
            colorGrading = ColorGradingConfig(
                contrast = 1.12f,
                saturation = 1.05f,
                temperature = 0.15f,
                tint = -0.05f,
                gamma = 1.02f,
                fade = 0.04f,
                highlightRollOff = 0.80f,
                shadowTintR = 0.02f,
                shadowTintG = 0.03f,
                shadowTintB = 0.05f
            ),
            grain = GrainConfig(enabled = true, intensity = 0.14f, scale = 1.0f),
            lens = LensConfig(vignetteIntensity = 0.32f, vignetteRadius = 0.85f, chromaticAberration = 0.003f, softness = 0.12f),
            halation = HalationConfig(enabled = true, threshold = 0.82f, intensity = 0.25f, radius = 14f),
            dateStamp = DateStampConfig(enabled = true, style = DateStampStyle.SEVEN_SEGMENT_ORANGE, customYearOffset = -28),
            border = BorderConfig(style = BorderStyle.NONE)
        ),
        CameraPreset(
            id = "analog_warm_film",
            iconResName = "cam_gr_f",
            name = "Warm Film",
            subtitle = "Golden Hour Emulsion",
            category = CameraCategory.ANALOG_FILM,
            year = "1978",
            description = "Rich amber highlights and warm copper undertones inspired by late afternoon film photography.",
            colorGrading = ColorGradingConfig(
                contrast = 1.15f,
                saturation = 1.12f,
                temperature = 0.40f,
                tint = 0.08f,
                gamma = 0.98f,
                fade = 0.05f,
                highlightRollOff = 0.76f,
                shadowTintR = 0.06f,
                shadowTintG = 0.04f,
                shadowTintB = 0.01f
            ),
            grain = GrainConfig(enabled = true, intensity = 0.16f, scale = 1.15f),
            lens = LensConfig(vignetteIntensity = 0.38f, vignetteRadius = 0.80f, chromaticAberration = 0.004f, softness = 0.15f),
            halation = HalationConfig(enabled = true, threshold = 0.78f, intensity = 0.32f, radius = 16f),
            dateStamp = DateStampConfig(enabled = true, style = DateStampStyle.SEVEN_SEGMENT_ORANGE, customYearOffset = -30)
        ),
        CameraPreset(
            id = "analog_soft_film",
            iconResName = "cam_ct2f",
            name = "Soft Film",
            subtitle = "Pastel Portrait Stock",
            category = CameraCategory.ANALOG_FILM,
            year = "1992",
            description = "Gentle low-contrast film stock designed to soften harsh sunlight and flatter skin tones.",
            colorGrading = ColorGradingConfig(
                contrast = 0.94f,
                saturation = 0.92f,
                temperature = 0.10f,
                tint = -0.04f,
                gamma = 1.08f,
                fade = 0.08f,
                highlightRollOff = 0.72f
            ),
            grain = GrainConfig(enabled = true, intensity = 0.10f, scale = 0.85f),
            lens = LensConfig(vignetteIntensity = 0.22f, vignetteRadius = 0.90f, chromaticAberration = 0.002f, softness = 0.30f),
            halation = HalationConfig(enabled = true, threshold = 0.76f, intensity = 0.22f, radius = 18f),
            dateStamp = DateStampConfig(enabled = true, style = DateStampStyle.MINIMAL_WHITE)
        ),
        CameraPreset(
            id = "analog_vintage_color",
            iconResName = "cam_d_exp",
            name = "Vintage Color",
            subtitle = "1970s Chromogenic Print",
            category = CameraCategory.ANALOG_FILM,
            year = "1974",
            description = "Nostalgic color matrix with muted blues, earthy greens and organic dye shifts.",
            colorGrading = ColorGradingConfig(
                contrast = 1.18f,
                saturation = 1.10f,
                temperature = 0.25f,
                tint = -0.15f,
                fade = 0.07f,
                highlightRollOff = 0.78f,
                shadowTintR = 0.05f,
                shadowTintG = 0.06f,
                shadowTintB = 0.01f
            ),
            grain = GrainConfig(enabled = true, intensity = 0.18f, scale = 1.25f, isColorNoise = true),
            lens = LensConfig(vignetteIntensity = 0.42f, vignetteRadius = 0.75f, chromaticAberration = 0.005f, softness = 0.14f),
            halation = HalationConfig(enabled = true, threshold = 0.80f, intensity = 0.30f, radius = 15f),
            dateStamp = DateStampConfig(enabled = true, style = DateStampStyle.SEVEN_SEGMENT_ORANGE, customYearOffset = -35)
        ),
        CameraPreset(
            id = "analog_faded_film",
            iconResName = "cam_nt16",
            name = "Faded Film",
            subtitle = "Aged Paper Negative",
            category = CameraCategory.ANALOG_FILM,
            year = "1968",
            description = "Lifted hazy shadow curve with desaturated palette reminiscent of prints stored in attic boxes.",
            colorGrading = ColorGradingConfig(
                contrast = 0.92f,
                saturation = 0.85f,
                temperature = 0.18f,
                tint = 0.05f,
                gamma = 1.12f,
                fade = 0.14f,
                highlightRollOff = 0.76f
            ),
            grain = GrainConfig(enabled = true, intensity = 0.16f, scale = 1.1f),
            lens = LensConfig(vignetteIntensity = 0.32f, vignetteRadius = 0.85f, chromaticAberration = 0.003f, softness = 0.16f),
            dateStamp = DateStampConfig(enabled = false)
        ),
        CameraPreset(
            id = "analog_cinema_film",
            iconResName = "cam_d3d",
            name = "Cinema Film",
            subtitle = "35mm Motion Picture Stock",
            category = CameraCategory.ANALOG_FILM,
            year = "1996",
            description = "Legendary motion picture emulsion with rich teal shadows and luminous red halation bloom around specular highlights.",
            colorGrading = ColorGradingConfig(
                contrast = 1.14f,
                saturation = 1.08f,
                temperature = -0.08f,
                tint = 0.02f,
                fade = 0.03f,
                highlightRollOff = 0.78f,
                shadowTintR = -0.03f,
                shadowTintG = 0.04f,
                shadowTintB = 0.08f
            ),
            grain = GrainConfig(enabled = true, intensity = 0.13f, scale = 0.95f),
            lens = LensConfig(vignetteIntensity = 0.38f, vignetteRadius = 0.80f, chromaticAberration = 0.004f, softness = 0.12f),
            halation = HalationConfig(enabled = true, threshold = 0.80f, intensity = 0.42f, radius = 20f),
            dateStamp = DateStampConfig(enabled = true, style = DateStampStyle.SEVEN_SEGMENT_ORANGE)
        ),

        // ==================== B. CCD DIGITAL CAMERAS ====================
        CameraPreset(
            id = "ccd_classic",
            iconResName = "cam_135ne",
            name = "CCD Classic",
            subtitle = "Early 2000s Compact Sensor",
            category = CameraCategory.CCD_DIGITAL,
            year = "2003",
            description = "Vibrant sky blues, crisp edge definition, punchy contrast and characteristic CCD color rendition.",
            colorGrading = ColorGradingConfig(
                contrast = 1.20f,
                saturation = 1.22f,
                temperature = -0.06f,
                tint = 0.02f,
                gamma = 0.96f,
                fade = 0.01f,
                highlightRollOff = 0.92f
            ),
            grain = GrainConfig(enabled = true, intensity = 0.05f, scale = 0.7f, isColorNoise = true),
            lens = LensConfig(vignetteIntensity = 0.18f, vignetteRadius = 0.92f, chromaticAberration = 0.002f, softness = 0.0f),
            dateStamp = DateStampConfig(enabled = true, style = DateStampStyle.SEVEN_SEGMENT_ORANGE, customYearOffset = -22)
        ),
        CameraPreset(
            id = "ccd_flash",
            iconResName = "cam_d_funs",
            name = "CCD Flash",
            subtitle = "Night Party Flash Look",
            category = CameraCategory.CCD_DIGITAL,
            year = "2005",
            description = "High micro-contrast with bright subject illumination and deep dramatic background falloff.",
            colorGrading = ColorGradingConfig(
                contrast = 1.30f,
                saturation = 1.15f,
                temperature = 0.08f,
                gamma = 0.92f,
                fade = 0.01f
            ),
            grain = GrainConfig(enabled = true, intensity = 0.09f, scale = 0.9f, isColorNoise = true),
            lens = LensConfig(vignetteIntensity = 0.55f, vignetteRadius = 0.65f),
            dateStamp = DateStampConfig(enabled = true, style = DateStampStyle.SEVEN_SEGMENT_ORANGE, customYearOffset = -20)
        ),
        CameraPreset(
            id = "ccd_cool_digital",
            iconResName = "cam_ir",
            name = "Cool Digital",
            subtitle = "Y2K Cyber Millennium",
            category = CameraCategory.CCD_DIGITAL,
            year = "2001",
            description = "Subtle cyan-blue bias and cool white balance typical of turn-of-the-century Japanese digicams.",
            colorGrading = ColorGradingConfig(
                contrast = 1.16f,
                saturation = 1.08f,
                temperature = -0.32f,
                tint = -0.06f,
                fade = 0.02f,
                shadowTintR = -0.04f,
                shadowTintG = 0.01f,
                shadowTintB = 0.07f
            ),
            grain = GrainConfig(enabled = true, intensity = 0.07f, scale = 0.8f),
            lens = LensConfig(vignetteIntensity = 0.28f, vignetteRadius = 0.85f),
            dateStamp = DateStampConfig(enabled = true, style = DateStampStyle.DIGITAL_LCD_GREEN, customYearOffset = -25)
        ),
        CameraPreset(
            id = "ccd_y2k_digital",
            iconResName = "cam_classic_u",
            name = "Y2K Digital",
            subtitle = "MySpace Era Digicam",
            category = CameraCategory.CCD_DIGITAL,
            year = "2004",
            description = "Saturated magenta-pink highlights and crunchy digital sensor response loved by aesthetic creators.",
            colorGrading = ColorGradingConfig(
                contrast = 1.24f,
                saturation = 1.28f,
                temperature = 0.12f,
                tint = 0.22f,
                fade = 0.03f
            ),
            grain = GrainConfig(enabled = true, intensity = 0.08f, scale = 1.0f, isColorNoise = true),
            lens = LensConfig(vignetteIntensity = 0.32f, vignetteRadius = 0.80f, chromaticAberration = 0.004f),
            dateStamp = DateStampConfig(enabled = true, style = DateStampStyle.SEVEN_SEGMENT_ORANGE, customYearOffset = -22)
        ),
        CameraPreset(
            id = "ccd_retro_compact",
            iconResName = "cam_dqs",
            name = "Retro Compact",
            subtitle = "Tourist Pocket Camera",
            category = CameraCategory.CCD_DIGITAL,
            year = "2006",
            description = "Everyday point-and-shoot digital look with punchy dynamic range and crisp sharpness.",
            colorGrading = ColorGradingConfig(
                contrast = 1.15f,
                saturation = 1.18f,
                temperature = 0.05f,
                fade = 0.02f
            ),
            grain = GrainConfig(enabled = true, intensity = 0.06f, scale = 0.7f),
            lens = LensConfig(vignetteIntensity = 0.22f, vignetteRadius = 0.88f),
            dateStamp = DateStampConfig(enabled = true, style = DateStampStyle.SEVEN_SEGMENT_ORANGE, customYearOffset = -20)
        ),

        // ==================== C. DISPOSABLE CAMERAS ====================
        CameraPreset(
            id = "disp_classic",
            iconResName = "cam_golf",
            name = "Disposable Classic",
            subtitle = "Single-Use 27 Exp",
            category = CameraCategory.DISPOSABLE,
            year = "1997",
            description = "High contrast, pronounced lens vignetting, plastic optic chromatic fringing and warm party tones.",
            colorGrading = ColorGradingConfig(
                contrast = 1.28f,
                saturation = 1.14f,
                temperature = 0.22f,
                tint = -0.04f,
                fade = 0.06f,
                shadowTintR = 0.03f,
                shadowTintG = 0.05f,
                shadowTintB = 0.01f
            ),
            grain = GrainConfig(enabled = true, intensity = 0.22f, scale = 1.4f),
            lens = LensConfig(vignetteIntensity = 0.72f, vignetteRadius = 0.65f, chromaticAberration = 0.010f, barrelDistortion = -0.08f),
            lightLeak = LightLeakConfig(enabled = true, intensity = 0.35f, warmColorBias = 0.95f),
            dateStamp = DateStampConfig(enabled = true, style = DateStampStyle.SEVEN_SEGMENT_ORANGE, customYearOffset = -27)
        ),
        CameraPreset(
            id = "disp_flash",
            iconResName = "cam_cpm35",
            name = "Flash Disposable",
            subtitle = "Built-in Strobe Snap",
            category = CameraCategory.DISPOSABLE,
            year = "1999",
            description = "Simulates direct close-range camera strobe with harsh falloff and vibrant foreground pop.",
            colorGrading = ColorGradingConfig(
                contrast = 1.34f,
                saturation = 1.20f,
                temperature = 0.16f,
                gamma = 0.90f,
                fade = 0.03f
            ),
            grain = GrainConfig(enabled = true, intensity = 0.24f, scale = 1.3f),
            lens = LensConfig(vignetteIntensity = 0.80f, vignetteRadius = 0.58f, chromaticAberration = 0.009f),
            lightLeak = LightLeakConfig(enabled = true, intensity = 0.40f, warmColorBias = 0.90f),
            dateStamp = DateStampConfig(enabled = true, style = DateStampStyle.SEVEN_SEGMENT_ORANGE, customYearOffset = -25)
        ),
        CameraPreset(
            id = "disp_warm",
            iconResName = "cam_135sr",
            name = "Warm Disposable",
            subtitle = "Summer Vacation 400",
            category = CameraCategory.DISPOSABLE,
            year = "1995",
            description = "Deep golden and orange hues reflecting sunset beach memories shot on affordable single-use plastic cams.",
            colorGrading = ColorGradingConfig(
                contrast = 1.22f,
                saturation = 1.24f,
                temperature = 0.48f,
                tint = 0.12f,
                fade = 0.07f
            ),
            grain = GrainConfig(enabled = true, intensity = 0.25f, scale = 1.5f),
            lens = LensConfig(vignetteIntensity = 0.68f, vignetteRadius = 0.68f, chromaticAberration = 0.008f),
            lightLeak = LightLeakConfig(enabled = true, intensity = 0.50f, warmColorBias = 1.0f),
            dateStamp = DateStampConfig(enabled = true, style = DateStampStyle.SEVEN_SEGMENT_ORANGE, customYearOffset = -29)
        ),
        CameraPreset(
            id = "disp_night",
            iconResName = "cam_d_half",
            name = "Night Disposable",
            subtitle = "High-ISO Street Snap",
            category = CameraCategory.DISPOSABLE,
            year = "1998",
            description = "Coarse gritty grain with muted contrast and moody green-tinted shadows for low-light city walks.",
            colorGrading = ColorGradingConfig(
                contrast = 1.10f,
                saturation = 0.90f,
                temperature = -0.10f,
                tint = -0.18f,
                fade = 0.12f,
                shadowTintR = 0.01f,
                shadowTintG = 0.07f,
                shadowTintB = 0.03f
            ),
            grain = GrainConfig(enabled = true, intensity = 0.32f, scale = 1.8f, isColorNoise = true),
            lens = LensConfig(vignetteIntensity = 0.75f, vignetteRadius = 0.62f, chromaticAberration = 0.012f),
            dateStamp = DateStampConfig(enabled = true, style = DateStampStyle.SEVEN_SEGMENT_ORANGE, customYearOffset = -26)
        ),

        // ==================== D. INSTANT PHOTOGRAPHY ====================
        CameraPreset(
            id = "inst_classic",
            iconResName = "cam_d_slide",
            name = "Instant Classic",
            subtitle = "Peel-Apart Chemical Film",
            category = CameraCategory.INSTANT,
            year = "1982",
            description = "Iconic white frame with milky black point, muted highlights and soft analog chemistry.",
            colorGrading = ColorGradingConfig(
                contrast = 1.04f,
                saturation = 0.94f,
                temperature = 0.18f,
                tint = -0.06f,
                gamma = 1.08f,
                fade = 0.12f,
                highlightRollOff = 0.76f,
                shadowTintR = 0.04f,
                shadowTintG = 0.03f,
                shadowTintB = 0.06f
            ),
            grain = GrainConfig(enabled = true, intensity = 0.15f, scale = 1.2f),
            lens = LensConfig(vignetteIntensity = 0.40f, vignetteRadius = 0.80f, softness = 0.25f),
            border = BorderConfig(style = BorderStyle.POLAROID_WHITE_CHIN),
            dateStamp = DateStampConfig(enabled = false)
        ),
        CameraPreset(
            id = "inst_soft_polaroid",
            iconResName = "cam_tlr",
            name = "Soft Polaroid",
            subtitle = "Dreamy Cream Tone",
            category = CameraCategory.INSTANT,
            year = "1986",
            description = "Dreamy low-contrast pastel tones with luminous warmth and characteristic vintage paper borders.",
            colorGrading = ColorGradingConfig(
                contrast = 0.92f,
                saturation = 0.88f,
                temperature = 0.24f,
                tint = 0.04f,
                fade = 0.16f,
                highlightRollOff = 0.72f
            ),
            grain = GrainConfig(enabled = true, intensity = 0.12f, scale = 1.0f),
            lens = LensConfig(vignetteIntensity = 0.30f, vignetteRadius = 0.85f, softness = 0.35f),
            border = BorderConfig(style = BorderStyle.SQUARE_INSTANT_WHITE),
            dateStamp = DateStampConfig(enabled = false)
        ),
        CameraPreset(
            id = "inst_vintage",
            iconResName = "cam_polaroid",
            name = "Vintage Instant",
            subtitle = "Aged Emulsion 600",
            category = CameraCategory.INSTANT,
            year = "1979",
            description = "Aged chemical dyes showing turquoise-green shadow drift and warm faded center.",
            colorGrading = ColorGradingConfig(
                contrast = 1.08f,
                saturation = 0.96f,
                temperature = 0.12f,
                tint = -0.16f,
                fade = 0.15f,
                shadowTintR = -0.02f,
                shadowTintG = 0.07f,
                shadowTintB = 0.04f
            ),
            grain = GrainConfig(enabled = true, intensity = 0.18f, scale = 1.3f),
            lens = LensConfig(vignetteIntensity = 0.52f, vignetteRadius = 0.72f),
            border = BorderConfig(style = BorderStyle.POLAROID_WHITE_CHIN),
            dateStamp = DateStampConfig(enabled = false)
        ),

        // ==================== E. CAMCORDER & RETRO VIDEO ====================
        CameraPreset(
            id = "cam_retro_vhs",
            iconResName = "cam_bessa",
            name = "Retro VHS",
            subtitle = "Magnetic Tape Standard",
            category = CameraCategory.CAMCORDER,
            year = "1989",
            description = "Horizontal color phase bleeding, scanline modulation, analog tape grain and green OSD date display.",
            colorGrading = ColorGradingConfig(
                contrast = 1.15f,
                saturation = 0.95f,
                temperature = -0.04f,
                tint = -0.08f,
                fade = 0.08f,
                highlightRollOff = 0.85f,
                shadowTintR = 0.02f,
                shadowTintG = 0.05f,
                shadowTintB = 0.03f
            ),
            grain = GrainConfig(enabled = true, intensity = 0.25f, scale = 1.8f, isColorNoise = true),
            lens = LensConfig(vignetteIntensity = 0.35f, vignetteRadius = 0.80f, chromaticAberration = 0.010f, softness = 0.28f),
            border = BorderConfig(style = BorderStyle.CAMCORDER_FRAME),
            dateStamp = DateStampConfig(enabled = true, style = DateStampStyle.DIGITAL_LCD_GREEN, customYearOffset = -35)
        ),
        CameraPreset(
            id = "cam_analog_video",
            iconResName = "cam_quicksnap",
            name = "Analog Video",
            subtitle = "8mm Home Video Tape",
            category = CameraCategory.CAMCORDER,
            year = "1994",
            description = "Soft rounded home video colors with gentle horizontal chroma smear and warm ambient lighting.",
            colorGrading = ColorGradingConfig(
                contrast = 1.08f,
                saturation = 1.02f,
                temperature = 0.16f,
                tint = 0.04f,
                fade = 0.06f
            ),
            grain = GrainConfig(enabled = true, intensity = 0.22f, scale = 1.6f),
            lens = LensConfig(vignetteIntensity = 0.38f, vignetteRadius = 0.80f, chromaticAberration = 0.008f, softness = 0.2f),
            border = BorderConfig(style = BorderStyle.ROUNDED_8MM),
            dateStamp = DateStampConfig(enabled = true, style = DateStampStyle.MONOSPACE_DOT_MATRIX, customYearOffset = -30)
        ),
        CameraPreset(
            id = "cam_90s_camcorder",
            iconResName = "cam_lomo",
            name = "90s Camcorder",
            subtitle = "Handheld CCD Video",
            category = CameraCategory.CAMCORDER,
            year = "1997",
            description = "Crisp vintage television broadcast look with sharp LCD timecodes and high-energy 90s aesthetic.",
            colorGrading = ColorGradingConfig(
                contrast = 1.20f,
                saturation = 1.10f,
                temperature = -0.10f,
                tint = 0.06f,
                fade = 0.04f
            ),
            grain = GrainConfig(enabled = true, intensity = 0.18f, scale = 1.3f, isColorNoise = true),
            lens = LensConfig(vignetteIntensity = 0.30f, vignetteRadius = 0.85f, chromaticAberration = 0.006f),
            border = BorderConfig(style = BorderStyle.CAMCORDER_FRAME),
            dateStamp = DateStampConfig(enabled = true, style = DateStampStyle.DIGITAL_LCD_GREEN, customYearOffset = -27)
        )
    )

    fun getPresetById(id: String): CameraPreset {
        return allPresets.find { it.id == id } ?: allPresets.first()
    }
}

