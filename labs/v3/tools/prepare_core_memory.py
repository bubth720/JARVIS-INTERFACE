#!/usr/bin/env python3
"""CONVERSATION-01 C3: extract the CORE consented-memory workflow (5 memory files + IdentityCodec) UNCHANGED from the
pinned CORE commit, each blob checked against labs/v3/core-memory.lock.json. No network, no edit."""
import argparse, hashlib, json, pathlib, subprocess
lock = json.loads((pathlib.Path(__file__).resolve().parents[1] / 'core-memory.lock.json').read_text())
p = argparse.ArgumentParser(); p.add_argument('--core', required=True); p.add_argument('--output', required=True); a = p.parse_args()
git = lambda *x: subprocess.check_output(['git', '-C', a.core, *x])
assert git('rev-parse', 'HEAD').decode().strip() == lock['commit'], 'CORE checkout is not the pinned commit'
out = pathlib.Path(a.output)
for f in lock['files']:
    data = git('show', f"{lock['commit']}:{f['path']}")
    blob = hashlib.sha1(b'blob ' + str(len(data)).encode() + b'\0' + data).hexdigest()
    assert blob == f['blob'] and len(data) == f['size'], 'CORE memory file mismatch: ' + f['path']
    dst = out / f["path"].removeprefix("app/src/main/java/"); dst.parent.mkdir(parents=True, exist_ok=True)
    dst.write_bytes(data)
print(f"PASS: {len(lock['files'])} CORE memory files extracted byte-identical from {lock['commit'][:7]}")
