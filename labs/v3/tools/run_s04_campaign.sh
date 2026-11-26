#!/usr/bin/env bash
# Use the unchanged full campaign. Also require the audit verdict and retain the last gallery checkpoint.
set -uo pipefail
api=$1
resources=$2
out=$3
tools=$(dirname "$0")
qualification=0
CAPTURES_ONLY=0 bash "$tools/run_emulator.sh" "$api" "$resources" "$out" || qualification=$?
if ! timeout 30 adb exec-out run-as fr.jarvis.v3 cat files/v3-gallery-trace.json < /dev/null > "$out/v3-gallery-trace.json"; then
    rm -f "$out/v3-gallery-trace.json"
else
    python3 - "$out/v3-gallery-trace.json" <<'PY'
import json
import sys
from pathlib import Path

trace = json.loads(Path(sys.argv[1]).read_text())
events = trace["events"]
waits = [(right["elapsedMs"] - left["elapsedMs"], left["event"]) for left, right in zip(events, events[1:])]
print("Gallery last checkpoint:", trace["lastEvent"])
print("Gallery longest intervals (ms):", sorted(waits, reverse=True)[:5])
PY
fi
python3 "$tools/check_s04_campaign.py" "$api" "$out" | tee "$out/s04-verdict.txt"
verdict=$?
if [ "$qualification" -ne 0 ]; then exit "$qualification"; fi
exit "$verdict"
