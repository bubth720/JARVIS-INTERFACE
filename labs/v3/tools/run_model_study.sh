#!/usr/bin/env bash
# CONVERSATION-01 Android model comparison on the running emulator (NOT a phone), airplane mode.
# Test APK only: each model goes through the protected A02 load of the app (sealed fd, SHA-256, ctx 768, 1 thread).
# Usage: run_model_study.sh <resources dir with 0.6b.gguf, 1.7b.gguf, bench JSONs> <out dir>
set -uo pipefail
res="$1"; out="$2"; mkdir -p "$out"
apk=interface/labs/v3/app/build/outputs/apk/debug/app-debug.apk
tapk=interface/labs/v3/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell cmd connectivity airplane-mode enable || adb shell settings put global airplane_mode_on 1
adb shell settings get global airplane_mode_on | tee "$out/airplane.txt"; grep -qx 1 "$out/airplane.txt" || exit 1
adb install -r "$apk" && adb install -r -t "$tapk" || exit 1
for f in fr-bench-30.json fr-conv-01.json; do adb push "$res/$f" "/data/local/tmp/$f" >/dev/null; done
adb shell getprop ro.product.model > "$out/device.txt"; adb shell cat /proc/meminfo | head -3 >> "$out/device.txt"
R=fr.jarvis.v3.test/androidx.test.runner.AndroidJUnitRunner; fail=0
study() { local label="$1" file="$2" sha="$3" small="$4"
  adb push "$res/$file" "/data/local/tmp/study.gguf" >/dev/null && adb shell chmod 644 /data/local/tmp/study.gguf
  adb shell am instrument -w -r -e class fr.jarvis.v3.ModelStudyTest#benchOneModel -e model /data/local/tmp/study.gguf -e sha256 "$sha" \
    -e label "$label" -e small "$small" -e benches /data/local/tmp/fr-bench-30.json,/data/local/tmp/fr-conv-01.json "$R" > "$out/instr-$label.txt" 2>&1
  adb exec-out run-as fr.jarvis.v3 cat "files/study-$label.json" > "$out/study-$label.json" 2>/dev/null || rm -f "$out/study-$label.json"
  if grep -q '^OK (1 test)' "$out/instr-$label.txt"; then echo "PASS $label" | tee -a "$out/results.txt"
  else echo "FAIL $label" | tee -a "$out/results.txt"; tail -n 30 "$out/instr-$label.txt" | tee -a "$out/results.txt"; fail=1; fi
  adb shell rm -f /data/local/tmp/study.gguf; }
study android-0.6b-ctx768-1t ${M06:?} ${S06:?} true
study android-1.7b-ctx768-1t ${M17:?} ${S17:?} false
adb shell rm -f /data/local/tmp/fr-*.json
exit $fail
