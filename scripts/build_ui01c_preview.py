#!/usr/bin/env python3
"""Produce a single offline HTML; no network resources or runtime build needed."""
from pathlib import Path
import sys

root = Path(__file__).resolve().parents[1] / 'preview'
html = (root / 'index.html').read_text()
html = html.replace('<link rel="stylesheet" href="style.css">', '<style>' + (root / 'style.css').read_text() + '</style>')
for name in ('design-data.js', 'app.js'):
    html = html.replace('<script src="' + name + '" defer></script>', '')
scripts = '\n'.join((root / name).read_text() for name in ('design-data.js', 'app.js')).replace('</script', '<\\/script')
html = html.replace('</body>', '<script>' + scripts + '</script></body>')
path = root / 'jarvis-ui01c.html'
if '--check' in sys.argv:
    if not path.exists() or path.read_text() != html:
        raise SystemExit('Standalone HTML is stale; run scripts/build_ui01c_preview.py')
else:
    path.write_text(html)
print('Standalone offline HTML ready:', path.name)
