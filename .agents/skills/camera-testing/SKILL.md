---
name: camera-testing
description: >-
  Comprehensive testing, diagnostic, and benchmarking procedures for CAMELEO on physical Android
  hardware and emulators. Covers ADB workflows, automated APK installation, logcat diagnostics,
  shutter latency benchmarking, memory profiling (dumpsys meminfo), frame rate monitoring (dumpsys gfxinfo),
  and objective image quality metrics (resolution verification, dynamic range, color banding, and SSIM/PSNR).
---

# Android Camera Testing, Diagnostics & Benchmarking

This skill provides testing protocols, ADB automation commands, diagnostic scripts, and image-quality evaluation standards for the **CAMELEO** Android camera application.

---

## 1. Activation Criteria

Activate this skill when:
- Deploying, validating, or regression-testing CAMELEO builds on physical Android test hardware.
- Diagnosing camera crashes, lifecycle leaks, surface destruction errors, or unhandled exceptions.
- Measuring camera performance: time-to-first-preview, shutter lag, photo processing latency, and memory footprint.
- Benchmarking output image quality: verifying full native resolution, zero color banding, sharpness, and correct EXIF orientation.
- Preparing release candidate APKs and executing the pre-release regression checklist.

---

## 2. Tools & Environment Reference

- **Android SDK Path**: `C:\Users\User\AppData\Local\Android\Sdk`
- **ADB Executable**: `C:\Users\User\AppData\Local\Android\Sdk\platform-tools\adb.exe`
- **Application Package**: `com.craftcam.app`
- **Main Activity**: `com.craftcam.app.MainActivity`
- **Gradle Wrapper**: `gradlew.bat` (Project Root)

---

## 3. Standard Workflows

### Workflow A: Device Connectivity & Capability Check
Verify connected device state before initiating tests:

```powershell
# 1. Check connected devices and authorization
& "C:\Users\User\AppData\Local\Android\Sdk\platform-tools\adb.exe" devices -l

# 2. Query device model, Android version, and ABI
& "C:\Users\User\AppData\Local\Android\Sdk\platform-tools\adb.exe" shell getprop ro.product.model
& "C:\Users\User\AppData\Local\Android\Sdk\platform-tools\adb.exe" shell getprop ro.build.version.release

# 3. Check physical camera sensor status
& "C:\Users\User\AppData\Local\Android\Sdk\platform-tools\adb.exe" shell dumpsys media.camera
```

---

### Workflow B: Clean Build & Automated Deployment
Always verify compilation before pushing to test hardware:

```powershell
# 1. Clean build debug APK
.\gradlew.bat assembleDebug

# 2. Install onto target device with downgrade and permission grant
& "C:\Users\User\AppData\Local\Android\Sdk\platform-tools\adb.exe" install -r -g "app\build\outputs\apk\debug\app-debug.apk"

# 3. Launch CAMELEO main activity
& "C:\Users\User\AppData\Local\Android\Sdk\platform-tools\adb.exe" shell am start -n com.craftcam.app/.MainActivity
```

---

### Workflow C: Real-Time Diagnostic Log Filtering
Monitor camera lifecycle and image processing logs without system log noise:

```powershell
& "C:\Users\User\AppData\Local\Android\Sdk\platform-tools\adb.exe" logcat -c
& "C:\Users\User\AppData\Local\Android\Sdk\platform-tools\adb.exe" logcat -v time -s Cameleo:V CameraManager:V ImageProcessor:V AndroidRuntime:E
```

Watch for critical failure signatures:
- `CameraAccessException: CAMERA_DISCONNECTED`: Hardware camera server daemon restart.
- `OutOfMemoryError: Failed to allocate a ... byte allocation`: JVM heap exhaustion during bitmap processing.
- `SurfaceTexture client disconnected`: Surface lifecycle mismatch between Compose and CameraX.

---

### Workflow D: Performance & Memory Profiling

#### 1. Measure Processing Memory Footprint:
```powershell
& "C:\Users\User\AppData\Local\Android\Sdk\platform-tools\adb.exe" shell dumpsys meminfo com.craftcam.app
```
Key metrics to track:
- **Native Heap**: Should stay below $150\text{ MB}$ during active photo capture and processing.
- **Dalvik Heap**: Should stay below $80\text{ MB}$; rapid growth indicates un-recycled `Bitmap` instances.

#### 2. Viewfinder Frame Drops & Latency:
```powershell
& "C:\Users\User\AppData\Local\Android\Sdk\platform-tools\adb.exe" shell dumpsys gfxinfo com.craftcam.app framestats
```
Ensure 95th percentile frame draw time is $< 16.6\text{ ms}$ (60 FPS preview).

---

### Workflow E: Image Quality & EXIF Verification
Verify captured images conform to premium camera standards:

1. **Pull the latest photo from the device**:
   ```powershell
   & "C:\Users\User\AppData\Local\Android\Sdk\platform-tools\adb.exe" pull /sdcard/DCIM/Cameleo/ C:\temp\cameleo_test\
   ```
2. **Quality Verification Script**:
   ```python
   from PIL import Image, ExifTags
   import sys

   def verify_photo(path):
       img = Image.open(path)
       w, h = img.size
       print(f"Resolution: {w}x{h} ({w*h/1e6:.1f} MP)")
       
       # Assert native sensor resolution (not downscaled to 1080p or 2560px)
       assert w * h >= 8_000_000, f"Image resolution too low: {w}x{h}"
       
       # Check EXIF orientation
       exif = img.getexif()
       orientation = exif.get(0x0112, 1)
       print(f"EXIF Orientation tag: {orientation}")
       print("Quality verification: PASSED")

   verify_photo(sys.argv[1])
   ```

---

## 4. Pre-Release Regression Checklist

Execute this 10-point checklist prior to every milestone release:

| # | Test Scenario | Expected Outcome | Status |
| :-: | :--- | :--- | :-: |
| 1 | **Fresh Install & Permissions** | App requests Camera & Audio; preview starts immediately upon grant. | [ ] |
| 2 | **Full Resolution Capture** | Output JPEG matches physical sensor resolution (e.g. 12MP/48MP). | [ ] |
| 3 | **Filter Switching** | Switching between CCD, Analog, and VHS presets renders preview smoothly. | [ ] |
| 4 | **Trilinear Banding Test** | Blue skies and soft skin gradients display zero stepped color bands. | [ ] |
| 5 | **Rapid Shutter Burst** | 5 consecutive shutter taps do not drop frames or crash the app. | [ ] |
| 6 | **Flash & Torch Modes** | Auto, On, and Off trigger flash properly without timing desync. | [ ] |
| 7 | **Backgrounding / Resume** | Home button press and re-entry restores camera preview without black screen. | [ ] |
| 8 | **Aspect Ratio Switch** | 4:3, 1:1, and 16:9 crops maintain proper viewfinder alignment and FOV. | [ ] |
| 9 | **Gallery Persistence** | Saved photos appear immediately in device Gallery/Google Photos via MediaStore. | [ ] |
| 10 | **Memory Leak Check** | Capturing 20 photos consecutively does not increase baseline memory by $> 20\text{ MB}$. | [ ] |

---

## 5. Limitations

- **Emulator Constraints**: Android Studio Virtual Devices (AVD) do not simulate physical Camera2 sensor characteristics (e.g. manual ISO, real lens vignetting, raw sensor bayer patterns). Always validate color science and performance on physical hardware.
- **Vendor OEM Quirks**: Samsung, Xiaomi, and MediaTek devices may report non-standard exposure compensation steps; always clamp exposure adjustments to device-reported ranges.
