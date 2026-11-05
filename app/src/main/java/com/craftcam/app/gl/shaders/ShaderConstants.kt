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

    const val FRAGMENT_SHADER_VIEWFINDER = """#version 300 es
#extension GL_OES_EGL_image_external_essl3 : require
precision highp float;

uniform samplerExternalOES sTexture;
uniform sampler2D uLutTexture;
uniform bool uLutEnabled;
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
uniform float uHighlightRollOff;
uniform vec3 uShadowTint;

// Lens and Optical Effects
uniform float uVignetteIntensity;
uniform float uVignetteRadius;
uniform float uChromaticAberration;
uniform float uBarrelDistortion;
uniform float uSoftness;

// Film Grain
uniform float uGrainIntensity;
uniform float uGrainScale;
uniform bool uIsColorNoise;
uniform float uRandomSeed;

// Halation and Light Leaks
uniform bool uHalationEnabled;
uniform float uHalationThreshold;
uniform float uHalationIntensity;
uniform bool uLightLeakEnabled;
uniform float uLightLeakIntensity;
uniform float uScanlineIntensity;

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

void main() {
    vec2 p = vQuadCoord - 0.5;
    float r2 = dot(p, p);

    // 1. Lens Distortion (Barrel/Pincushion in canonical visual space)
    vec2 uv = vQuadCoord;
    if (abs(uBarrelDistortion) > 0.001) {
        uv = 0.5 + p * (1.0 + uBarrelDistortion * r2 * 0.4);
        uv = clamp(uv, vec2(0.001), vec2(0.999));
    }

    // 2. Transverse Chromatic Aberration (Cauchy spectral dispersion)
    vec4 srcColor;
    if (uChromaticAberration > 0.0001) {
        vec2 caOffset = p * (uChromaticAberration * r2 * 0.22);
        vec2 uvR = clamp(uv + caOffset, vec2(0.001), vec2(0.999));
        vec2 uvG = clamp(uv - caOffset * 0.15, vec2(0.001), vec2(0.999));
        vec2 uvB = clamp(uv - caOffset * 1.35, vec2(0.001), vec2(0.999));

        vec2 oesR = (uTexMatrix * vec4(uvR, 0.0, 1.0)).xy;
        vec2 oesG = (uTexMatrix * vec4(uvG, 0.0, 1.0)).xy;
        vec2 oesB = (uTexMatrix * vec4(uvB, 0.0, 1.0)).xy;

        float r = texture(sTexture, oesR).r;
        float g = texture(sTexture, oesG).g;
        float b = texture(sTexture, oesB).b;
        srcColor = vec4(r, g, b, 1.0);
    } else {
        vec2 oesUV = (uTexMatrix * vec4(uv, 0.0, 1.0)).xy;
        srcColor = texture(sTexture, oesUV);
    }

    vec3 color = srcColor.rgb;

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

    // 2d. Photochemical 3D Film Emulsion LUT
    if (uLutEnabled) {
        color = sampleHaldLut(uLutTexture, color);
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
    if (abs(uContrast - 1.0) > 0.01) {
        color = clamp((color - 0.5) * uContrast + 0.5, 0.0, 1.0);
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

    // 8. Filmic Highlight Rolloff & Desaturating Shoulder (Creamy soft transitions to white)
    if (uHighlightRollOff < 0.99) {
        float knee = max(0.01, 1.0 - uHighlightRollOff);
        vec3 over = max(vec3(0.0), color - vec3(uHighlightRollOff));
        color = min(color, vec3(uHighlightRollOff)) + knee * (vec3(1.0) - exp(-over / knee));
        luma = dot(color, vec3(0.2126, 0.7152, 0.0722));
        
        float highOver = max(0.0, luma - uHighlightRollOff) / knee;
        color = mix(color, vec3(luma), clamp(highOver * 0.35, 0.0, 0.65));
    }

    // 9. Film Fade (Lifted blacks / aged paper floor)
    if (uFade > 0.001) {
        color = color * (1.0 - uFade) + uFade;
    }

    // 10. Concentric Dual-Radius Film Halation (Cadmium red-orange backscattering with boundary confinement)
    if (uHalationEnabled && uHalationIntensity > 0.01) {
        float kThresh = uHalationThreshold;
        if (luma > kThresh - 0.04) {
            vec2 hInner = (6.0 / uViewportSize);
            vec2 hOuter = (14.0 / uViewportSize);

            vec4 tInL = texture(sTexture, (uTexMatrix * vec4(clamp(uv - vec2(hInner.x, 0.0), vec2(0.001), vec2(0.999)), 0.0, 1.0)).xy);
            vec4 tInR = texture(sTexture, (uTexMatrix * vec4(clamp(uv + vec2(hInner.x, 0.0), vec2(0.001), vec2(0.999)), 0.0, 1.0)).xy);
            vec4 tInT = texture(sTexture, (uTexMatrix * vec4(clamp(uv - vec2(0.0, hInner.y), vec2(0.001), vec2(0.999)), 0.0, 1.0)).xy);
            vec4 tInB = texture(sTexture, (uTexMatrix * vec4(clamp(uv + vec2(0.0, hInner.y), vec2(0.001), vec2(0.999)), 0.0, 1.0)).xy);

            vec2 diag = hOuter * 0.7071;
            vec4 tOutTL = texture(sTexture, (uTexMatrix * vec4(clamp(uv + vec2(-diag.x, -diag.y), vec2(0.001), vec2(0.999)), 0.0, 1.0)).xy);
            vec4 tOutTR = texture(sTexture, (uTexMatrix * vec4(clamp(uv + vec2( diag.x, -diag.y), vec2(0.001), vec2(0.999)), 0.0, 1.0)).xy);
            vec4 tOutBL = texture(sTexture, (uTexMatrix * vec4(clamp(uv + vec2(-diag.x,  diag.y), vec2(0.001), vec2(0.999)), 0.0, 1.0)).xy);
            vec4 tOutBR = texture(sTexture, (uTexMatrix * vec4(clamp(uv + vec2( diag.x,  diag.y), vec2(0.001), vec2(0.999)), 0.0, 1.0)).xy);

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
                vec3 haloBase = mix(vec3(1.0, 0.32, 0.10), vec3(0.40, 0.38, 0.35), coreLuma * 0.85);
                vec3 haloColor = haloBase * (spread * uHalationIntensity * 0.45);
                color += haloColor;
            }
        }
    }

    // 11. Subtle Edge Light Leak Emulation
    if (uLightLeakEnabled && uLightLeakIntensity > 0.01) {
        float edgeDist = length(vec2(max(0.0, uv.x - 0.75) * 2.0, max(0.0, 0.25 - uv.y) * 2.0));
        float leakMask = smoothstep(0.40, 0.0, edgeDist);
        vec3 leakColor = vec3(1.0, 0.45, 0.12) * (leakMask * uLightLeakIntensity * 0.35);
        color = 1.0 - (1.0 - color) * (1.0 - leakColor);
    }

    // 12. Optical Cosine Fourth Lens Vignette
    if (uVignetteIntensity > 0.01) {
        float rNorm = length(p) / 0.7071068;
        float rn = rNorm / uVignetteRadius;
        float cos4Vig = 1.0 / pow(1.0 + rn * rn * 0.55, 2.0);
        float vignette = mix(1.0, cos4Vig, clamp(uVignetteIntensity * 1.15, 0.0, 1.0));
        color *= clamp(vignette, 0.25, 1.0);
    }

    // 13. Authentic Multi-Scale Silver Halide Dye-Cloud Grain
    if (uGrainIntensity > 0.005) {
        float sRise = smoothstep(0.0, 0.42, luma);
        float hFall = smoothstep(0.42, 1.0, luma);
        float grainWeight = (0.28 + 0.72 * sRise) * (1.0 - 0.88 * hFall);

        if (color.r > color.g && color.g > color.b && color.r > 0.25 && color.r < 0.90) {
            float skinWeight = clamp((color.r - color.b) * 2.0, 0.0, 1.0);
            grainWeight *= (1.0 - 0.35 * skinWeight);
        }

        vec2 grainCoord = vQuadCoord * uViewportSize.xy * (uGrainScale * 0.45);
        
        float fineG = filmNoise(grainCoord, uRandomSeed + 0.18);
        float clumpG = dyeCloudNoise(grainCoord * 0.28, uRandomSeed + 0.42);
        float noiseG = fineG * 0.65 + clumpG * 0.35;

        float fineR = filmNoise(grainCoord, uRandomSeed + 0.31);
        float clumpR = dyeCloudNoise(grainCoord * 0.28, uRandomSeed + 0.67);
        float noiseR = fineR * 0.58 + clumpR * 0.42;

        float fineB = filmNoise(grainCoord, uRandomSeed + 0.53);
        float clumpB = dyeCloudNoise(grainCoord * 0.28, uRandomSeed + 0.89);
        float noiseB = fineB * 0.50 + clumpB * 0.50;

        vec3 grainDelta;
        if (uIsColorNoise) {
            grainDelta = vec3(noiseR, noiseG, noiseB);
        } else {
            grainDelta = mix(vec3(noiseG), vec3(noiseR, noiseG, noiseB), 0.18);
        }

        float grainDamp = (uScanlineIntensity > 0.01) ? 0.35 : 1.0;
        color += grainDelta * (grainWeight * uGrainIntensity * 0.40 * grainDamp);
    }

    // 14. Retro Camcorder Tape Texture & Line Modulation
    if (uScanlineIntensity > 0.01) {
        vec2 tapeCoord = vec2(vQuadCoord.x * uViewportSize.x * 0.75, floor(vQuadCoord.y * 240.0));
        float tapeNoise = filmNoise(tapeCoord, uRandomSeed);
        color += vec3(tapeNoise * 0.05 * uScanlineIntensity);

        float scanline = sin(vQuadCoord.y * 480.0 * 3.14159) * 0.5 + 0.5;
        color *= mix(1.0, 0.88 + 0.12 * scanline, uScanlineIntensity);
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

uniform sampler2D sTexture;
uniform sampler2D uLutTexture;
uniform bool uLutEnabled;
in vec2 vTextureCoord;
out vec4 fragColor;

// Tonal and Color Grading
uniform float uContrast;
uniform float uSaturation;
uniform float uTemperature;
uniform float uTint;
uniform float uGamma;
uniform float uFade;
uniform float uHighlightRollOff;
uniform vec3 uShadowTint;

// Lens and Optical Effects
uniform float uVignetteIntensity;
uniform float uVignetteRadius;
uniform float uChromaticAberration;
uniform float uBarrelDistortion;
uniform float uSoftness;

// Film Grain
uniform float uGrainIntensity;
uniform float uGrainScale;
uniform bool uIsColorNoise;
uniform float uRandomSeed;

// Halation and Light Leaks
uniform bool uHalationEnabled;
uniform float uHalationThreshold;
uniform float uHalationIntensity;
uniform bool uLightLeakEnabled;
uniform float uLightLeakIntensity;
uniform float uScanlineIntensity;

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

void main() {
    vec2 uv = vTextureCoord;
    vec2 p = uv - 0.5;
    float r2 = dot(p, p);

    // 1. Lens Distortion
    if (abs(uBarrelDistortion) > 0.001) {
        uv = 0.5 + p * (1.0 + uBarrelDistortion * r2 * 0.4);
        uv = clamp(uv, vec2(0.001), vec2(0.999));
    }

    // 2. Transverse Chromatic Aberration (Cauchy spectral dispersion)
    vec4 srcColor;
    if (uChromaticAberration > 0.0001) {
        vec2 caOffset = p * (uChromaticAberration * r2 * 0.25);
        vec2 uvR = clamp(uv + caOffset, vec2(0.001), vec2(0.999));
        vec2 uvG = clamp(uv - caOffset * 0.15, vec2(0.001), vec2(0.999));
        vec2 uvB = clamp(uv - caOffset * 1.35, vec2(0.001), vec2(0.999));
        float r = texture(sTexture, uvR).r;
        float g = texture(sTexture, uvG).g;
        float b = texture(sTexture, uvB).b;
        srcColor = vec4(r, g, b, 1.0);
    } else {
        srcColor = texture(sTexture, uv);
    }

    vec3 color = srcColor.rgb;

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

    // 2d. Photochemical 3D Film Emulsion LUT
    if (uLutEnabled) {
        color = sampleHaldLut(uLutTexture, color);
    }

    // 3. White Balance
    color.r += 0.10 * uTemperature;
    color.b -= 0.10 * uTemperature;
    color.g += 0.06 * uTint;
    color.r -= 0.03 * uTint;
    color.b -= 0.03 * uTint;

    // 4. Contrast
    if (abs(uContrast - 1.0) > 0.01) {
        color = clamp((color - 0.5) * uContrast + 0.5, 0.0, 1.0);
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

    // 8. Filmic Highlight Rolloff & Desaturating Shoulder (Creamy soft transitions to white)
    if (uHighlightRollOff < 0.99) {
        float knee = max(0.01, 1.0 - uHighlightRollOff);
        vec3 over = max(vec3(0.0), color - vec3(uHighlightRollOff));
        color = min(color, vec3(uHighlightRollOff)) + knee * (vec3(1.0) - exp(-over / knee));
        luma = dot(color, vec3(0.2126, 0.7152, 0.0722));
        
        float highOver = max(0.0, luma - uHighlightRollOff) / knee;
        color = mix(color, vec3(luma), clamp(highOver * 0.35, 0.0, 0.65));
    }

    // 9. Film Fade
    if (uFade > 0.001) {
        color = color * (1.0 - uFade) + uFade;
    }

    // 10. Concentric Dual-Radius Film Halation (Cadmium red-orange backscattering with boundary confinement)
    if (uHalationEnabled && uHalationIntensity > 0.01) {
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
                vec3 haloBase = mix(vec3(1.0, 0.32, 0.10), vec3(0.40, 0.38, 0.35), coreLuma * 0.85);
                vec3 haloColor = haloBase * (spread * uHalationIntensity * 0.45);
                color += haloColor;
            }
        }
    }

    // 11. Subtle Edge Light Leak Emulation
    if (uLightLeakEnabled && uLightLeakIntensity > 0.01) {
        float edgeDist = length(vec2(max(0.0, uv.x - 0.75) * 2.0, max(0.0, 0.25 - uv.y) * 2.0));
        float leakMask = smoothstep(0.40, 0.0, edgeDist);
        vec3 leakColor = vec3(1.0, 0.45, 0.12) * (leakMask * uLightLeakIntensity * 0.35);
        color = 1.0 - (1.0 - color) * (1.0 - leakColor);
    }

    // 12. Optical Cosine Fourth Lens Vignette
    if (uVignetteIntensity > 0.01) {
        float rNorm = length(p) / 0.7071068;
        float rn = rNorm / uVignetteRadius;
        float cos4Vig = 1.0 / pow(1.0 + rn * rn * 0.55, 2.0);
        float vignette = mix(1.0, cos4Vig, clamp(uVignetteIntensity * 1.15, 0.0, 1.0));
        color *= clamp(vignette, 0.25, 1.0);
    }

    // 13. Authentic Multi-Scale Silver Halide Dye-Cloud Grain
    if (uGrainIntensity > 0.005) {
        float sRise = smoothstep(0.0, 0.42, luma);
        float hFall = smoothstep(0.42, 1.0, luma);
        float grainWeight = (0.28 + 0.72 * sRise) * (1.0 - 0.88 * hFall);

        if (color.r > color.g && color.g > color.b && color.r > 0.25 && color.r < 0.90) {
            float skinWeight = clamp((color.r - color.b) * 2.0, 0.0, 1.0);
            grainWeight *= (1.0 - 0.35 * skinWeight);
        }

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

        vec3 grainDelta;
        if (uIsColorNoise) {
            grainDelta = vec3(noiseR, noiseG, noiseB);
        } else {
            grainDelta = mix(vec3(noiseG), vec3(noiseR, noiseG, noiseB), 0.18);
        }

        float grainDamp = (uScanlineIntensity > 0.01) ? 0.35 : 1.0;
        color += grainDelta * (grainWeight * uGrainIntensity * 0.40 * grainDamp);
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
        "analog_35mm_classic"  -> "lut_analog_35mm_classic.png"
        "analog_warm_film"     -> "lut_analog_warm_film.png"
        "analog_soft_film"     -> "lut_analog_soft_film.png"
        "analog_vintage_color" -> "lut_analog_vintage_color.png"
        "analog_cinema_film"   -> "lut_analog_cinema_film.png"
        "ccd_flash"            -> "lut_ccd_flash.png"
        "ccd_y2k_digital"      -> "lut_ccd_y2k_digital.png"
        "ccd_retro_compact"    -> "lut_ccd_retro_compact.png"
        "disp_classic"         -> "lut_disp_classic.png"
        "disp_flash"           -> "lut_disp_flash.png"
        "disp_warm"            -> "lut_disp_warm.png"
        "disp_night"           -> "lut_disp_night.png"
        "inst_vintage"         -> "lut_inst_vintage.png"
        "cam_retro_vhs"        -> "lut_cam_retro_vhs.png"
        "cam_analog_video"     -> "lut_cam_analog_video.png"
        "cam_90s_camcorder"    -> "lut_cam_90s_camcorder.png"
        else                   -> "lut_analog_35mm_classic.png"
    }
}

