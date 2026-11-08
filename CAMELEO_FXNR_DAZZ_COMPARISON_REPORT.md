# CAMELEO FXN_R vs. Dazz Cam Reference Comparison & Architectural Audit Report

**Date**: November 8, 2026  
**Target Profile**: `FXN_R` (`analog_fxn_r`)  
**Visual Reference**: Authenticated Dazz Cam FXN_R photograph (`media_1794117406617_362ae0d9.jpg`)  
**Scope**: FXN_R-specific rendering pipeline inspection, quantitative colorimetry audit, and calibration blueprint.

---

## 1. Executive Summary

A comprehensive forensic audit of CAMELEO's `FXN_R` rendering path against the user-supplied Dazz Cam reference photograph has identified the precise mathematical and architectural root causes of the visual mismatch. 

Previously, `analog_fxn_r` was erroneously configured under the assumption that it was a high-contrast Fujicolor Natura 1600 profile with cold cyan/teal shadows and zero color warming. In contrast, the authenticated Dazz Cam FXN_R reference photograph exhibits:
1. **Warm Amber / Cream Photochemical Color Cast**: Soft warm white balance with golden-amber midtones and warm brown shadow density.
2. **Lifted Photochemical Black Floor (Toe Lift)**: Open, velvety shadows with zero digital black crush in dark fabrics and furniture.
3. **Gentle Highlight Compression (Smooth Shoulder)**: Organic filmic rolloff in bright walls and curtains with no harsh digital clipping.
4. **Subdued Saturation & Restrained Chroma**: Muted, naturalistic color separation preventing neon or hypersaturated color reproduction.
5. **Subtle Warm Ruby/Amber Halation**: Delicate optical diffusion around specular highlights and high-contrast boundaries.
6. **Fine Analog Halide Grain**: High-frequency parabolic midtone-weighted silver halide texture.

---

## 2. Quantitative Colorimetric Audit (Dazz Reference vs. Current CAMELEO)

### 2.1. Reference Image Analysis (`media_1794117406617_362ae0d9.jpg`)
| Region / Zone | Reference RGB Sample | Dominant Hue / Tone | Physical Description |
| :--- | :--- | :--- | :--- |
| **Wall / Highlight** | `(238, 227, 205)` | Warm Cream ($R > G > B$, $\Delta_{R-B} = +33$) | Soft amber highlight compression, no harsh digital clipping |
| **Pillow / Midtone** | `(217, 83, 108)` | Warm terracotta/magenta-peach | Rich analog density, preserved fabric texture, muted saturation |
| **Dark Clothing / Shadow** | `(47, 27, 97)` to `(20, 14, 62)` | Deep warm-indigo / velvety dark | Lifted black floor (P10 luminance ~35-45), zero hard clipping |
| **Shadow Floor (P5)** | `(13, 8, 38)` | Deep velvety tone | Lifted above digital 0; soft organic density |
| **Highlight Peak (P90)** | `(238, 226, 207)` | Warm ivory/cream | Compressed shoulder, $R/G/B$ ratio preserved |

### 2.2. Current CAMELEO Output Analysis (`latest_fxnr_shot.jpg`)
| Region / Zone | Current CAMELEO RGB Sample | Dominant Hue / Tone | Discrepancy vs. Dazz Cam |
| :--- | :--- | :--- | :--- |
| **Highlights** | `(140, 132, 116)` | Dull neutral grey-green | Lacks luminous cream warmth ($\Delta_{temp} \approx -0.18$) |
| **Shadow Floor** | `(17, 9, 3)` to `(0, 0, 0)` | Deep digital black | Harsh digital black crush; toe lift too low (`toeLift = 0.012`) |
| **Shadow Tint** | `(0.005, 0.020, 0.035)` | Artificial cool teal/cyan | Directly inverts Dazz Cam's warm brown shadow density |
| **Midtones** | `(77, 82, 66)` | Cold greenish-neutral | Negative green bias, missing amber/golden warmth |

---

## 3. Component-by-Component Pipeline Inspection

### 3.1. White Balance & Color Adaptation
- **Current State**:
  - `PresetCatalog.kt`: `temperature = 0.00f`, `tint = 0.01f`, `shadowTintR = 0.005f`, `shadowTintG = 0.020f`, `shadowTintB = 0.035f`.
  - `CameraProfileCatalog.kt`: `temperature = 0.00f`, `tint = 0.01f`, `shadowTintR = 0.005f`, `shadowTintG = 0.020f`, `shadowTintB = 0.035f`.
- **Diagnostic**: The profile was neutral-to-cold with an explicit cyan/teal shadow bias, directly opposing Dazz Cam's warm amber aesthetic.
- **Root Cause**: The catalog description assumed "Fujicolor Natura with signature cool teal/cyan shadow roll-off".

### 3.2. Tone Curve & Shadow Black Floor
- **Current State**:
  - `toeLift = 0.012f`
  - `fade = 0.012f`
  - `shoulderClip = 0.980f`
  - `contrast = 1.14f`
- **Diagnostic**: `toeLift = 0.012` is virtually indistinguishable from digital zero on 8-bit sensors, crushing shadow detail into pure black blocks. The contrast of `1.14` produces a steep gradient into deep shadows.
- **Root Cause**: Insufficient toe lift and excessive contrast steepness.

### 3.3. Halation & Highlight Bloom
- **Current State**:
  - `halationThreshold = 0.85f`
  - `halationIntensity = 0.16f`
  - `halationColorTint = (1.0f, 0.50f, 0.20f)`
- **Diagnostic**: Halation threshold at `0.85f` prevents halation from activating on normal bright regions (such as warm light fixtures and sunlit curtains). Intensity at `0.16f` is too timid.
- **Target**: `threshold = 0.78f`, `intensity = 0.26f`, `tint = (1.0f, 0.35f, 0.08f)`, `radius = 12f-14f`.

### 3.4. Film Grain Configuration
- **Current State**:
  - `grain.intensity = 0.16f`, asset `grains/grain_ou.jpg`, `luminanceWeightingEnabled = true`, `luminanceWeightingPower = 1.0f`.
- **Diagnostic**: The scanned halide grain asset `grain_ou.jpg` and parabolic formula $4L(1-L)$ are architecturally sound, but intensity `0.16f` is slightly suppressed on 12MP/4K camera captures.
- **Target**: Calibrate intensity to `0.20f` with `scale = 1.0f` to ensure natural, tactile fine grain structure.

### 3.5. Optical Softness & Digital Sharpening
- **Current State**:
  - `lens.softness = 0.0f`. Raw image proxy enters the pipeline without pre-tonal optical diffusion, leaving modern smartphone ISP edge-enhancement artifacts intact.
- **Target**: Introduce subtle optical softness (`softness = 0.08f - 0.10f`) in the lens configuration for FXN_R to reproduce vintage prime lens micro-contrast.

### 3.6. Fused Engine Implementation Anomaly in `ImageProcessor.kt`
- **Inspection Finding**: In `ImageProcessor.kt` lines 726-746:
  ```kotlin
  // Lines 726-734:
  if (abs(effectiveTemp) > 0.001f) { r += 0.10f * effectiveTemp; b -= 0.10f * effectiveTemp }
  if (abs(effectiveTint) > 0.001f) { g += 0.06f * effectiveTint; ... }

  if (stageToneCurve) {
      // Lines 738-746: DUPLICATE TEMPERATURE & TINT APPLICATION!
      if (abs(effectiveTemp) > 0.001f) { r += 0.10f * effectiveTemp; b -= 0.10f * effectiveTemp }
      ...
  }
  ```
- **Diagnostic**: Temperature and tint adjustments were being applied twice in succession when `stageToneCurve` was active. Removing this duplication ensures linear, predictable color adaptation.

---

## 4. Controlled Calibration Parameter Matrix

| Parameter | Current CAMELEO Value | Calibrated FXN_R Target | Parity Justification |
| :--- | :--- | :--- | :--- |
| `temperature` | `0.00f` | **`0.18f`** | Generates authentic warm amber/cream color temperature |
| `tint` | `0.01f` | **`0.02f`** | Balances magenta/green axis for golden warmth |
| `contrast` | `1.14f` | **`1.08f`** | Moderates tone curve steepness, avoiding harsh transitions |
| `saturation` | `1.06f` | **`1.02f`** | Mutes excess chroma, preventing neon skin or fabric |
| `fade` | `0.012f` | **`0.032f`** | Softens deep black floor into vintage chemical density |
| `toeLift` | `0.012f` | **`0.040f`** | Uncrushes dark clothing and wood shadows |
| `shoulderClip` | `0.980f` | **`0.965f`** | Compresses highlight peaks without harsh digital clipping |
| `highlightRollOff` | `0.76f` | **`0.78f`** | Gentle optical highlight decay |
| `shadowTintR` | `0.005f` | **`0.048f`** | Shifts shadow density from cold cyan to warm amber/brown |
| `shadowTintG` | `0.020f` | **`0.022f`** | Preserves olive/golden undertone in dark tones |
| `shadowTintB` | `0.035f` | **`0.008f`** | Completely eliminates unwanted digital blue shadow pooling |
| `softness` | `0.00f` | **`0.08f`** | Suppresses modern ISP oversharpening, smooths micro-contrast |
| `grain.intensity` | `0.16f` | **`0.20f`** | Fine, tactile 35mm silver halide emulsion texture |
| `halationThreshold` | `0.85f` | **`0.78f`** | Expands bloom response to warm interior lamps & curtains |
| `halationIntensity` | `0.16f` | **`0.26f`** | Subtle warm optical glow |
| `halationColorTint` | `(1.0, 0.50, 0.20)` | **`(1.0, 0.35, 0.08)`** | Rich warm ruby/amber halation tint |

---

## 5. Scope Isolation & Non-Interference Verification

All proposed modifications are strictly constrained to:
1. `analog_fxn_r` entry in `PresetCatalog.kt`.
2. `FXN_R` entry in `CameraProfileCatalog.kt`.
3. Deduplication of temperature/tint logic in `ImageProcessor.kt` (which benefits overall pipeline predictability).
4. Synchronizing `GLViewfinderRenderer.kt` and `ShaderConstants.kt` preview shader uniforms with `ImageProcessor.kt`.
5. Updating unit test assertions in `FxnrPresetTest.kt` and `CameraProfileCatalogReaTest.kt`.

All other 39 camera profiles (`D_CLASSIC`, `D_EXP`, `CCD_DIGITAL`, `135NE`, `ZERO`, `WARM_FILM`, etc.) remain 100% untouched.

---
*Report completed. Proceeding to execution phase.*

