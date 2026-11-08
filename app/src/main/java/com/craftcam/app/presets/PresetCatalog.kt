package com.craftcam.app.presets

import com.craftcam.app.model.*

object PresetCatalog {

    val allPresets: List<CameraPreset> = listOf(
        // ==================== A. ZERO NATURAL CAMERA ====================
        CameraPreset(
            id = "zero",
            iconResName = "cam_zero",
            name = "Zero",
            subtitle = "Natural Photographic Sensor",
            category = CameraCategory.CCD_DIGITAL,
            year = "2024",
            description = "Flagship natural camera pipeline. Pristine color calibration, asymptotic highlight shoulder, adaptive shadow recovery, edge-aware noise reduction, restrained sharpening, and soft multi-complexion skin protection without film grain or vintage artifacts.",
            colorGrading = ColorGradingConfig(
                contrast = 1.02f,
                saturation = 1.00f,
                temperature = 0.0f,
                tint = 0.0f,
                gamma = 1.0f,
                fade = 0.0f,
                highlightRollOff = 0.92f,
                shadowTintR = 0.0f,
                shadowTintG = 0.0f,
                shadowTintB = 0.0f
            ),
            grain = GrainConfig(
                enabled = false,
                intensity = 0.0f,
                scale = 1.0f,
                assetPath = null,
                blendMode = GrainBlendMode.OVERLAY,
                shadowStrength = 0.0f,
                highlightStrength = 0.0f
            ),
            lens = LensConfig(
                vignetteIntensity = 0.0f,
                vignetteRadius = 1.0f,
                chromaticAberration = 0.0f,
                softness = 0.0f
            ),
            halation = HalationConfig(enabled = false, threshold = 1.0f, intensity = 0.0f, radius = 0f),
            dust = DustConfig(enabled = false),
            lightLeak = LightLeakConfig(enabled = false),
            dateStamp = DateStampConfig(enabled = false),
            border = BorderConfig(style = BorderStyle.NONE)
        ),
        // ==================== B. ANALOG FILM CAMERAS ====================
        CameraPreset(
            id = "analog_35mm_classic",
            iconResName = "cam_d_classic",
            name = "35mm Classic",
            subtitle = "Universal Negative Film",
            category = CameraCategory.ANALOG_FILM,
            year = "1984",
            description = "Natural skin tones, balanced contrast and fine silver halide grain. The iconic standard for everyday 35mm street photography.",
            colorGrading = ColorGradingConfig(
                contrast = 1.14f,
                saturation = 1.10f,
                temperature = 0.12f,
                tint = -0.01f,
                gamma = 1.0f,
                fade = 0.035f,
                highlightRollOff = 0.80f,
                shadowTintR = 0.012f,
                shadowTintG = 0.018f,
                shadowTintB = 0.032f
            ),
            grain = GrainConfig(enabled = true, intensity = 0.22f, scale = 0.95f, assetPath = "grains/grain_dclassic.jpg", blendMode = GrainBlendMode.OVERLAY, shadowStrength = 0.30f, highlightStrength = 0.12f),
            lens = LensConfig(vignetteIntensity = 0.35f, vignetteRadius = 0.80f, chromaticAberration = 0.0025f, softness = 0.08f),
            halation = HalationConfig(enabled = true, threshold = 0.74f, intensity = 0.26f, radius = 14f),
            dust = DustConfig(enabled = true, assetPath = "dust_135.png", opacity = 0.40f),
            lightLeak = LightLeakConfig(enabled = true, intensity = 0.20f),
            flashOverlay = FlashOverlayConfig(enabled = true, assetPath = "flash_01.jpg", opacity = 0.45f),
            dateStamp = DateStampConfig(enabled = true, style = DateStampStyle.SEVEN_SEGMENT_ORANGE, customYearOffset = -28),
            border = BorderConfig(style = BorderStyle.CLASSIC_35MM_RANGEFINDER, assetPath = "dbalck_frame_in.png")
        ),
        CameraPreset(
            id = "analog_fxn_r",
            iconResName = "cam_fxnr",
            name = "FXNR",
            subtitle = "Warm Negative",
            category = CameraCategory.ANALOG_FILM,
            year = "1998",
            description = "Warm color negative film emulation with rich amber tones, golden highlights, lifted brown shadows, creamy whites, fine halide grain, and soft highlight bloom.",
            colorGrading = ColorGradingConfig(
                contrast = 1.06f,
                saturation = 1.04f,
                temperature = 0.18f,
                tint = 0.03f,
                gamma = 1.02f,
                fade = 0.048f,
                highlightRollOff = 0.78f,
                shadowTintR = 0.054f,
                shadowTintG = 0.024f,
                shadowTintB = 0.008f
            ),
            grain = GrainConfig(enabled = true, intensity = 0.22f, scale = 1.0f, assetPath = "grains/grain_ou.jpg", blendMode = GrainBlendMode.OVERLAY, shadowStrength = 0.32f, highlightStrength = 0.10f),
            lens = LensConfig(vignetteIntensity = 0.24f, vignetteRadius = 0.88f, chromaticAberration = 0.002f, softness = 0.14f),
            halation = HalationConfig(enabled = true, threshold = 0.82f, intensity = 0.28f, radius = 16f),
            dust = DustConfig(enabled = true, assetPath = "dust_135.png", opacity = 0.45f),
            lightLeak = LightLeakConfig(enabled = true, intensity = 0.22f),
            dateStamp = DateStampConfig(enabled = true, style = DateStampStyle.SEVEN_SEGMENT_ORANGE, customYearOffset = -21),
            border = BorderConfig(style = BorderStyle.CLASSIC_35MM_RANGEFINDER, assetPath = "dbalck_frame_in.png")
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
                contrast = 1.14f,
                saturation = 1.08f,
                temperature = 0.14f,
                tint = 0.04f,
                gamma = 0.98f,
                fade = 0.04f,
                highlightRollOff = 0.80f,
                shadowTintR = 0.04f,
                shadowTintG = 0.03f,
                shadowTintB = 0.01f
            ),
            grain = GrainConfig(enabled = true, intensity = 0.24f, scale = 1.05f, assetPath = "grains/grain_ou.jpg", blendMode = GrainBlendMode.OVERLAY, shadowStrength = 0.30f, highlightStrength = 0.15f),
            lens = LensConfig(vignetteIntensity = 0.30f, vignetteRadius = 0.85f, chromaticAberration = 0.002f, softness = 0.10f),
            halation = HalationConfig(enabled = true, threshold = 0.86f, intensity = 0.24f, radius = 14f),
            dust = DustConfig(enabled = true, assetPath = "135_dust.png", opacity = 0.45f),
            lightLeak = LightLeakConfig(enabled = true, intensity = 0.25f),
            dateStamp = DateStampConfig(enabled = true, style = DateStampStyle.SEVEN_SEGMENT_ORANGE, customYearOffset = -30),
            border = BorderConfig(style = BorderStyle.FILM_STRIP_135, assetPath = "135ne_frame_cut1.png")
        ),
        CameraPreset(
            id = "analog_135ne",
            iconResName = "cam_135ne",
            name = "135 NE",
            subtitle = "35mm Color Negative",
            category = CameraCategory.ANALOG_FILM,
            year = "1994",
            description = "Iconic 35mm negative film strip with authentic sprocket perforations, balanced contrast, fine halide grain, and rich exposure latitude.",
            colorGrading = ColorGradingConfig(
                contrast = 1.14f,
                saturation = 1.08f,
                temperature = 0.14f,
                tint = 0.04f,
                fade = 0.035f,
                highlightRollOff = 0.78f,
                shadowTintR = 0.04f,
                shadowTintG = 0.03f,
                shadowTintB = 0.01f
            ),
            grain = GrainConfig(enabled = true, intensity = 0.26f, scale = 1.10f, assetPath = "grains/grain_135sr.jpg", blendMode = GrainBlendMode.OVERLAY, shadowStrength = 0.32f, highlightStrength = 0.14f),
            lens = LensConfig(vignetteIntensity = 0.28f, vignetteRadius = 0.85f, chromaticAberration = 0.002f, softness = 0.10f),
            halation = HalationConfig(enabled = true, threshold = 0.76f, intensity = 0.24f, radius = 14f),
            dust = DustConfig(enabled = true, assetPath = "dust_135.png", opacity = 0.45f),
            lightLeak = LightLeakConfig(enabled = true, intensity = 0.22f),
            dateStamp = DateStampConfig(enabled = true, style = DateStampStyle.SEVEN_SEGMENT_ORANGE, customYearOffset = -28),
            border = BorderConfig(style = BorderStyle.FILM_STRIP_135, assetPath = "135ne_frame_cut1.png")
        ),
        CameraPreset(
            id = "analog_fisheye_f",
            iconResName = "cam_gr_f",
            name = "Fisheye Bubble",
            subtitle = "Circular 8mm Fisheye",
            category = CameraCategory.ANALOG_FILM,
            year = "1972",
            description = "Iconic ultra-wide 180° circular fisheye lens with dramatic hemispherical bulging, peripheral compression, and classic circular lens hood framing.",
            colorGrading = ColorGradingConfig(
                contrast = 1.15f,
                saturation = 1.10f,
                temperature = 0.05f,
                tint = 0.0f,
                gamma = 1.0f,
                fade = 0.03f,
                highlightRollOff = 0.82f
            ),
            grain = GrainConfig(enabled = true, intensity = 0.18f, scale = 1.0f, blendMode = GrainBlendMode.OVERLAY),
            lens = LensConfig(vignetteIntensity = 0.40f, vignetteRadius = 0.80f, chromaticAberration = 0.016f, softness = 0.10f),
            halation = HalationConfig(enabled = true, threshold = 0.85f, intensity = 0.20f, radius = 12f),
            dust = DustConfig(enabled = false, assetPath = "dust_135.png", opacity = 0.0f),
            lightLeak = LightLeakConfig(enabled = false),
            dateStamp = DateStampConfig(enabled = true, style = DateStampStyle.SEVEN_SEGMENT_ORANGE, customYearOffset = -30),
            border = BorderConfig(style = BorderStyle.NONE)
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
            grain = GrainConfig(enabled = true, intensity = 0.24f, scale = 0.95f, assetPath = "grains/grain_s67.jpg", blendMode = GrainBlendMode.OVERLAY, shadowStrength = 0.32f, highlightStrength = 0.14f),
            lens = LensConfig(vignetteIntensity = 0.22f, vignetteRadius = 0.90f, chromaticAberration = 0.002f, softness = 0.30f),
            halation = HalationConfig(enabled = true, threshold = 0.76f, intensity = 0.22f, radius = 18f),
            dust = DustConfig(enabled = true, assetPath = "dust_black.png", opacity = 0.50f),
            dateStamp = DateStampConfig(enabled = true, style = DateStampStyle.MINIMAL_WHITE),
            border = BorderConfig(style = BorderStyle.MEDIUM_FORMAT_67, assetPath = "s67_frame_t1.png")
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
                contrast = 1.15f,
                saturation = 1.08f,
                temperature = 0.14f,
                tint = -0.06f,
                fade = 0.04f,
                highlightRollOff = 0.80f,
                shadowTintR = 0.03f,
                shadowTintG = 0.04f,
                shadowTintB = 0.01f
            ),
            grain = GrainConfig(enabled = true, intensity = 0.25f, scale = 1.10f, isColorNoise = false, assetPath = "grains/grain_dexp.jpg", blendMode = GrainBlendMode.OVERLAY, shadowStrength = 0.30f, highlightStrength = 0.16f),
            lens = LensConfig(vignetteIntensity = 0.28f, vignetteRadius = 0.85f, chromaticAberration = 0.003f, softness = 0.10f),
            halation = HalationConfig(enabled = true, threshold = 0.82f, intensity = 0.22f, radius = 14f),
            dust = DustConfig(enabled = true, assetPath = "dust_135.png", opacity = 0.45f),
            lightLeak = LightLeakConfig(enabled = true, intensity = 0.22f),
            dateStamp = DateStampConfig(enabled = true, style = DateStampStyle.SEVEN_SEGMENT_ORANGE, customYearOffset = -35),
            border = BorderConfig(style = BorderStyle.SLIDE_MOUNT, assetPath = "border_slidep1.jpg")
        ),
        CameraPreset(
            id = "analog_cinema_film",
            iconResName = "cam_d3d",
            name = "D 3D",
            subtitle = "Stereoscopic 3D Parallax",
            category = CameraCategory.ANALOG_FILM,
            year = "3D",
            description = "Multi-lens stereoscopic film camera. Generates authentic analog 3D parallax wigglegram animation loops with horizontal depth movement.",
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
            grain = GrainConfig(enabled = true, intensity = 0.28f, scale = 1.05f, assetPath = "grains/grain_d3d1.jpg", blendMode = GrainBlendMode.OVERLAY, shadowStrength = 0.35f, highlightStrength = 0.16f),
            lens = LensConfig(vignetteIntensity = 0.38f, vignetteRadius = 0.80f, chromaticAberration = 0.004f, softness = 0.12f),
            halation = HalationConfig(enabled = true, threshold = 0.80f, intensity = 0.42f, radius = 20f),
            dust = DustConfig(enabled = true, assetPath = "dust_135.png", opacity = 0.55f),
            lightLeak = LightLeakConfig(enabled = true, intensity = 0.30f),
            dateStamp = DateStampConfig(enabled = true, style = DateStampStyle.SEVEN_SEGMENT_ORANGE),
            border = BorderConfig(style = BorderStyle.ROUNDED_16MM_GATE, assetPath = "mask_16mm_01_r.jpg")
        ),

        // ==================== B. CCD DIGITAL CAMERAS ====================
        CameraPreset(
            id = "ccd_r",
            iconResName = "cam_classic",
            name = "CCD R",
            subtitle = "2004 CCD Digital Zoom",
            category = CameraCategory.CCD_DIGITAL,
            year = "2004",
            description = "Olympus Camedia C-70 2000s compact digital camera. Punchy contrast, electric cyan skies, deep blacks, and authentic CCD sensor texture.",
            colorGrading = ColorGradingConfig(
                contrast = 1.25f,
                saturation = 1.22f,
                temperature = -0.02f,
                gamma = 0.96f,
                fade = 0.01f,
                highlightRollOff = 0.92f
            ),
            grain = GrainConfig(enabled = true, intensity = 0.24f, scale = 0.90f, isColorNoise = false, assetPath = "grains/grain_ccdr.jpg", blendMode = GrainBlendMode.OVERLAY, shadowStrength = 0.28f, highlightStrength = 0.16f),
            lens = LensConfig(vignetteIntensity = 0.28f, vignetteRadius = 0.88f, chromaticAberration = 0.002f),
            dust = DustConfig(enabled = true, assetPath = "dust_black.png", opacity = 0.35f),
            flashOverlay = FlashOverlayConfig(enabled = false, assetPath = "flashc_01.jpg", opacity = 0.50f),
            dateStamp = DateStampConfig(enabled = true, style = DateStampStyle.SEVEN_SEGMENT_ORANGE, customYearOffset = -20),
            border = BorderConfig(style = BorderStyle.NONE)
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
                saturation = 1.18f,
                temperature = 0.08f,
                gamma = 0.94f,
                fade = 0.01f,
                highlightRollOff = 0.88f
            ),
            grain = GrainConfig(enabled = true, intensity = 0.22f, scale = 0.90f, isColorNoise = false, assetPath = "grains/grain_dfuns.jpg", blendMode = GrainBlendMode.OVERLAY, shadowStrength = 0.28f, highlightStrength = 0.15f),
            lens = LensConfig(vignetteIntensity = 0.45f, vignetteRadius = 0.72f, chromaticAberration = 0.003f),
            dust = DustConfig(enabled = true, assetPath = "dust_black.png", opacity = 0.35f),
            flashOverlay = FlashOverlayConfig(enabled = true, assetPath = "overlay_flashc.jpg", opacity = 0.55f),
            dateStamp = DateStampConfig(enabled = true, style = DateStampStyle.SEVEN_SEGMENT_ORANGE, customYearOffset = -20)
        ),
        CameraPreset(
            id = "ccd_y2k_digital",
            iconResName = "cam_classic_u",
            name = "Classic U",
            subtitle = "MySpace Era Digicam",
            category = CameraCategory.CCD_DIGITAL,
            year = "2004",
            description = "Saturated magenta-pink highlights and crunchy digital sensor response loved by aesthetic creators.",
            colorGrading = ColorGradingConfig(
                contrast = 1.18f,
                saturation = 1.16f,
                temperature = 0.06f,
                tint = 0.10f,
                fade = 0.02f
            ),
            grain = GrainConfig(enabled = true, intensity = 0.22f, scale = 0.90f, isColorNoise = false, assetPath = "grains/grain_classicu.jpg", blendMode = GrainBlendMode.OVERLAY, shadowStrength = 0.28f, highlightStrength = 0.16f),
            lens = LensConfig(vignetteIntensity = 0.24f, vignetteRadius = 0.88f, chromaticAberration = 0.002f),
            dust = DustConfig(enabled = true, assetPath = "dust_black.png", opacity = 0.30f),
            dateStamp = DateStampConfig(enabled = true, style = DateStampStyle.SEVEN_SEGMENT_ORANGE, customYearOffset = -22)
        ),
        CameraPreset(
            id = "ccd_retro_compact",
            iconResName = "cam_dqs",
            name = "DQS",
            subtitle = "CCD Digital Compact",
            category = CameraCategory.CCD_DIGITAL,
            year = "2006",
            description = "Everyday point-and-shoot digital look with punchy dynamic range, authentic CCD color science, and crisp sharpness.",
            colorGrading = ColorGradingConfig(
                contrast = 1.15f,
                saturation = 1.12f,
                temperature = 0.02f,
                fade = 0.01f,
                highlightRollOff = 0.88f
            ),
            grain = GrainConfig(enabled = true, intensity = 0.18f, scale = 0.85f, isColorNoise = false, assetPath = "grains/grain_dqs.jpg", blendMode = GrainBlendMode.OVERLAY, shadowStrength = 0.25f, highlightStrength = 0.14f),
            lens = LensConfig(vignetteIntensity = 0.20f, vignetteRadius = 0.90f, chromaticAberration = 0.002f),
            dust = DustConfig(enabled = true, assetPath = "dust_black.png", opacity = 0.30f),
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
            description = "High contrast, natural lens falloff, plastic optic character and warm party tones.",
            colorGrading = ColorGradingConfig(
                contrast = 1.18f,
                saturation = 1.12f,
                temperature = 0.16f,
                tint = -0.02f,
                fade = 0.04f,
                highlightRollOff = 0.82f,
                shadowTintR = -0.01f,
                shadowTintG = 0.03f,
                shadowTintB = 0.04f
            ),
            grain = GrainConfig(enabled = true, intensity = 0.26f, scale = 1.10f, assetPath = "grains/grain_dexp2.jpg", blendMode = GrainBlendMode.OVERLAY, shadowStrength = 0.35f, highlightStrength = 0.18f),
            lens = LensConfig(vignetteIntensity = 0.35f, vignetteRadius = 0.82f, chromaticAberration = 0.003f, barrelDistortion = 0.0f),
            dust = DustConfig(enabled = true, assetPath = "dust_135.png", opacity = 0.45f),
            lightLeak = LightLeakConfig(enabled = true, intensity = 0.25f, warmColorBias = 0.90f),
            dateStamp = DateStampConfig(enabled = true, style = DateStampStyle.SEVEN_SEGMENT_ORANGE, customYearOffset = -27),
            border = BorderConfig(style = BorderStyle.GOLF_FRAME, assetPath = "golf_frame.jpg")
        ),
        CameraPreset(
            id = "disp_flash",
            iconResName = "cam_cpm35",
            name = "Flash Disposable",
            subtitle = "Built-in Strobe Snap",
            category = CameraCategory.DISPOSABLE,
            year = "1999",
            description = "Simulates direct close-range camera strobe with vibrant foreground pop and natural falloff.",
            colorGrading = ColorGradingConfig(
                contrast = 1.22f,
                saturation = 1.14f,
                temperature = 0.10f,
                gamma = 0.96f,
                fade = 0.02f
            ),
            grain = GrainConfig(enabled = true, intensity = 0.26f, scale = 1.10f, assetPath = "grains/grain_cpm35.jpg", blendMode = GrainBlendMode.OVERLAY, shadowStrength = 0.32f, highlightStrength = 0.16f),
            lens = LensConfig(vignetteIntensity = 0.38f, vignetteRadius = 0.80f, chromaticAberration = 0.003f),
            dust = DustConfig(enabled = true, assetPath = "dust_135.png", opacity = 0.45f),
            flashOverlay = FlashOverlayConfig(enabled = true, assetPath = "flash_01.jpg", opacity = 0.50f),
            lightLeak = LightLeakConfig(enabled = true, intensity = 0.25f, warmColorBias = 0.85f),
            dateStamp = DateStampConfig(enabled = true, style = DateStampStyle.SEVEN_SEGMENT_ORANGE, customYearOffset = -25),
            border = BorderConfig(style = BorderStyle.GOLF_FRAME, assetPath = "golf_frame.jpg")
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
                fade = 0.07f,
                highlightRollOff = 0.76f,
                shadowTintR = 0.06f,
                shadowTintG = 0.03f,
                shadowTintB = -0.02f
            ),
            grain = GrainConfig(enabled = true, intensity = 0.36f, scale = 1.30f, assetPath = "grains/grain540_01.jpg", blendMode = GrainBlendMode.OVERLAY, shadowStrength = 0.40f, highlightStrength = 0.20f),
            lens = LensConfig(vignetteIntensity = 0.68f, vignetteRadius = 0.68f, chromaticAberration = 0.008f),
            dust = DustConfig(enabled = true, assetPath = "dust_135.png", opacity = 0.65f),
            lightLeak = LightLeakConfig(enabled = true, intensity = 0.50f, warmColorBias = 1.0f),
            dateStamp = DateStampConfig(enabled = true, style = DateStampStyle.SEVEN_SEGMENT_ORANGE, customYearOffset = -29),
            border = BorderConfig(style = BorderStyle.FILM_STRIP_135, assetPath = "135ne_frame_cut2.png")
        ),
        CameraPreset(
            id = "disp_night",
            iconResName = "cam_d_half",
            name = "Night Disposable",
            subtitle = "High-ISO Street Snap",
            category = CameraCategory.DISPOSABLE,
            year = "1998",
            description = "Authentic grainy negative film with balanced low-light contrast and moody dark tones.",
            colorGrading = ColorGradingConfig(
                contrast = 1.14f,
                saturation = 0.94f,
                temperature = -0.06f,
                tint = -0.06f,
                fade = 0.06f,
                shadowTintR = 0.01f,
                shadowTintG = 0.04f,
                shadowTintB = 0.02f
            ),
            grain = GrainConfig(enabled = true, intensity = 0.28f, scale = 1.10f, isColorNoise = false, assetPath = "grains/grain_ou_lowlight.jpg", blendMode = GrainBlendMode.OVERLAY, shadowStrength = 0.35f, highlightStrength = 0.18f),
            lens = LensConfig(vignetteIntensity = 0.38f, vignetteRadius = 0.82f, chromaticAberration = 0.003f),
            dust = DustConfig(enabled = true, assetPath = "dust_black.png", opacity = 0.45f),
            lightLeak = LightLeakConfig(enabled = true, intensity = 0.22f),
            dateStamp = DateStampConfig(enabled = true, style = DateStampStyle.SEVEN_SEGMENT_ORANGE, customYearOffset = -26),
            border = BorderConfig(style = BorderStyle.HALF_FRAME, assetPath = "half_border.png")
        ),

        // ==================== D. INSTANT PHOTOGRAPHY ====================
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
            grain = GrainConfig(enabled = true, intensity = 0.30f, scale = 1.15f, assetPath = "grains/grain_instc.jpg", blendMode = GrainBlendMode.OVERLAY, shadowStrength = 0.35f, highlightStrength = 0.18f),
            lens = LensConfig(vignetteIntensity = 0.52f, vignetteRadius = 0.72f),
            dust = DustConfig(enabled = true, assetPath = "dust_instc.png", opacity = 0.70f),
            lightLeak = LightLeakConfig(enabled = true, intensity = 0.40f),
            border = BorderConfig(style = BorderStyle.POLAROID_WHITE_CHIN, assetPath = "vinst_border1.png"),
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
                contrast = 1.12f,
                saturation = 0.95f,
                temperature = -0.04f,
                tint = -0.04f,
                fade = 0.06f,
                highlightRollOff = 0.85f,
                shadowTintR = 0.01f,
                shadowTintG = 0.03f,
                shadowTintB = 0.02f
            ),
            grain = GrainConfig(enabled = true, intensity = 0.28f, scale = 1.40f, isColorNoise = true, assetPath = "grains/grain8mm_01.jpg", blendMode = GrainBlendMode.OVERLAY, shadowStrength = 0.35f, highlightStrength = 0.18f),
            lens = LensConfig(vignetteIntensity = 0.28f, vignetteRadius = 0.85f, chromaticAberration = 0.005f, softness = 0.20f),
            dust = DustConfig(enabled = true, assetPath = "vhs_grain_dust01.jpg", opacity = 0.45f),
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
                temperature = 0.14f,
                tint = 0.02f,
                fade = 0.05f,
                highlightRollOff = 0.82f
            ),
            grain = GrainConfig(enabled = true, intensity = 0.24f, scale = 1.30f, assetPath = "grains/grain_16mm.jpg", blendMode = GrainBlendMode.OVERLAY, shadowStrength = 0.32f, highlightStrength = 0.16f),
            lens = LensConfig(vignetteIntensity = 0.26f, vignetteRadius = 0.85f, chromaticAberration = 0.004f, softness = 0.15f),
            dust = DustConfig(enabled = true, assetPath = "8mm_dust.png", opacity = 0.45f),
            border = BorderConfig(style = BorderStyle.ROUNDED_16MM_GATE, assetPath = "mask_16mm_02_r.jpg"),
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
                contrast = 1.15f,
                saturation = 1.08f,
                temperature = -0.06f,
                tint = 0.04f,
                fade = 0.03f
            ),
            grain = GrainConfig(enabled = true, intensity = 0.22f, scale = 1.20f, isColorNoise = true, assetPath = "grains/grain_dcr_01.jpg", blendMode = GrainBlendMode.OVERLAY, shadowStrength = 0.30f, highlightStrength = 0.15f),
            lens = LensConfig(vignetteIntensity = 0.24f, vignetteRadius = 0.88f, chromaticAberration = 0.003f),
            dust = DustConfig(enabled = true, assetPath = "vhs_grain_dust05.jpg", opacity = 0.40f),
            border = BorderConfig(style = BorderStyle.CAMCORDER_FRAME),
            dateStamp = DateStampConfig(enabled = true, style = DateStampStyle.DIGITAL_LCD_GREEN, customYearOffset = -27)
        )
    )

    fun getPresetById(id: String): CameraPreset {
        if (id == "analog_35mm_classic" || id == "d_classic" || id == "dclassic" || id == "d classic" || id == "classic_35mm") {
            return allPresets.find { it.id == "analog_35mm_classic" } ?: allPresets.first()
        }
        if (id == "ccd_r" || id == "ccdr" || id == "ccd r" || id == "ccd-r" || id == "ccd_classic") {
            return allPresets.find { it.id == "ccd_r" || it.id == "ccdr" } ?: allPresets.first()
        }
        if (id == "ccd_flash" || id == "ccd flash" || id == "d_funs" || id == "dfuns") {
            return allPresets.find { it.id == "ccd_flash" } ?: allPresets.first()
        }
        if (id == "dqs") {
            return allPresets.find { it.id == "ccd_retro_compact" || it.id == "dqs" } ?: allPresets.first()
        }
        if (id == "classic_u" || id == "classicu" || id == "classic u") {
            return allPresets.find { it.id == "ccd_y2k_digital" || it.id == "classic_u" } ?: allPresets.first()
        }
        return allPresets.find { it.id == id } ?: allPresets.first()
    }
}
