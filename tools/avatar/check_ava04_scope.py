#!/usr/bin/env python3
"""Preserve AVA-03 and exclude private art from the public source/APK."""
from pathlib import Path
import subprocess
import zipfile
import hashlib
root=Path(__file__).resolve().parents[2]
base='4842ef1f47ac2a1efc29e77408dbdb3ec42768bb'
allowed={'app/src/main/java/com/jarvis/interfaceapp/ui/chat/ChatScreen.kt'}
files=subprocess.check_output(['git','ls-tree','-r','--name-only',base,'--','app','gradle','settings.gradle.kts','build.gradle.kts'],cwd=root,text=True).splitlines()
for f in files:
    if f not in allowed:
        assert (root/f).read_bytes()==subprocess.check_output(['git','show',base+':'+f],cwd=root),f
tracked=subprocess.check_output(['git','ls-files'],cwd=root,text=True).splitlines()
assert not any('athena-private/' in f or Path(f).name=='ATHENA_REFERENCE_OFFICIELLE.png' for f in tracked)
assert not list((root/'app/src/main/assets').glob('**/portrait-*.png'))
print('AVA-04 scope PASS: frozen AVA-04/UI-02 except UI-03 diagnostic entry; no private art tracked/bundled')
