import unittest
from m51.reuse_r1plus.policy import (
    CAMPAIGN, WifiObservationError, campaign_identity, classify, wait_for_wifi_disabled,
)


class Clock:
    def __init__(self): self.now = 0.0
    def time(self): return self.now
    def sleep(self, duration): self.now += duration


class WifiTests(unittest.TestCase):
    def observe(self, values, cost=0):
        clock = Clock()
        values = iter(values)
        def read(remaining):
            self.assertLessEqual(remaining, 10)
            self.assertGreater(remaining, 0)
            clock.now += cost
            value = next(values)
            if isinstance(value, Exception): raise value
            return value
        return wait_for_wifi_disabled(read, clock=clock.time, sleep=clock.sleep)

    def test_already_zero_records_actual_value(self):
        result = self.observe(['0\n'])
        self.assertEqual('0', result['final_value'])
        self.assertEqual([{'seconds': 0.0, 'value': '0'}], result['samples'])

    def test_one_then_zero_waits_without_writing_settings(self):
        result = self.observe(['1', '1', '0'])
        self.assertEqual(['1', '1', '0'], [s['value'] for s in result['samples']])
        self.assertEqual(.5, result['elapsed_seconds'])

    def test_always_one_expires_at_original_ten_second_budget(self):
        with self.assertRaises(WifiObservationError) as context:
            self.observe(['1']*100)
        evidence = context.exception.evidence
        self.assertEqual('WIFI_STABILIZATION_DEADLINE', evidence['reason'])
        self.assertEqual(10, evidence['elapsed_seconds'])
        self.assertEqual(40, len(evidence['samples']))

    def test_null_empty_and_unknown_values_never_admit(self):
        for value in ['null', '', '2', 'permission denied']:
            with self.subTest(value=value), self.assertRaises(WifiObservationError) as context:
                self.observe([value])
            self.assertEqual('WIFI_UNKNOWN_OBSERVATION', context.exception.evidence['reason'])

    def test_adb_error_is_preserved_without_exposing_exception_text(self):
        with self.assertRaises(WifiObservationError) as context:
            self.observe([OSError('/private/path')])
        self.assertEqual('WIFI_READ_FAILED', context.exception.evidence['reason'])
        self.assertNotIn('/private/path', str(context.exception.evidence))

    def test_late_zero_cannot_turn_expiration_into_pass(self):
        with self.assertRaises(WifiObservationError) as context:
            self.observe(['0'], cost=10.01)
        self.assertEqual('WIFI_LATE_OBSERVATION', context.exception.evidence['reason'])


class IdentityTests(unittest.TestCase):
    def setUp(self):
        self.sha = '1'*40
        self.env = {'GITHUB_SHA': self.sha, 'GITHUB_RUN_ID': '123', 'GITHUB_RUN_ATTEMPT': '1'}

    def call(self, **changes):
        env = dict(self.env, **changes)
        return campaign_identity(CAMPAIGN, self.sha, self.sha, '123', env)

    def test_exact_campaign_source_and_run_are_bound(self):
        self.assertEqual(1, self.call()['boot_limit_per_api'])
        self.assertFalse(self.call()['automatic_retry'])

    def test_wrong_sha_run_and_rerun_are_refused(self):
        for changes in [{'GITHUB_SHA':'2'*40}, {'GITHUB_RUN_ID':'456'}, {'GITHUB_RUN_ATTEMPT':'2'}, {'GITHUB_RUN_ATTEMPT':''}]:
            with self.subTest(changes=changes), self.assertRaises(ValueError): self.call(**changes)

    def test_build_source_mismatch_is_refused(self):
        with self.assertRaises(ValueError):
            campaign_identity(CAMPAIGN,self.sha,'2'*40,'123',self.env)

    def test_unknown_campaign_or_missing_run_are_refused(self):
        with self.assertRaises(ValueError): campaign_identity('R1-Q1',self.sha,self.sha,'123',self.env)
        with self.assertRaises(ValueError): campaign_identity(CAMPAIGN,self.sha,self.sha,None,self.env)


class ClassificationTests(unittest.TestCase):
    def test_system_crash_without_installed_jarvis_is_lab_failure(self):
        self.assertEqual('LABORATORY_FAILURE',classify(system_incident='SYSTEM_FATAL_EXCEPTION'))

    def test_failed_boot_is_lab_failure_not_application_failure(self):
        self.assertEqual('LABORATORY_FAILURE',classify(boot_completed=False))

    def test_observation_configuration_is_separate(self):
        self.assertEqual('CONFIGURATION_FAILURE',classify(configuration_error=True))

    def test_app_fatal_requires_application_actually_installed(self):
        self.assertEqual('UNKNOWN',classify(application_fatal=True))
        self.assertEqual('APPLICATION_FAILURE_OBSERVED',classify(application_fatal=True,jarvis_installed=True))

    def test_unknown_never_becomes_success(self):
        self.assertEqual('UNKNOWN',classify())


if __name__ == '__main__': unittest.main()
