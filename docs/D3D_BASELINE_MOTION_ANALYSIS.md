# D3D baseline motion analysis

**MEASURED RESULT** — All four supplied Telegram MP4s decode at 800 × 1072, 10 fps, 24 frames and 2.4 seconds. Presentation timestamps are 100 ms apart. Matching every decoded frame against the first four recovers 0, 1, 2, 3, 2, 1 repeated four times. The loop boundary advances one normal step. [Metadata, original file hashes, regions and tracks](calibration/d3d/originals.json)

**MEASURED RESULT** — Movement between frame 0 and frame 3 in native pixels. Each pair gives the original followed by the geometry replay. Positive displacement is rightward. Positive eye-spacing change means the tracked eyes moved farther apart.

| Original clip | Eye/nose region Δx | Background Δx | Interocular distance change |
|---|---:|---:|---:|
| 01-14-35 | −2.20 → −1.19 | −26.46 → −8.27 | +3.30 → +1.60 |
| 01-14-41 | −0.04 → −1.72 | −20.09 → −8.05 | −0.13 → +0.34 |
| 01-14-48 | +33.51 → −2.29 | +1.47 → −11.40 | −2.56 → −0.53 |
| 13-06-40 | +14.11 → −0.01 | −9.82 → −8.28 | +15.64 → +5.27 |

**OBSERVED FACT** — Up to 120 texture corners were tracked per fixed region using pyramidal Lucas–Kanade flow. Tracks with forward/backward error above 0.8 px were discarded. Four visually annotated landmarks per clip provide an independent measure of eye and nose-to-mouth spacing. Positions, counts, spreads and both components of motion are included. Tracking closed eyes or a blurred face introduces uncertainty. [Replay tracks](calibration/d3d/replays.json) · [Method](calibration/d3d/measure.py)

**MEASURED RESULT** — The large bridge movements in clips 3 and 4 reproduce the earlier observations. The original full-face median in clip 4 was only +0.13 px, with a horizontal interquartile spread of about 23 px. A stationary whole-face median can hide distortion. Clip 2 already held the bridge very well. Its textured background moved about 20 px in this region. The earlier 5 px estimate cannot be generalized to the entire background.

**OBSERVED FACT** — The replay starts with frame 0 of each supplied clip. The real Kotlin depth and warping code operates on its decoded 800 × 1072 pixels using manually annotated face bounds. Existing colour and grain in the input are retained. No second photographic grade is applied. The host encodes the views as H.264 at the original cadence. The baseline hashes identify the four source files in the user-supplied Telegram folder.

**NOT VERIFIED** — Original shutter inputs, historical depth maps, focus regions and capture-time face rectangles are absent from the videos. The replay cannot recover those data. The depth maps are new inferences on the decoded originals. The annotated replays validate geometry given a face location. They do not establish automatic face detection rates.

[All four scenes side by side](calibration/d3d/comparison.mp4) · [Clip 1 GIF](calibration/d3d/1/comparison.gif) · [Clip 2 GIF](calibration/d3d/2/comparison.gif) · [Clip 3 GIF](calibration/d3d/3/comparison.gif) · [Clip 4 GIF](calibration/d3d/4/comparison.gif)
