import importlib.util
from pathlib import Path
import subprocess
import tempfile
import unittest

TOOLS = Path(__file__).resolve().parents[1] / 'tools'
def load(name):
    spec = importlib.util.spec_from_file_location(name, TOOLS / (name + '.py'))
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module

scope = load('check_scope')
source = load('prepare_sources')


class HistoricalFence(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name)
        self.git('init', '-q')
        self.git('config', 'user.name', 'Synthetic fixture')
        self.git('config', 'user.email', 'fixture@example.invalid')
        (self.root / 'historical.kt').write_text('original')
        self.git('add', '.')
        self.git('commit', '-qm', 'fixture baseline')
        self.base = self.git('rev-parse', 'HEAD').strip()
        self.lab = self.root / 'labs/m50-s1'
        self.lab.mkdir(parents=True)
        (self.lab / 'new.kt').write_text('laboratory')

    def tearDown(self):
        self.temp.cleanup()

    def git(self, *args):
        return subprocess.check_output(['git', '-C', str(self.root), *args], text=True)

    def test_isolated_addition_allowed(self):
        scope.check(self.root, self.base)

    def test_historical_content_mutation_refused(self):
        (self.root / 'historical.kt').write_text('changed')
        with self.assertRaises(AssertionError): scope.check(self.root, self.base)

    def test_historical_deletion_refused(self):
        (self.root / 'historical.kt').unlink()
        with self.assertRaises(AssertionError): scope.check(self.root, self.base)

    def test_mode_change_refused(self):
        (self.root / 'historical.kt').chmod(0o755)
        with self.assertRaises(AssertionError): scope.check(self.root, self.base)

    def test_declared_crlf_checkout_is_not_a_historical_mutation(self):
        (self.root / '.gitattributes').write_text('*.bat text eol=crlf\n')
        (self.root / 'runner.bat').write_bytes(b'@echo fixture\r\n')
        self.git('add', '.gitattributes', 'runner.bat')
        self.git('commit', '-qm', 'declared checkout line endings')
        scope.check(self.root, self.git('rev-parse', 'HEAD').strip())

    def test_unreviewed_workflow_refused(self):
        dest = self.root / '.github/workflows/other.yml'
        dest.parent.mkdir(parents=True)
        dest.write_text('unreviewed')
        with self.assertRaises(AssertionError): scope.check(self.root, self.base)

    def test_new_runtime_outside_lab_refused(self):
        (self.root / 'runtime.kt').write_text('unreviewed')
        with self.assertRaises(AssertionError): scope.check(self.root, self.base)

    def test_new_symlink_refused(self):
        (self.lab / 'link.kt').symlink_to(self.root / 'historical.kt')
        with self.assertRaises(AssertionError): scope.check(self.root, self.base)

    def test_source_path_traversal_and_other_files_refused(self):
        for path in ('app/src/main/java/../../escape.kt', '/app/src/main/java/A.kt', 'app/src/main/java/A.java', 'app/src/main/java/x\\A.kt'):
            with self.subTest(path=path), self.assertRaises(ValueError): source.relative_source(path)

    def test_extraction_cannot_write_into_historical_checkout(self):
        with self.assertRaises(ValueError): source.extract(self.root, self.root, self.root / 'snapshot')

    def test_extraction_cannot_overwrite_output(self):
        with tempfile.TemporaryDirectory() as output:
            with self.assertRaises(ValueError): source.extract(self.root, self.root, Path(output))


if __name__ == '__main__': unittest.main()
