# CAMELEO — Thermal Vision Camera Implementation Report (`thermal_vision` / `THERMAL`)

**Date**: November 27, 2026  
**Status**: Code, GPU Shaders, CPU/Video Pipelines, UI, Artwork, and Offline JVM Unit Tests Complete (`BUILD SUCCESSFUL`); On-Device Hardware Execution **NOT VERIFIED** (no ADB device connected)

---

## 1. Executive Summary

- **OBSERVED FACT — Preset Identity & Preservation**: Added `"thermal_vision"` (`name = "THERMAL"`, `subtitle = "Simulated IR Vision"`, `CameraCategory.EXPERIMENTAL`, neutral photographic LUT/grain bypass) as the 35th preset in [`PresetCatalog.kt`](file:///c:/Users/User/Downloads/DazzProject/app/src/main/java/com/craftcam/app/presets/PresetCatalog.kt) and [`CameraProfileCatalog.kt`](file:///c:/Users/User/Downloads/DazzProject/app/src/main/java/com/craftcam/app/camera/profile/CameraProfileCatalog.kt). Zero existing presets, existing camera 3D LUTs, grain plates, optical profiles, or Golf Motion / MJU II code paths were altered (`git diff --stat -- app/src/main/assets/` shows zero Thermal asset edits).
- **OBSERVED FACT — End-to-End Pipeline Parity**: Implemented matching thermal math across four surfaces:
  1. Live OpenGL viewfinder preview ([`ShaderConstants.kt`](file:///c:/Users/User/Downloads/DazzProject/app/src/main/java/com/craftcam/app/gl/shaders/ShaderConstants.kt) `FRAGMENT_SHADER_VIEWFINDER` + [`GLViewfinderRenderer.kt`](file:///c:/Users/User/Downloads/DazzProject/app/src/main/java/com/craftcam/app/gl/GLViewfinderRenderer.kt) + [`ThermalGlHelper.kt`](file:///c:/Users/User/Downloads/DazzProject/app/src/main/java/com/craftcam/app/gl/ThermalGlHelper.kt)).
  2. Live CameraX `VideoCapture` recording via [`GPUVideoProcessor.kt`](file:///c:/Users/User/Downloads/DazzProject/app/src/main/java/com/craftcam/app/gl/GPUVideoProcessor.kt) (`thermal_vision` uniquely unlocks `CaptureMode.VIDEO` while `IS_VIDEO_FEATURE_PAUSED = true` stays enforced for all other cameras in [`CameraManager.kt`](file:///c:/Users/User/Downloads/DazzProject/app/src/main/java/com/craftcam/app/camera/CameraManager.kt)).
  3. Still capture & Darkroom photo import/redevelopment ([`ThermalVisionProcessor.kt`](file:///c:/Users/User/Downloads/DazzProject/app/src/main/java/com/craftcam/app/thermal/ThermalVisionProcessor.kt) invoked from [`ImageProcessor.kt`](file:///c:/Users/User/Downloads/DazzProject/app/src/main/java/com/craftcam/app/processor/ImageProcessor.kt)).
  4. Streaming gallery MP4/MOV video import transcoding ([`ThermalVideoTranscoder.kt`](file:///c:/Users/User/Downloads/DazzProject/app/src/main/java/com/craftcam/app/thermal/ThermalVideoTranscoder.kt) using `FRAGMENT_SHADER_STILL` + `MediaCodec` + `MediaMuxer`).
- **OBSERVED FACT — Truthfulness Constraint**: All UI elements (`THERMAL · SIMULATED IR`, `SIMULATED THERMAL · NON-MEASURING`, `HOT`/`COLD` gradient scale) explicitly label output as simulated relative apparent heat contrast. Zero fabricated Celsius (`°C`) or Fahrenheit (`°F`) readings exist in any UI, state, or test string.

---

## 2. Five Smooth GPU False-Color Palettes (`ThermalPaletteEngine`)

Generated analytically via Catmull-Rom splines into a shared `256 × 5` RGBA `GL_LINEAR` lookup texture (`createGlTextureBuffer()`) with ordered deterministic dithering in GLSL and CPU passes to prevent color banding ([`ThermalPaletteEngine.kt`](file:///c:/Users/User/Downloads/DazzProject/app/src/main/java/com/craftcam/app/thermal/ThermalPaletteEngine.kt), [`tools/thermal/generate_thermal_assets.py`](file:///c:/Users/User/Downloads/DazzProject/tools/thermal/generate_thermal_assets.py)).

**MEASURED RESULT** — Extracted from [`thermal_calibration_metrics.json`](file:///c:/Users/User/Downloads/DazzProject/docs/calibration/thermal/thermal_calibration_metrics.json) (normalized 256-step Euclidean RGB delta):

| Palette ID | Display Name | Cold Stop `(R, G, B)` | Midpoint `(R, G, B)` | Hot Stop `(R, G, B)` | Max Step Delta (norm) |
| :--- | :--- | :---: | :---: | :---: | :---: |
| `rainbow_thermal` | Rainbow Thermal (default) | `(18, 5, 44)` | `(8, 194, 118)` | `(217, 24, 24)` | `0.02095` |
| `ironbow` | Ironbow | `(6, 3, 14)` | `(176, 38, 80)` | `(255, 247, 156)` | `0.01783` |
| `red_hot` | Red Hot | `(7, 21, 56)` | `(62, 90, 146)` | `(255, 106, 69)` | `0.00936` |
| `white_hot` | White Hot | `(7, 8, 10)` | `(110, 115, 123)` | `(248, 250, 252)` | `0.00483` |
| `black_hot` | Black Hot | `(248, 250, 252)` | `(109, 114, 122)` | `(7, 8, 10)` | `0.00483` |

Visual reference ramp & repo-derived collection previews:
- [`thermal_palettes_ramp.png`](file:///c:/Users/User/Downloads/DazzProject/docs/calibration/thermal/thermal_palettes_ramp.png)
- [`cam_thermal.png`](file:///c:/Users/User/Downloads/DazzProject/app/src/main/res/drawable/cam_thermal.png) (`192×192`, `1,619` B)
- Collection samples in `app/src/main/res/drawable/`: `sample_thermal_vision.jpg` (`91,878` B), `sample_thermal_ironbow.jpg` (`76,536` B), `sample_thermal_redhot.jpg` (`20,391` B), `sample_thermal_whitehot.jpg` (`63,205` B), `sample_thermal_blackhot.jpg` (`18,611` B).

---

## 3. Intelligent Thermal Simulation & GPU Fallback Architecture

- **OBSERVED FACT — Bounded Low-Cost Analysis**: [`GlThermalMaskExtractor.kt`](file:///c:/Users/User/Downloads/DazzProject/app/src/main/java/com/craftcam/app/gl/GlThermalMaskExtractor.kt) renders a reduced `128 × 96` secondary `EGLSurface` to an `ImageReader` gated by `AtomicBoolean` backpressure at ~10.5 Hz, avoiding any per-frame high-res CPU pixel readback.
- **OBSERVED FACT — Subject Heat Estimation & Temporal Smoothing**: [`ThermalSubjectEstimator.kt`](file:///c:/Users/User/Downloads/DazzProject/app/src/main/java/com/craftcam/app/thermal/ThermalSubjectEstimator.kt) combines zero-cost Camera2 `HardwareFaceDetector` bounds (`CaptureResult.STATISTICS_FACES`, front-mirror corrected in `CameraManager`) with YCbCr skin/torso chromaticity concordance, head-torso spatial priors, separable spatial blur, P05–P96 adaptive histogram contrast normalization, and EMA temporal damping (`0.28f` mask / `0.18f` bounds) into preallocated double `ByteBuffer` masks (`RG8` uploaded via `glTexSubImage2D`).
- **OBSERVED FACT — GPU-Only Fallback**: When segmentation is disabled (`segmentationEnabled = false`), mask texture is unavailable, or faces are absent, both GLSL and CPU shaders execute the zero-mask luminance + chromatic warmth branch smoothly (`smoothstep` warm skin-chroma lift + adaptive normalization + subtle bolometer noise + microbolometer softness).

---

## 4. Dedicated UI, Camera Controls & Gallery Integration

- **OBSERVED FACT — Dedicated Thermal Layout (`ThermalVisionUi.kt`)**: Implemented [`ThermalVisionUi.kt`](file:///c:/Users/User/Downloads/DazzProject/app/src/main/java/com/craftcam/app/ui/viewfinder/ThermalVisionUi.kt) and integrated it into [`ViewfinderScreen.kt`](file:///c:/Users/User/Downloads/DazzProject/app/src/main/java/com/craftcam/app/ui/viewfinder/ViewfinderScreen.kt):
  - `ThermalTopToolbar`: `THERMAL / SIMULATED IR` badge, front/rear camera switch, `INVERT` hot/cold toggle, Effects/HUD drawer button, Settings menu.
  - `ThermalViewfinderOverlay`: Corner viewfinder brackets, vertical `HOT`/`COLD` palette gradient scale (inverting dynamically with `inverted`), optional monochrome HUD overlay, optional open-center crosshair reticle, `REC MM:SS` pulse badge.
  - `ThermalControlsDeck` + `ThermalBottomControlRow`: Horizontal 5-palette selector strip, `PHOTO | VIDEO` switcher, `IMPORT` launcher (`PickVisualMedia(ImageAndVideo)` routing stills via `ImageProcessor.developBitmap` and videos via `ThermalVideoTranscoder`), collapsible `CAM` camera wheel toggle, live `SENSITIVITY` and `CONTRAST` sliders, and photo/video shutter.
  - `ThermalEffectsDrawer`: Sensor softness, fine electronic noise, AI Subject Guidance toggle, Adaptive Contrast toggle, Monochrome HUD toggle, Crosshair toggle, Hot/Cold Inversion, Reset.
- **OBSERVED FACT — Darkroom Integration (`GalleryScreen.kt`, `CollectionScreen.kt`)**: Updated [`GalleryScreen.kt`](file:///c:/Users/User/Downloads/DazzProject/app/src/main/java/com/craftcam/app/ui/gallery/GalleryScreen.kt) (`resolveFilterName` maps `"THERMAL"` in `CAMELEO_THERMAL_*.jpg` / `CAMELEO_THERMAL_*.mp4` to `"THERMAL"` album; made `decodeOrientedMutableBitmap` `internal` for shared EXIF orientation handling) and [`CollectionScreen.kt`](file:///c:/Users/User/Downloads/DazzProject/app/src/main/java/com/craftcam/app/ui/collection/CollectionScreen.kt) (`resolveSampleResIds` for all 5 palette samples).

---

## 5. Verification Summary

- **MEASURED RESULT — Unit Tests**: Ran `.\gradlew.bat :app:testDebugUnitTest assembleDebug --offline` after final code edits (`BUILD SUCCESSFUL`).
  - JUnit XML totals: **32 suites, 200 tests, 0 failures, 0 errors, 0 skipped**.
  - [`ThermalVisionTest.kt`](file:///c:/Users/User/Downloads/DazzProject/app/src/test/java/com/craftcam/app/thermal/ThermalVisionTest.kt) (5 tests, 0 failures): verified 5-palette continuity & spectral endpoints, `invertHotCold` symmetry, `ThermalSubjectEstimator` human-over-background heat separation & temporal EMA convergence, deterministic CPU shader parity with/without segmentation + sensitivity/contrast controls, and preset/LUT isolation & zero fabricated temperature strings.
  - Updated catalog size assertions `34 -> 35` in `CameleoProductionSafetyTest.kt`, `MjuIIEmulationTest.kt`, `WhiteBalanceCalibrationTest.kt`, and `FilterDevelopmentIntegrityTest.kt`.
- **MEASURED RESULT — Debug APK**: Built [`app-debug.apk`](file:///c:/Users/User/Downloads/DazzProject/app/build/outputs/apk/debug/app-debug.apk) (`172,225,031` bytes).
- **NOT VERIFIED — Physical Android Device & On-Hardware Benchmarks**: `adb devices -l` reported `List of devices attached` (no device connected). Live GLSL shader compilation on Adreno/Mali GPU, 128×96 `ImageReader` surface extraction timing, `VideoCapture` GL frame timing, `ThermalVideoTranscoder` hardware `MediaCodec` encode, Darkroom video/photo playback, thermal/FPS/memory metrics remain unverified on hardware until a test device is attached.
