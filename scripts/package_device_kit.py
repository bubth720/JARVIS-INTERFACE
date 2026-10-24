#!/usr/bin/env python3
"""Package the debug APK and French physical-device checklist, without test APKs."""
import argparse
import hashlib
import os
from pathlib import Path
import shutil
import subprocess

root = Path(__file__).resolve().parents[1]
parser = argparse.ArgumentParser(description=__doc__)
options = parser.add_mutually_exclusive_group()
options.add_argument("--avatar", action="store_true", help="Package the AVATAR-001 prototype guide/assets report")
options.add_argument("--avatar-v2", action="store_true", help="Package AVA-03 partial code delivery and genuine technical fixtures")
args = parser.parse_args()
out = root / ("app/build/avatar-v2-kit" if args.avatar_v2 else "app/build/avatar-kit" if args.avatar else "app/build/device-kit")
folder = "docs/avatar/v2" if args.avatar_v2 else "docs/avatar" if args.avatar else "docs/device-validation"
names = ("GUIDE.md", "ASSET-MAP.md", "ART-REVIEW.md", "DELIVERY.md", "fixtures/ava03-technical-demo.zip") if args.avatar_v2 else ("guide-test.md", "assets-definitifs.md", "architecture-et-validation.md") if args.avatar else (
    "guide-android.md", "grille-appareils.csv", "retour-utilisateur.md")
inputs = [root / "app/build/outputs/apk/debug/app-debug.apk"] + [root / folder / name for name in names]
for source in inputs:
    if not source.is_file() or source.stat().st_size == 0:
        raise SystemExit(f"Missing input: {source.relative_to(root)}; build assembleDebug first")
out.mkdir(parents=True, exist_ok=True)
for source in inputs:
    shutil.copyfile(source, out / source.name)

def git(*args):
    return subprocess.check_output(["git", "-C", str(root), *args], text=True).strip()

commit = git("rev-parse", "HEAD")
dirty = bool(git("status", "--porcelain", "--untracked-files=normal"))
branch = os.environ.get("GITHUB_REF_NAME") or git("branch", "--show-current")
(out / "SOURCE.txt").write_text(
    f"JARVIS INTERFACE — {'AVA-03 PARTIAL / ART_ASSETS_MISSING' if args.avatar_v2 else 'AVATAR-001' if args.avatar else 'mission 006'} / debug\nBranch: {branch}\nCommit: {commit}\n"
    f"Uncommitted files: {dirty}\nAndroid minimum: API 26\n"
    "Physical POCO X8 Pro / Redmi Pad SE validation: pending user results\n", encoding="utf-8")
files = [out / p.name for p in inputs] + [out / "SOURCE.txt"]
(out / "SHA256SUMS.txt").write_text("".join(
    f"{hashlib.sha256(p.read_bytes()).hexdigest()}  {p.name}\n" for p in files), encoding="utf-8")
print(f"Device kit: {out.relative_to(root)} (APK + guide + checklists + provenance + checksums)")
