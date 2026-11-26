# FXNR photo quality comparison · 24 November 2026

The strongest opportunity is to retain more subject detail in the lower midtones. In both matched pairs, CAMELEO lifted the deepest blacks while darkening the median substantially. Increasing the black lift alone would target the wrong part of the image.

## Measurements

Each JPEG was resized proportionally to fit 800 × 800 with Pillow, then converted to 8-bit luma. The median is on a 0–255 scale. The last two columns are percentages of pixels in the indicated luma ranges. Values measure rendered JPEG brightness, not sensor dynamic range.

| Source | Median | 0–15 | 240–255 |
|---|---:|---:|---:|
| S21 CAMELEO archived negative, capture 1000053791 | 87 | 0.61% | 0.00% |
| Same capture, FXNR export | 53 | 0.00% | 0.00% |
| Bundled scene processed on S21, archived negative 1000053808 | 52 | 4.54% | 0.06% |
| Same fixture, FXNR export | 24 | 0.58% | 0.00% |
| Samsung stock camera, EXIF SM-G991B, 4032 × 3024 | 156 | 2.22% | 0.00% |
| Dazz FXN_R sample 01 | 153 | 11.07% | 17.04% |
| Dazz FXN_R sample 02 | 15 | 50.61% | 5.63% |
| Dazz FXN_R sample 03 | 132 | 11.77% | 14.00% |
| Dazz FXN_R sample 04 | 174 | 3.21% | 0.22% |

The negatives are CAMELEO's pre-effect JPEG archives. Each matches its export in dimensions and composition. The older capture includes the processing version used on 15 November. The second pair exercises the recent Android development pipeline with an existing picture. The Samsung JPEG and the four bundled Dazz examples depict independent scenes. Their numbers describe those photographs and cannot isolate a filter effect. The Dazz examples span both dark and bright renderings, so their average would be a poor exposure target.

## Improvements to test, in order

1. **Preserve low-midtones.** In the packaged FXNR lookup table, a neutral input near RGB 64 maps to approximately (45, 51, 54), while an input near 192 maps to (209, 209, 206). The processing path also applies input conditioning, contrast, clarity and a shoulder. Export the same negative after each stage to locate the largest drop. Compare a gentler lower-mid curve against the current grade on dark hair, cloth and faces while keeping the warm highlight response.
2. **Recover highlight separation before adding bloom.** The CPU clarity step clamps channels before the final shoulder. Once two bright values reach the same ceiling, the shoulder cannot separate them. Compare windows and specular highlights across a neutral exposure ramp. Count adjacent patches that remain distinguishable, and check for a color shift in the brightest patches.
3. **Separate texture from sharpening.** The existing FXNR clarity adjustment is a global contrast operation. Compare fine fabric and softly lit skin at the same output size with clarity and grain toggled independently. Judge retained detail, edge halos and noise on flat patches. A higher sharpness score alone would reward extra grain.
4. **Verify the preview against the export.** The saved-photo LUT path can apply a device normalization matrix. Feed a fixed color chart through both renderers and compare corresponding patches, including the front camera. Correct systematic color differences before adjusting the stock's saturation.

## What would establish better photos

Use the same source JPEG in CAMELEO and Dazz wherever import is supported. For camera-only modes, photograph a fixed scene with the same framing, light and exposure, keeping Samsung's stock capture as a separate end-to-end baseline. Include daylight skin, warm indoor light, backlit windows and night scenes. Evaluate highlight detail and shadow texture at equal display size, then run a blind preference comparison for color and grain. The present measurements justify testing the FXNR midtone curve first. They do not establish a winner across cameras or film stocks.
