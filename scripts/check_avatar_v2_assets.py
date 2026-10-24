#!/usr/bin/env python3
"""Verify AVA-03 public technical fixture only; reference art is never read or copied."""
import hashlib
import json
from pathlib import Path
import struct
import zipfile

root = Path(__file__).resolve().parents[1]
manifest_path = root / 'app/src/main/assets/avatar-v2-demo/manifest.json'
manifest = json.loads(manifest_path.read_text())
assert manifest == json.loads((root / 'app/src/test/resources/avatar/v2/manifest.json').read_text())
assert manifest['artGrade'] == 'DEMONSTRATION'
assert len(manifest['frames']) == 1 and manifest['frames'][0]['state'] == 'IDLE'
pixels = 0
expected = {'manifest.json': manifest_path.read_bytes()}
for resource in manifest['resources']:
    data = (root / 'app/src/main/assets/avatar-demo' / resource['file']).read_bytes()
    assert hashlib.sha256(data).hexdigest() == resource['sha256']
    width, height = struct.unpack('>II', data[16:24])
    assert (width, height) == (resource['width'], resource['height'])
    pixels += width * height
    expected[resource['file']] = data
assert pixels == 40_000
with zipfile.ZipFile(root / 'docs/avatar/v2/fixtures/ava03-technical-demo.zip') as archive:
    assert archive.testzip() is None
    assert set(archive.namelist()) == set(expected)
    for name, content in expected.items():
        assert archive.read(name) == content
availability = json.loads((root / 'app/src/main/assets/avatar-v2/availability.json').read_text())
assert availability['status'] == 'ART_ASSETS_MISSING' and availability['publicationAuthorized'] is False
assert not (root / 'app/src/main/assets/avatar-v2/manifest.json').exists(), 'Do not label a missing pack as production-ready'
reference_hash = '5d226d51391248b8e5f55771f9be341696c75ba1105de996303247386543f84c'
for path in (root / 'app/src/main/assets').rglob('*.png'):
    assert hashlib.sha256(path.read_bytes()).hexdigest() != reference_hash, 'Private reference must not be packaged'
print(json.dumps({'result': 'PASS', 'kind': 'TECHNICAL_DEMO_ONLY', 'textures': 5, 'pixels': pixels,
                  'rgbaBytesTheoretical': pixels * 4, 'officialArt': 'ART_ASSETS_MISSING'}))
