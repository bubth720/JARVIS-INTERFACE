"""Refuse performance conclusions unless the measured conditions match and provenance exists."""
import argparse,json,re
from pathlib import Path
FIELDS=('api','abi','resolution','density','refresh_rate_hz','profile','animation_scale','scenario','duration_s','emulator_revision','renderer')
def compare(before,after):
    reasons=[]
    for side,report in [('baseline',before),('candidate',after)]:
        if not re.fullmatch('[0-9a-f]{40}',report.get('source_commit','')):reasons.append(side+':SOURCE_COMMIT_MISSING')
        if not re.fullmatch('[0-9a-f]{64}',report.get('apk_sha256','')):reasons.append(side+':APK_SHA256_MISSING')
    for key in FIELDS:
        a=before.get('conditions',{}).get(key);b=after.get('conditions',{}).get(key)
        if a is None or b is None:reasons.append(key+':CONDITION_UNKNOWN')
        elif a!=b:reasons.append(key+':CONDITION_MISMATCH')
    if reasons:return {'status':'BLOCKED_NOT_COMPARABLE','reasons':reasons,'deltas':None,'performance_gain':'NOT_QUALIFIED'}
    result={}
    for name in ('modern_jank','legacy_jank'):
        a=before.get('gfxinfo',{}).get(name,{}).get('reported_percent');b=after.get('gfxinfo',{}).get(name,{}).get('reported_percent')
        result[name+'_reported_percentage_points']=None if a is None or b is None else b-a
    # Raw timing is not pooled between independent windows or inferred from histogram bins.
    a=before.get('gfxinfo',{}).get('frame_stats_groups',[]);b=after.get('gfxinfo',{}).get('frame_stats_groups',[])
    if len(a)==len(b)==1:
        for key in ('p50_ms','p95_ms','max_ms'):
            x=a[0].get(key);y=b[0].get(key);result[key+'_delta']=None if x is None or y is None else y-x
        result['raw_frames_baseline']=a[0].get('valid_frames');result['raw_frames_candidate']=b[0].get('valid_frames')
    return {'status':'COMPARABLE_CONDITIONS','deltas':result,'performance_gain':'REVIEW_RAW_METRICS_NO_AUTOMATIC_PASS',
            'physical_android':'NOT_QUALIFIED','llm_ttft':'NOT_RUN'}
def main():
    p=argparse.ArgumentParser();p.add_argument('baseline',type=Path);p.add_argument('candidate',type=Path);p.add_argument('--output',type=Path);a=p.parse_args()
    report=compare(json.loads(a.baseline.read_text()),json.loads(a.candidate.read_text()));s=json.dumps(report,indent=2)+'\n'
    if a.output:a.output.write_text(s)
    else:print(s,end='')
if __name__=='__main__':main()
