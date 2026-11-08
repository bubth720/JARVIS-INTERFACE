"""Read-only accelerator capability. Never changes host protection or boots Android."""
import json,os,platform,subprocess
from pathlib import Path

def main():
    out=Path('build/m51-r1-host');out.mkdir(parents=True,exist_ok=True)
    emulator=Path(os.environ.get('ANDROID_HOME',os.environ.get('ANDROID_SDK_ROOT','')))/'emulator/emulator'
    r=subprocess.run([str(emulator),'-accel-check'],capture_output=True,text=True,timeout=30)
    (out/'accel-check.txt').write_text(r.stdout+r.stderr)
    result={'machine':platform.machine(),'system':platform.system(),'accelerator_exit':r.returncode,
        'ready':r.returncode==0 and platform.machine()=='x86_64','protections_changed':False,'android_booted':False,
        'status':'ACCELERATOR_AVAILABLE' if r.returncode==0 else 'BLOCKED_ACCELERATOR'}
    (out/'host.json').write_text(json.dumps(result,indent=2)+'\n');print(json.dumps(result))
    if os.environ.get('GITHUB_OUTPUT'):
        with open(os.environ['GITHUB_OUTPUT'],'a') as f:f.write('ready='+str(result['ready']).lower()+'\n')
if __name__=='__main__':main()
