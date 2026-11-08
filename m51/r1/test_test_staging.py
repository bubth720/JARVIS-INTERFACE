import unittest,subprocess
from m51.r1.prepare_tests import ROOT,BASE,instrument,INSERTIONS
class ObservationOnly(unittest.TestCase):
    def test_original_text_recovered_by_removing_only_two_observations(self):
        original=subprocess.check_output(['git','show',BASE+':app/src/androidTest/java/com/jarvis/interfaceapp/DocumentPickerContractTest.kt'],cwd=ROOT).decode()
        transformed=instrument(original)
        for _,extra in INSERTIONS:transformed=transformed.replace(extra,'')
        self.assertEqual(original,transformed)
    def test_unknown_preimage_is_refused(self):
        with self.assertRaises(AssertionError):instrument('unknown test')
