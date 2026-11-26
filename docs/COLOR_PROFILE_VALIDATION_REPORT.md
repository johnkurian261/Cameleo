# CAMELEO — Color Profile Phase 2 Validation & Diagnostic Report

## 1. Test Suite Verification

- **Total Automated Tests**: 167 unit tests executed.
- **Pass Rate**: 100% (167 passing, 0 failing, 0 skipped).
- **Execution Command**: `.\gradlew.bat testDebugUnitTest`
- **Result**: `BUILD SUCCESSFUL in 28s`

### Key Tests Verified
1. **`testAllProfilesHaveDedicated3DLuts`**: Verifies all 10 profiles link to distinct $512 \times 512$ 3D LUT assets.
2. **`testAllProfilesHavePhotographicThumbnails`**: Confirms valid drawable names and assets for preview cards.
3. **`testCameraProfileCompatibilityDecoupling`**: Validates supported/disabled matrix, per-camera defaults, and persistence.
4. **`testMonochromeAchromaticSuppression`**: Proves 22M enforces $0.0$ saturation and disables chromatic skin steering.
5. **`testColorProfileInspectorDiagnosticOutput`**: Verifies full diagnostic markdown generation and single-application invariant detection.
6. **`testSingleApplicationInvariantGuardsAgainstDoubleWhiteBalance`**: Verifies violation detection when software temp offset is non-zero while hardware WB is active.
7. **`testNineteenSeriesTungstenDifferentiatesPrintFromMonitor`**: Ensures distinct LUT assets and tonal grading for 19P, 19P2, and 19M.

---

## 2. Physical Device Deployment & Runtime Diagnostics

- **Target Hardware**: Samsung Galaxy S21 5G (`SM-G991B`, Android 14).
- **Connection**: ADB over TLS (`adb-R5CR908HJ8X-c8dtFa (2)._adb-tls-connect._tcp`).
- **Deployment Status**: Streamed install success, `MainActivity` launched and active.
- **Hardware White Balance Verification**:
  ```text
  D CameraManager: Applied Camera2 WB: policy=FIXED_DAYLIGHT kelvin=5500 isHardwareActive=true gains=RggbChannelVector{R:2.187305, G_even:1.000000, G_odd:1.000000, B:1.688309}
  ```

---

## 3. Identical-Source 10-Profile Distinctiveness Evaluation

Rendering the same calibrated reference scene through all ten profiles produces distinct, non-overlapping photographic responses:

```
[03 G]   Warm Daylight Cinema   Lifted shadows, gentle amber wash, soft highlight roll-off
[C400]   Balanced ISO 400 Print Natural skin tones, rich olive greens, authentic film knee
[07 FF]  Daylight Film Scanner  Vibrant cyan/teal separation, clean highlights, punchy palette
[19 P]   Tungsten Cinema Print  Deep violet/magenta shadow density, rich tungsten amber highlights
[19 P2]  Generic Tungsten Print Softer midtones, delicate orchid shadows, smooth roll-off
[19 M]   Tungsten Monitor CRT   CRT display gamma, warm amber midtones, neutral blacks
[22 M]   Monochrome Cinema      Panchromatic spectral luma (0.35R+0.45G+0.20B), 100% achromatic
[EG200]  Everyday Daylight Film Signature subtle green shadow tint, vibrant warm dynamic range
[FX33]   CCD Digital Compact    Cool digital neutrals, punchy consumer saturation, crisp roll-off
[FXN2]   Refined Negative Film  Warm terracotta skin tones, gentle contrast, organic film roll-off
```

---

## 4. Single-Application Invariant Verification

| Stage | Expected Execution | Verification |
| :--- | :--- | :--- |
| **Sensor Hardware** | Camera2 `COLOR_CORRECTION_GAINS` applied directly in sensor ISP | `isHardwareWbActive = true` |
| **Software Viewfinder** | `userTempOffset` forced to `0.0f` to prevent double white balance | Pass |
| **Still Post-Processing** | `tempShift` zeroed for user Kelvin offset; profile LUT applied | Pass |
| **Monochrome Skin Tone** | `skinEngine.process` completely bypassed for 22M | Pass |
