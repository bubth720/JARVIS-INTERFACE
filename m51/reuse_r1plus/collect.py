"""Collect observations without turning missing Android evidence into success."""
import argparse
import hashlib
import json
import os
from pathlib import Path

from m51.reuse_r1plus.policy import CAMPAIGN, classify


def main():
    parser=argparse.ArgumentParser()
    parser.add_argument('--api',type=int,choices=[29,35],required=True)
    args=parser.parse_args()
    out=Path('build/reuse-r1plus-device');out.mkdir(parents=True,exist_ok=True)
    def read(path):
        p=Path(path)
        return json.loads(p.read_text()) if p.exists() else None
    host=read('build/m51-r1-host/host.json')
    qualification=read(out/'qualification.json')
    environment=read(out/'environment/diagnostic.json')
    result={'campaign':CAMPAIGN,'source_commit':os.environ.get('GITHUB_SHA'),
            'run_id':os.environ.get('GITHUB_RUN_ID'),'api':args.api,'status':'BLOCKED',
            'failure_category':'UNKNOWN','root_cause':'UNKNOWN','android_qualified':False,
            'application_tests':'NOT_RUN','llm':'BLOCKED','personal_devices':'NOT_RUN',
            'host':host,'runner_step_outcome':os.environ.get('ANDROID_STEP_OUTCOME','UNKNOWN')}
    if host and not host['ready']:
        result.update(failure_category=classify(boot_completed=False),reason='NO_ADMITTED_ACCELERATOR')
    if environment and environment['status']!='PASS_SYSTEM_PREFLIGHT_ONLY':
        result['failure_category']=classify(system_incident=environment.get('environment_incident'),
                                            configuration_error=bool(environment.get('wifi_observation',{}).get('reason')))
        result['reason']=environment.get('environment_incident') or environment.get('error') or 'PREFLIGHT_NOT_ACCEPTED'
    if qualification:
        result['qualification']=qualification
        result['application_tests']='OBSERVED' if qualification.get('individual') else 'NOT_RUN'
        if qualification['status']=='PASS_SOFTWARE_EMULATOR_QUALIFICATION':
            result.update(status='PASS',android_qualified=True,failure_category=None,root_cause=None)
        elif qualification['status']=='FAIL_CRITICAL_STOP':
            result.update(status='FAIL',failure_category='UNKNOWN',reason='CRITICAL_APPLICATION_PHASE_REVIEW_STACKS')
        elif qualification['status']=='FAIL_INDIVIDUAL_ASSERTION':
            result.update(status='FAIL',failure_category='UNKNOWN',reason='APPLICATION_OR_TEST_ASSERTION_REQUIRES_REVIEW')
    (out/'RECEPTION.json').write_text(json.dumps(result,indent=2)+'\n')
    sdk=os.environ.get('ANDROID_HOME') or os.environ.get('ANDROID_SDK_ROOT')
    images=[]
    if sdk:
        root=Path(sdk)/'system-images'/f'android-{args.api}'/'default/x86_64'
        if root.exists():
            for p in sorted(root.iterdir()):
                if not p.is_file() or p.is_symlink():continue
                h=hashlib.sha256()
                with p.open('rb') as stream:
                    for block in iter(lambda:stream.read(1024*1024),b''):h.update(block)
                images.append({'file':p.name,'bytes':p.stat().st_size,'sha256':h.hexdigest()})
    (out/'system-image.json').write_text(json.dumps(images,indent=2)+'\n')
    (out/'SHA256SUMS-REUSE').write_text(''.join(
        hashlib.sha256(p.read_bytes()).hexdigest()+'  '+p.relative_to(out).as_posix()+'\n'
        for p in sorted(out.rglob('*')) if p.is_file() and p.name!='SHA256SUMS-REUSE'))
    print(json.dumps(result))


if __name__=='__main__':main()
