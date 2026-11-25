# Reverse Engineering Analysis (REA) Report: Dazz Cam Color Temperature & White Balance Architecture

## 1. Executive Summary

This report provides a formal, evidence-based reverse engineering investigation into how color temperature, white balance (AWB), and illuminant adjustments are handled in the official **Dazz Cam** iOS application (`Payload/DazzCamera.app`), its core image filtering framework (`DZImageFilter.framework`), its localization resources, and its compiled Metal shaders.

### Key Finding
**There is NO dynamic, scene-adaptive automatic temperature adjustment running in software or filter post-processing in Dazz Cam.**
In Dazz Cam:
1. The user temperature control is purely a **manual color temperature slider** (ranging from cool blue ~2500K to warm amber ~9500K, centered at daylight 5500K).
2. Tapping the centered Kelvin readout or double-tapping the slider track resets the temperature to the camera's neutral 5500K baseline.
3. There is **no "Auto" mode toggle** on the temperature control. In fact, Dazz Cam's internal localizations explicitly state:
   > `"Auto Color Temperature is not recommended for this camera."`
4. The vintage film stock or digicam look (such as CPM35, CCD, D FUNS, D3D) is **static and calibrated** via 3D LUTs and fixed tone curves. The camera profile does not dynamically inspect image pixels to guess illuminants or shift RGB channel gains per frame.

---

## 2. Reverse Engineering Evidence & Observed Facts

Strictly distinguishing `[OBSERVED FACT]` from `[HYPOTHESIS]` per reverse engineering disciplines:

### 2.1 Localization Evidence (`Localizable.strings`)

Inspecting `Payload/DazzCamera.app/en.lproj/Localizable.strings` and `Base.lproj/Localizable.strings` reveals the exact strings and design intent of Dazz Cam's temperature architecture:

```strings
/* [OBSERVED FACT] */
"Color_Temperature" = "Color Temperature";
"Color_Temperature_Summary" = "Preserve the last used color temperature, rather than automatically reset.";
"Color_temperature_adjustment" = "Color temperature adjustment";
"Temperature" = "Temperature";
"Temperature_is_not_recommended" = "Auto Color Temperature is not recommended for this camera.";
"LOCK_WHITE_BALANCE_OPTION" = "Lock the white balance during recording.";
"Reset_Color" = "Reset Color";
```

#### Analytical Interpretation:
- `Temperature_is_not_recommended`: Dazz Cam explicitly discourages or disables automatic color temperature for vintage camera models because aggressive auto white balance algorithms continuously neutralize authentic warm amber, tungsten, and golden casts, destroying the vintage aesthetic.
- `Color_Temperature_Summary`: Explains the preference to remember the user's manual temperature adjustment rather than resetting it automatically when switching cameras.
- `LOCK_WHITE_BALANCE_OPTION`: Demonstrates that Dazz Cam locks white balance rather than allowing it to drift dynamically during recording.

---

### 2.2 Framework Evidence (`DZImageFilter.framework`)

Inspecting `Payload/DazzCamera.app/Frameworks/DZImageFilter.framework/DZImageFilter`:

```objc
/* [OBSERVED FACT] */
+[LUTAction actionWithLUT3DByConvertingColorTemperatureFromSourceColorSpace:sourceTransferFunction:sourceColorTemperature:destinationColorTemperature:]
+[LUTColorSpace convertColorTemperatureFromLUT3D:sourceColorSpace:sourceTransferFunction:sourceColorTemperature:destinationColorTemperature:]
+[LUTColorSpaceWhitePoint knownColorTemperatureWhitePoints]
+[LUTColorSpaceWhitePoint whitePointFromColorTemperature:]
+[LUTColorSpaceWhitePoint whitePointFromColorTemperature:customName:]
-[LUT colorAtInterpolatedR:g:b:]
-[LUT3D colorAtInterpolatedR:g:b:]
```

#### Analytical Interpretation:
- In `DZImageFilter`, color temperature conversion exists solely in **offline 3D LUT transformation** (converting a 3D LUT from a source white point/color temperature to a destination white point).
- There are **zero** symbols, classes, or routines in `DZImageFilter` that perform real-time, frame-by-frame scene illuminant estimation or automatic temperature shifting based on pixel content.

---

### 2.3 Main Binary Evidence (`DazzCamera`)

Inspecting unencrypted Mach-O metadata and Swift/Objective-C selectors in `Payload/DazzCamera.app/DazzCamera`:

```swift
/* [OBSERVED FACT] */
whiteBalanceTemperatureSfv                              // Getter for manual white balance temperature (Float)
adjustWhiteBalanceGainsyySo09AVCapturefgH0aF            // Method to apply manual AVCaptureWhiteBalanceGains
6AVCaptureWhiteBalanceGainsa10DazzCameraE9normalizeyABSo0A6DeviceCF // Extension normalizer for hardware gains
isWhiteBalanceLockSupportedSbv                          // Device capability check for locking WB
2focusExposeAndAdjustWhiteBalance15atAdjustedPointySo7CGPointV_tF // Tap-to-focus/expose/WB point of interest
LOCK_WHITE_BALANCE_OPTION                               // Lock WB setting
```

#### Analytical Interpretation:
- Dazz Cam interacts with the physical camera hardware (`AVCaptureDevice`) using either locked white balance or user-driven manual Kelvin offsets.
- When the user taps the viewfinder screen, it adjusts focus, exposure, and baseline point of interest (`focusExposeAndAdjustWhiteBalance atAdjustedPoint`).
- The temperature slider writes directly to `whiteBalanceTemperature` or manual gains. It does not engage an autonomous software loop that attempts to auto-adjust Kelvin on every captured frame.

---

### 2.4 Metal Shader Libraries (`*.metallib`)

Inspecting compiled shader functions in `filters.ci.metallib`, `HSL.ci.metallib`, and `Tone.ci.metallib`:

```metal
/* [OBSERVED FACT] */
inputLUT
rgbToneCurve
cmykToneCurve
selectiveColor
highlightRolloff
rawExposure
vibrance
```

#### Analytical Interpretation:
- The GPU filter pipeline applies static 3D LUT lookups, polynomial RGB/CMYK tone curves, selective color offsets, and highlight rolloff.
- There are no shader passes performing histogram-based illuminant classification or automatic channel gain shifting.

---

## 3. Structural Comparison: Dazz Cam vs CAMELEO

| Dimension | Authentic Dazz Cam | Previous CAMELEO (Issue) | Corrected CAMELEO |
| :--- | :--- | :--- | :--- |
| **Temperature UI Control** | Dedicated manual Kelvin slider with centered readout (`5500K`). | Added `[ A ]` / Auto toggle to the Kelvin readout that switched into auto AWB. | Manual Kelvin slider with centered readout (`5500K`). Tapping resets to 5500K. |
| **Auto Temperature Mode** | **None.** ("Auto Color Temperature is not recommended for this camera"). | Supported `isAuto = true`, enabling dynamic Camera2 AWB hunting. | `isAuto = false`. Default baseline 5500K daylight. |
| **Reset Interaction** | Single tap on Kelvin readout or double tap on slider track resets to 5500K. | Tapping toggled `isAuto`, switching between auto and manual. | Single tap on Kelvin readout or double tap on track resets directly to 5500K. |
| **Pipeline Post-Processing** | Fixed camera profile LUT + tone curve. Ambient light naturally preserved. | `DigicamColorEngine` guessed illuminant and applied dynamic partial WB gains per frame. | Authentic fixed camera profile without dynamic software temperature shifting. |
| **Camera Hardware WB** | Fixed / locked baseline gains; user slider offsets manual gains. | Native AWB allowed to hunt on every frame, neutralizing warm indoor/candlelight. | Hardware WB locked to daylight baseline (5500K) or manual slider setting. |

---

## 4. Architectural Corrections Implemented in CAMELEO

### 4.1 UI Alignment (`CameraExposureTemperatureControls.kt`)
- `TemperatureState` updated: `isAuto` defaults to `false`. Added `isDefault: Boolean get() = kelvin == 5500`.
- In `ExpandedTemperaturePanel`: Removed the auto toggle. Tapping the bold centered Kelvin readout (`5500K`) now immediately invokes `onReset()` to return to 5500K neutral daylight baseline.
- Retained the exact Dazz Cam 11-stop color gradient track, dotted center line, and black/white circular thumb with continuous 1K adjustment and double-tap reset.

### 4.2 Viewfinder Integration (`ViewfinderScreen.kt`)
- Updated `CameraExposureTemperatureControls` invocation: passes `isAuto = false`.
- `onTemperatureReset` calls `cameraManager.setKelvin(5500)`.
- `CameraInfoPills` tuning indicator: activates when `uiState.kelvin != 5500` or manual user tuning offsets are present.

### 4.3 Camera Manager (`CameraManager.kt`)
- Added `resetKelvin()` to explicitly reset temperature to 5500K.
- Ensured `applyCamera2Wb(isAuto = false, kelvin = clamped)` locks manual white balance gains on hardware when supported, preventing phone ISP auto-temperature hunting from washing out vintage tones.

### 4.4 Automated Unit Tests (`CaptureMenuAndSettingsAuditTest.kt`)
- Updated `testTemperatureAndAwbStateMachine`: validates default 5500K state, manual Kelvin adjustment bounded by [2500K, 9500K], and direct reset to 5500K baseline.
