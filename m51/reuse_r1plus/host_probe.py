"""Read-only accelerator admission: never chmods KVM, boots, or retries Android."""
import argparse
import hashlib
import json
import os
import platform
import subprocess
from pathlib import Path

from m51.reuse_r1plus.policy import CAMPAIGN, campaign_identity


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--out', type=Path, default=Path('build/m51-r1-host'))
    args = parser.parse_args()
    args.out.mkdir(parents=True, exist_ok=True)
    head = subprocess.check_output(['git','rev-parse','HEAD'], text=True).strip()
    result = {'ready': False, 'status': 'BLOCKED_ACCELERATOR',
              'machine': platform.machine(), 'system': platform.system(),
              'protections_changed': False, 'android_booted': False,
              'source_commit': head, 'accelerator_exit': None}
    try:
        result.update(campaign_identity(CAMPAIGN,head,head,os.environ.get('GITHUB_RUN_ID'),os.environ))
        sdk = os.environ.get('ANDROID_HOME') or os.environ.get('ANDROID_SDK_ROOT')
        if not sdk: raise ValueError('ANDROID_SDK_NOT_PROVIDED')
        emulator = Path(sdk)/'emulator/emulator'
        result['emulator_sha256'] = hashlib.sha256(emulator.read_bytes()).hexdigest()
        version = subprocess.run([str(emulator),'-version'],capture_output=True,text=True,timeout=30)
        (args.out/'emulator-version.txt').write_text(version.stdout+version.stderr)
        probe = subprocess.run([str(emulator),'-accel-check'],capture_output=True,text=True,timeout=30)
        (args.out/'accel-check.txt').write_text(probe.stdout+probe.stderr)
        result['accelerator_exit'] = probe.returncode
        kind = 'HVF' if platform.system()=='Darwin' else 'KVM' if platform.system()=='Linux' else None
        result['accelerator_kind'] = kind
        result['ready'] = probe.returncode==0 and platform.machine()=='x86_64' and kind is not None
        result['status'] = 'ACCELERATOR_AVAILABLE_NOT_ANDROID_QUALIFIED' if result['ready'] else 'BLOCKED_ACCELERATOR'
    except (OSError,ValueError,subprocess.SubprocessError) as exc:
        result['error_type'] = type(exc).__name__
        result['reason'] = str(exc) if isinstance(exc,ValueError) else 'HOST_TOOL_UNAVAILABLE'
    (args.out/'host.json').write_text(json.dumps(result,indent=2)+'\n')
    print(json.dumps(result))
    if os.environ.get('GITHUB_OUTPUT'):
        with open(os.environ['GITHUB_OUTPUT'],'a') as stream:
            stream.write('ready='+str(result['ready']).lower()+'\n')


if __name__=='__main__': main()
