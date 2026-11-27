"""Measure decoded video, including explicit facial and background patch tracking."""
import json, hashlib, sys
from pathlib import Path
import cv2
import numpy as np

OUT=Path(__file__).resolve().parent
SOURCE=Path(sys.argv[1]) if len(sys.argv)>1 else None
NAMES=["video_2026-11-26_01-14-35","video_2026-11-26_01-14-41","video_2026-11-26_01-14-48","video_2026-11-27_13-06-40"]
# Panel coordinates from supplied 200x268 contact sheet; bridge/eye box, face bounds, background.
BOXES=[((48,100,97,143),(35,67,126,208),(135,15,191,141)),
       ((52,131,117,165),(36,87,141,224),(2,3,170,57)),
       ((3,131,65,164),(0,99,93,227),(82,16,170,96)),
       ((2,103,60,149),(0,63,99,190),(125,9,191,66))]
def load(path):
    cap=cv2.VideoCapture(str(path))
    fps=cap.get(cv2.CAP_PROP_FPS)
    frames=[]
    pts=[]
    while True:
        ok,im=cap.read()
        if not ok: break
        frames.append(im)
        pts.append(round(cap.get(cv2.CAP_PROP_POS_MSEC),2))
    cap.release()
    return frames,fps,pts
def roi(box,w,h):
    return tuple(round(v*k) for v,k in zip(box,(w/200,h/268,w/200,h/268)))
def motion(a,b,box):
    x,y,X,Y=box
    mask=np.zeros_like(a)
    mask[y:Y,x:X]=255
    points=cv2.goodFeaturesToTrack(a,120,.008,5,mask=mask)
    if points is None: return {"n":0}
    options=dict(winSize=(21,21),maxLevel=4)
    q,ok,_=cv2.calcOpticalFlowPyrLK(a,b,points,None,**options)
    p,back,_=cv2.calcOpticalFlowPyrLK(b,a,q,None,**options)
    good=(ok.ravel()>0)&(back.ravel()>0)&(np.linalg.norm((p-points)[:,0],axis=1)<.8)
    d=(q-points)[good,0]
    if len(d)==0:return {"n":0}
    center=np.median(d,axis=0)
    return dict(n=len(d),median_xy=center.round(3).tolist(),spread_iqr_xy=np.ptp(np.percentile(d,[25,75],axis=0),axis=0).round(3).tolist())
def analyze(frames,fps,pts,i):
    gray=[cv2.cvtColor(f,cv2.COLOR_BGR2GRAY) for f in frames]
    h,w=gray[0].shape
    boxes={k:roi(v,w,h) for k,v in zip(("bridge","face","background"),BOXES[i])}
    # Visually annotated eye centres, nose tip and mouth centre. Track their textured neighbourhoods.
    landmarks=[[(55,130),(99,128),(77,149),(77,169)],
               [(57,138),(105,139),(79,160),(80,181)],
               [(17,150),(66,147),(40,166),(45,192)],
               [(8,116),(61,110),(30,151),(47,161)]][i]
    points=np.float32([(x*w/200,y*h/268) for x,y in landmarks]).reshape(-1,1,2)
    q,status,_=cv2.calcOpticalFlowPyrLK(gray[0],gray[3],points,None,winSize=(27,27),maxLevel=4)
    rev,status2,_=cv2.calcOpticalFlowPyrLK(gray[3],gray[0],q,None,winSize=(27,27),maxLevel=4)
    passed=(status.ravel()>0)&(status2.ravel()>0)&(np.linalg.norm((points-rev)[:,0],axis=1)<.8)
    def change(a,b):
        if not (passed[a] and passed[b]): return None
        return round(float(np.linalg.norm(q[a]-q[b])-np.linalg.norm(points[a]-points[b])),3)
    track=dict(points_frame0=points[:,0].tolist(),accepted=passed.tolist(),xy_displacements=(q-points)[:,0].tolist(),
               interocular_change_px=change(0,1),nose_to_mouth_change_px=change(2,3))
    return dict(width=w,height=h,fps=fps,count=len(frames),landmarks=track,pts_ms=pts,duration_s=len(frames)/fps,rois=boxes,
        flow_0_to_3={k:motion(gray[0],gray[3],box) for k,box in boxes.items()},
        nearest_view=[int(np.argmin([cv2.norm(f,g,cv2.NORM_L1) for g in gray[:4]])) for f in gray])
if __name__=="__main__":
    runs=[]
    for i,name in enumerate(NAMES):
        path=SOURCE/(name+".mp4")
        f,fps,pts=load(path)
        dest=OUT/str(i+1)
        dest.mkdir(exist_ok=True)
        cv2.imwrite(str(dest/"input.jpg"),f[0],[cv2.IMWRITE_JPEG_QUALITY,98])
        raw=OUT.parents[2]/"app/build/d3d-audit"/str(i+1)
        raw.mkdir(parents=True,exist_ok=True)
        cv2.cvtColor(f[0],cv2.COLOR_BGR2RGBA)[:,:,[3,0,1,2]].copy().tofile(raw/"input.argb")
        row=analyze(f,fps,pts,i)
        row.update(file=path.name,sha256=hashlib.sha256(path.read_bytes()).hexdigest())
        runs.append(row)
        print(i+1,row["flow_0_to_3"])
    (OUT/"originals.json").write_text(json.dumps(runs,indent=2))
