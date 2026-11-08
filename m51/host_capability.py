"""Inspect current KVM rights without changing device permissions, groups, modules or protections."""
import fcntl,json,os,platform,stat
from pathlib import Path
p=Path('/dev/kvm');accessible=p.exists() and stat.S_ISCHR(p.stat().st_mode) and os.access(p,os.R_OK|os.W_OK)
api=None;error=None
if accessible:
    try:
        fd=os.open(p,os.O_RDWR|os.O_CLOEXEC)
        try:api=fcntl.ioctl(fd,0xAE00,0)
        finally:os.close(fd)
    except OSError as e:error=e.errno
ready=accessible and api==12 and platform.machine()=='x86_64'
out=Path('build/m51-host');out.mkdir(parents=True,exist_ok=True)
r={'arch':platform.machine(),'kvm_present':p.exists(),'kvm_accessible':accessible,'kvm_api':api,'errno':error,'status':'READY_FOR_UI_EMULATOR' if ready else 'BLOCKED_HOST_CAPABILITY','protections_changed':False,'android':'NOT_RUN','software_emulation_attempt_permitted':platform.machine()=='x86_64'}
(out/'capability.json').write_text(json.dumps(r,indent=2)+'\n');print(json.dumps(r))
if os.environ.get('GITHUB_OUTPUT'):
    with open(os.environ['GITHUB_OUTPUT'],'a') as f:f.write('ready='+str(ready).lower()+'\n'+'software='+str(platform.machine()=='x86_64').lower()+'\n')
