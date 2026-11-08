import unittest
from m51.r1.qualification import fixture_reset_allowed
class FixtureCleanupScope(unittest.TestCase):
    def test_historical_and_personal_packages_always_refused(self):
        for p in ['com.jarvis.interfaceapp','fr.jarvis.v1.lab','fr.jarvis.llm','com.android.systemui']:
            with self.assertRaises(ValueError):fixture_reset_allowed(p,[p])
    def test_unowned_baseline_is_refused(self):
        with self.assertRaises(ValueError):fixture_reset_allowed('fr.jarvis.v1.lab.baseline',[])
    def test_only_explicitly_created_fixture_package_is_eligible(self):
        self.assertEqual('fr.jarvis.v1.lab.baseline',fixture_reset_allowed('fr.jarvis.v1.lab.baseline',['fr.jarvis.v1.lab.baseline']))
