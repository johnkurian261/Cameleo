"""Encode the four production-geometry replays and compare against the original videos."""
import csv
import json
import subprocess
import sys
from pathlib import Path
import cv2
import imageio_ffmpeg
import numpy as np
from PIL import Image
from measure import OUT, NAMES, BOXES, load, analyze, roi

root=OUT.parents[2]
source=Path(sys.argv[1])
runs=[]
reel=None
for i,name in enumerate(NAMES):
    dest=OUT/str(i+1)
    build=root/"app/build/d3d-audit"/str(i+1)
    original,fps,_=load(source/(name+".mp4"))
    views=[np.fromfile(build/f"view{k}.argb",dtype="uint8").reshape(1072,800,4)[:,:,[3,2,1]].copy() for k in range(4)]
    cv2.imwrite(str(dest/"depth.png"),cv2.imread(str(build/"depth.pgm"),cv2.IMREAD_GRAYSCALE))
    sequence=[views[k] for k in [0,1,2,3,2,1]*4]
    h,w=sequence[0].shape[:2]
    encoder=subprocess.Popen([imageio_ffmpeg.get_ffmpeg_exe(),"-loglevel","error","-y","-f","rawvideo","-pix_fmt","bgr24","-s",f"{w}x{h}","-r",str(fps),"-i","-","-c:v","libx264","-crf","17","-pix_fmt","yuv420p",str(dest/"corrected.mp4")],stdin=subprocess.PIPE)
    for frame in sequence: encoder.stdin.write(frame.tobytes())
    encoder.stdin.close()
    if encoder.wait(): raise RuntimeError("encode failed")
    frames,fps,pts=load(dest/"corrected.mp4")
    runs.append(analyze(frames,fps,pts,i))
    if reel is None:
        reel=subprocess.Popen([imageio_ffmpeg.get_ffmpeg_exe(),"-loglevel","error","-y","-f","rawvideo","-pix_fmt","bgr24","-s","800x568","-r","10","-i","-","-c:v","libx264","-crf","19","-pix_fmt","yuv420p",str(OUT/"comparison.mp4")],stdin=subprocess.PIPE)
    for before,after in zip(original,frames):
        sheet=np.zeros((568,800,3),dtype=np.uint8)
        sheet[32:]=cv2.resize(np.hstack([before,after]),(800,536))
        for x,label in [(8,f"{i+1}  Original capture"),(408,"Geometry replay")]:
            cv2.putText(sheet,label,(x,23),cv2.FONT_HERSHEY_SIMPLEX,.58,(235,235,235),1,cv2.LINE_AA)
        reel.stdin.write(sheet.tobytes())
    panels=[]
    for j in range(6):
        panels.append(Image.fromarray(cv2.cvtColor(cv2.resize(np.hstack([original[j],frames[j]]),(480,322)),cv2.COLOR_BGR2RGB)))
    panels[0].save(dest/"comparison.gif",save_all=True,append_images=panels[1:],duration=100,loop=0)
    frame=original[0].copy()
    x,y,X,Y=roi(BOXES[i][1],w,h)
    cv2.rectangle(frame,(x,y),(X,Y),(0,230,255),3)
    cx=int((x+X)/2)
    cy=int(y+(Y-y)*.46)
    rx=int((X-x)*.18)
    ry=int((Y-y)*.12)
    cv2.rectangle(frame,(cx-rx,cy-ry),(cx+rx,cy+ry),(40,255,60),3)
    cv2.drawMarker(frame,(cx,cy),(40,255,60))
    depth=cv2.resize(cv2.imread(str(dest/"depth.png")),(w,h))
    cv2.rectangle(depth,(cx-rx,cy-ry),(cx+rx,cy+ry),(40,255,60),3)
    cv2.imwrite(str(dest/"anchor.jpg"),cv2.resize(np.hstack([frame,depth]),(480,322)))
    print(i+1,runs[-1]["flow_0_to_3"])
reel.stdin.close()
if reel.wait(): raise RuntimeError("comparison encode failed")
(OUT/"replays.json").write_text(json.dumps(runs,indent=2))
