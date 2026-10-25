#!/usr/bin/env python3
"""Verify private/public APK separation without exposing or uploading artwork."""
from pathlib import Path
import argparse,hashlib,zipfile,json
p=argparse.ArgumentParser();p.add_argument('apk',type=Path);p.add_argument('--private-pack',type=Path)
a=p.parse_args()
with zipfile.ZipFile(a.apk) as z:
 names=[n for n in z.namelist() if n.startswith('assets/athena-private/')]
 if a.private_pack:
  assert names==['assets/athena-private/athena-review.zip'],names
  content=z.read(names[0]);assert hashlib.sha256(content).digest()==hashlib.sha256(a.private_pack.read_bytes()).digest()
  print(json.dumps({'distribution':'PRIVATE_PREVIEW','bundleSha256':hashlib.sha256(content).hexdigest()}))
 else:
  assert not names,'Private Athena assets cannot be published in a public CI APK'
  print('PUBLIC APK: no private Athena bundle; import required, no vector primary')
