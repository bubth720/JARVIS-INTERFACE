#!/usr/bin/env bash
# JARVIS V1 qualification on the running emulator (NOT a phone), airplane mode.
# Every instrumentation run below is a NEW app process (am instrument restarts the package), so tests d/f/g
# also prove a cold restart with the resources kept in private storage (no re-import).
# Each test is recorded PASS/FAIL in results.txt; the script fails at the end if a REQUIRED one failed.
set -uo pipefail
api="$1"; res="$2"; out="$3"
apk=interface/labs/v1/app/build/outputs/apk/debug/app-debug.apk
tapk=interface/labs/v1/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
mkdir -p "$out"; : > "$out/results.txt"
adb shell cmd connectivity airplane-mode enable || adb shell settings put global airplane_mode_on 1
adb shell settings get global airplane_mode_on | tee "$out/airplane.txt"; grep -qx 1 "$out/airplane.txt" || { echo "airplane mode not enabled" >&2; exit 1; }
adb shell dumpsys connectivity | grep -iE "^Active default network|NetworkAgentInfo" | head -5 > "$out/network.txt" || true
# Installed WITHOUT -t: the delivered APK is not testOnly (same binary as the published artifact).
adb install -r "$apk" || { echo "delivery APK install failed (without -t)" >&2; exit 1; }
adb install -r -t "$tapk"
for f in llm.gguf stt.bin tts.zip utt1.wav utt2.wav; do adb push "$res/$f" "/data/local/tmp/v1-$f" >/dev/null; adb shell chmod 644 "/data/local/tmp/v1-$f"; done
T=fr.jarvis.v1.V1VoiceEndToEndTest
R=fr.jarvis.v1.test/androidx.test.runner.AndroidJUnitRunner
fail=0
run() { # name required extra-args...
  local name="$1" req="$2"; shift 2
  adb shell am instrument -w -r -e class "$T#$name" "$@" "$R" > "$out/instr-$name.txt" 2>&1
  if grep -q '^OK (1 test)' "$out/instr-$name.txt"; then echo "PASS $name" | tee -a "$out/results.txt"
  else echo "FAIL $name ($req)" | tee -a "$out/results.txt"; grep -m3 -E "AssertionError|Exception|Timeout" "$out/instr-$name.txt" | tee -a "$out/results.txt"
       [ "$req" = required ] && fail=1; fi
}
pull() { adb exec-out run-as fr.jarvis.v1 cat "files/$1" > "$out/$1" 2>/dev/null || rm -f "$out/$1"; }
run a_manifestOnlyMicrophoneNoNetworkTestOnly required
run b_realVoiceConversationTwoExchangesInterruptionAndRelease required \
  -e llmPath /data/local/tmp/v1-llm.gguf -e sttPath /data/local/tmp/v1-stt.bin -e ttsZip /data/local/tmp/v1-tts.zip \
  -e wav1 /data/local/tmp/v1-utt1.wav -e wav2 /data/local/tmp/v1-utt2.wav
pull v1-report.json
# Real microphone (AudioRecord) on the emulator: informational, the emulator only delivers silence.
adb shell pm grant fr.jarvis.v1 android.permission.RECORD_AUDIO || true
run c_realAndroidMicrophoneCapture informational; pull v1-mic.json
run d_coldRestartResourcesPersistAndTextConversationKeepsContext required -e afterImport 1; pull v1-restart.json
run f_realScreenTypedQuestionShowsGeneratedAnswer required -e afterImport 1; pull v1-ui.json; pull v1-ui.png
run h_pocketSynthesisIsRealSpeechRecognisedBySttRoundTrip required -e afterImport 1; pull v1-tts.json
run g_textOnlyWithoutVoiceResources required -e afterImport 1; pull v1-textonly.json
# Close and reopen the real app (force-stop = full process kill), then screenshot the reopened screen.
adb shell am force-stop fr.jarvis.v1
adb shell am start -W -n fr.jarvis.v1/.MainActivity > "$out/reopen.txt"; sleep 5
adb shell pidof fr.jarvis.v1 > "$out/reopen-pid.txt" && echo "PASS reopen_after_force_stop" | tee -a "$out/results.txt" || { echo "FAIL reopen_after_force_stop (required)" | tee -a "$out/results.txt"; fail=1; }
adb exec-out screencap -p > "$out/v1-api$api-reopened.png" || true
adb logcat -d -b crash > "$out/crash.txt" || true
if grep -q "fr.jarvis.v1" "$out/crash.txt"; then echo "FAIL no_crash (required)" | tee -a "$out/results.txt"; fail=1; else echo "PASS no_crash" | tee -a "$out/results.txt"; fi
adb shell rm -f /data/local/tmp/v1-* || true
echo "=== API $api results"; cat "$out/results.txt"
exit $fail
