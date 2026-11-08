"""Freeze all M51 production sources and original tests at the exact R1 input."""
import hashlib,json,subprocess
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2];BASE='9c8960a7c9d3f572dff41fe18a5b002c9f66ac8a'
ALLOWED={'.github/workflows/m51-foundation.yml','m51/android-lab/build.gradle.kts'}
def git(*a):return subprocess.check_output(['git',*a],cwd=ROOT)
def main():
    subprocess.run(['git','merge-base','--is-ancestor',BASE,'HEAD'],cwd=ROOT,check=True)
    locked={}
    for line in git('ls-tree','-r',BASE).decode().splitlines():
        info,n=line.split('\t');mode,kind,oid=info.split();assert kind=='blob';locked[n]=(mode,oid)
    names=git('ls-files','--cached','--others','--exclude-standard').decode().splitlines();assert set(locked)<=set(names)
    stage={l.split('\t')[1]:l.split()[0] for l in git('ls-files','--stage').decode().splitlines()};changed=[]
    for n in names:
        p=ROOT/n;assert p.is_file() and not p.is_symlink();assert stage.get(n)=='100644' or (n in locked and stage[n]==locked[n][0])
        if n in locked and n not in ALLOWED:assert git('hash-object','--',n).decode().strip()==locked[n][1],n
        elif n in ALLOWED:changed.append(n)
        else:assert n.startswith('m51/r1/') and p.suffix.lower() in {'.py','.kt','.md','.json','.txt'},n;p.read_text()
    result={'source_commit':git('rev-parse','HEAD').decode().strip(),'source_tree':git('rev-parse','HEAD^{tree}').decode().strip(),'base':BASE,'historical_files_preserved':len(locked)-len(ALLOWED),'allowed_build_and_ci_changes':changed,'production_sources_changed':False,'original_tests_changed':False,'jni_changed':False,'other_modules_changed':False}
    out=ROOT/'build/m51-r1';out.mkdir(parents=True,exist_ok=True);(out/'preservation.json').write_text(json.dumps(result,indent=2)+'\n');print(json.dumps(result))
if __name__=='__main__':main()
