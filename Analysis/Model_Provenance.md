# CAMELEO Model Provenance & Licensing Register
## Computational Photography, Monocular Depth & Portrait Segmentation Assets

**Project:** CAMELEO  
**Package:** `com.craftcam.app.portrait` & `com.craftcam.app.scene`  
**Target Hardware:** Samsung Galaxy S21 5G (`SM-G991B`, Exynos 2100 / Mali-G78 MP14)  
**Document Version:** 1.0.0  
**License Compliance:** Apache 2.0 / Clean-Room Implementation  

---

## 1. Executive Licensing Overview

To guarantee complete offline operation, zero cloud data transfer, and strict privacy compliance (GDPR, CCPA, and Illinois BIPA), CAMELEO's computational portrait engine implements an independent, clean-room algorithmic architecture. No proprietary Apple, Samsung, Google, Dazz Cam, or Liit models, weights, or code are utilized.

---

## 2. Model & Algorithmic Provenance Register

### 2.1 Autonomous Monocular Defocus & Spatial Depth Estimator (`MonocularDepthEstimator.kt`)
- **Component Name:** CAMELEO Monocular DfD Depth Engine
- **Origin / Source:** Independent CAMELEO Implementation (Clean-room mathematical design based on optical Depth-from-Defocus principles and spatial geometric priors).
- **Version:** 1.0.0
- **License:** Apache 2.0 (Native CAMELEO Codebase)
- **Primary Purpose:** Downsampled ($256 \times 192$) relative inverse depth estimation from 2D luminance gradients, optical defocus cues, and hardware ISP face anchor constraints.
- **Modifications / Enhancements:** Integrated cross-bilateral edge-preserving guided smoothing and ground-sky perspective priors.
- **External Dependencies:** Zero. Operates 100% offline on standard CPU/Android Runtime without requiring external model downloads.
- **Redistribution Permission:** Permitted under Apache 2.0.

### 2.2 Photographic Anatomical Subject Segmenter (`SubjectSegmenter.kt`)
- **Component Name:** CAMELEO Photographic Anatomical Segmenter
- **Origin / Source:** Independent CAMELEO Implementation (Clean-room design combining facial anchor landmarks, anatomical upper-body trapezoidal projections, and YCbCr chromaticity clustering).
- **Version:** 1.0.0
- **License:** Apache 2.0 (Native CAMELEO Codebase)
- **Primary Purpose:** Generates soft-edged $[0.0, 1.0]$ subject confidence masks for single persons, two persons, or group portraits.
- **Modifications / Enhancements:** Integrated depth concordance filtering to reject background dropouts.
- **External Dependencies:** Zero.
- **Redistribution Permission:** Permitted under Apache 2.0.

### 2.3 Hardware ISP Face Detector Interface (`HardwareFaceDetector.kt`)
- **Component Name:** Android Camera2 Hardware Face Ingestion Pipeline
- **Origin / Source:** Android Open Source Project (AOSP) Camera2 HAL API (`android.hardware.camera2.CaptureResult.STATISTICS_FACES`).
- **Version:** Android API 34 (Samsung One UI 6.1 HAL implementation)
- **License:** Apache 2.0 (AOSP)
- **Primary Purpose:** Real-time ingestion of bounding boxes, eye landmarks, and face confidence directly from the Samsung S21 Exynos 2100 ISP silicon at 60 FPS.
- **Modifications / Enhancements:** Zero biometric identities or embeddings are extracted or stored. All face regions are strictly ephemeral volatile memory objects.
- **Redistribution Permission:** Permitted under Apache 2.0.

### 2.4 Optional Extensibility Interface: MobileNetV2-DeepLabV3 TFLite Model Specification
- **Component Name:** DeepLabV3 MobileNetV2 Quantized (Optional Neural Extension Hook)
- **Upstream Origin:** TensorFlow Model Garden / Google Research (AOSP)
- **Reference Repository:** `https://github.com/tensorflow/models/tree/master/research/deeplab`
- **Architecture:** MobileNetV2 backbone with Atrous Spatial Pyramid Pooling (ASPP).
- **Input Resolution:** $256 \times 256 \times 3$ RGB.
- **Quantization:** INT8 / FP16 quantized for mobile NPU/CPU execution.
- **License:** Apache 2.0
- **Redistribution Permission:** Permitted under Apache 2.0. Model execution is abstracted behind `DepthEstimator` and `SubjectSegmenter` interfaces, allowing hot-swapping if offline quantized weights are placed in `assets/models/`.

---

## 3. Dataset & Privacy Statement

1. **No External Training Datasets Embedded:** CAMELEO does not bundle scraped human image datasets or facial recognition databases.
2. **Biometric Privacy Safeguards:**
   - Facial bounding coordinates are discarded immediately after frame development.
   - Depth maps and subject masks are volatile float arrays wiped from heap after JPEG encoding.
   - Zero user photographs, masks, or metadata leave the device.

