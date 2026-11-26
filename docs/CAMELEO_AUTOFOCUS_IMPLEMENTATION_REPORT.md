# CAMELEO — Camera Autofocus Intelligence Implementation Report

## 1. Architectural Overview

To resolve camera focusing instability, hunting, and optical discrepancies across all CAMELEO camera profiles, a hardware-aware autofocus subsystem was engineered and integrated.

```
┌────────────────────────────────────────────────────────┐
│               Viewfinder UI & Gestures                 │
│  - Tap-to-Focus (Short Tap < 450ms)                    │
│  - AE/AF Lock (Long Press >= 500ms)                    │
│  - Dynamic Focus Box (Green=Locked, Gold=AE/AF Lock)   │
│  - Developer Focus Inspector Overlay Toggle ('AF' btn) │
└───────────────────────────┬────────────────────────────┘
                            │
                            ▼
┌────────────────────────────────────────────────────────┐
│             AutofocusController.kt                     │
│  - Aspect-Ratio Letterbox Projection [0.0, 1.0]        │
│  - Real-time Telemetry StateFlow<FocusTelemetry>       │
│  - Fixed-Focus Fallback (S21 13mm Ultrawide)           │
│  - Shutter Focus Settling Coordination (700ms timeout) │
│  - Diagnostic Circular Event Log Buffer (40 entries)   │
└───────────────────────────┬────────────────────────────┘
                            │
                            ▼
┌────────────────────────────────────────────────────────┐
│            CameraX / Camera2 Interop                   │
│  - CONTROL_AF_MODE_CONTINUOUS_PICTURE (Baseline)       │
│  - FocusMeteringAction (FLAG_AF | FLAG_AE)             │
│  - Ingestion of LENS_FOCUS_DISTANCE & LENS_STATE       │
└────────────────────────────────────────────────────────┘
```

---

## 2. Implemented Components

### 2.1 `AutofocusController.kt`
- **Location**: `app/src/main/java/com/craftcam/app/camera/focus/AutofocusController.kt`
- **Capabilities**:
  - `AutofocusStatus`: Maps Camera2 raw states (`INACTIVE`, `PASSIVE_SCAN`, `PASSIVE_FOCUSED`, `ACTIVE_SCAN`, `FOCUSED_LOCKED`, `NOT_FOCUSED_LOCKED`, `FIXED_FOCUS`).
  - `calculateNormalizedPoint()`: Converts screen touch coordinates into sensor array coordinates $[0, 1] \times [0, 1]$, mathematically compensating for 4:3 letterboxing on tall displays.
  - `coordinateShutterWithFocus()`: Coroutine-based shutter release gate that suspends until `isFocused && !isLensMoving`, with a hard $700\text{ms}$ timeout.
  - `triggerTapToFocus()` & `lockFocusAndExposure()`: Manages CameraX `startFocusAndMetering` and auto-cancel lifecycles.

### 2.2 Viewfinder & UI Enhancements
- **Location**: `ViewfinderOverlay.kt`, `ViewfinderScreen.kt`, `CameraControlMenu.kt`
- **Capabilities**:
  - Exact container width and height propagation from `pointerInput`.
  - Dynamic focus ring states: pulsing amber while scanning, bright green on focus achieved, gold badge with "AE/AF LOCK" on long-press.
  - Quick-toggle `AF` button in Row 2 of the Three-Dot Camera Control Menu.

### 2.3 Focus Inspector Diagnostic Overlay
- **Location**: `FocusInspectorOverlay.kt`
- **Capabilities**:
  - Live readout of current AF state, lens diopters ($D$), estimated physical distance ($m$), lens movement status, and convergence latency ($ms$).
  - Circular timeline showing recent state transitions and events.

---

## 3. Automated Verification & Test Coverage

All 151 unit tests passed with 100% success (`.\gradlew.bat testDebugUnitTest`):
- `AutofocusIntelligenceTest.kt`:
  - `afStatus_mapsCorrectlyFromCamera2Values`: Passed
  - `fixedFocusLens_overridesToFixedFocusStatus`: Passed
  - `calculateNormalizedPoint_centersAccuratelyInLetterboxedViewfinder`: Passed
  - `calculateNormalizedPoint_clampsOutOfBoundsTaps`: Passed
  - `onCameraCapabilitiesChanged_detectsFixedFocusUltrawide`: Passed
  - `onCameraCapabilitiesChanged_detectsFullAutofocusMainLens`: Passed
  - `coordinateShutterWithFocus_readyImmediatelyOnFixedFocus`: Passed
  - `diagnosticReport_containsAccurateMetricsAndLogEntries`: Passed
