#!/usr/bin/env python3
"""JARVIS V1 successor scope guard.

1. Runs the M55-D guard (labs/m55d/tools/check_scope.py), byte-identical, on its own frozen commit
   b018152 in a detached worktree; it chains the V1-B, V1-A and ORIGINAL M50-S1B guards.
2. On HEAD: every file of that commit (historical INTERFACE app, M50, V1-A, V1-B and M55-D labs and their
   workflows) is unchanged, and additions are confined to labs/v1/ and its workflow.
"""
import argparse
from pathlib import Path
import subprocess
import sys
import tempfile

V1A = 'b0181526c7b9fa74dff6c6822121c71eceff5827'   # M55-D: API29/API35 PASS, 2026-11-12
GUARD = 'labs/m55d/tools/check_scope.py'
ALLOWED = ('labs/v1/', '.github/workflows/v1-delivery.yml')


def git(root, *args):
    return subprocess.check_output(['git', '-C', str(root), *args])


def check(root):
    root = root.resolve()
    subprocess.run(['git', '-C', str(root), 'merge-base', '--is-ancestor', V1A, 'HEAD'], check=True)
    assert (root / GUARD).read_bytes() == git(root, 'show', f'{V1A}:{GUARD}'), 'M55-D guard modified'
    with tempfile.TemporaryDirectory() as tmp:
        wt = Path(tmp) / 'm55d'
        subprocess.run(['git', '-C', str(root), 'worktree', 'add', '--detach', str(wt), V1A], check=True, stdout=subprocess.DEVNULL)
        try:
            subprocess.run([sys.executable, str(wt / GUARD), '--interface', str(wt)], check=True)
        finally:
            subprocess.run(['git', '-C', str(root), 'worktree', 'remove', '--force', str(wt)], check=True)
    frozen = {}
    for record in git(root, 'ls-tree', '-rz', V1A).split(b'\0'):
        if record:
            meta, path = record.split(b'\t', 1)
            mode, kind, sha = meta.decode().split()
            frozen[path.decode()] = mode
    actual = set(git(root, 'ls-files', '-z', '--cached', '--others', '--exclude-standard').decode().split('\0')) - {''}
    assert set(frozen) <= actual, 'Historical or lab file removed'
    extra = sorted(p for p in actual - set(frozen) if not p.startswith(ALLOWED))
    assert not extra, 'Unexpected change outside labs/v1: ' + ', '.join(extra)
    for name in frozen:
        path = root / name
        assert not path.is_symlink(), 'Symlink: ' + name
        assert path.read_bytes() == git(root, 'cat-file', '--filters', f'{V1A}:{name}'), 'Historical or lab content changed: ' + name
        assert bool(path.stat().st_mode & 0o111) == (frozen[name] == '100755'), 'Historical mode changed: ' + name
    print(f'PASS: M55-D guard (V1-B, V1-A, M50 inside) passes on {V1A[:7]}; {len(frozen)} frozen files unchanged; additions only in labs/v1')


if __name__ == '__main__':
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--interface', required=True, type=Path)
    check(p.parse_args().interface)
