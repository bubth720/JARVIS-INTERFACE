#!/usr/bin/env python3
"""Verify only the public synthetic AVA-01 fixtures; never scan user directories."""
import hashlib
import json
from pathlib import Path
import struct
import zipfile
import zlib

root = Path(__file__).resolve().parents[1]
base = root / "app/src/main/assets/avatar-demo"
manifest = json.loads((base / "manifest.json").read_text())
assert manifest == json.loads((root / "app/src/test/resources/avatar/manifest.json").read_text())
for layer in manifest["layers"]:
    data = (base / layer["file"]).read_bytes()
    assert hashlib.sha256(data).hexdigest() == layer["sha256"]
    assert data[:8] == b"\x89PNG\r\n\x1a\n"
    width, height = struct.unpack(">II", data[16:24])
    assert (width, height) == (80, 100)
    offset, payload = 8, b""
    while offset < len(data):
        length = struct.unpack(">I", data[offset:offset + 4])[0]
        kind = data[offset + 4:offset + 8]
        chunk = data[offset + 8:offset + 8 + length]
        crc = struct.unpack(">I", data[offset + 8 + length:offset + 12 + length])[0]
        assert zlib.crc32(kind + chunk) & 0xffffffff == crc
        if kind == b"IDAT":
            payload += chunk
        offset += 12 + length
    rgba = zlib.decompress(payload)
    assert len(rgba) == height * (width * 4 + 1)
    alphas = {rgba[y * 321 + 1 + x * 4 + 3] for y in range(height) for x in range(width)}
    assert alphas == {0, 255}
with zipfile.ZipFile(root / "docs/intensive-2026-10-22/fixtures/avatar-demo.zip") as archive:
    assert archive.testzip() is None
    for name in archive.namelist():
        assert archive.read(name) == (base / name).read_bytes()
print(json.dumps({"result": "PASS", "png_files": len(manifest["layers"]),
                  "checks": ["sha256", "png_crc", "rgba_transparency", "zip_round_trip"],
                  "android_render_executed": False}))
