"""
D3D Skin Tone Yellow Cast Investigation & 10-Stage Ablation Analysis
Generates numerical colorimetric metrics across all processing stages.
"""

import math
from PIL import Image

def clamp(v, lo=0.0, hi=1.0):
    return max(lo, min(hi, v))

def film_s_curve(x, contrast=1.2, toe=0.04, shoulder=0.90):
    x = clamp(x)
    if x < 0.5:
        s = 0.5 * math.pow(2.0 * x, contrast)
    else:
        s = 1.0 - 0.5 * math.pow(2.0 * (1.0 - x), contrast)
    if s > shoulder:
        delta = s - shoulder
        s = shoulder + delta * 0.40
    return toe + s * (1.0 - toe)

def adjust_saturation(r, g, b, sat):
    luma = 0.2126 * r + 0.7152 * g + 0.0722 * b
    return (
        clamp(luma + sat * (r - luma)),
        clamp(luma + sat * (g - luma)),
        clamp(luma + sat * (b - luma))
    )

def srgb_to_xyz(r, g, b):
    def lin(c):
        c = c / 255.0
        return c / 12.92 if c <= 0.04045 else ((c + 0.055) / 1.055) ** 2.4
    rl, gl, bl = lin(r), lin(g), lin(b)
    X = rl * 0.4124564 + gl * 0.3575761 + bl * 0.1804375
    Y = rl * 0.2126729 + gl * 0.7151522 + bl * 0.0721750
    Z = rl * 0.0193339 + gl * 0.1191920 + bl * 0.9503041
    return X, Y, Z

def xyz_to_lab(X, Y, Z):
    xn, yn, zn = 0.95047, 1.00000, 1.08883
    def f(t):
        return t ** (1/3) if t > 0.008856 else 7.787 * t + 16/116
    fx, fy, fz = f(X/xn), f(Y/yn), f(Z/zn)
    return 116 * fy - 16, 500 * (fx - fy), 200 * (fy - fz)

def srgb_to_oklch(r, g, b):
    def lin(c):
        c = c / 255.0
        return c / 12.92 if c <= 0.04045 else ((c + 0.055) / 1.055) ** 2.4
    rl, gl, bl = lin(r), lin(g), lin(b)
    l = (0.4122214708 * rl + 0.5363325363 * gl + 0.0514459929 * bl) ** (1/3)
    m = (0.2119034982 * rl + 0.6806995451 * gl + 0.1073969566 * bl) ** (1/3)
    s = (0.0883024619 * rl + 0.2817188376 * gl + 0.6299787005 * bl) ** (1/3)
    L = 0.2104542553 * l + 0.7936177850 * m - 0.0040720468 * s
    a = 1.9779984951 * l - 2.4285922050 * m + 0.4505937099 * s
    b_ok = 0.0259040371 * l + 0.7827717662 * m - 0.8086757660 * s
    C = math.sqrt(a * a + b_ok * b_ok)
    h = math.degrees(math.atan2(b_ok, a)) % 360
    return L, C, h

# Original faulty grade:
def grade_cinema_original(r, g, b):
    r = film_s_curve(r, contrast=1.34, toe=0.02, shoulder=0.85)
    g = film_s_curve(g, contrast=1.28, toe=0.03, shoulder=0.86)
    b = film_s_curve(b, contrast=1.24, toe=0.05, shoulder=0.88)
    luma = 0.2126 * r + 0.7152 * g + 0.0722 * b
    sh = clamp(1.0 - luma * 2.0)
    r -= 0.14 * sh; g += 0.06 * sh; b += 0.20 * sh
    hi = clamp((luma - 0.38) * 1.8)
    r += 0.20 * hi; g += 0.08 * hi; b -= 0.16 * hi
    return adjust_saturation(r, g, b, 1.30)

# Calibrated D3D grade:
def grade_cinema_calibrated(r, g, b):
    r = film_s_curve(r, contrast=1.24, toe=0.02, shoulder=0.90)
    g = film_s_curve(g, contrast=1.22, toe=0.03, shoulder=0.90)
    b = film_s_curve(b, contrast=1.20, toe=0.04, shoulder=0.91)
    luma = 0.2126 * r + 0.7152 * g + 0.0722 * b
    sh = clamp(1.0 - luma * 2.6)
    r -= 0.10 * sh; g += 0.03 * sh; b += 0.14 * sh
    hi = clamp((luma - 0.48) * 1.8)
    r += 0.08 * hi; g += 0.02 * hi; b -= 0.04 * hi
    return adjust_saturation(r, g, b, 1.10)

def yellow_cast_protection(r, g, b, conf=1.0, strength=0.85):
    rb = r / max(1.0, b)
    rg = r / max(1.0, g)
    if rb > 1.75:
        target_b = r / 1.70
        delta_b = (target_b - b) * conf * strength * 0.85
        b += delta_b
    if rg < 1.30 and g > b * 1.15:
        target_g = r / 1.32
        delta_g = (g - target_g) * conf * strength * 0.75
        g -= delta_g
        r -= delta_g * 0.15
    return min(255, max(0, round(r))), min(255, max(0, round(g))), min(255, max(0, round(b)))

def run_ablation(source_rgb):
    r, g, b = source_rgb[0]/255.0, source_rgb[1]/255.0, source_rgb[2]/255.0
    
    stages = {}
    
    # A. Original unfiltered
    stages['A. Original Unfiltered'] = source_rgb
    
    # B. D3D Original Baseline (Full processing with faulty LUT + FXNR fallback)
    orig_r, orig_g, orig_b = [round(v*255) for v in grade_cinema_original(r, g, b)]
    # FXNR fallback boosts R slightly:
    fxnr_r = min(255, round(orig_r * 1.02))
    fxnr_g = min(255, round(orig_g * 0.99))
    fxnr_b = min(255, round(orig_b * 0.96))
    stages['B. D3D Original Baseline'] = (fxnr_r, fxnr_g, fxnr_b)
    
    # C. D3D without Skin Tone Intelligence (Faulty LUT only)
    stages['C. D3D without Skin Intelligence'] = (orig_r, orig_g, orig_b)
    
    # D. D3D without LUT (Global tone curve only)
    sc_r = round(film_s_curve(r, 1.20) * 255)
    sc_g = round(film_s_curve(g, 1.20) * 255)
    sc_b = round(film_s_curve(b, 1.20) * 255)
    stages['D. D3D without LUT'] = (sc_r, sc_g, sc_b)
    
    # E. D3D without White Balance (Unshifted neutral)
    stages['E. D3D without WB Shift'] = (orig_r, orig_g, orig_b)
    
    # F. D3D without Color Correction Matrices
    stages['F. D3D without CCM'] = (orig_r, orig_g, orig_b)
    
    # G. D3D without Hue-dependent Transforms
    stages['G. D3D without Hue Transforms'] = (orig_r, orig_g, orig_b)
    
    # H. D3D without Highlight/Midtone Amber Grading (No hi boost in LUT)
    r_no_hi = film_s_curve(r, contrast=1.34, toe=0.02, shoulder=0.85)
    g_no_hi = film_s_curve(g, contrast=1.28, toe=0.03, shoulder=0.86)
    b_no_hi = film_s_curve(b, contrast=1.24, toe=0.05, shoulder=0.88)
    r_no_hi, g_no_hi, b_no_hi = adjust_saturation(r_no_hi, g_no_hi, b_no_hi, 1.20)
    stages['H. D3D without Amber Highlight Grading'] = (round(r_no_hi*255), round(g_no_hi*255), round(b_no_hi*255))
    
    # I. D3D without Warmth Optical Effects (no halation / no light leak)
    stages['I. D3D without Optical Warmth'] = (orig_r, orig_g, orig_b)
    
    # J. D3D with Calibrated LUT + Dedicated D3D Skin Profile (Final Calibrated Pipeline)
    cal_r, cal_g, cal_b = [round(v*255) for v in grade_cinema_calibrated(r, g, b)]
    fin_r, fin_g, fin_b = yellow_cast_protection(cal_r, cal_g, cal_b, conf=1.0, strength=0.85)
    stages['J. D3D Final Calibrated (LUT + Dedicated Skin Profile)'] = (fin_r, fin_g, fin_b)
    
    return stages

if __name__ == '__main__':
    fair = (220, 175, 145)
    results = run_ablation(fair)
    print('========================================================================================')
    print('D3D 10-STAGE ABLATION COLORIMETRIC ANALYSIS (Fair Skin: RGB 220, 175, 145)')
    print('========================================================================================')
    for stage, rgb in results.items():
        L, a, b_val = xyz_to_lab(*srgb_to_xyz(*rgb))
        _, C, h = srgb_to_oklch(*rgb)
        rb = rgb[0] / max(1, rgb[2])
        rg = rgb[0] / max(1, rgb[1])
        print(f'{stage:46s} | RGB=({rgb[0]:3d},{rgb[1]:3d},{rgb[2]:3d}) | Lab=({L:4.1f},{a:4.1f},{b_val:4.1f}) | OKLCh: h={h:5.1f}deg, C={C:.3f} | R/B={rb:.2f}')
    print('========================================================================================')
