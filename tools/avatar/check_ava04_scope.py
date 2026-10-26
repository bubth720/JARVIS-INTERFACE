#!/usr/bin/env python3
"""Preserve AVA-03 and exclude private art from the public source/APK."""
from pathlib import Path
import subprocess
import zipfile
import hashlib
root=Path(__file__).resolve().parents[2]
base='1d0a89f3d92cba64122db08cc7b326239f85c08d'
allowed={'app/src/main/java/com/jarvis/interfaceapp/workspace/WorkspaceScreen.kt','app/src/main/java/com/jarvis/interfaceapp/ui/chat/core/CoreConversationClientAdapter.kt','app/src/test/java/com/jarvis/interfaceapp/ui/chat/core/CoreConversationClientTest.kt'}
files=subprocess.check_output(['git','ls-tree','-r','--name-only',base,'--','app','gradle','settings.gradle.kts','build.gradle.kts'],cwd=root,text=True).splitlines()
for f in files:
    if f not in allowed:
        assert (root/f).read_bytes()==subprocess.check_output(['git','show',base+':'+f],cwd=root),f
tracked=subprocess.check_output(['git','ls-files'],cwd=root,text=True).splitlines()
assert not any('athena-private/' in f or Path(f).name=='ATHENA_REFERENCE_OFFICIELLE.png' for f in tracked)
assert not list((root/'app/src/main/assets').glob('**/portrait-*.png'))
print('AVA-04 scope PASS: frozen avatar engines/V1 except explicit FUSION-02 UI/client wiring; no private art tracked/bundled')
