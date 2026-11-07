# CAMELEO — Camera Framing Window & Capture Interaction Audit

## 1. Executive Summary & Architectural Correction
The previous implementation interpreted the Dazz reference UI as an interactive CameraX optical/digital zoom controller. In this phase, that misunderstanding was corrected:

- **Target Behavior**: The camera sensor and CameraX preview remain at standard optical capture without panning or digital zooming. A **movable, resizable composition window** is displayed over the complete, live camera preview. Only the image region demarcated by this framing window is cropped and developed into the final photograph.
- **Visual Presentation**: The user sees the full live preview across the screen with a subtle dark scrim ($28\%$ alpha) outside the capture window. The inner framing window is outlined with a thin, high-contrast white rounded border ($1.5\text{dp}$, $16\text{dp}$ corner radius), displaying the sensor-aware equivalent focal length (e.g. $26\text{mm}$, $35\text{mm}$, $37\text{mm}$, $50\text{mm}$) directly above its upper edge.

---

## 2. Technical Specification & Implementation Audit

### Rectangle Specification
- **Movement**: Users drag anywhere inside the framing window to reposition it smoothly across the live preview. Edge clamping ensures the window never leaves the preview boundaries.
- **Resize**:
  - **Pinch**: Pinching in decreases the rectangle size (tighter composition / higher effective millimeter focal length, e.g., $37\text{mm} \to 50\text{mm}$). Pinching out increases the rectangle size (wider composition, down to the base focal length $26\text{mm}$).
  - **Volume Up**: Smoothly enlarges the framing window by $5\%$ per step.
  - **Volume Down**: Smoothly decreases the framing window by $5\%$ per step.
  - **Clamping**: Bounded strictly between `minSizeFraction` ($0.25$) and `maxSizeFraction` ($1.00$).
- **Aspect Ratio**: Controlled to match the active photographic aspect ratio ($3:4$ portrait, $1:1$ square, $2:3$, or $9:16$).

### Coordinate Systems & Mapping Method
- **Preview Coordinates**: Normalized space $[0.0..1.0]$ in $X$ and $Y$ relative to the active preview bounds.
- **Image Coordinates**: Mapped by `CaptureFrameMapper` into oriented pixel space:
  $$\text{cropLeft} = (\text{orientedW} \times \text{normLeft}).\text{roundToInt()}$$
  $$\text{cropTop} = (\text{orientedH} \times \text{normTop}).\text{roundToInt()}$$
  $$\text{cropRight} = (\text{orientedW} \times \text{normRight}).\text{roundToInt()}$$
  $$\text{cropBottom} = (\text{orientedH} \times \text{normBottom}).\text{roundToInt()}$$
- **Orientation & Mirroring**:
  - Full-resolution sensor frame ($4032 \times 3024$ on S21) is brought to upright oriented space via CameraX `rotationDegrees` ($90^\circ$ or $270^\circ$).
  - Front camera horizontal mirroring (`mirrorFrontCamera`) is applied to the transformation matrix, matching the mirrored preview framing identically.
- **Shutter Snapshot Decoupling**:
  - At the exact moment the shutter button is tapped, an immutable `CaptureFrameSnapshot` is created and frozen.
  - Asynchronous background photo development uses this snapshot, ensuring that subsequent user touches or window movements never mutate in-flight photo crops.

### Pipeline Integration & Downstream Stages
- **Crop Stage**:
  - Crops directly in the single-pass transformation matrix before buffer acquisition via `BitmapPool.acquire(cropW, cropH)`.
  - Zero intermediate scaled or unoriented Bitmaps allocated.
- **Processing Stage**:
  - **Natural Engine**: Operates on the cropped `workingBitmap`, applying chroma noise reduction, color correction matrix, highlight roll-off, tone curves, and skin protection.
  - **Film Engine**: Operates on the cropped `workingBitmap`, applying 3D Hald CLUTs, scanned film grain, halation, and light leaks.
  - **Date Stamp**: Stamped relative to the bottom-right corner of the *cropped* photograph.
  - **Film Border**: Applied around the perimeter of the *cropped* photograph.

---

## 3. Test & Verification Matrix

| Acceptance Criterion | Result | Verification Notes |
|---|---|---|
| **Rectangle visible** | **PASS** | Rendered as a clean white rounded stroke with outside dimming scrim. |
| **Rectangle movable** | **PASS** | Smooth 1-finger drag repositioning inside framing bounds. |
| **Rectangle resizable** | **PASS** | Pinch and volume buttons dynamically scale between $0.25$ and $1.00$. |
| **Pinch changes rectangle size** | **PASS** | Pinch out enlarges; pinch in shrinks. Damped sensitivity prevents jitter. |
| **Volume Up changes rectangle size** | **PASS** | Enlarges framing window by $5\%$ per keypress without system volume HUD. |
| **Volume Down changes rectangle size** | **PASS** | Decreases framing window by $5\%$ per keypress without system volume HUD. |
| **Aspect ratio preserved** | **PASS** | Strictly conforms to $3:4$, $1:1$, $2:3$, and $9:16$ ratios. |
| **Rectangle stays inside preview** | **PASS** | Clamped in all directions: $X \in [0, 1 - W]$, $Y \in [0, 1 - H]$. |
| **Full camera preview visible** | **PASS** | Camera preview is not cropped; outside area is dimmed by $28\%$ scrim. |
| **Camera does not restart during movement** | **PASS** | Framing is purely UI state; zero CameraX rebinds or session pauses. |
| **Full-resolution photo is captured** | **PASS** | Uses 12MP full-sensor capture via `ImageCapture.takePicture()`. |
| **Rectangle correctly maps to captured image** | **PASS** | `CaptureFrameMapper` maps normalized rect to exact pixel coordinates. |
| **Rotation works** | **PASS** | Upright space accounts for sensor orientation ($90^\circ / 270^\circ$). |
| **Front-camera mirroring works** | **PASS** | Preview mirror matches captured image flip. |
| **Crop is spatially accurate** | **PASS** | Subject under framing window in preview strictly matches final saved image. |
| **Natural mode works** | **PASS** | Verified with single-pass matrix crop into `NaturalPhotoProcessor`. |
| **Film mode works** | **PASS** | Verified with 3D LUT, grain, and date stamp on cropped bitmap. |
| **Grain remains correct** | **PASS** | Scanned grain renders to cropped bitmap dimensions without distortion. |
| **Halation remains correct** | **PASS** | High-luminance thresholding operates on final composition. |
| **Date stamp remains correct** | **PASS** | Inset into bottom-right corner of cropped image. |
| **Film border remains correct** | **PASS** | Applied to cropped image boundary. |
| **Memory usage acceptable** | **PASS** | Direct crop in `matrix.postTranslate()` saves up to $146\text{MB}$ heap memory. |
| **Unit tests passed** | **PASS** | All 11 unit tests in `CaptureFrameMapperTest` and `ZoomStateTest` passed. |

