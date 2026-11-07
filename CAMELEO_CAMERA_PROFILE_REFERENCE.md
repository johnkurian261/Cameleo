# CAMELEO Camera Profile Reference Catalog

**Version:** 2.0  
**Target Engine:** CAMELEO Decoupled Rendering Engine (OpenGL ES 3.0 + CameraX)  
**Architecture Contract:**  
$$\mathbf{CAMERA} \neq \mathbf{LENS} \neq \mathbf{OPTICAL\ EFFECT} \neq \mathbf{COLOR\ PROFILE} \neq \mathbf{FILM\ STOCK} \neq \mathbf{CAPTURE\ FRAME} \neq \mathbf{COMPUTATIONAL\ PROCESSING}$$

---

## 1. Profile Architecture Specification

Each camera in CAMELEO is defined by a data-driven `CameraProfile` binding seven independent component profiles:

```kotlin
data class CameraProfile(
    val id: String,
    val name: String,
    val subtitle: String,
    val focalLengths: List<FocalLengthOption>,
    val defaultFocalLengthMm: Float,
    val supportedLenses: List<LensType>,
    val defaultLens: LensType,
    val defaultRatio: AspectRatioMode,
    val filmProfile: FilmProfile,
    val opticalProfile: OpticalProfile,
    val grainProfile: GrainProfile,
    val exposureProfile: ExposureProfile,
    val computationalProfile: ComputationalProfile,
    val captureFrameDefaults: CaptureFrameDefaults
)
```

---

## 2. Complete Camera Catalog

### 1. FXNR — Warm Negative (`analog_fxn_r`)
- **Category:** Analog 35mm Still Camera
- **Focal Lengths:** 35mm (Default), 50mm
- **Supported Lenses:** Normal, Fisheye W, Prism, Starburst, Color Flash
- **Film Emulsion:** `lut_fxn_r.png` (512x512 3D Hald CLUT, Linear Pre-transform space)
- **Tonal Response:** Contrast 1.05, Saturation 1.10, Temp +0.10, Tint +0.02, Gamma 0.98, Highlight Rolloff 0.88, Shadow Tint (R=0.03, G=0.01, B=-0.01)
- **Halide Grain:** `GrainMode.SCANNED`, Asset: `grains/grain_ou.jpg`, Intensity: 0.35, Scale: 1.0, Blend: `OVERLAY`, Shadow: 0.40, Highlight: 0.20
- **Optics & Halation:** Cosine⁴ Vignette: 0.25, CA: 0.003, Halation: Active (Thresh=0.82, Intensity=0.45, Cadmium red backscatter)
- **Shutter Character:** Mechanical focal-plane shutter, instant re-arm

---

### 2. D Classic — Universal Negative (`analog_35mm_classic`)
- **Category:** Analog 35mm Still Camera
- **Focal Lengths:** 37mm (Default), 50mm
- **Supported Lenses:** Normal, Fisheye W, Prism, Starburst
- **Film Emulsion:** `lut_analog_35mm_classic.png` (sRGB Input Space)
- **Tonal Response:** Contrast 1.08, Saturation 1.05, Temp +0.05, Tint 0.0, Gamma 1.0, Highlight Rolloff 0.85
- **Halide Grain:** `GrainMode.SCANNED`, Asset: `grains/grain_dclassic.jpg`, Intensity: 0.40, Scale: 1.1, Blend: `OVERLAY`
- **Optics & Halation:** Vignette: 0.30, CA: 0.002, Halation: Active (Thresh=0.85, Intensity=0.35)
- **Frame Ratio:** 3:2 Classic 35mm Aspect

---

### 3. D Exp — Double Exposure Negative (`analog_vintage_color`)
- **Category:** Analog Creative Still Camera
- **Focal Lengths:** 28mm, 35mm (Default)
- **Supported Lenses:** Normal, Prism, ND Filter
- **Film Emulsion:** `lut_analog_vintage_color.png` (Flattened Pre-transform Space)
- **Tonal Response:** Contrast 1.00, Saturation 0.95, Temp +0.15, Tint -0.04, Fade 0.05 (lifted blacks)
- **Halide Grain:** `GrainMode.SCANNED`, Asset: `grains/grain_dexp.jpg`, Intensity: 0.45, Scale: 1.2, Blend: `OVERLAY`
- **Optics & Halation:** Softness: 0.08, Light Leak: Edge-Leak Active, Halation: Thresh=0.80

---

### 4. CCD R — 2000s Digicam (`ccd_y2k_digital`)
- **Category:** Early Digital CCD Camera
- **Focal Lengths:** 35mm (Default), 50mm
- **Supported Lenses:** Normal, Starburst, ND Filter
- **Film Emulsion:** `lut_ccd_y2k_digital.png` (Crisp digital color matrix)
- **Tonal Response:** Contrast 1.12, Saturation 1.15, Temp -0.05 (cool daylight bias), Clarity 0.15, Highlight Rolloff 0.92
- **Halide Grain:** `GrainMode.SCANNED`, Asset: `grains/grain_classicu.jpg`, Intensity: 0.22 (fine digital sensor noise), Blend: `OVERLAY`
- **Optics & Halation:** Sharpness 1.25, Zero Halation, Specular Starburst Active

---

### 5. Instant V — Peel-Apart Integral Film (`inst_vintage`)
- **Category:** Instant Peel-Apart Film Camera
- **Focal Lengths:** 35mm (Fixed)
- **Supported Lenses:** Normal, Prism, Color Flash
- **Film Emulsion:** `lut_inst_vintage.png` (Photochemical diffusion transfer)
- **Tonal Response:** Contrast 0.95, Saturation 0.90, Temp +0.12, Fade 0.08 (milky dye base), Shadow Tint (R=0.04, G=0.03, B=0.06)
- **Halide Grain:** `GrainMode.SCANNED`, Asset: `grains/grain_instc.jpg`, Intensity: 0.50, Scale: 1.4, Blend: `SCREEN` (dye cloud lift)
- **Frame Ratio:** 1:1 Square Polarized Frame with Vintage Border

---

### 6. Disp Classic — Single-Use Disposable (`disp_classic`)
- **Category:** Disposable Camera
- **Focal Lengths:** 32mm Fixed Meniscus Plastic Lens
- **Supported Lenses:** Normal, Color Flash
- **Film Emulsion:** `lut_disp_classic.png`
- **Tonal Response:** Contrast 1.15, Saturation 1.20, Temp +0.08, Gamma 0.95
- **Halide Grain:** `GrainMode.SCANNED`, Asset: `grains/grain_dexp2.jpg`, Intensity: 0.55, Scale: 1.3, Blend: `OVERLAY`
- **Optics & Halation:** Edge Curvature 0.06, Vignette 0.45, Optical Softness 0.12, Fixed Focus Range 1.2m–$\infty$

---

### 7. Disp Flash — Party High-Speed Film (`disp_flash`)
- **Category:** Disposable Camera
- **Focal Lengths:** 30mm Fixed Lens
- **Supported Lenses:** Normal, Color Flash
- **Film Emulsion:** `lut_disp_flash.png` (Push-processed ISO 800)
- **Tonal Response:** Contrast 1.20, Saturation 1.15, Highlight Rolloff 0.82
- **Halide Grain:** `GrainMode.SCANNED`, Asset: `grains/grain_cpm35.jpg`, Intensity: 0.65, Scale: 1.35, Blend: `SCREEN`
- **Optics & Halation:** Direct xenon flash flare simulation, intense cadmium halation

---

### 8. Disp Warm — Golden Hour Disposable (`disp_warm`)
- **Category:** Disposable Camera
- **Focal Lengths:** 35mm
- **Film Emulsion:** `lut_disp_warm.png`
- **Tonal Response:** Temp +0.30, Tint +0.05, Saturation 1.25, Warm Amber Toe
- **Halide Grain:** `GrainMode.SCANNED`, Asset: `grains540_01.jpg`, Intensity: 0.45

---

### 9. Disp Night — Low-Light Push Film (`disp_night`)
- **Category:** Disposable Camera
- **Focal Lengths:** 35mm
- **Film Emulsion:** `lut_disp_night.png`
- **Tonal Response:** Lifted Shadows +0.15, High-Speed Grain, Deep Halation
- **Halide Grain:** `GrainMode.SCANNED`, Asset: `grains/grain_ou_lowlight.jpg`, Intensity: 0.70

---

### 10. Warm Film — Portra-Style Warm Emulsion (`analog_warm_film`)
- **Category:** Professional Color Negative
- **Focal Lengths:** 35mm, 50mm
- **Film Emulsion:** `lut_analog_warm_film.png`
- **Tonal Response:** Soft Contrast 0.98, Warm Skin Tone Preservation, Creamy Rolloff 0.80
- **Halide Grain:** `GrainMode.SCANNED`, Asset: `grains/grain_ou.jpg`, Intensity: 0.32

---

### 11. Soft Film — Medium Format 6x7 Emulsion (`analog_soft_film`)
- **Category:** Medium Format Color Negative
- **Focal Lengths:** 35mm, 85mm Portrait
- **Film Emulsion:** `lut_analog_soft_film.png`
- **Tonal Response:** Ultra-Smooth Dynamic Range, Rolloff 0.75, Low Microcontrast
- **Halide Grain:** `GrainMode.SCANNED`, Asset: `grains/grain_s67.jpg`, Intensity: 0.25 (ultra-fine 120 film halide)

---

### 12. Cinema 35 — 5219 Vision3 Tungsten Motion Film (`analog_cinema_film`)
- **Category:** Motion Picture 35mm Negative
- **Focal Lengths:** 35mm, 50mm
- **Film Emulsion:** `lut_analog_cinema_film.png`
- **Tonal Response:** Tungsten 3200K Balance, Rich Cyan-Teal Shadows, Deep Amber Halation Fringe
- **Halide Grain:** `GrainMode.SCANNED`, Asset: `grains/grain_d3d1.jpg`, Intensity: 0.38

---

### 13. CCD Flash — Compact Digital Flash (`ccd_flash`)
- **Category:** 2000s Digital Compact
- **Focal Lengths:** 35mm
- **Film Emulsion:** `lut_ccd_flash.png`
- **Halide Grain:** `GrainMode.SCANNED`, Asset: `grains/grain_dfuns.jpg`, Intensity: 0.28

---

### 14. Retro Compact — 90s Street Point-and-Shoot (`ccd_retro_compact`)
- **Category:** 90s Compact Film
- **Focal Lengths:** 28mm Wide, 35mm Street
- **Film Emulsion:** `lut_ccd_retro_compact.png`
- **Halide Grain:** `GrainMode.SCANNED`, Asset: `grains/grain_ccdr.jpg`, Intensity: 0.42

---

### 15. Retro VHS — Magnetic Tape Camcorder (`cam_retro_vhs`)
- **Category:** Analog Video Camcorder
- **Focal Lengths:** 35mm
- **Film Emulsion:** `lut_cam_retro_vhs.png`
- **Texture:** Horizontal Chroma Smear, CRT Line Modulation (480 lines), Tape Noise
- **Grain:** `grains/grain8mm_01.jpg`

---

### 16. Hi8 Analog — Video8/Hi8 Magnetic Camcorder (`cam_analog_video`)
- **Category:** Analog Video Camcorder
- **Focal Lengths:** 35mm
- **Film Emulsion:** `lut_cam_analog_video.png`
- **Texture:** Interlaced Scanlines, Subcarrier Chroma Delay

---

### 17. MiniDV 90s — Digital Video Camcorder (`cam_90s_camcorder`)
- **Category:** Digital Video Camcorder
- **Focal Lengths:** 35mm
- **Film Emulsion:** `lut_cam_90s_camcorder.png`
- **Texture:** Subtle digital tape dropouts, 4:3 Camcorder Framing

---

### 18. CAMELEO Natural — Modern Flagship Computational Camera (`natural_s21`)
- **Category:** Clean Computational Photography
- **Focal Lengths:** 26mm Sensor Native
- **Supported Lenses:** Normal Lens Only (clean, un-distorted)
- **Film Emulsion:** Bypassed completely (zero LUT)
- **Color Pipeline:** Radiometric linear matrix with S21 sensor calibration
- **Grain / Optics / Vintage Effects:** 100% Disabled (No grain, no halation, no dust, no leaks, no borders)
- **Computational Processing:** Multi-frame HDR fusion, subpixel alignment, motion deghosting, conservative tone mapping, face-protected sharpening

---

## 3. Lens & Optical Effects Reference

| Lens Profile | Optical Effect | Parameters | Shading / Hardware Action |
| :--- | :--- | :--- | :--- |
| **NORMAL** | Standard Lens | Flat optical field | Identity transform |
| **WIDE** | Wide-Angle Barrel | $k_1 = 0.18$ | Moderate field expansion |
| **FISHEYE_F** | Circular Hood Fisheye | $k_1 = 0.42, k_2 = 0.15$ | Hemispherical barrel + outer circle hood vignette at $r=0.72$ |
| **FISHEYE_W** | Full-Frame Fisheye | $k_1 = 0.28, k_2 = 0.08$ | Full-frame curvilinear expansion with soft peripheral falloff |
| **PRISM** | Multi-Facet Optical Refraction | $disp = 0.85$ | $\pm 30^\circ$ facet angular splitting with chromatic wavelength dispersion |
| **STARBURST** | Diffraction Cross | $thresh = 0.84, str = 0.65$ | 4-ray diagonal diffraction spikes on specular highlights |
| **ND** | Neutral Density Filter | $2.0$ Stops Reduction, $0.28$ Transmission | Adjusts Camera2 AE compensation steps downwards + shader transmission |
| **COLOR_FLASH** | Colored Gel Flash Filter | Warm Amber, Cool Cyan, Magenta, Mint | Tinted flash illumination overlay during flash firing |

