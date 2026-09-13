"""Image-only feature matching baseline; not an Android-ready model.
References are independent game icons. Target crops exclude name/CP/stats.
No screenshot used as a reference except Glimmet, separately reported.
"""
from pathlib import Path
import cv2,numpy as np,json
ROOT=Path(__file__).resolve().parent
OUT=Path(__file__).resolve().parent
sift=cv2.SIFT_create(nfeatures=2000,contrastThreshold=0.015)
bf=cv2.BFMatcher(cv2.NORM_L2)
def features(im,mask=None):return sift.detectAndCompute(cv2.cvtColor(im,cv2.COLOR_BGR2GRAY),mask)
refs=[]
for p in sorted((ROOT/'references').glob('*.png')):
 im=cv2.imread(str(p),cv2.IMREAD_UNCHANGED)
 rgb=im[:,:,:3];mask=im[:,:,3] if im.shape[2]==4 else None
 rgb=cv2.resize(rgb,None,fx=2,fy=2);mask=cv2.resize(mask,None,fx=2,fy=2) if mask is not None else None
 kp,d=features(rgb,mask);refs.append((p.stem,kp,d))

def rank(im):
 kp,desc=features(im);ranking=[]
 for name,rk,rd in refs:
  good=[];inliers=0;spread=0
  if rd is not None and desc is not None and len(desc)>1:
   pairs=bf.knnMatch(rd,desc,k=2)
   good=[a for pair in pairs if len(pair)==2 for a,b in [pair] if a.distance<0.70*b.distance]
   # Repeated matches to one target feature are not independent evidence.
   unique={}
   for m in sorted(good,key=lambda m:m.distance):unique.setdefault(m.trainIdx,m)
   good=list(unique.values())
   if len(good)>=4:
    src=np.float32([rk[m.queryIdx].pt for m in good]);dst=np.float32([kp[m.trainIdx].pt for m in good])
    H,mask=cv2.findHomography(src,dst,cv2.RANSAC,4.0)
    if H is not None and mask is not None:
     chosen=dst[mask.ravel().astype(bool)];inliers=len(chosen)
     if len(chosen)>=3:spread=float(cv2.contourArea(cv2.convexHull(chosen))/(im.shape[0]*im.shape[1]))
  ranking.append({'reference':name,'matches':len(good),'inliers':inliers,'coverage':round(spread,4)})
 return sorted(ranking,key=lambda x:(x['inliers'],x['matches']),reverse=True)

cases=[
 ('cinderace','04-1000000532.png',(0.08,.10,.88,.345),'cinderace'),
 ('shiny-metagross','03-1000000533.png',(.08,.11,.88,.345),'metagross-shiny'),
 ('decorated-lapras','02-1000000534.png',(.08,.10,.88,.345),'lapras-mystic'),
 ('armored-mewtwo','01-1000000541.png',(.08,.10,.88,.345),'armored-mewtwo'),
 ('mewtwo','02-1000000542.png',(.08,.10,.88,.345),'mewtwo'),
 ('glimmet-unseen','03-1000000543.png',(.08,.10,.88,.31),None),
 ('map-unrelated','05-1000000531.png',(.02,.40,.98,.79),None),
 ('bag-unrelated','01-1000000535.png',(.02,.13,.98,.44),None),
]
results=[]
for label,file,box,expected in cases:
 crop=cv2.imread(str(ROOT/'inputs'/(label+'-input.png')))
 ranking=rank(crop);top=ranking[0];runner=ranking[1]
 # Fixed gates chosen before viewing results. Unknown is a first-class outcome.
 accepted=top['inliers']>=10 and top['coverage']>=.015 and top['inliers']>=runner['inliers']+4
 prediction=top['reference'] if accepted else None
 results.append({'case':label,'expected':expected,'prediction':prediction,'correct':prediction==expected,'ranking':ranking})

 print(label,'=>',prediction,'top',top)
(OUT/'results.json').write_text(json.dumps(results,indent=2))
