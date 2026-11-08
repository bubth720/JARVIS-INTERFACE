"""Private reference inventory only. No master approval, renderer export, copying or network access."""
import argparse,hashlib,json,os,re,stat,struct,zlib
from pathlib import Path,PurePosixPath
MAX_FILE=32*1024*1024
MAX_TOTAL=256*1024*1024
class Refusal(ValueError):pass
def png_metadata(data):
    if not data.startswith(b'\x89PNG\r\n\x1a\n'):raise Refusal('UNSUPPORTED_HEADER')
    pos=8;dims=None;ended=False;idat=False
    while pos<len(data):
        if len(data)-pos<12:raise Refusal('PNG_TRUNCATED')
        n=struct.unpack('>I',data[pos:pos+4])[0];kind=data[pos+4:pos+8];end=pos+12+n
        if end>len(data) or n>MAX_FILE:raise Refusal('PNG_CHUNK_BOUNDS')
        content=data[pos+8:pos+8+n];crc=struct.unpack('>I',data[pos+8+n:end])[0]
        if zlib.crc32(kind+content)&0xffffffff != crc:raise Refusal('PNG_CRC')
        if dims is None:
            if kind!=b'IHDR' or n!=13:raise Refusal('PNG_IHDR')
            w,h=struct.unpack('>II',content[:8])
            if not (1<=w<=4096 and 1<=h<=4096 and w*h*4<=64*1024*1024):raise Refusal('PIXEL_ENVELOPE')
            dims=[w,h]
        elif kind==b'IHDR':raise Refusal('PNG_DUPLICATE_IHDR')
        if kind==b'IDAT':idat=True
        if kind==b'IEND':
            if n!=0 or end!=len(data):raise Refusal('PNG_TRAILING_OR_IEND')
            ended=True;break
        pos=end
    if not ended or not idat:raise Refusal('PNG_INCOMPLETE')
    return {'format':'PNG','dimensions':dims,'decode_qualification':'NOT_RUN'}
def mp4_metadata(data):
    pos=0;kinds=[]
    while pos<len(data):
        if len(data)-pos<8:raise Refusal('MP4_TRUNCATED')
        n=struct.unpack('>I',data[pos:pos+4])[0];kind=data[pos+4:pos+8];header=8
        if n==1:
            if len(data)-pos<16:raise Refusal('MP4_TRUNCATED')
            n=struct.unpack('>Q',data[pos+8:pos+16])[0];header=16
        if n==0:n=len(data)-pos
        if n<header or n>len(data)-pos:raise Refusal('MP4_BOX_BOUNDS')
        kinds.append(kind);pos+=n
        if len(kinds)>4096:raise Refusal('MP4_BOX_LIMIT')
    if not kinds or kinds[0]!=b'ftyp' or b'moov' not in kinds or b'mdat' not in kinds:raise Refusal('MP4_INCOMPLETE')
    return {'format':'MP4','decode_qualification':'NOT_RUN','audio_extracted':False}
def read_one(root,entry,budget):
    remaining=MAX_TOTAL-budget[0]
    rel=entry.get('file')
    if not isinstance(rel,str) or '\\' in rel:raise Refusal('PRIVATE_RELATIVE_PATH_REQUIRED')
    p=PurePosixPath(rel)
    if p.is_absolute() or not p.parts or any(x in ('..','.') for x in p.parts):raise Refusal('PRIVATE_RELATIVE_PATH_REQUIRED')
    path=root.joinpath(*p.parts)
    if any(q.is_symlink() for q in [path,*path.parents] if q!=root.parent):raise Refusal('SYMLINK_REFUSED')
    if not path.is_relative_to(root):raise Refusal('ROOT_ESCAPE')
    before=path.lstat()
    if not stat.S_ISREG(before.st_mode) or before.st_nlink!=1:raise Refusal('REGULAR_UNALIASED_FILE_REQUIRED')
    if before.st_size<=0 or before.st_size>MAX_FILE or before.st_size>remaining:raise Refusal('BYTE_ENVELOPE')
    fd=os.open(path,os.O_RDONLY|os.O_NOFOLLOW)
    with os.fdopen(fd,'rb') as f:
        opened=os.fstat(f.fileno())
        if (opened.st_ino,opened.st_dev)!=(before.st_ino,before.st_dev):raise Refusal('FILE_CHANGED')
        data=f.read(min(MAX_FILE,remaining)+1)
        budget[0]+=len(data)
    after=path.lstat()
    if len(data)!=before.st_size or (after.st_ino,after.st_dev,after.st_size,after.st_mtime_ns)!=(before.st_ino,before.st_dev,before.st_size,before.st_mtime_ns):raise Refusal('FILE_CHANGED')
    observed=hashlib.sha256(data).hexdigest()
    if not re.fullmatch('[0-9a-f]{64}',str(entry.get('sha256',''))) or entry['sha256']!=observed:raise Refusal('SHA256_MISMATCH')
    kind=entry.get('kind')
    if kind=='IMAGE' and path.suffix.lower()=='.png':meta=png_metadata(data)
    elif kind=='VIDEO' and path.suffix.lower()=='.mp4':meta=mp4_metadata(data)
    else:raise Refusal('UNSUPPORTED_FORMAT')
    return observed,len(data),meta
def inventory(root,manifest):
    if any(p.is_symlink() for p in [root.absolute(),*root.absolute().parents]):raise Refusal('PRIVATE_ROOT_REQUIRED')
    root=root.resolve(strict=True)
    if not root.is_dir() or stat.S_IMODE(root.stat().st_mode)&0o077:raise Refusal('PRIVATE_ROOT_REQUIRED')
    if manifest.get('schema')!='jarvis.avatar.assets/1' or manifest.get('visibility')!='PRIVATE_ONLY':raise Refusal('PRIVATE_MANIFEST_REQUIRED')
    entries=manifest.get('inputs')
    if not isinstance(entries,list) or len(entries)>300:raise Refusal('ENTRY_LIMIT')
    rows=[];seen=set();ids=set();budget=[0]
    for index,entry in enumerate(entries):
        row={'source_index':index,'status':'QUARANTINED','admitted_for_runtime':False}
        try:
            if not isinstance(entry,dict):raise Refusal('INVALID_ENTRY')
            identity=entry.get('id','')
            if not isinstance(identity,str) or not re.fullmatch('[A-Z0-9_-]{1,48}',identity) or identity in ids:raise Refusal('DUPLICATE_OR_INVALID_ID')
            ids.add(identity)
            if entry.get('rights') not in ('REFERENCE_ONLY','DERIVATIVE_PRIVATE_DECLARED','DISTRIBUTION_DECLARED'):raise Refusal('RIGHTS_NOT_DECLARED')
            if not isinstance(entry.get('rights_evidence_ref'),str) or not re.fullmatch('[A-Z0-9_-]{1,64}',entry['rights_evidence_ref']):raise Refusal('RIGHTS_EVIDENCE_MISSING')
            observed,size,meta=read_one(root,entry,budget)
            row.update(sha256=observed,size_bytes=size,metadata=meta,status='DUPLICATE' if observed in seen else 'CANDIDATE_FOR_HUMAN_REVIEW',rights='DECLARED_NOT_VERIFIED')
            seen.add(observed)
        except (Refusal,OSError,ValueError,TypeError) as e:
            row['reason']=str(e) if isinstance(e,Refusal) else 'FILE_OR_METADATA_REFUSED'
        rows.append(row)
    return {'schema':'jarvis.avatar.inventory/1','visibility':'PRIVATE_ONLY','rows':rows,'bytes_read':budget[0],
            'candidate_count':sum(r['status']=='CANDIDATE_FOR_HUMAN_REVIEW' for r in rows),
            'duplicate_count':sum(r['status']=='DUPLICATE' for r in rows),'quarantined_count':sum(r['status']=='QUARANTINED' for r in rows),
            'master_approval':'PENDING_HUMAN','runtime_admitted':False,'android_exports':'NOT_BUILT',
            'files_copied':False,'audio_extracted':False,'network_used':False}
def main():
    p=argparse.ArgumentParser();p.add_argument('--root',type=Path,required=True);p.add_argument('--manifest',type=Path,required=True);p.add_argument('--output',type=Path,required=True);a=p.parse_args()
    if a.manifest.stat().st_size>65536:raise Refusal('MANIFEST_BYTE_LIMIT')
    report=inventory(a.root,json.loads(a.manifest.read_text()))
    repo=Path(__file__).resolve().parents[1];out=a.output.resolve()
    if out.is_relative_to(repo) or out.is_relative_to(a.root.resolve()):raise Refusal('PRIVATE_OUTPUT_OUTSIDE_CODE_AND_INPUTS_REQUIRED')
    if stat.S_IMODE(out.parent.stat().st_mode)&0o077:raise Refusal('PRIVATE_OUTPUT_DIRECTORY_REQUIRED')
    fd=os.open(out,os.O_WRONLY|os.O_CREAT|os.O_EXCL,0o600)
    with os.fdopen(fd,'w') as f:json.dump(report,f,indent=2);f.write('\n')
    print(json.dumps({'status':'PRIVATE_INVENTORY_WRITTEN','entries':len(report['rows']),'runtime_admitted':False}))
if __name__=='__main__':main()
