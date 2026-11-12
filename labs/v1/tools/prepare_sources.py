#!/usr/bin/env python3
"""V1: extract exactly nine pinned Kotlin blobs (CORE x6, INTERFACE x3) and verify the pinned LLM A02 native tree. No network."""
import argparse
import hashlib
import json
from pathlib import Path, PurePosixPath
import re
import subprocess
import tempfile

LOCK = Path(__file__).resolve().parents[1] / 'sources.json'


def git(root, *args):
    return subprocess.check_output(['git', '-C', str(root), *args])


def relative_source(value):
    path = PurePosixPath(value)
    prefix = 'app/src/main/java/'
    if not value.startswith(prefix) or path.is_absolute() or '..' in path.parts or '\\' in value or not value.endswith('.kt'):
        raise ValueError('Invalid Kotlin source path')
    return Path(value[len(prefix):])


def verify_llm(root, module):
    # The A02 engine is built from the LLM checkout itself: it must be the exact pinned commit,
    # clean, with every listed native file byte-identical to the lock.
    if git(root, 'rev-parse', 'HEAD').decode().strip() != module['sha']:
        raise ValueError('LLM checkout is not the pinned commit')
    if git(root, 'rev-parse', 'HEAD^{tree}').decode().strip() != module['tree']:
        raise ValueError('LLM tree mismatch')
    if git(root, 'status', '--porcelain', '--untracked-files=no').strip():
        raise ValueError('LLM checkout modified')
    for source in module['native']:
        data = (root / source['path']).read_bytes()
        blob = hashlib.sha1(b'blob ' + str(len(data)).encode() + b'\0' + data).hexdigest()
        if blob != source['sha'] or len(data) != source['size']:
            raise ValueError('LLM native file mismatch: ' + source['path'])


def extract(core, interface, llm, output):
    lock = json.loads(LOCK.read_text())
    roots = {'CORE': core.resolve(), 'INTERFACE': interface.resolve()}
    verify_llm(llm.resolve(), next(m for m in lock['modules'] if m['id'] == 'LLM'))
    lock = dict(lock, modules=[m for m in lock['modules'] if m['id'] != 'LLM'])
    # Never overwrite the historical source graph, even through a symlink.
    output = output.resolve()
    if any(output == root or root in output.parents for root in roots.values()):
        raise ValueError('Output must be outside the source checkouts')
    if output.exists():
        raise ValueError('Output must be a new directory')
    output.parent.mkdir(parents=True, exist_ok=True)
    if {m['id'] for m in lock['modules']} != set(roots) or sum(len(m['files']) for m in lock['modules']) != 9:
        raise ValueError('Exactly the reviewed nine sources are required')
    with tempfile.TemporaryDirectory(prefix='m50-source-', dir=output.parent) as staging:
        destination = Path(staging) / 'snapshot'
        destination.mkdir()
        used = set()
        for module in lock['modules']:
            root = roots[module['id']]
            sha = module['sha']
            if not re.fullmatch('[0-9a-f]{40}', sha):
                raise ValueError('Full commit required')
            if git(root, 'rev-parse', sha + '^{tree}').decode().strip() != module['tree']:
                raise ValueError('Tree mismatch')
            for source in module['files']:
                relative = relative_source(source['path'])
                if relative in used:
                    raise ValueError('Source collision')
                used.add(relative)
                data = git(root, 'cat-file', 'blob', sha + ':' + source['path'])
                blob = hashlib.sha1(b'blob ' + str(len(data)).encode() + b'\0' + data).hexdigest()
                entry = git(root, 'ls-tree', sha, '--', source['path']).decode().split()
                if entry[:3] != ['100644', 'blob', source['sha']] or blob != source['sha'] or len(data) != source['size']:
                    raise ValueError('Blob, mode or size mismatch')
                target = destination / 'kotlin' / relative
                target.parent.mkdir(parents=True, exist_ok=True)
                target.write_bytes(data)
        (destination / 'provenance.json').write_text(json.dumps(lock, indent=2) + '\n')
        destination.rename(output)
    print('PASS: nine exact Git blobs extracted; LLM A02 native files verified; historical files untouched')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--core', type=Path, required=True)
    parser.add_argument('--interface', type=Path, required=True)
    parser.add_argument('--llm', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    extract(args.core, args.interface, args.llm, args.output)
