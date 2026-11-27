"""Analyze full-size viewpoint tracks and 400px dense flow. Run after HybridReplayTest."""
from pathlib import Path
import json
import sys
import cv2
import numpy as np
from PIL import Image

OUT = Path(__file__).resolve().parent
ROOT = OUT.parents[2]
sys.path.insert(0, str(OUT.parent/"d3d"))
from measure import analyze, load, NAMES
cycle = [0,1,2,3,2,1]
order = ["A","B","C","D","E","F","x1.25","x1.75","x2.0"]

def dense(frames):
    h,w = frames[0].shape[:2]
    gray = [cv2.resize(cv2.cvtColor(f,cv2.COLOR_BGR2GRAY),(400,round(h*400/w)),interpolation=cv2.INTER_AREA) for f in frames]
    series = []
    for i,a in enumerate(gray):
        b = gray[(i+1)%len(gray)]
        flow = cv2.calcOpticalFlowFarneback(a,b,None,.5,3,15,3,5,1.2,0)
        series.append([round(float(v),4) for v in (np.linalg.norm(flow,axis=2).mean(),abs(flow[:,:,1]).mean())])
    return series

def writevideo(name, frames, fps):
    h,w=frames[0].shape[:2]
    video=cv2.VideoWriter(str(OUT/name),cv2.VideoWriter_fourcc(*"mp4v"),fps,(w,h))
    assert video.isOpened()
    for f in frames: video.write(f)
    video.release()
    read,rate,_=load(OUT/name)
    assert len(read)==len(frames) and abs(rate-fps)<.02

def main(whatsapp, telegram):
    frames,fps,pts=load(whatsapp)
    flow=dense(frames)
    results={"whatsapp":{"width":frames[0].shape[1],"height":frames[0].shape[0],"fps":fps,"count":len(frames),"pts_ms":pts,"adjacent_magnitude_and_abs_vertical_at_400":flow,"cycle_ids":[int(np.argmin([cv2.norm(f,g,cv2.NORM_L1) for g in frames[:4]])) for f in frames]},"scenes":[]}
    comparison=[]
    for i,name in enumerate(NAMES):
        original,rate,pts=load(telegram/(name+".mp4"))
        scene={"source":name,"original":analyze(original,rate,pts,i),"candidates":{}}
        panels={}
        for key in order:
            views=[np.fromfile(ROOT/f"app/build/d3d-hybrid/{i+1}/{key}-{n}.argb",np.uint8).reshape(1072,800,4)[:,:,[3,2,1]].copy() for n in range(4)]
            record=analyze(views,10,[0,100,200,300],i)
            seq=[views[n] for n in cycle]
            record["adjacent_magnitude_and_abs_vertical_at_400"]=dense(seq)
            scene["candidates"][key]=record
            if key in ("B","F"): panels[key]=seq
        sheets=[]
        for n in range(6):
            row=np.zeros((352,720,3),np.uint8)
            for k,(im,label) in enumerate([(original[n],"Original clip"),(panels["B"][n],"Clean replay"),(panels["F"][n],"Hybrid replay")]):
                row[30:,k*240:(k+1)*240]=cv2.resize(im,(240,322),interpolation=cv2.INTER_AREA)
                cv2.putText(row,f"{i+1}  {label}",(k*240+6,21),cv2.FONT_HERSHEY_SIMPLEX,.53,(248,248,248),1,cv2.LINE_AA)
            sheets.append(row)
        comparison+=sheets*4
        if i==0:
            for ms in (100,125,150):
                writevideo(f"timing-{ms}ms.mp4",panels["F"]*4,1000/ms)
            gif=[Image.fromarray(cv2.cvtColor(f,cv2.COLOR_BGR2RGB)) for f in sheets]
            gif[0].save(OUT/"comparison.gif",save_all=True,append_images=gif[1:],duration=100,loop=0)
        results["scenes"].append(scene)
    writevideo("comparison.mp4",comparison,10)
    (OUT/"measurements.json").write_text(json.dumps(results,indent=2))

if __name__=="__main__":
    main(Path(sys.argv[1]),Path(sys.argv[2]))
