#!/usr/bin/env python3
"""Destructive layout fixtures on a disposable emulator only; never targets a phone.
Requires the debug APK, ADB and a booted emulator. No other app data is cleared.
"""
import argparse
import json
import re
import subprocess
import time
import xml.etree.ElementTree as ET
from pathlib import Path

PACKAGE = "com.jarvis.interfaceapp"
LAYOUT = "files/datastore/dashboard.json"


class Check:
    def __init__(self, args):
        self.args = args
        self.output = Path(args.output)
        self.output.mkdir(parents=True, exist_ok=True)
        self.evidence = {"serial": args.serial, "cases": {}}

    def adb(self, *args, data=None, check=True, timeout=60):
        result = subprocess.run([self.args.adb, "-s", self.args.serial, *args],
                                input=data, stdout=subprocess.PIPE, stderr=subprocess.PIPE, timeout=timeout)
        if check and result.returncode:
            raise RuntimeError(f"ADB {args}: {result.stderr.decode(errors='replace')}")
        return result

    def text(self, *args, **kwargs):
        return self.adb(*args, **kwargs).stdout.decode().strip()

    def mark(self, name, detail=True):
        self.evidence["cases"][name] = detail
        (self.output / "evidence.json").write_text(json.dumps(self.evidence, indent=2))
        print(f"PASS {name}", flush=True)

    def tree(self):
        self.adb("shell", "uiautomator", "dump", "/sdcard/jarvis-storage-ui.xml")
        return list(ET.fromstring(self.text("exec-out", "cat", "/sdcard/jarvis-storage-ui.xml")).iter("node"))

    def wait_node(self, tag, predicate=lambda n: True, timeout=35):
        deadline = time.monotonic() + timeout
        while time.monotonic() < deadline:
            for node in self.tree():
                if node.get("resource-id") == tag and predicate(node):
                    return node
            time.sleep(0.2)
        raise AssertionError(f"UI node not ready: {tag}")

    @staticmethod
    def bounds(node):
        return [int(v) for v in re.findall(r"\d+", node.get("bounds"))]

    def tap(self, tag):
        left, top, right, bottom = self.bounds(self.wait_node(tag))
        self.adb("shell", "input", "tap", str((left + right) // 2), str((top + bottom) // 2))

    def raw(self):
        return self.adb("exec-out", "run-as", PACKAGE, "cat", LAYOUT).stdout

    def saved(self, profile=None, expected=None):
        deadline = time.monotonic() + 35
        while time.monotonic() < deadline:
            self.wait_node("storage-status", lambda n: n.get("text") == "Disposition enregistrée.")
            doc = json.loads(self.raw())
            if profile is None or all(self.avatar(doc, profile)[key] == value for key, value in expected.items()):
                return doc
            time.sleep(0.2)
        raise AssertionError(f"Expected saved {profile}: {expected}")

    @staticmethod
    def avatar(doc, profile):
        return next(p for p in doc["profiles"][profile] if p["id"] == "avatar")

    def start(self):
        self.adb("shell", "am", "start", "-W", "-n", PACKAGE + "/.MainActivity")
        self.wait_node("dashboard")

    def stop(self):
        self.adb("shell", "am", "force-stop", PACKAGE)
        assert not self.text("shell", "pidof", PACKAGE, check=False), "Process still alive"

    def restart(self):
        old = self.text("shell", "pidof", PACKAGE)
        assert old
        self.stop()
        self.start()
        new = self.text("shell", "pidof", PACKAGE)
        assert new and new != old, (old, new)
        nodes = self.tree()
        assert not any(n.get("resource-id") in ("move-AVATAR", "close-settings") for n in nodes)
        assert not any("Position refusée" in n.get("text", "") for n in nodes)
        return {"old_pid": old, "stopped_before_launch": True, "new_pid": new}

    def reset(self):
        self.tap("open-settings")
        self.tap("reset-layouts")
        self.tap("confirm-reset")
        result = self.saved()
        self.tap("close-settings")
        self.wait_node("dashboard")
        return result

    def density(self):
        values = re.findall(r"density:\s*(\d+)", self.text("shell", "wm", "density"))
        return int(values[-1]) / 160

    def cells(self, profile):
        left, top, right, bottom = self.bounds(self.wait_node("panel-SYSTEM"))
        padding = 8 * self.density()
        return ((right - left + padding) / (3 if profile == "compact" else 6),
                (bottom - top + padding) / (4 if profile == "compact" else 3))

    def swipe(self, tag, dx, dy):
        left, top, right, bottom = self.bounds(self.wait_node(tag))
        x, y = (left + right) // 2, (top + bottom) // 2
        self.adb("shell", "input", "swipe", str(x), str(y), str(round(x + dx)), str(round(y + dy)), "650")

    def geometry(self, profile, expected):
        self.wait_node("storage-status", lambda n: n.get("text") == "Disposition enregistrée.")
        avatar = self.bounds(self.wait_node("panel-AVATAR"))
        system = self.bounds(self.wait_node("panel-SYSTEM"))
        cw, ch = self.cells(profile)
        gap = 8 * self.density()
        assert abs((avatar[1] - system[1]) - expected["y"] * ch) <= 3, (avatar, system)
        assert abs((avatar[3] - avatar[1] + gap) - expected["height"] * ch) <= 3
        assert abs((avatar[2] - avatar[0] + gap) - expected["width"] * cw) <= 3

    def fixture(self, data):
        self.stop()
        self.adb("shell", f"run-as {PACKAGE} sh -c 'cat > {LAYOUT}'", data=data)
        self.start()

    def protected_case(self, name, data, word):
        self.fixture(data)
        self.wait_node("storage-status", lambda n: word in n.get("text", ""))
        assert self.raw() == data
        assert self.wait_node("toggle-edit").get("enabled") == "false"
        self.restart()
        self.wait_node("storage-status", lambda n: word in n.get("text", ""))
        assert self.raw() == data
        old_archives = set(self.text("shell", "run-as", PACKAGE, "ls", "files/datastore").splitlines())
        self.reset()
        new_archives = set(self.text("shell", "run-as", PACKAGE, "ls", "files/datastore").splitlines()) - old_archives
        archives = [p for p in new_archives if p.startswith("dashboard.recovery-") and p.endswith(".json")]
        assert archives
        assert any(self.adb("exec-out", "run-as", PACKAGE, "cat", "files/datastore/" + p).stdout == data for p in archives)
        self.mark(name)

    def run(self):
        self.adb("install", "-r", self.args.apk)
        self.stop()
        self.adb("shell", "run-as", PACKAGE, "rm", "-f", LAYOUT)
        self.adb("shell", "wm", "size", "reset")
        self.adb("shell", "wm", "density", "reset")
        self.start()
        self.wait_node("storage-status", lambda n: "aucune sauvegarde" in n.get("text", ""))
        assert self.adb("shell", "run-as", PACKAGE, "test", "-e", LAYOUT, check=False).returncode != 0
        self.mark("first_installation_without_layout_file")
        baseline = self.reset()
        self.adb("shell", f"run-as {PACKAGE} sh -c 'cat > files/storage-mission-sentinel'", data=b"unrelated-data")

        self.tap("toggle-edit")
        cw, ch = self.cells("compact")
        self.swipe("move-AVATAR", 0, ch)
        self.saved("compact", {"y": 1})
        self.swipe("resize-AVATAR", 0, -ch)
        self.saved("compact", {"height": 3})
        self.tap("lock-AVATAR")
        self.saved("compact", {"locked": True})
        compact = {"x": 0, "y": 1, "width": 3, "height": 3, "locked": True}
        self.mark("compact_move_resize_lock_saved")

        self.adb("shell", "wm", "size", "1600x1200")
        self.adb("shell", "wm", "density", "240")
        self.wait_node("move-AVATAR", lambda n: n.get("enabled") == "true")
        cw, ch = self.cells("wide")
        self.swipe("move-AVATAR", 0, ch)
        self.saved("wide", {"y": 1})
        self.swipe("resize-AVATAR", -cw, 0)
        configured = self.saved("wide", {"width": 5})
        wide = {"x": 0, "y": 1, "width": 5, "height": 3, "locked": False}
        assert all(self.avatar(configured, "compact")[k] == v for k, v in compact.items())
        self.mark("wide_saved_independently")

        previous = self.raw()
        self.swipe("move-AVATAR", -cw, 0)
        deadline = time.monotonic() + 30
        while not any("Position refusée" in n.get("text", "") for n in self.tree()):
            assert time.monotonic() < deadline
        assert self.raw() == previous
        self.mark("invalid_placement_does_not_write")
        details = self.restart()
        assert json.loads(self.raw()) == configured
        self.geometry("wide", wide)
        self.mark("real_process_death_restores_wide_and_temporary_state_is_absent", details)

        self.adb("shell", "wm", "size", "reset")
        self.adb("shell", "wm", "density", "reset")
        self.geometry("compact", compact)
        self.tap("toggle-edit")
        assert self.wait_node("move-AVATAR").get("enabled") == "false"
        self.tap("open-settings")
        self.mark("real_process_death_from_settings_restores_home", self.restart())
        self.geometry("compact", compact)

        if self.args.reboot:
            old_boot = self.text("shell", "cat", "/proc/sys/kernel/random/boot_id")
            print("Rebooting disposable emulator…", flush=True)
            self.adb("reboot")
            self.adb("wait-for-device", timeout=180)
            deadline = time.monotonic() + 180
            while self.text("shell", "getprop", "sys.boot_completed", check=False) != "1":
                assert time.monotonic() < deadline, "Emulator boot timed out"
                time.sleep(1)
            new_boot = self.text("shell", "cat", "/proc/sys/kernel/random/boot_id")
            assert new_boot != old_boot
            self.start()
            assert json.loads(self.raw()) == configured
            self.geometry("compact", compact)
            self.mark("emulator_reboot_restores_saved_layout", {"old_boot": old_boot, "new_boot": new_boot})

        assert self.reset() == baseline
        details = self.restart()
        assert json.loads(self.raw()) == baseline
        self.geometry("compact", {"y": 0, "width": 3, "height": 4})
        self.mark("confirmed_reset_survives_real_process_death", details)
        self.protected_case("corrupt_file_preserved_until_confirmed_reset_and_archived", b"corrupt-layout", "illisible")
        self.protected_case("unknown_version_preserved_until_confirmed_reset_and_archived",
                            b'{"version":999,"profiles":{}}', "incompatible")
        assert self.adb("exec-out", "run-as", PACKAGE, "cat", "files/storage-mission-sentinel").stdout == b"unrelated-data"
        self.adb("shell", "run-as", PACKAGE, "rm", "files/storage-mission-sentinel")
        self.mark("other_app_data_untouched_by_reset")
        (self.output / "final-layout.json").write_bytes(self.raw())
        print("Storage restart scenario completed successfully.", flush=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--adb", default="adb")
    parser.add_argument("--serial", required=True)
    parser.add_argument("--allow-layout-reset", action="store_true")
    parser.add_argument("--apk", default="app/build/outputs/apk/debug/app-debug.apk")
    parser.add_argument("--output", default="app/build/reports/storage-restart")
    parser.add_argument("--reboot", action="store_true")
    args = parser.parse_args()
    if not args.serial.startswith("emulator-") or not args.allow_layout_reset:
        parser.error("Only disposable emulator-* targets with --allow-layout-reset are supported")
    Check(args).run()


if __name__ == "__main__":
    main()
