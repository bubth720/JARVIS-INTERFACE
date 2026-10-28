#!/usr/bin/env python3
"""Extract only the named public OFF fixture PNG from a checked test emulator."""
import argparse
import hashlib
import json
from pathlib import Path
import re
import struct
import subprocess
import zlib


def png_metadata(data):
    if (not 45 <= len(data) <= 16 * 1024 * 1024 or
            data[:8] != b'\x89PNG\r\n\x1a\n' or data[8:16] != b'\x00\x00\x00\rIHDR' or
            data[-12:] != b'\x00\x00\x00\x00IEND\xaeB`\x82'):
        raise ValueError('Invalid public PNG envelope')
    width, height = struct.unpack('>II', data[16:24])
    if not 1 <= width <= 8192 or not 1 <= height <= 8192:
        raise ValueError('Invalid public PNG dimensions')
    offset = 8
    has_image_data = False
    while offset < len(data):
        if len(data) - offset < 12:
            raise ValueError('Truncated PNG chunk')
        length = struct.unpack('>I', data[offset:offset + 4])[0]
        end = offset + 12 + length
        if end > len(data):
            raise ValueError('Truncated PNG chunk payload')
        chunk = data[offset + 4:end - 4]
        if zlib.crc32(chunk) != struct.unpack('>I', data[end - 4:end])[0]:
            raise ValueError('Corrupt PNG chunk')
        has_image_data |= chunk[:4] == b'IDAT' and length > 0
        offset = end
    if not has_image_data:
        raise ValueError('PNG has no image data')
    return {'width': width, 'height': height, 'bytes': len(data),
            'sha256': hashlib.sha256(data).hexdigest()}


def collect(serial, output):
    if not re.fullmatch(r'emulator-[0-9]+', serial):
        raise ValueError('Refusing non-emulator capture')
    if output.is_symlink():
        raise ValueError('Refusing capture symlink')
    output.parent.mkdir(parents=True, exist_ok=True)
    with output.open('wb') as stream:
        result = subprocess.run(['adb', '-s', serial, 'exec-out', 'run-as',
                                 'com.jarvis.interfaceapp', 'cat',
                                 'files/prefusion-lot3-off.png'], stdout=stream,
                                stderr=subprocess.PIPE, check=False)
    metadata = {'exit_code': result.returncode, 'source': 'public_off_fixture'}
    if result.returncode:
        # Do not publish raw stderr, environment, stack traces or arbitrary paths.
        error = result.stderr.lower()
        metadata['reason'] = ('RUN_AS_DENIED' if b'not debuggable' in error or b'permission denied' in error
                              else 'PUBLIC_PNG_MISSING' if b'no such file' in error
                              else 'ADB_CAPTURE_FAILED')
    else:
        try:
            metadata.update(png_metadata(output.read_bytes()), reason='PUBLIC_PNG_AVAILABLE')
        except ValueError:
            metadata.update(exit_code=1, reason='INVALID_PUBLIC_PNG')
    output.with_suffix('.json').write_text(json.dumps(metadata, indent=2) + '\n')
    print('::warning title=Public OFF capture::exit_code=' + str(metadata['exit_code']) +
          ', reason=' + metadata['reason'])
    return metadata['exit_code']


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--serial', required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    raise SystemExit(collect(args.serial, args.output))
