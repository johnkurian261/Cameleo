# D3D hybrid motion restoration

**Selected configuration F** keeps the captured focus/face convergence and the existing adaptive baseline near the subject. It restores part of the historical swing in reliably distant layers. Across the four source portraits, face-to-background separation rose 69–93% over the clean replay. The full historical amplitude produced more edge exposure and larger changes in facial proportions.

[Three-way comparison video](calibration/d3d_hybrid_motion/comparison.mp4) · [First scene animated](calibration/d3d_hybrid_motion/comparison.gif) · [Per-scene tracks and timings](calibration/d3d_hybrid_motion/measurements.json) · [Occlusion counts](calibration/d3d_hybrid_motion/occlusion.csv)

## History and measurements

The direct parent of `f7e0a45` used a fixed 3.2%-of-width baseline (25.6 px at 800 px). The correction multiplied that budget by scene-span, face-area and depth-confidence factors, then applied an inner-face relief cap. On these portraits the resulting baseline was 8.38, 9.02, 10.36 and 10.61 px. The reduction applied to the entire image, including distant walls. The captured focus and robust convergence changed which surface stays stationary. They were a secondary contributor on these four controlled inputs. A face detection miss during original capture could produce a much larger pivot error.

The normalized relative-depth mapping, four lens offsets, depth-guided resampling, foreground visibility ordering, gap filling and export cadence did not change in the correction. The correction added invalid-depth and resource-lifetime handling. The CameraManager, catalog, GL viewfinder, GPU video exporter and model asset were outside that commit's diff. No evidence here points to a timing or depth-model replacement as the cause.

The newer WhatsApp clip decodes to 478 × 642, 24 frames at 10 fps, 2.4 seconds, repeating 0–1–2–3–2–1. At width 400, dense Farnebäck mean adjacent displacement was **0.558 and 0.562 px** over the first and second six-step cycles, including the transition back to view 0. Individual steps ranged from 0.530 to 0.606 px. This independently supports the low end of the preliminary estimate. Compression, resizing, scene content and flow settings affect the numbers. The WhatsApp scene has no matched pre-export frame. Original Telegram videos were measured separately at native resolution. The available fourth clip is dated November 27.

## Controlled comparison

Each row below uses the same decoded 800 × 1072 first frame, annotated face bounds, inferred Depth Anything V2 map and production Kotlin warp for each candidate. Original shutter inputs and sensor-time focus coordinates remain unavailable. Original encoded footage appears in the video's left panel for context. A reconstructs the historical geometry using the former face-median pivot and baseline. The earlier and clean implementations used the same relative-disparity projection, so D isolates the historical pivot with the clean baseline. The replay retains the baked photographic grade across every view.

| Configuration / bridge-to-background peak separation (px) | Portrait 1 | Portrait 2 | Portrait 3 | Portrait 4 |
|---|---:|---:|---:|---:|
| A Historical geometry | 22.00 | 20.63 | 22.15 | 20.12 |
| B Clean, 1.00× | 7.16 | 6.28 | 9.07 | 8.10 |
| C Historical baseline, clean pivot | 22.12 | 19.57 | 22.09 | 19.98 |
| D Historical pivot, clean baseline | 7.11 | 6.21 | 9.05 | 8.13 |
| Global 1.25× | 8.96 | 7.84 | 11.06 | 10.27 |
| E Global 1.50× | 10.78 | 9.51 | 13.46 | 12.38 |
| Global 1.75× | 12.60 | 11.14 | 15.71 | 14.35 |
| Global 2.00× | 14.43 | 12.45 | 17.91 | 16.42 |
| **F Hybrid** | **13.44** | **12.12** | **17.53** | **13.71** |

Peak separation is the absolute difference of median horizontal feature tracks on the facial bridge and background from view 0 to 3. Dividing by 800 gives 1.52–2.19% of image width for F. Background median vertical drift stayed within 0.03 px. Forward/backward tracking, per-region spread, landmark acceptance and adjacent dense flow are in the measurements. The fourth portrait retained the largest distortion. Its measured interocular span changed by 5.22 px under B, 6.16 px under F, 11.07 px at global 2× and 13.39 px with the historical baseline.

The hybrid uses a smooth increase in displacement behind the convergence plane, beginning at a normalized disparity difference of 0.18 and reaching full strength at 0.58. The gain is attenuated by subject-to-background separation and limited to 0.75 on inferred depth. The heuristic fallback gets zero extra gain. Near the pivot, the original projection retains nose, eye, ear and shoulder relief. This is relative monocular depth. The thresholds do not encode physical distances. Face scale and inner-face relief still limit the baseline. No optical zoom, rotation, vertical displacement, dynamic crop or frame interpolation was introduced.

## Boundaries and timing

The maximum fraction of interior pixels requiring fill in any one view rose from 0.089% under B to 0.268% under F, versus 0.416% under C. Longest interior gaps were 4, 8 and 13 px respectively. Border fill peaked at 0.50%, 0.88% and 1.47%. All replay output pixels were opaque. These counts quantify newly exposed texture that must be estimated. They cannot guarantee accuracy behind hair, a hand, or an occluded edge. The hybrid uses the existing depth-aware fill and edge sampling. The production renderer has no access to a second physical viewpoint.

The four symmetric positions and six-view loop retain historical 100 ms steps. Identical selected views were also exported at [125 ms](calibration/d3d_hybrid_motion/timing-125ms.mp4) and [150 ms](calibration/d3d_hybrid_motion/timing-150ms.mp4) alongside [100 ms](calibration/d3d_hybrid_motion/timing-100ms.mp4). The slower intervals extend each loop from 0.60 s to 0.75 or 0.90 s with identical spatial depth. The default uses the cadence in both reference generations. The last frame is the inner view, one ordinary step from the first.

## Verification and scope

The production diff covers only `ParallaxWarpEngine.kt` and the corresponding debug displacement calculation in `ThreeDPhotoProcessor.kt`. A D3D unit regression checks convergence, preserved local facial relief, distant-layer gain, shallow scenes and the heuristic fallback. The existing visibility, fisheye restriction and viewpoint sequencing tests ran in both variants. No camera profile, LUT, filter, model, artwork or shared renderer is part of the D3D checkpoint. The four-scene host replay passed, the full suite passed 220 tests in debug and 220 in release, and the debug APK built.

To reproduce, use the original audit's `docs/calibration/d3d/measure.py` to unpack the Telegram source frames, run the Gradle test with `-I docs/calibration/d3d_hybrid_motion/replay.gradle :app:testDebugUnitTest --tests *HybridReplayTest`, then run this audit's `measure.py` with the WhatsApp path and Telegram folder. The replay writes candidate views and edge counts under `app/build/d3d-hybrid`.

**Physical Galaxy S21 verification — NOT VERIFIED.** ADB returned an empty device list. Fresh captures, automatic subject selection, speed on the phone, and group, low-light, outdoor and flash behavior still need physical validation.
