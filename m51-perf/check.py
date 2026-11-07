"""All reference bytes and modes unchanged; only a performance review harness is added."""
import hashlib,json,subprocess
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1];BASE='0dfeef8ff09d60a68d18398ccb6b2530f3b87df4'
def git(*a):return subprocess.check_output(['git',*a],cwd=ROOT)
def main():
    names=set()
    for line in git('ls-tree','-r',BASE).decode().splitlines():
        meta,name=line.split('\t');mode,kind,blob=meta.split();assert kind=='blob';p=ROOT/name;assert p.is_file() and not p.is_symlink()
        assert p.read_bytes()==git('cat-file','--filters',BASE+':'+name),name;names.add(name)
        assert ('100755' if p.stat().st_mode&0o111 else '100644')==mode,name
    changed=set(git('ls-files','--cached','--others','--exclude-standard').decode().splitlines())-names
    assert all(n.startswith('m51-perf/') or n=='.github/workflows/m51-ui-perf-review.yml' for n in changed)
    for n in changed:
        p=ROOT/n;assert p.is_file() and not p.is_symlink();p.read_bytes().decode('utf-8');assert p.suffix not in ('.gguf','.apk','.jar','.so','.pyc')
    out=ROOT/'build/m51-perf';out.mkdir(parents=True,exist_ok=True)
    report={'base':BASE,'commit':git('rev-parse','HEAD').decode().strip(),'tree':git('rev-parse','HEAD^{tree}').decode().strip(),'worktree_dirty':bool(git('status','--porcelain')),'preserved_files':len(names),'active_sources_changed':False,'android_executed':'NOT_RUN','new_measurements':'NOT_RUN'}
    (out/'preservation.json').write_text(json.dumps(report,indent=2)+'\n');print(json.dumps(report))
if __name__=='__main__':main()
