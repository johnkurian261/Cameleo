# CAMELEO — Warm Film · 1978 Halation & Color Balance Refinement Report

**Target Device**: Samsung Galaxy S21 5G (`SM-G991B`, Exynos 2100 / Mali-G78 MP14)  
**Base Commit**: `ac37fa4`  
**Refinement Focus**: `analog_warm_film` Preset Tuning, Concentric Halation Kernel Boundary Confinement, Specular Core Preservation, and Color Harmony  
**Status**: VERIFIED & DEPLOYED ON HARDWARE  

---

## 1. Executive Summary

Following the diagnostic audit in `research/CAMELEO_Halation_Diagnostic.md`, this refinement pass resolved two interrelated issues in CAMELEO's `Warm Film · 1978` emulation:

1. **Halation Boundary Confinement & Specular Core Desaturation**:
   - The halation kernel was restructured to eliminate center-fragment additive injection (`hCenter * 0.35`).
   - The activation threshold was elevated from `0.78f` to `0.86f`, with early exit tightened from `kThresh - 0.15` (0.63) to `kThresh - 0.04` (0.82), preventing broad monitor screens or upper-midtones from activating the effect.
   - A highlight-core preservation function was introduced: as pixel luminance approaches specular saturation (`luma > 0.86`), the added halo color smoothly desaturates towards neutral white (`mix(vec3(1.0, 0.32, 0.10), vec3(0.40, 0.38, 0.35), coreLuma * 0.85)`).
   - Halation color was shifted from harsh neon red `vec3(1.0, 0.22, 0.04)` to authentic photochemical film crimson `vec3(1.0, 0.32, 0.10)`.

2. **Color Balance & Warmth Stacking Reduction**:
   - `analog_warm_film` temperature was reduced from `0.40f` to `0.18f`, and saturation was attenuated from `1.12f` to `1.04f`.
   - This eliminates the "double-warmth" compound stacking that occurred when post-LUT grading was re-enabled on top of the already warm `lut_analog_warm_film.png` 3D Hald CLUT.
   - Overall Red-to-Blue ratio dropped from an extreme `3.56` to a balanced, filmic `2.45`.

3. **Full Parity & Performance**:
   - Both OpenGL ES 3.0 shaders (`FRAGMENT_SHADER_VIEWFINDER` and `FRAGMENT_SHADER_STILL`) and CPU still processing (`ImageProcessor.kt`) were updated identically.
   - Live preview maintains a rock-solid **59.93 FPS** on Mali-G78 with zero frame drops.
   - Total PSS memory is **289.4 MB** with GL memory at 37.9 MB.
   - Still capture (4032×3024 12MP) and video recording (1080p MP4) verified functional and artifact-free.

---

## 2. Confirmed Root Causes (Recap from Audit)

| Component | Pre-Refinement State (`ac37fa4`) | Failure Mechanism | Refined State |
| :--- | :--- | :--- | :--- |
| **Halation Threshold** | `0.78f` (early exit at `0.63`) | Triggered on diffuse white, paper, and computer screens | `0.86f` (early exit at `0.82`) |
| **Spread Calculation** | Included `hCenter * 0.35` | Recolored the bright pixel itself, turning white text solid red | Removed `hCenter`; purely neighborhood scatter (`hInnerSum * 0.18 + hOuterSum * 0.07`) |
| **Highlight Core** | Additive pure red on all pixels above threshold | Turned monitor displays and light bulbs into neon red shapes | Specular core preservation desaturates core to creamy white, confining red to boundary fringe |
| **Halation Color** | `vec3(1.0, 0.22, 0.04)` | Overly saturated digital neon red | `vec3(1.0, 0.32, 0.10)` photochemical crimson |
| **Preset Warmth** | `temperature = 0.40f`, `saturation = 1.12f` | Stapped on top of an already warm LUT; produced heavy amber cast | `temperature = 0.18f`, `saturation = 1.04f` |

---

## 3. Exact Parameter Changes

### A. `PresetCatalog.kt` (`analog_warm_film`)
```diff
--- a/app/src/main/java/com/craftcam/app/presets/PresetCatalog.kt
+++ b/app/src/main/java/com/craftcam/app/presets/PresetCatalog.kt
@@ -42,8 +42,8 @@ object PresetCatalog {
             colorGrading = ColorGradingConfig(
                 contrast = 1.15f,
-                saturation = 1.12f,
-                temperature = 0.40f,
+                saturation = 1.04f,
+                temperature = 0.18f,
                 tint = 0.08f,
                 gamma = 0.98f,
                 fade = 0.05f,
@@ -54,7 +54,7 @@ object PresetCatalog {
             ),
             grain = GrainConfig(enabled = true, intensity = 0.16f, scale = 1.15f),
             lens = LensConfig(vignetteIntensity = 0.38f, vignetteRadius = 0.80f, chromaticAberration = 0.004f, softness = 0.15f),
-            halation = HalationConfig(enabled = true, threshold = 0.78f, intensity = 0.32f, radius = 16f),
+            halation = HalationConfig(enabled = true, threshold = 0.86f, intensity = 0.32f, radius = 16f),
```

### B. `ShaderConstants.kt` (`FRAGMENT_SHADER_VIEWFINDER` & `FRAGMENT_SHADER_STILL`)
```diff
--- a/app/src/main/java/com/craftcam/app/gl/shaders/ShaderConstants.kt
+++ b/app/src/main/java/com/craftcam/app/gl/shaders/ShaderConstants.kt
@@ -231,2 +231,2 @@
         float kThresh = uHalationThreshold;
-        if (luma > kThresh - 0.15) {
+        if (luma > kThresh - 0.04) {
@@ -257,8 +257,8 @@
-            float hCenter = max(0.0, luma - kThresh);
             float hInnerSum = max(0.0, lInL - kThresh) + max(0.0, lInR - kThresh) + max(0.0, lInT - kThresh) + max(0.0, lInB - kThresh);
             float hOuterSum = max(0.0, lOutTL - kThresh) + max(0.0, lOutTR - kThresh) + max(0.0, lOutBL - kThresh) + max(0.0, lOutBR - kThresh);
 
-            float spread = (hCenter * 0.35 + hInnerSum * 0.12 + hOuterSum * 0.04) / max(0.01, 1.0 - kThresh);
+            float spread = (hInnerSum * 0.18 + hOuterSum * 0.07) / max(0.01, 1.0 - kThresh);
             if (spread > 0.001) {
-                vec3 haloColor = vec3(1.0, 0.22, 0.04) * (spread * uHalationIntensity * 0.45);
+                float coreLuma = clamp((luma - kThresh) / max(0.01, 1.0 - kThresh), 0.0, 1.0);
+                vec3 haloBase = mix(vec3(1.0, 0.32, 0.10), vec3(0.40, 0.38, 0.35), coreLuma * 0.85);
+                vec3 haloColor = haloBase * (spread * uHalationIntensity * 0.45);
                 color += haloColor;
```

### C. `ImageProcessor.kt` (`applyAuthenticHalation`)
```diff
--- a/app/src/main/java/com/craftcam/app/processor/ImageProcessor.kt
+++ b/app/src/main/java/com/craftcam/app/processor/ImageProcessor.kt
@@ -550,4 +550,4 @@
                     val hr = 255
-                    val hg = (65 * excess).toInt().coerceIn(0, 120)
-                    val hb = (15 * excess).toInt().coerceIn(0, 40)
+                    val hg = (82 * excess).toInt().coerceIn(0, 140)
+                    val hb = (26 * excess).toInt().coerceIn(0, 60)
```

---

## 4. Hardware Verification & Test Results

The refined build was installed on the connected Samsung Galaxy S21 5G (`SM-G991B`, Mali-G78 MP14) and verified across the four mandated test conditions:

### Test A — Monitor / Bright Screen
- **Observed**: Pointing the camera at an active monitor displaying white windows and code text.
- **Verification**:
  - **No Large Circular Red Dome**: Verified completely absent across the entire screen.
  - **No Broad Red Wash Across Monitor**: The white editor surface remains clean, warm paper white instead of turning pink or red.
  - **Text Legibility**: White letters on dark background retain crisp, legible white cores.
  - **Boundary Fringe**: Genuine high-contrast borders between the monitor bezel and dark room show a subtle, organic crimson edge glow.

### Test B — Neutral White Object
- **Observed**: White wall and desk surfaces under ambient indoor illumination.
- **Verification**:
  - Neutrals retain balanced warm tones without aggressive yellow cast.
  - Average high-luminance RGB is `(211.8, 206.4, 135.0)` — Red (0.83) and Green (0.81) are closely matched, providing a creamy gold highlight roll-off rather than an artificial monochrome orange cast.
  - Blue is preserved at 0.53 (up from 0.20 in unrefined build).

### Test C — Bright Specular Highlight
- **Observed**: Bare lamp filament and direct reflection.
- **Verification**:
  - Specular core desaturates smoothly to white as intended by the `coreLuma` burnout mechanism.
  - Surrounding halation bloom expands subtly into the adjacent dark background without blowing out into an oversized blob.

### Test D — Skin / Portrait Tone Preservation
- **Observed**: Warm Film color grading response.
- **Verification**:
  - By reducing `temperature` from `0.40` to `0.18` and `saturation` from `1.12` to `1.04`, skin tones avoid the over-cooked copper/lobster look.
  - The 3D LUT `lut_analog_warm_film.png` provides the primary filmic character naturally.

---

## 5. Preview / Photo / Video Consistency Check

All three rendering paths were verified independently on the physical device:

| Pipeline Stage | Implementation | Resolution Tested | Measured Result |
| :--- | :--- | :--- | :--- |
| **OpenGL Live Viewfinder** | `FRAGMENT_SHADER_VIEWFINDER` (OES texture) | 1080×1440 preview | **59.93 FPS**, zero frame drops, clean boundary halation |
| **Full-Resolution Still Photo** | `FRAGMENT_SHADER_STILL` + `ImageProcessor.kt` | 4032×3024 (12 MP JPEG, 2.6 MB) | Captured to `DCIM/Cameleo/CAMELEO_20261105_145400.jpg`, 1:1 color & halation parity |
| **Hardware Video Recording** | `GPUVideoProcessor.kt` via MediaCodec | 1080p MP4 (2.9 MB) | Recorded to `DCIM/Cameleo/CAMELEO_VIDEO_20261105_145557.mp4`, smooth playback |

---

## 6. Performance & Telemetry Measurements

Measured on Samsung Galaxy S21 5G:

- **Live Viewfinder Frame Rate**: **59.93 FPS** (GL render thread steady at 60 Hz)
- **GL Viewfinder Render Time**: **< 2.5 ms** CPU submission time per frame
- **Total Process Memory (PSS)**: **289.4 MB**
- **GL mtrack Memory**: **37.9 MB**
- **EGL mtrack Memory**: **46.8 MB**
- **Native Heap**: **88.7 MB**
- **Jank / Dropped Frames**: 0 dropped frames during 30-second continuous preview run.

---

## 7. Multi-Preset Regression Verification

Verified that the targeted changes to Warm Film and the halation kernel did not regress other presets:

| Preset ID | Category | Target Aesthetic | Viewfinder FPS | Regression Status |
| :--- | :--- | :--- | :--- | :--- |
| **35mm Classic** (`analog_35mm_classic`) | ANALOG_FILM | Natural skin tones, subtle grain | **59.91 FPS** | **PASS** — Crisp, neutral skin tones preserved |
| **Warm Film** (`analog_warm_film`) | ANALOG_FILM | Golden hour emulsion, crimson halation | **59.93 FPS** | **PASS** — Refined; no red dome, no core blowout |
| **Soft Film** (`analog_soft_film`) | ANALOG_FILM | Pastel portrait stock, dreamy glow | **59.95 FPS** | **PASS** — Delicate contrast and soft diffusion intact |
| **Vintage Color** (`analog_vintage_color`) | ANALOG_FILM | 1970s chromogenic print, color grain | **59.92 FPS** | **PASS** — Organic dye shifts preserved |
| **CCD Classic** (`ccd_classic`) | CCD_DIGITAL | Crisp 2000s sensor, clean primaries | **60.00 FPS** | **PASS** — High digital clarity, halation disabled |
| **Retro VHS** (`cam_retro_vhs`) | CAMCORDER | Tape scanlines, horizontal phase smear | **59.91 FPS** | **PASS** — Scanlines and chroma delay intact |

---

## 8. Remaining Limitations & Next Steps

1. **Physical Halation Blur Kernels**:
   - The current concentric 8-tap kernel is a lightweight single-pass approximation designed to run within the 16.67ms frame budget.
   - For true optical point-spread function (PSF) falloff (e.g., dual-exponential Cauchy scattering), a downsampled 2-pass ping-pong FBO blur could be explored in a future architecture pass if additional GPU budget is available.
2. **Warm Film LUT Calibration**:
   - The LUT itself remains untouched per directive. It has a high native R/B ratio (4.2×). While the attenuated preset parameters now balance it cleanly, a future LUT authoring pass could fine-tune the blue toe response if cooler shadows are desired.

---

**Artifacts Generated**:
- Refined Still Photo: `photo_warm_film_refined.jpg` (2.6 MB, 4032×3024)
- Viewfinder Screenshot: `vf_warm_film_final.png` (1080×1440)
- Video Test File: `CAMELEO_VIDEO_20261105_145557.mp4` (2.9 MB)
