import importlib.util
from pathlib import Path
import tempfile
import unittest

spec=importlib.util.spec_from_file_location('summary',Path(__file__).parents[1]/'summarize_prefusion_lot3.py')
summary=importlib.util.module_from_spec(spec);spec.loader.exec_module(summary)

class PublicSummaryTest(unittest.TestCase):
    def read(self,xml):
        with tempfile.TemporaryDirectory() as folder:
            p=Path(folder);(p/'TEST-public.xml').write_text(xml)
            return summary.collect(p)
    def test_missing_is_not_a_pass(self):
        with tempfile.TemporaryDirectory() as folder:
            self.assertEqual(summary.collect(Path(folder))['reports'],0)
    def test_success_counts(self):
        r=self.read('<testsuite tests="22" failures="0" errors="0" skipped="0"/>')
        self.assertEqual(r['tests'],22);self.assertEqual(r['reports'],1)
    def test_failure_preserved_and_bounded(self):
        r=self.read('<testsuite tests="1" failures="1"><testcase classname="com.jarvis.interfaceapp.prefusion.Test" name="negative"><failure message="expected false">secret stack excluded</failure></testcase></testsuite>')
        self.assertEqual(r['failures'],1);self.assertIn('expected false',r['details'][0]);self.assertNotIn('secret stack',str(r))
    def test_external_classes_not_published(self):
        r=self.read('<testsuite tests="1" errors="1"><testcase classname="other.package"><error message="not public"/></testcase></testsuite>')
        self.assertEqual(r['errors'],1);self.assertEqual(r['details'],[])
    def test_malformed_is_not_a_pass(self):
        self.assertEqual(self.read('<broken')['reports'],0)
    def test_negative_counts_rejected(self):
        self.assertEqual(self.read('<testsuite tests="-1"/>')['reports'],0)

if __name__=='__main__':unittest.main()
