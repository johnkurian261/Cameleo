"""Prepare reduced-size scene inputs and a deterministic SDR chart. Requires Pillow."""
from pathlib import Path
from PIL import Image, ImageDraw

root = Path(__file__).resolve().parents[2]
out = root/"app/build/fxnr-inputs"
out.mkdir(parents=True, exist_ok=True)
scenes = {"portrait":"ccd_flash", "backlit":"analog_fxn_r_2", "sky":"analog_fxn_r_4",
          "tungsten":"analog_fxn_r", "night":"disp_night", "scene":"analog_cinema_film"}
for name, asset in scenes.items():
    src = root/f"app/src/main/res/drawable/sample_{asset}.jpg"
    im = Image.open(src).convert("RGB")
    im.thumbnail((512,512),Image.Resampling.LANCZOS)
    im.save(out/f"{name}.png")
archive = root/"app/build/d3d-probe/comparison-negative.jpg"
if archive.is_file():
    im = Image.open(archive).convert("RGB")
    im.thumbnail((512,512),Image.Resampling.LANCZOS)
    im.save(out/"s21.png")

im = Image.new("RGB",(512,256))
d = ImageDraw.Draw(im)
for x in range(512):
    level = x//2
    d.line((x,0,x,63),fill=(level,)*3)
for j,color in enumerate(["#563626","#ae7762","#656e42","#446399","#c89166","#dfc0a8","#834238","#323434"]):
    d.rectangle((j*64,64,j*64+63,127),fill=color)
# Known low-contrast periodic textile, flat dark reference and a high-contrast edge.
for x in range(512):
    for y in range(128,256):
        level = (48 + 5*((x//3+y//4)%2)) if x<128 else (48 if x<256 else 210)
        im.putpixel((x,y),(level,)*3)
im.save(out/"chart.png")
