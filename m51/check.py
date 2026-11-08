"""Preserve every UI-01C file and mode; laboratory changes are additions only."""
import hashlib,json,subprocess
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
BASE='0dfeef8ff09d60a68d18398ccb6b2530f3b87df4'
def git(*args):return subprocess.check_output(['git',*args],cwd=ROOT)
def main():
    subprocess.run(['git','merge-base','--is-ancestor',BASE,'HEAD'],cwd=ROOT,check=True)
    frozen={}
    for line in git('ls-tree','-r',BASE).decode().splitlines():
        meta,n=line.split('\t',1);mode,kind,_=meta.split();assert kind=='blob';frozen[n]=(mode,git('cat-file','--filters',BASE+':'+n))
    actual=set(git('ls-files','--cached','--others','--exclude-standard').decode().splitlines())
    assert set(frozen)<=actual
    assert all(n.startswith('m51/') or n=='.github/workflows/m51-foundation.yml' for n in actual-set(frozen))
    modes={line.split('\t',1)[1]:line.split()[0] for line in git('ls-files','--stage').decode().splitlines()}
    for n in actual:
        p=ROOT/n;assert p.is_file() and not p.is_symlink() and all(not a.is_symlink() for a in p.parents if a!=ROOT)
        assert modes.get(n)==(frozen[n][0] if n in frozen else '100644'),n
        if n in frozen:assert p.read_bytes()==frozen[n][1],n
        else:
            assert p.suffix.lower() not in ('.gguf','.onnx','.bin','.jks','.keystore','.pem','.key','.pyc','.mp4','.wav')
            p.read_bytes().decode('utf-8')
    result={'base':BASE,'commit':git('rev-parse','HEAD').decode().strip(),'tree':git('rev-parse','HEAD^{tree}').decode().strip(),
            'worktree_dirty':bool(git('status','--porcelain')),'frozen_files':len(frozen),'historical_files_changed':False,
            'runtime_modules_connected':['INTERFACE_LOCAL_ONLY'],'core_llm_connected':False,'jni_patch_applied':False,
            'model_downloaded':False,'personal_device_installed':False}
    out=ROOT/'build/m51-reports';out.mkdir(parents=True,exist_ok=True);(out/'preservation.json').write_text(json.dumps(result,indent=2)+'\n');print(json.dumps(result))
if __name__=='__main__':main()
