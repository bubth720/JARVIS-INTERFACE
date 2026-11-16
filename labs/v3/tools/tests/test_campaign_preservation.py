"""Compare the untouched full-campaign path and resource pins to the audited revision."""
from pathlib import Path
import re
import subprocess
import unittest

ROOT = Path(__file__).resolve().parents[4]
BASE = 'f73d4f5c20739cbeae21479c4b3f107b90370456'


def before(path):
    return subprocess.check_output(['git', '-C', str(ROOT), 'show', BASE + ':' + path], text=True)


class CampaignPreservationTest(unittest.TestCase):
    def test_common_setup_and_complete_campaign_are_byte_identical(self):
        path = 'labs/v3/tools/run_emulator.sh'
        old, new = before(path), (ROOT / path).read_text()
        capture = '\nif [ "${CAPTURES_ONLY:-0}" = 1 ]; then\n'
        campaign = '\nrun c_realEngineStatesCardsMenuLandscapeBackground'
        self.assertEqual(old.split(capture)[0], new.split(capture)[0])
        self.assertEqual(old[old.index(campaign):], new[new.index(campaign):])

    def test_external_repository_revisions_are_unchanged(self):
        path = '.github/workflows/v3-cockpit.yml'
        pattern = r'repository: (bubth720/[^,]+), ref: ([0-9a-f]{40})'
        old, new = before(path), (ROOT / path).read_text()
        self.assertEqual(4, len(re.findall(pattern, old)))
        self.assertEqual(re.findall(pattern, old), re.findall(pattern, new))

    def test_model_and_audio_preparation_are_unchanged(self):
        path = '.github/workflows/v3-cockpit.yml'
        def resources(text):
            return text.split('      - name: Local resources', 1)[1].split('      - name: Enable emulator acceleration', 1)[0]
        self.assertEqual(resources(before(path)), resources((ROOT / path).read_text()))
