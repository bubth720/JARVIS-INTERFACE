#!/usr/bin/env python3
"""M55-D successor scope guard.

1. Runs the V1-B guard (labs/v1b/tools/check_scope.py), byte-identical, on its own frozen commit
   d592505 in a detached worktree; it runs the V1-A guard, which runs the ORIGINAL M50-S1B guard.
2. On HEAD: every file of the V1-B commit (historical INTERFACE app, M50, V1-A and V1-B labs and their
   workflows) is unchanged, and additions are confined to labs/m55d/ and its workflow.
"""
import argparse
from pathlib import Path
import subprocess
import sys
import tempfile

V1A = 'd592505d03607d29c0b86dea0de5a2f0f375916b'   # V1-B: API29/API35 mechanics PASS, 2026-11-12
GUARD = 'labs/v1b/tools/check_scope.py'
ALLOWED = ('labs/m55d/', '.github/workflows/m55d-lab.yml')


def git(root, *args):
    return subprocess.check_output(['git', '-C', str(root), *args])


def check(root):
    root = root.resolve()
    subprocess.run(['git', '-C', str(root), 'merge-base', '--is-ancestor', V1A, 'HEAD'], check=True)
    assert (root / GUARD).read_bytes() == git(root, 'show', f'{V1A}:{GUARD}'), 'V1-B guard modified'
    with tempfile.TemporaryDirectory() as tmp:
        wt = Path(tmp) / 'v1b'
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
    assert set(frozen) <= actual, 'Historical, V1-A or V1-B file removed'
    extra = sorted(p for p in actual - set(frozen) if not p.startswith(ALLOWED))
    assert not extra, 'Unexpected change outside labs/m55d: ' + ', '.join(extra)
    for name in frozen:
        path = root / name
        assert not path.is_symlink(), 'Symlink: ' + name
        assert path.read_bytes() == git(root, 'cat-file', '--filters', f'{V1A}:{name}'), 'Historical, V1-A or V1-B content changed: ' + name
        assert bool(path.stat().st_mode & 0o111) == (frozen[name] == '100755'), 'Historical mode changed: ' + name
    print(f'PASS: V1-B guard (V1-A and original M50 guards inside) passes on {V1A[:7]}; {len(frozen)} frozen files unchanged; additions only in labs/m55d')


if __name__ == '__main__':
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--interface', required=True, type=Path)
    check(p.parse_args().interface)
