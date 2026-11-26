# CAMELEO — Daily Development Progress & Architectural Reports

This document tracks daily engineering milestones, architectural audits, test suite health, physical device deployments, and feature additions across the CAMELEO development lifecycle.

---

## Daily Log: November 26, 2026

### Focus Areas
1. **Color Profile Architecture Phase 2 (Dedicated 3D LUTs & Sensitometry)**
2. **Camera Icon Pixel Art Extraction & Replacement (D Funs, Zero, DQS, D3D, CPM35)**
3. **Hardware-Aware White Balance & Single-Application Invariant**
4. **Camera Autofocus Intelligence & Focus Stability**
5. **App Stability & Capture Freezes Elimination**
6. **Ricoh GR Digital (2005) CCD Profile Calibration**
7. **Skin Colour Science Calibration Integrity & Authenticity Audit**
8. **D3D Stereoscopic Lens Discipline & Fisheye Exclusion**

---

### Detailed Daily Milestones

#### 1. Color Profile Architecture Phase 2 (`65f884d`)
- **Audit & Decoupling**: Audited `Localizable.strings` and `DZImageFilter.framework`, confirming that Dazz Cam backs each color profile with dedicated 3D lookup tables (`lookup_*`). Strictly decoupled the **Physical Camera Body** (optics, distortion, softness, vignette, chromatic aberration, halation, grain, frame, shutter, flash) from the **Color Profile** (film emulsion, dye density curves, non-linear S-curves, tone reproduction).
- **Dedicated 64×64×64 3D LUTs**: Generated 10 calibrated $512 \times 512$ Hald-8 PNGs for all profiles:
  - `03G`: Warm daylight movie film, lifted toe, gentle shoulder, golden amber midtone bias (`lut_profile_03g.png`).
  - `C400`: Signature balanced ISO 400 consumer color print film, authentic skin tones, rich olive greens (`lut_profile_c400.png`).
  - `07FF`: Vision3 50D/250D scanner look, vibrant cyan/teal sky separation, clean highlights (`lut_profile_07ff.png`).
  - `19P`: Tungsten movie stock on cinema print tones, deep violet/magenta shadow density (`lut_profile_19p.png`).
  - `19P2`: Generic print tones, softer midtone gradation, delicate orchid shadows (`lut_profile_19p2.png`).
  - `19M`: Monitor CRT calibrated tones, warm amber midtones, neutral blacks (`lut_profile_19m.png`).
  - `22M`: Double-X 5222 panchromatic monochrome, spectral weighting $\text{Luma} = 0.35R + 0.45G + 0.20B$, zero skin hue drift (`lut_profile_22m.png`).
  - `EG200`: Consumer everyday daylight film, subtle green shadow tint, vibrant dynamic range (`lut_profile_eg200.png`).
  - `FX33`: Pocket compact CCD digital grade, cool neutrals, punchy consumer saturation (`lut_profile_fx33.png`).
  - `FXN2`: Second-generation refined negative film, terracotta/apricot skin tones, gentle contrast (`lut_profile_fxn2.png`).
- **Compatibility Matrix ([`CameraProfileCompatibility.kt`](file:///c:/Users/User/Downloads/DazzProject/app/src/main/java/com/craftcam/app/color/profile/CameraProfileCompatibility.kt))**: Defined per-camera supported profiles, defaults (e.g. `C400` for 35mm/disposables, `19P` for D3D/CT2F, `FX33` for CCDs), disabled profile swapping for Zero (modern ISP) and camcorders.
- **Single-Application Invariant & White Balance**: Guarded against double color grading; zeroed software temperature offset when hardware WB is active.
- **Achromatic Monochrome Guarantee**: Added `isMonochrome` bypass to `SkinToneIntelligenceEngine` to eliminate any skin tone steering on black-and-white output.
- **Diagnostic Inspector ([`ColorProfileInspector.kt`](file:///c:/Users/User/Downloads/DazzProject/app/src/main/java/com/craftcam/app/color/profile/ColorProfileInspector.kt))**: Created developer inspector producing markdown diagnostics for active body, LUT, color spaces, and invariant status.
- **Real Preview Thumbnails**: Generated 10 photographic thumbnails (`thumb_profile_*.png`) showing actual photographic output in the bottom sheet.
- **Automated Tests**: Added 7 new tests in `ColorProfilePhase2Test.kt` (test suite increased to **167 passing tests**).

#### 2. Camera Pixel Art Icon Replacements (`3418edc`, `d03609c`, `b061832`)
- Extracted and centered high-contrast pixel art camera silhouettes onto transparent $198 \times 198$ RGBA canvases using border-connected BFS flood-fill (`threshold = 12`) to eliminate black halos while preserving dark interior viewfinders and lenses:
  - **D Funs** (`cam_dfuns.png`, `cam_d_funs.png`): Yellow disposable body with red diagonal stripe and "Dazz" brand.
  - **Zero** (`cam_zero.png`): Vertical slate smartphone silhouette with triple-lens module.
  - **DQS** (`cam_dqs.png`): Green top band, red divider stripe, tan lower body, flash window.
  - **D3D** (`cam_d3d.png`): Triple-lens stereoscopic camera body with orange bezel and dual sensor viewfinders.
  - **CPM 35** (`cam_cpm35.png`): Ivory disposable body with dark top plate, concentric lens rings, and dual grip buttons.

#### 3. Hardware-Aware White Balance Architecture (`3418edc`, `f5ad4a2`)
- **Samsung Galaxy S21 Sensor Calibration ([`HardwareWhiteBalanceCalibrator.kt`](file:///c:/Users/User/Downloads/DazzProject/app/src/main/java/com/craftcam/app/color/HardwareWhiteBalanceCalibrator.kt))**:
  - Calibrated against Sony IMX555 / IMX374 spectral matrices across $[2500K..9500K]$ using Planckian locus ($y = -3.000 x^2 + 2.870 x - 0.275$).
  - Outputs `COLOR_CORRECTION_GAINS` directly to Camera2 ($R \approx 2.187, G = 1.0, B \approx 1.688$ at 5500K daylight; $R \approx 1.305, G = 1.0, B \approx 2.696$ at 2856K tungsten).
- **Single-Application Invariant**: Shaders and CPU `ImageProcessor` verify `isHardwareWbActive` and suppress software user Kelvin offset to eliminate duplicate color shifts.
- **Preset Policies**: Configured `FIXED_DAYLIGHT` (5500K) for disposables, `FIXED_TUNGSTEN` (3200K) for CT2F, `AUTO` for Zero, `HYBRID_ILLUMINANT` for vintage CCDs.

#### 4. Ricoh GR Digital (2005) CCD Profile Calibration (`b4d6eaa`)
- Replaced film emulation on `analog_warm_film` / `ccd_grd` with authentic Ricoh GR Digital (2005) 8.1MP CCD rendering.
- Eliminated inappropriate analog film dust, scratches, and orange halation from the digital camera profile.
- Generated dedicated `lut_ccd_grd.png` with punchy neutral greens, high dynamic range digital contrast, and cool-neutral shadow tone.

#### 5. App Stability & Shutter Reliability (`ea43a28`, `ae900d7`)
- **Watchdog & Freeze Prevention**: Implemented lifecycle-safe preview watchdog, try-catch guards on `imageReader.acquireLatestImage()`, and safe GL context checks.
- **ZSL Buffer Drops**: Resolved buffer drops on Samsung S21 by configuring `ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY`.
- **Automatic Shutter Retry**: Added automatic retry mechanism on timeout or shutter lag.

#### 6. Hardware-Aware Autofocus Subsystem (`2f5a312`)
- Created centralized `AutofocusController` managing Camera2 `CONTROL_AF_MODE`, `CONTROL_AF_TRIGGER`, and `CONTROL_AF_STATE`.
- Implemented continuous picture autofocus (`CONTINUOUS_PICTURE`) with touch-to-metering region coordinate mapping, letterbox aspect ratio projection, and pre-capture settling gate.

#### 7. D3D Stereoscopic Lens Discipline & Fisheye Exclusion
- **Optical Incompatibility**: Excluded circular fisheye (`FISHEYE_F`) and full-frame fisheye (`FISHEYE_W`) lenses from the D3D (`d_3d`, `analog_cinema_film`) camera profile. Fisheye curvilinear warping and black hood vignetting severely corrupt stereoscopic horizontal parallax alignment, causing depth estimation failure and severe retinal rivalry.
- **Catalog & Selection Validation**: Updated `D_3D.supportedLenses` in `CameraProfileCatalog.kt` to strictly omit fisheye lenses. Added validation in `CameraManager.selectLensAccessory` to prevent mounting unsupported lenses on D3D.
- **Viewfinder & Pipeline Guards**: Guarded `GLViewfinderRenderer.kt` and `ImageProcessor.kt` so that D3D rendering automatically falls back to `LensProfile.NORMAL` if an invalid lens state is passed.
- **Automated Unit Tests**: Added `d3dCameraProfileStrictlyExcludesFisheyeLenses` in `ThreeDModeTest.kt` asserting that `D_3D.supportedLenses` contains only compatible optical accessories.

---

### Commits Completed Today (Nov 26, 2026)
1. `65f884d` — *feat(color): implement Color Profile Phase 2 dedicated 3D LUTs and authentic camera integration*
2. `b061832` — *feat(camera): replace CPM35 icon with new ivory disposable pixel art drawable*
3. `d03609c` — *feat(camera): replace D3D icon with new triple-lens 3D pixel art drawable*
4. `3418edc` — *feat(camera): update D Funs, Zero, and DQS icons and implement hardware white balance calibration*
5. `b4d6eaa` — *fix(grd): implement authentic Ricoh GR Digital 2005 CCD profile, eliminate film dust and halation, and generate dedicated lut_ccd_grd*
6. `ae900d7` — *fix(capture): eliminate ZSL buffer drops with MINIMIZE_LATENCY, stabilize D FUNS stream, and add automatic capture retry*
7. `ea43a28` — *fix(stability): prevent capture freezes with try-catch guards, lifecycle-safe preview watchdog, and GL context checks*
8. `2f5a312` — *feat(focus): implement hardware-aware autofocus subsystem, coordinate letterbox projection, and settling gate*
9. `21842fa` — *feat(color): calibrate source-relative skin protection, D3D shadow neutrality, and gallery cleanup*
10. `a887a31` — *feat(cpm35): calibrate authentic CCD electronic noise, gradient-adaptive detail, and digital highlight clipping*
11. `8a832f7` — *feat(skintone): implement Phase 3 authentic camera rendering, source-relative protection, and full-res optimization*
12. `0deac49` — *feat(color): implement authentic Dazz Cam 10-profile color scheme architecture*
13. `f5ad4a2` — *fix(camera): align temperature control with Dazz Cam reverse engineering analysis*
14. `378ad2b` — *fix(gallery): resize oversized back button to proportional 44dp circular target*
15. `69e6bbe` — *feat(color): implement camera-specific skin colour science for digicam and disposable profiles*
16. `c4fcce0` — *feat(d3d): calibrate cinema LUT and implement dedicated D3D skin profile with yellow-cast guard*

---

## Daily Log: November 25, 2026

### Focus Areas
1. **Skin Tone Intelligence Engine Phase 1 & 2**
2. **Digicam Physical Accuracy & Hybrid Illuminant Estimation**

### Detailed Daily Milestones
- **Skin Tone Intelligence Engine (`10233ae`)**: Built 4-layer computational skin intelligence system:
  - Layer 1: Face anatomy detection & spatial prior weighting.
  - Layer 2: Fast color-space skin probability density estimation.
  - Layer 3: Guided mask feathering and boundary refinement.
  - Layer 4: Camera-specific hue steering and highlight roll-off.
- **Continuous OKLCh Color Modeling (`07f7fa5`)**: Upgraded hue steering to perceptual OKLCh color space ($L, C, h$) with smooth sigmoid protection curves to prevent edge posterization.
- **Hybrid Illuminant Estimation (`4d393e7`)**: Implemented Grey-World and Max-RGB chromaticity estimator in `HybridIlluminantEstimator.kt` to classify scene lighting into Daylight, Tungsten, Fluorescent, or Flash.

---

## Daily Log: November 24, 2026

### Focus Areas
1. **Release Management & Version Bump**
2. **D3D Capture Pipeline & In-App Video Playback**

### Detailed Daily Milestones
- **Release v2.2.0 (`af992c7`)**: Tagged and documented v2.2.0 release, updated GitHub Actions APK release workflows.
- **D3D & Video Playback (`3d21ea5`)**: Refined stereoscopic multi-frame rendering for D3D stereo capture and improved hardware video decoding in the media player.

---

## Daily Log: November 22, 2026

### Focus Areas
1. **UI Refinements & Zoom Mode Normalization**

### Detailed Daily Milestones
- **UI Tuning (`e05811c`)**: Normalized pill icon sizes in viewfinder chrome, refined D3D processing indicator animations, and streamlined zoom mode controls ("Frame" vs "Lens").

---

## Daily Log: November 12, 2026

### Focus Areas
1. **Dazz Cam Tactile Controls & UI Reverse-Engineering**
2. **D3D Stereoscopic Parallax Calibration**

### Detailed Daily Milestones
- **Dazz Cam Temperature & Exposure Slider (`376446c`, `2f0944b`, `d139b98`)**: Engineered exact tactile temperature slider with dotted line markings, black-and-white ring thumb, and haptic feedback.
- **Stereoscopic Wigglegram (`a0ebc62`, `7938d11`, `5bb8381`)**: Calibrated depth stratification and convergence pivot lock to ensure zero horizontal drift during 3-frame looping animations.

---

## Test & Build Verification Summary

| Metric | Status |
| :--- | :--- |
| **Unit Test Suite** | **167 tests passing** (0 failures, 0 skipped) |
| **Build State** | `assembleDebug` SUCCESSFUL |
| **Physical Hardware** | Verified on Samsung Galaxy S21 5G (`SM-G991B`, Android 14) via ADB |
| **Git Working Tree** | Clean, checkpointed at `65f884d` |
