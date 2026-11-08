import hashlib,importlib.util,json,os,struct,tempfile,unittest,zlib
from pathlib import Path
spec=importlib.util.spec_from_file_location('inventory',Path(__file__).parents[1]/'inventory.py');m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m)
def png():
 def chunk(k,b):return struct.pack('>I',len(b))+k+b+struct.pack('>I',zlib.crc32(k+b)&0xffffffff)
 return b'\x89PNG\r\n\x1a\n'+chunk(b'IHDR',struct.pack('>IIBBBBB',1,1,8,6,0,0,0))+chunk(b'IDAT',zlib.compress(b'\x00\x00\x00\x00\x00'))+chunk(b'IEND',b'')
class InventoryTests(unittest.TestCase):
 def setUp(self):
  self.tmp=tempfile.TemporaryDirectory();self.root=Path(self.tmp.name);self.data=png();(self.root/'fixture.png').write_bytes(self.data)
  self.entry={'id':'UNIT_1','file':'fixture.png','kind':'IMAGE','sha256':hashlib.sha256(self.data).hexdigest(),'rights':'REFERENCE_ONLY','rights_evidence_ref':'UNIT_PUBLIC_SYNTHETIC'}
 def tearDown(self):self.tmp.cleanup()
 def run_inventory(self,entries=None):return m.inventory(self.root,{'schema':'jarvis.avatar.assets/1','visibility':'PRIVATE_ONLY','inputs':entries if entries is not None else [self.entry]})
 def test_synthetic_candidate_never_admitted_or_master_approved(self):
  r=self.run_inventory();self.assertEqual(1,r['candidate_count']);self.assertFalse(r['runtime_admitted']);self.assertEqual('PENDING_HUMAN',r['master_approval']);self.assertEqual('NOT_RUN',r['rows'][0]['metadata']['decode_qualification'])
 def test_unknown_rights_quarantined(self):self.entry['rights']='UNKNOWN';self.assertEqual(1,self.run_inventory()['quarantined_count'])
 def test_missing_rights_evidence_quarantined(self):self.entry.pop('rights_evidence_ref');self.assertEqual(1,self.run_inventory()['quarantined_count'])
 def test_duplicate_bytes_not_master_selection(self):
  other=dict(self.entry,id='UNIT_2');r=self.run_inventory([self.entry,other]);self.assertEqual(1,r['duplicate_count']);self.assertFalse(r['runtime_admitted'])
 def test_duplicate_id_refused(self):self.assertEqual(1,self.run_inventory([self.entry,self.entry])['quarantined_count'])
 def test_path_escape_refused_without_read(self):self.entry['file']='../fixture.png';self.assertEqual('PRIVATE_RELATIVE_PATH_REQUIRED',self.run_inventory()['rows'][0]['reason'])
 def test_absolute_path_refused(self):self.entry['file']=str(self.root/'fixture.png');self.assertEqual(1,self.run_inventory()['quarantined_count'])
 def test_symlink_refused(self):
  (self.root/'link.png').symlink_to(self.root/'fixture.png');self.entry['file']='link.png';self.assertEqual('SYMLINK_REFUSED',self.run_inventory()['rows'][0]['reason'])
 def test_hardlink_refused(self):os.link(self.root/'fixture.png',self.root/'alias.png');self.assertEqual(1,self.run_inventory()['quarantined_count'])
 def test_symlinked_input_root_refused(self):
  alias=self.root/'ROOT_ALIAS';alias.symlink_to(self.root,target_is_directory=True)
  with self.assertRaises(m.Refusal):m.inventory(alias,{'schema':'jarvis.avatar.assets/1','visibility':'PRIVATE_ONLY','inputs':[]})
 def test_bad_sha_refused(self):self.entry['sha256']='b'*64;self.assertEqual('SHA256_MISMATCH',self.run_inventory()['rows'][0]['reason'])
 def test_corrupt_png_crc_refused(self):
  d=self.data[:-1]+bytes([self.data[-1]^1]);(self.root/'fixture.png').write_bytes(d);self.entry['sha256']=hashlib.sha256(d).hexdigest();self.assertEqual('PNG_CRC',self.run_inventory()['rows'][0]['reason'])
 def test_missing_file_error_does_not_expose_path(self):self.entry['file']='private-person-name.png';r=self.run_inventory();self.assertNotIn('private-person-name',json.dumps(r));self.assertEqual(1,r['quarantined_count'])
 def test_large_file_rejected_before_read(self):
  with (self.root/'fixture.png').open('wb') as f:f.truncate(m.MAX_FILE+1)
  self.assertEqual('BYTE_ENVELOPE',self.run_inventory()['rows'][0]['reason'])
 def test_entries_bounded(self):
  with self.assertRaises(m.Refusal):self.run_inventory([self.entry]*301)
 def test_public_manifest_cannot_authorize_private_references(self):
  with self.assertRaises(m.Refusal):m.inventory(self.root,{'schema':'jarvis.avatar.assets/1','visibility':'PUBLIC','inputs':[]})
 def test_mp4_metadata_only_no_audio_claim(self):
  box=lambda k,b=b'':struct.pack('>I',8+len(b))+k+b
  d=box(b'ftyp',b'isom')+box(b'moov')+box(b'mdat');(self.root/'clip.mp4').write_bytes(d)
  e=dict(self.entry,file='clip.mp4',kind='VIDEO',sha256=hashlib.sha256(d).hexdigest());r=self.run_inventory([e]);self.assertEqual('NOT_RUN',r['rows'][0]['metadata']['decode_qualification']);self.assertFalse(r['audio_extracted'])
 def test_corrupt_mp4_box_refused(self):
  with self.assertRaises(m.Refusal):m.mp4_metadata(struct.pack('>I',999)+b'ftyp')
 def test_rejected_hash_reads_still_consume_total_budget(self):
  old=m.MAX_TOTAL;m.MAX_TOTAL=len(self.data)
  try:
   self.entry['sha256']='0'*64;r=self.run_inventory([self.entry,dict(self.entry,id='UNIT_2')])
   self.assertEqual(len(self.data),r['bytes_read']);self.assertEqual('BYTE_ENVELOPE',r['rows'][1]['reason'])
  finally:m.MAX_TOTAL=old
 def test_no_original_copied_deleted_or_modified(self):
  before=(self.root/'fixture.png').read_bytes();r=self.run_inventory();self.assertFalse(r['files_copied']);self.assertEqual(before,(self.root/'fixture.png').read_bytes())
