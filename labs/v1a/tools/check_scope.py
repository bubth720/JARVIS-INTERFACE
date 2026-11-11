#!/usr/bin/env python3
"""V1-A successor scope guard.

1. Runs the ORIGINAL M50-S1B guard, byte-identical, on its own frozen commit in a detached
   worktree (where it was designed to pass).
2. On HEAD: every file of that frozen commit (historical INTERFACE app + M50 lab + its CI)
   is unchanged, and additions are confined to labs/v1a/ and its workflow.
"""
import argparse
from pathlib import Path
import subprocess
import sys
import tempfile

M50 = 'b9a6c1997710c182dd481b23a47ff7120e3e98a0'   # codex/m50-s1b-android-20261104
GUARD = 'labs/m50-s1/tools/check_scope.py'
ALLOWED = ('labs/v1a/', '.github/workflows/v1a-lab.yml')


def git(root, *args):
    return subprocess.check_output(['git', '-C', str(root), *args])


def check(root):
    root = root.resolve()
    subprocess.run(['git', '-C', str(root), 'merge-base', '--is-ancestor', M50, 'HEAD'], check=True)
    assert (root / GUARD).read_bytes() == git(root, 'show', f'{M50}:{GUARD}'), 'Original M50 guard modified'
    with tempfile.TemporaryDirectory() as tmp:
        wt = Path(tmp) / 'm50'
        subprocess.run(['git', '-C', str(root), 'worktree', 'add', '--detach', str(wt), M50], check=True, stdout=subprocess.DEVNULL)
        try:
            subprocess.run([sys.executable, str(wt / GUARD), '--interface', str(wt)], check=True)
        finally:
            subprocess.run(['git', '-C', str(root), 'worktree', 'remove', '--force', str(wt)], check=True)
    frozen = {}
    for record in git(root, 'ls-tree', '-rz', M50).split(b'\0'):
        if record:
            meta, path = record.split(b'\t', 1)
            mode, kind, sha = meta.decode().split()
            frozen[path.decode()] = mode
    actual = set(git(root, 'ls-files', '-z', '--cached', '--others', '--exclude-standard').decode().split('\0')) - {''}
    assert set(frozen) <= actual, 'Historical file removed'
    extra = sorted(p for p in actual - set(frozen) if not p.startswith(ALLOWED))
    assert not extra, 'Unexpected change outside labs/v1a: ' + ', '.join(extra)
    for name in frozen:
        path = root / name
        assert not path.is_symlink(), 'Symlink: ' + name
        assert path.read_bytes() == git(root, 'cat-file', '--filters', f'{M50}:{name}'), 'Historical content changed: ' + name
        assert bool(path.stat().st_mode & 0o111) == (frozen[name] == '100755'), 'Historical mode changed: ' + name
    print(f'PASS: original M50 guard passes on {M50[:7]}; {len(frozen)} frozen files unchanged; additions only in labs/v1a')


if __name__ == '__main__':
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--interface', required=True, type=Path)
    check(p.parse_args().interface)
