# CAMELEO — Capture Menu & Settings Current State Audit

## 1. Executive Summary & Audit Scope
This document records the empirical state of the **CAMELEO three-dot capture menu** and **Settings screen** across source code, state flow, UI layouts, and persistence architecture, benchmarked against real Dazz Cam reference captures (`media_1794316966893_caec5678.jpg`, `media_1794316966895_caaa21b8.jpg`, `media_1794316966891_857b5779.jpg`, `media_1794316966897_70ab1cf7.jpg`).

---

## 2. Three-Dot Capture Menu (`CameraControlMenu.kt`)

### 2.1 Hierarchy & Layout
- **Container Structure**:
  - Fullscreen transparent scrim (`Box.fillMaxSize().clickable { onDismiss() }`) capturing clicks outside.
  - Floating card anchored at `Alignment.TopEnd` with `statusBarsPadding()` and `padding(top = 52.dp, end = 20.dp)`.
  - Enter/Exit animations: `fadeIn(160ms) + scaleIn(160ms, 0.90f)` and `fadeOut(120ms) + scaleOut(120ms, 0.95f)`.
- **Dimensions**:
  - Width: `236.dp`
  - Corner radius: `30.dp`
  - Internal padding: `14.dp`
- **Colors & Styling**:
  - Background: Frosted charcoal `Color(0xD92D2D30)` (~85% alpha).
  - Border: `1.dp` solid `Color(0x28FFFFFF)` (subtle hairline translucent white border).
  - Action button backgrounds: Solid dark grey `Color(0xFF1E1E20)`.

### 2.2 Control Rows & Button Behaviors
- **Row 1 (Circular Action Toggles)**:
  1. **Flash Toggle** (`60.dp` circle, `CircleShape`):
     - Cycles: `ImageCapture.FLASH_MODE_OFF` → `FLASH_MODE_AUTO` → `FLASH_MODE_ON` → `FLASH_MODE_OFF`.
     - Icon rendering: Custom `Canvas(26.dp)` drawing a lightning bolt. When `OFF`, draws a clean diagonal slash (`⚡⃥`). When `ON`, fills with bright amber (`Color(0xFFFFCC00)`).
     - Updates `CameraManager.setFlashMode` and updates active `ImageCapture.flashMode`.
  2. **Frame Border Toggle** (`60.dp` circle, `CircleShape`):
     - Toggles `frameBorderEnabled` in `UserTuningConfig` and `captureFrameState.isVisible`.
     - Icon rendering: Custom `Canvas(26.dp)` drawing a rounded rectangle frame. When disabled, draws a clean diagonal slash across it (`⊘`).
     - State synchronization: Controls visual overlay visibility and prevents image cropping when disabled.
  3. **Assistive Grid Toggle** (`60.dp` circle, `CircleShape`):
     - Toggles `showGrid` in `CameraManager` and `AppSettingsStore.assistiveGrid`.
     - Icon rendering: Custom `Canvas(26.dp)` drawing a $3 \times 3$ grid. When disabled, draws a clean diagonal slash across it.
- **Row 2 (Zoom Mode & Settings)**:
  1. **Zoom Mode Pill** (`weight(1f)`, height `60.dp`, `RoundedCornerShape(30.dp)`):
     - Renders framing icon `[○]` with two-line typography:
       - Header: `"Zoom Mode"` (white, 13sp, bold).
       - Value: `"Frame"` or `"Lens"` (`Color(0xFF8E8E93)`, 11sp).
     - Clicking cycles between `"Frame"` (composition crop window resizing) and `"Lens"` (hardware CameraX optical sensor zoom).
  2. **Settings Gear Button** (`60.dp` circle, `CircleShape`):
     - White settings gear icon `Icons.Default.Settings` (`26.dp`).
     - Dismisses the popup menu and invokes `onNavigateToSettings()`.

---

## 3. Settings Screen (`SettingsScreen.kt`)

### 3.1 Hierarchy & Navigation
- **Top App Bar**:
  - Circular dark back button (`38.dp`, `Color(0xFF1C1C1E)`, `CircleShape`) on left with `Icons.AutoMirrored.Filled.ArrowBack`.
  - Centered title `"Settings"` (white, 17sp, bold).
  - Back action calls `onNavigateBack()` (`navController.popBackStack()`), returning directly to the viewfinder.
- **Scrollable List**: `LazyColumn` with pure black background (`Color.Black`) and `spacedBy(16.dp)`.

### 3.2 Row-by-Row Functional Audit Matrix

| Setting Row | Visual Treatment | State Source | Audit Classification | Current Behavior & Gap Analysis |
| :--- | :--- | :--- | :--- | :--- |
| **Language** | Nav row with chevron `>` | System Locale | `UI PRESENT, FUNCTIONALITY NOT IMPLEMENTED` | Displays Toast info. System language is respected. Dedicated in-app language picker is out of scope. |
| **Export to Album** | Coral toggle switch (`#E25241`) | `AppSettings.exportToAlbum` | `IMPLEMENTED` | Backed by `AppSettingsStore`. Controls whether media is saved to DCIM/Cameleo album. |
| **Keep Original Photos** | Toggle switch | `AppSettings.keepOriginalPhotos` | `IMPLEMENTED` | Backed by `AppSettingsStore`. Automatically archives un-graded raw film negative in `negatives/neg_{id}.jpg`. |
| **Save Location** | Toggle switch | `AppSettings.saveLocation` | `PARTIAL` | Preference persisted in SharedPreferences. EXIF GPS tagging requires runtime location permissions. |
| **Mirror Front Camera** | Coral toggle switch (`#E25241`) | `AppSettings.mirrorFrontCamera` | `IMPLEMENTED` | Connected to `GLViewfinderRenderer.setMirrorFrontPreview` and `ImageProcessor.processAndSaveImage(mirrorFrontCamera = ...)`. Needs complete sync in `ViewfinderScreen` capture trigger. |
| **Live Photo Cover** | Nav row with chevron `>` | N/A | `UI ONLY` | Live photo / video feature was paused in commit `74c0484` to focus 100% on still photo film emulation. |
| **ISO & Shutter Slider** | Row with label `"Segmented >"` | `ProConfig` | `IMPLEMENTED` | Displays `"Segmented"`. Full manual ISO and exposure time controls exist in `ProControlsBar.kt` with discrete stepped haptics. |
| **Assistive grid** | Toggle switch | `AppSettings.assistiveGrid` & `uiState.showGrid` | `IMPLEMENTED` | Controls $3 \times 3$ grid overlay. Needs two-way reactive binding between `CameraManager` and `AppSettingsStore`. |
| **Level** | Toggle switch | `AppSettings.level` & `uiState.showLevel` | `PARTIAL` | State persisted in SharedPreferences and `CameraState.showLevel`. Sensor orientation overlay rendering not yet connected. |
| **Shutter Vibration** | Coral toggle switch (`#E25241`) | `AppSettings.shutterVibration` | `IMPLEMENTED` | Controls tactile vibration on shutter button press in `TactileShutterButton.kt`. |
| **Timestamp Format** | Nav row with chevron `>` | `DateStampConfig` | `IMPLEMENTED` | Vintage 7-segment digital date stamps supported in `DateStampRenderer.kt`. |
| **Preserve Setting** | Nav row with chevron `>` | `AppSettingsStore` | `IMPLEMENTED` | Selected camera body, lens accessory, and tuning are preserved across app launches. |
| **Storage** | Nav row with chevron `>` | System MediaStore | `IMPLEMENTED` | Displays storage location info (`DCIM/Cameleo`). |
| **Advisory Banner** | Crimson banner (`#381A18`) with 👉 | Static info | `IMPLEMENTED` | Advises users that media is stored locally in device storage. |
| **Share / Feedback / Review** | Nav rows with intents | System Intents | `IMPLEMENTED` | Launches Android share sheet, email intent, and review confirmation. |
| **Recommended apps** | Toggle switch | `AppSettings.recommendedApps` | `IMPLEMENTED` | Persisted in `AppSettingsStore`. |
| **Privacy / Terms** | Nav rows with dialogs | In-app info | `IMPLEMENTED` | Informs user that photo processing is 100% on-device and privacy-first. |

---

## 4. State Synchronization & Architecture Gaps Identified

1. **Grid State Two-Way Synchronization**:
   - `CameraManager._uiState.value.showGrid` and `AppSettingsStore.current.assistiveGrid` had separate write paths.
   - *Fix*: `CameraManager` must collect `AppSettingsStore.state` and synchronize `showGrid`, while `toggleGrid()` must update `AppSettingsStore`.
2. **Mirror Front Camera Shutter Trigger**:
   - In `ViewfinderScreen.kt`, `mirrorFrontCamera` was passed as `uiState.lensFacing == CameraSelector.LENS_FACING_FRONT` without checking `AppSettingsStore.current.mirrorFrontCamera`.
   - *Fix*: Pass `(uiState.lensFacing == CameraSelector.LENS_FACING_FRONT) && settings.mirrorFrontCamera`.
3. **Shutter Vibration in Shutter Button**:
   - `TactileShutterButton.kt` called `view.performHapticFeedback` directly on press without checking `settings.shutterVibration`.
   - *Fix*: Check `AppSettingsStore.current.shutterVibration` before triggering haptics on shutter tap.
4. **Frame Border Toggle Non-Cropping Guarantee**:
   - When `frameBorderEnabled` is false, `createCaptureFrameSnapshot` in `CameraManager` should pass `null` so `ImageProcessor` does not perform a crop.
   - *Fix*: Pass `frameSnapshot = if (uiState.tuning.frameBorderEnabled) createCaptureFrameSnapshot(aspectRatio) else null`.
5. **Zoom Mode Integration**:
   - In `"Lens"` mode, multi-touch pinch on viewfinder must invoke `cameraManager.onPinchZoom`. In `"Frame"` mode, multi-touch pinch must invoke `cameraManager.resizeCaptureFrame`.
   - *Fix*: Connect `AppSettingsStore.current.zoomMode` to the pinch handler in `ViewfinderScreen.kt`.

