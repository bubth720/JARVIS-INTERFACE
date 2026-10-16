#!/usr/bin/env python3
"""Package the debug APK and French physical-device checklist, without test APKs."""
import hashlib
import os
from pathlib import Path
import shutil
import subprocess

root = Path(__file__).resolve().parents[1]
out = root / "app/build/device-kit"
inputs = [root / "app/build/outputs/apk/debug/app-debug.apk"] + [
    root / "docs/device-validation" / name
    for name in ("guide-android.md", "grille-appareils.csv", "retour-utilisateur.md")
]
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
    f"JARVIS INTERFACE — mission 006 / debug\nBranch: {branch}\nCommit: {commit}\n"
    f"Uncommitted files: {dirty}\nAndroid minimum: API 26\n"
    "Physical POCO X8 Pro / Redmi Pad SE validation: pending user results\n", encoding="utf-8")
files = [out / p.name for p in inputs] + [out / "SOURCE.txt"]
(out / "SHA256SUMS.txt").write_text("".join(
    f"{hashlib.sha256(p.read_bytes()).hexdigest()}  {p.name}\n" for p in files), encoding="utf-8")
print(f"Device kit: {out.relative_to(root)} (APK + guide + checklists + provenance + checksums)")
