import importlib.util,unittest
from pathlib import Path
S=importlib.util.spec_from_file_location('gfxinfo',Path(__file__).with_name('gfxinfo.py'));M=importlib.util.module_from_spec(S);S.loader.exec_module(M)
ROOT=Path(__file__).resolve().parents[1]

def frames(rows):return '---PROFILEDATA---\nFlags,IntendedVsync,FrameCompleted,\n'+''.join(','.join(map(str,r))+',\n' for r in rows)+'---PROFILEDATA---\n'

class GfxInfoTests(unittest.TestCase):
 def test_historical_phone_modern_and_legacy_remain_different(self):
  r=M.parse((ROOT/'docs/evidence/ui-01c/phone-frames.txt').read_text());self.assertEqual(151,r['total_frames_reported']);self.assertEqual(94.70,r['modern_jank']['reported_percent']);self.assertEqual(.66,r['legacy_jank']['reported_percent']);self.assertFalse(r['raw_framestats_available']);self.assertEqual('NOT_RUN_RAW_FRAMESTATS_ABSENT_OR_INVALID',r['frame_timing_status'])
 def test_historical_tablet_gpu_top_bucket_not_exact_duration(self):
  r=M.parse((ROOT/'docs/evidence/ui-01c/tablet-frames.txt').read_text());self.assertEqual(98.01,r['modern_jank']['reported_percent']);self.assertEqual(51.66,r['legacy_jank']['reported_percent']);self.assertEqual(4950,r['reported_percentiles']['gpu']['95']['reported_raw_ms']);self.assertIn('UNQUALIFIED',r['reported_percentiles']['gpu']['95']['interpretation'])
 def test_raw_twenty_nearest_rank_without_clock_origin_subtraction(self):
  rows=[(0,10**15+i*10**9,10**15+i*10**9+(i+1)*10**6) for i in range(20)];g=M.parse(frames(rows))['frame_stats_groups'][0];self.assertEqual(10,g['p50_ms']);self.assertEqual(19,g['p95_ms']);self.assertEqual(20,g['max_ms']);self.assertEqual(20,len(g['samples']))
 def test_p95_absent_under_twenty(self):
  g=M.parse(frames([(0,100,200)]))['frame_stats_groups'][0];self.assertIsNone(g['p95_ms']);self.assertEqual(1,g['valid_frames'])
 def test_invalid_and_flagged_rows_are_counted(self):
  g=M.parse(frames([(1,100,200),(0,0,200),(0,200,100),(0,100,2**63-1),(0,100,200)]))['frame_stats_groups'][0];self.assertEqual(1,g['valid_frames']);self.assertEqual(1,g['rejected_rows']['NONZERO_FLAGS']);self.assertEqual(3,g['rejected_rows']['INVALID_OR_UNFINISHED_TIMESTAMPS'])
 def test_large_valid_outlier_not_removed(self):
  rows=[(0,i*10**10+1,i*10**10+1000001) for i in range(19)]+[(0,10**15,10**15+60*10**9)];g=M.parse(frames(rows))['frame_stats_groups'][0];self.assertEqual(60000,g['max_ms']);self.assertFalse(g['outlier_censoring'])
 def test_missing_data_is_null(self):
  r=M.parse('');self.assertEqual('UNKNOWN',r['status']);self.assertIsNone(r['total_frames_reported']);self.assertIsNone(r['modern_jank']['count'])
 def test_multiple_windows_are_not_summed(self):
  r=M.parse(frames([(0,100,200)])+frames([(0,100,200)]));self.assertEqual(2,len(r['frame_stats_groups']));self.assertNotIn('total_raw_frames',r)
 def test_counter_contradiction_flagged(self):
  r=M.parse('Total frames rendered: 1\nJanky frames: 2 (200.00%)\n');self.assertEqual('UNKNOWN',r['status']);self.assertIn('modern_COUNTER_EXCEEDS_TOTAL',r['issues'])
 def test_changed_csv_column_order_is_supported(self):
  t='---PROFILEDATA---\nFrameCompleted,Flags,IntendedVsync\n200,0,100\n---PROFILEDATA---';g=M.parse(t)['frame_stats_groups'][0];self.assertEqual(1,g['valid_frames'])
 def test_unknown_header_not_fabricated(self):
  r=M.parse('---PROFILEDATA---\nSomething,Else\n1,2\n---PROFILEDATA---');self.assertEqual('UNKNOWN',r['frame_stats_groups'][0]['status'])
 def test_multi_process_summary_is_not_treated_as_one_measurement(self):
  r=M.parse('** Graphics info for pid 1 [a] **\nTotal frames rendered: 1\n** Graphics info for pid 2 [a] **\nTotal frames rendered: 2\n');self.assertEqual('UNKNOWN',r['status'])
if __name__=='__main__':unittest.main()
