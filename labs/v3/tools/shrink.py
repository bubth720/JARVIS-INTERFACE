#!/usr/bin/env python3
"""Downscale emulator captures (540 px wide JPEG) for review in the repository; originals stay in the artifact."""
import sys
from pathlib import Path
from PIL import Image
src, dst = Path(sys.argv[1]), Path(sys.argv[2]); dst.mkdir(parents=True, exist_ok=True)
for p in sorted(src.glob('*.png')):
    im = Image.open(p).convert('RGB'); w = 540 if im.width < im.height else 960
    im.thumbnail((w, 4000)); im.save(dst / (p.stem + '.jpg'), quality=80, optimize=True); print(dst / (p.stem + '.jpg'))
