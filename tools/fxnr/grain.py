from pathlib import Path
import numpy as np
from PIL import Image
p=Path("app/src/main/assets/grains")
a=np.asarray(Image.open(p/"grain_ou.jpg").convert("L"),dtype=float)
h,w=a.shape
a=a[h//2-512:h//2+512,w//2-512:w//2+512]
# Scale the measured scan fluctuations to ~2.5 output codes around the renderer's neutral density.
a=np.clip(np.rint(128+2.5*(a-a.mean())/a.std()),0,255).astype("uint8")
Image.fromarray(a).save(p/"fxnr_v2_density.png")
