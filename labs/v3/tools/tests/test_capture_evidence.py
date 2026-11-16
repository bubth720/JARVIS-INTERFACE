import json
from pathlib import Path
import tempfile
import unittest

from capture_fixture import index, png
from validate_captures import AVATAR, validate


class CaptureEvidenceTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)
        (self.root / 'shots').mkdir()
        self.data = index()
        for row in self.data['captures']:
            (self.root / 'shots' / ('api29-' + row['file'] + '.png')).write_bytes(png())
        self.write_index()

    def write_index(self):
        (self.root / 'audit-tour.json').write_text(json.dumps(self.data))

    def test_complete_evidence_has_hashes_and_dimensions(self):
        result = validate(self.root, 29)
        self.assertEqual(20, result['captured'])
        self.assertTrue(all(len(r['sha256']) == 64 and r['width'] == 1 for r in result['screens']))

    def test_wrong_api_is_rejected(self):
        with self.assertRaises(ValueError): validate(self.root, 35)

    def test_missing_required_screen_is_rejected(self):
        self.data['captures'].pop(0); self.write_index()
        with self.assertRaises(ValueError): validate(self.root, 29)

    def test_duplicate_index_is_rejected(self):
        self.data['captures'].append(self.data['captures'][0]); self.write_index()
        with self.assertRaises(ValueError): validate(self.root, 29)

    def test_missing_or_empty_png_is_rejected(self):
        path = next((self.root / 'shots').glob('*.png'))
        path.write_bytes(b'')
        with self.assertRaises(ValueError): validate(self.root, 29)
        path.unlink()
        with self.assertRaises(OSError): validate(self.root, 29)

    def test_truncated_png_is_rejected(self):
        next((self.root / 'shots').glob('*.png')).write_bytes(png()[:-1])
        with self.assertRaises(ValueError): validate(self.root, 29)

    def test_disabled_avatar_requires_explicit_reason(self):
        row = next(r for r in self.data['captures'] if r['file'] == AVATAR)
        row.update(status='unavailable', reason='Avatar disabled in this build')
        self.write_index()
        result = validate(self.root, 29)
        self.assertEqual(19, result['captured'])
        row.pop('reason'); self.write_index()
        with self.assertRaises(ValueError): validate(self.root, 29)

    def test_required_screen_cannot_be_declared_unavailable(self):
        self.data['captures'][0].update(status='unavailable', reason='not loaded')
        self.write_index()
        with self.assertRaises(ValueError): validate(self.root, 29)

    def test_symlink_is_rejected(self):
        path = next((self.root / 'shots').glob('*.png'))
        path.unlink(); path.symlink_to(self.root / 'audit-tour.json')
        with self.assertRaises(ValueError): validate(self.root, 29)
