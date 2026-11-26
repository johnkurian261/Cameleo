"""Summarize S21 renderer output. Run after pulling fxnr-results. Requires Pillow and NumPy."""
from pathlib import Path
import csv
import numpy as np
from PIL import Image, ImageDraw

out = Path(__file__).resolve().parents[2]/"research/fxnr-v2"
data = out/"fxnr-results"
profiles = ["source","legacy","gentle","v2","strong"]
def rgb(path):
    return np.asarray(Image.open(path).convert("RGB"), dtype=float)
def luma(im):
    return im @ [0.2126,0.7152,0.0722]
def write(name, rows):
    with (out/name).open("w",newline="") as f:
        w=csv.DictWriter(f,fieldnames=rows[0].keys())
        w.writeheader()
        w.writerows(rows)

rows = []
scenes = sorted(p for p in data.iterdir() if (p/"source.png").is_file())
for scene in scenes:
    src = rgb(scene/"source.png")
    sy = luma(src)
    for file in sorted(scene.rglob("*.png")):
        im = rgb(file)
        y = luma(im)
        shadow = (sy>=24)&(sy<=80)
        rows.append(dict(scene=scene.name,stage=file.relative_to(scene).with_suffix("").as_posix(),
            p05=round(np.percentile(y,5),2),median=round(np.median(y),2),p95=round(np.percentile(y,95),2),
            any_channel_255_pct=round(100*(im.max(2)==255).mean(),3),
            all_channels_0_pct=round(100*(im.max(2)==0).mean(),3),
            mean_absolute_rgb_change=round(np.abs(src-im).mean(),2),
            shadow_luma_sd=round(y[shadow].std(),2)))
write("measurements.csv",rows)

sheet=Image.new("RGB",(len(profiles)*173,24+len(scenes)*137),(20,20,20))
d=ImageDraw.Draw(sheet)
for i,name in enumerate(["Original","Legacy","Gentle 0.015","V2 0.035","Strong 0.065"]):
    d.text((i*173+4,5),name)
for j,scene in enumerate(scenes):
    for i,profile in enumerate(profiles):
        im=Image.open(scene/f"{profile}.png")
        im.thumbnail((169,114))
        x=i*173+(173-im.width)//2
        sheet.paste(im,(x,24+j*137))
    d.text((4,138+j*137),scene.name)
sheet.save(out/"contact-sheet.jpg",quality=94)

rows=[]
chart=data/"chart"
for profile in profiles:
    im=rgb(chart/("source.png" if profile=="source" else f"{profile}-flat-0.png"))
    y=luma(im)
    even=np.indices((80,96))
    mask=(((even[1]+16)//3+(even[0]+144)//4)%2)==1
    textile=y[144:224,16:112]
    edge=y[160,230:280]
    with_grain=luma(rgb(chart/f"{profile}.png"))
    rows.append(dict(profile=profile,black=round(y[32,0],2),white=round(y[32,-1],2),
        highlight_distinct_rgb=len(np.unique(im[32,400::2],axis=0)),
        highlight_distinct_luma=len(np.unique(np.rint(y[32,400::2]))),
        weave_contrast=round(textile[mask].mean()-textile[~mask].mean(),2),
        dark_patch_sd=round(with_grain[164:222,160:222].std(),2),
        dark_side_undershoot=round(max(0,edge[0]-min(edge[:26])),2),
        light_side_overshoot=round(max(0,max(edge[26:])-edge[-1]),2)))
write("chart.csv",rows)
# glReadPixels starts at the lower-left. Register the OES readback to the CPU chart.
preview_rows=[]
for facing, camera in [("false","0"),("true","1")]:
    cpu=rgb(chart/f"v2-flat-{camera}.png")
    gpu=rgb(chart/f"preview-{facing}.png")[::-1]
    for j in range(8):
        patch=np.s_[80:112,64*j+16:64*j+48,:]
        a,b=cpu[patch].mean((0,1)),gpu[patch].mean((0,1))
        preview_rows.append(dict(camera=camera,patch=j,cpu_rgb="/".join(str(round(v,1)) for v in a),
            gpu_rgb="/".join(str(round(v,1)) for v in b),mean_abs_rgb=round(np.abs(a-b).mean(),2)))
write("preview.csv",preview_rows)
