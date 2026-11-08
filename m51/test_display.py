import unittest
from display_config import require_display

class DisplayQualification(unittest.TestCase):
    def test_reproduces_observed_api29_clamp_and_refuses_qualification(self):
        with self.assertRaisesRegex(ValueError,'CLAMPED'):
            require_display('Physical size: 320x640\nOverride size: 640x1280',
                            'Physical density: 160\nOverride density: 420','1080x1920',420)

    def test_explicit_override_controls_the_effective_size(self):
        result=require_display('Physical size: 1080x1920\nOverride size: 1200x1920',
                               'Physical density: 420\nOverride density: 240','1200x1920',240)
        self.assertEqual({'effective_size':'1200x1920','effective_density':240},result)

    def test_native_geometry_needs_no_override(self):
        self.assertEqual('1080x1920',require_display('Physical size: 1080x1920',
                         'Physical density: 420','1080x1920',420)['effective_size'])

    def test_unknown_or_contradictory_observation_is_not_a_pass(self):
        for report in ['','Override size: 1080x1920','Physical size: 1080x1920\nOverride size: 600x800\nOverride size: 1080x1920']:
            with self.assertRaisesRegex(ValueError,'UNKNOWN'):
                require_display(report,'Physical density: 420','1080x1920',420)
