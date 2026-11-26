# CAMELEO — Camera Body & Color Profile Compatibility Matrix

## 1. Overview

In Dazz Cam and CAMELEO, not every camera is an interchangeable film body.
Cameras fall into three architectural categories:
1. **Interchangeable Film Bodies**: Support all 10 color profiles (`03G`..`FXN2`) with authentic defaults and per-camera persistence.
2. **Specialized Digital & Cinema Bodies**: Support profile swapping tailored to their historical medium.
3. **Fixed Hardware & Analog Pipelines**: Fixed dedicated imaging pipelines where color profile swapping is disabled to preserve authentic physics (e.g. Modern Natural ISP, CRT/VHS tape).

---

## 2. Compatibility Matrix

| Camera ID | Display Name | Category | Color Profile Support | Default Profile | Supported Profiles | Stored Preference Key |
| :--- | :--- | :--- | :---: | :---: | :--- | :--- |
| `analog_35mm_classic` | **D Classic** | Film 35mm | **Supported** | `C400` | All 10 Profiles | `color_profile_analog_35mm_classic` |
| `analog_135sr` | **135 SR** | Film 35mm | **Supported** | `C400` | All 10 Profiles | `color_profile_analog_135sr` |
| `analog_135ne` | **135 NE** | Film 35mm | **Supported** | `C400` | All 10 Profiles | `color_profile_analog_135ne` |
| `ccd_flash` | **D Funs** | Disposable | **Supported** | `C400` | All 10 Profiles | `color_profile_ccd_flash` |
| `ccd_retro_compact` | **DQS** | Disposable | **Supported** | `C400` | All 10 Profiles | `color_profile_ccd_retro_compact` |
| `disp_flash` | **CPM 35** | Disposable | **Supported** | `C400` | All 10 Profiles | `color_profile_disp_flash` |
| `analog_cinema_film` | **D3D** | 3D Stereo Film | **Supported** | `19P` | All 10 Profiles | `color_profile_analog_cinema_film` |
| `disp_classic` | **Golf** | Disposable | **Supported** | `C400` | All 10 Profiles | `color_profile_disp_classic` |
| `disp_warm` | **Classic Warm** | Disposable | **Supported** | `EG200` | All 10 Profiles | `color_profile_disp_warm` |
| `analog_soft_film` | **CT2F** | Movie Film | **Supported** | `19P` | All 10 Profiles | `color_profile_analog_soft_film` |
| `analog_warm_film` | **Warm Film** | Movie Film | **Supported** | `03G` | All 10 Profiles | `color_profile_analog_warm_film` |
| `analog_fxn_r` | **FXN-R** | Negative Film | **Supported** | `FXN2` | All 10 Profiles | `color_profile_analog_fxn_r` |
| `ccd_r` | **CCD R** | Retro Digicam | **Supported** | `FX33` | All 10 Profiles | `color_profile_ccd_r` |
| `ccd_grd` | **GR D** | Digicam | **Supported** | `FX33` | All 10 Profiles | `color_profile_ccd_grd` |
| `ccd_y2k_digital` | **Classic U** | Y2K Digicam | **Supported** | `FX33` | All 10 Profiles | `color_profile_ccd_y2k_digital` |
| `zero` | **Zero** | Modern Natural | **Disabled** | `C400` | N/A (Fixed Natural ISP) | Fixed Pipeline |
| `analog_dblack` | **D Black** | Monochrome | **Disabled** | `22M` | N/A (Fixed B&W Chemistry) | Fixed Pipeline |
| `cam_retro_vhs` | **Retro VHS** | Camcorder | **Disabled** | N/A | N/A (Fixed Magnetic Tape) | Fixed Pipeline |
| `cam_analog_video`| **Analog Video**| Camcorder | **Disabled** | N/A | N/A (Fixed CRT Video) | Fixed Pipeline |
| `cam_90s_camcorder`| **90s Camcorder**| Camcorder | **Disabled** | N/A | N/A (Fixed Hi8 Video) | Fixed Pipeline |

---

## 3. Preference Persistence & Reset Behaviour

1. **Stable ID Storage**:
   Selections are saved using persistent string identifiers (e.g. `"C400"`, `"19P"`, `"FX33"`) in `AppSettingsStore` SharedPreferences under key `color_profile_<preset_id>`.
2. **Camera Switching**:
   When switching between cameras, CAMELEO automatically loads that specific camera's last selected profile.
3. **Camera Reset Action**:
   Tapping "Reset" in the bottom sheet restores the camera body to its authentic hardware default (e.g., `19P` for CT2F/D3D; `FX33` for CCD Digicams; `FXN2` for FXNR; `C400` for 35mm and disposables).
