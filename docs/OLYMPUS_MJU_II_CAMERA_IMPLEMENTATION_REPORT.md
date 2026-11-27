# CAMELEO — Olympus Mju II (`analog_mju2`) Camera Implementation Report

**Date**: November 27, 2026  
**Target Preset**: `analog_mju2` ("MJU II" — *1997 Compact Film*)  
**Hardware Verification Status**: **NOT VERIFIED** (`adb devices -l` reported zero attached devices during this session)

---

## 1. Executive Summary & Preservation Audit

CAMELEO now includes a dedicated, authentic 1997 35mm compact film camera emulation (**MJU II**, inspired by the original fixed-lens 1997 Olympus Mju II / Stylus Epic `35mm f/2.8` 4-element glass lens compact — distinctly excluding Mju II Zoom and digital models). No duplicate Mju preset existed among CAMELEO's 33 existing cameras, so `analog_mju2` was added as the **34th** camera preset without modifying any existing camera's photographic output, LUTs, or optical presets.

### Strict Preservation Verification (`OBSERVED FACT`)
- Existing LUT PNG assets under [`app/src/main/assets/`](file:///c:/Users/User/Downloads/DazzProject/app/src/main/assets/) and existing camera presets (`D Classic`, `D FUNS`, `DQS`, `CPM 35`, `CT2F`, `135 SR`, `135 NE`, `GR D`, `FXNR`, `Golf`, `D3D`, etc.) remain unaltered.
- Dedicated `MJU II` asset [`app/src/main/assets/lut_mju2.png`](file:///c:/Users/User/Downloads/DazzProject/app/src/main/assets/lut_mju2.png) (`103,288` bytes, `512x512` Hald-8 `SRGB`) is used only by `analog_mju2` and its aliases (`mju2`, `mju_ii`, `mju ii`, `olympus_mju2`, `stylus_epic`).
- Original CAMELEO pixel-art icon [`app/src/main/res/drawable/cam_mju2.png`](file:///c:/Users/User/Downloads/DazzProject/app/src/main/res/drawable/cam_mju2.png) (`192x192` transparent RGBA, `3,364` bytes, nearest-neighbor scaled cleanly from the user-supplied wordmark-free artwork) and preview card [`app/src/main/res/drawable/sample_analog_mju2.jpg`](file:///c:/Users/User/Downloads/DazzProject/app/src/main/res/drawable/sample_analog_mju2.jpg) (`420x280`, `57,667` bytes, rendered from CAMELEO's existing internal `sample_zero.jpg`) were added cleanly.

---

## 2. Architecture & Implementation Details

### 2.1 Camera Identity, Optical Spec & Color Profile Compatibility
- **Preset & Profile**:
  - [`PresetCatalog.kt`](file:///c:/Users/User/Downloads/DazzProject/app/src/main/java/com/craftcam/app/presets/PresetCatalog.kt#L229-L283): `id = "analog_mju2"`, `name = "MJU II"`, `subtitle = "1997 Compact Film"`, `category = CameraCategory.ANALOG_FILM`, `year = "1997"`, `iconResName = "cam_mju2"`, `WhiteBalancePolicy.FIXED_DAYLIGHT` (`5500K`), clean borderless default behavior, optional `CLASSIC_35MM_BLACK` border, and optional `'97` orange 7-segment date stamp (`customYearOffset = -29`).
  - [`CameraProfileCatalog.kt`](file:///c:/Users/User/Downloads/DazzProject/app/src/main/java/com/craftcam/app/camera/profile/CameraProfileCatalog.kt#L513-L574): `MJU_II` (`35mm` primary focal length, `AspectRatioMode.RATIO_3_2`, lenses `NORMAL/WIDE/PRISM/STARBURST/ND/COLOR_FLASH` excluding fisheyes, `d3dProfile = null`).
  - [`CameraProfileCompatibility.kt`](file:///c:/Users/User/Downloads/DazzProject/app/src/main/java/com/craftcam/app/color/profile/CameraProfileCompatibility.kt#L53-L58): `ProfilePolicy(isSupported = true, defaultProfileId = "C400")` — preserves `lut_mju2.png` at 100% by default with neutral adjustments, blends smoothly (`dualLutMix = 0.65f`) with custom profiles (`EG200`, `FXN2`, `03G`), and routes `22M` strictly to monochrome `lut_profile_22m.png`.
- **Optical & Grain Discipline (`OBSERVED FACT`)**:
  - Crisp center resolution with restrained outer behavior (`softness = 0.030f`, `vignetteIntensity = 0.16f`, `vignetteRadius = 0.88f`, `chromaticAberration = 0.0012f`, `barrelDistortion = -0.006f`), high-threshold halation (`threshold = 0.88f`, `intensity = 0.14f`), zero dust/light-leak/fake-flash overlays.
  - Strictly `GrainMode.PROCEDURAL` / `GrainSourceMode.PROCEDURAL_ONLY` monochrome silver-halide grain (`intensity = 0.12f`, `scale = 0.85f`, `isColorNoise = false`, `assetPath = ""`).

### 2.2 Dedicated 3D LUT Calibration & Flash Handling
- **Independent Calibration Suite**: [`tools/mju_ii/calibrate_mju_ii.py`](file:///c:/Users/User/Downloads/DazzProject/tools/mju_ii/calibrate_mju_ii.py) registered in [`generate_all_luts.py`](file:///c:/Users/User/Downloads/DazzProject/generate_all_luts.py#L685-L690).
  - Full colorimetric metrics, neutral gray axis transfer curve (`0..255`), and side-by-side comparison are archived in [`docs/calibration/mju_ii/MJU_II_COLOR_AND_OPTICAL_SPEC.md`](file:///c:/Users/User/Downloads/DazzProject/docs/calibration/mju_ii/MJU_II_COLOR_AND_OPTICAL_SPEC.md), [`mju2_calibration_metrics.json`](file:///c:/Users/User/Downloads/DazzProject/docs/calibration/mju_ii/mju2_calibration_metrics.json), [`mju2_transfer_curve_256.csv`](file:///c:/Users/User/Downloads/DazzProject/docs/calibration/mju_ii/mju2_transfer_curve_256.csv), and [`mju2_vs_zero_sample_comparison.jpg`](file:///c:/Users/User/Downloads/DazzProject/docs/calibration/mju_ii/mju2_vs_zero_sample_comparison.jpg).
- **Skin Science & True Hardware/EXIF Flash Detection**:
  - [`SkinToneProfile.kt`](file:///c:/Users/User/Downloads/DazzProject/app/src/main/java/com/craftcam/app/color/skin/SkinToneProfile.kt#L185-L204): Added `MJU_II` (`skin_mju2`) with controlled OKLCh warmth (`0.06f`), yellow-cast protection (`0.85f`), highlight protection (`0.95f`), and `skinHighlightKnee = 0.87f`.
  - [`CameraManager.kt`](file:///c:/Users/User/Downloads/DazzProject/app/src/main/java/com/craftcam/app/camera/CameraManager.kt#L647-L722) & [`ImageProcessor.kt`](file:///c:/Users/User/Downloads/DazzProject/app/src/main/java/com/craftcam/app/processor/ImageProcessor.kt#L77-L96): Enforces clean borderless/datestamp-free defaults when switching into `analog_mju2` (restoring user defaults when leaving); confirms hardware flash firing on `FLASH_MODE_AUTO` via `CaptureResult.FLASH_STATE` and JPEG `ExifInterface.TAG_FLASH` (`detectFlashFiredFromProxyOrResult`, lines `2299-2327`) before closing `ImageProxy`; tightens MJU II flash shoulder to `0.89f` (`ImageProcessor.kt:802-803`) and skin highlight knee to `0.84f` (`ImageProcessor.kt:1377-1379`) only when flash actually fired.
  - [`GalleryScreen.kt`](file:///c:/Users/User/Downloads/DazzProject/app/src/main/java/com/craftcam/app/ui/gallery/GalleryScreen.kt#L1309-L1375): Reads EXIF `TAG_FLASH` (`readExifFlashFired`) on Darkroom photo import and redevelopment so flash-shot portraits keep highlight protection without artificial masks.
- **Shader & UI Wiring**: Updated [`ShaderConstants.kt`](file:///c:/Users/User/Downloads/DazzProject/app/src/main/java/com/craftcam/app/gl/shaders/ShaderConstants.kt#L1550-L1597), [`GLViewfinderRenderer.kt`](file:///c:/Users/User/Downloads/DazzProject/app/src/main/java/com/craftcam/app/gl/GLViewfinderRenderer.kt#L634), [`CollectionScreen.kt`](file:///c:/Users/User/Downloads/DazzProject/app/src/main/java/com/craftcam/app/ui/collection/CollectionScreen.kt#L1462), and added quick `'97` date stamp selector pill in [`ColorTuningSheet.kt`](file:///c:/Users/User/Downloads/DazzProject/app/src/main/java/com/craftcam/app/ui/components/ColorTuningSheet.kt#L354-L356).

---

## 3. Verification Results

| Check | Command / Target | Classification & Result |
| :--- | :--- | :--- |
| **Calibration & Metrics Script** | `python tools/mju_ii/calibrate_mju_ii.py` | **MEASURED RESULT** — `neutral_gray_monotonic = true`, `max_gray_channel_delta_norm = 0.0`, all 11 skin/env patches `clip = False` |
| **Python Generator Registry** | `python -c "import generate_all_luts; print('ALL_GRADES lut_mju2.png callable:', callable(generate_all_luts.ALL_GRADES['lut_mju2.png']))"` | **OBSERVED FACT** — `True` |
| **Offline Unit Tests (32 Suites)** | `.\gradlew.bat :app:testDebugUnitTest assembleDebug --rerun --offline` + JUnit XML audit | **MEASURED RESULT** — **193 tests passed, 0 failures, 0 errors, 0 skipped** (`BUILD SUCCESSFUL in 29s`; includes all 5 tests in [`MjuIIEmulationTest.kt`](file:///c:/Users/User/Downloads/DazzProject/app/src/test/java/com/craftcam/app/camera/profile/MjuIIEmulationTest.kt) sampling real `lut_mju2.png`) |
| **Debug APK Build** | `.\gradlew.bat assembleDebug --offline` | **OBSERVED FACT** — [`app/build/outputs/apk/debug/app-debug.apk`](file:///c:/Users/User/Downloads/DazzProject/app/build/outputs/apk/debug/app-debug.apk) (`172,224,532` bytes) |
| **Physical Hardware (Galaxy S21)** | `$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe devices -l` | **NOT VERIFIED** — zero devices attached (`List of devices attached` empty); live Camera2 `FLASH_STATE_FIRED`, AF/AE convergence, GL preview parity, and real-world skin/flash captures remain untested on physical hardware |

### MJU II Files Created & Modified
- **Created**:
  - [`tools/mju_ii/calibrate_mju_ii.py`](file:///c:/Users/User/Downloads/DazzProject/tools/mju_ii/calibrate_mju_ii.py)
  - [`app/src/main/assets/lut_mju2.png`](file:///c:/Users/User/Downloads/DazzProject/app/src/main/assets/lut_mju2.png)
  - [`app/src/main/res/drawable/cam_mju2.png`](file:///c:/Users/User/Downloads/DazzProject/app/src/main/res/drawable/cam_mju2.png)
  - [`app/src/main/res/drawable/sample_analog_mju2.jpg`](file:///c:/Users/User/Downloads/DazzProject/app/src/main/res/drawable/sample_analog_mju2.jpg)
  - [`app/src/test/java/com/craftcam/app/camera/profile/MjuIIEmulationTest.kt`](file:///c:/Users/User/Downloads/DazzProject/app/src/test/java/com/craftcam/app/camera/profile/MjuIIEmulationTest.kt)
  - [`docs/calibration/mju_ii/MJU_II_COLOR_AND_OPTICAL_SPEC.md`](file:///c:/Users/User/Downloads/DazzProject/docs/calibration/mju_ii/MJU_II_COLOR_AND_OPTICAL_SPEC.md), [`mju2_calibration_metrics.json`](file:///c:/Users/User/Downloads/DazzProject/docs/calibration/mju_ii/mju2_calibration_metrics.json), [`mju2_transfer_curve_256.csv`](file:///c:/Users/User/Downloads/DazzProject/docs/calibration/mju_ii/mju2_transfer_curve_256.csv), [`mju2_vs_zero_sample_comparison.jpg`](file:///c:/Users/User/Downloads/DazzProject/docs/calibration/mju_ii/mju2_vs_zero_sample_comparison.jpg)
  - [`docs/OLYMPUS_MJU_II_CAMERA_IMPLEMENTATION_REPORT.md`](file:///c:/Users/User/Downloads/DazzProject/docs/OLYMPUS_MJU_II_CAMERA_IMPLEMENTATION_REPORT.md)
- **Modified (for MJU II)**:
  - [`generate_all_luts.py`](file:///c:/Users/User/Downloads/DazzProject/generate_all_luts.py), [`PresetCatalog.kt`](file:///c:/Users/User/Downloads/DazzProject/app/src/main/java/com/craftcam/app/presets/PresetCatalog.kt), [`CameraProfileCatalog.kt`](file:///c:/Users/User/Downloads/DazzProject/app/src/main/java/com/craftcam/app/camera/profile/CameraProfileCatalog.kt), [`CameraProfileCompatibility.kt`](file:///c:/Users/User/Downloads/DazzProject/app/src/main/java/com/craftcam/app/color/profile/CameraProfileCompatibility.kt), [`SkinToneProfile.kt`](file:///c:/Users/User/Downloads/DazzProject/app/src/main/java/com/craftcam/app/color/skin/SkinToneProfile.kt)
  - [`ShaderConstants.kt`](file:///c:/Users/User/Downloads/DazzProject/app/src/main/java/com/craftcam/app/gl/shaders/ShaderConstants.kt), [`GLViewfinderRenderer.kt`](file:///c:/Users/User/Downloads/DazzProject/app/src/main/java/com/craftcam/app/gl/GLViewfinderRenderer.kt), [`ImageProcessor.kt`](file:///c:/Users/User/Downloads/DazzProject/app/src/main/java/com/craftcam/app/processor/ImageProcessor.kt), [`CameraManager.kt`](file:///c:/Users/User/Downloads/DazzProject/app/src/main/java/com/craftcam/app/camera/CameraManager.kt)
  - [`CollectionScreen.kt`](file:///c:/Users/User/Downloads/DazzProject/app/src/main/java/com/craftcam/app/ui/collection/CollectionScreen.kt), [`ColorTuningSheet.kt`](file:///c:/Users/User/Downloads/DazzProject/app/src/main/java/com/craftcam/app/ui/components/ColorTuningSheet.kt), [`GalleryScreen.kt`](file:///c:/Users/User/Downloads/DazzProject/app/src/main/java/com/craftcam/app/ui/gallery/GalleryScreen.kt)
  - [`WhiteBalanceCalibrationTest.kt`](file:///c:/Users/User/Downloads/DazzProject/app/src/test/java/com/craftcam/app/color/WhiteBalanceCalibrationTest.kt), [`FilterDevelopmentIntegrityTest.kt`](file:///c:/Users/User/Downloads/DazzProject/app/src/test/java/com/craftcam/app/processor/FilterDevelopmentIntegrityTest.kt), [`CameleoProductionSafetyTest.kt`](file:///c:/Users/User/Downloads/DazzProject/app/src/test/java/com/craftcam/app/CameleoProductionSafetyTest.kt)
