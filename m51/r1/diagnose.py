"""One bounded Android diagnostic campaign; preflight independent of JARVIS, no retries."""
import argparse,hashlib,json,os,re,subprocess,time
from pathlib import Path
from m51.r1.policy import instrumentation_result,system_incident
from m51.display_config import require_display
ROOT=Path(__file__).resolve().parents[2]
SCENARIOS=[('saf','com.jarvis.interfaceapp.DocumentPickerContractTest#actualAndroidDocumentPickerImportsLocalFileWithoutDeletingOriginal'),
 ('navigation','com.jarvis.interfaceapp.Ui01cContractTest#navigationContractAllIconsAndModulesInBothOrientations'),
 ('project','com.jarvis.interfaceapp.Ui01cContractTest#projectContractStepsProgressAndHistoryAreReal'),
 ('capture-project','com.jarvis.interfaceapp.Ui01cContractTest#settingsContractAllWeatherProfilesSlidersAndRealCacheIsolation,com.jarvis.interfaceapp.Ui01cContractTest#projectContractStepsProgressAndHistoryAreReal')]

def main():
    p=argparse.ArgumentParser();p.add_argument('--api',type=int,choices=[29,35],required=True);p.add_argument('--delivery',type=Path,required=True);p.add_argument('--out',type=Path,required=True);a=p.parse_args()
    a.out.mkdir(parents=True,exist_ok=True);serial=os.environ.get('ANDROID_SERIAL','');assert re.fullmatch(r'emulator-\d+',serial)
    def adb(*args,timeout=30,check=True,binary=False):
        try:r=subprocess.run(['adb','-s',serial,*map(str,args)],capture_output=True,timeout=timeout)
        except subprocess.TimeoutExpired as e:
            (a.out/'last-timeout.txt').write_bytes((e.stdout or b'')+b'\nSTDERR\n'+(e.stderr or b''));raise
        if check and r.returncode:
            (a.out/'last-adb-error.txt').write_bytes(r.stdout+b'\nSTDERR\n'+r.stderr);raise RuntimeError('ADB_COMMAND_FAILED:'+str(args[0]))
        return r.stdout if binary else r.stdout.decode(errors='replace')
    assert adb('shell','getprop','ro.kernel.qemu').strip()=='1'
    assert int(adb('shell','getprop','ro.build.version.sdk').strip())==a.api
    assert 'x86_64' in adb('shell','getprop','ro.product.cpu.abilist')
    meta=json.loads((a.delivery/'build.json').read_text());head=subprocess.check_output(['git','rev-parse','HEAD'],cwd=ROOT,text=True).strip();assert meta['source_commit']==head
    for row in meta['apks']:
        assert row['package'] in ['fr.jarvis.v1.lab','fr.jarvis.v1.lab.baseline']
        assert not adb('shell','pm','path',row['package'],check=False).strip(),'JARVIS_MUST_BE_ABSENT_DURING_PREFLIGHT'
        for key,digest in [('apk','sha256'),('test_apk','test_sha256')]:assert hashlib.sha256((a.delivery/row[key]).read_bytes()).hexdigest()==row[digest]
    report={'source_commit':head,'source_tree':meta['source_tree'],'api':a.api,'campaign':'R1-D1','status':'RUNNING','phase':'SYSTEM_PREFLIGHT','boot_attempts':1,'invocations':[],'production_code_changed':False,'personal_devices':'NOT_RUN','qualification':'NOT_RUN','causes':{'snapshot':'UNKNOWN','saf':'UNKNOWN','navigation':'UNKNOWN','api35_process':'UNKNOWN'}}
    def snapshot(stem):
        window=adb('shell','dumpsys','window');events=adb('logcat','-d','-b','events');crashes=adb('logcat','-d','-b','crash')
        for name,data in [('window',window),('events',events),('crashes',crashes)]: (a.out/f'{stem}-{name}.txt').write_text(data)
        (a.out/f'{stem}.png').write_bytes(adb('exec-out','screencap','-p',binary=True))
        return window,events,crashes
    try:
        size,density=('1080x1920','420') if a.api==29 else ('1200x1920','240')
        adb('shell','wm','size',size);adb('shell','wm','density',density)
        report['display']=require_display(adb('shell','wm','size'),adb('shell','wm','density'),size,density)
        adb('shell','settings','put','system','accelerometer_rotation','0');adb('shell','settings','put','system','user_rotation','0')
        adb('shell','cmd','connectivity','airplane-mode','enable');adb('shell','svc','wifi','disable')
        report['airplane_mode']=adb('shell','cmd','connectivity','airplane-mode').strip();assert report['airplane_mode']=='enabled'
        assert adb('shell','settings','get','global','wifi_on').strip()=='0'
        (a.out/'home-start.txt').write_text(adb('shell','am','start','-W','-a','android.intent.action.MAIN','-c','android.intent.category.HOME'))
        (a.out/'settings-start.txt').write_text(adb('shell','am','start','-W','-a','android.settings.SETTINGS'))
        adb('shell','input','keyevent','KEYCODE_HOME')
        first_pid=None;start=time.monotonic()
        for sample in range(13):
            if time.monotonic()-start>120:
                report.update(status="BLOCKED_ENVIRONMENT",environment_incident="PREFLIGHT_WALL_BUDGET_EXCEEDED");raise RuntimeError("PREFLIGHT_WALL_BUDGET_EXCEEDED")
            window,events,crashes=snapshot(f'preflight-{sample:02d}')
            incident=system_incident(window,events,crashes)
            pid=adb('shell','pidof','com.android.systemui').strip()
            if not pid or (first_pid is not None and pid!=first_pid):incident='SYSTEM_UI_PID_CHANGED_OR_MISSING'
            first_pid=pid
            if incident:
                report.update(status='BLOCKED_ENVIRONMENT',environment_incident=incident,jarvis_installed=False);raise RuntimeError(incident)
            if sample<12:time.sleep(5)
        report['system_preflight']={'status':'PASS','observed_seconds':time.monotonic()-start,'samples':13,'systemui_pid':first_pid,'jarvis_installed':False}
        if a.api==35:
            report.update(status='PASS_SYSTEM_PREFLIGHT_ONLY',phase='DONE');return
        report['phase']='ISOLATED_API29_REPRODUCTION'
        row=next(x for x in meta['apks'] if x['variant']=='baseline');pkg=row['package']
        adb('install','-t',str((a.delivery/row['apk']).resolve()),timeout=120);adb('install','-t',str((a.delivery/row['test_apk']).resolve()),timeout=120)
        for stem,method in SCENARIOS:
            adb('shell','am','force-stop',pkg)
            text=adb('shell','am','instrument','-w','-r','-e','class',method,'-e','listener','com.jarvis.interfaceapp.r1.DiagnosticListener',pkg+'.test/androidx.test.runner.AndroidJUnitRunner',timeout=900)
            (a.out/f'{stem}-instrumentation.txt').write_text(text)
            result=instrumentation_result(text);result.update(scenario=stem,method=method,attempts=1)
            report['invocations'].append(result)
            window,events,crashes=snapshot(stem)
            for file in ['events.jsonl','threads.jsonl','picker.jsonl']:
                data=adb('exec-out','run-as',pkg,'cat','files/m51-r1/'+file,check=False,binary=True)
                (a.out/f'{stem}-{file}').write_bytes(data)
            (a.out/f'{stem}-activity.txt').write_text(adb('shell','dumpsys','activity','activities'))
            if result['critical'] or ('Process: '+pkg+',') in crashes:
                report.update(status='FAIL_CRITICAL_STOP',critical_scenario=stem);raise RuntimeError('CRITICAL_PROCESS_FAILURE_STOP_NO_RETRY')
            incident=system_incident(window,events,crashes)
            if incident:
                report.update(status='BLOCKED_ENVIRONMENT',environment_incident=incident);raise RuntimeError(incident)
        report.update(status='DIAGNOSED_ASSERTIONS_REVIEW_REQUIRED',phase='DONE')
    except Exception as e:
        report['error']=str(e)
        if report['status']=='RUNNING':report['status']='FAIL_DIAGNOSTIC'
        raise
    finally:
        (a.out/'diagnostic.json').write_text(json.dumps(report,indent=2)+'\n')
        (a.out/'SHA256SUMS').write_text(''.join(hashlib.sha256(f.read_bytes()).hexdigest()+'  '+f.name+'\n' for f in sorted(a.out.iterdir()) if f.is_file() and f.name!='SHA256SUMS'))
if __name__=='__main__':main()
