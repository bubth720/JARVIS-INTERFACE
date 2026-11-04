#!/usr/bin/env python3
"""Inspect the actual laboratory APK; do not infer permission or native state from source."""
import argparse
import hashlib
import json
from pathlib import Path
import re
import subprocess
import zipfile


def inspect(apk, aapt):
    badging = subprocess.check_output([str(aapt), 'dump', 'badging', str(apk)], text=True)
    manifest = subprocess.check_output([str(aapt), 'dump', 'xmltree', str(apk), 'AndroidManifest.xml'], text=True)
    permissions = subprocess.check_output([str(aapt), 'dump', 'permissions', str(apk)], text=True)
    assert "package: name='fr.jarvis.lab.m50s1'" in badging
    assert "sdkVersion:'29'" in badging and "targetSdkVersion:'35'" in badging
    assert re.search(r'android:testOnly[^\n]*0xffffffff', manifest), 'testOnly missing'
    assert re.search(r'android:allowBackup[^\n]*0x0\b', manifest), 'Backup enabled'
    assert 'uses-permission' not in permissions and 'uses-permission' not in manifest
    assert not re.search(r'E: (service|provider|receiver)\b', manifest)
    assert len(re.findall(r'E: activity\b', manifest)) == 1
    with zipfile.ZipFile(apk) as archive:
        names = archive.namelist()
        assert not any(p.startswith('lib/') for p in names), 'Native libraries forbidden'
        assert not any(p.startswith('assets/') for p in names), 'Model/assets excluded from this laboratory'
        for forbidden in (b'AppGraph', b'LlamaLocalEngine', b'WhisperLocalEngine'):
            assert not any(forbidden in archive.read(p) for p in names if re.fullmatch(r'classes\d*\.dex', p)), 'Historical runtime imported'
    return {'schema': 'jarvis.m50-s1b.apk/1', 'sha256': hashlib.sha256(apk.read_bytes()).hexdigest(),
            'size': apk.stat().st_size, 'application_id': 'fr.jarvis.lab.m50s1', 'min_sdk': 29,
            'target_sdk': 35, 'test_only': True, 'permissions': [], 'native_libraries': [],
            'models': [], 'backup_enabled': False, 'android_execution': 'NOT_PROVEN_BY_THIS_CHECK'}


if __name__ == '__main__':
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--apk', required=True, type=Path)
    p.add_argument('--aapt', required=True, type=Path)
    p.add_argument('--output', required=True, type=Path)
    args = p.parse_args()
    result = inspect(args.apk, args.aapt)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(result, indent=2) + '\n')
    print(json.dumps(result))
