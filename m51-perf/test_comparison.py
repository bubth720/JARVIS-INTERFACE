import importlib.util,unittest,copy
from pathlib import Path
S=importlib.util.spec_from_file_location('comparison',Path(__file__).with_name('comparison.py'));M=importlib.util.module_from_spec(S);S.loader.exec_module(M)
class ComparisonTests(unittest.TestCase):
 def reports(self):
  before={'source_commit':'a'*40,'apk_sha256':'b'*64,'conditions':dict(zip(M.FIELDS,(35,'x86_64','1080x2400',420,60,'ECO',0,'idle',30,'37.2.13','swiftshader'))),'gfxinfo':{'modern_jank':{'reported_percent':90},'legacy_jank':{'reported_percent':2},'frame_stats_groups':[{'valid_frames':20,'p50_ms':10,'p95_ms':20,'max_ms':30}]}}
  after=copy.deepcopy(before);after['source_commit']='c'*40;after['apk_sha256']='d'*64;return before,after
 def test_matching_conditions_separate_modern_legacy(self):
  a,b=self.reports();b['gfxinfo']['modern_jank']['reported_percent']=80;b['gfxinfo']['legacy_jank']['reported_percent']=3;r=M.compare(a,b);self.assertEqual(-10,r['deltas']['modern_jank_reported_percentage_points']);self.assertEqual(1,r['deltas']['legacy_jank_reported_percentage_points'])
 def test_each_missing_condition_blocks(self):
  for key in M.FIELDS:
   a,b=self.reports();del b['conditions'][key];self.assertEqual('BLOCKED_NOT_COMPARABLE',M.compare(a,b)['status'])
 def test_different_resolution_or_profile_blocks(self):
  a,b=self.reports();b['conditions']['profile']='BALANCED';r=M.compare(a,b);self.assertIsNone(r['deltas'])
 def test_missing_provenance_blocks(self):
  a,b=self.reports();del b['source_commit'];self.assertEqual('BLOCKED_NOT_COMPARABLE',M.compare(a,b)['status'])
 def test_missing_p95_stays_null(self):
  a,b=self.reports();b['gfxinfo']['frame_stats_groups'][0]['p95_ms']=None;r=M.compare(a,b);self.assertIsNone(r['deltas']['p95_ms_delta'])
 def test_multiple_windows_never_aggregate_timings(self):
  a,b=self.reports();b['gfxinfo']['frame_stats_groups']*=2;self.assertNotIn('p50_ms_delta',M.compare(a,b)['deltas'])
 def test_both_missing_metric_not_replaced_by_zero(self):
  a,b=self.reports();a['gfxinfo']['modern_jank']={};b['gfxinfo']['modern_jank']={};self.assertIsNone(M.compare(a,b)['deltas']['modern_jank_reported_percentage_points'])
if __name__=='__main__':unittest.main()
