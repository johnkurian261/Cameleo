"""
Python Implementation of the CAMELEO Digicam Calibration Pipeline.
Simulates illuminant estimation, partial white balance, red protection,
compact tonal curve, optical scatter, and electronic noise.
"""

import os
import math
import random
from PIL import Image, ImageDraw, ImageFont

USER_UPLOADED_DIR = r"C:\Users\User\.gemini\antigravity\brain\fee3dcbb-fa61-427d-99ce-2304f6de2127\.user_uploaded"
OUTPUT_DIR = os.path.join(os.path.dirname(__file__), "output_simulation")
os.makedirs(OUTPUT_DIR, exist_ok=True)

class DigicamSimulator:
    def __init__(self, model="CPM35"):
        self.model = model

    def estimate_illuminant(self, img, is_flash=False):
        if is_flash:
            return "GOLDEN_DIRECT_FLASH"

        # Downsample for fast statistics
        small = img.resize((64, 64), Image.Resampling.BILINEAR)
        pixels = list(small.getdata())
        sum_r = sum(p[0] for p in pixels)
        sum_g = sum(p[1] for p in pixels)
        sum_b = sum(p[2] for p in pixels)
        count = len(pixels)

        avg_r = max(1.0, sum_r / count)
        avg_g = max(1.0, sum_g / count)
        avg_b = max(1.0, sum_b / count)

        blue_ratio = avg_b / avg_r
        green_ratio = avg_g / avg_r

        avg_luma = 0.299 * avg_r + 0.587 * avg_g + 0.114 * avg_b

        if blue_ratio <= 0.35:
            return "AMBER_TUNGSTEN"
        elif avg_luma < 115.0 and green_ratio >= 0.82 and (avg_g - avg_b) / avg_r >= 0.18 and blue_ratio <= 0.72:
            return "YELLOW_GREEN_ARTIFICIAL"
        else:
            return "GOLDEN_DAYLIGHT"

    def get_parameters(self, illuminant):
        params = {
            "GOLDEN_DAYLIGHT": {
                "gains": (1.03, 0.98, 0.86),
                "shadow_tint": (0.015, 0.010, 0.005),
                "contrast": 1.12,
                "saturation": 1.08,
                "red_boost": 1.10,
                "highlight_rolloff": 0.88,
                "toe_lift": 0.018,
                "optical_scatter": 0.15,
                "noise_mult": 0.85
            },
            "AMBER_TUNGSTEN": {
                "gains": (1.18, 0.88, 0.32),
                "shadow_tint": (0.045, 0.022, 0.008),
                "contrast": 1.15,
                "saturation": 1.12,
                "red_boost": 1.15,
                "highlight_rolloff": 0.82,
                "toe_lift": 0.025,
                "optical_scatter": 0.34,
                "noise_mult": 1.45
            },
            "GOLDEN_DIRECT_FLASH": {
                "gains": (1.12, 0.95, 0.65),
                "shadow_tint": (0.030, 0.012, 0.006),
                "contrast": 1.25,
                "saturation": 1.18,
                "red_boost": 1.35,
                "highlight_rolloff": 0.78,
                "toe_lift": 0.015,
                "optical_scatter": 0.22,
                "noise_mult": 1.05
            },
            "YELLOW_GREEN_ARTIFICIAL": {
                "gains": (1.02, 1.05, 0.52),
                "shadow_tint": (0.012, 0.024, 0.008),
                "contrast": 1.18,
                "saturation": 1.06,
                "red_boost": 1.12,
                "highlight_rolloff": 0.84,
                "toe_lift": 0.020,
                "optical_scatter": 0.38,
                "noise_mult": 1.50
            }
        }[illuminant]

        if self.model == "CPM35":
            params["gains"] = (params["gains"][0] * 1.05, params["gains"][1] * 0.98, params["gains"][2] * 0.92)
            params["contrast"] *= 0.98
            params["saturation"] *= 1.05
            params["red_boost"] *= 1.10
        else: # CCD
            params["gains"] = (params["gains"][0] * 0.98, params["gains"][1] * 1.02, params["gains"][2] * 0.98)
            params["contrast"] *= 1.06
            params["saturation"] *= 1.02
            params["toe_lift"] = max(0.01, params["toe_lift"] * 0.85)

        return params

    def process(self, img, is_flash=False, iso=100):
        illuminant = self.estimate_illuminant(img, is_flash)
        params = self.get_parameters(illuminant)

        w, h = img.size
        pixels = list(img.getdata())

        gain_r, gain_g, gain_b = params["gains"]
        shadow_r = params["shadow_tint"][0] * 255.0
        shadow_g = params["shadow_tint"][1] * 255.0
        shadow_b = params["shadow_tint"][2] * 255.0
        contrast = params["contrast"]
        sat = params["saturation"]
        red_boost = params["red_boost"]
        shoulder = params["highlight_rolloff"]
        toe = params["toe_lift"]

        # Precompute 256-entry S-curve
        tone_lut = [0.0] * 256
        for i in range(256):
            x = i / 255.0
            x_toe = toe + (1.0 - toe) * (x ** 1.06)
            if x_toe < 0.45:
                x_cont = 0.45 * ((x_toe / 0.45) ** contrast)
            else:
                x_cont = 1.0 - 0.55 * (((1.0 - x_toe) / 0.55) ** contrast)
            if x_cont > shoulder:
                excess = x_cont - shoulder
                headroom = 1.0 - shoulder
                x_final = shoulder + headroom * (excess / (excess + headroom * 0.85))
            else:
                x_final = x_cont
            tone_lut[i] = max(0.0, min(1.0, x_final))

        out_pixels = []
        for r, g, b in pixels:
            # 1. Partial White Balance gains
            rf = max(0.0, min(255.0, r * gain_r))
            gf = max(0.0, min(255.0, g * gain_g))
            bf = max(0.0, min(255.0, b * gain_b))

            # 2. Saturated Red Protection
            if rf > gf * 1.25 and rf > bf * 1.25:
                dominance = min(1.0, max(0.0, (rf - max(gf, bf)) / 255.0))
                boost = 1.0 + (red_boost - 1.0) * dominance
                rf = min(255.0, rf * boost)
                gf = max(0.0, gf * (1.0 - dominance * 0.08))
                bf = max(0.0, bf * (1.0 - dominance * 0.12))

            # 3. Tone curve
            rf = tone_lut[int(round(rf))] * 255.0
            gf = tone_lut[int(round(gf))] * 255.0
            bf = tone_lut[int(round(bf))] * 255.0

            # 4. Saturation
            luma = 0.299 * rf + 0.587 * gf + 0.114 * bf
            rf = max(0.0, min(255.0, luma + (rf - luma) * sat))
            gf = max(0.0, min(255.0, luma + (gf - luma) * sat))
            bf = max(0.0, min(255.0, luma + (bf - luma) * sat))

            # 5. Shadow Tint
            shadow_weight = max(0.0, min(1.0, (60.0 - luma) / 60.0))
            if shadow_weight > 0.0:
                rf = min(255.0, rf + shadow_r * shadow_weight)
                gf = min(255.0, gf + shadow_g * shadow_weight)
                bf = min(255.0, bf + shadow_b * shadow_weight)

            out_pixels.append((int(round(rf)), int(round(gf)), int(round(bf))))

        out_img = Image.new("RGB", (w, h))
        out_img.putdata(out_pixels)

        # 6. Electronic CCD Noise injection
        noise_mult = params["noise_mult"]
        if noise_mult > 0.01:
            out_img = self.apply_noise(out_img, iso, noise_mult)

        return out_img, illuminant

    def apply_noise(self, img, iso, noise_mult):
        w, h = img.size
        pixels = list(img.getdata())
        iso_scale = math.sqrt(max(0.8, min(16.0, iso / 100.0)))
        base_noise = 3.5 * noise_mult * iso_scale

        noisy_pixels = []
        for r, g, b in pixels:
            luma = (0.299 * r + 0.587 * g + 0.114 * b) / 255.0
            signal_sigma = 0.35 + math.sqrt(luma) * 0.65

            rand_luma = (random.random() - 0.5) * 2.0 * base_noise * signal_sigma
            rand_chroma_r = (random.random() - 0.5) * 2.0 * base_noise * 0.40 * signal_sigma
            rand_chroma_b = (random.random() - 0.5) * 2.0 * base_noise * 0.40 * signal_sigma

            rn = int(round(max(0, min(255, r + rand_luma + rand_chroma_r))))
            gn = int(round(max(0, min(255, g + rand_luma))))
            bn = int(round(max(0, min(255, b + rand_luma + rand_chroma_b))))
            noisy_pixels.append((rn, gn, bn))

        res = Image.new("RGB", (w, h))
        res.putdata(noisy_pixels)
        return res

def main():
    print("Simulating CAMELEO Digicam Calibration on references...")
    test_refs = [
        ("ref1_restaurant", "media_1795613973085_2ce0732d.jpg", False, "CPM35"),
        ("ref2_red_dress", "media_1795613973098_78114b3b.jpg", True, "CPM35"),
        ("ref3_indoor_portrait", "media_1795613973098_57e0a7c6.jpg", False, "CPM35"),
        ("ref4_sunlight", "media_1795613973100_a79222ac.jpg", False, "CCD"),
        ("ref5_car_portrait", "media_1795613973104_eaba7f3e.jpg", False, "CCD"),
        ("ref6_mirror_selfie", "media_1795613973105_f442b347.jpg", False, "CPM35"),
        ("ref7_train", "media_1795613973108_5c61493b.jpg", False, "CCD"),
        ("ref8_night_street", "media_1795613973083_e86e5f83.jpg", False, "CCD"),
    ]

    for label, fname, is_flash, model in test_refs:
        fpath = os.path.join(USER_UPLOADED_DIR, fname)
        if not os.path.exists(fpath):
            continue
        img = Image.open(fpath).convert("RGB")
        sim = DigicamSimulator(model)
        out_img, illum = sim.process(img, is_flash=is_flash)
        out_path = os.path.join(OUTPUT_DIR, f"{label}_{model}_{illum}.jpg")
        out_img.save(out_path, quality=92)
        print(f"Processed {label} -> {model} [{illum}] -> {out_path}")

if __name__ == "__main__":
    main()
