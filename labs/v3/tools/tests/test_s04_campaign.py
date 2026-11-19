import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

TOOLS = Path(__file__).resolve().parents[1]
BASELINE = TOOLS.parent / "captures" / "run-44391288619"
CASES = ("results.txt", "audit-results.txt")


class S04CampaignTest(unittest.TestCase):
    def setUp(self):
        tmp = tempfile.TemporaryDirectory()
        self.addCleanup(tmp.cleanup)
        self.out = Path(tmp.name)

    def write_fixture(self, api, fix_failures=False):
        for name in CASES:
            raw = (BASELINE / f"api{api}-{name}").read_text()
            if fix_failures:
                raw = "\n".join("PASS " + line[5:] if line.startswith("FAIL ") else line for line in raw.splitlines())
            (self.out / name).write_text(raw)

    def check(self, api):
        return subprocess.run(
            [sys.executable, str(TOOLS / "check_s04_campaign.py"), str(api), str(self.out)],
            text=True, capture_output=True, check=False,
        )

    def test_the_red_campaign_is_refused_on_both_apis(self):
        for api, qual in ((29, 11), (35, 9)):
            self.write_fixture(api)
            result = self.check(api)
            self.assertEqual(1, result.returncode)
            self.assertIn(f"results.txt: {qual}/11", result.stdout)
            self.assertIn("audit-results.txt: 28/30", result.stdout)

    def test_success_needs_every_pass_exactly_once(self):
        for api in (29, 35):
            self.write_fixture(api, fix_failures=True)
            result = self.check(api)
            self.assertEqual(0, result.returncode, result.stdout)
            self.assertIn("audit-results.txt: 30/30 criteria, 26 recorded cases", result.stdout)
        receipt = self.out / "audit-results.txt"
        with receipt.open("a") as stream:
            stream.write("\nPASS k_sphereStillAtEveryNonAnswerTransition\n")
        self.assertEqual(1, self.check(35).returncode)
        (self.out / "results.txt").unlink()
        self.assertIn("results.txt: unavailable", self.check(35).stdout)
