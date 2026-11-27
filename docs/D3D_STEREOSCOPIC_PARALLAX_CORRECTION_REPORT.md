# D3D stereoscopic parallax correction

**Verdict — verified on four offline geometry replays. Live S21 capture remains NOT VERIFIED.**

**MEASURED RESULT** — The two off-centre portraits showed the largest improvement. Median extreme-view bridge drift fell from 33.51 to 2.29 px and from 14.11 to 0.01 px. The first remains just outside the experimental 2 px target. Clip 4 retains about 5.27 px of tracked eye-spacing change despite an anchored bridge. The first two portraits now have less background motion, and the previously excellent bridge anchoring in clip 2 lost 1.68 px. This is a conservative portrait setting. [Full comparison](D3D_BASELINE_MOTION_ANALYSIS.md) · [Video](calibration/d3d/comparison.mp4)

## What caused the instability

**OBSERVED FACT** — The old D3D geometry path used the preview face list frozen before the shutter callback. The producer used hard-coded orientation assumptions. The export rotated and sometimes mirrored or cropped the still independently. If the preview detector missed a face, geometry took a median near the image centre. On a left-edge portrait, that region can contain the wall. One median for convergence is appropriate, but sampling the wrong surface moves the entire face. Historical face metadata is unavailable, so attribution of a particular clip to a specific detection failure is a **HYPOTHESIS**.

**OBSERVED FACT** — The old warp also used the same width-scaled displacement budget at a given strength across scenes. Strong depth separation or high estimated relief across a close face could produce substantial stretching. It already projected horizontally, used depth for visibility, sampled the farther side for holes and made four distinct views. The input clip timing and near-zero vertical median motion supported retaining that infrastructure.

## Capture to playback

1. **OBSERVED FACT** — CameraX captures one still. D3D synthesizes viewpoints. The camera does not record four simultaneous physical views. Capture coordination, watchdogs and the image-processing semaphore stay on the existing path.
2. **OBSERVED FACT** — The new geometry metadata path takes only a timestamp-matched exposure result when available. It maps sensor face boxes, optional eyes and a localized, locked AF region through the CameraX sensor-to-buffer transform and the same bitmap matrix used for rotation, mirroring, crop and output scale. Off-crop focus is discarded. Preview faces continue to serve existing photographic grading. Only D3D geometry changes its source. There is no dedicated explicit 3D-anchor UI.
3. **OBSERVED FACT** — A valid capture focus point has priority. Otherwise the largest confidence-weighted valid still face supplies the eye-midpoint region. With bounds only, the sampler uses a compact upper-central face region. If metadata is unavailable, the existing Android native face detector runs once on a thumbnail of the final oriented crop. Without a valid face, a coarse grid looks for a coherent nearer surface away from the edges, then falls back to a central median. Nonfinite and out-of-range pivot samples are rejected.
4. **OBSERVED FACT** — Depth Anything V2 predicts relative near-high disparity. The existing aspect-preserving inference, percentile normalization and cross-bilateral refinement are retained. Invalid model output falls back to the heuristic path. No metric scene distance is inferred. Each generated view uses the same depth field and convergence value. Actual local relief remains in the face.
5. **OBSERVED FACT** — The virtual baseline respects the strength setting, width, 10th–90th percentile scene span, portrait area, central facial relief and lower-confidence heuristic depth. It never boosts a flat map. The four horizontal offsets, depth-aware splatting, visibility order, subpixel resampling and far-side reconstruction remain. All views share framing and dimensions. Synthetic filling can still stretch unseen texture.
6. **OBSERVED FACT** — The still is graded once before projection. Retro frame styling, grain distribution, native LUT routing, white balance, optical controls, colour profiles and camera artwork were outside the geometry edits. Normal, Prism and Starburst remain available. Fisheye remains excluded in the catalog and export guard. The only RetroFrameRenderer edit releases partial output on failure.
7. **OBSERVED FACT** — The encoder uses the existing 6-frame bounce, four repetitions and 100 ms timestamps. MediaCodec writes H.264 to MediaStore. The in-app gallery uses its existing playback/loop setting. The comparison GIFs are review artifacts. The D3D capture path exports MP4.

## Evidence and limits

**MEASURED RESULT** — All four headless replays used the production depth backend, generated four fully opaque frames and retained background motion. The measured vertical median of the bridge was within 0.01 px in all replays. Replay depth-stage wall time ranged from 665 to 1,655 ms in the final recorded per-clip run. Warping, upsampling and dumping the four frames took 164–757 ms. Sampled JVM heap growth ranged from 0 to about 103 MiB. A zero means GC or heap reuse obscured growth. This excludes native ONNX allocations, Android bitmaps, the HAL and the encoder. It is neither phone memory nor shutter-to-save latency. [Clip 1 measurements](calibration/d3d/1/replay.csv) · [Clip 2](calibration/d3d/2/replay.csv) · [Clip 3](calibration/d3d/3/replay.csv) · [Clip 4](calibration/d3d/4/replay.csv)

**OBSERVED FACT** — Yellow boxes show annotated faces, green boxes the bounds-only convergence sample. The right panel shows the depth used at the same crop. The overlays are separate files. The original videos have no recorded depth channel, so there is no historical depth image to display. [Clip 1 overlay](calibration/d3d/1/anchor.jpg) · [Clip 2](calibration/d3d/2/anchor.jpg) · [Clip 3](calibration/d3d/3/anchor.jpg) · [Clip 4](calibration/d3d/4/anchor.jpg)

**MEASURED RESULT** — All 210 ordinary unit tests passed in both debug and release. The additional opt-in replay suite also passed in both variants. Existing tests exercise signed depth, occlusion, thin edges, pinned surfaces, viewpoint order and lens restrictions. New deterministic tests exercise focus priority, invalid samples and amplitude limits. The debug APK built. [Test and asset verification](calibration/d3d/verification.json)

**NOT VERIFIED** — ADB returned no device. Real capture-time face/AF metadata, automatic near-edge face detection, front-camera mirroring, rotated live captures, actual S21 latency/memory, gallery playback after this build, low light, flash, multiple subjects and distant landscapes still require hardware validation. The four portrait replays cannot establish those cases. Actual newly revealed background detail is unknowable from a single exposure.

## Reproduce

**OBSERVED FACT** — From the repository root, run the following with NumPy, Pillow, OpenCV and imageio-ffmpeg installed. The first command reads the four originals. The Gradle init script adds a development-only JVM replay test. The last command encodes review MP4s and GIFs and measures decoded motion.

```powershell
python docs/calibration/d3d/measure.py "C:/Users/User/AppData/Roaming/Telegram Desktop"
.\gradlew.bat --offline --max-workers=1 -I docs/calibration/d3d/replay.gradle test assembleDebug
python docs/calibration/d3d/compare.py "C:/Users/User/AppData/Roaming/Telegram Desktop"
```

**OBSERVED FACT** — Production changes are in CaptureConvergence.kt, ImageProcessor.kt (D3D entry point only), DepthAnythingV2Engine.kt, ThreeDDepthEngine.kt, ParallaxWarpEngine.kt, ThreeDPhotoProcessor.kt and RetroFrameRenderer.kt. The change includes ThreeDModeTest.kt, the development-only replay and measurement scripts, both D3D reports, their evidence and the daily log. Partial viewpoint lists and ONNX results now release on failure, cancellation propagates through the D3D processor and the caller retains ownership of the pooled source.
