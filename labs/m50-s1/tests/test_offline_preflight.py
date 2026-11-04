"""Isolated checks of the emulator's asynchronous but fail-closed offline gate."""
import importlib.util
from pathlib import Path
import unittest

script = Path(__file__).resolve().parents[1] / 'tools/run_emulator.py'
spec = importlib.util.spec_from_file_location('run_emulator_offline_fixture', script)
mod = importlib.util.module_from_spec(spec)
spec.loader.exec_module(mod)


class OfflinePreflight(unittest.TestCase):
    def test_delayed_wifi_shutdown_must_reach_verified_offline_state(self):
        observations = iter([('0', '1'), ('1', '1'), ('1', '0')])
        sleeps = []
        mod.require_offline(lambda: next(observations), now=lambda: 0.0,
                            sleep=sleeps.append, timeout_seconds=1)
        self.assertEqual(len(sleeps), 2)

    def test_wifi_still_enabled_is_denied_at_deadline(self):
        clock = iter([0.0, 0.5, 1.5])
        with self.assertRaisesRegex(AssertionError, 'Offline radios not confirmed'):
            mod.require_offline(lambda: ('1', '1'), now=lambda: next(clock),
                                sleep=lambda _: None, timeout_seconds=1)

    def test_airplane_mode_not_enabled_is_denied(self):
        clock = iter([0.0, 0.5, 1.5])
        with self.assertRaises(AssertionError):
            mod.require_offline(lambda: ('0', '0'), now=lambda: next(clock),
                                sleep=lambda _: None, timeout_seconds=1)


if __name__ == '__main__':
    unittest.main()
