# dazzz — Vintage Analog Camera for Android

[![Build & Release](https://github.com/johnkurian261/Cameleo/actions/workflows/build_and_release.yml/badge.svg)](https://github.com/johnkurian261/Cameleo/actions/workflows/build_and_release.yml)
[![Latest Release](https://img.shields.io/github/v/release/johnkurian261/Cameleo?color=FF8C1A&label=Download%20APK)](https://github.com/johnkurian261/Cameleo/releases/latest)
[![Platform](https://img.shields.io/badge/Platform-Android%208.0%2B-brightgreen)](https://developer.android.com)

> **⚠️ In Development** — This is a reverse-engineered Android port of [Dazz Cam](https://apps.apple.com/app/dazz-analog-filter-camera/id1459773543) (iOS), built through clean-room analysis of the app's observable behavior, UI structure, and asset inventory. No proprietary source code was copied. This project is for educational and personal use only.

dazzz is a premium vintage camera application for Android inspired by Dazz Cam — the beloved iOS analog filter camera. It recreates Dazz Cam's signature film presets, camera model collection, color grading pipeline, and UI experience natively on Android using Jetpack Compose and CameraX.

---

## 📥 Download & Try dazzz (Direct APK)

Anyone can download and install dazzz directly on an Android device:

| Release | Format | Link |
|---|---|---|
| **v2.0.0 (Latest Release)** | APK (Ready to install) | [**Download dazzz.apk (v2.0.0)**](https://github.com/johnkurian261/Cameleo/releases/download/latest/Cameleo.apk) |
| **All Releases & Release Notes** | Asset Catalog | [**GitHub Releases**](https://github.com/johnkurian261/Cameleo/releases) |

### 📲 Quick Installation Guide (Android Phone)
1. **Download**: Tap [**dazzz.apk**](https://github.com/johnkurian261/Cameleo/releases/download/latest/Cameleo.apk) from Chrome or your phone's browser.
2. **Enable Unknown Apps**: If prompted with *"For your security, your phone is not allowed to install unknown apps from this source"*, tap **Settings** and toggle **"Allow from this source"**.
3. **Install**: Return to the installer and tap **Install**.
4. **Permissions**: Open **dazzz** and grant Camera, Audio, and Storage permissions.
5. **Shoot**: Explore vintage films (FXN R, CCD Digital, 135NE, GR D), high-fidelity **Zero** camera, authentic floating 3-dot control menu, and the classic tactile red shutter!

---

## Key Features

### 1. Real-Time Optical Viewfinder (OpenGL ES 3.0)
- **Zero-Latency GPU Pipeline**: Camera frames stream directly from the sensor ISP to an `EGLImage` target (`GL_TEXTURE_EXTERNAL_OES`) for smooth 60 FPS live preview.
- **Single-Pass Uber-Fragment Shader**:
  - Transverse Chromatic Aberration & Barrel Distortion warping.
  - White Balance (Temperature & Tint color matrix).
  - Tonal Curves (Contrast, Saturation, Gamma, Black Point Fade).
  - Film Halation (Red dye diffusion around specular highlights).
  - Authentic Light Leaks (Screen blend overlay).
  - Lens Vignette (Radial optical illumination falloff).
  - Photographic Film Grain (Luminance-weighted density curve).
  - VHS Scanlines (Periodic horizontal modulation for retro camcorder presets).

### 2. Original Camera Collection (20 Presets across 5 Categories)
- **Analog Film**:
  - `35mm Classic` (1984 Universal Negative Film)
  - `Warm Film` (1978 Golden Hour Emulsion)
  - `Soft Film` (1992 Pastel Portrait Stock)
  - `Vintage Color` (1974 Chromogenic Print)
  - `Faded Film` (1968 Aged Paper Negative)
  - `Cinema Film` (1996 35mm Motion Picture with Red Halation)
- **CCD Digital**:
  - `CCD Classic` (2003 Early Compact Digicam)
  - `CCD Flash` (2005 Night Party Strobe)
  - `Cool Digital` (2001 Y2K Cyber Millennium)
  - `Y2K Digital` (2004 Aesthetic Digicam)
  - `Retro Compact` (2006 Point-and-Shoot)
- **Disposable Cameras**:
  - `Disposable Classic` (1997 Single-Use Plastic Lens)
  - `Flash Disposable` (1999 Strobe Snap)
  - `Warm Disposable` (1995 Summer Vacation 400)
  - `Night Disposable` (1998 High-ISO Street Snap)
- **Instant Photography**:
  - `Instant Classic` (1982 Peel-Apart Chemical Film with White Chin)
  - `Soft Polaroid` (1986 Square Instant Print)
  - `Vintage Instant` (1979 Aged Emulsion 600)
- **Camcorder & Retro Video**:
  - `Retro VHS` (1989 Magnetic Tape Standard with Green OSD)
  - `Analog Video` (1994 8mm Home Tape)
  - `90s Camcorder` (1997 Handheld CCD Video)

### 3. High-Resolution Photo Capture & Darkroom Pipeline
- Full-sensor resolution uncompressed image capture using Android CameraX.
- Lossless aspect ratio cropping (4:3, 1:1, 3:2, 16:9).
- Multi-stage photographic color grading and grain synthesis.
- Vintage date stamping (Orange 7-segment LED, Green LCD, Dot Matrix).
- Physical analog borders (Polaroid white chin, square instant, 35mm film sprockets, 8mm curved gate).
- Android MediaStore export to `DCIM/CRAFTCAM` preserving EXIF metadata.
- Non-destructive negative caching for darkroom redevelopment.

### 4. Tactile UI & Floating Control Menu (Dazz Cam Fidelity)
- **Authentic Floating 3-Dot Control Menu**: Quick-access floating panel with circular buttons and diagonal slash states for Flash (OFF / AUTO / ON), Composition Frame overlay toggle, and Assistive 3×3 Grid.
- **Dedicated Zoom Mode Selection**: Seamlessly toggle between **Frame** mode (compositional crop adjustment) and **Lens** mode (physical optical / sensor hardware zoom).
- **Grouped Settings Screen**: Dazz-style preferences with coral toggles (`#E25241`), front preview & capture mirroring, shutter vibration gating, and local storage safety advisories.
- **Deep Matte Canvas**: Dark graphite photographic UI (`#0A0B0E`, `#14161B`) with vintage amber accents.
- **Mechanical Shutter**: Dual-ring shutter button with tactile spring physics and synchronized haptic click feedback.
- **Prewarmed Darkroom Gallery**: Instant gallery opening with background image pre-caching and high-speed photo development.

---

## Build and Installation Instructions

### Prerequisites
- Android Studio Iguana / Jellyfish or later.
- Android SDK 34 (`platforms;android-34`, `build-tools;34.0.0`).
- JDK 17 or JDK 21.
- Gradle 8.9 (included via Gradle Wrapper).

### Building from Command Line
```powershell
# Build Debug APK
.\gradlew.bat assembleDebug

# Build Release APK
.\gradlew.bat assembleRelease
```

The compiled APKs will be located at:
- Debug: `app/build/outputs/apk/debug/app-debug.apk`
- Release: `app/build/outputs/apk/release/app-release.apk`

### Installing on Device via ADB
Connect an Android device with USB Debugging enabled:
```powershell
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

