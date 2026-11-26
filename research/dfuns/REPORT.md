# D FUNS v1 implementation and validation

D FUNS now has its own capture configuration, fixed-focus request, negative-film response and scan rendering. The largest demonstrated colour change is in neutrals and skin-like colours. A dark skin-coloured chart patch with input RGB (126, 80, 56) rendered as (169, 62, 21) under the old profile and (132, 75, 47) under v1. These are S21 renderer measurements from an artificial target. A same-scene FunSaver negative and a documented scan are still needed to measure photographic fidelity.

## What the reference photographs establish

Kodak specifies 800-speed film and a manually charged flash with a nominal working range of 1.2–3.5 m. Those facts support a single-frame capture and direct illumination with natural distance falloff. The product sheet does not provide a lens transfer function, dye spectra, sensitometric curves or scanner profile. The 32 mm equivalent framing, f/10, 1/120 second and 3 m focus used here are provisional engineering targets. Different production variants, developing labs and scans can yield different results. [Kodak product information](https://business.kodakmoments.com/product/kodak-fun-saver-single-use-camera) · [The Darkroom's camera samples](https://thedarkroom.com/disposable-cameras-top-cameras-reviewed-compared/kodak-fun-saver-disposable-camera/) · [Firsthand FunSaver review](https://www.lomography.com/magazine/76046-kodak-funsaver-single-use-camera)

All 22 supplied JPEGs passed the coarse duplicate screen. Seven had a short side of at least 640 pixels. The other 15 inform scene coverage only. The inventory records SHA-256, dimensions, available source URL and manually reviewed scene type. The files lack verified camera, lab, editing and licensing records. The measurements describe candidate scans and include scene content, JPEG processing and scanner behaviour. No reference pixels ship in the APK. [Manifest](manifest.json) · [Per-image measurements](measurements.json)

| Scene from the larger files | Median encoded luma | Flat-patch standard deviation in 8-bit levels |
|---|---:|---:|
| Indoor portrait, apparent flash | 0.085 | 7.04 |
| Outdoor animal | 0.300 | 4.74 |
| Daylight street | 0.303 | 5.24 |
| Indoor group, apparent flash | 0.362 | 16.92 |
| Backlit sunset | 0.367 | 7.05 |
| Backlit landscape | 0.566 | 4.98 |
| Daylight, apparent light leak | 0.682 | 2.92 |

The wide spread argues for preserving dark interiors and bright backlit scenes. It also argues against using a single reference histogram as a target. The toolkit measures RGB/luma histograms, brightness percentiles, near-endpoint fractions, tonal-region Lab medians, and the radial frequency distribution of low-gradient patches. Skin colour accuracy, optical MTF, scene dynamic range and film-only grain cannot be inferred from these unpaired JPEGs. Public records for additional camera-attributed photographs are listed separately. A 100–300-image verified calibration set remains outstanding. [Public reference records](sources.csv)

## Architecture and implementation

| Stage | Previous behaviour | D FUNS v1 |
|---|---|---|
| Capture | Shared processed still with high-quality edge and noise reduction when available | Uses the selected Camera2 ID to negotiate calibrated Bayer, then YUV, then JPEG. Requests edge processing and noise reduction off when supported. Records actual still metadata. |
| Exposure and focus | Phone autoexposure and autofocus | Requests calibrated fixed focus near 3 m. The optional fixed-film exposure control scales ambient RAW using reported aperture, ISO and shutter metadata within four stops. Default RAW compensation stays within 0.8–1.25×. Flash and ISP fallback exposures stay under camera control. |
| Optics | Strong vignette and lateral colour separation | 3:2 profile default, restrained peripheral blur, vignette and colour separation. The optical model is radial and does not estimate subject depth. Phone field of view depends on lens and crop. |
| Colour and development | CCD LUT plus added contrast, warmth and saturation | A 64³ transform mixes linear RGB film layers, maps exposure to bounded dye density, computes transmission, removes base density and applies positive scan response. Preview and export share the same generated asset. |
| Grain | Strong scanned texture | Existing scanned grain with capture-dependent phase, luminance weighting and size adjusted for image dimensions. Film stock, scanner noise and compression are still confounded in the references. |
| Light and decoration | Export could draw a flash texture even with flash off | Real CameraX flash, no baked flash image, dust or date stamp. Additional halation is zero pending point-source measurements. The LED pulse still differs from a disposable xenon flash. |

RAW development normalizes per-site levels, applies per-frame white balance, demosaics with three rolling rows, applies the reported colour transform and encodes an SDR working bitmap. The current handoff quantizes before the film transform. Optical stages run in the existing bitmap renderer. End-to-end floating-point scene-linear optics, independent dye spectra, measured scatter, lens-shading correction, a calibrated flash energy model and independently measured scanner response remain open. CameraX preview uses a processed camera surface even for RAW still capture. Matching the LUT cannot ensure exact preview brightness or noise.

## S21 renderer comparison

The probe ran the production Kotlin renderer on the same 1536 × 1024 ramp and colour patches, with the same camera ID and grain seed. The historical comparison uses the previous preset and stock parameters in the current renderer. It excludes the old separately selected flash overlay and final date stamping. The chart is synthetic and the saved PNGs contain no personal photographs. [Input](dfuns-validation/input.png) · [Previous profile](dfuns-validation/legacy.png) · [D FUNS v1](dfuns-validation/dfuns.png)

| Median RGB inside a patch | Input | Previous profile | D FUNS v1 |
|---|---|---|---|
| Neutral | 136, 136, 136 | 164, 154, 142 | 138, 135, 134 |
| Dark skin-like patch | 126, 80, 56 | 169, 62, 21 | 132, 75, 47 |
| Light skin-like patch | 220, 167, 136 | 227, 191, 112 | 218, 169, 135 |

Across the full chart, 12.0% of previous-profile pixels and 3.7% of v1 pixels reached 0 or 255 in at least one channel. Rendering took 2967, 772 and 591 ms for the previous profile and 890, 430 and 1447 ms for v1. The fixed test order, cold-start effects and three samples preclude a speedup claim. These timings exclude shutter, decoding and saving. [Raw timing samples](dfuns-validation/render.csv)

## Calibration and reproduction

Run these commands from the repository root with Python, NumPy and Pillow. The reference analysis runs locally. Intermediate arrays preserve values above display white and the density preview uses a fixed 3-density-unit scale.

```powershell
python tools/dfuns/analyze.py C:/Users/User/Downloads/disposible_kodak research/dfuns
python tools/dfuns/test_film.py
python tools/dfuns/film.py --input chart.png --out app/build/dfuns-stages
python tools/dfuns/calibrate.py paired-patches.csv app/build/dfuns-candidate.json
python tools/dfuns/film.py --profile app/build/dfuns-candidate.json --lut app/build/dfuns-candidate.png
.\gradlew.bat --offline -I tools/dfuns/regression.gradle assembleDebug assembleDebugAndroidTest testDebugUnitTest
adb shell am instrument -w com.craftcam.app.test/com.craftcam.probe.DFunsProbe
adb shell am instrument -w -e live true com.craftcam.app.test/com.craftcam.probe.DFunsProbe
```

For a measured profile, photograph the same neutral and colour patches, skin tones, fine texture and point lights using the phone and a documented FunSaver. Hold viewpoint, illumination and scanning settings fixed. Include backlit daylight, interiors and flash at multiple measured distances. Record the physical camera variant and lab settings. Export the phone's normal capture, a minimally processed input, the previous profile and v1. Register images, then sample common unclipped patch interiors. The physical film photograph is the fifth comparison. Split entire scenes into train and holdout before taking samples.

Supply CSV columns `scene,split,input_r,input_g,input_b,scan_r,scan_g,scan_b`. Use encoded sRGB from 0–1 and split values `train` or `holdout`. The fitter adjusts three half-exposures and scanner gamma within bounded ranges. It reports brightness and chroma error for both splits and writes a separate candidate. Promote a candidate only after held-out improvement and inspection of shadows, skin and highlights. The checked-in profile is provisional. A neural image generator would obscure scene fidelity and cannot resolve the missing paired evidence.

## Changed files

- Capture, metadata and RAW development live in `camera/CameraManager.kt`, `camera/CameraState.kt`, `camera/DFunsCapture.kt` and `processor/DFunsRaw.kt`.
- Preset routing, optics and rendering live in `presets/PresetCatalog.kt`, `camera/profile/CameraProfileCatalog.kt`, `processor/ImageProcessor.kt`, `gl/GLViewfinderRenderer.kt`, `gl/shaders/ShaderConstants.kt` and `assets/lut_dfuns_v1.png`.
- The D FUNS exposure control lives in `ui/collection/CollectionScreen.kt` and `ui/navigation/CraftCamNavHost.kt`. The historic `ccd_flash` ID preserves stored camera selections.
- The catalog regression is in `CameraProfileCatalogReaTest.kt`. The profile, model compiler, calibration tool, analysis, host tests and opt-in device probe are in `tools/dfuns`. This report and its outputs are in `research/dfuns`.
