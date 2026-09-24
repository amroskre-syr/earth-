#!/usr/bin/env python3
import argparse, cv2, numpy as np, random
from pathlib import Path

def main():
    p=argparse.ArgumentParser()
    p.add_argument("--frames",default="assets/earth_frames")
    p.add_argument("--background",default="assets/background.jpg")
    p.add_argument("--clouds",default="assets/clouds.png")
    p.add_argument("--output",default="output/earth_rotation.mp4")
    p.add_argument("--fps",type=int,default=30); p.add_argument("--seconds",type=int,default=12)
    a=p.parse_args()
    paths=sorted([x for x in Path(a.frames).glob("*") if x.suffix.lower() in {".png",".jpg",".jpeg",".webp"}])
    if not paths: raise SystemExit("Add original rotation frames to assets/earth_frames/")
    earth=[cv2.imread(str(x),cv2.IMREAD_UNCHANGED) for x in paths]
    bg=cv2.imread(a.background,cv2.IMREAD_COLOR)
    if bg is None: raise SystemExit("Add original background as assets/background.jpg")
    H,W=earth[0].shape[:2]; bg=cv2.resize(bg,(W,H))
    clouds=cv2.imread(a.clouds,cv2.IMREAD_UNCHANGED) if Path(a.clouds).exists() else None
    Path(a.output).parent.mkdir(parents=True,exist_ok=True)
    out=cv2.VideoWriter(a.output,cv2.VideoWriter_fourcc(*"mp4v"),a.fps,(W,H))
    n=a.fps*a.seconds; rng=random.Random(42)
    cloud_phase=0.0; cloud_speed=rng.uniform(-0.42,-0.18)
    for i in range(n):
        t=i/n
        # distant background moves in same direction, deliberately much slower
        shift=int((t*W*0.10)%W)
        canvas=np.roll(bg,shift,axis=1).copy()
        e=earth[int(t*len(earth))%len(earth)]
        if e.shape[:2]!=(H,W): e=cv2.resize(e,(W,H))
        if e.shape[2] if e.ndim==3 else 1 == 4:
            alpha=e[:,:,3:4]/255.; canvas=(e[:,:,:3]*alpha+canvas*(1-alpha)).astype(np.uint8)
        else: canvas=e[:,:,:3].copy()
        # Optional original transparent cloud layer: counter-rotation with gently varying speed.
        if clouds is not None and clouds.ndim==3 and clouds.shape[2]==4:
            if i%(a.fps*2)==0: cloud_speed=rng.uniform(-0.48,-0.16)
            cloud_phase=(cloud_phase+cloud_speed)%W
            c=cv2.resize(clouds,(W,H)); c=np.roll(c,int(cloud_phase),axis=1)
            al=c[:,:,3:4]/255.; canvas=(c[:,:,:3]*al+canvas*(1-al)).astype(np.uint8)
        out.write(canvas)
    out.release()
    print(a.output)
if __name__=="__main__": main()
