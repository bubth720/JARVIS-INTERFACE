import unittest
from summarize_frames import summarize
class Frames(unittest.TestCase):
    def data(self,rows):return {'schema':'jarvis.m51.frame-metrics/1','overflow':False,'rows':rows,'package':'fr.jarvis.v1.lab','mode':'ANIMATED','api':35,'clock_domain':'ANDROID_FRAME_METRICS_REPORTED'}
    def test_small_idle_window_never_invents_percentile(self):
        r=summarize(self.data([[1,1,1000,0,0,-1]]));self.assertIsNone(r['total_duration_ms']['p95']);self.assertEqual(r['status'],'INSUFFICIENT_UI_FRAMES')
    def test_outlier_preserved_and_invalid_not_silently_zeroed(self):
        rows=[[i+1,i+1,(i+1)*1000000,0,0,-1] for i in range(20)]
        rows[-1][2]=9000000000;rows.append([0,0,-1,0,2,-1]);r=summarize(self.data(rows))
        self.assertEqual(r['valid_frames'],20);self.assertEqual(r['invalid_frames'],1);self.assertEqual(r['total_duration_ms']['maximum'],9000);self.assertEqual(r['total_duration_ms']['p95'],19);self.assertEqual(r['dropped_callbacks'],2)
    def test_overflow_refuses_distribution(self):
        d=self.data([]);d['overflow']=True
        with self.assertRaises(AssertionError):summarize(d)
if __name__=='__main__':unittest.main()
