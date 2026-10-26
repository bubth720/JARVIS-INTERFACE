#!/usr/bin/env python3
"""FUSION-02 owner-local invariants; no networking or private-media reads."""
import hashlib,json,subprocess,sys
from pathlib import Path
import xml.etree.ElementTree as ET
base='1d0a89f3d92cba64122db08cc7b326239f85c08d'
for prefix in ['app/src/main/java/com/jarvis/interfaceapp/avatar','app/src/main/java/com/jarvis/interfaceapp/storage','app/src/main/java/com/jarvis/interfaceapp/domain','app/src/main/assets','app/src/main/AndroidManifest.xml','app/build.gradle.kts','gradle']:
 files=subprocess.check_output(['git','ls-tree','-r','--name-only',base,'--',prefix],text=True).splitlines()
 for file in files:assert Path(file).read_bytes()==subprocess.check_output(['git','show',base+':'+file]),file
provenance=json.loads(Path('docs/fusion-02/PROVENANCE.json').read_text())
assert provenance['base']==base and provenance['consumed']==[]
assert provenance['runtime']['ipc']=='DISABLED' and not provenance['runtime']['privateArtPublished']
assert provenance['dependencies']['versionsTomlSha256']==hashlib.sha256(Path('gradle/libs.versions.toml').read_bytes()).hexdigest()
for file in Path('app/src/main/java/com/jarvis/interfaceapp/fusion').glob('*.kt'):
 s=file.read_text()
 assert not any(call in s for call in ['bindService(', 'openOutputStream(', 'openInputStream(', 'contentResolver.', 'Runtime.getRuntime(', 'ProcessBuilder(']),file
if '--results' in sys.argv:
 reports=list(Path('app/build/test-results/testDebugUnitTest').glob('TEST-*.xml'));assert reports
 totals={k:sum(int(ET.parse(r).getroot().get(k,0)) for r in reports) for k in ['tests','failures','errors','skipped']}
 assert totals['tests']>=191 and all(totals[k]==0 for k in ['failures','errors','skipped']),totals
 print(totals)
print('FUSION-02 PASS: owner-local, unchanged avatar/storage/assets/dependencies/manifest; no active transport or file effects')
