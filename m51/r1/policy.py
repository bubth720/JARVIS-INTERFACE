"""Strict parsing for real diagnostic evidence; no retry or inferred success."""
import re

def instrumentation_result(text):
    codes=[int(n) for n in re.findall(r'^INSTRUMENTATION_STATUS_CODE:\s*(-?\d+)',text,re.M)]
    ok=re.search(r'(?m)^OK\s*\((\d+) tests?\)',text)
    critical='Process crashed' in text or 'INSTRUMENTATION_FAILED' in text
    return {'started':codes.count(1),'passed':codes.count(0),'failed':sum(n in (-1,-2) for n in codes),
            'completed':bool(ok) and not critical and 'FAILURES!!!' not in text,'critical':critical,
            'reported_total':int(ok[1]) if ok else None}

def system_incident(window, events, crashes):
    if re.search(r'(Application Not Responding:|ANR in)\s*com\.android\.(systemui|launcher3|settings)',window):return 'SYSTEM_ANR_WINDOW'
    if re.search(r'am_(?:anr|crash).*com\.android\.(?:systemui|launcher3|settings)',events):return 'SYSTEM_ANR_OR_CRASH_EVENT'
    if re.search(r'Process: com\.android\.(?:systemui|launcher3|settings)(?:,|\s)',crashes):return 'SYSTEM_FATAL_EXCEPTION'
    return None
