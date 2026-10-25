#!/usr/bin/env python3
"""Preserve AVA-03 and exclude private art from the public source/APK."""
from pathlib import Path
import subprocess
import zipfile
import hashlib
root=Path(__file__).resolve().parents[2]
base='0869b3959d3636deab3c938e6cb98b7e555f19af'
allowed={'app/src/main/java/com/jarvis/interfaceapp/avatar/AvatarDemo.kt','app/src/main/java/com/jarvis/interfaceapp/ui/JarvisTheme.kt', 'app/src/main/java/com/jarvis/interfaceapp/ui/JarvisApp.kt', 'app/src/main/java/com/jarvis/interfaceapp/ui/chat/ChatController.kt', 'app/src/main/java/com/jarvis/interfaceapp/ui/HomeViewModel.kt', 'app/src/main/java/com/jarvis/interfaceapp/ui/chat/ChatScreen.kt', 'app/src/main/java/com/jarvis/interfaceapp/JarvisApplication.kt', 'app/src/main/java/com/jarvis/interfaceapp/MainActivity.kt', 'app/src/main/java/com/jarvis/interfaceapp/ui/DashboardGrid.kt', 'app/src/main/java/com/jarvis/interfaceapp/ui/chat/ChatViewModel.kt', 'app/src/main/java/com/jarvis/interfaceapp/ui/DashboardController.kt'}
files=subprocess.check_output(['git','ls-tree','-r','--name-only',base,'--','app','gradle','settings.gradle.kts','build.gradle.kts'],cwd=root,text=True).splitlines()
for f in files:
    if f not in allowed:
        assert (root/f).read_bytes()==subprocess.check_output(['git','show',base+':'+f],cwd=root),f
tracked=subprocess.check_output(['git','ls-files'],cwd=root,text=True).splitlines()
assert not any('athena-private/' in f or Path(f).name=='ATHENA_REFERENCE_OFFICIELLE.png' for f in tracked)
assert not list((root/'app/src/main/assets').glob('**/portrait-*.png'))
print('AVA-04 scope PASS: frozen avatar engines/V1 except explicit FINAL-01 UI wiring; no private art tracked/bundled')
