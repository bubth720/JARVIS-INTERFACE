#!/usr/bin/env python3
"""Accept only actual host XML plus both emulator reports bound to the identical APK."""
import argparse
from datetime import datetime, timezone
import hashlib
import json
import os
from pathlib import Path
import subprocess
import xml.etree.ElementTree as ET


def qualify(interface, reports):
    assert not subprocess.check_output(['git', '-C', str(interface), 'status', '--porcelain']), 'Clean source checkout required'
    lab = interface / 'labs/m50-s1'
    apk = lab / 'app/build/outputs/apk/debug/app-debug.apk'
    digest = hashlib.sha256(apk.read_bytes()).hexdigest()
    inspection = json.loads((reports / 'APK.json').read_text())
    assert inspection['sha256'] == digest and inspection['test_only'] and not inspection['permissions']
    host = []
    for file in (lab / 'app/build/test-results/testDebugUnitTest').glob('TEST-*.xml'):
        for case in ET.parse(file).getroot().iter('testcase'):
            assert not any(case.find(tag) is not None for tag in ('failure', 'error', 'skipped'))
            host.append((case.get('classname'), case.get('name')))
    assert len(host) >= 11 and len(host) == len(set(host)), 'Missing or repeated host cases'
    android = {}
    for api in (29, 35):
        result = json.loads((reports / f'api{api}/RESULTS.json').read_text())
        assert result['api'] == api and result['apk_sha256'] == digest
        assert result['instrumentation']['passed'] >= 6 and result['instrumentation']['failed'] == result['instrumentation']['skipped'] == 0
        assert result['external_force_stop_restart'] == 'PASS' and result['unknown_same_process_blocked']
        assert result['new_process_epoch'] and not result['old_ram_restored'] and result['airplane_mode']
        android[str(api)] = result
    subject = subprocess.check_output(['git', '-C', str(interface), 'rev-parse', 'HEAD'], text=True).strip()
    tree = subprocess.check_output(['git', '-C', str(interface), 'rev-parse', 'HEAD^{tree}'], text=True).strip()
    report = {'schema': 'jarvis.m50-s1b.qualification/1', 'observed_at': datetime.now(timezone.utc).isoformat(),
              'source_sha': subject, 'source_tree': tree, 'qualified': True, 'scope': 'Private synthetic CORE/INTERFACE laboratory only',
              'apk': inspection, 'host_cases': host, 'android': android,
              'llm_native': 'DISABLED_NOT_QUALIFIED', 'nexus': 'DISABLED', 'services': 'DISABLED',
              'voice': 'DISABLED', 'evolution': 'DISABLED', 'physical_device': 'NOT_RUN', 'M50': 'NOT_ACHIEVED'}
    (reports / 'QUALIFICATION.json').write_text(json.dumps(report, indent=2) + '\n')
    if os.environ.get('GITHUB_OUTPUT'):
        with open(os.environ['GITHUB_OUTPUT'], 'a') as stream:
            stream.write('apk_sha256=' + digest + '\n')
    print(json.dumps({'qualified': True, 'source_sha': subject, 'apk_sha256': digest,
                      'host_pass': len(host), 'android_pass_by_api': {api: value['instrumentation']['passed'] for api, value in android.items()}}))


if __name__ == '__main__':
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--interface', type=Path, required=True)
    p.add_argument('--reports', type=Path, required=True)
    args = p.parse_args()
    qualify(args.interface.resolve(), args.reports.resolve())
