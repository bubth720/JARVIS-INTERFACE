#!/usr/bin/env python3
"""Disposable emulator only: real 006→008 APK update + byte preservation + volatile input."""
import argparse,hashlib,json,re,subprocess,time,xml.etree.ElementTree as ET
from pathlib import Path

p=argparse.ArgumentParser()
p.add_argument('--serial',required=True);p.add_argument('--base-apk',required=True);p.add_argument('--apk',required=True)
p.add_argument('--allow-test-fixture',action='store_true');p.add_argument('--output',default='evidence008/upgrade.json')
a=p.parse_args()
assert a.serial.startswith('emulator-') and a.allow_test_fixture,'Disposable emulator and explicit test-fixture flag required'
PACKAGE='com.jarvis.interfaceapp'
def adb(*args,input=None):
 return subprocess.run(['adb','-s',a.serial,*args],input=input,stdout=subprocess.PIPE,stderr=subprocess.PIPE,check=True).stdout
def stop():adb('shell','am','force-stop',PACKAGE)
def launch():adb('shell','am','start','-W','-n',PACKAGE+'/.MainActivity')
def xml():
 adb('shell','uiautomator','dump','/sdcard/mission008-window.xml')
 return adb('exec-out','cat','/sdcard/mission008-window.xml').decode()
def node(tag,timeout=30):
 end=time.monotonic()+timeout
 while time.monotonic()<end:
  text=xml()
  for n in ET.fromstring(text).iter('node'):
   if n.attrib.get('resource-id','').split(':id/')[-1]==tag:return n
  time.sleep(.25)
 raise AssertionError('UI control not found: '+tag)
def tap(tag):
 n=node(tag);x1,y1,x2,y2=map(int,re.findall(r'\d+',n.attrib['bounds']))
 assert x2>x1 and y2>y1
 adb('shell','input','tap',str((x1+x2)//2),str((y1+y2)//2))
def stored():return adb('exec-out','run-as',PACKAGE,'cat','files/datastore/dashboard.json')
def sha(data):return hashlib.sha256(data).hexdigest()

repo=Path(__file__).resolve().parents[1]
fixture=json.loads((repo/'docs/validation/mission-005-default-layout.json').read_text())
fixture['mission008_test_extension']='DEMONSTRATION FIXTURE ONLY'
fixture['profiles']['compact'][0].update(y=1,height=3,locked=True)
fixture['profiles']['wide'][0].update(x=1,y=1,width=5,height=3,locked=True)
raw=json.dumps(fixture,separators=(',',':')).encode()
stop();adb('install','-r',a.base_apk);adb('shell','run-as',PACKAGE,'mkdir','-p','files/datastore')
adb('shell','run-as',PACKAGE,'tee','files/datastore/dashboard.json',input=raw)
launch();node('open-settings');assert 'Disposition enregistrée.' in xml(),'Base did not accept the supported fixture'
assert stored()==raw,'Base 006 rewrote fixture unexpectedly'
stop();adb('install','-r',a.apk);launch();node('open-settings')
assert 'cockpit-v2' not in xml(),'V2 must not open by default after upgrade'
assert 'Disposition enregistrée.' in xml(),'Upgrade did not restore the valid layout'
assert stored()==raw,'Upgrade changed layout document'
tap('open-settings');tap('open-cockpit-v2');node('v2-draft')
phrase='MISSION008_EPHEMERAL_TEST'
# Use Android keyboard navigation, not accessibility-bounds pixel coordinates.
for attempt in range(10):
 text=xml()
 if any(n.attrib.get('focused')=='true' and (n.attrib.get('class')=='android.widget.EditText' or n.attrib.get('resource-id','').split(':id/')[-1]=='v2-draft') for n in ET.fromstring(text).iter('node')):break
 adb('shell','input','keyevent','KEYCODE_TAB')
 time.sleep(.25)
else:
 failure=Path(a.output).parent/'upgrade-focus-failure.xml';failure.parent.mkdir(parents=True,exist_ok=True);failure.write_text(text)
 raise AssertionError('Local editor did not gain focus; see '+str(failure))
time.sleep(.5)
adb('shell','input','text',phrase)
end=time.monotonic()+15
while time.monotonic()<end:
 text=xml()
 if phrase in text:break
 time.sleep(.25)
else:
 failure=Path(a.output).parent/'upgrade-window-failure.xml';failure.parent.mkdir(parents=True,exist_ok=True);failure.write_text(text)
 raise AssertionError('Local text not observed after Android input; see '+str(failure))
assert stored()==raw,'Ephemeral input changed DataStore'
pid_before=adb('shell','pidof',PACKAGE).decode().strip()
stop();assert not adb('shell','sh','-c','"pidof '+PACKAGE+' || true"').decode().strip()
launch();node('open-settings');pid_after=adb('shell','pidof',PACKAGE).decode().strip()
assert pid_after and pid_before!=pid_after,'Expected actual process restart'
assert 'cockpit-v2' not in xml(),'Experimental mode survived process death'
tap('open-settings');tap('open-cockpit-v2');node('v2-draft')
assert phrase not in xml(),'Input was unexpectedly restored after process death'
assert stored()==raw,'Process restart changed layout document'
result={'base':'ebd6c5fc873054d3f04bb2388d53e407dc73b7b7','source':subprocess.check_output(['git','rev-parse','HEAD'],cwd=repo,text=True).strip(),'serial':a.serial,'same_key_apk_update':True,'layout_bytes_unchanged':True,'layout_sha256':sha(raw),'unknown_extension_preserved':True,'pid_before':pid_before,'pid_after':pid_after,'mode_default_after_restart':'historical-dashboard','ephemeral_input_not_restored':True,'app_apk_sha256':sha(Path(a.apk).read_bytes()),'base_apk_sha256':sha(Path(a.base_apk).read_bytes())}
out=Path(a.output);out.parent.mkdir(parents=True,exist_ok=True);out.write_text(json.dumps(result,indent=2)+'\n');print(json.dumps(result,indent=2))
stop();adb('shell','rm','-f','/sdcard/mission008-window.xml')
