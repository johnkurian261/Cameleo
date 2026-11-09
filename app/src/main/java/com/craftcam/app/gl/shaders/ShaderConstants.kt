package com.craftcam.app.gl.shaders

object ShaderConstants {

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

    if (uDebugStageOptics && !uNaturalModeEnabled) {
        if (uLensType == 2) { // FISHEYE_F: Classic circular hemispherical fisheye bubble
            float rBubble = 0.44; // Safe radius ensuring full 360° circular clearance without border cutoff
            if (rPhys > rBubble) {
                fisheyeMask = 0.0;
                uv = vec2(0.5);
            } else {
                float rn = rPhys / rBubble;
                // Authentic hemispherical fisheye warp: cubic radial expansion
                // rn goes 0..1, rDist goes 0..1 with strong center magnification (2.78x) and perimeter compression
                float rDistNorm = rn * (0.36 + 0.64 * rn * rn);
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
            float rDistNorm = rn * (0.55 + 0.45 * rn * rn);
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
    if (uDebugStageOptics && !uNaturalModeEnabled) {
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

    // 2-D3D. Single-Image Stereoscopic Parallax & Depth Separation
    if (uD3DEnabled && uD3DStrength > 0.01) {
        float yNorm = vQuadCoord.y;
        float centerDist = length(vec2(vQuadCoord.x - 0.5, (vQuadCoord.y - 0.5) * aspect));
        float depthPrior = clamp((1.0 - yNorm) * 0.65 + (1.0 - centerDist * 1.3) * 0.35, 0.0, 1.0);
        float pivot = 0.45;
        float delta = (depthPrior - pivot) * uD3DStrength;

        float parallaxOffset = delta * uD3DParallax * 0.5;
        float caOffset = delta * uD3DRgbSeparation;

        vec2 uvL = clamp(uv + vec2(-parallaxOffset + caOffset, 0.0), vec2(0.001), vec2(0.999));
        vec2 uvR = clamp(uv + vec2(parallaxOffset - caOffset, 0.0), vec2(0.001), vec2(0.999));

        vec2 oesL = (uTexMatrix * vec4(uvL, 0.0, 1.0)).xy;
        vec2 oesR = (uTexMatrix * vec4(uvR, 0.0, 1.0)).xy;

        float rL = texture(sTexture, oesL).r;
        float bR = texture(sTexture, oesR).b;

        if (uD3DAnaglyph > 0.5) {
            float leftLuma = 0.299 * rL + 0.587 * texture(sTexture, oesL).g + 0.114 * texture(sTexture, oesL).b;
            color = vec3(leftLuma, texture(sTexture, oesR).g, bR);
        } else {
            color = vec3(rL * 0.70 + color.r * 0.30, color.g, bR * 0.70 + color.b * 0.30);
        }
    }

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
    if (uLutEnabled && uDebugStageLut) {
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
            float rolledMax = knee + s * (d / (d + s * 0.75));
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
            float sLift = 0.035 * (1.0 - clamp(luma, 0.0, 1.0)) * (1.0 - clamp(luma, 0.0, 1.0));
            color += vec3(sLift);
            luma = dot(color, vec3(0.2126, 0.7152, 0.0722));
        }

        if (abs(uContrast - 1.0) > 0.01) {
            float pivot = 0.26;
            color = clamp((color - pivot) * uContrast + pivot, 0.0, 1.0);
        }

        // 5. Preset & User Saturation
        luma = dot(color, vec3(0.2126, 0.7152, 0.0722));
        if (abs(uSaturation - 1.0) > 0.01) {
            color = mix(vec3(luma), color, uSaturation);
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
        if (uHighlightRollOff < 0.99 && !uNaturalModeEnabled) {
            float knee = max(0.86, uHighlightRollOff);
            float maxCh = max(max(color.r, color.g), color.b);
            if (maxCh > knee) {
                float d = maxCh - knee;
                float s = 1.0 - knee;
                float rolledMax = knee + s * (d / (d + s * 0.75));
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

    // 10. Concentric Dual-Radius Film Halation (optimized 4-tap diamond sampling for preview)
    if (uHalationEnabled && uHalationIntensity > 0.01 && uDebugStageHalation) {
        float kThresh = uHalationThreshold;
        if (luma > kThresh - 0.04) {
            vec2 hStep = (10.0 / uViewportSize);
            vec2 diag = hStep * 0.7071;

            vec4 tTL = texture(sTexture, (uTexMatrix * vec4(clamp(uv + vec2(-diag.x, -diag.y), vec2(0.001), vec2(0.999)), 0.0, 1.0)).xy);
            vec4 tTR = texture(sTexture, (uTexMatrix * vec4(clamp(uv + vec2( diag.x, -diag.y), vec2(0.001), vec2(0.999)), 0.0, 1.0)).xy);
            vec4 tBL = texture(sTexture, (uTexMatrix * vec4(clamp(uv + vec2(-diag.x,  diag.y), vec2(0.001), vec2(0.999)), 0.0, 1.0)).xy);
            vec4 tBR = texture(sTexture, (uTexMatrix * vec4(clamp(uv + vec2( diag.x,  diag.y), vec2(0.001), vec2(0.999)), 0.0, 1.0)).xy);

            float lTL = dot(tTL.rgb, vec3(0.2126, 0.7152, 0.0722));
            float lTR = dot(tTR.rgb, vec3(0.2126, 0.7152, 0.0722));
            float lBL = dot(tBL.rgb, vec3(0.2126, 0.7152, 0.0722));
            float lBR = dot(tBR.rgb, vec3(0.2126, 0.7152, 0.0722));

            float hSum = max(0.0, lTL - kThresh) + max(0.0, lTR - kThresh) + max(0.0, lBL - kThresh) + max(0.0, lBR - kThresh);
            float spread = (hSum * 0.25) / max(0.01, 1.0 - kThresh);
            if (spread > 0.001) {
                // Highlight-core preservation: maintain neutral white in specular cores, concentrate red-orange fringe at boundaries
                float coreLuma = clamp((luma - kThresh) / max(0.01, 1.0 - kThresh), 0.0, 1.0);
                vec3 haloBase = mix(uHalationColorTint, vec3(0.40, 0.38, 0.35), coreLuma * 0.85);
                vec3 haloColor = haloBase * (spread * uHalationIntensity * 0.50);
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

    // Fisheye Barrel Mask & Outer Boundary Falloff
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

    if (uDebugStageOptics && !uNaturalModeEnabled) {
        if (uLensType == 2) { // FISHEYE_F: Classic circular hemispherical fisheye bubble
            float rBubble = 0.44; // Safe radius ensuring full 360° circular clearance without border cutoff
            if (rPhys > rBubble) {
                fisheyeMask = 0.0;
                uv = vec2(0.5);
            } else {
                float rn = rPhys / rBubble;
                // Authentic hemispherical fisheye warp: cubic radial expansion
                // rn goes 0..1, rDist goes 0..1 with strong center magnification (2.78x) and perimeter compression
                float rDistNorm = rn * (0.36 + 0.64 * rn * rn);
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
            float rDistNorm = rn * (0.55 + 0.45 * rn * rn);
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
    if (uDebugStageOptics && !uNaturalModeEnabled) {
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

    // 2-D3D. Single-Image Stereoscopic Parallax & Depth Separation
    if (uD3DEnabled && uD3DStrength > 0.01) {
        float yNorm = vQuadCoord.y;
        float centerDist = length(vec2(vQuadCoord.x - 0.5, (vQuadCoord.y - 0.5) * aspect));
        float depthPrior = clamp((1.0 - yNorm) * 0.65 + (1.0 - centerDist * 1.3) * 0.35, 0.0, 1.0);
        float pivot = 0.45;
        float delta = (depthPrior - pivot) * uD3DStrength;

        float parallaxOffset = delta * uD3DParallax * 0.5;
        float caOffset = delta * uD3DRgbSeparation;

        vec2 uvL = clamp(uv + vec2(-parallaxOffset + caOffset, 0.0), vec2(0.001), vec2(0.999));
        vec2 uvR = clamp(uv + vec2(parallaxOffset - caOffset, 0.0), vec2(0.001), vec2(0.999));

        vec2 oesL = (uTexMatrix * vec4(uvL, 0.0, 1.0)).xy;
        vec2 oesR = (uTexMatrix * vec4(uvR, 0.0, 1.0)).xy;

        float rL = texture(sTexture, oesL).r;
        float bR = texture(sTexture, oesR).b;

        if (uD3DAnaglyph > 0.5) {
            float leftLuma = 0.299 * rL + 0.587 * texture(sTexture, oesL).g + 0.114 * texture(sTexture, oesL).b;
            color = vec3(leftLuma, texture(sTexture, oesR).g, bR);
        } else {
            color = vec3(rL * 0.70 + color.r * 0.30, color.g, bR * 0.70 + color.b * 0.30);
        }
    }

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
    if (uLutEnabled && uDebugStageLut) {
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
            float rolledMax = knee + s * (d / (d + s * 0.75));
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
            float sLift = 0.035 * (1.0 - clamp(luma, 0.0, 1.0)) * (1.0 - clamp(luma, 0.0, 1.0));
            color += vec3(sLift);
            luma = dot(color, vec3(0.2126, 0.7152, 0.0722));
        }

        if (abs(uContrast - 1.0) > 0.01) {
            float pivot = 0.26;
            color = clamp((color - pivot) * uContrast + pivot, 0.0, 1.0);
        }

        // 5. Saturation
        luma = dot(color, vec3(0.2126, 0.7152, 0.0722));
        if (abs(uSaturation - 1.0) > 0.01) {
            color = mix(vec3(luma), color, uSaturation);
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
        if (uHighlightRollOff < 0.99 && !uNaturalModeEnabled) {
            float knee = max(0.86, uHighlightRollOff);
            float maxCh = max(max(color.r, color.g), color.b);
            if (maxCh > knee) {
                float d = maxCh - knee;
                float s = 1.0 - knee;
                float rolledMax = knee + s * (d / (d + s * 0.75));
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

    // Fisheye Barrel Mask & Outer Boundary Falloff
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

    fragColor = vec4(clamp(color, 0.0, 1.0), 1.0);
}
"""

    fun getLutAssetForPreset(presetId: String): String = when (presetId) {
        "zero"                 -> "lut_zero.png"
        "analog_fxn_r", "fxnr", "fxn_r" -> "lut_fxn_r.png"
        "ccd_r", "ccdr", "ccd r", "ccd-r", "ccd_classic" -> "lut_ccd_r.png"
        "analog_35mm_classic", "d_classic", "dclassic", "d classic", "classic_35mm" -> "lut_analog_35mm_classic.png"
        "analog_warm_film", "ccd_grd", "grd", "gr d" -> "lut_analog_warm_film.png"
        "analog_soft_film", "analog_ct2f", "ct2f" -> "lut_ct2f.png"
        "analog_vintage_color", "analog_dexp", "dexp", "d exp" -> "lut_dexp.png"
        "analog_cinema_film", "analog_d3d", "d3d", "d 3d" -> "lut_d3d.png"
        "analog_135ne", "135ne", "135 ne"-> "lut_135ne.png"
        "ccd_flash", "ccd flash", "d_funs", "dfuns" -> "lut_ccd_flash.png"
        "ccd_y2k_digital", "classic_u", "classicu" -> "lut_ccd_y2k_digital.png"
        "ccd_retro_compact", "dqs" -> "lut_ccd_retro_compact.png"
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
        else                   -> "lut_analog_35mm_classic.png"
    }

    fun getGrainAssetForPreset(presetId: String, isCamcorder: Boolean): String = when {
        isCamcorder                            -> "grains/grain8mm_01.jpg"
        presetId == "zero"                     -> "grains/grain_dclassic.jpg"
        presetId in listOf("analog_fxn_r", "fxnr", "vsco_a6", "a6", "vsco_g3", "g3", "vsco_hb1", "hb1") -> "grains/grain_ou.jpg"
        presetId in listOf("ccd_r", "ccdr")    -> "grains/grain_ccdr.jpg"
        presetId in listOf("analog_35mm_classic", "d_classic", "dclassic", "vsco_c1", "c1", "vsco_b1", "b1") -> "grains/grain_dclassic.jpg"
        presetId in listOf("analog_warm_film", "ccd_grd", "grd") -> "grains/grain_ou.jpg"
        presetId in listOf("analog_soft_film", "analog_s67", "vsco_f2", "f2") -> "grains/grain_s67.jpg"
        presetId in listOf("analog_vintage_color", "analog_dexp", "vsco_m5", "m5", "vsco_x1", "x1") -> "grains/grain_dexp.jpg"
        presetId in listOf("analog_cinema_film", "analog_d3d", "d3d") -> "grains/grain_d3d1.jpg"
        presetId in listOf("analog_135ne", "135ne") -> "grains/grain_135sr.jpg"
        presetId in listOf("ccd_flash", "dfuns") -> "grains/grain_dfuns.jpg"
        presetId in listOf("ccd_y2k_digital", "classicu") -> "grains/grain_classicu.jpg"
        presetId in listOf("ccd_retro_compact", "dqs") -> "grains/grain_dqs.jpg"
        presetId in listOf("disp_classic", "disp_golf") -> "grains/grain_dexp2.jpg"
        presetId in listOf("disp_flash", "disp_cpm35") -> "grains/grain_cpm35.jpg"
        presetId in listOf("disp_warm", "135sr") -> "grains/grain540_01.jpg"
        presetId in listOf("inst_vintage", "inst_c", "inst_paf", "paf") -> "grains/grain_instc.jpg"
        presetId in listOf("analog_dblack", "dblack") -> "grains/grain_dexp.jpg"
        presetId in listOf("analog_nt16", "nt16") -> "grains/grain_ou_lowlight.jpg"
        presetId in listOf("vsco_handycam", "handycam", "handycam85") -> "grains/grain8mm_01.jpg"
        else                                   -> "grains/grain_dclassic.jpg"
    }
}

