"""Parse Android gfxinfo without conflating modern/legacy/GPU or inventing frame timings."""
import argparse,csv,hashlib,json,math,re
from pathlib import Path

SCHEMA='jarvis.ui.gfxinfo/1'
LONG_MAX=2**63-1

def nearest_rank(values,p):
    assert values and 0<p<=1
    return sorted(values)[math.ceil(p*len(values))-1]

def timed_group(lines):
    reader=list(csv.reader(lines));header=next((r for r in reader if 'IntendedVsync' in r and 'FrameCompleted' in r and 'Flags' in r),None)
    if header is None:return {'status':'UNKNOWN','reason':'FRAMESTATS_HEADER_MISSING','valid_frames':0,'p50_ms':None,'p95_ms':None,'max_ms':None}
    index=reader.index(header);cols={name:header.index(name) for name in ('Flags','IntendedVsync','FrameCompleted')}
    values=[];rejected={};durations=[]
    def refuse(reason):rejected[reason]=rejected.get(reason,0)+1
    for row in reader[index+1:]:
        if not row or not any(x.strip() for x in row):continue
        try:
            flags,start,end=[int(row[cols[x]]) for x in ('Flags','IntendedVsync','FrameCompleted')]
        except (ValueError,IndexError):refuse('MALFORMED_ROW');continue
        if flags!=0:refuse('NONZERO_FLAGS');continue
        if start<=0 or end<=start or start>=LONG_MAX or end>=LONG_MAX:refuse('INVALID_OR_UNFINISHED_TIMESTAMPS');continue
        delta=end-start;values.append(delta/1_000_000);durations.append({'intended_vsync_ns':start,'frame_completed_ns':end,'duration_ns':delta})
    n=len(values)
    return {'status':'AVAILABLE' if n else 'UNKNOWN','clock_rule':'DELTA_WITHIN_ANDROID_FRAMESTATS_DOMAIN_ONLY',
            'valid_frames':n,'rejected_rows':rejected,'samples':durations,
            'p50_ms':nearest_rank(values,.5) if n else None,
            'p95_ms':nearest_rank(values,.95) if n>=20 else None,'p95_status':'MEASURED' if n>=20 else 'NOT_RUN_INSUFFICIENT_COMPARABLE_FRAMES',
            'max_ms':max(values) if n else None,'percentile_estimator':'NEAREST_RANK',
            'outlier_censoring':False}

def parse(text):
    def integer(pattern):
        m=re.search(pattern,text,re.M);return int(m[1]) if m else None
    def counter(label):
        m=re.search(r'^'+re.escape(label)+r':\s*(\d+)\s*\(([0-9.]+)%\)',text,re.M)
        return {'count':int(m[1]),'reported_percent':float(m[2]),'source':'ANDROID_REPORTED_COUNTER'} if m else {'count':None,'reported_percent':None,'source':'ABSENT'}
    total=integer(r'^Total frames rendered:\s*(\d+)')
    reported={'render':{},'gpu':{}}
    for rank,gpu,value in re.findall(r'^(50|90|95|99)th (gpu )?percentile:\s*(\d+)ms\s*$',text,re.M):
        item={'reported_raw_ms':int(value),'source':'ANDROID_REPORTED_BINNED_PERCENTILE',
              'sample_count_sufficient':None if gpu else total is not None and total>=20,'interpretation':'BINNED_NOT_RAW_FRAME_SAMPLES'}
        if gpu and int(value)>=4950:item['interpretation']='TOP_BUCKET_OR_SENTINEL_UNQUALIFIED_EXACT_DURATION_UNKNOWN'
        reported['gpu' if gpu else 'render'][rank]=item
    parts=text.split('---PROFILEDATA---')
    groups=[timed_group(parts[i].strip().splitlines()) for i in range(1,len(parts)-1,2)]
    issues=[]
    modern=counter('Janky frames');legacy=counter('Janky frames (legacy)')
    for label,c in [('modern',modern),('legacy',legacy)]:
        if total is not None and c['count'] is not None and c['count']>total:issues.append(label+'_COUNTER_EXCEEDS_TOTAL')
    if len(re.findall(r'^\*\* Graphics info for pid ',text,re.M))>1:issues.append('MULTIPLE_PROCESS_SECTIONS_DO_NOT_AGGREGATE')
    return {'schema':SCHEMA,'status':'AVAILABLE' if total is not None and not issues else 'UNKNOWN',
            'total_frames_reported':total,'modern_jank':modern,'legacy_jank':legacy,
            'reported_percentiles':reported,'frame_stats_groups':groups,'issues':issues,
            'raw_framestats_available':bool(groups),'aggregation':'WINDOW_GROUPS_SEPARATE_NO_SUM',
            'frame_timing_status':'MEASURED_RAW' if groups and all(g['valid_frames'] for g in groups) else 'NOT_RUN_RAW_FRAMESTATS_ABSENT_OR_INVALID',
            'jank_definitions':'MODERN_AND_LEGACY_KEPT_SEPARATE; no fixed 16.67ms threshold inferred',
            'physical_device_provenance':'UNKNOWN_INPUT_CANNOT_ESTABLISH_DEVICE','llm_ttft':'NOT_RUN'}

def main():
    p=argparse.ArgumentParser();p.add_argument('input',type=Path);p.add_argument('--output',type=Path);a=p.parse_args()
    raw=a.input.read_bytes();r=parse(raw.decode('utf-8'));r['input_sha256']=hashlib.sha256(raw).hexdigest()
    s=json.dumps(r,indent=2)+'\n'
    if a.output:a.output.parent.mkdir(parents=True,exist_ok=True);a.output.write_text(s)
    else:print(s,end='')
if __name__=='__main__':main()
