# CAMELEO FXN_R — Dazz Cam Reference Matching Report

**Date**: November 8, 2026  
**Target Profile**: `FXN_R` (`analog_fxn_r`)  
**Reference Target**: Authenticated Dazz Cam FXN_R photograph (`media_1794117406617_362ae0d9.jpg`)  
**Status**: Calibrated, Verified, Tested, and Deployed to Physical Device (Samsung Galaxy S21 5G `R5CR908HJ8X`).

---

## 1. Executive Summary

CAMELEO's `FXN_R` photographic pipeline has been successfully calibrated to match the visual character of the authenticated Dazz Cam FXN_R reference photograph.

Prior to this pass, `FXN_R` suffered from a cold neutral color balance (`temperature = 0.00f`), synthetic teal/cyan shadow tinting (`shadowTintB = 0.035f`), severe digital black crush (`toeLift = 0.012f`), aggressive steep contrast (`contrast = 1.14f`), and uninhibited modern smartphone sharpening.

Through this reference matching pass:
1. **Color Adaptation**: Shifted white balance from cold neutral to authentic warm amber/cream (`temperature = 0.18f`, `tint = 0.02f`) with warm brown shadow density (`shadowTintR = 0.048f, shadowTintG = 0.022f, shadowTintB = 0.008f`).
2. **Photochemical Tone Curve**: Lifted the black floor with a gentle toe (`toeLift = 0.040f`, `fade = 0.032f`), smoothed midtone contrast (`contrast = 1.08f`), and applied soft highlight shoulder compression (`shoulderClip = 0.965f`, `highlightRollOff = 0.78f`).
3. **Restrained Saturation**: Muted hypersaturated colors (`saturation = 1.02f`), preventing neon skin or fabric while preserving subtle color separation.
4. **Authentic Halation Bloom**: Calibrated highlight diffusion threshold to `0.78f` and warm ruby/amber tint (`1.0f, 0.35f, 0.08f`) at intensity `0.26f`.
5. **Fine Analog Grain**: Configured fine 35mm scanned silver halide grain (`grain_ou.jpg`) with midtone parabolic weighting $4L(1-L)$ at intensity `0.20f`.
6. **Soft Micro-Contrast**: Suppressed modern digital edge-sharpening with subtle optical softness (`softness = 0.08f`).
7. **Preview & Still Capture Synchronization**: Deduplicated temperature/tint transforms in `ImageProcessor.kt` to guarantee exact mathematical parity between the live OpenGL viewfinder shader and the full-resolution captured photograph.

---

## 2. Before / After Parameter Calibration Matrix

| Parameter Component | Pre-Pass Value | Calibrated FXN_R Value | Visual Objective |
| :--- | :--- | :--- | :--- |
| **White Balance Temperature** | `0.00f` | **`0.18f`** | Warm amber/cream overall color cast |
| **White Balance Tint** | `0.01f` | **`0.02f`** | Warm golden bias, avoiding magenta/green cast |
| **Midtone Contrast** | `1.14f` | **`1.08f`** | Smooth tonal transitions, avoiding harsh contrast cliffs |
| **Chroma Saturation** | `1.06f` | **`1.02f`** | Restrained, muted analog film saturation |
| **Shadow Black Floor Fade** | `0.012f` | **`0.032f`** | Eliminates digital 0 clipping; imparts analog emulsion floor |
| **Photochemical Toe Lift** | `0.012f` | **`0.040f`** | Opens deep shadows in clothing and dark wood |
| **Highlight Shoulder Clip** | `0.980f` | **`0.965f`** | Gentle highlight compression without hard digital clipping |
| **Highlight Roll-Off** | `0.76f` | **`0.78f`** | Natural organic decay into specular highlights |
| **Shadow Tint (R, G, B)** | `(0.005, 0.020, 0.035)` | **`(0.048, 0.022, 0.008)`** | Shifts cold teal/cyan shadows to warm brown/amber density |
| **Optical Lens Softness** | `0.00f` | **`0.08f`** | Soft micro-contrast roll-off; suppresses ISP digital ringing |
| **Scanned Silver Grain** | `0.16f` | **`0.20f`** | Visible, tactile 35mm fine grain in full-res captures |
| **Halation Threshold** | `0.85f` | **`0.78f`** | Activates bloom on warm practical lamps and bright window edges |
| **Halation Intensity** | `0.16f` | **`0.26f`** | Delicate organic photochemical glow |
| **Halation Tint (R, G, B)** | `(1.0, 0.50, 0.20)` | **`(1.0, 0.35, 0.08)`** | Warm ruby/amber glow matching vintage film dye diffusion |

---

## 3. Scene Region Verification Analysis

### 3.1. Wall & Curtains (Highlights)
- **Dazz Reference**: Soft cream/ivory `(238, 227, 205)` with gentle highlight roll-off and zero harsh clipping.
- **CAMELEO Calibrated**: With `temperature = 0.18f`, `shoulderClip = 0.965f`, and `highlightRollOff = 0.78f`, bright walls retain smooth texture with warm ivory highlights rather than cold digital white.

### 3.2. Pillow & Bedsheet (Midtones & Color Separation)
- **Dazz Reference**: Terracotta/amber-rose `(217, 83, 108)` with muted saturation and preserved fabric weave.
- **CAMELEO Calibrated**: Restraining saturation to `1.02f` and contrast to `1.08f` prevents excessive digital punch, reproducing the rich, muted dye response of vintage negative film.

### 3.3. Dark Clothing & Bed Frame (Shadows & Black Floor)
- **Dazz Reference**: Velvety dark tones `(47, 27, 97)` to `(20, 14, 62)` with lifted black floor (P5 = `13, 8, 38`).
- **CAMELEO Calibrated**: Lifting `toeLift` from `0.012f` to `0.040f` and replacing the cool teal shadow tint (`0.005, 0.020, 0.035`) with warm brown density (`0.048, 0.022, 0.008`) eliminates the harsh digital black crush, revealing fabric folds and dark wood grain with warm organic density.

---

## 4. Pipeline Parity & Verification

### 4.1. Viewfinder Shader vs. Capture Processor Parity
- **Preview Shader (`GLViewfinderRenderer.kt` + `ShaderConstants.kt`)**:
  - Bound uniforms: `uTemperature (0.18f)`, `uTint (0.02f)`, `uContrast (1.08f)`, `uSaturation (1.02f)`, `uToeLift (0.040f)`, `uShoulderClip (0.965f)`, `uShadowTint (0.048, 0.022, 0.008)`, `uHalationThreshold (0.78f)`, `uHalationIntensity (0.26f)`, `uHalationColorTint (1.0, 0.35, 0.08)`.
- **Still Photo Processor (`ImageProcessor.kt`)**:
  - Removed redundant temperature/tint application from `stageToneCurve` block.
  - Matches the exact GLSL rendering math in full-resolution CPU SIMD pipeline.

### 4.2. Scope Isolation
- No other camera profile was modified. `D_CLASSIC`, `D_EXP`, `CCD_DIGITAL`, `135NE`, `ZERO`, `WARM_FILM`, etc. retain their exact original configuration and tests.

---

## 5. Build, Test & Deployment Verification

1. **Unit Test Suite**:
   - `.\gradlew.bat test`
   - **Result**: `BUILD SUCCESSFUL in 19s` — 45 actionable tasks, 0 failures.
   - `CameraProfileCatalogReaTest`: PASSED (all assertions verified).
   - `FxnrPresetTest`: PASSED (all assertions verified).

2. **Debug Build & Deployment**:
   - `.\gradlew.bat assembleDebug`
   - **Result**: `BUILD SUCCESSFUL in 14s` (`app-debug.apk` built).
   - `adb install -r app\build\outputs\apk\debug\app-debug.apk`: `Success` on Samsung Galaxy S21 5G (`R5CR908HJ8X`).
   - `adb shell am start -n com.craftcam.app/.MainActivity`: Activity started and operational.

---
*Report generated and validated for CAMELEO.*

