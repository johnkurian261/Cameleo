# FXNR V2 photographic quality report

Galaxy S21 SM-G991B · 24 November 2026 · default film intensity 70% · normal lens

The legacy scanned-grain pass produced the largest measured drop on the archived S21 scene. Its median luma fell from 79.1 immediately before grain to 46.5 immediately after. The scan averages about 19/255, while the blend subtracts 128 and amplifies the residual. Most of the texture therefore acted as negative exposure. In V2, the equivalent readings were 91.6 and 92.3.

## Controlled rendering

Each row uses identical decoded pixels for every stock. Values are median Rec.709-weighted encoded RGB luma on a 0–255 scale. The inputs fit 512 × 512. These measurements use the bitmap immediately before JPEG encoding. The results directory includes the corresponding quality-93 JPEGs. The earlier archived report used 800-pixel Pillow luma and historical JPEGs, so those values differ.

| Scene | Source | Legacy | Lift 0.015 | Selected 0.035 | Lift 0.065 |
|---|---:|---:|---:|---:|---:|
| s21 | 83.7 | 46.0 | 88.8 | 91.9 | 96.5 |
| scene | 54.2 | 27.0 | 58.5 | 60.1 | 62.4 |
| portrait | 101.0 | 53.7 | 105.5 | 108.7 | 113.4 |
| backlit | 15.0 | 15.9 | 25.9 | 25.9 | 25.9 |
| sky | 175.1 | 164.6 | 185.9 | 185.9 | 185.9 |
| night | 101.3 | 58.7 | 106.6 | 109.7 | 114.4 |
| tungsten | 152.1 | 133.3 | 163.1 | 163.2 | 163.3 |

The 0.035 setting preserved separation in dark fabric without lifting the night and portrait fixtures as far as 0.065. The toe tapers the change near black. The neutral black patch measured 17.4 in legacy and 18.5 in V2. No automatic night-scene classifier or global exposure compensation was introduced. Night photography here is represented by a lit interior at night and a deeply backlit scene. Additional real low-light portraits would improve coverage.

[Comparison contact sheet](contact-sheet.jpg) · [Processing stages](stage-sheet.jpg) · [All stage statistics](measurements.csv) · [Originals, individual stage exports and JPEGs](fxnr-results/)

## Processing order and changes

1. CameraX requests the highest available 4:3 photo resolution with fallback, minimize-latency capture and camera JPEG quality 95. Exposure compensation and AWB act in the camera. The normal film path converts the ImageProxy to a bitmap, closes it, then draws rotation, mirroring and the framing crop together. An optional negative is archived separately. Android draws the ISP image into an ARGB_8888 working bitmap. The rendering math operates on display-encoded SDR RGB.
2. Legacy applies radial softness and transverse channel displacement before its grade. Normal-lens V2 keeps native detail, with softness available through the diffusion option. It avoids two full-frame scratch passes at default settings. No sharpening or skin smoothing is required to manufacture additional edge contrast.
3. Legacy applies an approximate manufacturer/module matrix to byte channels on the CPU. Its pre-LUT exponent of 1.25 depresses lower-half values. V2 feeds ISP-rendered RGB directly to the 64-cube LUT in both paths. CPU export trilinearly interpolates eight entries in the packed 512 × 512 atlas. GLES uses a centered, linearly filtered 3D texture or bilinear atlas slices interpolated in blue. Both mix the lookup with the original by filter intensity.
4. Both apply temperature and tint, stock shadow lift and contrast about 0.26. Legacy then adds another global contrast about 0.5 under the name clarity and hard-clamps each channel. V2 replaces that stage with a smooth compact polynomial on luma. The same offset is added to each channel. Above luma 0.685 the adjustment is zero. A small smooth toe correction helps preserve the old black point. Saturation stays floating point through the shoulder, and V2 omits the CPU-only peach bias.
5. Gamma, when active, precedes shadow dye tint. The existing max-channel rational shoulder compresses all channels by the same factor, followed by stock highlight desaturation, toe/white scaling and fade. The original JPEG sets the upper limit for captured detail. There is no reconstruction of already-clipped channels.
6. Vignette and optional lens effects precede luminance-weighted grain. A 1024-square density tile from the original scan is centered near 128 and scaled to a standard deviation of about 2.5 texture codes. The first normalized trial used 8 and gave a dark flat-patch deviation of 7.94 output codes. The selected configuration measured 2.29. Default FXNR uses one scanned layer. Runtime capture randomizes its phase, while the regression fixes it.
7. After packing the float grade to 8 bits the export builds a reduced-resolution highlight mask, blurs it and screens the warm halo over the image. This intentionally thresholds the rendered print highlights after the shoulder. It approximates halation in display space. Preview uses a cheaper analytical glow. Dust, light leak, flash artwork, date and border are subsequent optional passes. Default FXNR disables dust, leak, date and border. The app compresses the final bitmap once at JPEG quality 93, writes optional EXIF and publishes via MediaStore. JPEG input and output are separate encodes.

## Detail, highlight and color checks

On the neutral input ramp from 200 through 255, the grain-free export produced 15 distinct rounded luma codes, up from 9. Distinct RGB triples rose from 22 to 31. The top white patch stayed near 230 in both. On the synthetic five-code fabric weave the measured modulation was 4.0 in legacy and 5.0 in V2 with grain off. The tested straight edge showed zero overshoot or undershoot. A real knit, curly hair or noisy face can behave differently. Abrupt or repeated steps in the source LUT still limit the output ramp.

The physical GLES viewfinder rendered the same chart through its external OES surface using front and rear renderer configurations. With grain, halation, optics and vignette disabled, eight fixed color patches averaged 0.50 absolute RGB codes of difference per channel versus CPU export. This isolates the color grade. Live ISP exposure, AWB, grain phase and spatial glow can still differ between preview and capture. No calibrated colorimeter or skin-tone ground truth was available.

[Chart and edge measurements](chart.csv) · [CPU and GPU patch values](preview.csv)

## Device, resolution and performance

Rear ID 0 reported FULL hardware, RAW_SENSOR, JPEG and YUV. Front ID 1 reported LIMITED hardware with JPEG and YUV. Both advertised ISO 50–3200 and fast/high-quality noise reduction and edge enhancement. The binding code now checks the selected camera's advertised processing modes before setting preview or capture requests. Missing capabilities leave the vendor default in control. FXNR consumes the SDR ISP image. A RAW or HDR10 acquisition pipeline would require different development and matching.

A live CameraX smoke test rendered the S21 rear camera at 4032 × 3024 and front camera at 3648 × 2736 through FXNR V2. Those frames stayed in memory. [Lens results](fxnr-results/lens-smoke.txt)

The normal high-resolution S21 image fits the 4096 long-edge setting. For larger FXNR captures V2 keeps native dimensions when the heap estimate permits, with a 4096 fallback. The user's compact-export option still requests 2048 and framing may crop the result. Other devices and higher-resolution sensors were not benchmarked.

For 4032 × 3024 on the S21, four warmed runs per stock alternated order after one warm-up each. Median processing time was **3.40 s legacy and 2.46 s V2** (28% lower). This measures the effect pass on a mutable bitmap, excluding image decode, shutter, JPEG and MediaStore. The process high-water RSS reached 613 MiB. Both stocks, diagnostic images and caches shared that process, so this peak cannot establish a per-stock memory saving. The smaller scan reduces its decoded pixel array from roughly 24 MiB to 4 MiB.

[Timing samples](fxnr-results/benchmark.csv) · [Camera2 capability dump](fxnr-results/capabilities.txt)

## Dazz and Samsung comparison

Four packaged Dazz FXN_R photos and a Samsung stock-camera JPEG show independent scenes. Some Dazz demonstration images serve here as already-graded stress inputs. Regrading such an image tests how CAMELEO handles it, without isolating Dazz's own filter. A fixed scene captured by all three cameras or identical ungraded imports would establish a direct comparison. There is currently no controlled evidence of superiority over Dazz or Samsung. [Prior baseline and reference measurements](../FXNR_PHOTO_QUALITY_COMPARISON.md)

## Files and reproducibility

Production changes cover CameraManager.kt, CameraProfileCatalog.kt, CameraProfileComponents.kt, ImageProcessor.kt, GrainStage.kt, GLViewfinderRenderer.kt, GPUVideoProcessor.kt, ShaderConstants.kt and the fxnr_v2_density.png texture. CameraProfileCatalog.FXN_R_LEGACY retains the old configuration. Point FXN_R at that profile for a reversible whole-app A/B build. FilmResponseTest.kt checks curve monotonicity. GalleryScreen.kt and gallery_menu_chevron.png implement the accompanying gallery changes. The repository ignore rules include the new test harness and texture.

From the repository root run the following with a connected Android device, ADB on PATH, and Python with Pillow and NumPy. The optional archived S21 input is used if present. Gallery images are user data. Regression input and output use app-specific test storage.

```powershell
python tools/fxnr/fixtures.py
.\gradlew.bat -I tools/fxnr/regression.gradle assembleDebug assembleDebugAndroidTest testDebugUnitTest
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w com.craftcam.app.test/com.craftcam.probe.FxnrRegression
adb shell am instrument -w -e live true com.craftcam.app.test/com.craftcam.probe.FxnrRegression
adb pull /sdcard/Android/data/com.craftcam.app/files/fxnr-results/. research/fxnr-v2/fxnr-results
python tools/fxnr/measure.py
```
