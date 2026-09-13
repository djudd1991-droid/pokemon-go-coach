import cv2,numpy as np,json,pathlib,time
cv2.setNumThreads(1);root=pathlib.Path(__file__).parent;a=np.load(root/'atlas.npz');names=json.load(open(root/'names.json'));refs=[(a[f'p{i}'],a[f'd{i}']) for i in range(len(names))]
matcher=cv2.FlannBasedMatcher(dict(algorithm=1,trees=4),dict(checks=32));matcher.add([r[1][::4].copy() for r in refs]);matcher.train();bf=cv2.BFMatcher(cv2.NORM_L2);sift=cv2.SIFT_create(nfeatures=2000,contrastThreshold=.015)
def identify(im):
 kp,d=sift.detectAndCompute(cv2.cvtColor(im,cv2.COLOR_BGR2GRAY),None)
 if d is None or len(d)<2:return '',[]
 votes={}
 for pair in matcher.knnMatch(d,k=2):
  if len(pair)==2 and pair[0].distance < .8*pair[1].distance:
   m=pair[0];votes[m.imgIdx]=votes.get(m.imgIdx,0)+1
 candidates=set(sorted(votes,key=votes.get,reverse=True)[:16])|set(range(7));scores={};target=np.float32([k.pt for k in kp])
 for i in candidates:
  rp,rd=refs[i];unique={}
  for pair in bf.knnMatch(rd,d,k=2):
   if len(pair)==2 and pair[0].distance<.70*pair[1].distance:
    m=pair[0]
    if m.trainIdx not in unique or m.distance<unique[m.trainIdx].distance:unique[m.trainIdx]=m
  ms=list(unique.values())
  if len(ms)<4:continue
  src=np.float32([rp[m.queryIdx] for m in ms]);dst=np.float32([target[m.trainIdx] for m in ms]);H,mask=cv2.findHomography(src,dst,cv2.RANSAC,4)
  if H is None:continue
  pts=dst[mask.ravel().astype(bool)];n=len(pts);area=cv2.contourArea(cv2.convexHull(pts))/(im.shape[0]*im.shape[1]) if n>=3 else 0
  if n>scores.get(names[i],(0,0))[0]:scores[names[i]]=(n,area)
 rank=sorted(scores,key=lambda n:scores[n][0],reverse=True)
 if not rank:return '',[]
 b=rank[0];hi,area=scores[b];second=scores[rank[1]][0] if len(rank)>1 else 0
 return b if hi>=10 and hi>=second+4 and area>=.015 else '',[(n,scores[n]) for n in rank[:3]]
results=[]
for p in root.parent/'inputs'.glob('*.png'):
 t=time.time();pred,ranks=identify(cv2.imread(str(p)));results.append({'image':p.name,'prediction':pred,'top':ranks,'seconds':round(time.time()-t,2)});print(results[-1],flush=True)
(root/'results.json').write_text(json.dumps(results,indent=2))
