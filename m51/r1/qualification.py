"""Q1: accelerated healthy emulator, individual regressions, then unchanged M51 campaign."""
import argparse,hashlib,json,os,re,subprocess,sys
from pathlib import Path
from m51.r1.policy import instrumentation_result,system_incident
ROOT=Path(__file__).resolve().parents[2]
FAULTS=[('saf','com.jarvis.interfaceapp.DocumentPickerContractTest#actualAndroidDocumentPickerImportsLocalFileWithoutDeletingOriginal'),
 ('navigation','com.jarvis.interfaceapp.Ui01cContractTest#navigationContractAllIconsAndModulesInBothOrientations'),
 ('project','com.jarvis.interfaceapp.Ui01cContractTest#projectContractStepsProgressAndHistoryAreReal')]
PACKAGES={'fr.jarvis.v1.lab.baseline','fr.jarvis.v1.lab.baseline.test'}
def fixture_reset_allowed(package,created_here):
    if package not in PACKAGES or package not in created_here:raise ValueError('UNOWNED_OR_HISTORICAL_PACKAGE_RESET_REFUSED')
    return package

def main():
    p=argparse.ArgumentParser();p.add_argument('--api',type=int,choices=[29,35],required=True);p.add_argument('--delivery',type=Path,required=True);p.add_argument('--out',type=Path,required=True);a=p.parse_args()
    a.out.mkdir(parents=True,exist_ok=True);serial=os.environ.get('ANDROID_SERIAL','');assert re.fullmatch(r'emulator-\d+',serial)
    def adb(*args,timeout=30,check=True,binary=False):
        try:r=subprocess.run(['adb','-s',serial,*map(str,args)],capture_output=True,timeout=timeout)
        except subprocess.TimeoutExpired as e:
            (a.out/'last-timeout.txt').write_bytes((e.stdout or b'')+b'\nSTDERR\n'+(e.stderr or b''));raise
        if check and r.returncode:
            (a.out/'last-adb-error.txt').write_bytes(r.stdout+b'\nSTDERR\n'+r.stderr);raise RuntimeError('ADB_COMMAND_FAILED')
        return r.stdout if binary else r.stdout.decode(errors='replace')
    assert adb('shell','getprop','ro.kernel.qemu').strip()=='1'
    assert int(adb('shell','getprop','ro.build.version.sdk').strip())==a.api
    meta=json.loads((a.delivery/'build.json').read_text());head=subprocess.check_output(['git','rev-parse','HEAD'],cwd=ROOT,text=True).strip();assert meta['source_commit']==head
    host=json.loads(Path('build/m51-r1-host/host.json').read_text());assert host['ready'] and host['accelerator_exit']==0 and not host['protections_changed']
    report={'campaign':'R1-Q1','source_commit':head,'source_tree':meta['source_tree'],'api':a.api,'status':'RUNNING','stage':'ENVIRONMENT','individual':[],
            'physical_android':'NOT_RUN','llm':'NOT_RUN','boot_attempts':1,'production_changes':False,'created_packages':[]}
    def evidence(stem):
        for label,args in [('window',('shell','dumpsys','window')),('events',('logcat','-d','-b','events')),('crashes',('logcat','-d','-b','crash'))]:
            text=adb(*args);(a.out/f'{stem}-{label}.txt').write_text(text)
        (a.out/f'{stem}.png').write_bytes(adb('exec-out','screencap','-p',binary=True))
        return (a.out/f'{stem}-crashes.txt').read_text()
    try:
        # Same guard as D1, still no JARVIS installed. No interpretation of boot-completed as stability.
        subprocess.run([sys.executable,'-m','m51.r1.diagnose','--api',str(a.api),'--preflight-only','--delivery',str(a.delivery),'--out',str(a.out/'environment')],cwd=ROOT,check=True)
        env=json.loads((a.out/'environment/diagnostic.json').read_text());assert env['status']=='PASS_SYSTEM_PREFLIGHT_ONLY'
        report['environment']=env['system_preflight'];report['stage']='INDIVIDUAL_REGRESSIONS'
        row=next(r for r in meta['apks'] if r['variant']=='baseline');pkg=row['package']
        for key,digest in [('apk','sha256'),('test_apk','test_sha256')]:assert hashlib.sha256((a.delivery/row[key]).read_bytes()).hexdigest()==row[digest]
        assert not adb('shell','pm','path',pkg,check=False).strip()
        adb('install','-t',str((a.delivery/row['apk']).resolve()),timeout=120);report['created_packages'].append(pkg)
        adb('install','-t',str((a.delivery/row['test_apk']).resolve()),timeout=120);report['created_packages'].append(pkg+'.test')
        for stem,method in FAULTS:
            adb('shell','am','force-stop',pkg)
            text=adb('shell','am','instrument','-w','-r','-e','class',method,pkg+'.test/androidx.test.runner.AndroidJUnitRunner',timeout=900)
            (a.out/f'individual-{stem}.txt').write_text(text);result=instrumentation_result(text);result.update(scenario=stem,attempts=1);report['individual'].append(result)
            crash=evidence(stem)
            if result['critical'] or ('Process: '+pkg+',') in crash:
                report['status']='FAIL_CRITICAL_STOP';raise RuntimeError('CRITICAL_PROCESS_FAILURE_NO_RETRY')
            if not result['completed'] or result['reported_total']!=1:
                report['status']='FAIL_INDIVIDUAL_ASSERTION';raise RuntimeError('INDIVIDUAL_REGRESSION_NOT_PASSED')
        # These two packages were absent on this fresh guarded cloud emulator and installed here.
        # Only after all individual tests PASS, discard their synthetic fixture installation so
        # the historical full campaign keeps its original FRESH_LAB_PACKAGE_REQUIRED guard.
        for name in [pkg+'.test',pkg]:adb('uninstall',fixture_reset_allowed(name,report['created_packages']))
        report['passed_individual_fixture_cleanup']='ONLY_PACKAGES_CREATED_HERE_ALL_TESTS_PASSED'
        Path('build/m51-host').mkdir(parents=True,exist_ok=True)
        Path('build/m51-host/capability.json').write_text(json.dumps({**host,'status':'OBSERVED_ACCELERATOR_AVAILABLE','emulation':'HVF','system_preflight':'PASS','personal_device':False})+'\n')
        report['stage']='BASELINE_18_CANDIDATE_24_RESTART_AND_FRAMES'
        size,density=('1080x1920','420') if a.api==29 else ('1200x1920','240')
        full=a.out/'full';subprocess.run([sys.executable,'m51/device_campaign.py','--delivery',str(a.delivery),'--output',str(full),'--api',str(a.api),'--size',size,'--density',density,'--profile','R1-macos-intel-hvf'],cwd=ROOT,check=True)
        device=json.loads((full/'device.json').read_text());assert device['status']=='PASS_FUNCTIONAL_UI_BENCHMARKS_RECORDED'
        report.update(status='PASS_SOFTWARE_EMULATOR_QUALIFICATION',functional=device['functional'],restart=device['restart'],benchmarks=device['benchmarks'])
    except Exception as e:
        if report['status']=='RUNNING':report['status']='FAIL_OR_BLOCKED_REVIEW_RAW_EVIDENCE'
        report['error']=str(e);raise
    finally:
        (a.out/'qualification.json').write_text(json.dumps(report,indent=2)+'\n')
        files=[f for f in a.out.rglob('*') if f.is_file() and f.name!='SHA256SUMS-ALL']
        (a.out/'SHA256SUMS-ALL').write_text(''.join(hashlib.sha256(f.read_bytes()).hexdigest()+'  '+f.relative_to(a.out).as_posix()+'\n' for f in sorted(files)))
if __name__=='__main__':main()
