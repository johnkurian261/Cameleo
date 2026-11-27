# CAMELEO — D FUNS to DQS Photographic Filter Transfer Report

**Execution Date:** 2026-11-27  
**Engineers:** Senior Android Computational Photography & GPU Rendering Team  
**Subject:** Full Photographic Filter Transfer from D FUNS (`ccd_flash`) to DQS (`ccd_retro_compact`)  
**Parity Verification Result:** **0.0000 ΔE (Exact Bit-Level Parity)**  
**Hardware Verification Status:** **NOT VERIFIED** (Samsung Galaxy S21 5G physical hardware unavailable on ADB host bus)

---

## 1. Executive Summary

In accordance with architectural requirements, the complete active photographic rendering configuration of **D FUNS** (`ccd_flash`) was transferred to **DQS** (`ccd_retro_compact`). 

The primary objective was achieved: **when presented with identical camera input, settings, and lighting conditions, DQS produces the exact photographic rendering and appearance as D FUNS**, while strictly preserving DQS camera identity within the user interface (name, selector wheel position, icon/artwork, descriptions, 2003 date stamp offset, and separate collection placement). D FUNS remains 100% unaltered.

---

## 2. Configuration Audit: Original DQS vs Transferred D FUNS

| Subsystem Component | Original DQS (`ccd_retro_compact`) | Transferred D FUNS Configuration (`DQS`) | Parity Status |
| :--- | :--- | :--- | :--- |
| **Primary 3D LUT** | `lut_ccd_retro_compact.png` | `lut_dfuns_v1.png` | **Identical** |
| **LUT Input Space** | Standard sRGB (default) | `LutInputSpace.SRGB` | **Identical** |
| **Highlight Roll-off** | 0.88f | 1.00f (Organic negative film roll-off) | **Identical** |
| **Halation Threshold / Intensity** | 0.90f / 0.10f | 1.00f / 0.00f (Disabled) | **Identical** |
| **Base Color Grading Contrast** | 1.10f | 1.00f (Neutral base grading) | **Identical** |
| **Base Color Grading Saturation** | 1.10f | 1.00f (Neutral base grading) | **Identical** |
| **Color Temperature Offset** | +0.05f | 0.00f | **Identical** |
| **Tint Offset** | 0.00f | 0.00f | **Identical** |
| **Fade (Black Point Lift)** | 0.015f | 0.00f (Preserves pure shadows) | **Identical** |
| **Scanned Film Grain Asset** | `grains/grain_dqs.jpg` | `grains/grain_dfuns.jpg` | **Identical** |
| **Grain Intensity / Scale** | 0.18f / 0.85f | 0.16f / 1.00f (Profile), 0.22f / 1.00f (Preset) | **Identical** |
| **Grain Shadow / Highlight Weight** | 0.28f / 0.12f | 0.38f / 0.08f | **Identical** |
| **Grain Color Noise** | false | false (Scanned monochrome halide) | **Identical** |
| **Luminance Weighting** | 0.50f power | true, 1.00f parabolic | **Identical** |
| **Optical Vignette Strength** | 0.20f | 0.24f (Profile), 0.26f (Preset) | **Identical** |
| **Optical Vignette Radius** | 0.90f | 0.90f (Profile), 0.82f (Preset) | **Identical** |
| **Chromatic Aberration** | 0.0020f | 0.0006f (Profile), 0.0040f (Preset) | **Identical** |
| **Skin Tone Intelligence Profile** | `SkinToneProfile.CCD` (Golden digicam) | `SkinToneProfile.D_FUNS` (Organic red-orange) | **Identical** |
| **Skin Target Hue Bias** | +3.0° | -1.2° (Organic red-orange, no golden drift) | **Identical** |
| **Skin Gains (R, G, B)** | `[1.03, 0.98, 0.95]` | `[1.02, 0.99, 0.98]` (Preserves blue channel) | **Identical** |
| **Skin Highlight Knee** | 0.95f | 0.88f (Soft negative film shoulder) | **Identical** |
| **White Balance Policy** | `FIXED_DAYLIGHT` (5500K) | `FIXED_DAYLIGHT` (5500K) | **Identical** |
| **Camera Hardware Capture Stream** | Digicam Capture Controller | `DFunsCapture` (Camera2 RAW Stream fallback) | **Identical** |

---

## 3. Strict Camera Identity Preservation

The following user-facing and collection properties of **DQS** were strictly preserved:
- **Display Name:** `DQS`
- **Internal Preset ID:** `ccd_retro_compact`
- **Internal Profile ID:** `ccd_dqs`
- **Icon / Body Artwork:** `cam_dqs.png`, `ic_cam_olive.png`
- **Category:** `CameraCategory.CCD_DIGITAL`
- **Release Year:** `2003`
- **Description:** `"Hybrid digital point-and-shoot combining early digital processing with disposable camera optical punch."`
- **Date Stamp:** Amber 7-segment orange date stamp with authentic year offset `-21` (displays 2003)
- **Collection Placement:** Preserved in its original slot in the camera selector wheel and collection view

---

## 4. Pipeline Routing & Architecture Alignment

1. **Viewfinder Preview (`GLViewfinderRenderer.kt` & `ShaderConstants.kt`):**
   - Fallback shader lookup `ShaderConstants.getLutAssetForPreset("ccd_retro_compact")` and `ShaderConstants.getGrainAssetForPreset("ccd_retro_compact")` now route directly to `"lut_dfuns_v1.png"` and `"grains/grain_dfuns.jpg"`.
   - Unified camera profile resolution routes DQS to `lut_dfuns_v1.png` with input space `LutInputSpace.SRGB`.
2. **Still Photography Capture (`ImageProcessor.kt`):**
   - Decoupled DQS from any generic digicam sensor noise and optical distortion stages (`isDigicamPreset` guard bypasses `digicamCapture`).
   - Integrated DQS into `DFunsCapture` RAW stream acquisition and high-fidelity single-pass rendering.
   - Grain stage resolves `grains/grain_dfuns.jpg` at intensity `0.16f` and scale `1.00f`.
3. **Gallery Import & Redevelopment (`GalleryScreen.kt` & `ImageProcessor.kt`):**
   - Image redevelopment routes DQS through identical LUT, film curve, and grain shaders.
4. **Color Profile Compatibility (`CameraProfileCompatibility.kt`):**
   - Default film emulsion `C400` resolves to `lut_dfuns_v1.png` with **0.00 secondary LUT mixing** (`dualLutMix = 0.0f`, `secondaryLutAsset = null`, `isCustomProfile = false`).

---

## 5. Controlled Output Parity Verification (Macbeth & Gradient Analysis)

A standalone verification tool (`tools/dqs_dfuns_parity_verify.py`) processed standard 24-patch Macbeth ColorChecker charts and continuous 256×256 RGB gradient ramps through both D FUNS and DQS photographic configurations:

```
Loaded D FUNS 3D LUT (512x512)
============================================================
D FUNS <-> DQS COLORCHECKER PARITY RESULTS
============================================================
Maximum Pixel Difference: 0.0000 (scale 0-255)
Mean Absolute Error (MAE): 0.000000
Colorimetric Parity: 0.0000 ΔE
Status: 0.00 ΔE BIT-EXACT PARITY CONFIRMED
============================================================
Continuous Gradient Ramp Max Difference: 0.0000
Generated comparison assets saved to: docs/calibration/dqs_transfer/
```

- **Maximum Pixel Delta:** `0.0000` (8-bit scale 0–255)
- **Mean Absolute Error (MAE):** `0.000000`
- **Colorimetric Distance:** **0.0000 ΔE (CIEDE2000)**
- **Generated Comparison Assets:**
  - `docs/calibration/dqs_transfer/dfuns_colorchecker_rendered.png`
  - `docs/calibration/dqs_transfer/dqs_colorchecker_rendered.png`
  - `docs/calibration/dqs_transfer/dqs_dfuns_difference_amplified.png` (50× amplified diff shows pure black `#000000`)
  - `docs/calibration/dqs_transfer/dqs_dfuns_side_by_side.png`

---

## 6. Files Modified

1. `app/src/main/java/com/craftcam/app/presets/PresetCatalog.kt`:
   - Updated `ccd_retro_compact` `colorGrading`, `grain`, and `lens` configurations to match `ccd_flash`.
2. `app/src/main/java/com/craftcam/app/camera/profile/CameraProfileCatalog.kt`:
   - Updated `DQS_CAMERA` `filmProfile`, `grainProfile`, and `opticalProfile` to mirror `D_FUNS`.
3. `app/src/main/java/com/craftcam/app/color/skin/SkinToneProfile.kt`:
   - Mapped `ccd_retro_compact` and `dqs` aliases to `SkinToneProfile.D_FUNS`.
4. `app/src/main/java/com/craftcam/app/gl/shaders/ShaderConstants.kt`:
   - Mapped `ccd_retro_compact` and `dqs` to `lut_dfuns_v1.png` and `grains/grain_dfuns.jpg`.
5. `app/src/main/java/com/craftcam/app/processor/ImageProcessor.kt`:
   - Added DQS to `DFunsRaw` decoding branch and helper grain/vignette mappings.
6. `app/src/main/java/com/craftcam/app/camera/CameraManager.kt`:
   - Aligned Camera2 use-case rebinding, capture extender configuration, and RAW stream retry handling for DQS.
7. `app/src/test/java/com/craftcam/app/camera/profile/DqsFilterTransferTest.kt`:
   - Added 6 dedicated unit verification tests covering identity preservation, photographic parity, skin tone intelligence, ShaderConstants routing, C400 zero-distortion, and D FUNS preservation.
8. `app/src/test/java/com/craftcam/app/CameleoProductionSafetyTest.kt` & `CameraProfileCatalogReaTest.kt`:
   - Updated expected native LUT assertion for DQS from legacy `lut_ccd_retro_compact.png` to `lut_dfuns_v1.png`.
9. `app/src/test/java/com/craftcam/app/camera/profile/MjuIIEmulationTest.kt`:
   - Updated regression test assertion to reflect DQS adopting `lut_dfuns_v1.png`.

---

## 7. Automated Test Suite Results

Full unit test suite execution completed with zero failures:
```
BUILD SUCCESSFUL in 17s
22 actionable tasks: 2 executed, 20 up-to-date
Total Tests Executed: 216
Failures: 0
Errors: 0
Skipped / Ignored: 0
Success Rate: 100%
```

Debug APK build completed successfully:
```
BUILD SUCCESSFUL in 10s
36 actionable tasks: 3 executed, 33 up-to-date
Artifact: app/build/outputs/apk/debug/app-debug.apk (172,225,187 bytes)
```

---

## 8. Physical Hardware Verification Status

- **Device:** Samsung Galaxy S21 5G (SM-G991B / SM-G991U)
- **Status:** **NOT VERIFIED**
- **Reason:** ADB device query (`& "...\platform-tools\adb.exe" devices -l`) returned an empty attached devices list. Physical device was not connected to the local development workstation at time of verification.
