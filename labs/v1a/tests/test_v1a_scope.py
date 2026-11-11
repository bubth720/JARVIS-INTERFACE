import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

LAB = Path(__file__).resolve().parents[1]
ROOT = LAB.parents[1]
sys.path.insert(0, str(LAB / 'tools'))
import check_scope  # noqa: E402


class LockTest(unittest.TestCase):
    def test_lock_pins_nine_kotlin_sources_and_exact_llm_engine(self):
        lock = json.loads((LAB / 'sources.json').read_text())
        mods = {m['id']: m for m in lock['modules']}
        self.assertEqual(set(mods), {'CORE', 'INTERFACE', 'LLM'})
        self.assertEqual(len(mods['CORE']['files']) + len(mods['INTERFACE']['files']), 9)
        core = {f['path'].rsplit('/', 1)[1] for f in mods['CORE']['files']}
        self.assertTrue({'SessionCoordinator.kt', 'LocalLanguageModel.kt', 'InferenceContract.kt'} <= core)
        llm = {f['path'] for f in mods['LLM']['native']}
        self.assertIn('app/src/main/cpp/bridge.cpp', llm)
        self.assertIn('a02/native/a02_bridge.cpp', llm)
        for m in mods.values():
            self.assertRegex(m['sha'], '^[0-9a-f]{40}$')

    def test_no_synthetic_reply_in_production_sources(self):
        for path in (LAB / 'app/src/main').rglob('*.kt'):
            text = path.read_text()
            self.assertNotIn('SYNTHETIC', text, path)
            self.assertNotIn('LlamaLocalEngine', text, path)


class ScopeTest(unittest.TestCase):
    def worktree(self):
        tmp = tempfile.TemporaryDirectory()
        wt = Path(tmp.name) / 'wt'
        subprocess.run(['git', '-C', str(ROOT), 'worktree', 'add', '--detach', str(wt), 'HEAD'], check=True, stdout=subprocess.DEVNULL)
        self.addCleanup(lambda: (subprocess.run(['git', '-C', str(ROOT), 'worktree', 'remove', '--force', str(wt)], check=False), tmp.cleanup()))
        return wt

    def test_head_passes(self):
        check_scope.check(self.worktree())

    def test_file_outside_lab_is_refused(self):
        wt = self.worktree()
        (wt / 'stray.txt').write_text('x')
        with self.assertRaises(AssertionError):
            check_scope.check(wt)

    def test_historical_change_is_refused(self):
        wt = self.worktree()
        target = wt / 'labs/m50-s1/README.md'
        target.write_text(target.read_text() + 'mutation\n')
        with self.assertRaises(AssertionError):
            check_scope.check(wt)


if __name__ == '__main__':
    unittest.main()
