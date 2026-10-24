import json
from pathlib import Path
import struct
import tempfile
import unittest
import zipfile
import zlib
from make_athena_pack import build,inspect_png,plan_dimensions,private_output,PIXELS
from check_athena_pack import check

def png(opaque=False):
    def chunk(name,data):return struct.pack('>I',len(data))+name+data+struct.pack('>I',zlib.crc32(name+data)&0xffffffff)
    raw=bytes([0,40,50,60,255,0,0,0,255 if opaque else 0])
    return b'\x89PNG\r\n\x1a\n'+chunk(b'IHDR',struct.pack('>IIBBBBB',2,1,8,6,0,0,0))+chunk(b'IDAT',zlib.compress(raw))+chunk(b'IEND',b'')

class PackTest(unittest.TestCase):
    def setup_spec(self,path):
        (path/'face.png').write_bytes(png())
        spec={'id':'athena.private.test','author':'test fixture','frames':[{'state':'IDLE','expression':'NEUTRAL','perspective':'FACE','file':'face.png'}]}
        (path/'spec.json').write_text(json.dumps(spec));return path/'spec.json'
    def test_rgba_is_actually_decoded(self): self.assertEqual(inspect_png(png()),(2,1))
    def test_opaque_image_is_refused(self):
        with self.assertRaises(ValueError):inspect_png(png(True))
    def test_corrupt_crc_is_refused(self):
        b=bytearray(png());b[-1]^=1
        with self.assertRaises(ValueError):inspect_png(bytes(b))
    def test_pack_is_deterministic_and_unapproved(self):
        with tempfile.TemporaryDirectory() as folder:
            p=Path(folder);spec=self.setup_spec(p);a=build(spec,p/'a');b=build(spec,p/'b')
            self.assertEqual(a,b);self.assertEqual((p/'a/athena-review.zip').read_bytes(),(p/'b/athena-review.zip').read_bytes())
            self.assertFalse(a['publicationApproved']);self.assertFalse(a['permanentActivationApproved']);self.assertFalse(a['rigReady'])
            self.assertEqual(check(p/'a/athena-review.zip')['resources'],1)
    def test_planner_reserves_existing_preview_and_keeps_budget(self):
        for n in (1,8,12,16):
            w,h=plan_dimensions(n);self.assertEqual(w*4,h*3);self.assertLessEqual(w*h*n+40000,PIXELS)
    def test_public_assets_output_is_refused(self):
        with self.assertRaises(ValueError):private_output(Path(__file__).resolve().parents[2]/'app/src/main/assets/athena')
    def test_source_escape_is_refused(self):
        with tempfile.TemporaryDirectory() as folder:
            p=Path(folder);spec=self.setup_spec(p);m=json.loads(spec.read_text());m['frames'][0]['file']='../escape.png';spec.write_text(json.dumps(m))
            with self.assertRaises(ValueError):build(spec,p/'out')
    def test_missing_neutral_is_refused(self):
        with tempfile.TemporaryDirectory() as folder:
            p=Path(folder);spec=self.setup_spec(p);m=json.loads(spec.read_text());m['frames'][0]['state']='ERROR';spec.write_text(json.dumps(m))
            with self.assertRaises(ValueError):build(spec,p/'out')
    def test_extra_archive_file_is_refused(self):
        with tempfile.TemporaryDirectory() as folder:
            p=Path(folder);build(self.setup_spec(p),p/'out');z=p/'out/athena-review.zip'
            with zipfile.ZipFile(z,'a') as archive:archive.writestr('unexpected.txt',b'x')
            with self.assertRaises(ValueError):check(z)

if __name__=='__main__':unittest.main()
