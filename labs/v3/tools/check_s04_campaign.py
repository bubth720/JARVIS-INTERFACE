"""Qualify the S04 reports against the cases from the full reference campaign."""

import argparse
import re
from collections import Counter
from pathlib import Path

REFERENCE = Path(__file__).resolve().parents[1] / "captures" / "run-44391288619"
# F2/F3/F4 are three cases. E2/E3 share one text-fit check, alongside E6 (accessibility) and E7 (avatar).
AUDIT_EXTRA = {"f2f3f4_documentsContradictoryCorruptAndInjected": 2, "e2e3e6e7_layoutStressAccessibilityAvatar": 2}


def results(path):
    return re.findall(r"^(PASS|FAIL) ([A-Za-z0-9_]+)\s*$", path.read_text(), flags=re.MULTILINE)


def verify(receipt, reference, extras, total):
    actual = results(receipt)
    expected = Counter(name for _, name in results(reference))
    observed = Counter(name for _, name in actual)
    passed = sum(1 + extras.get(name, 0) for name, count in observed.items() if count == 1 and ("PASS", name) in actual)
    expected_total = sum(1 + extras.get(name, 0) for name in expected)
    print(f"{receipt.name}: {passed}/{total} criteria, {len(actual)} recorded cases")
    if observed != expected:
        print(f"  Missing: {dict(expected - observed)}")
        print(f"  Extra or duplicate: {dict(observed - expected)}")
    failed = [name for status, name in actual if status == "FAIL"]
    if failed:
        print(f"  Failed: {', '.join(failed)}")
    return observed == expected and not failed and expected_total == passed == total


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("api", choices=("29", "35"))
    parser.add_argument("output", type=Path)
    args = parser.parse_args()
    checks = []
    for name, extras, total in (("results.txt", {}, 11), ("audit-results.txt", AUDIT_EXTRA, 30)):
        try:
            checks.append(verify(args.output / name, REFERENCE / f"api{args.api}-{name}", extras, total))
        except OSError as exc:
            print(f"{name}: unavailable ({exc})")
            checks.append(False)
    return 0 if all(checks) else 1


if __name__ == "__main__":
    raise SystemExit(main())
