#!/usr/bin/env python3
"""Freeze every historical INTERFACE blob and mode; allow only the isolated lab and its CI."""
import argparse
from pathlib import Path
import subprocess

BASE = '3a0841266fd691b74fae784004804429e6e2e219'


def git(root, *args):
    return subprocess.check_output(['git', '-C', str(root), *args])


def check(root, base=BASE):
    root = root.resolve()
    subprocess.run(['git', '-C', str(root), 'merge-base', '--is-ancestor', base, 'HEAD'], check=True)
    frozen = {}
    for record in git(root, 'ls-tree', '-rz', base).split(b'\0'):
        if record:
            meta, path = record.split(b'\t', 1)
            mode, kind, sha = meta.decode().split()
            assert kind == 'blob' and mode in ('100644', '100755')
            frozen[path.decode()] = (mode, sha)
    actual = set(git(root, 'ls-files', '-z', '--cached', '--others', '--exclude-standard').decode().split('\0')) - {''}
    assert set(frozen) <= actual, 'Historical file removed'
    additions = actual - set(frozen)
    assert all(p.startswith('labs/m50-s1/') or p == '.github/workflows/m50-s1b-lab.yml' for p in additions), 'Unexpected change outside the laboratory'
    for name in actual:
        path = root / name
        assert path.is_file() and not path.is_symlink(), 'Non-regular source'
        assert not any(p.is_symlink() for p in path.parents if root in p.parents), 'Symlink directory'
        if name in frozen:
            mode, sha = frozen[name]
            # Respect the frozen repository's declared checkout line endings (notably gradlew.bat).
            assert path.read_bytes() == git(root, 'cat-file', '--filters', base + ':' + name), 'Historical content changed: ' + name
            assert bool(path.stat().st_mode & 0o111) == (mode == '100755'), 'Historical mode changed'
    print('PASS: historical INTERFACE application, build graph, tests and workflows frozen')


if __name__ == '__main__':
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--interface', required=True, type=Path)
    check(p.parse_args().interface)
