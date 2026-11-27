package com.craftcam.app.gl.shaders

object ShaderConstants {

    private const val THERMAL_UNIFORMS_GLSL = """
// Simulated Thermal Vision Uniforms
uniform bool uThermalEnabled;
uniform int uThermalPalette;
uniform float uThermalSensitivity;
uniform float uThermalContrast;
uniform bool uThermalInverted;
uniform float uThermalSoftness;
uniform float uThermalNoiseIntensity;
uniform bool uThermalAdaptiveContrast;
uniform bool uThermalMaskValid;
uniform int uThermalMaskYFlip;
uniform float uThermalMinLuma;
uniform float uThermalMaxLuma;
uniform sampler2D uThermalPaletteTex;
uniform sampler2D uThermalMaskTex;
uniform bool uThermalHasFace;
uniform vec4 uThermalFaceRect;
"""

    private const val CREATIVE_TEMPERATURE_UNIFORMS_GLSL = """
// Creative Temperature Filter Uniforms (-100 Frozen Blue .. +100 Deep Red, independent from HW WB)
uniform float uCreativeTemperature;
uniform bool uCreativeTempSkinProtect;
"""

    private const val CREATIVE_TEMPERATURE_HELPERS_GLSL = """
vec3 applyCreativeTemperature(vec3 cIn, float tempValue, bool skinProtect) {
    float tNorm = clamp(tempValue / 100.0, -1.0, 1.0);
    if (abs(tNorm) < 0.001) return cIn;

    vec3 c = clamp(cIn, 0.0, 1.0);
    float y0 = dot(c, vec3(0.2126, 0.7152, 0.0722));

    float skinW = 0.0;
    if (skinProtect) {
        float rgbSum = max(0.001, c.r + c.g + c.b);
        float ng = c.g / rgbSum;
        float sDom = smoothstep(0.01, 0.08, c.r - c.g) * smoothstep(0.02, 0.14, c.r - c.b);
        float sGreen = smoothstep(0.22, 0.28, ng) * (1.0 - smoothstep(0.35, 0.40, ng));
        float sLuma = smoothstep(0.08, 0.20, y0) * (1.0 - smoothstep(0.86, 0.96, y0));
        skinW = clamp(sDom * sGreen * sLuma * 1.35, 0.0, 1.0);
    }

    float effT = (tNorm < 0.0) ? (tNorm * (1.0 - 0.56 * skinW)) : (tNorm * (1.0 - 0.52 * skinW));
    float u = abs(effT);
    float u2 = u * u;
    float u3 = u2 * u;
    bool isCold = effT < 0.0;

    float shadowGuard = smoothstep(0.0, 0.15, y0);
    float highlightGuard = 1.0 - 0.46 * smoothstep(0.68, 1.0, y0);
    float chromaBell = 0.42 + 0.58 * (4.0 * y0 * (1.0 - y0));
    float weight = shadowGuard * highlightGuard * chromaBell;

    float dR;
    float dG;
    float dB;
    if (isCold) {
        dR = -(0.36 * u + 0.34 * u2);
        dG = (0.11 * u - 0.22 * u2);
        dB = (0.44 * u + 0.24 * u2);
    } else {
        dR = (0.36 * u + 0.24 * u2);
        float gQuad = 0.44 - 0.16 * skinW;
        float gCube = 0.42 - 0.24 * skinW;
        dG = (0.26 * u - gQuad * u2 - gCube * u3);
        dB = -(0.46 * u + 0.36 * u2);
    }

    float gainR = max(0.08, 1.0 + dR * (0.60 + 0.42 * weight));
    float gainG = max(0.14, 1.0 + dG * (0.58 + 0.38 * weight));
    float gainB = max(0.06, 1.0 + dB * (0.62 + 0.44 * weight));

    float addR = (dR >= 0.0) ? (dR * 0.090 * weight) : (dR * 0.055 * weight * smoothstep(0.05, 0.35, c.r));
    float addG = (dG >= 0.0) ? (dG * 0.070 * weight) : (dG * 0.040 * weight * smoothstep(0.05, 0.35, c.g));
    float addB = (dB >= 0.0) ? (dB * 0.090 * weight) : (dB * 0.050 * weight * smoothstep(0.05, 0.35, c.b));

    vec3 outCol = max(vec3(0.0), vec3(c.r * gainR + addR, c.g * gainG + addG, c.b * gainB + addB));

    float y1 = dot(outCol, vec3(0.2126, 0.7152, 0.0722));
    float lumPreserve = 0.74 * (1.0 - 0.34 * u2);
    float rawRatio = (y1 > 0.0001) ? (y0 / y1) : 1.0;
    float clampedRatio = clamp(rawRatio, 0.54, 1.55);
    float scale = 1.0 + (clampedRatio - 1.0) * lumPreserve;
    outCol *= scale;

    if (!isCold && u > 0.65) {
        float deepRedPull = ((u - 0.65) / 0.35) * 0.14 * (1.0 - 0.55 * skinW) * smoothstep(0.25, 0.90, y0);
        outCol.r *= (1.0 - deepRedPull * 0.25);
        outCol.g *= (1.0 - deepRedPull * 1.15);
        outCol.b *= (1.0 - deepRedPull * 1.65);
    }

    const float knee = 0.84;
    const float headroom = 1.0 - knee;
    float maxCh = max(outCol.r, max(outCol.g, outCol.b));
    if (maxCh > knee && maxCh > 0.0001) {
        float over = maxCh - knee;
        float rolledMax = knee + headroom * (over / (over + headroom * 0.55));
        outCol *= (rolledMax / maxCh);
    }

    vec3 ex = max(vec3(0.0), outCol - vec3(knee));
    vec3 compressed = knee + headroom * (ex / (ex + vec3(headroom * 0.65)));
    outCol = vec3(
        (outCol.r > knee) ? compressed.r : outCol.r,
        (outCol.g > knee) ? compressed.g : outCol.g,
        (outCol.b > knee) ? compressed.b : outCol.b
    );
    return clamp(outCol, 0.0, 1.0);
}
"""

    private const val THERMAL_HELPERS_GLSL = """
float thermalSoftCompress(float v) {
    if (v <= 0.0) return 0.0;
    if (v >= 1.0) return 1.0;
    if (v < 0.06) {
        float t = v / 0.06;
        return 0.06 * (t * (0.75 + 0.25 * t));
    }
    if (v > 0.94) {
        float ex = (v - 0.94) / 0.06;
        return 0.94 + 0.06 * (ex / (1.0 + 0.35 * (1.0 - ex)));
    }
    return clamp(v, 0.0, 1.0);
}

vec3 evaluateThermalVision(
    vec3 baseRgb,
    float lL, float lR, float lT, float lB,
    vec2 maskUv,
    vec2 pixelCoord,
    float seed
) {
    float baseLuma = dot(baseRgb, vec3(0.2126, 0.7152, 0.0722));
    float blurredLuma = 0.25 * (lL + lR + lT + lB);
    float detailEdge = (baseLuma - blurredLuma) * 0.22;
    float sharpLuma = baseLuma + detailEdge;
    float radLuma = mix(sharpLuma, blurredLuma, clamp(uThermalSoftness, 0.0, 1.0));

    float minL = uThermalAdaptiveContrast ? uThermalMinLuma : 0.0;
    float maxL = uThermalAdaptiveContrast ? uThermalMaxLuma : 1.0;
    float span = max(0.28, maxL - minL);
    float normLuma = clamp((radLuma - minL) / span, 0.0, 1.0);
    float warmBias = clamp((baseRgb.r - baseRgb.b) * 0.14, -0.06, 0.11);

    float heat;
    if (uThermalMaskValid) {
        vec2 sampleUv = clamp(vec2(maskUv.x, uThermalMaskYFlip > 0 ? (1.0 - maskUv.y) : maskUv.y), vec2(0.002), vec2(0.998));
        vec2 m = texture(uThermalMaskTex, sampleUv).rg;
        float humanConf = m.r;
        float skinConf = m.g;
        float bgHeat = clamp(normLuma * 0.74 - 0.05 + warmBias * 0.65, 0.0, 0.92);
        float subjInternal = mix(0.42, 0.92, normLuma);
        float subjHeat = clamp(subjInternal + 0.14 * skinConf + warmBias * 0.45, 0.0, 0.98);
        heat = mix(bgHeat, subjHeat, humanConf);
    } else {
        float skinChromaWarm = clamp((baseRgb.r - baseRgb.g) * 0.45 + (baseRgb.r - baseRgb.b) * 0.20, 0.0, 0.25) * smoothstep(0.15, 0.75, baseLuma);
        float baseFallback = clamp(normLuma * 0.92 + warmBias + skinChromaWarm * 0.35, 0.0, 1.0);
        if (uThermalHasFace) {
            vec2 topUv = vec2(maskUv.x, uThermalMaskYFlip > 0 ? (1.0 - maskUv.y) : maskUv.y);
            vec2 fd = (topUv - uThermalFaceRect.xy) / max(uThermalFaceRect.zw, vec2(0.05));
            float faceW = exp(-dot(fd, fd) * 1.15);
            float subjInternal = mix(0.45, 0.95, normLuma);
            heat = mix(baseFallback * 0.85, clamp(subjInternal + warmBias * 0.4, 0.0, 0.98), clamp(faceW, 0.0, 1.0));
        } else {
            heat = baseFallback;
        }
    }

    float gainShift = (uThermalSensitivity - 1.0) * 0.26;
    heat = thermalSoftCompress((heat - 0.5) * uThermalContrast + 0.5 + gainShift);

    float noiseHash = filmNoise(pixelCoord * 0.85, seed);
    if (uThermalNoiseIntensity > 0.001) {
        float colPattern = filmNoise(vec2(floor(pixelCoord.x * 0.5), 1.0), 0.13) * 0.18;
        heat = clamp(heat + (noiseHash + colPattern) * uThermalNoiseIntensity, 0.0, 1.0);
    }

    float palU = (uThermalInverted ? (1.0 - heat) : heat) * (255.0 / 256.0) + (0.5 / 256.0);
    float palV = (float(clamp(uThermalPalette, 0, 4)) + 0.5) / 5.0;
    vec3 palRgb = texture(uThermalPaletteTex, vec2(palU, palV)).rgb;

    float dither = noiseHash * (0.85 / 255.0);
    return clamp(palRgb + vec3(dither), 0.0, 1.0);
}
"""

    const val VERTEX_SHADER_VIEWFINDER = """#version 300 es
layout (location = 0) in vec4 aPosition;
layout (location = 1) in vec4 aTextureCoord;

out vec2 vQuadCoord;

void main() {
    gl_Position = aPosition;
    vQuadCoord = aTextureCoord.xy;
}
"""

    const val FRAGMENT_SHADER_PASSTHROUGH = """#version 300 es
#extension GL_OES_EGL_image_external_essl3 : enable
#extension GL_OES_EGL_image_external : enable
precision highp float;
precision mediump sampler2D;
precision mediump sampler3D;
#ifdef GL_OES_EGL_image_external
precision mediump samplerExternalOES;
#endif
uniform lowp samplerExternalOES sTexture;
uniform mat4 uTexMatrix;
in vec2 vQuadCoord;
out vec4 fragColor;
void main() {
    vec2 oesUV = (uTexMatrix * vec4(vQuadCoord, 0.0, 1.0)).xy;
    fragColor = texture(sTexture, oesUV);
}
"""

    const val VERTEX_SHADER_PASSTHROUGH_ES2 = """attribute vec4 aPosition;
attribute vec4 aTextureCoord;
varying vec2 vQuadCoord;
void main() {
    gl_Position = aPosition;
    vQuadCoord = aTextureCoord.xy;
}
"""

    const val FRAGMENT_SHADER_PASSTHROUGH_ES2 = """#extension GL_OES_EGL_image_external : enable
precision mediump float;
#ifdef GL_OES_EGL_image_external
precision mediump samplerExternalOES;
#endif
uniform lowp samplerExternalOES sTexture;
uniform mat4 uTexMatrix;
varying vec2 vQuadCoord;
void main() {
    vec2 oesUV = (uTexMatrix * vec4(vQuadCoord, 0.0, 1.0)).xy;
    gl_FragColor = texture2D(sTexture, oesUV);
}
"""

    const val FRAGMENT_SHADER_VIEWFINDER = """#version 300 es
#extension GL_OES_EGL_image_external_essl3 : enable
#extension GL_OES_EGL_image_external : enable
precision highp float;
precision mediump sampler2D;
precision mediump sampler3D;
#ifdef GL_OES_EGL_image_external
precision mediump samplerExternalOES;
#endif

uniform lowp samplerExternalOES sTexture;
uniform sampler2D uLutTexture;
uniform mediump sampler3D sLut3D;
uniform sampler2D uLutTextureB;
uniform mediump sampler3D sLut3D_B;
uniform bool uLutEnabled;
uniform bool uUseLut3D;
uniform bool uHasSecondaryLut;
uniform float uDualLutMix;
uniform int uLutInputSpace; // 0=SRGB, 1=LINEAR, 2=CUSTOM_PRETRANSFORM
uniform bool uPreLinearizationEnabled;
uniform float uPreLinearizationStrength;
uniform float uFilterIntensity;
uniform float uLowerMidtoneStrength;
uniform float uClarityStrength;
uniform float uSkinProtection;
uniform mat4 uTexMatrix;
uniform vec2 uViewportSize;
in vec2 vQuadCoord;
out vec4 fragColor;

// Tonal and Color Grading
uniform float uContrast;
uniform float uSaturation;
uniform float uTemperature;
uniform float uTint;
uniform float uGamma;
uniform float uFade;
uniform float uToeLift;
uniform float uShoulderClip;
uniform float uHighlightRollOff;
uniform vec3 uShadowTint;

// Lens and Optical Effects
uniform float uVignetteIntensity;
uniform float uVignetteRadius;
uniform float uChromaticAberration;
uniform float uBarrelDistortion;
uniform float uSoftness;

// Dedicated Camera Lens System
uniform int uLensType; // 0=NORMAL, 1=WIDE, 2=FISHEYE_F, 3=FISHEYE_W, 4=PRISM, 5=STARBURST, 6=ND, 7=COLOR_FLASH
uniform float uLensDistortion;
uniform float uLensEdgeCurvature;
uniform float uLensVignette;
uniform float uLensPrismDispersion;
uniform float uLensStarburstStrength;
uniform float uLensStarburstThreshold;
uniform float uLensNdFactor;
uniform vec3 uLensColorFlashTint;
uniform bool uLensIsFlashActive;

// D3D Single-Image 3D Photography Filter
uniform bool uD3DEnabled;
uniform float uD3DStrength;
uniform float uD3DParallax;
uniform float uD3DRgbSeparation;
uniform float uD3DAnaglyph;

// Film Grain
uniform float uGrainIntensity;
uniform float uGrainScale;
uniform bool uIsColorNoise;
uniform float uRandomSeed;
uniform sampler2D sGrainTexture;
uniform bool uHasGrainTexture;
uniform int uGrainBlendMode;
uniform vec2 uGrainOffset;
uniform float uGrainShadowStrength;
uniform float uGrainHighlightStrength;
uniform bool uGrainLumaWeightEnabled;
uniform float uGrainLumaWeightPower;

// Halation and Light Leaks
uniform bool uHalationEnabled;
uniform float uHalationThreshold;
uniform float uHalationIntensity;
uniform vec3 uHalationColorTint;
uniform bool uLightLeakEnabled;
uniform float uLightLeakIntensity;
uniform float uScanlineIntensity;

// Developer Visual A/B Stage Toggles (Section 24)
uniform bool uDebugStagePreLinearization;
uniform bool uDebugStageLut;
uniform bool uDebugStageToneCurve;
uniform bool uDebugStageGrain;
uniform bool uDebugStageHalation;
uniform bool uDebugStageOptics;
uniform bool uDebugStageVignette;

// Natural Camera Engine
uniform bool uNaturalModeEnabled;
uniform float uNaturalShadowLift;

// Pro Camera Tools (Focus Peaking & Zebra Warning)
uniform bool uFocusPeakingEnabled;
uniform vec3 uPeakingColor;
uniform float uPeakingThreshold;
uniform bool uZebraEnabled;
uniform float uZebraThreshold;
uniform float uZebraTime;

uniform int uDiagnosticMode;
$THERMAL_UNIFORMS_GLSL
$CREATIVE_TEMPERATURE_UNIFORMS_GLSL

// Stochastic sine-free hash for authentic silver halide film grain (precision-safe)
float filmNoise(vec2 coord, float seed) {
    vec2 p = mod(coord, 1024.0) + vec2(fract(seed * 1.6180339887), fract(seed * 1.4142135623)) * 64.0;
    vec3 p3 = fract(vec3(p.xyx) * vec3(0.1031, 0.1030, 0.0973));
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z) - 0.5;
}

// 2-Scale Cellular Dye Cloud: Hermite stochastic interpolation creates organic physical crystal clusters
float dyeCloudNoise(vec2 coord, float seed) {
    vec2 p = mod(coord, 512.0) + fract(seed * 1.6180339) * 32.0;
    vec2 ip = floor(p);
    vec2 fp = fract(p);
    vec2 u = fp * fp * (3.0 - 2.0 * fp);
    
    float n00 = filmNoise(ip, seed);
    float n10 = filmNoise(ip + vec2(1.0, 0.0), seed);
    float n01 = filmNoise(ip + vec2(0.0, 1.0), seed);
    float n11 = filmNoise(ip + vec2(1.0, 1.0), seed);
    
    return mix(mix(n00, n10, u.x), mix(n01, n11, u.x), u.y);
}

// Trilinear 3D Hald CLUT Sampling (512x512 = 8x8 tiles of 64x64 slices)
vec3 sampleHaldLut(sampler2D lut, vec3 rgb) {
    rgb = clamp(rgb, 0.0, 1.0);
    float blueVal = rgb.b * 63.0;
    
    float slice0 = floor(blueVal);
    float slice1 = min(63.0, slice0 + 1.0);
    float blueFract = fract(blueVal);
    
    float s0_col = mod(slice0, 8.0);
    float s0_row = floor(slice0 / 8.0);
    
    float s1_col = mod(slice1, 8.0);
    float s1_row = floor(slice1 / 8.0);
    
    // Pixel coordinates in 512x512 texture
    float px0_x = s0_col * 64.0 + 0.5 + rgb.r * 63.0;
    float px1_x = s1_col * 64.0 + 0.5 + rgb.r * 63.0;
    
    // In image bitmap, row 0 is at top, but in OpenGL v=0 is bottom
    float px0_y = s0_row * 64.0 + 0.5 + rgb.g * 63.0;
    float px1_y = s1_row * 64.0 + 0.5 + rgb.g * 63.0;
    
    vec2 uv0 = vec2(px0_x / 512.0, px0_y / 512.0);
    vec2 uv1 = vec2(px1_x / 512.0, px1_y / 512.0);
    
    vec3 col0 = texture(lut, uv0).rgb;
    vec3 col1 = texture(lut, uv1).rgb;
    
    return mix(col0, col1, blueFract);
}

// Rational pre-linearization curve: flattens aggressive ISP contrast before 3D LUT sampling (VSCO-style)
vec3 preLinearizeLutInput(vec3 c, float strength) {
    vec3 linearC;
    linearC.r = (c.r <= 0.5) ? pow(c.r * 2.0, 1.25) * 0.5 : 1.0 - pow((1.0 - c.r) * 2.0, 1.25) * 0.5;
    linearC.g = (c.g <= 0.5) ? pow(c.g * 2.0, 1.25) * 0.5 : 1.0 - pow((1.0 - c.g) * 2.0, 1.25) * 0.5;
    linearC.b = (c.b <= 0.5) ? pow(c.b * 2.0, 1.25) * 0.5 : 1.0 - pow((1.0 - c.b) * 2.0, 1.25) * 0.5;
    return mix(c, clamp(linearC, 0.0, 1.0), clamp(strength, 0.0, 1.0));
}

// Hardware-accelerated 3D Texture single-sample trilinear lookup (GLES 3.0 GL_TEXTURE_3D)
vec3 sample3DLut(mediump sampler3D lut3d, vec3 rgb) {
    rgb = clamp(rgb, 0.0, 1.0);
    vec3 lutCoord = rgb * (63.0 / 64.0) + (0.5 / 64.0);
    return texture(lut3d, lutCoord).rgb;
}
$THERMAL_HELPERS_GLSL
$CREATIVE_TEMPERATURE_HELPERS_GLSL

void main() {
    // Physical isotropic coordinates (aspect-corrected from uViewportSize)
    float aspect = uViewportSize.x / max(uViewportSize.y, 1.0);
    bool isPortrait = aspect <= 1.0;

    vec2 pRaw = vQuadCoord - 0.5;
    vec2 pPhys = isPortrait ? vec2(pRaw.x, pRaw.y / aspect) : vec2(pRaw.x * aspect, pRaw.y);
    float rPhys = length(pPhys);

    vec2 p = pRaw;
    float r2 = rPhys * rPhys;
    float r = rPhys;

    // 1. Lens Optical Distortion (Fisheye F / Fisheye W / Wide / Barrel)
    vec2 uv = vQuadCoord;
    float fisheyeMask = 1.0;

    if (uDebugStageOptics) {
        if (uLensType == 2) { // FISHEYE_F: Classic circular hemispherical fisheye bubble
            float rBubble = 0.44; // Safe radius ensuring full 360° circular clearance without border cutoff
            if (rPhys > rBubble) {
                fisheyeMask = 0.0;
                uv = vec2(0.5);
            } else {
                float rn = rPhys / rBubble;
                // Authentic hemispherical fisheye warp: cubic radial expansion
                // rn goes 0..1, rDist goes 0..1 with moderate center magnification (1.43x) and perimeter compression
                float rDistNorm = rn * (0.70 + 0.30 * rn * rn);
                float rDistPhys = rDistNorm * 0.44;
                vec2 dir = pPhys / max(rPhys, 0.0001);
                vec2 pDistPhys = dir * rDistPhys;
                vec2 pDist = isPortrait ? vec2(pDistPhys.x, pDistPhys.y * aspect) : vec2(pDistPhys.x / aspect, pDistPhys.y);
                uv = 0.5 + pDist;
                fisheyeMask = 1.0 - smoothstep(rBubble - 0.012, rBubble, rPhys);
            }
        } else if (uLensType == 3) { // FISHEYE_W: Full-frame wide-angle curvilinear fisheye
            float rCorner = isPortrait ? length(vec2(0.5, 0.5 / aspect)) : length(vec2(0.5 * aspect, 0.5));
            float rn = clamp(rPhys / rCorner, 0.0, 1.0);
            float rDistNorm = rn * (0.80 + 0.20 * rn * rn);
            float rDistPhys = rDistNorm * rCorner;
            vec2 dir = pPhys / max(rPhys, 0.0001);
            vec2 pDistPhys = dir * rDistPhys;
            vec2 pDist = isPortrait ? vec2(pDistPhys.x, pDistPhys.y * aspect) : vec2(pDistPhys.x / aspect, pDistPhys.y);
            uv = 0.5 + pDist;
        } else if (uLensType == 1) { // WIDE: Moderate barrel field expansion (isotropic)
            vec2 pDistPhys = pPhys * (1.0 + uLensDistortion * (rPhys * rPhys) * 0.35);
            vec2 pDist = isPortrait ? vec2(pDistPhys.x, pDistPhys.y * aspect) : vec2(pDistPhys.x / aspect, pDistPhys.y);
            uv = 0.5 + pDist;
        } else if (abs(uBarrelDistortion) > 0.001) {
            vec2 pDistPhys = pPhys * (1.0 + uBarrelDistortion * (rPhys * rPhys) * 0.35);
            vec2 pDist = isPortrait ? vec2(pDistPhys.x, pDistPhys.y * aspect) : vec2(pDistPhys.x / aspect, pDistPhys.y);
            uv = 0.5 + pDist;
        }
    }
    uv = clamp(uv, vec2(0.001), vec2(0.999));

    // 2. Transverse Chromatic Aberration & Spectral Dispersion
    vec4 srcColor;
    float effCA = (uDebugStageOptics && !uNaturalModeEnabled) ? uChromaticAberration : 0.0;
    if (uDebugStageOptics) {
        if (uLensType == 2) effCA = max(effCA, 0.016);
        else if (uLensType == 3) effCA = max(effCA, 0.008);
        else if (uLensType == 4) effCA = max(effCA, 0.010);
    }

    if (effCA > 0.0001 && fisheyeMask > 0.001) {
        vec2 caDir = pPhys / max(rPhys, 0.0001);
        vec2 caPhysOffset = caDir * (effCA * rPhys * rPhys * 1.5);
        vec2 caOffset = isPortrait ? vec2(caPhysOffset.x, caPhysOffset.y * aspect) : vec2(caPhysOffset.x / aspect, caPhysOffset.y);
        vec2 uvR = clamp(uv + caOffset, vec2(0.001), vec2(0.999));
        vec2 uvG = clamp(uv - caOffset * 0.15, vec2(0.001), vec2(0.999));
        vec2 uvB = clamp(uv - caOffset * 1.25, vec2(0.001), vec2(0.999));

        vec2 oesR = (uTexMatrix * vec4(uvR, 0.0, 1.0)).xy;
        vec2 oesG = (uTexMatrix * vec4(uvG, 0.0, 1.0)).xy;
        vec2 oesB = (uTexMatrix * vec4(uvB, 0.0, 1.0)).xy;

        float rCh = texture(sTexture, oesR).r;
        float gCh = texture(sTexture, oesG).g;
        float bCh = texture(sTexture, oesB).b;
        srcColor = vec4(rCh, gCh, bCh, 1.0);
    } else {
        vec2 oesUV = (uTexMatrix * vec4(uv, 0.0, 1.0)).xy;
        srcColor = texture(sTexture, oesUV);
    }

    vec3 color = srcColor.rgb;
    vec3 preGradeColor = color;

    // Diagnostic Mode 1: Passthrough (Raw Camera Feed, zero effects)
    if (uDiagnosticMode == 1) {
        fragColor = vec4(preGradeColor, 1.0);
        return;
    }

    // Dedicated Simulated Thermal Vision Pipeline (bypasses film LUTs & film grain)
    if (uThermalEnabled) {
        vec2 tStep = 2.0 / max(uViewportSize, vec2(320.0));
        float lL = dot(texture(sTexture, (uTexMatrix * vec4(clamp(uv - vec2(tStep.x, 0.0), vec2(0.001), vec2(0.999)), 0.0, 1.0)).xy).rgb, vec3(0.2126, 0.7152, 0.0722));
        float lR = dot(texture(sTexture, (uTexMatrix * vec4(clamp(uv + vec2(tStep.x, 0.0), vec2(0.001), vec2(0.999)), 0.0, 1.0)).xy).rgb, vec3(0.2126, 0.7152, 0.0722));
        float lT = dot(texture(sTexture, (uTexMatrix * vec4(clamp(uv - vec2(0.0, tStep.y), vec2(0.001), vec2(0.999)), 0.0, 1.0)).xy).rgb, vec3(0.2126, 0.7152, 0.0722));
        float lB = dot(texture(sTexture, (uTexMatrix * vec4(clamp(uv + vec2(0.0, tStep.y), vec2(0.001), vec2(0.999)), 0.0, 1.0)).xy).rgb, vec3(0.2126, 0.7152, 0.0722));
        vec3 thermalRgb = evaluateThermalVision(preGradeColor, lL, lR, lT, lB, vQuadCoord, vQuadCoord * uViewportSize, uRandomSeed);
        fragColor = vec4(thermalRgb, 1.0);
        return;
    }

    // Pre-grade normalized chromaticity & continuous physiological skin locus (derived from uncorrupted camera input)
    float preRgbSum = max(0.0001, preGradeColor.r + preGradeColor.g + preGradeColor.b);
    float preNormR = preGradeColor.r / preRgbSum;
    float preNormG = preGradeColor.g / preRgbSum;
    float preLuma = dot(preGradeColor, vec3(0.2126, 0.7152, 0.0722));

    // Elliptical skin locus in normalized chromaticity (major axis along melanin line: theta = -0.42 rad)
    // Center: (0.42, 0.32), Axis U (melanin shift): 0.080, Axis V (erythema / blood shift): 0.038
    float dr = preNormR - 0.42;
    float dg = preNormG - 0.32;
    float uDist = dr * 0.913 - dg * 0.408;
    float vDist = dr * 0.408 + dg * 0.913;
    float skinDistSq = (uDist * uDist) / (0.080 * 0.080) + (vDist * vDist) / (0.038 * 0.038);
    float isSkinProb = exp(-0.5 * skinDistSq);

    // Continuous luminance gating: avoids crushed dark shadows (<0.08) and specular highlights (>0.85)
    float lumaGate = smoothstep(0.08, 0.20, preLuma) * (1.0 - smoothstep(0.85, 0.98, preLuma));
    float rawSkinWeight = isSkinProb * lumaGate;
    float skinWeight = clamp(rawSkinWeight * uSkinProtection, 0.0, 1.0);

    // 2-D3D. Live Viewfinder Stability:
    // D3D stereoscopy is synthesized upon capture as an authentic multi-frame wigglegram.
    // The live preview remains clean, stable, and tack-sharp with D3D film color science and grain (zero UV stretching).

    // 2a. Prism Multi-Facet Refraction & Chromatic Splitting
    if (uLensType == 4 && uLensPrismDispersion > 0.01) {
        float disp = (0.038 + r * 0.028) * uLensPrismDispersion;
        vec2 off1 = vec2(disp * 0.866, disp * 0.50);
        vec2 off2 = vec2(-disp * 0.866, disp * 0.50);

        vec2 oesP1 = (uTexMatrix * vec4(clamp(uv + off1, vec2(0.001), vec2(0.999)), 0.0, 1.0)).xy;
        vec2 oesP2 = (uTexMatrix * vec4(clamp(uv + off2, vec2(0.001), vec2(0.999)), 0.0, 1.0)).xy;

        vec3 prism1 = texture(sTexture, oesP1).rgb;
        vec3 prism2 = texture(sTexture, oesP2).rgb;
        prism1.r *= 1.08;
        prism2.b *= 1.12;

        float facetWeight = clamp(r * 1.35 * uLensPrismDispersion, 0.0, 0.45);
        color = mix(color, (prism1 + prism2) * 0.5, facetWeight);
    }

    // 2a-star. Starburst Diffraction Rays (optimized 2-pass sampling for smooth preview)
    if (uLensType == 5 && uLensStarburstStrength > 0.01) {
        float starThresh = uLensStarburstThreshold;
        vec2 starStep = vec2(5.5) / uViewportSize;
        vec3 starAccum = vec3(0.0);
        for (int i = 1; i <= 2; i++) {
            float fi = float(i) * 2.5;
            vec2 d1 = vec2(fi * starStep.x, fi * starStep.y);
            vec2 d2 = vec2(-fi * starStep.x, fi * starStep.y);

            vec3 s1 = texture(sTexture, (uTexMatrix * vec4(clamp(uv + d1, vec2(0.001), vec2(0.999)), 0.0, 1.0)).xy).rgb;
            vec3 s2 = texture(sTexture, (uTexMatrix * vec4(clamp(uv - d1, vec2(0.001), vec2(0.999)), 0.0, 1.0)).xy).rgb;
            vec3 s3 = texture(sTexture, (uTexMatrix * vec4(clamp(uv + d2, vec2(0.001), vec2(0.999)), 0.0, 1.0)).xy).rgb;
            vec3 s4 = texture(sTexture, (uTexMatrix * vec4(clamp(uv - d2, vec2(0.001), vec2(0.999)), 0.0, 1.0)).xy).rgb;

            float w = 1.0 / float(i);
            starAccum += (max(vec3(0.0), s1 - starThresh) +
                          max(vec3(0.0), s2 - starThresh) +
                          max(vec3(0.0), s3 - starThresh) +
                          max(vec3(0.0), s4 - starThresh)) * w;
        }
        color += starAccum * (uLensStarburstStrength * 0.90);
    }

    // 2a-nd. Neutral Density Filter Light Attenuation
    if (uLensType == 6 && uLensNdFactor < 0.99) {
        color *= uLensNdFactor;
    }

    // 2a-flash. Color Gel Flash Tint
    if (uLensType == 7 && uLensIsFlashActive) {
        color = mix(color, color * uLensColorFlashTint * 1.30, 0.70);
    }

    // 2b. Optical Softness (Contrast-preserving spherical veiling flare)
    if (uSoftness > 0.005) {
        float rSoftness = clamp(r2 * 2.8 * uSoftness, 0.0, 0.85);
        if (rSoftness > 0.01) {
            vec2 sStep = (3.0 / uViewportSize) * (1.0 + rSoftness * 1.5);
            vec3 sL = texture(sTexture, (uTexMatrix * vec4(clamp(uv - vec2(sStep.x, 0.0), vec2(0.001), vec2(0.999)), 0.0, 1.0)).xy).rgb;
            vec3 sR = texture(sTexture, (uTexMatrix * vec4(clamp(uv + vec2(sStep.x, 0.0), vec2(0.001), vec2(0.999)), 0.0, 1.0)).xy).rgb;
            vec3 sT = texture(sTexture, (uTexMatrix * vec4(clamp(uv - vec2(0.0, sStep.y), vec2(0.001), vec2(0.999)), 0.0, 1.0)).xy).rgb;
            vec3 sB = texture(sTexture, (uTexMatrix * vec4(clamp(uv + vec2(0.0, sStep.y), vec2(0.001), vec2(0.999)), 0.0, 1.0)).xy).rgb;
            vec3 softSample = (sL + sR + sT + sB) * 0.25;
            
            vec3 diffuseGlow = max(vec3(0.0), softSample - color * 0.75);
            color = mix(color, softSample, rSoftness * 0.25) + diffuseGlow * (rSoftness * 0.35);
        }
    }

    // 2c. Camcorder Chroma Subcarrier Delay (Horizontal Color Smear)
    if (uScanlineIntensity > 0.01) {
        vec2 chromaOffset = vec2(3.5 / uViewportSize.x, 0.0);
        vec3 delayedChroma = texture(sTexture, (uTexMatrix * vec4(clamp(uv - chromaOffset, vec2(0.001), vec2(0.999)), 0.0, 1.0)).xy).rgb;
        color.r = mix(color.r, delayedChroma.r, 0.35);
        color.b = mix(color.b, delayedChroma.b, 0.45);
    }

    float luma = dot(color, vec3(0.2126, 0.7152, 0.0722));

    // 2d. Photochemical 3D Film Emulsion LUT with Dual-LUT Crossfading (Section 7)
    if (uDiagnosticMode == 3) {
        // Mode 3: Profile Adjustments Only (Bypasses LUT)
    } else if (uLutEnabled && uDebugStageLut) {
        vec3 lutInput = color;
        if (uLutInputSpace == 1) { // LINEAR
            lutInput = pow(max(lutInput, vec3(0.0)), vec3(2.2));
        } else if (uLutInputSpace == 2) { // CUSTOM_PRETRANSFORM (ISP Contrast Flattening)
            if (uPreLinearizationEnabled && uDebugStagePreLinearization) {
                lutInput = preLinearizeLutInput(lutInput, uPreLinearizationStrength);
            }
        }

        vec3 lutColor;
        if (uUseLut3D) {
            lutColor = sample3DLut(sLut3D, lutInput);
            if (uHasSecondaryLut && uDualLutMix > 0.001) {
                vec3 lutColorB = sample3DLut(sLut3D_B, lutInput);
                lutColor = mix(lutColor, lutColorB, clamp(uDualLutMix, 0.0, 1.0));
            }
        } else {
            lutColor = sampleHaldLut(uLutTexture, lutInput);
            if (uHasSecondaryLut && uDualLutMix > 0.001) {
                vec3 lutColorB = sampleHaldLut(uLutTextureB, lutInput);
                lutColor = mix(lutColor, lutColorB, clamp(uDualLutMix, 0.0, 1.0));
            }
        }
        color = mix(color, lutColor, clamp(uFilterIntensity, 0.0, 1.0));
    }

    // Diagnostic Mode 2: LUT Only (Bypasses profile adjustments and skin protection)
    if (uDiagnosticMode == 2) {
        fragColor = vec4(clamp(color, 0.0, 1.0), 1.0);
        return;
    }

    // 2e. CAMELEO Natural Mode Processing
    if (uNaturalModeEnabled) {
        vec3 rMat = vec3(1.024, -0.016, -0.008);
        vec3 gMat = vec3(-0.012, 1.016, -0.004);
        vec3 bMat = vec3(-0.006, 0.012, 0.994);
        color = vec3(dot(color, rMat), dot(color, gMat), dot(color, bMat));

        // Continuous max-channel asymptotic highlight shoulder (preserves skin & cloud chromaticity ratios)
        float knee = 0.68;
        float maxCh = max(max(color.r, color.g), color.b);
        if (maxCh > knee) {
            float d = maxCh - knee;
            float s = 1.0 - knee;
            float rolledMax = knee + s * (d / (d + s));
            color = color * (rolledMax / maxCh);
            float coreLuma = dot(color, vec3(0.2126, 0.7152, 0.0722));
            float desat = clamp((coreLuma - knee) / (1.0 - knee), 0.0, 1.0);
            color = mix(color, vec3(coreLuma), desat * 0.40);
        }

        // Adaptive rational shadow toe lift (true black preserved at Y=0)
        float yLuma = dot(color, vec3(0.2126, 0.7152, 0.0722));
        float lift = uNaturalShadowLift * (1.0 - clamp(yLuma, 0.0, 1.0)) * (1.0 - clamp(yLuma, 0.0, 1.0)) * clamp(yLuma, 0.0, 1.0) * 1.6;
        color += vec3(lift);
    }
    
    // 3. Preset & User Temperature / Tint (Warm/Cool Shift)
    if (abs(uTemperature) > 0.001) {
        color.r += 0.10 * uTemperature;
        color.b -= 0.10 * uTemperature;
    }
    if (abs(uTint) > 0.001) {
        color.g += 0.06 * uTint;
        color.r -= 0.03 * uTint;
        color.b -= 0.03 * uTint;
    }

    // 4. Preset & User Contrast
    if (uDebugStageToneCurve) {
        if (!uNaturalModeEnabled) {
            luma = dot(color, vec3(0.2126, 0.7152, 0.0722));
            float sLift = uFilterIntensity * 0.035 * (1.0 - clamp(luma, 0.0, 1.0)) * (1.0 - clamp(luma, 0.0, 1.0));
            color += vec3(sLift);
            luma = dot(color, vec3(0.2126, 0.7152, 0.0722));
        }

        if (abs(uContrast - 1.0) > 0.01) {
            float pivot = 0.26;
            color = (color - pivot) * uContrast + pivot;
            if (uLowerMidtoneStrength == 0.0) color = clamp(color, 0.0, 1.0);
        }

        if (uLowerMidtoneStrength > 0.0) {
            float y = dot(color, vec3(0.2126, 0.7152, 0.0722));
            float x = clamp((y - 0.035) / 0.65, 0.0, 1.0);
            float envelope = 4.0 * x * (1.0 - x);
            float toe = 1.0 - smoothstep(0.0, 0.18, y);
            color += vec3(uLowerMidtoneStrength * envelope * envelope - 0.029 * uFilterIntensity * toe);
        }

        // 4b. Authentic Midtone Clarity (from DAZZ Camera Engine)
        if (abs(uClarityStrength) > 0.005) {
            float boost = 1.0 + uClarityStrength * 0.18;
            float offset = -uClarityStrength * 0.18 * 0.5;
            color = clamp(color * boost + offset, 0.0, 1.0);
            luma = dot(color, vec3(0.2126, 0.7152, 0.0722));
        }

        // 5. Preset & User Saturation with Smart Vibrance Protection
        luma = dot(color, vec3(0.2126, 0.7152, 0.0722));
        if (uLowerMidtoneStrength > 0.0) {
            color = mix(vec3(luma), color, uSaturation);
        } else if (abs(uSaturation - 1.0) > 0.01) {
            const float lr = 0.2126;
            const float lg = 0.7152;
            const float lb = 0.0722;
            float sat = uSaturation;
            float sr = (1.0 - sat) * lr;
            float sg = (1.0 - sat) * lg;
            float sb = (1.0 - sat) * lb;
            vec3 vibColor = clamp(vec3(
                color.r * (sr + sat) + color.g * sg + color.b * sb,
                color.r * sr + color.g * (sg + sat) + color.b * sb,
                color.r * sr + color.g * sg + color.b * (sb + sat)
            ), 0.0, 1.0);
            color = mix(mix(vec3(luma), color, uSaturation), vibColor, clamp(uSkinProtection, 0.0, 1.0));
            luma = dot(color, vec3(0.2126, 0.7152, 0.0722));
        }

        // 5b. Authentic Photographic Skin Tone Protection (Redesigned Continuous Chromaticity Preservation)
        // Diagnostic Mode 4: Combined LUT + Profile (Skin protection bypassed)
        // Diagnostic Mode 5: Skin Protection Mask Visualization
        if (uDiagnosticMode == 5) {
            fragColor = vec4(vec3(skinWeight), 1.0);
            return;
        }

        if (skinWeight > 0.001 && uDiagnosticMode != 4) {
            // 1. Smooth Mustard / Excess Yellow-Green Suppression (Prevents sickly green-yellow cast)
            float postRgbSum = max(0.0001, color.r + color.g + color.b);
            float postNormG = color.g / postRgbSum;
            float mustardExcess = smoothstep(0.33, 0.38, postNormG);
            if (mustardExcess > 0.0) {
                float targetG = postRgbSum * 0.315;
                color.g = mix(color.g, targetG, mustardExcess * skinWeight * 0.75);
            }

            // 2. Continuous Blue Headroom Preservation (Prevents plastic yellowing under direct flash/tungsten)
            float minBlue = color.r * 0.30;
            if (color.b < minBlue) {
                color.b += (minBlue - color.b) * skinWeight * 0.60;
            }

            // 3. Asymptotic Soft-Knee Highlight Compression (Prevents hard R=255 clipping on skin highlights)
            if (color.r > 0.82) {
                float knee = 0.82;
                float excess = color.r - knee;
                float headroom = 1.0 - knee;
                float compressedR = knee + headroom * (excess / (excess + headroom * 0.50));
                color.r = mix(color.r, compressedR, skinWeight);
            }
            luma = dot(color, vec3(0.2126, 0.7152, 0.0722));
        }

        // 6. Gamma Response
        if (abs(uGamma - 1.0) > 0.01) {
            color = pow(max(color, vec3(0.0)), vec3(1.0 / uGamma));
            luma = dot(color, vec3(0.2126, 0.7152, 0.0722));
        }

        // 7. Shadow Tint (Photochemical dye bias in toe)
        float shadowWeight = clamp(1.0 - luma * 3.0, 0.0, 1.0);
        color += uShadowTint * shadowWeight;

        // 8. Filmic Asymptotic Highlight Shoulder Rolloff (preserves hue/chromaticity into specular highlights)
        if (uHighlightRollOff < 1.0 && !uNaturalModeEnabled) {
            float knee = uHighlightRollOff;
            float maxCh = max(max(color.r, color.g), color.b);
            if (maxCh > knee) {
                float d = maxCh - knee;
                float s = 1.0 - knee;
                float rolledMax = knee + s * (d / (d + s));
                color = color * (rolledMax / maxCh);
                
                float coreLuma = dot(color, vec3(0.2126, 0.7152, 0.0722));
                float desat = clamp((coreLuma - knee) / (1.0 - knee), 0.0, 1.0);
                color = mix(color, vec3(coreLuma), desat * 0.45);
            }
            luma = dot(color, vec3(0.2126, 0.7152, 0.0722));
        }

        // 8b. Lifted Toe & Shoulder Compression (Lightroom Film Profile Parity)
        if (uToeLift > 0.001 || uShoulderClip < 0.999) {
            color = clamp(color, 0.0, 1.0);
            color = uToeLift + color * (uShoulderClip - uToeLift);
            luma = dot(color, vec3(0.2126, 0.7152, 0.0722));
        }

        // 9. Film Fade (Lifted blacks / aged paper floor)
        if (uFade > 0.001) {
            color = color * (1.0 - uFade) + uFade;
        }
    }

    // 9b. Creative Temperature Filter (-100 Frozen Blue .. +100 Deep Red)
    if (abs(uCreativeTemperature) > 0.05) {
        color = applyCreativeTemperature(color, uCreativeTemperature, uCreativeTempSkinProtect);
        luma = dot(color, vec3(0.2126, 0.7152, 0.0722));
    }

    // 10. Concentric Dual-Radius Film Halation (Zero-Stall Analytical Preview Model for smooth 60fps)
    if (uHalationEnabled && uHalationIntensity > 0.01 && uDebugStageHalation) {
        float kThresh = uHalationThreshold;
        if (luma > kThresh) {
            float spread = smoothstep(kThresh, 1.0, luma);
            float coreLuma = clamp((luma - kThresh) / max(0.01, 1.0 - kThresh), 0.0, 1.0);
            vec3 haloBase = mix(uHalationColorTint, vec3(0.40, 0.38, 0.35), coreLuma * 0.85);
            vec3 haloColor = haloBase * (spread * uHalationIntensity * 0.40);
            color += haloColor;
        }
    }

    // 11. Subtle Edge Light Leak Emulation
    if (uLightLeakEnabled && uLightLeakIntensity > 0.01) {
        float edgeDist = length(vec2(max(0.0, uv.x - 0.75) * 2.0, max(0.0, 0.25 - uv.y) * 2.0));
        float leakMask = 1.0 - smoothstep(0.0, 0.40, edgeDist);
        vec3 leakColor = vec3(1.0, 0.45, 0.12) * (leakMask * uLightLeakIntensity * 0.35);
        color = 1.0 - (1.0 - color) * (1.0 - leakColor);
    }

    // 12. Optical Cosine Fourth Lens Vignette
    if (uDebugStageVignette) {
        float vigTotal = uVignetteIntensity + uLensVignette;
        if (vigTotal > 0.01) {
            float rNorm = length(p) / 0.7071068;
            float rn = rNorm / max(0.72, uVignetteRadius);
            float cos4Vig = 1.0 / pow(1.0 + rn * rn * 0.45, 2.0);
            float vignette = mix(1.0, cos4Vig, clamp(vigTotal * 0.65, 0.0, 0.55));
            color *= clamp(vignette, 0.50, 1.0);
        }
    }

    // 13. Authentic Scanned Photographic Film Halide Grain
    if (uGrainIntensity > 0.005 && uDebugStageGrain) {
        float shadowT = clamp(luma / 0.35, 0.0, 1.0);
        float shadowMask = shadowT * shadowT * (3.0 - 2.0 * shadowT);
        float highlightT = clamp((luma - 0.65) / 0.35, 0.0, 1.0);
        float highlightMask = 1.0 - (highlightT * highlightT * (3.0 - 2.0 * highlightT));
        float grainWeight = (uGrainShadowStrength + (1.0 - uGrainShadowStrength) * shadowMask) * 
                            (uGrainHighlightStrength + (1.0 - uGrainHighlightStrength) * highlightMask);

        // Section 9: Parabolic midtone luminance weighting M(L) = 4L(1-L)
        if (uGrainLumaWeightEnabled && uGrainLumaWeightPower > 0.01) {
            float midtoneParabola = clamp(4.0 * luma * (1.0 - luma), 0.0, 1.0);
            grainWeight = mix(grainWeight, grainWeight * midtoneParabola, uGrainLumaWeightPower);
        }
        grainWeight = max(0.35, grainWeight);

        if (uHasGrainTexture) {
            vec2 grainUv = fract(vQuadCoord * (uViewportSize / 512.0) * (uGrainScale * 0.75) + uGrainOffset);
            vec4 gSample = texture(sGrainTexture, grainUv);
            float gLuma = dot(gSample.rgb, vec3(0.299, 0.587, 0.114));
            float gDev = (gLuma - 0.5) * 4.5;
            float effIntensity = uGrainIntensity * grainWeight * 2.0;
            
            if (uGrainBlendMode == 1) { // SCREEN
                float gNorm = clamp((gDev + 1.0) * 0.5, 0.0, 1.0);
                float sFactor = effIntensity * 0.90;
                if (uIsColorNoise) {
                    vec3 cDev = (gSample.rgb - vec3(0.5)) * 4.5;
                    vec3 cNorm = clamp((cDev + 1.0) * 0.5, 0.0, 1.0);
                    color = 1.0 - (1.0 - color) * (1.0 - cNorm * sFactor);
                } else {
                    color = 1.0 - (1.0 - color) * (1.0 - vec3(gNorm) * sFactor);
                }
            } else if (uGrainBlendMode == 2) { // MULTIPLY
                float gNorm = clamp((gDev + 1.0) * 0.5, 0.0, 1.0);
                float mFactor = 1.0 - (1.0 - gNorm) * effIntensity * 0.90;
                color *= mFactor;
            } else if (uGrainBlendMode == 3) { // NORMAL
                float gNorm = clamp((gDev + 1.0) * 0.5, 0.0, 1.0);
                color = mix(color, vec3(gNorm), effIntensity * 0.55);
            } else { // OVERLAY (mode 0)
                vec3 delta;
                if (uIsColorNoise) {
                    vec3 cDev = (gSample.rgb - vec3(0.5)) * 4.5;
                    delta = cDev * effIntensity * 0.95;
                } else {
                    delta = vec3(gDev) * effIntensity * 0.95;
                }
                color += delta;
            }
        } else {
            // High-performance single-pass film grain for preview (avoids 15x hash stall)
            vec2 grainCoord = vQuadCoord * uViewportSize.xy * (uGrainScale * 0.45);
            float noiseG = filmNoise(grainCoord, uRandomSeed + 0.18);
            vec3 grainDelta = vec3(noiseG);
            if (uIsColorNoise) {
                float noiseR = filmNoise(grainCoord + vec2(17.0, 31.0), uRandomSeed + 0.31);
                float noiseB = filmNoise(grainCoord + vec2(43.0, 71.0), uRandomSeed + 0.53);
                grainDelta = vec3(noiseR, noiseG, noiseB);
            }
            float grainDamp = (uScanlineIntensity > 0.01) ? 0.35 : 1.0;
            color += grainDelta * (grainWeight * uGrainIntensity * 1.35 * grainDamp);
        }
    }

    // 14. Retro Camcorder Tape Texture & Line Modulation
    if (uScanlineIntensity > 0.01) {
        vec2 tapeCoord = vec2(vQuadCoord.x * uViewportSize.x * 0.75, floor(vQuadCoord.y * 240.0));
        float tapeNoise = filmNoise(tapeCoord, uRandomSeed);
        color += vec3(tapeNoise * 0.05 * uScanlineIntensity);

        float scanline = sin(vQuadCoord.y * 480.0 * 3.14159) * 0.5 + 0.5;
        color *= mix(1.0, 0.88 + 0.12 * scanline, uScanlineIntensity);
    }

    // Fisheye Barrel Mask & Outer Boundary Falloff (applied after grain/scanlines so lens barrel stays pitch black)
    if (uLensType == 2) { // FISHEYE_F: Classic circular hood vignette
        color *= fisheyeMask;
        if (rPhys <= 0.465) {
            float rim = smoothstep(0.44, 0.462, rPhys) * (1.0 - smoothstep(0.462, 0.466, rPhys));
            color += vec3(0.08, 0.075, 0.07) * rim;
        }
    } else if (uLensType == 3) { // FISHEYE_W: Full-frame peripheral soft falloff
        float rCorner = isPortrait ? length(vec2(0.5, 0.5 / aspect)) : length(vec2(0.5 * aspect, 0.5));
        float rn = clamp(rPhys / rCorner, 0.0, 1.0);
        float wideVig = 1.0 - smoothstep(0.65, 1.05, rn);
        color *= mix(1.0, wideVig, 0.30);
    }

    // 15. Pro Focus Peaking (Sobel-like gradient edge detection on camera preview)
    if (uFocusPeakingEnabled) {
        vec2 texel = 1.0 / uViewportSize;
        vec2 sampleL = (uTexMatrix * vec4(uv - vec2(texel.x, 0.0), 0.0, 1.0)).xy;
        vec2 sampleR = (uTexMatrix * vec4(uv + vec2(texel.x, 0.0), 0.0, 1.0)).xy;
        vec2 sampleT = (uTexMatrix * vec4(uv - vec2(0.0, texel.y), 0.0, 1.0)).xy;
        vec2 sampleB = (uTexMatrix * vec4(uv + vec2(0.0, texel.y), 0.0, 1.0)).xy;

        float lumL = dot(texture(sTexture, sampleL).rgb, vec3(0.299, 0.587, 0.114));
        float lumR = dot(texture(sTexture, sampleR).rgb, vec3(0.299, 0.587, 0.114));
        float lumT = dot(texture(sTexture, sampleT).rgb, vec3(0.299, 0.587, 0.114));
        float lumB = dot(texture(sTexture, sampleB).rgb, vec3(0.299, 0.587, 0.114));

        float gx = lumR - lumL;
        float gy = lumB - lumT;
        float edge = length(vec2(gx, gy));

        float peakMask = smoothstep(uPeakingThreshold, uPeakingThreshold + 0.08, edge);
        color = mix(color, uPeakingColor, peakMask * 0.90);
    }

    // 16. Pro Zebra Exposure Warning (Diagonal animated stripes over highlights)
    if (uZebraEnabled) {
        float peakLuma = dot(color, vec3(0.299, 0.587, 0.114));
        if (peakLuma >= uZebraThreshold) {
            vec2 screenPx = vQuadCoord * uViewportSize;
            float stripe = step(0.5, fract((screenPx.x + screenPx.y + uZebraTime * 45.0) / 22.0));
            vec3 stripeColor = mix(vec3(0.05), vec3(0.95), stripe);
            color = mix(color, stripeColor, 0.75);
        }
    }

    fragColor = vec4(clamp(color, 0.0, 1.0), 1.0);
}
"""

    const val VERTEX_SHADER_STILL = """#version 300 es
layout (location = 0) in vec4 aPosition;
layout (location = 1) in vec2 aTextureCoord;

out vec2 vTextureCoord;

void main() {
    gl_Position = aPosition;
    vTextureCoord = aTextureCoord;
}
"""

    const val FRAGMENT_SHADER_STILL = """#version 300 es
precision highp float;
precision mediump sampler2D;
precision mediump sampler3D;

uniform sampler2D sTexture;
uniform sampler2D uLutTexture;
uniform mediump sampler3D sLut3D;
uniform sampler2D uLutTextureB;
uniform mediump sampler3D sLut3D_B;
uniform bool uLutEnabled;
uniform bool uUseLut3D;
uniform bool uHasSecondaryLut;
uniform float uDualLutMix;
uniform int uLutInputSpace; // 0=SRGB, 1=LINEAR, 2=CUSTOM_PRETRANSFORM
uniform bool uPreLinearizationEnabled;
uniform float uPreLinearizationStrength;
uniform float uFilterIntensity;
uniform float uLowerMidtoneStrength;
uniform float uClarityStrength;
uniform float uSkinProtection;
in vec2 vTextureCoord;
uniform vec2 uViewportSize;
out vec4 fragColor;

// Tonal and Color Grading
uniform float uContrast;
uniform float uSaturation;
uniform float uTemperature;
uniform float uTint;
uniform float uGamma;
uniform float uFade;
uniform float uToeLift;
uniform float uShoulderClip;
uniform float uHighlightRollOff;
uniform vec3 uShadowTint;

// Lens and Optical Effects
uniform float uVignetteIntensity;
uniform float uVignetteRadius;
uniform float uChromaticAberration;
uniform float uBarrelDistortion;
uniform float uSoftness;

// Dedicated Camera Lens System
uniform int uLensType; // 0=NORMAL, 1=WIDE, 2=FISHEYE_F, 3=FISHEYE_W, 4=PRISM, 5=STARBURST, 6=ND, 7=COLOR_FLASH
uniform float uLensDistortion;
uniform float uLensEdgeCurvature;
uniform float uLensVignette;
uniform float uLensPrismDispersion;
uniform float uLensStarburstStrength;
uniform float uLensStarburstThreshold;
uniform float uLensNdFactor;
uniform vec3 uLensColorFlashTint;
uniform bool uLensIsFlashActive;

// D3D Single-Image 3D Photography Filter
uniform bool uD3DEnabled;
uniform float uD3DStrength;
uniform float uD3DParallax;
uniform float uD3DRgbSeparation;
uniform float uD3DAnaglyph;

// Film Grain
uniform float uGrainIntensity;
uniform float uGrainScale;
uniform bool uIsColorNoise;
uniform float uRandomSeed;
uniform sampler2D sGrainTexture;
uniform bool uHasGrainTexture;
uniform int uGrainBlendMode;
uniform vec2 uGrainOffset;
uniform float uGrainShadowStrength;
uniform float uGrainHighlightStrength;
uniform bool uGrainLumaWeightEnabled;
uniform float uGrainLumaWeightPower;

// Halation and Light Leaks
uniform bool uHalationEnabled;
uniform float uHalationThreshold;
uniform float uHalationIntensity;
uniform vec3 uHalationColorTint;
uniform bool uLightLeakEnabled;
uniform float uLightLeakIntensity;
uniform float uScanlineIntensity;

// Developer Visual A/B Stage Toggles (Section 24)
uniform bool uDebugStagePreLinearization;
uniform bool uDebugStageLut;
uniform bool uDebugStageToneCurve;
uniform bool uDebugStageGrain;
uniform bool uDebugStageHalation;
uniform bool uDebugStageOptics;
uniform bool uDebugStageVignette;

// Natural Camera Engine
uniform bool uNaturalModeEnabled;
uniform float uNaturalShadowLift;

// Pro Camera Tools (Focus Peaking & Zebra Warning)
uniform bool uFocusPeakingEnabled;
uniform vec3 uPeakingColor;
uniform float uPeakingThreshold;
uniform bool uZebraEnabled;
uniform float uZebraThreshold;
uniform float uZebraTime;

uniform int uDiagnosticMode;
$THERMAL_UNIFORMS_GLSL
$CREATIVE_TEMPERATURE_UNIFORMS_GLSL

// Stochastic sine-free hash for authentic silver halide film grain (precision-safe)
float filmNoise(vec2 coord, float seed) {
    vec2 p = mod(coord, 1024.0) + vec2(fract(seed * 1.6180339887), fract(seed * 1.4142135623)) * 64.0;
    vec3 p3 = fract(vec3(p.xyx) * vec3(0.1031, 0.1030, 0.0973));
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z) - 0.5;
}

// 2-Scale Cellular Dye Cloud: Hermite stochastic interpolation creates organic physical crystal clusters
float dyeCloudNoise(vec2 coord, float seed) {
    vec2 p = mod(coord, 512.0) + fract(seed * 1.6180339) * 32.0;
    vec2 ip = floor(p);
    vec2 fp = fract(p);
    vec2 u = fp * fp * (3.0 - 2.0 * fp);
    
    float n00 = filmNoise(ip, seed);
    float n10 = filmNoise(ip + vec2(1.0, 0.0), seed);
    float n01 = filmNoise(ip + vec2(0.0, 1.0), seed);
    float n11 = filmNoise(ip + vec2(1.0, 1.0), seed);
    
    return mix(mix(n00, n10, u.x), mix(n01, n11, u.x), u.y);
}

vec3 sampleHaldLut(sampler2D lut, vec3 rgb) {
    rgb = clamp(rgb, 0.0, 1.0);
    float blueVal = rgb.b * 63.0;
    
    float slice0 = floor(blueVal);
    float slice1 = min(63.0, slice0 + 1.0);
    float blueFract = fract(blueVal);
    
    float s0_col = mod(slice0, 8.0);
    float s0_row = floor(slice0 / 8.0);
    
    float s1_col = mod(slice1, 8.0);
    float s1_row = floor(slice1 / 8.0);
    
    float px0_x = s0_col * 64.0 + 0.5 + rgb.r * 63.0;
    float px1_x = s1_col * 64.0 + 0.5 + rgb.r * 63.0;
    
    float px0_y = s0_row * 64.0 + 0.5 + rgb.g * 63.0;
    float px1_y = s1_row * 64.0 + 0.5 + rgb.g * 63.0;
    
    vec2 uv0 = vec2(px0_x / 512.0, px0_y / 512.0);
    vec2 uv1 = vec2(px1_x / 512.0, px1_y / 512.0);
    
    vec3 col0 = texture(lut, uv0).rgb;
    vec3 col1 = texture(lut, uv1).rgb;
    
    return mix(col0, col1, blueFract);
}

// Rational pre-linearization curve: flattens aggressive ISP contrast before 3D LUT sampling (VSCO-style)
vec3 preLinearizeLutInput(vec3 c, float strength) {
    vec3 linearC;
    linearC.r = (c.r <= 0.5) ? pow(c.r * 2.0, 1.25) * 0.5 : 1.0 - pow((1.0 - c.r) * 2.0, 1.25) * 0.5;
    linearC.g = (c.g <= 0.5) ? pow(c.g * 2.0, 1.25) * 0.5 : 1.0 - pow((1.0 - c.g) * 2.0, 1.25) * 0.5;
    linearC.b = (c.b <= 0.5) ? pow(c.b * 2.0, 1.25) * 0.5 : 1.0 - pow((1.0 - c.b) * 2.0, 1.25) * 0.5;
    return mix(c, clamp(linearC, 0.0, 1.0), clamp(strength, 0.0, 1.0));
}

// Hardware-accelerated 3D Texture single-sample trilinear lookup (GLES 3.0 GL_TEXTURE_3D)
vec3 sample3DLut(mediump sampler3D lut3d, vec3 rgb) {
    rgb = clamp(rgb, 0.0, 1.0);
    vec3 lutCoord = rgb * (63.0 / 64.0) + (0.5 / 64.0);
    return texture(lut3d, lutCoord).rgb;
}
$THERMAL_HELPERS_GLSL
$CREATIVE_TEMPERATURE_HELPERS_GLSL

void main() {
    // Physical isotropic coordinates (aspect-corrected from uViewportSize or 3:4 fallback)
    float aspect = (uViewportSize.x > 0.0) ? (uViewportSize.x / max(uViewportSize.y, 1.0)) : 0.75;
    bool isPortrait = aspect <= 1.0;

    vec2 pRaw = vTextureCoord - 0.5;
    vec2 pPhys = isPortrait ? vec2(pRaw.x, pRaw.y / aspect) : vec2(pRaw.x * aspect, pRaw.y);
    float rPhys = length(pPhys);

    vec2 p = pRaw;
    float r2 = rPhys * rPhys;
    float r = rPhys;

    // 1. Lens Optical Distortion (Fisheye F / Fisheye W / Wide / Barrel)
    vec2 uv = vTextureCoord;
    float fisheyeMask = 1.0;

    if (uDebugStageOptics) {
        if (uLensType == 2) { // FISHEYE_F: Classic circular hemispherical fisheye bubble
            float rBubble = 0.44; // Safe radius ensuring full 360° circular clearance without border cutoff
            if (rPhys > rBubble) {
                fisheyeMask = 0.0;
                uv = vec2(0.5);
            } else {
                float rn = rPhys / rBubble;
                // Authentic hemispherical fisheye warp: cubic radial expansion
                // rn goes 0..1, rDist goes 0..1 with moderate center magnification (1.43x) and perimeter compression
                float rDistNorm = rn * (0.70 + 0.30 * rn * rn);
                float rDistPhys = rDistNorm * 0.44;
                vec2 dir = pPhys / max(rPhys, 0.0001);
                vec2 pDistPhys = dir * rDistPhys;
                vec2 pDist = isPortrait ? vec2(pDistPhys.x, pDistPhys.y * aspect) : vec2(pDistPhys.x / aspect, pDistPhys.y);
                uv = 0.5 + pDist;
                fisheyeMask = 1.0 - smoothstep(rBubble - 0.012, rBubble, rPhys);
            }
        } else if (uLensType == 3) { // FISHEYE_W: Full-frame wide-angle curvilinear fisheye
            float rCorner = isPortrait ? length(vec2(0.5, 0.5 / aspect)) : length(vec2(0.5 * aspect, 0.5));
            float rn = clamp(rPhys / rCorner, 0.0, 1.0);
            float rDistNorm = rn * (0.80 + 0.20 * rn * rn);
            float rDistPhys = rDistNorm * rCorner;
            vec2 dir = pPhys / max(rPhys, 0.0001);
            vec2 pDistPhys = dir * rDistPhys;
            vec2 pDist = isPortrait ? vec2(pDistPhys.x, pDistPhys.y * aspect) : vec2(pDistPhys.x / aspect, pDistPhys.y);
            uv = 0.5 + pDist;
        } else if (uLensType == 1) { // WIDE
            vec2 pDistPhys = pPhys * (1.0 + uLensDistortion * (rPhys * rPhys) * 0.35);
            vec2 pDist = isPortrait ? vec2(pDistPhys.x, pDistPhys.y * aspect) : vec2(pDistPhys.x / aspect, pDistPhys.y);
            uv = 0.5 + pDist;
        } else if (abs(uBarrelDistortion) > 0.001) {
            vec2 pDistPhys = pPhys * (1.0 + uBarrelDistortion * (rPhys * rPhys) * 0.35);
            vec2 pDist = isPortrait ? vec2(pDistPhys.x, pDistPhys.y * aspect) : vec2(pDistPhys.x / aspect, pDistPhys.y);
            uv = 0.5 + pDist;
        }
    }
    uv = clamp(uv, vec2(0.001), vec2(0.999));

    // 2. Transverse Chromatic Aberration & Spectral Dispersion
    vec4 srcColor;
    float effCA = (uDebugStageOptics && !uNaturalModeEnabled) ? uChromaticAberration : 0.0;
    if (uDebugStageOptics) {
        if (uLensType == 2) effCA = max(effCA, 0.016);
        else if (uLensType == 3) effCA = max(effCA, 0.008);
        else if (uLensType == 4) effCA = max(effCA, 0.010);
    }

    if (effCA > 0.0001 && fisheyeMask > 0.001) {
        vec2 caDir = pPhys / max(rPhys, 0.0001);
        vec2 caPhysOffset = caDir * (effCA * rPhys * rPhys * 1.5);
        vec2 caOffset = isPortrait ? vec2(caPhysOffset.x, caPhysOffset.y * aspect) : vec2(caPhysOffset.x / aspect, caPhysOffset.y);
        vec2 uvR = clamp(uv + caOffset, vec2(0.001), vec2(0.999));
        vec2 uvG = clamp(uv - caOffset * 0.15, vec2(0.001), vec2(0.999));
        vec2 uvB = clamp(uv - caOffset * 1.35, vec2(0.001), vec2(0.999));
        float rCh = texture(sTexture, uvR).r;
        float gCh = texture(sTexture, uvG).g;
        float bCh = texture(sTexture, uvB).b;
        srcColor = vec4(rCh, gCh, bCh, 1.0);
    } else {
        srcColor = texture(sTexture, uv);
    }

    vec3 color = srcColor.rgb;
    vec3 preGradeColor = color;

    // Diagnostic Mode 1: Passthrough (Raw Camera Feed, zero effects)
    if (uDiagnosticMode == 1) {
        fragColor = vec4(preGradeColor, 1.0);
        return;
    }

    // Dedicated Simulated Thermal Vision Pipeline (bypasses film LUTs & film grain)
    if (uThermalEnabled) {
        vec2 tStep = 2.0 / max(uViewportSize, vec2(320.0));
        float lL = dot(texture(sTexture, clamp(uv - vec2(tStep.x, 0.0), vec2(0.001), vec2(0.999))).rgb, vec3(0.2126, 0.7152, 0.0722));
        float lR = dot(texture(sTexture, clamp(uv + vec2(tStep.x, 0.0), vec2(0.001), vec2(0.999))).rgb, vec3(0.2126, 0.7152, 0.0722));
        float lT = dot(texture(sTexture, clamp(uv - vec2(0.0, tStep.y), vec2(0.001), vec2(0.999))).rgb, vec3(0.2126, 0.7152, 0.0722));
        float lB = dot(texture(sTexture, clamp(uv + vec2(0.0, tStep.y), vec2(0.001), vec2(0.999))).rgb, vec3(0.2126, 0.7152, 0.0722));
        vec3 thermalRgb = evaluateThermalVision(preGradeColor, lL, lR, lT, lB, vTextureCoord, vTextureCoord * max(uViewportSize, vec2(1080.0)), uRandomSeed);
        fragColor = vec4(thermalRgb, 1.0);
        return;
    }

    // Pre-grade normalized chromaticity & continuous physiological skin locus (derived from uncorrupted camera input)
    float preRgbSum = max(0.0001, preGradeColor.r + preGradeColor.g + preGradeColor.b);
    float preNormR = preGradeColor.r / preRgbSum;
    float preNormG = preGradeColor.g / preRgbSum;
    float preLuma = dot(preGradeColor, vec3(0.2126, 0.7152, 0.0722));

    // Elliptical skin locus in normalized chromaticity (major axis along melanin line: theta = -0.42 rad)
    // Center: (0.42, 0.32), Axis U (melanin shift): 0.080, Axis V (erythema / blood shift): 0.038
    float dr = preNormR - 0.42;
    float dg = preNormG - 0.32;
    float uDist = dr * 0.913 - dg * 0.408;
    float vDist = dr * 0.408 + dg * 0.913;
    float skinDistSq = (uDist * uDist) / (0.080 * 0.080) + (vDist * vDist) / (0.038 * 0.038);
    float isSkinProb = exp(-0.5 * skinDistSq);

    // Continuous luminance gating: avoids crushed dark shadows (<0.08) and specular highlights (>0.85)
    float lumaGate = smoothstep(0.08, 0.20, preLuma) * (1.0 - smoothstep(0.85, 0.98, preLuma));
    float rawSkinWeight = isSkinProb * lumaGate;
    float skinWeight = clamp(rawSkinWeight * uSkinProtection, 0.0, 1.0);

    // 2-D3D. Stable D3D video/preview (zero UV stretching).

    // 2a. Prism Multi-Facet Refraction & Chromatic Splitting
    if (uLensType == 4 && uLensPrismDispersion > 0.01) {
        float disp = (0.038 + r * 0.028) * uLensPrismDispersion;
        vec2 off1 = vec2(disp * 0.866, disp * 0.50);
        vec2 off2 = vec2(-disp * 0.866, disp * 0.50);

        vec3 prism1 = texture(sTexture, clamp(uv + off1, vec2(0.001), vec2(0.999))).rgb;
        vec3 prism2 = texture(sTexture, clamp(uv + off2, vec2(0.001), vec2(0.999))).rgb;
        prism1.r *= 1.08;
        prism2.b *= 1.12;

        float facetWeight = clamp(r * 1.35 * uLensPrismDispersion, 0.0, 0.45);
        color = mix(color, (prism1 + prism2) * 0.5, facetWeight);
    }

    // 2a-star. Starburst Diffraction Rays
    if (uLensType == 5 && uLensStarburstStrength > 0.01) {
        float starThresh = uLensStarburstThreshold;
        vec2 starStep = vec2(0.0045, 0.0045);
        vec3 starAccum = vec3(0.0);
        for (int i = 1; i <= 3; i++) {
            float fi = float(i) * 2.2;
            vec2 d1 = vec2(fi * starStep.x, fi * starStep.y);
            vec2 d2 = vec2(-fi * starStep.x, fi * starStep.y);

            vec3 s1 = texture(sTexture, clamp(uv + d1, vec2(0.001), vec2(0.999))).rgb;
            vec3 s2 = texture(sTexture, clamp(uv - d1, vec2(0.001), vec2(0.999))).rgb;
            vec3 s3 = texture(sTexture, clamp(uv + d2, vec2(0.001), vec2(0.999))).rgb;
            vec3 s4 = texture(sTexture, clamp(uv - d2, vec2(0.001), vec2(0.999))).rgb;

            float w = 1.0 / float(i);
            starAccum += (max(vec3(0.0), s1 - starThresh) +
                          max(vec3(0.0), s2 - starThresh) +
                          max(vec3(0.0), s3 - starThresh) +
                          max(vec3(0.0), s4 - starThresh)) * w;
        }
        color += starAccum * (uLensStarburstStrength * 0.70);
    }

    // 2a-nd. Neutral Density Filter Light Attenuation
    if (uLensType == 6 && uLensNdFactor < 0.99) {
        color *= uLensNdFactor;
    }

    // 2a-flash. Color Gel Flash Tint
    if (uLensType == 7 && uLensIsFlashActive) {
        color = mix(color, color * uLensColorFlashTint * 1.30, 0.70);
    }

    // 2b. Optical Softness (Contrast-preserving spherical veiling flare)
    if (uSoftness > 0.005) {
        float rSoftness = clamp(r2 * 2.8 * uSoftness, 0.0, 0.85);
        if (rSoftness > 0.01) {
            vec2 sStep = vec2(0.0022) * (1.0 + rSoftness * 1.5);
            vec3 sL = texture(sTexture, clamp(uv - vec2(sStep.x, 0.0), vec2(0.001), vec2(0.999))).rgb;
            vec3 sR = texture(sTexture, clamp(uv + vec2(sStep.x, 0.0), vec2(0.001), vec2(0.999))).rgb;
            vec3 sT = texture(sTexture, clamp(uv - vec2(0.0, sStep.y), vec2(0.001), vec2(0.999))).rgb;
            vec3 sB = texture(sTexture, clamp(uv + vec2(0.0, sStep.y), vec2(0.001), vec2(0.999))).rgb;
            vec3 softSample = (sL + sR + sT + sB) * 0.25;
            
            vec3 diffuseGlow = max(vec3(0.0), softSample - color * 0.75);
            color = mix(color, softSample, rSoftness * 0.25) + diffuseGlow * (rSoftness * 0.35);
        }
    }

    // 2c. Camcorder Chroma Subcarrier Delay (Horizontal Color Smear)
    if (uScanlineIntensity > 0.01) {
        vec2 chromaOffset = vec2(0.0035, 0.0);
        vec3 delayedChroma = texture(sTexture, clamp(uv - chromaOffset, vec2(0.001), vec2(0.999))).rgb;
        color.r = mix(color.r, delayedChroma.r, 0.35);
        color.b = mix(color.b, delayedChroma.b, 0.45);
    }

    float luma = dot(color, vec3(0.2126, 0.7152, 0.0722));

    // 2d. Photochemical 3D Film Emulsion LUT with Dual-LUT Crossfading (Section 7)
    if (uDiagnosticMode == 3) {
        // Mode 3: Profile Adjustments Only (Bypasses LUT)
    } else if (uLutEnabled && uDebugStageLut) {
        vec3 lutInput = color;
        if (uLutInputSpace == 1) { // LINEAR
            lutInput = pow(max(lutInput, vec3(0.0)), vec3(2.2));
        } else if (uLutInputSpace == 2) { // CUSTOM_PRETRANSFORM (ISP Contrast Flattening)
            if (uPreLinearizationEnabled && uDebugStagePreLinearization) {
                lutInput = preLinearizeLutInput(lutInput, uPreLinearizationStrength);
            }
        }

        vec3 lutColor;
        if (uUseLut3D) {
            lutColor = sample3DLut(sLut3D, lutInput);
            if (uHasSecondaryLut && uDualLutMix > 0.001) {
                vec3 lutColorB = sample3DLut(sLut3D_B, lutInput);
                lutColor = mix(lutColor, lutColorB, clamp(uDualLutMix, 0.0, 1.0));
            }
        } else {
            lutColor = sampleHaldLut(uLutTexture, lutInput);
            if (uHasSecondaryLut && uDualLutMix > 0.001) {
                vec3 lutColorB = sampleHaldLut(uLutTextureB, lutInput);
                lutColor = mix(lutColor, lutColorB, clamp(uDualLutMix, 0.0, 1.0));
            }
        }
        color = mix(color, lutColor, clamp(uFilterIntensity, 0.0, 1.0));
    }

    // Diagnostic Mode 2: LUT Only (Bypasses profile adjustments and skin protection)
    if (uDiagnosticMode == 2) {
        fragColor = vec4(clamp(color, 0.0, 1.0), 1.0);
        return;
    }

    // 2e. CAMELEO Natural Mode Processing
    if (uNaturalModeEnabled) {
        vec3 rMat = vec3(1.024, -0.016, -0.008);
        vec3 gMat = vec3(-0.012, 1.016, -0.004);
        vec3 bMat = vec3(-0.006, 0.012, 0.994);
        color = vec3(dot(color, rMat), dot(color, gMat), dot(color, bMat));

        // Continuous max-channel asymptotic highlight shoulder (preserves skin & cloud chromaticity ratios)
        float knee = 0.68;
        float maxCh = max(max(color.r, color.g), color.b);
        if (maxCh > knee) {
            float d = maxCh - knee;
            float s = 1.0 - knee;
            float rolledMax = knee + s * (d / (d + s));
            color = color * (rolledMax / maxCh);
            float coreLuma = dot(color, vec3(0.2126, 0.7152, 0.0722));
            float desat = clamp((coreLuma - knee) / (1.0 - knee), 0.0, 1.0);
            color = mix(color, vec3(coreLuma), desat * 0.40);
        }

        // Adaptive rational shadow toe lift (true black preserved at Y=0)
        float yLuma = dot(color, vec3(0.2126, 0.7152, 0.0722));
        float lift = uNaturalShadowLift * (1.0 - clamp(yLuma, 0.0, 1.0)) * (1.0 - clamp(yLuma, 0.0, 1.0)) * clamp(yLuma, 0.0, 1.0) * 1.6;
        color += vec3(lift);
    }

    // 3. White Balance
    color.r += 0.10 * uTemperature;
    color.b -= 0.10 * uTemperature;
    color.g += 0.06 * uTint;
    color.r -= 0.03 * uTint;
    color.b -= 0.03 * uTint;

    // 4. Contrast
    if (uDebugStageToneCurve) {
        if (!uNaturalModeEnabled) {
            luma = dot(color, vec3(0.2126, 0.7152, 0.0722));
            float sLift = uFilterIntensity * 0.035 * (1.0 - clamp(luma, 0.0, 1.0)) * (1.0 - clamp(luma, 0.0, 1.0));
            color += vec3(sLift);
            luma = dot(color, vec3(0.2126, 0.7152, 0.0722));
        }

        if (abs(uContrast - 1.0) > 0.01) {
            float pivot = 0.26;
            color = (color - pivot) * uContrast + pivot;
            if (uLowerMidtoneStrength == 0.0) color = clamp(color, 0.0, 1.0);
        }

        if (uLowerMidtoneStrength > 0.0) {
            float y = dot(color, vec3(0.2126, 0.7152, 0.0722));
            float x = clamp((y - 0.035) / 0.65, 0.0, 1.0);
            float envelope = 4.0 * x * (1.0 - x);
            float toe = 1.0 - smoothstep(0.0, 0.18, y);
            color += vec3(uLowerMidtoneStrength * envelope * envelope - 0.029 * uFilterIntensity * toe);
        }

        // 4b. Authentic Midtone Clarity (from DAZZ Camera Engine)
        if (abs(uClarityStrength) > 0.005) {
            float boost = 1.0 + uClarityStrength * 0.18;
            float offset = -uClarityStrength * 0.18 * 0.5;
            color = clamp(color * boost + offset, 0.0, 1.0);
            luma = dot(color, vec3(0.2126, 0.7152, 0.0722));
        }

        // 5. Saturation with Smart Vibrance Protection
        luma = dot(color, vec3(0.2126, 0.7152, 0.0722));
        if (uLowerMidtoneStrength > 0.0) {
            color = mix(vec3(luma), color, uSaturation);
        } else if (abs(uSaturation - 1.0) > 0.01) {
            const float lr = 0.2126;
            const float lg = 0.7152;
            const float lb = 0.0722;
            float sat = uSaturation;
            float sr = (1.0 - sat) * lr;
            float sg = (1.0 - sat) * lg;
            float sb = (1.0 - sat) * lb;
            vec3 vibColor = clamp(vec3(
                color.r * (sr + sat) + color.g * sg + color.b * sb,
                color.r * sr + color.g * (sg + sat) + color.b * sb,
                color.r * sr + color.g * sg + color.b * (sb + sat)
            ), 0.0, 1.0);
            color = mix(mix(vec3(luma), color, uSaturation), vibColor, clamp(uSkinProtection, 0.0, 1.0));
            luma = dot(color, vec3(0.2126, 0.7152, 0.0722));
        }

        // 5b. Authentic Photographic Skin Tone Protection (Redesigned Continuous Chromaticity Preservation)
        // Diagnostic Mode 4: Combined LUT + Profile (Skin protection bypassed)
        // Diagnostic Mode 5: Skin Protection Mask Visualization
        if (uDiagnosticMode == 5) {
            fragColor = vec4(vec3(skinWeight), 1.0);
            return;
        }

        if (skinWeight > 0.001 && uDiagnosticMode != 4) {
            // 1. Smooth Mustard / Excess Yellow-Green Suppression (Prevents sickly green-yellow cast)
            float postRgbSum = max(0.0001, color.r + color.g + color.b);
            float postNormG = color.g / postRgbSum;
            float mustardExcess = smoothstep(0.33, 0.38, postNormG);
            if (mustardExcess > 0.0) {
                float targetG = postRgbSum * 0.315;
                color.g = mix(color.g, targetG, mustardExcess * skinWeight * 0.75);
            }

            // 2. Continuous Blue Headroom Preservation (Prevents plastic yellowing under direct flash/tungsten)
            float minBlue = color.r * 0.30;
            if (color.b < minBlue) {
                color.b += (minBlue - color.b) * skinWeight * 0.60;
            }

            // 3. Asymptotic Soft-Knee Highlight Compression (Prevents hard R=255 clipping on skin highlights)
            if (color.r > 0.82) {
                float knee = 0.82;
                float excess = color.r - knee;
                float headroom = 1.0 - knee;
                float compressedR = knee + headroom * (excess / (excess + headroom * 0.50));
                color.r = mix(color.r, compressedR, skinWeight);
            }
            luma = dot(color, vec3(0.2126, 0.7152, 0.0722));
        }

        // 6. Gamma
        if (abs(uGamma - 1.0) > 0.01) {
            color = pow(max(color, vec3(0.0)), vec3(1.0 / uGamma));
            luma = dot(color, vec3(0.2126, 0.7152, 0.0722));
        }

        // 7. Shadow Tint
        float shadowWeight = clamp(1.0 - luma * 3.0, 0.0, 1.0);
        color += uShadowTint * shadowWeight;

        // 8. Filmic Asymptotic Highlight Shoulder Rolloff (preserves hue/chromaticity into specular highlights)
        if (uHighlightRollOff < 1.0 && !uNaturalModeEnabled) {
            float knee = uHighlightRollOff;
            float maxCh = max(max(color.r, color.g), color.b);
            if (maxCh > knee) {
                float d = maxCh - knee;
                float s = 1.0 - knee;
                float rolledMax = knee + s * (d / (d + s));
                color = color * (rolledMax / maxCh);
                
                float coreLuma = dot(color, vec3(0.2126, 0.7152, 0.0722));
                float desat = clamp((coreLuma - knee) / (1.0 - knee), 0.0, 1.0);
                color = mix(color, vec3(coreLuma), desat * 0.45);
            }
            luma = dot(color, vec3(0.2126, 0.7152, 0.0722));
        }

        // 8b. Lifted Toe & Shoulder Compression (Lightroom Film Profile Parity)
        if (uToeLift > 0.001 || uShoulderClip < 0.999) {
            color = clamp(color, 0.0, 1.0);
            color = uToeLift + color * (uShoulderClip - uToeLift);
            luma = dot(color, vec3(0.2126, 0.7152, 0.0722));
        }

        // 9. Film Fade
        if (uFade > 0.001) {
            color = color * (1.0 - uFade) + uFade;
        }
    }

    // 9b. Creative Temperature Filter (-100 Frozen Blue .. +100 Deep Red)
    if (abs(uCreativeTemperature) > 0.05) {
        color = applyCreativeTemperature(color, uCreativeTemperature, uCreativeTempSkinProtect);
        luma = dot(color, vec3(0.2126, 0.7152, 0.0722));
    }

    // 10. Concentric Dual-Radius Film Halation (Cadmium red-orange backscattering with boundary confinement)
    if (uHalationEnabled && uHalationIntensity > 0.01 && uDebugStageHalation) {
        float kThresh = uHalationThreshold;
        if (luma > kThresh - 0.04) {
            vec2 hInner = vec2(0.0035);
            vec2 hOuter = vec2(0.0080);

            vec4 tInL = texture(sTexture, clamp(uv - vec2(hInner.x, 0.0), vec2(0.001), vec2(0.999)));
            vec4 tInR = texture(sTexture, clamp(uv + vec2(hInner.x, 0.0), vec2(0.001), vec2(0.999)));
            vec4 tInT = texture(sTexture, clamp(uv - vec2(0.0, hInner.y), vec2(0.001), vec2(0.999)));
            vec4 tInB = texture(sTexture, clamp(uv + vec2(0.0, hInner.y), vec2(0.001), vec2(0.999)));

            vec2 diag = hOuter * 0.7071;
            vec4 tOutTL = texture(sTexture, clamp(uv + vec2(-diag.x, -diag.y), vec2(0.001), vec2(0.999)));
            vec4 tOutTR = texture(sTexture, clamp(uv + vec2( diag.x, -diag.y), vec2(0.001), vec2(0.999)));
            vec4 tOutBL = texture(sTexture, clamp(uv + vec2(-diag.x,  diag.y), vec2(0.001), vec2(0.999)));
            vec4 tOutBR = texture(sTexture, clamp(uv + vec2( diag.x,  diag.y), vec2(0.001), vec2(0.999)));

            float lInL = dot(tInL.rgb, vec3(0.2126, 0.7152, 0.0722));
            float lInR = dot(tInR.rgb, vec3(0.2126, 0.7152, 0.0722));
            float lInT = dot(tInT.rgb, vec3(0.2126, 0.7152, 0.0722));
            float lInB = dot(tInB.rgb, vec3(0.2126, 0.7152, 0.0722));

            float lOutTL = dot(tOutTL.rgb, vec3(0.2126, 0.7152, 0.0722));
            float lOutTR = dot(tOutTR.rgb, vec3(0.2126, 0.7152, 0.0722));
            float lOutBL = dot(tOutBL.rgb, vec3(0.2126, 0.7152, 0.0722));
            float lOutBR = dot(tOutBR.rgb, vec3(0.2126, 0.7152, 0.0722));

            float hInnerSum = max(0.0, lInL - kThresh) + max(0.0, lInR - kThresh) + max(0.0, lInT - kThresh) + max(0.0, lInB - kThresh);
            float hOuterSum = max(0.0, lOutTL - kThresh) + max(0.0, lOutTR - kThresh) + max(0.0, lOutBL - kThresh) + max(0.0, lOutBR - kThresh);

            float spread = (hInnerSum * 0.18 + hOuterSum * 0.07) / max(0.01, 1.0 - kThresh);
            if (spread > 0.001) {
                // Highlight-core preservation: maintain neutral white in specular cores, concentrate red-orange fringe at boundaries
                float coreLuma = clamp((luma - kThresh) / max(0.01, 1.0 - kThresh), 0.0, 1.0);
                vec3 haloBase = mix(uHalationColorTint, vec3(0.40, 0.38, 0.35), coreLuma * 0.85);
                vec3 haloColor = haloBase * (spread * uHalationIntensity * 0.45);
                color += haloColor;
            }
        }
    }

    // 11. Subtle Edge Light Leak Emulation
    if (uLightLeakEnabled && uLightLeakIntensity > 0.01) {
        float edgeDist = length(vec2(max(0.0, uv.x - 0.75) * 2.0, max(0.0, 0.25 - uv.y) * 2.0));
        float leakMask = 1.0 - smoothstep(0.0, 0.40, edgeDist);
        vec3 leakColor = vec3(1.0, 0.45, 0.12) * (leakMask * uLightLeakIntensity * 0.35);
        color = 1.0 - (1.0 - color) * (1.0 - leakColor);
    }

    // 12. Optical Cosine Fourth Lens Vignette
    if (uDebugStageVignette) {
        float vigTotal = uVignetteIntensity + uLensVignette;
        if (vigTotal > 0.01) {
            float rNorm = length(p) / 0.7071068;
            float rn = rNorm / max(0.72, uVignetteRadius);
            float cos4Vig = 1.0 / pow(1.0 + rn * rn * 0.45, 2.0);
            float vignette = mix(1.0, cos4Vig, clamp(vigTotal * 0.65, 0.0, 0.55));
            color *= clamp(vignette, 0.50, 1.0);
        }
    }

    // 13. Authentic Scanned Photographic Film Halide Grain
    if (uGrainIntensity > 0.005 && uDebugStageGrain) {
        float shadowT = clamp(luma / 0.35, 0.0, 1.0);
        float shadowMask = shadowT * shadowT * (3.0 - 2.0 * shadowT);
        float highlightT = clamp((luma - 0.65) / 0.35, 0.0, 1.0);
        float highlightMask = 1.0 - (highlightT * highlightT * (3.0 - 2.0 * highlightT));
        float grainWeight = (uGrainShadowStrength + (1.0 - uGrainShadowStrength) * shadowMask) * 
                            (uGrainHighlightStrength + (1.0 - uGrainHighlightStrength) * highlightMask);

        // Section 9: Parabolic midtone luminance weighting M(L) = 4L(1-L)
        if (uGrainLumaWeightEnabled && uGrainLumaWeightPower > 0.01) {
            float midtoneParabola = clamp(4.0 * luma * (1.0 - luma), 0.0, 1.0);
            grainWeight = mix(grainWeight, grainWeight * midtoneParabola, uGrainLumaWeightPower);
        }
        grainWeight = max(0.35, grainWeight);

        if (uHasGrainTexture) {
            vec2 grainUv = fract(uv * 4.0 * (uGrainScale * 0.75) + uGrainOffset);
            vec4 gSample = texture(sGrainTexture, grainUv);
            float gLuma = dot(gSample.rgb, vec3(0.299, 0.587, 0.114));
            float gDev = (gLuma - 0.5) * 4.5;
            float effIntensity = uGrainIntensity * grainWeight * 2.0;
            
            if (uGrainBlendMode == 1) { // SCREEN
                float gNorm = clamp((gDev + 1.0) * 0.5, 0.0, 1.0);
                float sFactor = effIntensity * 0.90;
                if (uIsColorNoise) {
                    vec3 cDev = (gSample.rgb - vec3(0.5)) * 4.5;
                    vec3 cNorm = clamp((cDev + 1.0) * 0.5, 0.0, 1.0);
                    color = 1.0 - (1.0 - color) * (1.0 - cNorm * sFactor);
                } else {
                    color = 1.0 - (1.0 - color) * (1.0 - vec3(gNorm) * sFactor);
                }
            } else if (uGrainBlendMode == 2) { // MULTIPLY
                float gNorm = clamp((gDev + 1.0) * 0.5, 0.0, 1.0);
                float mFactor = 1.0 - (1.0 - gNorm) * effIntensity * 0.90;
                color *= mFactor;
            } else if (uGrainBlendMode == 3) { // NORMAL
                float gNorm = clamp((gDev + 1.0) * 0.5, 0.0, 1.0);
                color = mix(color, vec3(gNorm), effIntensity * 0.55);
            } else { // OVERLAY (mode 0)
                vec3 delta;
                if (uIsColorNoise) {
                    vec3 cDev = (gSample.rgb - vec3(0.5)) * 4.5;
                    delta = cDev * effIntensity * 0.95;
                } else {
                    delta = vec3(gDev) * effIntensity * 0.95;
                }
                color += delta;
            }
        } else {
            vec2 grainCoord = uv * 1400.0 * (uGrainScale * 0.45);
            float fineG = filmNoise(grainCoord, uRandomSeed + 0.18);
            float clumpG = dyeCloudNoise(grainCoord * 0.28, uRandomSeed + 0.42);
            float noiseG = fineG * 0.65 + clumpG * 0.35;
            float fineR = filmNoise(grainCoord, uRandomSeed + 0.31);
            float clumpR = dyeCloudNoise(grainCoord * 0.28, uRandomSeed + 0.67);
            float noiseR = fineR * 0.58 + clumpR * 0.42;
            float fineB = filmNoise(grainCoord, uRandomSeed + 0.53);
            float clumpB = dyeCloudNoise(grainCoord * 0.28, uRandomSeed + 0.89);
            float noiseB = fineB * 0.50 + clumpB * 0.50;
            vec3 grainDelta = uIsColorNoise ? vec3(noiseR, noiseG, noiseB) : mix(vec3(noiseG), vec3(noiseR, noiseG, noiseB), 0.18);
            float grainDamp = (uScanlineIntensity > 0.01) ? 0.35 : 1.0;
            color += grainDelta * (grainWeight * uGrainIntensity * 1.35 * grainDamp);
        }
    }

    // 14. Retro Camcorder Tape Texture & Line Modulation
    if (uScanlineIntensity > 0.01) {
        vec2 tapeCoord = vec2(uv.x * 1200.0 * 0.75, floor(uv.y * 240.0));
        float tapeNoise = filmNoise(tapeCoord, uRandomSeed);
        color += vec3(tapeNoise * 0.05 * uScanlineIntensity);

        float scanline = sin(uv.y * 480.0 * 3.14159) * 0.5 + 0.5;
        color *= mix(1.0, 0.88 + 0.12 * scanline, uScanlineIntensity);
    }

    // Fisheye Barrel Mask & Outer Boundary Falloff (applied after grain/scanlines so lens barrel stays pitch black)
    if (uLensType == 2) { // FISHEYE_F: Classic circular hood vignette
        color *= fisheyeMask;
        if (rPhys <= 0.465) {
            float rim = smoothstep(0.44, 0.462, rPhys) * (1.0 - smoothstep(0.462, 0.466, rPhys));
            color += vec3(0.08, 0.075, 0.07) * rim;
        }
    } else if (uLensType == 3) { // FISHEYE_W: Full-frame peripheral soft falloff
        float rCorner = isPortrait ? length(vec2(0.5, 0.5 / aspect)) : length(vec2(0.5 * aspect, 0.5));
        float rn = clamp(rPhys / rCorner, 0.0, 1.0);
        float wideVig = 1.0 - smoothstep(0.65, 1.05, rn);
        color *= mix(1.0, wideVig, 0.30);
    }

    fragColor = vec4(clamp(color, 0.0, 1.0), 1.0);
}
"""

    fun getLutAssetForPreset(presetId: String): String = when (presetId) {
        "zero"                 -> "lut_zero.png"
        "analog_fxn_r", "fxnr", "fxn_r" -> "lut_fxn_r.png"
        "ccd_r", "ccdr", "ccd r", "ccd-r", "ccd_classic" -> "lut_ccd_r.png"
        "analog_35mm_classic", "d_classic", "dclassic", "d classic", "classic_35mm" -> "lut_analog_35mm_classic.png"
        "ccd_grd", "analog_warm_film", "grd", "gr d" -> "lut_ccd_grd.png"
        "analog_soft_film", "analog_ct2f", "ct2f" -> "lut_ct2f.png"
        "analog_mju2", "mju2", "mju_ii", "mju ii", "olympus_mju2", "stylus_epic" -> "lut_olympus_mju2.png"
        "analog_vintage_color", "analog_dexp", "dexp", "d exp" -> "lut_dexp.png"
        "analog_cinema_film", "analog_d3d", "d3d", "d 3d" -> "lut_d3d.png"
        "analog_135ne", "135ne", "135 ne"-> "lut_135ne.png"
        "ccd_flash", "ccd flash", "d_funs", "dfuns" -> "lut_dfuns_v1.png"
        "ccd_retro_compact", "dqs" -> "lut_dqs_preserved.png"
        "ccd_y2k_digital", "classic_u", "classicu" -> "lut_ccd_y2k_digital.png"
        "disp_classic", "disp_golf", "golf" -> "lut_disp_classic.png"
        "disp_flash", "disp_cpm35", "cpm35", "cpm 35" -> "lut_disp_flash.png"
        "disp_warm", "analog_135sr", "135sr", "135 sr" -> "lut_disp_warm.png"
        "inst_vintage", "inst_c", "instc" -> "lut_inst_vintage.png"
        "inst_paf", "paf"      -> "lut_inst_vintage.png"
        "analog_nt16", "nt16"  -> "lut_nt16.png"
        "analog_dblack", "dblack", "d black" -> "lut_bw.png"
        "analog_s67", "s67"    -> "lut_analog_35mm_classic.png"
        "analog_fisheye_f", "fisheye" -> "lut_analog_35mm_classic.png"
        "cam_retro_vhs"        -> "lut_cam_retro_vhs.png"
        "cam_analog_video"     -> "lut_cam_analog_video.png"
        "cam_90s_camcorder"    -> "lut_cam_90s_camcorder.png"
        // VSCO Film Collection
        "vsco_a6", "a6"        -> "lut_vsco_a6.png"
        "vsco_c1", "c1"        -> "lut_vsco_c1.png"
        "vsco_f2", "f2"        -> "lut_vsco_f2.png"
        "vsco_g3", "g3"        -> "lut_vsco_g3.png"
        "vsco_m5", "m5"        -> "lut_vsco_m5.png"
        "vsco_hb1", "hb1"      -> "lut_vsco_hb1.png"
        "vsco_b1", "b1"        -> "lut_vsco_b1.png"
        "vsco_x1", "x1"        -> "lut_vsco_x1.png"
        "vsco_handycam", "handycam", "handycam85" -> "lut_vsco_handycam.png"
        "thermal_vision", "thermal", "thermal_ir" -> ""
        else                   -> "lut_analog_35mm_classic.png"
    }

    fun getGrainAssetForPreset(presetId: String, isCamcorder: Boolean): String = when {
        isCamcorder                            -> "grains/grain8mm_01.jpg"
        presetId == "zero"                     -> "grains/grain_dclassic.jpg"
        presetId in listOf("analog_fxn_r", "fxnr", "vsco_a6", "a6", "vsco_g3", "g3", "vsco_hb1", "hb1") -> "grains/grain_ou.jpg"
        presetId in listOf("ccd_r", "ccdr")   -> "grains/grain_ccdr.jpg"
        presetId in listOf("analog_35mm_classic", "d_classic", "dclassic", "vsco_c1", "c1", "vsco_b1", "b1") -> "grains/grain_dclassic.jpg"
        presetId in listOf("warm_film_gr") -> "grains/grain_ou.jpg"
        presetId in listOf("analog_soft_film", "analog_s67", "vsco_f2", "f2") -> "grains/grain_s67.jpg"
        presetId in listOf("analog_vintage_color", "analog_dexp", "vsco_m5", "m5", "vsco_x1", "x1") -> "grains/grain_dexp.jpg"
        presetId in listOf("analog_cinema_film", "analog_d3d", "d3d") -> "grains/grain_d3d1.jpg"
        presetId in listOf("analog_135ne", "135ne") -> "grains/grain_135sr.jpg"
        presetId in listOf("ccd_flash", "dfuns", "ccd_retro_compact", "dqs") -> "grains/grain_dfuns.jpg"
        presetId in listOf("ccd_y2k_digital", "classicu") -> "grains/grain_classicu.jpg"
        presetId in listOf("disp_classic", "disp_golf") -> "grains/grain_dexp2.jpg"
        presetId in listOf("disp_flash", "disp_cpm35", "ccd_grd", "analog_warm_film", "grd", "gr d", "analog_mju2", "mju2", "mju_ii", "mju ii", "olympus_mju2", "stylus_epic", "thermal_vision", "thermal", "thermal_ir") -> ""
        presetId in listOf("disp_warm", "135sr") -> "grains/grain540_01.jpg"
        presetId in listOf("inst_vintage", "inst_c") -> "grains/grain_instc.jpg"
        presetId in listOf("inst_paf", "paf") -> "grains/grain_paf.jpg"
        presetId in listOf("analog_dblack", "dblack") -> "grains/grain_dexp.jpg"
        presetId in listOf("analog_nt16", "nt16") -> "grains/grain_ou_lowlight.jpg"
        presetId in listOf("cam_retro_vhs", "retro_vhs", "vhs") -> "grains/vhs_grain01.jpg"
        presetId in listOf("vsco_handycam", "handycam", "handycam85") -> "grains/grain8mm_01.jpg"
        else                                   -> "grains/grain_dclassic.jpg"
    }
}

