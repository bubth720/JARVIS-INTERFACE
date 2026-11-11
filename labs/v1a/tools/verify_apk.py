#!/usr/bin/env python3
"""Inspect the actual V1-A APK: no permission, single A02 engine, no bundled model, 16 KiB ELF alignment."""
import argparse, hashlib, json, re, subprocess, zipfile
from pathlib import Path

def inspect(apk, aapt, readelf, nm):
    badging = subprocess.check_output([str(aapt), 'dump', 'badging', str(apk)], text=True)
    manifest = subprocess.check_output([str(aapt), 'dump', 'xmltree', str(apk), 'AndroidManifest.xml'], text=True)
    permissions = subprocess.check_output([str(aapt), 'dump', 'permissions', str(apk)], text=True)
    assert "package: name='fr.jarvis.v1a.lab'" in badging
    assert "sdkVersion:'29'" in badging and "targetSdkVersion:'35'" in badging
    assert re.search(r'android:testOnly[^\n]*0xffffffff', manifest), 'testOnly missing'
    assert re.search(r'android:allowBackup[^\n]*0x0\b', manifest), 'Backup enabled'
    assert 'uses-permission' not in permissions and 'uses-permission' not in manifest, 'Permission requested'
    assert not re.search(r'E: (service|provider|receiver)\b', manifest)
    libs = {}
    with zipfile.ZipFile(apk) as archive:
        names = archive.namelist()
        # No model may be bundled: any GGUF, any .bin above 1 MiB (small library resources such as
        # kotlinx DebugProbesKt.bin are allowed) and any non-native entry above 32 MiB are refused.
        infos = archive.infolist()
        suspicious = [i.filename for i in infos if i.filename.lower().endswith('.gguf')
                      or (i.filename.lower().endswith('.bin') and i.file_size > 1 << 20)
                      or (i.file_size > 32 << 20 and not i.filename.endswith('.so'))]
        assert not suspicious, 'Model bundled in APK: ' + ', '.join(suspicious)
        small_bins = sorted(f'{i.filename}:{i.file_size}' for i in infos if i.filename.lower().endswith('.bin'))
        so = [n for n in names if n.startswith('lib/') and n.endswith('.so')]
        assert so and all(n.endswith('/libjarvis_llm_a02.so') or n.endswith('/libc++_shared.so') for n in so), so
        assert any(n == 'lib/arm64-v8a/libjarvis_llm_a02.so' for n in so), 'ARM64 engine missing'
        for forbidden in (b'LlamaLocalEngine', b'WhisperLocalEngine', b'com/ffmpegkit', b'LAB/SYNTHETIC'):
            assert not any(forbidden in archive.read(p) for p in names if re.fullmatch(r'classes\d*\.dex', p)), forbidden
        for n in so:
            data = archive.read(n)
            libs[n] = hashlib.sha256(data).hexdigest()
            tmp = Path('/tmp') / n.replace('/', '_')
            tmp.write_bytes(data)
            aligns = [l.split()[-1] for l in subprocess.check_output([str(readelf), '-lW', str(tmp)], text=True).splitlines() if l.strip().startswith('LOAD')]
            assert aligns and all(a == '0x4000' for a in aligns), (n, aligns)
            if n.endswith('libjarvis_llm_a02.so'):
                symbols = subprocess.check_output([str(nm), '-D', '--defined-only', str(tmp)], text=True)
                assert 'Java_fr_jarvis_llm_engine_NativeBridge_load\n' not in symbols, 'Pathname loader exported'
                assert 'Java_fr_jarvis_llm_a02_SealedNativeBridge_loadSealedFd' in symbols
    return {'schema': 'jarvis.v1a.apk/1', 'sha256': hashlib.sha256(apk.read_bytes()).hexdigest(), 'size': apk.stat().st_size,
            'application_id': 'fr.jarvis.v1a.lab', 'min_sdk': 29, 'target_sdk': 35, 'test_only': True, 'permissions': [],
            'native_libraries': libs, 'models_bundled': [], 'small_bin_resources': small_bins, 'backup_enabled': False, 'path_loader_exported': False,
            'android_execution': 'NOT_PROVEN_BY_THIS_CHECK'}

if __name__ == '__main__':
    p = argparse.ArgumentParser(description=__doc__)
    for a in ('--apk', '--aapt', '--readelf', '--nm', '--output'): p.add_argument(a, required=True, type=Path)
    a = p.parse_args()
    result = inspect(a.apk, a.aapt, a.readelf, a.nm)
    a.output.parent.mkdir(parents=True, exist_ok=True)
    a.output.write_text(json.dumps(result, indent=2) + '\n')
    print(json.dumps(result))
