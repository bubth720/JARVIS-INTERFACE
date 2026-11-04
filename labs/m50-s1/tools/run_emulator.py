#!/usr/bin/env python3
"""Run fresh instrumentation then real external force-stop/restart, on an authorized emulator only."""
import argparse
import hashlib
import json
from pathlib import Path
import re
import shutil
import subprocess
import time
import xml.etree.ElementTree as ET

PACKAGE = 'fr.jarvis.lab.m50s1'
ACTIVITY = PACKAGE + '/com.stark.jarvislocal.LabActivity'


def adb(*args):
    return subprocess.check_output(['adb', *args], text=True, timeout=60).strip()


def hierarchy():
    adb('shell', 'uiautomator', 'dump', '/sdcard/m50-lab.xml')
    return ET.fromstring(adb('shell', 'cat', '/sdcard/m50-lab.xml'))


def find(description, *, text=None, enabled=None):
    for _ in range(20):
        tree = hierarchy()
        for node in tree.iter('node'):
            if node.get('content-desc') == description and (text is None or text in node.get('text', '')) and (enabled is None or node.get('enabled') == str(enabled).lower()):
                return node
        time.sleep(0.2)
    raise RuntimeError('Laboratory UI condition not observed: ' + description)


def tap(node):
    values = list(map(int, re.findall(r'\d+', node.attrib['bounds'])))
    adb('shell', 'input', 'tap', str((values[0] + values[2]) // 2), str((values[1] + values[3]) // 2))


def epoch():
    tree = hierarchy()
    values = [n.attrib['content-desc'][6:] for n in tree.iter('node') if n.get('content-desc', '').startswith('epoch:')]
    assert len(values) == 1
    return values[0]


def run(api, lab, sources, reports):
    assert adb('shell', 'getprop', 'ro.kernel.qemu') == '1', 'Physical device forbidden'
    assert adb('shell', 'getprop', 'ro.build.version.sdk') == str(api)
    reports.mkdir(parents=True, exist_ok=False)
    apk = lab / 'app/build/outputs/apk/debug/app-debug.apk'
    expected = hashlib.sha256(apk.read_bytes()).hexdigest()
    adb('shell', 'svc', 'wifi', 'disable')
    adb('shell', 'svc', 'data', 'disable')
    # Use the ConnectivityService shell API; sending its protected broadcast is forbidden.
    adb('shell', 'cmd', 'connectivity', 'airplane-mode', 'enable')
    assert adb('shell', 'settings', 'get', 'global', 'airplane_mode_on') == '1'
    assert adb('shell', 'settings', 'get', 'global', 'wifi_on') == '0'
    results = lab / 'app/build/outputs/androidTest-results/connected'
    if results.exists():
        shutil.rmtree(results)
    cmd = ['gradle', '-p', str(lab), '-PlabSources=' + str(sources), ':app:connectedDebugAndroidTest', '--rerun', '--no-daemon', '--max-workers=2', '--console=plain']
    with (reports / 'instrumentation.log').open('w') as stream:
        p = subprocess.run(cmd, stdout=stream, stderr=subprocess.STDOUT, timeout=1200)
    if results.exists():
        shutil.copytree(results, reports / 'xml')
    if p.returncode:
        for file in (reports / 'xml').rglob('TEST-*.xml'):
            for case in ET.parse(file).getroot().iter('testcase'):
                for tag in ('failure', 'error'):
                    failure = case.find(tag)
                    if failure is not None:
                        diagnostic = f"{case.get('classname')}.{case.get('name')}: {failure.get('message', 'failed')}"[:1500]
                        diagnostic = diagnostic.replace('%', '%25').replace('\r', '%0D').replace('\n', '%0A')
                        print('::error title=M50-S1B instrumented test failure::' + diagnostic)
    assert p.returncode == 0, 'Instrumentation failed; preserve actual log/XML'
    cases = []
    for file in (reports / 'xml').rglob('TEST-*.xml'):
        for case in ET.parse(file).getroot().iter('testcase'):
            assert not any(case.find(x) is not None for x in ('failure', 'error', 'skipped'))
            cases.append((case.get('classname'), case.get('name')))
    assert len(cases) == len(set(cases)) and len(cases) >= 6, 'Missing, duplicate or stale instrumentation cases'
    assert hashlib.sha256(apk.read_bytes()).hexdigest() == expected, 'Instrumented APK changed'
    adb('install', '-t', '-r', str(apk))
    adb('shell', 'am', 'force-stop', PACKAGE)
    startup = adb('shell', 'am', 'start', '-W', '-n', ACTIVITY, '--es', 'LAB_SCENARIO', 'RESOURCE_UNKNOWN')
    cold_start = re.search(r'TotalTime:\s*(\d+)', startup)
    find('lab-status', text='OFF')
    before = epoch()
    tap(find('lab-activate', enabled=True))
    tap(find('lab-question'))
    adb('shell', 'input', 'text', 'synthetic-fixture')
    # With a hardware keyboard, BACK would close the Activity instead of hiding an IME.
    ime = adb('shell', 'dumpsys', 'input_method')
    if re.search(r'(mInputShown|isInputViewShown)=true', ime):
        adb('shell', 'input', 'keyevent', 'KEYCODE_BACK')
    tap(find('lab-submit', enabled=True))
    find('lab-status', text='UNKNOWN')
    find('lab-submit', enabled=False)
    memory = adb('shell', 'dumpsys', 'meminfo', PACKAGE)
    pss = re.search(r'TOTAL PSS:\s*(\d+)', memory) or re.search(r'^\s*TOTAL\s+(\d+)', memory, re.M)
    # Starting the Activity again cannot replace this UNKNOWN process owner.
    adb('shell', 'am', 'start', '-W', '-n', ACTIVITY)
    find('lab-status', text='UNKNOWN')
    assert epoch() == before
    with (reports / 'unknown.png').open('wb') as stream:
        subprocess.run(['adb', 'exec-out', 'screencap', '-p'], stdout=stream, check=True, timeout=30)
    adb('shell', 'am', 'force-stop', PACKAGE)
    adb('shell', 'am', 'start', '-W', '-n', ACTIVITY)
    find('lab-status', text='OFF')
    after = epoch()
    assert after != before, 'Process restart was not observed'
    assert find('lab-response').get('text', '') == '', 'Old RAM response restored'
    with (reports / 'restart.png').open('wb') as stream:
        subprocess.run(['adb', 'exec-out', 'screencap', '-p'], stdout=stream, check=True, timeout=30)
    report = {'api': api, 'apk_sha256': expected, 'platform': 'Android emulator x86_64',
              'instrumentation': {'passed': len(cases), 'failed': 0, 'skipped': 0, 'cases': cases},
              'airplane_mode': True, 'wifi_enabled': False, 'external_force_stop_restart': 'PASS',
              'unknown_same_process_blocked': True, 'new_process_epoch': True, 'old_ram_restored': False,
              'diagnostic_metrics': {'cold_start_total_ms': int(cold_start[1]) if cold_start else None,
                                     'unknown_synthetic_process_pss_kib': int(pss[1]) if pss else None,
                                     'native_inference_latency': 'NOT_RUN'},
              'physical_device': 'NOT_RUN', 'native_generation': 'NOT_RUN'}
    (reports / 'RESULTS.json').write_text(json.dumps(report, indent=2) + '\n')
    print(json.dumps(report))


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--api', type=int, choices=(29, 35), required=True)
    parser.add_argument('--lab', type=Path, required=True)
    parser.add_argument('--sources', type=Path, required=True)
    parser.add_argument('--reports', type=Path, required=True)
    args = parser.parse_args()
    try:
        run(args.api, args.lab.resolve(), args.sources.resolve(), args.reports.resolve())
    except Exception as error:
        # Public laboratory diagnostic only; do not publish the captured device log as an annotation.
        message = str(error).replace('%', '%25').replace('\r', '%0D').replace('\n', '%0A')
        print('::error title=M50-S1B emulator qualification failed::' + message)
        raise
