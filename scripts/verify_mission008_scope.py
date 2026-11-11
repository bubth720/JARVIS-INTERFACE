#!/usr/bin/env python3
"""Assert the protected 006 contracts are byte-for-byte unchanged; no migration."""
from pathlib import Path
import subprocess

BASE = 'ebd6c5fc873054d3f04bb2388d53e407dc73b7b7'
ROOT = Path(__file__).resolve().parents[1]
PROTECTED = [
    'app/src/main/AndroidManifest.xml', 'app/build.gradle.kts', 'gradle/libs.versions.toml',
    'app/src/main/java/com/jarvis/interfaceapp/MainActivity.kt',
    'app/src/main/java/com/jarvis/interfaceapp/JarvisApplication.kt',
    'app/src/main/java/com/jarvis/interfaceapp/ui/DashboardGrid.kt',
    'app/src/main/java/com/jarvis/interfaceapp/ui/DashboardController.kt',
    'app/src/main/java/com/jarvis/interfaceapp/avatar/AvatarContract.kt',
]
for directory in ('domain', 'storage'):
    PROTECTED += subprocess.check_output(['git', 'ls-tree', '-r', '--name-only', BASE,
        'app/src/main/java/com/jarvis/interfaceapp/' + directory], cwd=ROOT, text=True).splitlines()
for path in PROTECTED:
    expected = subprocess.check_output(['git', 'show', BASE + ':' + path], cwd=ROOT)
    assert (ROOT / path).read_bytes() == expected, 'Protected contract changed: ' + path
print(f'{len(PROTECTED)} protected files identical to base 006. DataStore JSON v1 and manifest preserved.')
