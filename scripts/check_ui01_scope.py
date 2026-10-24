#!/usr/bin/env python3
"""Protect storage/avatar/permissions and pin CORE metadata test provenance. No network or source writes."""
from pathlib import Path
import subprocess,json,hashlib,sys,zipfile
BASE='4842ef1f47ac2a1efc29e77408dbdb3ec42768bb'
paths=subprocess.check_output(['git','ls-tree','-r','--name-only',BASE,'--','app','gradle','settings.gradle.kts','build.gradle.kts'],text=True).splitlines()
# UI-03 adds a separate diagnostic entry; every previous avatar/settings/contract file stays pinned.
allowed={'app/src/main/java/com/jarvis/interfaceapp/ui/chat/ChatScreen.kt'}
for name in paths:
    if name not in allowed:assert Path(name).read_bytes()==subprocess.check_output(['git','show',BASE+':'+name]),name
r=json.loads(Path('docs/intensive-2026-10-22/CORE02-REFERENCE.json').read_text())
f=Path('fixtures/core02-reference/src/com/stark/jarvislocal/events/CoreEventBus.kt')
assert r['commit']=='e86107b8213b0aeaf9d22025cf6bef91a8e544a5'
assert hashlib.sha256(f.read_bytes()).hexdigest()==r['sha256']
assert 'sourceSets.getByName("test").java.srcDir' in Path('app/build.gradle.kts').read_text()
if '--apk' in sys.argv:
    with zipfile.ZipFile('app/build/outputs/apk/debug/app-debug.apk') as z:
        for n in z.namelist():
            if n.endswith('.dex'):assert b'com/stark/jarvislocal/events/CoreEventBus' not in z.read(n),'Test-only CORE source leaked into app'
print(json.dumps({'base':BASE,'protected_files':len(paths)-len(allowed),'dataStoreVersion':1,'coreSource':'PINNED_TEST_ONLY','ipc':'ABSENT','model':'ABSENT'}))
