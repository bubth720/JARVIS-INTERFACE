#!/usr/bin/env python3
"""Validate the tour index against transferred PNGs; never infer success from an artifact's presence."""
import argparse
import hashlib
import json
from pathlib import Path
import struct

TABS = ('ai_core', 'voice', 'memory', 'nexus', 'missions', 'agents', 'automations', 'security', 'evolution', 'blueprint')
REQUIRED = {
    'tour-01-ecran-principal-sphere-repos', 'tour-02-reponse-documentaire-citee',
    'tour-03-proposition-de-souvenir', 'tour-04-souvenir-confirme', 'tour-05-menu-radial',
    'tour-20-nexus-bibliotheque', 'tour-21-palette-de-commandes', 'tour-22-reglages', 'tour-40-paysage',
} | {'tour-10-command-center-' + tab for tab in TABS}
AVATAR = 'tour-30-avatar-athena-gros-plan'


def validate(output, api):
    output = Path(output)
    index = json.loads((output / 'audit-tour.json').read_text())
    if index.get('schema') != 'jarvis.capture-tour/1' or index.get('api') != api or index.get('package') != 'fr.jarvis.v3':
        raise ValueError('TOUR_IDENTITY_MISMATCH')
    rows = index['captures']
    names = [r['file'] for r in rows]
    if len(names) != len(set(names)) or set(names) != REQUIRED | {AVATAR}:
        raise ValueError('TOUR_SCREENS_MISSING_OR_DUPLICATED')
    verified = []
    for row in rows:
        name = row['file']
        if not row.get('screen'):
            raise ValueError('SCREEN_DESCRIPTION_MISSING')
        if row.get('status') == 'unavailable' and name == AVATAR and row.get('reason'):
            verified.append(row)
            continue
        if row.get('status') != 'captured':
            raise ValueError('REQUIRED_SCREEN_NOT_CAPTURED:' + name)
        path = output / 'shots' / f'api{api}-{name}.png'
        if path.is_symlink():
            raise ValueError('SYMLINK_CAPTURE')
        data = path.read_bytes()
        if len(data) < 45 or data[:8] != b'\x89PNG\r\n\x1a\n' or data[12:16] != b'IHDR':
            raise ValueError('INVALID_PNG:' + name)
        width, height = struct.unpack('>II', data[16:24])
        if not width or not height or data[-12:] != b'\0\0\0\0IEND\xaeB`\x82':
            raise ValueError('INCOMPLETE_PNG:' + name)
        verified.append({**row, 'path': str(path.relative_to(output)), 'width': width, 'height': height,
                         'bytes': len(data), 'sha256': hashlib.sha256(data).hexdigest()})
    return {'schema': 'jarvis.capture-evidence/1', 'api': api, 'status': 'PASS',
            'captured': sum(r['status'] == 'captured' for r in verified), 'screens': verified}


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--api', type=int, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    try:
        summary = validate(args.output, args.api)
        (args.output / 'capture-summary.json').write_text(json.dumps(summary, ensure_ascii=False, indent=2) + '\n')
        print(f"Verified {summary['captured']} PNG captures on API {args.api}")
    except (ValueError, KeyError, TypeError, OSError, struct.error) as error:
        (args.output / 'capture-summary.json').unlink(missing_ok=True)
        parser.exit(1, f'CAPTURE_EVIDENCE_INVALID: {error}\n')
