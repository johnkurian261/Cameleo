---
name: computational-photography
description: >-
  Advanced computational photography pipeline for CAMELEO. Covers mathematical foundations, algorithms,
  and Kotlin/OpenCV/OpenGL implementations for HDR exposure fusion (Mertens), burst multi-frame noise
  reduction (MFNR), optical flow and feature-based frame alignment (ORB/AKAZE), demosaicing, tone mapping
  operators (Reinhard, Durand, Filmic S-curve), color science (3D Hald CLUT trilinear interpolation),
  and vintage film emulation (halide grain, optical vignette falloff).
---

# Computational Photography & Color Science Pipeline

This skill provides mathematical formulations, algorithmic blueprints, and implementation patterns for CAMELEO's computational photography and film rendering engine.

---

## 1. Activation Criteria

Activate this skill when:
- Designing or implementing multi-frame image fusion (HDR, burst denoising, super-resolution).
- Developing motion-aware alignment algorithms (pyramidal optical flow, homography via RANSAC).
- Implementing or tuning color science, 3D LUT sampling, and color transformation matrices.
- Optimizing tone mapping operators (Filmic S-curve, Reinhard, Durand bilateral filter).
- Synthesizing physical film emulation effects (silver halide grain, optical cosine-fourth vignette, halation, chromatic aberration).
- Accelerating image processing algorithms using multithreaded Kotlin, OpenCV JNI, or OpenGL ES compute/fragment shaders.

---

## 2. Mathematical Foundations & Algorithms

### 2.1 Multi-Exposure Fusion (Mertens-Kautz-Van Reeth Algorithm)
Instead of reconstructing high-dynamic-range radiance maps and tonemapping, exposure fusion directly combines a bracketed sequence into a high-quality LDR image without color shifts or tone inversion.

#### 1. Quality Measures per pixel $(i,j)$ in frame $k$:
- **Contrast ($C$)**: Absolute value of the response to a discrete Laplacian filter:
  $$C_{k}(i,j) = |\nabla^2 I_k(i,j)|$$
- **Saturation ($S$)**: Standard deviation across color channels:
  $$S_k(i,j) = \sqrt{\frac{1}{3} \sum_{c \in \{R,G,B\}} (I_k^c(i,j) - \mu_k(i,j))^2}$$
- **Well-Exposedness ($E$)**: Proximity to mid-gray ($0.5$), evaluated with a Gaussian weighting curve ($\sigma = 0.2$):
  $$E_k(i,j) = \prod_{c \in \{R,G,B\}} \exp\left( - \frac{(I_k^c(i,j) - 0.5)^2}{2\sigma^2} \right)$$

#### 2. Composite Weight Map:
$$W_k(i,j) = \left[ C_k(i,j) \right]^{\omega_c} \times \left[ S_k(i,j) \right]^{\omega_s} \times \left[ E_k(i,j) \right]^{\omega_e}$$
Normalized across $N$ bracketed frames:
$$\hat{W}_k(i,j) = \frac{W_k(i,j)}{\sum_{m=1}^{N} W_m(i,j) + \epsilon}$$

#### 3. Multi-Scale Pyramid Blending:
To prevent seams and halo artifacts, decompose weight maps into a **Gaussian Pyramid** $G\{ \hat{W}_k \}$ and images into a **Laplacian Pyramid** $L\{ I_k \}$:
$$L\{ F \}_l(i,j) = \sum_{k=1}^N G\{ \hat{W}_k \}_l(i,j) \cdot L\{ I_k \}_l(i,j)$$
The final fused image $F$ is reconstructed by recursively expanding and summing the Laplacian levels:
$$F = \sum_{l=0}^{M} \text{expand}^l(L\{ F \}_l)$$

---

### 2.2 Burst Photography & Multi-Frame Noise Reduction (MFNR)
For low-light shooting without camera shake:
1. Capture a rapid burst of $M$ identical short-exposure frames ($M \in [4, 8]$) to avoid motion blur.
2. Select the sharpest frame (highest Laplacian variance $\text{Var}(\nabla^2 I)$) as reference frame $I_{\text{ref}}$.
3. Align each secondary frame $I_k$ to $I_{\text{ref}}$ using a 2D affine or perspective homography $H_k$:
   $$p_{\text{ref}} \sim H_k p_k$$
4. **Motion-Aware Temporal Denoising**:
   Given camera noise model $\sigma^2(I) = \sigma_{\text{read}}^2 + I \cdot \sigma_{\text{shot}}^2$, compute temporal blending weight:
   $$w_k(i,j) = \exp\left( - \frac{|I_k(i,j) - I_{\text{ref}}(i,j)|^2}{2 \cdot \gamma \cdot \sigma^2(I_{\text{ref}}(i,j))} \right)$$
   $$\hat{I}(i,j) = \frac{I_{\text{ref}}(i,j) + \sum_{k=1}^{M-1} w_k(i,j) I_k(i,j)}{1 + \sum_{k=1}^{M-1} w_k(i,j)}$$
   In moving areas, $w_k \to 0$, falling back safely to the sharp reference frame to eliminate ghosting.

---

### 2.3 Color Science & 3D Hald CLUT Trilinear Interpolation
In CAMELEO, film profiles use 3D Color Lookup Tables (CLUTs) formatted as Hald grids ($N \times N \times N$, e.g., $N=64$ or $N=512$).

#### Trilinear Interpolation Mathematics:
Given input RGB color $(r, g, b) \in [0.0, 1.0]^3$, compute continuous lattice coordinates:
$$x = r \cdot (N - 1), \quad y = g \cdot (N - 1), \quad z = b \cdot (N - 1)$$
Decompose into discrete lower indices and fractional interpolation weights:
$$x_0 = \lfloor x \rfloor, \quad x_1 = \min(x_0 + 1, N - 1), \quad \Delta x = x - x_0$$
$$y_0 = \lfloor y \rfloor, \quad y_1 = \min(y_0 + 1, N - 1), \quad \Delta y = y - y_0$$
$$z_0 = \lfloor z \rfloor, \quad z_1 = \min(z_0 + 1, N - 1), \quad \Delta z = z - z_0$$

Evaluate the 8 corner vertices $C_{x, y, z}$ with weights:
$$C(r,g,b) = \sum_{i=0}^1 \sum_{j=0}^1 \sum_{k=0}^1 \left[ (1 - \Delta x)^{1-i} \Delta x^i \right] \left[ (1 - \Delta y)^{1-j} \Delta y^j \right] \left[ (1 - \Delta z)^{1-k} \Delta z^k \right] C_{x_i, y_j, z_k}$$

> [!IMPORTANT]
> **Zero-Banding Guarantee**: Nearest-neighbor or naive bilinear sampling induces severe 8-bit banding in smooth sky and skin tones. Trilinear interpolation on 3D floating-point lattice coordinates is mandatory.

---

### 2.4 Physical Film Emulation Modeling

#### 1. Silver Halide Organic Grain:
Real photographic grain is an emulsion of silver halide crystals clustered predominantly in midtones, tapering off in deep shadows and blown highlights.
- Generate standard Gaussian noise via Box-Muller transform:
  $$Z = \sqrt{-2 \ln U_1} \cdot \cos(2\pi U_2), \quad U_1, U_2 \sim \mathcal{U}(0, 1)$$
- Modulate by luminance-dependent response curve $M(Y)$ where $Y \in [0, 1]$ is Rec.709 luminance:
  $$M(Y) = 4.0 \cdot Y \cdot (1.0 - Y)$$
- Additive grain modulation:
  $$I_{\text{film}} = I + Z \cdot M(Y) \cdot \kappa_{\text{grain}}$$

#### 2. Optical Cosine-Fourth Law Vignette:
Camera lenses produce natural radial light falloff governed by the $\cos^4(\theta)$ law.
- For sensor coordinates $(x, y)$ centered at origin $(0, 0)$ with half-diagonal $R_{\max}$:
  $$\bar{r} = \frac{\sqrt{x^2 + y^2}}{R_{\max}}$$
- Polynomial approximation avoiding expensive trigonometric evaluation:
  $$V(\bar{r}) = 1.0 - \alpha_{\text{vignette}} \cdot \bar{r}^2 + \beta_{\text{vignette}} \cdot \bar{r}^4$$
  $$I_{\text{vignette}} = I \cdot V(\bar{r})$$

---

## 3. Implementation Guidance

### 3.1 High-Performance Kotlin Multithreaded Processing
For Android CPU execution without JNI overhead, partition image rows across available CPU cores:

```kotlin
suspend fun applyTrilinearLutParallel(
    pixels: IntArray,
    width: Int,
    height: Int,
    lutCube: FloatArray, // Flattened 3D float array [R][G][B][3]
    lutSize: Int
) = withContext(Dispatchers.Default) {
    val numCores = Runtime.getRuntime().availableProcessors().coerceAtLeast(2)
    val chunkSize = (height + numCores - 1) / numCores

    val jobs = (0 until numCores).map { coreIndex ->
        launch {
            val startY = coreIndex * chunkSize
            val endY = minOf(startY + chunkSize, height)
            val lutMax = lutSize - 1

            for (y in startY until endY) {
                var idx = y * width
                for (x in 0 until width) {
                    val color = pixels[idx]
                    val r = (color shr 16 and 0xFF) / 255.0f
                    val g = (color shr 8 and 0xFF) / 255.0f
                    val b = (color and 0xFF) / 255.0f

                    // Continuous coordinate mapping
                    val fx = r * lutMax
                    val fy = g * lutMax
                    val fz = b * lutMax

                    val x0 = fx.toInt()
                    val y0 = fy.toInt()
                    val z0 = fz.toInt()

                    val x1 = minOf(x0 + 1, lutMax)
                    val y1 = minOf(y0 + 1, lutMax)
                    val z1 = minOf(z0 + 1, lutMax)

                    val dx = fx - x0
                    val dy = fy - y0
                    val dz = fz - z0

                    // Trilinear interpolation across 8 vertices
                    val outR = trilinearSampleChannel(lutCube, x0, y0, z0, x1, y1, z1, dx, dy, dz, lutSize, 0)
                    val outG = trilinearSampleChannel(lutCube, x0, y0, z0, x1, y1, z1, dx, dy, dz, lutSize, 1)
                    val outB = trilinearSampleChannel(lutCube, x0, y0, z0, x1, y1, z1, dx, dy, dz, lutSize, 2)

                    pixels[idx] = (0xFF shl 24) or
                        ((outR * 255f).toInt().coerceIn(0, 255) shl 16) or
                        ((outG * 255f).toInt().coerceIn(0, 255) shl 8) or
                        ((outB * 255f).toInt().coerceIn(0, 255))
                    idx++
                }
            }
        }
    }
    jobs.joinAll()
}
```

### 3.2 OpenCV Architecture for Multi-Frame Fusion
When incorporating native OpenCV JNI for heavy computational tasks (homography estimation, optical flow, exposure fusion):

```kotlin
// Mertens Fusion workflow via OpenCV Android SDK
fun fuseBracketedExposureSequence(bracketedBitmaps: List<Bitmap>): Bitmap {
    val matList = ArrayList<Mat>()
    for (bitmap in bracketedBitmaps) {
        val mat = Mat()
        Utils.bitmapToMat(bitmap, mat)
        Imgproc.cvtColor(mat, mat, Imgproc.COLOR_RGBA2RGB)
        matList.add(mat)
    }

    val mergeMertens = Photo.createMergeMertens()
    val fusedMat = Mat()
    mergeMertens.process(matList, fusedMat)

    // Convert back from 32-bit float [0, 1] to 8-bit [0, 255]
    fusedMat.convertTo(fusedMat, CvType.CV_8UC3, 255.0)
    Imgproc.cvtColor(fusedMat, fusedMat, Imgproc.COLOR_RGB2RGBA)

    val outputBitmap = Bitmap.createBitmap(fusedMat.cols(), fusedMat.rows(), Bitmap.Config.ARGB_8888)
    Utils.matToBitmap(fusedMat, outputBitmap)

    // Release native memory
    matList.forEach { it.release() }
    fusedMat.release()

    return outputBitmap
}
```

---

## 4. Limitations & Performance Guidelines

1. **Memory Budget & Heap Allocation**:
   - A $12\text{MP}$ ($4000 \times 3000$) `ARGB_8888` bitmap consumes $48\text{ MB}$ uncompressed memory.
   - A burst of 6 frames requires $288\text{ MB}$ in RAM.
   - **Rule**: Never hold multiple full-resolution bitmaps in the JVM heap simultaneously. Process frames in-place or downscale for alignment vector calculation, then warp native frames sequentially.
2. **Execution Latency Targets**:
   - Live Viewfinder LUT: $\le 16\text{ ms}$ (60 FPS) via OpenGL ES fragment shader.
   - High-Res Capture Post-Processing: $\le 800\text{ ms}$ for $12\text{MP}$ trilinear LUT + vignette + grain.
   - Multi-Frame Fusion: $\le 2.5\text{ s}$ total processing time with asynchronous UI progress reporting.

---

## 5. Expected Outputs & Verification

- **Color Accuracy**: Verify dynamic range and color rendition with test charts (e.g., Macbeth ColorChecker 24).
- **Smoothness**: Delta-E gradients must exhibit zero stepped contouring in 10-bit and 8-bit displays.
- **Artifact-Free Output**: Fusion algorithms must display zero edge haloing or motion ghosting.

