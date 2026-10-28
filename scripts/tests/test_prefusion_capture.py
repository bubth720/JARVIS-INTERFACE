import base64
import importlib.util
from pathlib import Path
import unittest


spec = importlib.util.spec_from_file_location('capture', Path(__file__).parents[1] / 'collect_prefusion_capture.py')
capture = importlib.util.module_from_spec(spec)
spec.loader.exec_module(capture)


class CaptureFormatTest(unittest.TestCase):
    image = base64.b64decode('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+ip1sAAAAASUVORK5CYII=')

    def test_known_fixture_png_dimensions_and_digest(self):
        result = capture.png_metadata(self.image)
        self.assertEqual((result['width'], result['height']), (1, 1))
        self.assertEqual(len(result['sha256']), 64)

    def test_nonempty_text_is_not_a_png(self):
        with self.assertRaises(ValueError):
            capture.png_metadata(b'nonempty public error text, no actual PNG' * 2)

    def test_truncated_png_is_refused(self):
        with self.assertRaises(ValueError):
            capture.png_metadata(self.image[:-4])

    def test_corrupted_image_payload_is_refused(self):
        corrupted = bytearray(self.image)
        corrupted[45] ^= 1
        with self.assertRaises(ValueError):
            capture.png_metadata(bytes(corrupted))
