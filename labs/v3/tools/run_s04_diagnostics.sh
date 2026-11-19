#!/usr/bin/env bash
# Reproduce the S04 failures on an ephemeral emulator. The full campaign uses run_emulator.sh.
set -euo pipefail
res=$1
out=$2
mkdir -p "$out"
apk=interface/labs/v3/app/build/outputs/apk
A() { timeout 60 adb "$@" < /dev/null; }
A shell cmd connectivity airplane-mode enable
A shell settings get global airplane_mode_on | tr -d '\r' | grep -qx 1
for setting in window_animation_scale transition_animation_scale animator_duration_scale; do
    A shell settings put global "$setting" 1
done
A shell settings put secure show_ime_with_hard_keyboard 1
A install -r "$apk/debug/app-debug.apk"
A install -r -t "$apk/androidTest/debug/app-debug-androidTest.apk"
for f in llm.gguf stt.bin tts.zip utt1.wav; do
    timeout 600 adb push "$res/$f" "/data/local/tmp/v3-$f"
    A shell chmod 644 "/data/local/tmp/v3-$f"
done

fail=0
run() {
    local class=$1 name=$2 limit=$3 report=$4
    shift 4
    A shell am broadcast -a android.intent.action.CLOSE_SYSTEM_DIALOGS > /dev/null 2>&1 || true
    A logcat -c
    local result=0
    timeout "$limit" adb shell am instrument -w -r -e class "fr.jarvis.v3.$class#$name" "$@" \
        fr.jarvis.v3.test/androidx.test.runner.AndroidJUnitRunner < /dev/null > "$out/$name.txt" 2>&1 || result=$?
    if [[ -n "$report" ]]; then
        A exec-out run-as fr.jarvis.v3 cat "files/$report" > "$out/$report" || rm -f "$out/$report"
    fi
    if [[ $result -eq 0 ]] && grep -q '^OK (1 test)' "$out/$name.txt"; then
        echo "PASS $name"
    else
        echo "FAIL $name (instrumentation exit $result)"
        tail -n 35 "$out/$name.txt"
        fail=1
        A exec-out screencap -p > "$out/$name.png" || true
        A logcat -d -t 800 > "$out/$name.logcat" || true
        A shell am force-stop fr.jarvis.v3 || true
    fi
}

run V3CockpitTest a_manifestSinglePermissionNoNetworkInstallable 120 ''
run V3CockpitTest b_firstLaunchThenVerifiedImport 600 v3-first-launch.json -e llmPath /data/local/tmp/v3-llm.gguf \
    -e sttPath /data/local/tmp/v3-stt.bin -e ttsZip /data/local/tmp/v3-tts.zip
if [[ $fail -ne 0 ]]; then
    echo "NOT_RUN c, K, P (setup or verified import failed)"
    exit "$fail"
fi
run V3CockpitTest c_realEngineStatesCardsMenuLandscapeBackground 600 v3-states.json -e afterImport 1 -e wav1 /data/local/tmp/v3-utt1.wav
if [[ -s "$out/v3-states.json" ]]; then jq -c '{tabTrace}' "$out/v3-states.json"; fi
A shell pm grant fr.jarvis.v3 android.permission.RECORD_AUDIO
run AuditCampaignT3Test k_sphereStillAtEveryNonAnswerTransition 420 audit-k.json -e afterImport 1
run AuditCampaignT5Test p_sphereStillAndCheapOutsideTheAnswer 600 audit-p.json -e afterImport 1
for report in audit-k.json audit-p.json; do
    if [[ -s "$out/$report" ]]; then jq -c . "$out/$report"; fi
done
exit "$fail"
