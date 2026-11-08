"""UI frame distributions only; never TTFT. Negative/sentinel values retained and counted, no zeros invented."""
import argparse,json,math
from pathlib import Path
def summarize(data):
    assert data['schema']=='jarvis.m51.frame-metrics/1'
    assert not data['overflow']
    rows=data['rows'];valid=[r for r in rows if r[0]>0 and r[1]>0 and r[2]>0]
    values=sorted(r[2]/1e6 for r in valid)
    def percentile(p):return values[max(0,math.ceil(p*len(values))-1)] if len(values)>=20 else None
    return {'schema':'jarvis.m51.ui-frame-summary/1','package':data['package'],'mode':data['mode'],'api':data['api'],
            'raw_frames':len(rows),'valid_frames':len(valid),'invalid_frames':len(rows)-len(valid),
            'dropped_callbacks':sum(max(0,r[4]) for r in rows),'first_draw_frames':sum(r[3]==1 for r in rows),
            'total_duration_ms':{'p50':percentile(.5),'p95':percentile(.95),'maximum':max(values) if len(values)>=20 else None},
            'status':'MEASURED_UI_FRAME_DISTRIBUTION' if len(values)>=20 else 'INSUFFICIENT_UI_FRAMES',
            'clock_domain':data['clock_domain'],'cross_clock_subtraction':False,'physical_android':'NOT_RUN','llm_ttft':'NOT_RUN'}
def main():
    p=argparse.ArgumentParser();p.add_argument('input',type=Path);p.add_argument('--output',type=Path,required=True);a=p.parse_args()
    result=summarize(json.loads(a.input.read_text()));a.output.write_text(json.dumps(result,indent=2)+'\n');print(json.dumps(result))
if __name__=='__main__':main()
