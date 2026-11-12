#!/usr/bin/env bash
# Run the full V1-B instrumentation (sandbox A02 + CORE, and the REAL model test) on the running
# emulator with the SAME APKs built earlier. Fails unless every test passed AND the real-model
# test actually executed (not skipped). Airplane mode is enabled first. Dialogue quality is judged
# afterwards by the pinned JARVIS-LLM a02/tests/dialogue_gate.py (separate workflow step).
set -euo pipefail
api="$1"; model="$2"; sha="$3"; out="$4"
apk=interface/labs/v1b/app/build/outputs/apk/debug/app-debug.apk
tapk=interface/labs/v1b/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
mkdir -p "$out"
adb shell cmd connectivity airplane-mode enable || adb shell settings put global airplane_mode_on 1
adb shell settings get global airplane_mode_on | tee "$out/airplane.txt"
grep -qx 1 "$out/airplane.txt"
adb install -r -t "$apk"; adb install -r -t "$tapk"
adb push "$model" /data/local/tmp/v1b-model.gguf
adb shell chmod 644 /data/local/tmp/v1b-model.gguf
adb shell am instrument -w -r -e modelPath /data/local/tmp/v1b-model.gguf -e modelSha "$sha" \
  fr.jarvis.v1b.lab.test/androidx.test.runner.AndroidJUnitRunner | tee "$out/instrumentation.txt"
adb shell run-as fr.jarvis.v1b.lab cat files/v1b-real-report.json > "$out/v1b-real-report.json" || true
adb logcat -d > "$out/logcat.txt" || true
adb shell rm -f /data/local/tmp/v1b-model.gguf
grep -q '^OK (' "$out/instrumentation.txt" || { echo "Instrumentation did not pass on API $api" >&2; exit 1; }
grep -q 'realOfflineV1bConversationThroughCoreAndA02' "$out/instrumentation.txt"
grep -q 'AssumptionViolatedException' "$out/instrumentation.txt" && { echo 'Real-model test was skipped' >&2; exit 1; }
python3 - "$out/v1b-real-report.json" <<'PY'
import json, sys
r = json.load(open(sys.argv[1]))
assert len(r["v1bDialogues"]) == 4 and all(len(d["turns"]) == 3 for d in r["v1bDialogues"])
assert r["streamingObserved"] and r["trace"]["sealedInodeMapped"] and r["trace"]["protectedOutputCeiling"] == 96
assert {b["maxTokens"] for b in r["v1bBench"]} == {48, 128, 256} and len(r["reloadCycles"]) == 5
print(json.dumps({k: r[k] for k in ("sdk", "loadMs", "stopDrainMs", "v1bBench")}, ensure_ascii=False)[:3000])
PY
