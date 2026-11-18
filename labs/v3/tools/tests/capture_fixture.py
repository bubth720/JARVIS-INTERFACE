"""Tiny synthetic PNGs for host tests only. Never bundled as emulator evidence."""
import json
import os
from pathlib import Path
import struct
import sys
import zlib

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from validate_captures import AVATAR, REQUIRED


def png():
    def chunk(kind, data):
        return struct.pack('>I', len(data)) + kind + data + struct.pack('>I', zlib.crc32(kind + data))
    return (b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', 1, 1, 8, 2, 0, 0, 0))
            + chunk(b'IDAT', zlib.compress(b'\0\0\0\0')) + chunk(b'IEND', b''))


def index():
    return {'schema': 'jarvis.capture-tour/1', 'api': 29, 'package': 'fr.jarvis.v3',
            'captures': [{'file': name, 'screen': 'SYNTHETIC HOST TEST FIXTURE', 'status': 'captured'}
                         for name in sorted(REQUIRED | {AVATAR})]}


if __name__ == '__main__':
    mode = os.environ.get('FAKE_EVIDENCE')
    if sys.argv[1] == 'list':
        names = sorted(REQUIRED | {AVATAR})
        if mode == 'missing_png': names = names[1:]
        print('\n'.join(n + '.png' for n in names))
    elif sys.argv[1] == 'index':
        if mode == 'missing_index': sys.exit(1)
        print(json.dumps(index()))
    elif sys.argv[1] == 'png' and mode != 'empty_png':
        sys.stdout.buffer.write(png())
