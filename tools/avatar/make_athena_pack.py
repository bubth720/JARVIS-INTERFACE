#!/usr/bin/env python3
"""Build a deterministic PRIVATE V2 full-pose review pack; never publish or activate it."""
import argparse
import hashlib
import json
from pathlib import Path
import struct
import zipfile
import zlib

ROOT = Path(__file__).resolve().parents[2]
PIXELS = 2_097_152
MAX_BYTES = 8 * 1024 * 1024
STATES = {'IDLE','LISTENING','THINKING','SPEAKING','WAITING','WORKING','REACTION','ERROR'}
EXPRESSIONS = {'NEUTRAL','SMILE','ATTENTIVE','THOUGHTFUL','SURPRISE','LAUGH','CONCERNED','WAITING','FOCUSED','WINK','TALKING'}
POSES = {'FACE','THREE_QUARTER_LEFT','THREE_QUARTER_RIGHT','LEFT','RIGHT','BACK'}

def digest(data): return hashlib.sha256(data).hexdigest()

def inspect_png(data):
    if len(data)>2*1024*1024 or data[:8]!=b'\x89PNG\r\n\x1a\n': raise ValueError('PNG_SIZE_OR_SIGNATURE')
    at=8; compressed=[]; width=height=0; ended=False
    while at<len(data):
        if len(data)-at<12: raise ValueError('PNG_TRUNCATED')
        size=struct.unpack_from('>I',data,at)[0]
        if size>len(data)-at-12: raise ValueError('PNG_CHUNK_SIZE')
        name=data[at+4:at+8]; value=data[at+8:at+8+size]
        if zlib.crc32(name+value)&0xffffffff!=struct.unpack_from('>I',data,at+8+size)[0]: raise ValueError('PNG_CRC')
        if at==8:
            if name!=b'IHDR' or size!=13: raise ValueError('PNG_HEADER')
            width,height,depth,color,compression,filtering,interlace=struct.unpack('>IIBBBBB',value)
            if depth!=8 or color!=6 or compression or filtering or interlace: raise ValueError('RGBA8_NONINTERLACED_REQUIRED')
            if not (1<=width<=2048 and 1<=height<=2048 and width*height<=PIXELS): raise ValueError('PNG_PIXEL_BUDGET')
        elif name==b'IHDR': raise ValueError('DUPLICATE_HEADER')
        if name==b'acTL': raise ValueError('APNG_REFUSED')
        if name==b'IDAT': compressed.append(value)
        at+=size+12
        if name==b'IEND':
            if size or at!=len(data): raise ValueError('PNG_TRAILING_DATA')
            ended=True; break
    if not ended or not compressed: raise ValueError('PNG_INCOMPLETE')
    expected=(width*4+1)*height
    dec=zlib.decompressobj(); raw=dec.decompress(b''.join(compressed),expected+1)
    if len(raw)!=expected or not dec.eof or dec.unused_data or dec.unconsumed_tail: raise ValueError('PNG_DECODE_SIZE')
    previous=bytearray(width*4); transparent=visible=False
    for y in range(height):
        start=y*(width*4+1); kind=raw[start]; row=bytearray(raw[start+1:start+1+width*4])
        if kind>4: raise ValueError('PNG_FILTER')
        for i in range(len(row)):
            a=row[i-4] if i>=4 else 0; b=previous[i]; c=previous[i-4] if i>=4 else 0
            if kind==1: row[i]=(row[i]+a)&255
            elif kind==2: row[i]=(row[i]+b)&255
            elif kind==3: row[i]=(row[i]+(a+b)//2)&255
            elif kind==4:
                p=a+b-c; pa,pb,pc=abs(p-a),abs(p-b),abs(p-c)
                row[i]=(row[i]+(a if pa<=pb and pa<=pc else b if pb<=pc else c))&255
        transparent |= any(v<255 for v in row[3::4]); visible |= any(v>0 for v in row[3::4]); previous=row
    if not transparent or not visible: raise ValueError('VISIBLE_TRANSPARENT_RGBA_REQUIRED')
    return width,height

def private_output(path):
    path=Path(path).resolve()
    if path.is_relative_to(ROOT) and not path.is_relative_to(ROOT/'artifacts/athena-private'):
        raise ValueError('PRIVATE_OUTPUT_ONLY: choose a directory outside Git or artifacts/athena-private')
    return path

def plan_dimensions(count,reserved_pixels=40_000):
    if not 1<=count<=16 or not 0<=reserved_pixels<PIXELS: raise ValueError('PLAN_COUNT_OR_RESERVE')
    # 3:4 logical portraits, reserve the actual AVA-03 technical preview cache.
    width=min(768,int(((PIXELS-reserved_pixels)/(count*4/3))**0.5))
    width-=width%3
    return width,width*4//3

def build(spec_path,output):
    spec_path=Path(spec_path).resolve(); spec=json.loads(spec_path.read_text()); output=private_output(output)
    records=spec['frames']
    if not 1<=len(records)<=16: raise ValueError('FRAME_BUDGET')
    files={};resources=[];layers=[];frames=[];keys=set();pixels=0
    for i,r in enumerate(records):
        key=(r['state'],r['expression'],r['perspective'])
        if key in keys or key[0] not in STATES or key[1] not in EXPRESSIONS or key[2] not in POSES: raise ValueError('FRAME_KEY')
        keys.add(key)
        source=(spec_path.parent/r['file']).resolve()
        if not source.is_relative_to(spec_path.parent): raise ValueError('SOURCE_PATH')
        data=source.read_bytes();w,h=inspect_png(data);pixels+=w*h
        name=f'portrait-{i:02d}.png'; ident=f'portrait-{i:02d}'
        files[name]=data
        resources.append(dict(id=ident,file=name,sha256=digest(data),width=w,height=h))
        layers.append(dict(id=ident,resource=ident,order=i,x=0,y=0,width=240,height=320,pivotX=120,pivotY=188,depth=0,channel='STATIC'))
        frames.append(dict(state=key[0],expression=key[1],perspective=key[2],layers=[ident],mode='IMAGE_REPLACEMENT'))
    if ('IDLE','NEUTRAL','FACE') not in keys: raise ValueError('NEUTRAL_REQUIRED')
    if pixels+spec.get('reservedPixels',40_000)>PIXELS: raise ValueError('GLOBAL_PIXEL_BUDGET')
    manifest=dict(version=2,id=spec['id'],author=spec['author'],license='PRIVATE ART PROPOSAL. No publication or permanent activation approved.',artGrade='PREMIUM_CANDIDATE',width=240,height=320,resources=resources,layers=layers,frames=frames)
    data=(json.dumps(manifest,ensure_ascii=False,indent=2)+'\n').encode();files['manifest.json']=data
    if len(data)>65536 or sum(map(len,files.values()))>MAX_BYTES: raise ValueError('BUNDLE_BUDGET')
    output.mkdir(parents=True,exist_ok=True)
    with zipfile.ZipFile(output/'athena-review.zip','w',compression=zipfile.ZIP_DEFLATED) as z:
        for name,content in sorted(files.items()):
            entry=zipfile.ZipInfo(name,(1980,1,1,0,0,0));entry.compress_type=zipfile.ZIP_DEFLATED;z.writestr(entry,content)
    (output/'manifest.json').write_bytes(data)
    receipt=dict(status='ART_REVIEW_PENDING',manifestSha256=digest(data),zipSha256=digest((output/'athena-review.zip').read_bytes()),pixels=pixels,reservedPixels=spec.get('reservedPixels',40_000),rgbaBytes=pixels*4,resources=len(resources),renderMode='IMAGE_REPLACEMENT',rigReady=False,aestheticApproved=False,publicationApproved=False,permanentActivationApproved=False)
    (output/'review-request.json').write_text(json.dumps(receipt,indent=2)+'\n')
    return receipt

if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('spec');p.add_argument('--output',required=True)
    args=p.parse_args();print(json.dumps(build(args.spec,args.output),indent=2))
