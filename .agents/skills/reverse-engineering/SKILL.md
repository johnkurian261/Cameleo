---
name: reverse-engineering
description: >-
  Disciplined, evidence-based reverse engineering and architectural analysis of authorized binaries
  and assets (Mach-O, IPA packages, Android APKs, CoreUI asset catalogs, Metal shaders).
  Enforces strict legal and ethical compliance (no DRM or FairPlay decryption bypass),
  distinguishes observed facts from hypotheses, and guides static/dynamic analysis using
  Ghidra, strings, and asset extractors.
---

# Reverse Engineering & Binary Inspection Architecture

This skill establishes standard operating procedures for ethical, rigorous, and evidence-based reverse engineering of authorized mobile application bundles, Mach-O binaries, asset catalogs, and compiled shaders.

---

## 1. Activation Criteria

Activate this skill when:
- Analyzing authorized iOS (`.ipa`) or Android (`.apk`) application bundles for architecture, pipeline designs, or asset structures.
- Inspecting Mach-O 64-bit ARM binaries using Ghidra, strings, symbol tables, or Objective-C/Swift metadata.
- Extracting and analyzing iOS CoreUI asset catalogs (`Assets.car`), LZFSE-compressed payloads, or Apple CGBI PNGs.
- Inspecting compiled Metal shading language bytecode (`.metallib`, AIR format) to deduce rendering passes, blur radiuses, or color curves.
- Producing comparative technical reports or documenting implementation parameters.

---

## 2. Ethical, Legal & Operational Boundaries

> [!CAUTION]
> **Strict Operational Boundaries**:
> 1. **Authorized Scope Only**: Analyze ONLY files and binaries authorized for technical inspection and interoperability research.
> 2. **NO DRM Bypass**: Never attempt to crack, circumvent, or decrypt DRM-protected payloads (e.g., Apple FairPlay DRM `cryptid 1`, Google Play DRM, Widevine). If a binary has an encrypted `__TEXT` segment, that segment cannot and must not be analyzed; report the DRM boundary honestly and proceed with unencrypted bundle assets.
> 3. **Truthful Source Claims**: Never claim access to proprietary source code or confidential documentation without verifiable proof. Decompiled pseudocode is a reconstruction, not source code.
> 4. **Epistemic Classification**: Every technical statement in a report must be classified into:
>    - **[OBSERVED FACT]**: Direct evidence derived from an extracted asset, clear string literal, exported symbol, or binary header.
>    - **[HYPOTHESIS]**: An engineering inference, mathematical approximation, or behavioral deduction.

---

## 3. Tooling & Environment Reference

Our workspace contains the following verified static analysis tools:

| Tool | Location / Invocation | Purpose |
| :--- | :--- | :--- |
| **Ghidra 12.2** | `C:\tools\ghidra_12.2_PUBLIC\ghidraRun.bat` | Interactive GUI decompilation and disassembler (ARM64, x86_64). |
| **Ghidra Headless** | `C:\tools\ghidra_12.2_PUBLIC\support\analyzeHeadless.bat` | Scriptable batch analysis, headless decompilation, and symbol export. |
| **Python Utilities** | Python 3.14 (`lzfse`, `pefile`, `hachoir`, `PIL`) | Asset extraction, de-compression, and format conversions. |
| **CGBI Normalizer** | `c:\Users\User\Downloads\DazzProject\convert_cgbi.py` | Converts Apple-proprietary CGBI PNGs to standard sRGB PNG. |

---

## 4. Standard Workflows

### Workflow A: Bundle Inspection & DRM Verification
Before initiating binary analysis on a Mach-O file within an IPA payload:

```powershell
# 1. Inspect package structure
Expand-Archive -Path "App.ipa" -DestinationPath "Payload_Extracted"

# 2. Check encryption status in Mach-O load commands (using Python/pefile/hachoir)
python -c "
import struct
with open('Payload/App.app/AppBinary', 'rb') as f:
    header = f.read(4096)
    # Check for LC_ENCRYPTION_INFO_64 (0x2C)
    # If cryptid != 0, binary is FairPlay encrypted
    print('Header size:', len(header))
"
```
- If `cryptid == 1`: The binary executable code is encrypted on disk by the App Store. Binary disassembly will yield meaningless ciphertext. Restrict analysis strictly to unencrypted metadata (`Info.plist`, UI layout, CoreUI assets, shader libraries, config plists).

---

### Workflow B: Headless Ghidra Analysis
To decompile or search symbols without launching the heavy GUI:

```powershell
C:\tools\ghidra_12.2_PUBLIC\support\analyzeHeadless.bat ^
    "C:\temp\ghidra_projects" CameleoProject ^
    -import "Payload/App.app/AppBinary" ^
    -postScript StringSearchScript.java ^
    -deleteProject
```

Common Ghidra search targets for vintage camera apps:
- `MTLRenderPipelineState`, `MTLComputePipelineState`: Metal GPU rendering passes.
- Filter identifiers (e.g., `D35`, `Classic_U`, `FX_Grain`).
- Lens parameter structures (aperture, focal length, vignette falloff exponents).

---

### Workflow C: CoreUI `Assets.car` Deep Extraction
iOS bundles package textures, color palettes, and LUTs inside `Assets.car`.

1. **Format Overview**: CoreUI stores assets using Apple's proprietary BOM (Bill of Materials) tree index and CSIR/ISTC compression (often raw RGBA, JPEG, or LZFSE-compressed image blocks).
2. **Extraction Procedure**:
   - Extract raw streams from `Assets.car`.
   - If chunks are LZFSE-compressed:
     ```python
     import lzfse
     decompressed = lzfse.decompress(compressed_data)
     ```
   - If PNG images are inverted or premultiplied by Apple's `pngcrush -iphone`:
     Run `python convert_cgbi.py input_cgbi.png output_standard.png` to fix the byte order from BGRA to RGBA and reverse premultiplied alpha.

---

### Workflow D: Metal Shader Library Inspection (`.metallib`)
Metal shader libraries are compiled into Apple Intermediate Representation (AIR) bytecode:

1. Scan `.metallib` files for function names and string descriptors:
   ```powershell
   python -c "
   with open('default.metallib', 'rb') as f:
       data = f.read()
       # Search for ASCII function signatures
       import re
       funcs = re.findall(b'[a-zA-Z0-9_]{4,64}(?:kernel|vertex|fragment)', data)
       for fn in set(funcs):
           print(fn.decode('utf-8', errors='ignore'))
   "
   ```
2. Identify shader parameter buffer layouts: Look for uniforms such as `vignette_intensity`, `grain_scale`, `exposure_bias`, and color matrices ($3 \times 3$ or $4 \times 4$).

---

## 5. Technical Reporting Template

When documenting reverse-engineering findings for CAMELEO, adhere strictly to this template:

```markdown
# Reverse Engineering Technical Report: [Target Module]

## 1. Scope & Verification
- **Target File**: [e.g., Payload/App.app/AppBinary]
- **Tooling Used**: [e.g., Ghidra 12.2, Python 3.14 LZFSE]
- **DRM / Encryption Status**: [Unencrypted / FairPlay cryptid=0 / Not Applicable]

## 2. Observed Facts (Verified Evidence)
- **Asset / String**: [Exact path, hash, or string constant]
- **Observed Structure**: [e.g., Hald-64 CLUT, 512x512 PNG, 8-bit RGB]
- **Call Trace / Symbol**: [e.g., filterApplyPipelineWithLut:]

## 3. Engineering Hypotheses & Inferences
- **Hypothesis**: [e.g., Vignette calculation uses a cosine-fourth polynomial with exponent 2.0]
- **Confidence Rating**: [High / Medium / Low]
- **Technical Basis**: [Mathematical justification or correlation with visual output]

## 4. Cameleo Clean-Room Implementation Recommendation
- [Concrete, independent Kotlin/Shader implementation guidelines without infringing code]
```

---

## 6. Limitations

- **Stripped Symbols**: Release builds often strip Objective-C / Swift symbol names. Cross-reference string literals with cross-references (XRefs) in Ghidra.
- **Dynamic Swizzling**: Behavior altered at runtime via Objective-C runtime method swizzling cannot be fully resolved statically; document as hypothesis until verified.

