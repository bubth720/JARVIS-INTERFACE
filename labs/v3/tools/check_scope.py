#!/usr/bin/env python3
"""JARVIS V3 scope guard (isolated successor of the delivered V1).

1. Runs the V1 guard (labs/v1/tools/check_scope.py) byte-identical on the frozen V1 delivery commit
   9599d99 in a detached worktree; it chains the M55-D, V1-B, V1-A and original M50 guards.
2. On HEAD: every file of that commit (historical app, all labs including labs/v1, workflows) is
   unchanged, and additions are confined to labs/v3/ and the V3 workflow. Avatar, LLM, CORE and VOICE
   are other repositories and are only read at pinned commits by the workflow.
"""
import argparse, subprocess, sys, tempfile
from pathlib import Path

V1 = '9599d99147f9c9baaccdd07b4d5a6359d558b4c6'   # V1 delivery: run 43313299256 PASS API 29/35
GUARD = 'labs/v1/tools/check_scope.py'
ALLOWED = ('labs/v3/', '.github/workflows/v3-cockpit.yml')


def git(root, *a):
    return subprocess.check_output(['git', '-C', str(root), *a])


def check(root):
    root = root.resolve()
    subprocess.run(['git', '-C', str(root), 'merge-base', '--is-ancestor', V1, 'HEAD'], check=True)
    assert (root / GUARD).read_bytes() == git(root, 'show', f'{V1}:{GUARD}'), 'V1 guard modified'
    with tempfile.TemporaryDirectory() as tmp:
        wt = Path(tmp) / 'v1'
        subprocess.run(['git', '-C', str(root), 'worktree', 'add', '--detach', str(wt), V1], check=True, stdout=subprocess.DEVNULL)
        try:
            subprocess.run([sys.executable, str(wt / GUARD), '--interface', str(wt)], check=True)
        finally:
            subprocess.run(['git', '-C', str(root), 'worktree', 'remove', '--force', str(wt)], check=True)
    frozen = {}
    for rec in git(root, 'ls-tree', '-rz', V1).split(b'\0'):
        if rec:
            meta, path = rec.split(b'\t', 1)
            frozen[path.decode()] = meta.decode().split()[0]
    actual = set(git(root, 'ls-files', '-z', '--cached', '--others', '--exclude-standard').decode().split('\0')) - {''}
    assert set(frozen) <= actual, 'Historical or V1 file removed'
    extra = sorted(p for p in actual - set(frozen) if not p.startswith(ALLOWED))
    assert not extra, 'Unexpected change outside labs/v3: ' + ', '.join(extra)
    for name, mode in frozen.items():
        path = root / name
        assert not path.is_symlink(), 'Symlink: ' + name
        assert path.read_bytes() == git(root, 'cat-file', '--filters', f'{V1}:{name}'), 'Historical or V1 content changed: ' + name
        assert bool(path.stat().st_mode & 0o111) == (mode == '100755'), 'Mode changed: ' + name
    print(f'PASS: V1 guard chain passes on {V1[:7]}; {len(frozen)} frozen files unchanged (labs/v1 included); additions only in labs/v3')


if __name__ == '__main__':
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--interface', required=True, type=Path)
    check(p.parse_args().interface)
