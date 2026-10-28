#!/usr/bin/env python3
"""Owner-local preservation and fresh public emulator evidence. No media or network reads."""
import argparse
import hashlib
import json
from pathlib import Path
import subprocess
import xml.etree.ElementTree as ET

ROOT=Path(__file__).resolve().parents[1]
BASE='c14eebdbce8b2fda024043d6abd1e84bc719f113'
PUBLIC_CLASSES={
 'com.jarvis.interfaceapp.prefusion.PrefusionConsumerBoundaryTest':11,
 'com.jarvis.interfaceapp.prefusion.PrefusionOffUiTest':2,
 'com.jarvis.interfaceapp.ui.chat.core.CoreConnectionDiagnosticsTest':2,
 'com.jarvis.interfaceapp.ui.chat.ChatInteractionTest':2,
 'com.jarvis.interfaceapp.fusion.FusionInteractionTest':5,
}

def main():
 p=argparse.ArgumentParser(description=__doc__)
 p.add_argument('--instrumented',type=Path);p.add_argument('--api',type=int,choices=[26,35]);p.add_argument('--output',type=Path)
 a=p.parse_args()
 # Existing production/test sources are preserved byte-for-byte. New tests are additive.
 paths=subprocess.check_output(['git','-C',str(ROOT),'ls-tree','-r','--name-only',BASE,'--','app','gradle','settings.gradle.kts','build.gradle.kts'],text=True).splitlines()
 for name in paths:
  assert (ROOT/name).read_bytes()==subprocess.check_output(['git','-C',str(ROOT),'show',BASE+':'+name]),name
 result={'base_commit':BASE,'preserved_files':len(paths),'production_transport':'OFF',
         'android_identity_proof':False,'personal_device_tested':False}
 if a.instrumented:
  assert a.api is not None, 'Exact emulator API required'
  reports=list(a.instrumented.rglob('TEST-*.xml'));assert reports,'No executed Android XML evidence'
  totals={k:0 for k in ['tests','failures','errors','skipped']};classes={}
  for f in reports:
   root=ET.parse(f).getroot()
   for k in totals:totals[k]+=int(root.get(k,0))
   for case in root.findall('testcase'):
    name=case.get('classname');classes[name]=classes.get(name,0)+1
  assert set(classes)==set(PUBLIC_CLASSES),classes
  assert all(classes[k]>=v for k,v in PUBLIC_CLASSES.items()),classes
  assert totals['tests']>=22 and all(totals[k]==0 for k in ['failures','errors','skipped']),totals
  result.update({'api':a.api,'kind':'real_android_emulator_instrumentation','counts':totals,'classes':classes,
                 'private_art_tests':'NOT_RUN: private pack not authorized for public CI',
                 'sha256':{str(f.relative_to(a.instrumented)):hashlib.sha256(f.read_bytes()).hexdigest() for f in reports}})
 if a.output:
  a.output.parent.mkdir(parents=True,exist_ok=True);a.output.write_text(json.dumps(result,indent=2)+'\n')
 print(json.dumps(result,sort_keys=True))

if __name__=='__main__':main()
