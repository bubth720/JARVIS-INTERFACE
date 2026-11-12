#!/usr/bin/env python3
"""Every reused component is byte-identical to its pinned source (git blob of sources.json)."""
import argparse, hashlib, json, pathlib, subprocess, sys
root = pathlib.Path(__file__).resolve().parents[3]
lock = json.loads((root / 'labs/v1/sources.json').read_text())
p = argparse.ArgumentParser(); p.add_argument('--voice', type=pathlib.Path, help='JARVIS-VOICE checkout (for its pinned commit)')
a = p.parse_args()
def blob(data): return hashlib.sha1(b'blob ' + str(len(data)).encode() + b'\0' + data).hexdigest()
for r in lock['reused_byte_identical']:
    data = (root / r['target']).read_bytes()
    assert blob(data) == r['blob'], 'Reused file modified: ' + r['target']
if a.voice:
    v = lock['voice']
    assert subprocess.check_output(['git', '-C', str(a.voice), 'rev-parse', 'HEAD']).decode().strip() == v['sha'], 'VOICE checkout is not the pinned commit'
    assert not subprocess.check_output(['git', '-C', str(a.voice), 'status', '--porcelain', '--untracked-files=no']).strip(), 'VOICE checkout modified'
print(f"PASS: {len(lock['reused_byte_identical'])} reused files byte-identical; VOICE pocketRuntime pinned {lock['voice']['sha'][:7]}")
