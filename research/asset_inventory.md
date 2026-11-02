# REA Investigation: Asset Inventory & Catalog Analysis

This document catalogs the assets discovered within the reference application bundle (`Payload/DazzCamera.app`), identifies their functional role in the analog camera simulation, and outlines the clean-room asset creation strategy for CRAFTCAM.

---

## 1. Asset Category Summary

| Category | Count | File Types | Functional Role in App |
| :--- | :---: | :--- | :--- |
| **Film & Noise Textures** | 51 | `.jpg`, `.png` | Real photographic grain loops for live preview and capture blending. |
| **Light Leaks** | 27 | `.jpg` (1000x1500) | Optical film burn and canister light leaks composited in Screen mode. |
| **Lens Vignettes** | 6 | `.jpg` (1125x1500) | Peripheral optical illumination falloff masks. |
| **Film Frames & Borders** | 35 | `.png`, `.jpg` | Authentic 35mm sprockets, Polaroid frames, and 8mm/16mm masks. |
| **Date & Time Typography** | 3 | `.ttf`, `.png`, `.jpg`| Font and digit matrices for classic vintage date stamps. |
| **Audio Sound Effects** | 5 | `.mp3` | Tactile audio feedback (shutter clicks, motor drives, projector clicks). |
| **Camera Showcase Media** | 111 | `.jpg`, `.mp4` | In-app camera preview cards and sample gallery images. |
| **Compiled Asset Catalogs**| 2 | `.car` (BOM format) | High-DPI UI icons, camera body illustrations, vector symbols. |
| **Camera Index Catalog** | 1 | `.json` (PK-zip) | Structured camera manifest linking models to aspect ratios. |

---

## 2. Granular Inventory of Discovered Assets

### A. Photographic Grain & Noise Textures
- `grain540_01.jpg` through `grain540_10.jpg`:
  - Dimensions: $960 \times 540$ pixels.
  - Characteristics: 10 sequential photographic frames of monochrome 35mm film grain. Cycled sequentially at 24/30 FPS in live preview to produce authentic boiling film grain without procedural GPU load.
- `grain8mm_01.jpg` through `grain8mm_10.jpg`:
  - Dimensions: $960 \times 540$ pixels.
  - Characteristics: Coarse, high-contrast silver halide clumps characteristic of 8mm motion picture film.
- `vhs_grain01.jpg` through `vhs_grain20.jpg`:
  - Dimensions: $427 \times 320$ pixels.
  - Characteristics: 20 frames of magnetic tape luminance noise and analog tracking distortion.
- `vhs_grain_dust01.jpg` through `vhs_grain_dust20.jpg`:
  - Dimensions: $427 \times 320$ pixels.
  - Characteristics: Magnetic tape dropout flecks and sync line glitches.
- Medium/Large Format Stills:
  - `grain_120.jpg`: Fine, uniform grain for medium format 6x7 cameras.
  - `grain_135sr.jpg`: Classic SLR 35mm film grain.
  - `grain_16mm.jpg`: 16mm film stock texture.
  - `half_grain.jpg`: Split-frame half-format grain texture.

### B. Optical Light Leaks
- `lightleaks1.jpg` through `lightleaks27.jpg`:
  - Dimensions: $1000 \times 1500$ pixels.
  - Content: Pure black background with saturated red, amber, and orange light leaks entering from corners, top margins, and side edges.
  - Application: Blended in Screen mode; randomly flipped horizontally or inverted to create endless variations.

### C. Lens Vignettes & Optical Masks
- `vignette_dclassic.jpg`: $1125 \times 1500$ smooth symmetrical radial falloff.
- `vignette_cpm35.jpg`: $994 \times 1500$ cinematic wide vignette with heavy corner shading.
- `vignette_ct2f.jpg`: Compact 35mm rangefinder vignette.
- `vignette_dexp.jpg`, `vignette_dexp3.jpg`: Asymmetric double-exposure vignetting.
- `vignette_nt16.jpg`: 16mm motion picture lens vignette with soft organic edges.
- `blur_mask_hoga.jpg`: Holga toy camera center-sharp peripheral blur mask.

### D. Physical Film Borders & Frames
- 35mm Slide & Negative Frames:
  - `dslide_frame01.png` through `15.png`: Cardboard and plastic 35mm slide mounts.
  - `135ne_frame_cut1.png` through `cut4.png`: 35mm film strip cuts with sprocket holes.
  - `s67_frame_b_01.jpg` through `08.jpg`: Medium format 6x7 black borders with film rebate numbers.
- Instant Photography Frames:
  - `instc_frame01.png` through `15.png`: Classic rectangular white instant photo frames with wide bottom chin.
  - `instsq_frame01.png` through `15.png`: Square format instant frames.
  - `vinst_border1.png` through `3.png`: Vintage instant film borders with subtle aging and patina.
- Motion Picture Frames:
  - `8mm_frame1.png`, `8mm_frame1_b.png`: Rounded 8mm film gate mask.
  - `golf_border_b.png`, `l.png`, `r.png`: Multi-frame split view borders.

### E. Typography & Date Stamps
- `VCR_OSD_MONO.ttf`: Monospaced retro TrueType font replicating 1980s/1990s VCR and camcorder on-screen display (OSD) characters.
- `num_16mm.jpg`: $130 \times 200$ sprite sheet of 16mm mechanical edge numbers.
- `num_dcr.png`: Retro digital camera seven-segment LCD/LED digit sprites.

### F. Tactile Audio Feedback
- `sound_slidep_bg.mp3`: Subtle ambient motor hum.
- `sound_slidep_click.mp3`: Sharp mechanical shutter snap.
- `sound_slidep_click2.mp3`: Alternate dampened shutter release.

### G. Compiled Asset Catalogs (`Assets.car`)
Inspection of the BOM tree in `DazzCamera.app/Assets.car` and `DZImageFilter.framework/Assets.car` extracted names of vector graphics and camera icons:
- Camera Selection Icons: `camera_dclassic.png`, `camera_ccdr`, `camera_classicu.png`, `camera_cpm35.png`, `camera_ct2f.png`, `camera_d3d`, `camera_dam.png`, `camera_dcr.png`, `camera_dexp.png`, `camera_135ne`, `camera_135sr`, `camera_16mm`, `camera_8mm`.
- Embedded LUTs in `DZImageFilter`: `lookup_ofm_light`, `lookup_ou_light`, `lookup_paf_eb3000_light`, `lookup_paf_ec100_light`, `lut_lip`, `LookupTableDefault.png`.

### H. Camera Manifest (`index260826.json`)
The application embeds a camera index defining 53 cameras and accessories:
- Formats and aspect ratios:
  - 3:2 (Classic 35mm: `dclassic`, `ct2f`, `cpm35`, `135sr`, `dexp`)
  - 4:3 (CCD and Compacts: `ccdr`, `grdr`, `classicu`, `fxn`)
  - 1:1 (Square format: `instsq`, `instsqc`, `hoga`)
  - 16:9 / 4:3 (Video: `vclassic`, `vhs`, `v8mm`, `v16mm`, `dcr`)

---

## 3. Clean-Room Asset Recreation Guidelines for CRAFTCAM

In strict adherence to reverse-engineering ethics and copyright protection:
1. **Zero Direct Asset Copying**: None of Dazz Cam's proprietary PNG, JPG, or audio files will be copied or embedded into the CRAFTCAM codebase.
2. **Procedural & Original Assets**:
   - Film grain will be generated procedurally via mathematical GLSL algorithms (Luminance-weighted Simplex/Hash noise) and clean original high-definition grain textures.
   - Light leaks will be generated organically using multi-stop radial gradient synthesis and original photon diffusion maps.
   - Borders and frames will be procedurally drawn using Jetpack Compose Canvas and vector SVG paths, allowing infinite scaling and resolution independence.
   - Date stamps will be rendered dynamically using standard TrueType monospaced digital fonts.
   - Audio clicks will utilize Android's built-in system tactile audio feedback and high-fidelity haptic waveforms (`VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)`).

