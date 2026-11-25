# CAMELEO — Authentic Digicam & Disposable Camera Skin Colour Science Report

## 1. Executive Summary

### 1.1 Objective & Guiding Principles
The objective of this calibration is to establish authentic, camera-specific photographic skin colour science across CAMELEO's vintage camera profiles, strictly eliminating universal golden, yellow, peach, or beauty-processed skin rendering.

In computational photography, authentic camera response emerges from:
1. **Photochemical Film Response vs. Semiconductor Sensor Response**: A single-use 35mm disposable camera (colour negative film, dye-coupler densities, chemical scanner development) behaves fundamentally differently from a CCD digital compact (silicon photodiode spectral sensitivity, Bayer CFA, digital gain matrices, legacy integer DSP tone curves).
2. **Illuminant-Sensitive Adaptation**: Digicams preserve imperfect auto-white balance (amber tungsten retention, yellow-green artificial light, harsh direct strobe pop), but must never force mustard yellow skin under clean daylight.
3. **Multi-Complexion Fidelity**: Skin must not be homogenized toward a single target peach or golden hue. Every individual's unique melanin density and undertone must be preserved while rendered through the authentic photographic transfer function of the active camera.

---

## 2. Codebase Audit & Root-Cause Findings

A thorough audit of the CAMELEO codebase revealed several critical architectural flaws that previously caused overlapping, overly yellow, or miscategorized skin rendering:

### 2.1 The D FUNS Miscategorization Bug
- **Observed Behavior**: In `PresetCatalog.kt` line 341, `ccd_flash` ("D Funs") was categorized as `CameraCategory.CCD_DIGITAL` with subtitle "CCD Flash Party Camera".
- **Pipeline Impact**: In `ImageProcessor.kt` line 571, `val isCcd = ... preset.category == CameraCategory.CCD_DIGITAL` evaluated to `true`. This mistakenly routed D Funs into the **digital CCD pipeline** (`DigicamColorEngine`), applying electronic sensor noise, digital unsharp mask, and digital partial white balance instead of the photochemical colour negative film pipeline.
- **LUT Distortion**: `generate_all_luts.py` mapped `"lut_dfuns.png": grade_ccd_flash`, which applied an aggressive digital strobe formula (`if luma > 0.32: r += 0.14 * pop; g += 0.08 * pop; saturation = 1.34`), blowing highlights and starving the blue channel.

### 2.2 Blue Channel Starvation in D FUNS Skin Profile
- In `SkinToneProfileCatalog.D_FUNS`, the profile specified:
  ```kotlin
  skinGains = floatArrayOf(1.06f, 0.98f, 0.90f)
  ```
  Multiplying the blue channel by $0.90$ actively subtracted $-10\%$ blue from skin pixels, artificially inducing a yellow-orange cast on top of the film response.

### 2.3 Unapplied Target Hue Bias
- `SkinToneProfile` defined `val targetHueBias: Float`, but an inspection of `SkinToneIntelligenceEngine.kt` revealed that `targetHueBias` was **never referenced or applied** during pixel processing. Profile-specific hue steering was completely inactive.

### 2.4 Shared vs. Isolated Pipeline Routing
- With the fix applied, camera profiles now have clear, isolated processing topologies:
  - **CPM35 & CCD**: Dedicated `DigicamColorEngine` $\to$ `SkinToneIntelligenceEngine` (Layer 1–4) $\to$ `DigicamOpticalEngine` $\to$ `DigicamSensorEngine`.
  - **D FUNS**: Dedicated Photochemical Negative 3D LUT (`lut_dfuns_v1.png`) $\to$ Film Exposure Curve $\to$ Negative Development $\to$ `SkinToneIntelligenceEngine` $\to$ Scanned Film Grain $\to$ Plastic Lens Vignette.
  - **D3D**: 3-Lens Parallax Stereo Alignment $\to$ Cinema Vision3 500T 3D LUT (`lut_d3d.png`) $\to$ Dedicated D3D Skin Profile with Yellow Cast Guard.

---

## 3. Reference Dataset & Quantitative Calibration

Using the Tier 1 reference image (`media_1795629514630_1160f255.png`), six standardized skin swatches across the full Fitzpatrick complexion spectrum were measured in CIELAB ($L^*, a^*, b^*$), OKLCh ($L, C, h^\circ$), $R/B$, and $R/G$ ratios.

### Reference Swatch Measurements

#### Category A: CPM35 / CCD — Digital Skin Response
*"Golden illumination, rich saturation and stronger tonal separation"*

| Swatch | Description | Sample sRGB | CIELAB ($L^*, a^*, b^*$) | OKLCh Hue ($h^\circ$) | Chroma ($C$) | $R/B$ Ratio | $R/G$ Ratio |
|---|---|---|---|---|---|---|---|
| **1** | Fair Melanin (Digital) | `(244, 196, 142)` | (82.2, 10.2, 33.6) | **69.5°** | 0.089 | 1.72 | 1.24 |
| **2** | Light-Medium (Digital) | `(219, 160, 106)` | (70.3, 15.7, 36.6) | **62.9°** | 0.099 | 2.07 | 1.37 |
| **3** | Medium (Digital) | `(190, 135,  84)` | (60.7, 15.4, 35.6) | **62.8°** | 0.095 | 2.26 | 1.41 |
| **4** | Medium-Dark (Digital) | `(159, 103,  64)` | (48.8, 18.4, 31.0) | **54.7°** | 0.090 | 2.48 | 1.54 |
| **5** | Dark Rich (Digital) | `(121,  73,  46)` | (36.0, 17.6, 24.6) | **49.3°** | 0.077 | 2.63 | 1.66 |
| **6** | Deep Melanin (Digital) | `( 79,  48,  34)` | (23.3, 12.4, 14.8) | **44.9°** | 0.051 | 2.32 | 1.65 |

#### Category B: D FUNS — Disposable Film Response
*"Organic red-orange colour relationships and softer tonal transitions"*

| Swatch | Description | Sample sRGB | CIELAB ($L^*, a^*, b^*$) | OKLCh Hue ($h^\circ$) | Chroma ($C$) | $R/B$ Ratio | $R/G$ Ratio |
|---|---|---|---|---|---|---|---|
| **1** | Fair Melanin (Film) | `(241, 199, 171)` | (83.2, 10.9, 19.6) | **55.4°** | 0.061 | 1.41 | 1.21 |
| **2** | Light-Medium (Film) | `(219, 165, 138)` | (72.1, 16.4, 21.7) | **47.7°** | 0.074 | 1.59 | 1.33 |
| **3** | Medium (Film) | `(192, 140, 112)` | (62.6, 16.2, 22.7) | **49.4°** | 0.075 | 1.71 | 1.37 |
| **4** | Medium-Dark (Film) | `(158, 109,  82)` | (50.5, 16.2, 22.9) | **49.6°** | 0.074 | 1.93 | 1.45 |
| **5** | Dark Rich (Film) | `(121,  80,  60)` | (38.1, 14.7, 18.8) | **46.7°** | 0.063 | 2.02 | 1.51 |
| **6** | Deep Melanin (Film) | `( 80,  56,  44)` | (25.9,  8.9, 11.7) | **47.6°** | 0.040 | 1.82 | 1.43 |

### Key Colorimetric Differentiators
1. **Hue Angle Difference**:
   - Digital (CPM35/CCD) skin highlights and midtones exhibit an amber-golden hue angle: $h \in [62.8^\circ, 69.5^\circ]$.
   - Disposable Film (D FUNS) skin exhibits an organic red-orange hue angle: $h \in [46.7^\circ, 55.4^\circ]$ ($\sim 15^\circ$ more red).
2. **Yellow Chroma & Dye Density**:
   - Digital skin has high yellow saturation: $b^* \in [31.0, 36.6]$, with $b^*/a^* \approx 2.33$.
   - Film skin has moderate yellow saturation: $b^* \in [18.8, 22.9]$, with $b^*/a^* \approx 1.32$ (rich red-orange blood perfusion and cyan dye-coupler balance).
3. **Blue Channel Headroom**:
   - Digital direct flash pushes $R/B$ up to $2.07–2.63$.
   - Colour negative film maintains blue headroom: $R/B \in [1.41, 1.93]$, preventing harsh digital yellowing.

---

## 4. Technical Implementation & Camera-Specific Calibrations

### 4.1 Single-Use 35mm Disposable Film Camera (D FUNS)
- **Preset Configuration (`PresetCatalog.kt`)**:
  - `category = CameraCategory.DISPOSABLE`
  - `subtitle = "Single-use 35mm"`, `year = "1995"`
  - Gentle film contrast ($1.08$), moderate saturation ($1.06$), soft highlight rolloff ($0.90$).
- **Photochemical 3D LUT (`generate_all_luts.py`)**:
  - Implemented `grade_dfuns_disposable_film`:
    - Film S-curve: Red toe $0.035$, Green toe $0.030$, Blue toe $0.038$; shoulder $0.88–0.89$.
    - Soft highlight illumination: $+0.05 \cdot hi$ Red, $+0.015 \cdot hi$ Green, $+0.010 \cdot hi$ Blue (blue is preserved).
    - Shadow base density lift: $+0.025 \cdot sh$ Red, $+0.010 \cdot sh$ Green, $+0.005 \cdot sh$ Blue.
  - Regenerated `lut_dfuns.png` and `lut_dfuns_v1.png`.
- **Skin Tone Profile (`SkinToneProfileCatalog.D_FUNS`)**:
  - `targetHueBias = -1.2f`: Steers skin toward organic red-orange ($h \approx 48^\circ–55^\circ$), eliminating golden yellow drift.
  - `skinGains = floatArrayOf(1.02f, 0.99f, 0.98f)`: Preserves blue channel; stops blue starvation.
  - `yellowCastProtectionEnabled = true`, `strength = 0.80f`: Enforces $R/B \le 1.95$.
  - `skinHighlightKnee = 0.90f`: Negative film shoulder compression.

### 4.2 Golden Nostalgic Digital Compact (CPM35)
- **Aesthetic**: Recreates the warm, nostalgic early 2000s compact camera look under ambient indoor lighting and flash (Sony Cyber-shot DSC-W130 / Canon PowerShot A70).
- **Skin Tone Profile (`SkinToneProfileCatalog.CPM35`)**:
  - `targetHueBias = 3.5f`: Radiant golden-amber cast matching reference photographs 1, 2, 3, and 6.
  - `skinGains = floatArrayOf(1.05f, 0.98f, 0.92f)`: Authentic early CCD sensor matrix response.
  - `yellowCastProtectionEnabled = true`, `strength = 0.70f`: Dynamically guards against mustard yellow skin under daylight ($R/B \le 2.35, h < 75^\circ$) while allowing rich amber tungsten to bloom.
  - `skinHighlightKnee = 0.84f`: Authentic digital specular highlight clipping on foreheads and cheeks.

### 4.3 2000s Pocket Digicam (CCD)
- **Aesthetic**: Crisp, direct-flash party look with vibrant primary colors and digital clarity (Canon Digital IXUS 70 / Sony Cyber-shot DSC-W55).
- **Skin Tone Profile (`SkinToneProfileCatalog.CCD`)**:
  - `targetHueBias = 1.0f`: Clean, balanced daylight skin with punchy flash contrast.
  - `skinGains = floatArrayOf(1.02f, 1.00f, 0.96f)`.
  - `yellowCastProtectionEnabled = true`, `strength = 0.75f`: Enforces $R/B \le 1.75$.
  - `skinHighlightKnee = 0.92f`: Preserves naturally bright direct-flash highlights.
  - `textureDampening = 0.10f`: Sharp digital electronic sensor texture.

### 4.4 Motion Picture 3D Stereo Film (D3D)
- **Aesthetic**: Calibrated Kodak Vision3 500T cinema film with 3D parallax wigglegram animation.
- **Skin Tone Profile (`SkinToneProfileCatalog.D3D`)**:
  - `targetHueBias = 0.0f`, `skinGains = floatArrayOf(1.0f, 1.0f, 1.0f)`.
  - `yellowCastProtectionEnabled = true`, `strength = 0.85f`: Enforces $R/B \le 1.75, R/G \ge 1.30$, completely eliminating the mustard cast.

### 4.5 Continuous OKLCh Hue Steering Engine (`SkinToneIntelligenceEngine.kt`)
- Added exact, bit-exact roundtrip `oklchToRgb` to `SkinColourEstimator.kt`.
- In `SkinToneIntelligenceEngine.kt`, implemented continuous hue steering:
  $$\Delta h = (targetHueBias \times conf).coerceIn(-maxShift, maxShift)$$
  This steers skin hues continuously and perceptually uniformly without distorting luminance or saturation.

---

## 5. Verification & Testing Results

### 5.1 Automated Quality Suite
Authored [AuthenticCameraSkinColourScienceTest.kt](file:///c:/Users/User/Downloads/DazzProject/app/src/test/java/com/craftcam/app/color/skin/AuthenticCameraSkinColourScienceTest.kt):
1. `testDFunsProducesOrganicRedOrangeSkinWithoutYellowCast`: Confirms D FUNS renders skin with $h \in [45^\circ, 58^\circ]$, $R/B \le 1.95$, and blue preserved.
2. `testCPM35ProducesGoldenAmberSkinUnderTungsten`: Confirms CPM35 renders rich golden amber skin ($h \in [60^\circ, 72^\circ]$).
3. `testCPM35PreventsMustardYellowUnderDaylight`: Confirms daylight protection keeps CPM35 $R/B \le 2.35$ and $h < 75^\circ$.
4. `testCCDProducesCrispDirectFlashHighlights`: Confirms CCD preserves bright direct-flash highlight punch ($r \ge 238$).
5. `testCamerasProduceDistinctPhotographicSkinSignatures`: Verifies that D FUNS, CPM35, CCD, and D3D produce distinctly separated color signatures on identical input pixels ($\Delta h > 3.0^\circ$, distance $> 0.02$).
6. `testNonSkinEnvironmentalObjectsAreCompletelyProtected`: Verifies that yellow fabric ($RGB: 240, 210, 30$) and teal shadows ($RGB: 20, 45, 55$) receive 0 skin pixels ($conf = 0.0$).

**Test Result**: **123 tests completed, 0 failed (100% pass rate)**.

### 5.2 Build & Physical Deployment
- **Gradle Compilation**: Succeeded in 3s (`app-debug.apk`).
- **Physical Device**: Samsung Galaxy S21 5G (`SM-G991B`, Android 14) connected via wireless ADB.
- **Verification**: App launched cleanly via `com.craftcam.app/.MainActivity`.
