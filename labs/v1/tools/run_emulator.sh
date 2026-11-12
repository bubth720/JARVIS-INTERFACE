#!/usr/bin/env bash
# JARVIS V1 qualification on the running emulator (NOT a phone): airplane mode, install, push the three
# verified resources and two French utterances, run the REQUIRED end-to-end tests (manifest + real voice
# conversation), then the real-microphone test (recorded separately: it depends on the emulator audio input).
set -euo pipefail
api="$1"; res="$2"; out="$3"
apk=interface/labs/v1/app/build/outputs/apk/debug/app-debug.apk
tapk=interface/labs/v1/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
mkdir -p "$out"
adb shell cmd connectivity airplane-mode enable || adb shell settings put global airplane_mode_on 1
adb shell settings get global airplane_mode_on | tee "$out/airplane.txt"; grep -qx 1 "$out/airplane.txt"
adb install -r -t "$apk"; adb install -r -t "$tapk"
for f in llm.gguf stt.bin tts.zip utt1.wav utt2.wav; do adb push "$res/$f" "/data/local/tmp/v1-$f" >/dev/null; adb shell chmod 644 "/data/local/tmp/v1-$f"; done
T=fr.jarvis.v1.V1VoiceEndToEndTest
adb shell am instrument -w -r -e class "$T#a_manifestOnlyMicrophoneNoNetworkTestOnly,$T#b_realVoiceConversationTwoExchangesInterruptionAndRelease" \
  -e llmPath /data/local/tmp/v1-llm.gguf -e sttPath /data/local/tmp/v1-stt.bin -e ttsZip /data/local/tmp/v1-tts.zip \
  -e wav1 /data/local/tmp/v1-utt1.wav -e wav2 /data/local/tmp/v1-utt2.wav \
  fr.jarvis.v1.test/androidx.test.runner.AndroidJUnitRunner | tee "$out/instrumentation-e2e.txt"
adb shell run-as fr.jarvis.v1 cat files/v1-report.json > "$out/v1-report.json" || true
# Real microphone (AudioRecord). If PulseAudio feeds the emulator's virtual microphone, play utterance 1 meanwhile.
player=""; if command -v paplay >/dev/null && pactl info >/dev/null 2>&1; then (sleep 4; for i in 1 2 3; do paplay --device=vspk "$res/utt1.wav"; done) & player=$!; fi
adb shell am instrument -w -r -e class "$T#c_realAndroidMicrophoneCapture" fr.jarvis.v1.test/androidx.test.runner.AndroidJUnitRunner | tee "$out/instrumentation-mic.txt" || true
[ -n "$player" ] && kill "$player" 2>/dev/null || true
adb shell run-as fr.jarvis.v1 cat files/v1-mic.json > "$out/v1-mic.json" || true
adb shell am start -W -n fr.jarvis.v1/.MainActivity >/dev/null; sleep 4; adb exec-out screencap -p > "$out/v1-api$api.png" || true
adb logcat -d -b crash > "$out/crash.txt" || true
adb shell rm -f /data/local/tmp/v1-* || true
grep -q '^OK (2 tests)' "$out/instrumentation-e2e.txt" || { echo "REQUIRED end-to-end tests did not pass on API $api" >&2; exit 1; }
python3 -c "import json,sys; r=json.load(open(sys.argv[1])); print(json.dumps({k:r[k] for k in ('sdk','loadMs','voiceTurns','interruption','release')}, ensure_ascii=False)[:4000])" "$out/v1-report.json"
