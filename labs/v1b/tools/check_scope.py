#!/usr/bin/env python3
"""V1-B successor scope guard.

1. Runs the V1-A guard (labs/v1a/tools/check_scope.py), byte-identical, on its own frozen commit
   4ed3f99 in a detached worktree. That guard itself runs the ORIGINAL M50-S1B guard on b9a6c19.
2. On HEAD: every file of the V1-A commit (historical INTERFACE app, M50 lab, V1-A lab and their
   workflows) is unchanged, and additions are confined to labs/v1b/ and its workflow.
"""
import argparse
from pathlib import Path
import subprocess
import sys
import tempfile

V1A = '4ed3f99c198fde3b559433bb6fc71c46851c0a3e'   # V1-A: API29/API35 PASS, 2026-11-11
GUARD = 'labs/v1a/tools/check_scope.py'
ALLOWED = ('labs/v1b/', '.github/workflows/v1b-lab.yml')


def git(root, *args):
    return subprocess.check_output(['git', '-C', str(root), *args])


def check(root):
    root = root.resolve()
    subprocess.run(['git', '-C', str(root), 'merge-base', '--is-ancestor', V1A, 'HEAD'], check=True)
    assert (root / GUARD).read_bytes() == git(root, 'show', f'{V1A}:{GUARD}'), 'V1-A guard modified'
    with tempfile.TemporaryDirectory() as tmp:
        wt = Path(tmp) / 'v1a'
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
    assert set(frozen) <= actual, 'Historical or V1-A file removed'
    extra = sorted(p for p in actual - set(frozen) if not p.startswith(ALLOWED))
    assert not extra, 'Unexpected change outside labs/v1b: ' + ', '.join(extra)
    for name in frozen:
        path = root / name
        assert not path.is_symlink(), 'Symlink: ' + name
        assert path.read_bytes() == git(root, 'cat-file', '--filters', f'{V1A}:{name}'), 'Historical or V1-A content changed: ' + name
        assert bool(path.stat().st_mode & 0o111) == (frozen[name] == '100755'), 'Historical mode changed: ' + name
    print(f'PASS: V1-A guard (and the original M50 guard) pass on {V1A[:7]}; {len(frozen)} frozen files unchanged; additions only in labs/v1b')


if __name__ == '__main__':
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--interface', required=True, type=Path)
    check(p.parse_args().interface)
