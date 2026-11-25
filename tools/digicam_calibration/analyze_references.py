"""
Reference Image Regional Analysis and Calibration Toolkit for CPM35 and CCD Digicam Emulation (Phase 2).
Performs both global and region-level colorimetric analysis across the 8 Tier 1 reference photographs:
- Skin tone hue, chroma, luminance, and Lab coordinates
- Neutral surface balance
- Red clothing saturation and gamut response (Ref 2)
- Green foliage and artificial lighting tint (Ref 7, 8)
- Highlight light sources & optical diffusion
- Deep shadows and electronic noise floor
"""

import os
import json
import math
from PIL import Image, ImageStat

USER_UPLOADED_DIR = r"C:\Users\User\.gemini\antigravity\brain\fee3dcbb-fa61-427d-99ce-2304f6de2127\.user_uploaded"

REFERENCES = [
    {
        "id": "ref1",
        "name": "Restaurant (Warm Amber)",
        "file": "media_1795613973085_2ce0732d.jpg",
        "camera": "Sony DSC-W130 (inferred/metadata)",
        "lighting": "Amber Tungsten",
        "primary_profile": "CPM35",
        "target_mean_rgb": (161.3, 94.8, 24.4),
        "target_median_luma": 93.6,
        "regions": {
            "skin_face": [0.46, 0.32, 0.58, 0.48], # [left, top, right, bottom] normalized
            "neutral_table": [0.70, 0.85, 0.90, 0.98],
            "ambient_lights": [0.55, 0.05, 0.85, 0.25],
            "shadows": [0.02, 0.70, 0.15, 0.95]
        }
    },
    {
        "id": "ref2",
        "name": "Red Dress (Direct Illumination/Flash)",
        "file": "media_1795613973098_78114b3b.jpg",
        "camera": "iPhone 15 / Compact Digicam look",
        "lighting": "Golden Direct Flash",
        "primary_profile": "CPM35",
        "target_mean_rgb": (166.6, 64.4, 23.4),
        "target_median_luma": 76.3,
        "regions": {
            "skin_face": [0.45, 0.16, 0.65, 0.32],
            "red_dress": [0.15, 0.55, 0.65, 0.90],
            "shadows": [0.80, 0.60, 0.98, 0.85]
        }
    },
    {
        "id": "ref3",
        "name": "Indoor Portrait (Motion Softness & Amber Stamp)",
        "file": "media_1795613973098_57e0a7c6.jpg",
        "camera": "Canon EOS 1100D (vintage warm grading)",
        "lighting": "Amber Tungsten + Motion",
        "primary_profile": "CPM35",
        "target_mean_rgb": (180.0, 96.1, 25.7),
        "target_median_luma": 128.6,
        "regions": {
            "skin_face": [0.42, 0.30, 0.58, 0.52],
            "wall_amber": [0.70, 0.20, 0.95, 0.60],
            "amber_timestamp": [0.70, 0.82, 0.95, 0.90]
        }
    },
    {
        "id": "ref4",
        "name": "Sunlight Portrait (Bright Highlights)",
        "file": "media_1795613973100_a79222ac.jpg",
        "camera": "iPhone 13 / Digicam emulation",
        "lighting": "Golden Daylight",
        "primary_profile": "CCD",
        "target_mean_rgb": (132.1, 113.7, 93.5),
        "target_median_luma": 102.9,
        "regions": {
            "skin_face": [0.38, 0.28, 0.58, 0.52],
            "sunlit_shoulder": [0.35, 0.62, 0.65, 0.85],
            "background_foliage": [0.45, 0.02, 0.65, 0.25]
        }
    },
    {
        "id": "ref5",
        "name": "Car Portrait (Backlit Sun & Direct Foreground)",
        "file": "media_1795613973104_eaba7f3e.jpg",
        "camera": "Y2K Pocket Digicam (Sony/Canon)",
        "lighting": "Golden Daylight + Fill",
        "primary_profile": "CCD",
        "target_mean_rgb": (147.7, 124.4, 94.2),
        "target_median_luma": 124.9,
        "regions": {
            "skin_face": [0.26, 0.22, 0.45, 0.45],
            "overexposed_window": [0.48, 0.18, 0.80, 0.48],
            "neutral_interior": [0.65, 0.60, 0.88, 0.85]
        }
    },
    {
        "id": "ref6",
        "name": "Mirror Selfie (Imperfect AWB)",
        "file": "media_1795613973105_f442b347.jpg",
        "camera": "Red Digicam in mirror",
        "lighting": "Amber Tungsten AWB",
        "primary_profile": "CPM35",
        "target_mean_rgb": (160.6, 99.8, 46.1),
        "target_median_luma": 108.7,
        "regions": {
            "skin_face": [0.20, 0.12, 0.44, 0.48],
            "red_camera": [0.41, 0.32, 0.58, 0.48],
            "neutral_wall": [0.65, 0.15, 0.85, 0.60]
        }
    },
    {
        "id": "ref7",
        "name": "Train Interior (Yellow-Green & Timestamp)",
        "file": "media_1795613973108_5c61493b.jpg",
        "camera": "Silver Sony Cyber-shot / Ixus",
        "lighting": "Yellow-Green Artificial",
        "primary_profile": "CCD",
        "target_mean_rgb": (120.3, 108.0, 81.8),
        "target_median_luma": 80.0,
        "regions": {
            "skin_arm_neck": [0.38, 0.48, 0.52, 0.65],
            "train_interior_ceiling": [0.60, 0.05, 0.90, 0.25],
            "digital_timestamp": [0.65, 0.90, 0.92, 0.96]
        }
    },
    {
        "id": "ref8",
        "name": "Night Street (Olive-Yellow & Glow)",
        "file": "media_1795613973083_e86e5f83.jpg",
        "camera": "Compact Digicam (Sony Cyber-shot)",
        "lighting": "Yellow-Green Artificial / Sodium",
        "primary_profile": "CCD",
        "target_mean_rgb": (98.2, 87.3, 36.1),
        "target_median_luma": 71.0,
        "regions": {
            "skin_face": [0.42, 0.35, 0.58, 0.55],
            "street_lamp_glow": [0.34, 0.30, 0.44, 0.45],
            "night_shadow": [0.75, 0.65, 0.95, 0.95]
        }
    }
]

def rgb_to_lab(r, g, b):
    def to_lin(c):
        c = c / 255.0
        return c / 12.92 if c <= 0.04045 else ((c + 0.055) / 1.055) ** 2.4
    r_l, g_l, b_l = to_lin(r), to_lin(g), to_lin(b)
    X = r_l * 0.4124 + g_l * 0.3576 + b_l * 0.1805
    Y = r_l * 0.2126 + g_l * 0.7152 + b_l * 0.0722
    Z = r_l * 0.0193 + g_l * 0.1192 + b_l * 0.9505
    X, Y, Z = X / 0.95047, Y / 1.00000, Z / 1.08883
    def f(t):
        return t ** (1/3) if t > 0.008856 else (7.787 * t) + (16 / 116)
    L = max(0.0, 116.0 * f(Y) - 16.0)
    a = 500.0 * (f(X) - f(Y))
    b_val = 200.0 * (f(Y) - f(Z))
    return L, a, b_val

def extract_region_stats(img, box_norm):
    w, h = img.size
    left = int(box_norm[0] * w)
    top = int(box_norm[1] * h)
    right = int(box_norm[2] * w)
    bottom = int(box_norm[3] * h)
    crop = img.crop((left, top, right, bottom))
    stat = ImageStat.Stat(crop)
    r_mean, g_mean, b_mean = stat.mean[:3]
    r_std, g_std, b_std = stat.stddev[:3]

    sum_rgb = max(1.0, r_mean + g_mean + b_mean)
    r_norm = r_mean / sum_rgb
    g_norm = g_mean / sum_rgb

    max_c = max(r_mean, max(g_mean, b_mean))
    min_c = min(r_mean, min(g_mean, b_mean))
    chroma = max_c - min_c
    sat = chroma / max_c if max_c > 0 else 0.0

    L, a, b_val = rgb_to_lab(r_mean, g_mean, b_mean)

    return {
        "box_norm": box_norm,
        "mean_rgb": (round(r_mean, 1), round(g_mean, 1), round(b_mean, 1)),
        "stddev_rgb": (round(r_std, 1), round(g_std, 1), round(b_std, 1)),
        "norm_rg": (round(r_norm, 3), round(g_norm, 3)),
        "chroma": round(chroma, 1),
        "saturation": round(sat, 3),
        "cielab": (round(L, 1), round(a, 1), round(b_val, 1))
    }

def main():
    print("=" * 95)
    print("CAMELEO CPM35 & CCD DIGICAM — PHASE 2 REGIONAL CALIBRATION TOOLKIT")
    print("=" * 95)

    results = []

    for item in REFERENCES:
        fpath = os.path.join(USER_UPLOADED_DIR, item["file"])
        if not os.path.exists(fpath):
            print(f"Error: {item['file']} not found!")
            continue

        img = Image.open(fpath).convert("RGB")
        w, h = img.size
        stat = ImageStat.Stat(img)
        r_mean, g_mean, b_mean = stat.mean[:3]

        gray = img.convert("L")
        hist = gray.histogram()
        total = sum(hist)
        cum = 0
        median_luma = 0
        for i, count in enumerate(hist):
            cum += count
            if cum >= total / 2:
                median_luma = i
                break

        # Regional extraction
        region_stats = {}
        if "regions" in item:
            for rname, rbox in item["regions"].items():
                region_stats[rname] = extract_region_stats(img, rbox)

        res = {
            "id": item["id"],
            "name": item["name"],
            "file": item["file"],
            "dimensions": f"{w}x{h}",
            "lighting": item["lighting"],
            "primary_profile": item["primary_profile"],
            "measured_global_mean_rgb": (round(r_mean, 1), round(g_mean, 1), round(b_mean, 1)),
            "measured_median_luma": median_luma,
            "target_mean_rgb": item["target_mean_rgb"],
            "target_median_luma": item["target_median_luma"],
            "regions": region_stats
        }
        results.append(res)

        print(f"[{item['id'].upper()}] {item['name']} ({w}x{h}) — {item['lighting']} -> {item['primary_profile']}")
        print(f"  Global RGB: ({r_mean:5.1f}, {g_mean:5.1f}, {b_mean:5.1f}) | Median Luma: {median_luma}")
        for rname, rdata in region_stats.items():
            print(f"    - {rname:20s}: RGB={rdata['mean_rgb']}, Sat={rdata['saturation']:4.2f}, Lab={rdata['cielab']}")
        print("-" * 95)

    out_json = os.path.join(os.path.dirname(__file__), "reference_regional_analysis.json")
    with open(out_json, "w", encoding="utf-8") as f:
        json.dump(results, f, indent=2)
    print(f"\nRegional calibration dataset successfully saved to:\n{out_json}")

if __name__ == "__main__":
    main()
