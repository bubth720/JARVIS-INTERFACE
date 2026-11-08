"""Bounded cloud emulator campaign. All writes require one exact emulated serial and lab package."""
import argparse,hashlib,json,os,re,subprocess,sys,time
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def main():
    p=argparse.ArgumentParser();p.add_argument('--delivery',type=Path,required=True);p.add_argument('--output',type=Path,required=True);p.add_argument('--api',type=int,required=True);p.add_argument('--size',required=True);p.add_argument('--density',required=True);p.add_argument('--profile',required=True);a=p.parse_args()
    out=a.output;out.mkdir(parents=True,exist_ok=True)
    serial=os.environ.get('ANDROID_SERIAL','');assert re.fullmatch(r'emulator-\d+',serial),'EMULATOR_SERIAL_REQUIRED'
    def adb(*args,binary=False,timeout=120,check=True):
        run=subprocess.run(['adb','-s',serial,*map(str,args)],capture_output=True,timeout=timeout)
        if check and run.returncode:raise RuntimeError('ADB_COMMAND_FAILED:'+args[0]+':'+run.stderr.decode(errors='replace')[-400:])
        return run.stdout if binary else run.stdout.decode(errors='strict')
    assert adb('shell','getprop','ro.kernel.qemu').strip()=='1','PERSONAL_OR_UNKNOWN_DEVICE_REFUSED'
    assert int(adb('shell','getprop','ro.build.version.sdk').strip())==a.api
    assert 'x86_64' in adb('shell','getprop','ro.product.cpu.abilist')
    meta=json.loads((a.delivery/'build.json').read_text());head=subprocess.check_output(['git','rev-parse','HEAD'],cwd=ROOT,text=True).strip();assert meta['source_commit']==head
    for row in meta['apks']:
        for key,digest in [('apk','sha256'),('test_apk','test_sha256')]:assert sha(a.delivery/row[key])==row[digest]
        assert row['package'] in ('fr.jarvis.v1.lab','fr.jarvis.v1.lab.baseline') and row['test_only']
    report={'source_commit':head,'source_tree':meta['source_tree'],'serial':serial,'api':a.api,'abi':'x86_64','profile':a.profile,'host_capability':json.loads(Path('build/m51-host/capability.json').read_text()),
            'status':'RUNNING','apks':meta['apks'],'functional':{},'restart':{},'benchmarks':[],'physical_android':'NOT_RUN','llm_generation':'NOT_RUN','core_connection':False}
    try:
        adb('shell','wm','size',a.size);adb('shell','wm','density',a.density)
        adb('shell','settings','put','system','accelerometer_rotation','0');adb('shell','settings','put','system','user_rotation','0')
        adb('shell','settings','put','global','airplane_mode_on','1');adb('shell','am','broadcast','-a','android.intent.action.AIRPLANE_MODE','--ez','state','true')
        adb('shell','svc','wifi','disable');adb('shell','svc','data','disable')
        report['airplane_mode']=adb('shell','settings','get','global','airplane_mode_on').strip();assert report['airplane_mode']=='1'
        report['display_size']=adb('shell','wm','size').strip();report['display_density']=adb('shell','wm','density').strip()
        report['device_fingerprint']=adb('shell','getprop','ro.build.fingerprint').strip()
        report['runner_image']=os.environ.get('ImageVersion','UNKNOWN')
        def instrumentation(row,stem,extra):
            pkg=row['package'];text=adb('shell','am','instrument','-w','-r',*extra,pkg+'.test/androidx.test.runner.AndroidJUnitRunner',timeout=900)
            (out/(stem+'.txt')).write_text(text)
            match=re.search(r'OK\s*\((\d+) tests?\)',text)
            if not match or 'FAILURES!!!' in text or 'INSTRUMENTATION_FAILED' in text:raise RuntimeError('INSTRUMENTATION_FAILED:'+stem)
            return int(match[1])
        for row in meta['apks']:
            pkg=row['package'];variant=row['variant']
            assert not adb('shell','pm','path',pkg,check=False).strip(),'FRESH_LAB_PACKAGE_REQUIRED'
            adb('install','-t',str((a.delivery/row['apk']).resolve()));adb('install','-t',str((a.delivery/row['test_apk']).resolve()))
            report['functional'][variant]=instrumentation(row,variant+'-functional',['-e','notClass','com.jarvis.interfaceapp.ProcessRestartProbe,com.jarvis.interfaceapp.m51.M51FrameProbe'])
            report['restart'][variant]={}
            report['restart'][variant]['write']=instrumentation(row,variant+'-restart-write',['-e','class','com.jarvis.interfaceapp.ProcessRestartProbe','-e','processProbe','write'])
            adb('shell','am','force-stop',pkg)
            report['restart'][variant]['verify']=instrumentation(row,variant+'-restart-verify',['-e','class','com.jarvis.interfaceapp.ProcessRestartProbe','-e','processProbe','verify'])
            cold=adb('shell','am','start','-W','-n',pkg+'/com.jarvis.interfaceapp.MainActivity');(out/(variant+'-cold-start.txt')).write_text(cold)
            time.sleep(2)
            (out/(variant+'-portrait.png')).write_bytes(adb('exec-out','screencap','-p',binary=True))
            adb('shell','settings','put','system','user_rotation','1');time.sleep(2)
            (out/(variant+'-landscape.png')).write_bytes(adb('exec-out','screencap','-p',binary=True))
            adb('shell','settings','put','system','user_rotation','0');adb('shell','am','force-stop',pkg)
        # Same emulator, resolution, scene, animations and observer. Variants alternate; no model involved.
        for setting in ('window_animation_scale','transition_animation_scale','animator_duration_scale'):
            adb('shell','settings','put','global',setting,'1')
        report['animation_scales']={k:adb('shell','settings','get','global',k).strip() for k in ('window_animation_scale','transition_animation_scale','animator_duration_scale')}
        for mode,repeats in [('ANIMATED',2),('ECO',1)]:
            for repetition in range(repeats):
                for row in meta['apks']:
                    pkg=row['package'];identifier=f"{row['variant']}-{mode.lower()}-{repetition}"
                    adb('shell','am','force-stop',pkg)
                    instrumentation(row,identifier+'-probe',['-e','class','com.jarvis.interfaceapp.m51.M51FrameProbe','-e','benchMode',mode,'-e','benchId',identifier,'-e','benchMillis','20000'])
                    for suffix in ('.json','-gfxinfo.txt','-reset.txt','-memory.txt','.png'):
                        data=adb('exec-out','run-as',pkg,'cat','files/m51-evidence/'+identifier+suffix,binary=True)
                        (out/(identifier+suffix)).write_bytes(data)
                    subprocess.run([sys.executable,str(ROOT/'m51/summarize_frames.py'),str(out/(identifier+'.json')),'--output',str(out/(identifier+'-summary.json'))],check=True)
                    report['benchmarks'].append(identifier)
        report['status']='PASS_FUNCTIONAL_UI_BENCHMARKS_RECORDED'
    except Exception as e:
        report['status']='FAIL';report['error']=str(e);raise
    finally:
        crashes=adb('logcat','-d','-b','crash',check=False)
        (out/'crashes.txt').write_text(crashes)
        own_crash=any(('Process: '+row['package']+',') in crashes for row in meta['apks'])
        if own_crash:report.update(status='FAIL',error='LAB_APPLICATION_CRASH')
        (out/'device.json').write_text(json.dumps(report,indent=2)+'\n')
        (out/'SHA256SUMS').write_text(''.join(sha(p)+'  '+p.name+'\n' for p in sorted(out.iterdir()) if p.is_file() and p.name!='SHA256SUMS'))
        if own_crash:raise RuntimeError('LAB_APPLICATION_CRASH')
if __name__=='__main__':main()
