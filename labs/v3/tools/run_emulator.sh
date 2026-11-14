#!/usr/bin/env bash
# JARVIS V3 qualification on the running emulator (NOT a phone), airplane mode. Each instrumentation is a
# new app process. Animations are re-enabled for the V3 visuals (the runner disables them), then set to 0
# for the reduced-motion test only. Every test is recorded PASS/FAIL; the script fails if a required one failed.
set -uo pipefail
api="$1"; res="$2"; out="$3"
apk=interface/labs/v3/app/build/outputs/apk/debug/app-debug.apk
tapk=interface/labs/v3/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
mkdir -p "$out/shots"; : > "$out/results.txt"
adb shell cmd connectivity airplane-mode enable || adb shell settings put global airplane_mode_on 1
adb shell settings get global airplane_mode_on | tee "$out/airplane.txt"; grep -qx 1 "$out/airplane.txt" || { echo "airplane mode not enabled" >&2; exit 1; }
anim() { for k in window_animation_scale transition_animation_scale animator_duration_scale; do adb shell settings put global $k "$1"; done; }
anim 1
# Show the soft keyboard even though the emulator declares a hardware keyboard (keyboard state captures).
adb shell settings put secure show_ime_with_hard_keyboard 1
adb shell wm size | tee "$out/screen.txt"; adb shell wm density | tee -a "$out/screen.txt"
adb install -r "$apk" || { echo "V3 APK install failed (without -t)" >&2; exit 1; }
adb install -r -t "$tapk"
for f in llm.gguf stt.bin tts.zip utt1.wav; do adb push "$res/$f" "/data/local/tmp/v3-$f" >/dev/null; adb shell chmod 644 "/data/local/tmp/v3-$f"; done
T=fr.jarvis.v3.V3CockpitTest; R=fr.jarvis.v3.test/androidx.test.runner.AndroidJUnitRunner; fail=0
run() { local name="$1"; shift
  adb shell am broadcast -a android.intent.action.CLOSE_SYSTEM_DIALOGS > /dev/null 2>&1 || true
  adb logcat -c || true
  adb shell am instrument -w -r -e class "$T#$name" "$@" "$R" > "$out/instr-$name.txt" 2>&1
  if grep -q '^OK (1 test)' "$out/instr-$name.txt"; then echo "PASS $name" | tee -a "$out/results.txt"
  else echo "FAIL $name" | tee -a "$out/results.txt"; fail=1
       # Evidence of the failure: screen at that moment, end of the instrumentation output, errors in logcat.
       adb exec-out screencap -p > "$out/shots/api$api-FAIL-$name.png" || true
       { echo "--- instrumentation (tail)"; tail -n 25 "$out/instr-$name.txt"; echo "--- logcat errors"; adb logcat -d -t 400 '*:E' | grep -vE 'emulator|goldfish|ranchu' | tail -n 25; } | tee -a "$out/results.txt"; fi; }
pull() { adb exec-out run-as fr.jarvis.v3 cat "files/$1" > "$out/$1" 2>/dev/null || rm -f "$out/$1"; }
run a_manifestSinglePermissionNoNetworkInstallable
run b_firstLaunchThenVerifiedImport -e llmPath /data/local/tmp/v3-llm.gguf -e sttPath /data/local/tmp/v3-stt.bin -e ttsZip /data/local/tmp/v3-tts.zip; pull v3-first-launch.json
run c_realEngineStatesCardsMenuLandscapeBackground -e afterImport 1 -e wav1 /data/local/tmp/v3-utt1.wav; pull v3-states.json
run f_conversationContextPersonaResume -e afterImport 1; pull v3-conv.json
run g_localKnowledgeCitedAnswer -e afterImport 1; pull v3-knowledge.json
run h_consentedMemoryProposeConfirmRecallDelete -e afterImport 1; pull v3-memory.json
run i_longConversationStaysCoherentAndBounded -e afterImport 1; pull v3-long.json
# AUDIT-GLOBAL-01 campaign T1: tests written to EXPOSE defects; recorded in audit-results.txt, never in the verdict.
audit() { local name="$1"; shift
  adb shell am instrument -w -r -e class "fr.jarvis.v3.AuditCampaignTest#$name" "$@" "$R" > "$out/audit-$name.txt" 2>&1
  if grep -q '^OK (1 test)' "$out/audit-$name.txt"; then echo "PASS $name" >> "$out/audit-results.txt"
  else { echo "FAIL $name"; grep -m3 -E "AssertionError|Exception|Timeout" "$out/audit-$name.txt"; } >> "$out/audit-results.txt"; fi
  for f in $(adb shell run-as fr.jarvis.v3 ls files 2>/dev/null | tr -d '\r' | grep '^audit-'); do adb exec-out run-as fr.jarvis.v3 cat "files/$f" > "$out/$f"; done; }
for t in b3_ambiguousMemoriesAreNotInjectedWithoutChoice c3_unknownQuestionsAreNotInvented c8_generalAnswerIsNeverPresentedAsDocumentary \
         d2_voiceErrorsLeaveTheSessionUsableWithoutDuplicates d3_interruptionAtEachStage f2f3f4_documentsContradictoryCorruptAndInjected \
         f6_deletionIsVerifiedOnDisk g6_modelOrDocumentCannotCreateAMemory; do audit "$t" -e afterImport 1; done
audit2() { local name="$1"; shift
  adb shell am instrument -w -r -e class "fr.jarvis.v3.AuditCampaignT2Test#$name" "$@" "$R" > "$out/audit-$name.txt" 2>&1
  if grep -q '^OK (1 test)' "$out/audit-$name.txt"; then echo "PASS $name" >> "$out/audit-results.txt"
  else { echo "FAIL $name"; grep -m3 -E "AssertionError|Exception|Timeout" "$out/audit-$name.txt"; } >> "$out/audit-results.txt"; fi
  for f in $(adb shell run-as fr.jarvis.v3 ls files 2>/dev/null | tr -d '\r' | grep '^audit-'); do adb exec-out run-as fr.jarvis.v3 cat "files/$f" > "$out/$f"; done; }
for t in a3_knowledgeImportFormatsAndLimits a4_corruptFutureAndSymlinkDatabasesRefusedAndKept a5_memoryConsentMismatchAndExpiry \
         e2e3e6e7_layoutStressAccessibilityAvatar g1_microphoneRevokedTextStillWorks g2_thermalSevereRefusesTurn h4_loadUnloadCyclesDoNotLeak; do audit2 "$t" -e afterImport 1; done
adb shell pm grant fr.jarvis.v3 android.permission.RECORD_AUDIO || true
audit3() { local name="$1"; shift
  adb shell am instrument -w -r -e class "fr.jarvis.v3.AuditCampaignT3Test#$name" "$@" "$R" > "$out/audit-$name.txt" 2>&1
  if grep -q '^OK (1 test)' "$out/audit-$name.txt"; then echo "PASS $name" >> "$out/audit-results.txt"
  else { echo "FAIL $name"; grep -m3 -E "AssertionError|Exception|Timeout" "$out/audit-$name.txt"; } >> "$out/audit-results.txt"; fi
  for f in $(adb shell run-as fr.jarvis.v3 ls files 2>/dev/null | tr -d '\r' | grep '^audit-'); do adb exec-out run-as fr.jarvis.v3 cat "files/$f" > "$out/$f"; done; }
for t in j_sessionLifecycleNeverDeadlocks k_sphereStillAtEveryNonAnswerTransition l_hiddenInjectionsInADocument \
         m_longConversationMemoryAndLatency n_resumeKeepsSystemNotices; do audit3 "$t" -e afterImport 1; done
anim 0; run d_reducedMotionStaticSphereAndFirstText -e afterImport 1; pull v3-reduced.json; anim 1
run e_galleryCapturesAndBeforeAfterBench; pull v3-bench.json
for f in $(adb shell run-as fr.jarvis.v3 ls files/v3shots 2>/dev/null | tr -d '\r'); do adb exec-out run-as fr.jarvis.v3 cat "files/v3shots/$f" > "$out/shots/api$api-$f"; done
adb shell am force-stop fr.jarvis.v3
adb shell am start -W -n fr.jarvis.v3/.CockpitActivity > "$out/reopen.txt"; sleep 6
if adb shell pidof fr.jarvis.v3 > /dev/null; then echo "PASS reopen_after_force_stop" | tee -a "$out/results.txt"; else echo "FAIL reopen_after_force_stop" | tee -a "$out/results.txt"; fail=1; fi
adb logcat -d -b crash > "$out/crash.txt" || true
if grep -q "fr.jarvis.v3" "$out/crash.txt"; then echo "FAIL no_crash" | tee -a "$out/results.txt"; fail=1; else echo "PASS no_crash" | tee -a "$out/results.txt"; fi
adb shell rm -f /data/local/tmp/v3-* || true
echo "=== API $api results"; cat "$out/results.txt"
exit $fail
