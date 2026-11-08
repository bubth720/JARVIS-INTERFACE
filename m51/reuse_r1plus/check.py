"""Freeze R1, application, assets, tests, contracts and proofs; two opt-in lab edits."""
import json
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parents[2]
BASE = 'b9a9506785b8851f55789ed4329c9394a3937447'
CHANGED = {'m51/r1/diagnose.py','m51/r1/qualification.py'}
WORKFLOW = '.github/workflows/reuse-r1plus.yml'


def git(*args): return subprocess.check_output(['git',*args],cwd=ROOT)


def main():
    subprocess.run(['git','merge-base','--is-ancestor',BASE,'HEAD'],cwd=ROOT,check=True)
    frozen = {}
    for entry in git('ls-tree','-rz',BASE).split(b'\0'):
        if not entry: continue
        meta,name = entry.split(b'\t'); mode,kind,oid = meta.decode().split()
        name = name.decode(); assert kind=='blob'; frozen[name]=(mode,oid)
        p = ROOT/name
        assert p.is_file() and not p.is_symlink(),name
        assert bool(p.stat().st_mode & 0o111)==(mode=='100755'),name
        if name not in CHANGED:
            assert git('hash-object','--',name).decode().strip()==oid,name
    names = set(git('ls-files','--cached','--others','--exclude-standard').decode().splitlines())
    assert set(frozen)<=names
    added = sorted(names-frozen.keys())
    assert all(n==WORKFLOW or n.startswith('m51/reuse_r1plus/') for n in added),added
    for name in added:
        p=ROOT/name
        assert p.is_file() and not p.is_symlink() and not (p.stat().st_mode & 0o111),name
        assert p.suffix in {'.py','.md','.json','.txt','.yml'},name
        p.read_text(encoding='utf-8')
    out = ROOT/'build/reuse-r1plus'; out.mkdir(parents=True,exist_ok=True)
    result = {'status':'PASS','base':BASE,'head':git('rev-parse','HEAD').decode().strip(),
              'tree':git('rev-parse','HEAD^{tree}').decode().strip(),
              'frozen_historical_files':len(frozen)-len(CHANGED),'changed_laboratory_files':sorted(CHANGED),
              'production_sources_changed':False,'historical_tests_changed':False,
              'historical_evidence_changed':False,'modules_connected':False,'additions':added}
    (out/'preservation.json').write_text(json.dumps(result,indent=2)+'\n')
    print(json.dumps(result))


if __name__=='__main__': main()
