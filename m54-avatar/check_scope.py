"""Freeze the full UI-01C base; this review never enters app source sets or APKs."""
import hashlib,json,subprocess
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
BASE='0dfeef8ff09d60a68d18398ccb6b2530f3b87df4'
def git(*args):return subprocess.check_output(['git',*args],cwd=ROOT)
def main():
 frozen={}
 for row in git('ls-tree','-r',BASE).decode().splitlines():
  meta,name=row.split('\t',1);mode,kind,blob=meta.split();assert kind=='blob';frozen[name]=(mode,git('cat-file','--filters',BASE+':'+name))
 actual=set(git('ls-files','--cached','--others','--exclude-standard').decode().splitlines());assert set(frozen)<=actual
 modes={l.split('\t',1)[1]:l.split()[0] for l in git('ls-files','--stage').decode().splitlines()}
 assert all(n.startswith('m54-avatar/') or n=='.github/workflows/m54-avatar-review.yml' for n in actual-set(frozen))
 for n in actual:
  p=ROOT/n;assert p.is_file() and not p.is_symlink();assert modes.get(n)==(frozen[n][0] if n in frozen else '100644'),n
  if n in frozen:assert p.read_bytes()==frozen[n][1],n
  else:
   assert p.suffix.lower() not in ('.png','.jpg','.jpeg','.webp','.mp4','.wav','.ogg','.onnx','.gguf','.jar','.pyc','.so','.apk'),n
   p.read_bytes().decode('utf-8')
 out=ROOT/'build/m54-avatar/reports';out.mkdir(parents=True,exist_ok=True)
 result={'base':BASE,'commit':git('rev-parse','HEAD').decode().strip(),'tree':git('rev-parse','HEAD^{tree}').decode().strip(),'worktree_dirty':bool(git('status','--porcelain')),'frozen_files':len(frozen),'historical_contract_sha256':hashlib.sha256((ROOT/'app/src/main/java/com/jarvis/interfaceapp/avatar/AvatarContract.kt').read_bytes()).hexdigest(),'app_modified':False,'runtime_connected':False,'private_assets_published':False}
 (out/'preservation.json').write_text(json.dumps(result,indent=2)+'\n');print(json.dumps(result))
if __name__=='__main__':main()
