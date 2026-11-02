# Cameleo — Vintage Analog Camera for Android

Cameleo is a premium vintage camera application for Android inspired by the analog photography experience of Dazz Cam, classic 35mm film stocks, early 2000s CCD digicams, party disposable cameras, instant prints, and retro camcorders.

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

### 4. Tactile UI & Dark Photographic Canvas
- Deep matte graphite and black aesthetic (`#0A0B0E`, `#14161B`) with vintage amber accents (`#FF8C1A`).
- Mechanical dual-ring shutter button with spring physics and haptic click feedback.
- Rule of Thirds assistive grid and tap-to-focus reticle.
- Comprehensive integrated Darkroom Gallery with full-screen viewer and photo details.

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

