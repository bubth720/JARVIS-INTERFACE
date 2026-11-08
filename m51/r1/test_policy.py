import unittest
from m51.r1.policy import instrumentation_result,system_incident
class EvidencePolicy(unittest.TestCase):
    def test_crashed_process_never_passes_with_zero_exit(self):
        r=instrumentation_result('INSTRUMENTATION_RESULT: shortMsg=Process crashed.\nINSTRUMENTATION_CODE: 0\n')
        self.assertTrue(r['critical']);self.assertFalse(r['completed']);self.assertIsNone(r['reported_total'])
    def test_partial_suite_is_not_success(self):
        r=instrumentation_result('INSTRUMENTATION_STATUS_CODE: 1\nINSTRUMENTATION_STATUS_CODE: 0\n')
        self.assertEqual((1,1,False),(r['started'],r['passed'],r['completed']))
    def test_anr_in_original_api35_window_blocks_admission(self):
        self.assertEqual('SYSTEM_ANR_WINDOW',system_incident('Application Not Responding: com.android.systemui','',''))
    def test_system_crash_and_event_are_not_ignored(self):
        self.assertIsNotNone(system_incident('','am_anr: [0,577,com.android.systemui,0,Input dispatching timed out]',''))
        self.assertIsNotNone(system_incident('','','Process: com.android.launcher3, PID: 4'))
    def test_real_ok_retains_exact_count(self):
        self.assertEqual(18,instrumentation_result('OK (18 tests)\n')['reported_total'])
