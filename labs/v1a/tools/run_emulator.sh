#!/usr/bin/env bash
# Run the full V1-A instrumentation (sandbox A02 + CORE, and the REAL model test) on the running
# emulator with the SAME APKs built earlier. Fails unless every test passed AND the real-model
# test actually executed (not skipped). Airplane mode is enabled first.
set -euo pipefail
api="$1"; model="$2"; sha="$3"; out="$4"
apk=interface/labs/v1a/app/build/outputs/apk/debug/app-debug.apk
tapk=interface/labs/v1a/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
mkdir -p "$out"
adb shell cmd connectivity airplane-mode enable || adb shell settings put global airplane_mode_on 1
adb shell settings get global airplane_mode_on | tee "$out/airplane.txt"
adb install -r "$apk"; adb install -r "$tapk"
adb push "$model" /data/local/tmp/v1a-model.gguf
adb shell chmod 644 /data/local/tmp/v1a-model.gguf
adb shell am instrument -w -r -e modelPath /data/local/tmp/v1a-model.gguf -e modelSha "$sha" \
  fr.jarvis.v1a.lab.test/androidx.test.runner.AndroidJUnitRunner | tee "$out/instrumentation.txt"
adb shell run-as fr.jarvis.v1a.lab cat files/v1a-real-report.json > "$out/v1a-real-report.json" || true
adb logcat -d > "$out/logcat.txt" || true
adb shell rm -f /data/local/tmp/v1a-model.gguf
grep -q '^OK (' "$out/instrumentation.txt" || { echo "Instrumentation did not pass on API $api" >&2; exit 1; }
grep -q 'realOfflineConversationThroughCoreAndA02' "$out/instrumentation.txt"
grep -q 'AssumptionViolatedException' "$out/instrumentation.txt" && { echo 'Real-model test was skipped' >&2; exit 1; }
python3 -c "import json,sys; r=json.load(open(sys.argv[1])); assert len(r['turns'])==3 and r['unload']=='CONFIRMED' and r['trace']['sealedInodeMapped']; print(json.dumps(r, ensure_ascii=False)[:2000])" "$out/v1a-real-report.json"
