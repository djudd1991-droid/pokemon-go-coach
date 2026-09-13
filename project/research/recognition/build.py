import json,struct,pathlib,cv2,numpy as np,shutil
root=pathlib.Path(__file__).parent;assets=root.parents[1]/'app/src/main/assets';sift=cv2.SIFT_create(nfeatures=350,contrastThreshold=.015);cv2.setNumThreads(1)
refs=[]
# Preserve prior verified references at their original descriptor density.
for r in json.load(open(assets/'visual-references.json')):refs.append((r['species'],np.array(r['points'],np.float32),np.array(r['descriptors'],np.float32),True))
manifest=json.load(open(root/'manifest.json'))
for e in manifest:
 if 'error' in e:continue
 im=cv2.imread(str(root/'icons'/e['file']),cv2.IMREAD_UNCHANGED)
 if im is None:continue
 rgb=im[:,:,:3];mask=im[:,:,3] if im.shape[2]==4 else None
 # Composite transparent sprites before extraction so invisible RGB pixels
 # cannot dominate descriptors along the silhouette.
 if mask is not None:
  alpha=mask[:,:,None]/255.0;rgb=(rgb*alpha+190*(1-alpha)).astype(np.uint8)
 rgb=cv2.resize(rgb,None,fx=2,fy=2);mask=cv2.resize(mask,None,fx=2,fy=2) if mask is not None else None
 kp,d=sift.detectAndCompute(cv2.cvtColor(rgb,cv2.COLOR_BGR2GRAY),mask)
 if d is None or len(d)<10:continue
 refs.append((e['name'],np.array([k.pt for k in kp],np.float32),d,False))
 name=e['name'].lower().replace(' ','-')+'.png';shutil.copyfile(root/'icons'/e['file'],assets/'portraits'/name)
with open(assets/'visual-atlas.bin','wb') as out:
 out.write(struct.pack('>ii',1,len(refs)))
 for name,pts,d,priority in refs:
  b=name.encode();out.write(struct.pack('>i',len(b)));out.write(b);out.write(struct.pack('>ii',int(priority),len(pts)));out.write(pts.astype('>f4').tobytes());out.write(d.astype(np.uint8).tobytes())
np.savez_compressed(root/'atlas.npz',**{f'p{i}':r[1] for i,r in enumerate(refs)},**{f'd{i}':r[2] for i,r in enumerate(refs)})
(root/'names.json').write_text(json.dumps([r[0] for r in refs]));print('Atlas',len(refs),'references',len({r[0] for r in refs}),'species; descriptors',sum(len(r[1]) for r in refs),flush=True)
