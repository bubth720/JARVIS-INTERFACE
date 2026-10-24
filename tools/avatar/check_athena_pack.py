#!/usr/bin/env python3
"""Inspect an offline review ZIP; Android PremiumAssetValidator remains authoritative."""
import argparse
import json
from pathlib import Path
import zipfile
from make_athena_pack import inspect_png,digest,PIXELS,MAX_BYTES

def check(path):
    with zipfile.ZipFile(path) as z:
        entries=z.infolist();names=[i.filename for i in entries]
        if len(names)!=len(set(n.lower() for n in names)) or len(names)>17 or sum(i.file_size for i in entries)>MAX_BYTES: raise ValueError('ZIP_BUDGET_OR_DUPLICATE')
        manifest_bytes=z.read('manifest.json');m=json.loads(manifest_bytes)
        if m['version']!=2 or len(m['layers'])>16 or len(m['resources'])>16: raise ValueError('SCHEMA_BUDGET')
        if set(names)!={'manifest.json'}|{r['file'] for r in m['resources']}: raise ValueError('EXTRA_OR_MISSING_FILE')
        pixels=0
        for r in m['resources']:
            if '/' in r['file'] or '\\' in r['file']:raise ValueError('PATH')
            if r['width']*r['height']+pixels>PIXELS or z.getinfo(r['file']).file_size>2*1024*1024:raise ValueError('TEXTURE_BUDGET')
            b=z.read(r['file']);w,h=inspect_png(b)
            if digest(b)!=r['sha256'] or (w,h)!=(r['width'],r['height']):raise ValueError('HASH_OR_DIMENSIONS')
            pixels+=w*h
        return dict(result='OFFLINE_CHECK_PASS',resources=len(m['resources']),pixels=pixels,manifestSha256=digest(manifest_bytes),artApproved=False,androidValidatorExecuted=False)
if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('pack');a=p.parse_args();print(json.dumps(check(Path(a.pack)),indent=2))
