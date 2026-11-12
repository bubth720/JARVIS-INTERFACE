#!/usr/bin/env bash
# M55-D: install the testOnly lab + test APKs on the running emulator, airplane mode, run the
# instrumentation, pull the measured report and a screenshot. Fails unless every test passed.
set -euo pipefail
api="$1"; out="$2"
apk=interface/labs/m55d/app/build/outputs/apk/debug/app-debug.apk
tapk=interface/labs/m55d/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
mkdir -p "$out"
adb shell cmd connectivity airplane-mode enable || adb shell settings put global airplane_mode_on 1
adb shell settings get global airplane_mode_on | tee "$out/airplane.txt"
adb install -r -t "$apk"; adb install -r -t "$tapk"
adb shell am instrument -w -r fr.jarvis.m55d.lab.test/androidx.test.runner.AndroidJUnitRunner | tee "$out/instrumentation.txt"
adb shell run-as fr.jarvis.m55d.lab cat files/m55d-report.json > "$out/m55d-report.json" || true
adb shell am start -W -n fr.jarvis.m55d.lab/fr.jarvis.m55d.MainActivity > /dev/null; sleep 3
adb exec-out screencap -p > "$out/cockpit-api$api.png" || true
adb logcat -d -b crash > "$out/crash.txt" || true
grep -q '^OK (' "$out/instrumentation.txt" || { echo "Instrumentation did not pass on API $api" >&2; exit 1; }
python3 -c "import json,sys; r=json.load(open(sys.argv[1])); print(json.dumps(r)[:3000])" "$out/m55d-report.json"
