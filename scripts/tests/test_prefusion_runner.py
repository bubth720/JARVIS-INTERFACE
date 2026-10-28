"""Host-only runner regression: simulated ADB/Gradle, never Android proof."""
import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest


class RunnerLifecycleTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        scripts = self.root / 'scripts'
        scripts.mkdir()
        shutil.copyfile(Path(__file__).parents[1] / 'run_prefusion_lot3_emulator.sh',
                        scripts / 'run_prefusion_lot3_emulator.sh')
        # This substitutes only the fixture transcript validator. It does not
        # fabricate XML or present this shell harness as Android execution.
        (scripts / 'check_prefusion_lot3.py').write_text('')
        self.state = self.root / 'installed.json'
        self.state.write_text('[]')
        self.calls = self.root / 'adb-calls.jsonl'
        bindir = self.root / 'bin'
        bindir.mkdir()
        self.env = dict(os.environ, PATH=str(bindir) + os.pathsep + os.environ['PATH'],
                        FIXTURE_ROOT=str(self.root), FIXTURE_SERIAL='emulator-5554')
        for relative in ['app/build/outputs/apk/debug/app-debug.apk',
                         'app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk']:
            p = self.root / relative
            p.parent.mkdir(parents=True, exist_ok=True)
            p.write_bytes(b'host-only fixture, not an APK')
        gradle = self.root / 'gradlew'
        gradle.write_text('''#!/usr/bin/env python3
import json,os
from pathlib import Path
r=Path(os.environ['FIXTURE_ROOT'])
# Model connectedAndroidTest package cleanup, which originally broke restart.
(r/'installed.json').write_text('[]')
(r/'app/build/outputs/androidTest-results/connected').mkdir(parents=True)
''')
        gradle.chmod(0o755)
        adb = bindir / 'adb'
        adb.write_text(r'''#!/usr/bin/env python3
import json,os,sys
from pathlib import Path
r=Path(os.environ['FIXTURE_ROOT']);a=sys.argv[1:]
with (r/'adb-calls.jsonl').open('a') as f:f.write(json.dumps(a)+'\n')
if a==['get-serialno']:
 print(os.environ['FIXTURE_SERIAL']);sys.exit(0)
assert a[:2]==['-s','emulator-5554'],a
a=a[2:];state=r/'installed.json';installed=json.loads(state.read_text())
if a==['shell','getprop','ro.build.version.sdk']:
 print(os.environ.get('FIXTURE_API','26'))
elif a[:1]==['install']:
 if os.environ.get('FIXTURE_INSTALL_FAIL'):sys.exit(17)
 installed.append('com.jarvis.interfaceapp.test' if 'androidTest' in a[-1] else 'com.jarvis.interfaceapp')
 state.write_text(json.dumps(installed));print('Success')
elif a[:3]==['shell','pm','path']:
 if a[3] in installed:print('package:/fixture/'+a[3]+'.apk')
elif a==['shell','pm','list','instrumentation']:
 if len(installed)==2:print('instrumentation:com.jarvis.interfaceapp.test/androidx.test.runner.AndroidJUnitRunner (target=com.jarvis.interfaceapp)')
elif a[:3]==['shell','am','force-stop']:pass
elif a[:3]==['shell','am','start']:
 assert len(installed)==2,'Target/test APK missing after connectedAndroidTest cleanup'
elif a[:3]==['shell','am','instrument']:
 assert len(installed)==2;print('OK (2 tests)')
elif a[:2]==['shell','run-as']:sys.stdout.buffer.write(b'fixture bytes; not a PNG capture')
else:raise AssertionError(a)
''')
        adb.chmod(0o755)

    def run_fixture(self, **env):
        result = subprocess.run(['bash', 'scripts/run_prefusion_lot3_emulator.sh', '26'],
                                cwd=self.root, env=dict(self.env, **env), capture_output=True, text=True)
        stage = json.loads((self.root / 'app/build/reports/prefusion-lot3/api-26/stage.json').read_text())
        calls = [json.loads(line) for line in self.calls.read_text().splitlines()]
        return result, stage, calls

    def test_personal_device_refused_before_install(self):
        result, stage, calls = self.run_fixture(FIXTURE_SERIAL='physical-fixture')
        self.assertEqual(result.returncode, 2)
        self.assertEqual(stage, {'stage': 'validate_emulator', 'exit_code': 2})
        self.assertEqual(calls, [['get-serialno']])

    def test_wrong_api_refused_before_install(self):
        result, stage, calls = self.run_fixture(FIXTURE_API='35')
        self.assertNotEqual(result.returncode, 0)
        self.assertEqual(stage['stage'], 'validate_emulator')
        self.assertFalse(any('install' in call for call in calls))

    def test_connected_cleanup_is_followed_by_explicit_pinned_reinstall(self):
        result, stage, calls = self.run_fixture()
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(stage, {'stage': 'complete', 'exit_code': 0})
        installed = [call for call in calls if len(call) > 2 and call[2] == 'install']
        self.assertEqual(len(installed), 2)
        self.assertTrue(all(call[:2] == ['-s', 'emulator-5554'] for call in calls[1:]))

    def test_failed_install_preserves_failure_and_never_starts_app(self):
        result, stage, calls = self.run_fixture(FIXTURE_INSTALL_FAIL='1')
        self.assertEqual(result.returncode, 17)
        self.assertEqual(stage, {'stage': 'prepare_restart', 'exit_code': 17})
        self.assertFalse(any('start' in call for call in calls))
