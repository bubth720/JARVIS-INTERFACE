#!/usr/bin/env bash
# JARVIS V3 qualification on the running emulator (NOT a phone), airplane mode. Each instrumentation is a
# new app process. Animations are re-enabled for the V3 visuals (the runner disables them), then set to 0
# for the reduced-motion test only. Every test is recorded PASS/FAIL; the script fails if a required one failed.
set -uo pipefail
api="$1"; res="$2"; out="$3"
apk=interface/labs/v3/app/build/outputs/apk/debug/app-debug.apk
tapk=interface/labs/v3/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
mkdir -p "$out/shots"; : > "$out/results.txt"
T=fr.jarvis.v3.V3CockpitTest; R=fr.jarvis.v3.test/androidx.test.runner.AndroidJUnitRunner; fail=0
# S6 / A-029 (run 43643453245): the emulator went offline after test c and every later adb call waited forever
# (2 h 40 min, job killed, no results). Owner rule (15/11): a lost emulator must stop the run FAST with a clear
# diagnostic. Every adb call is bounded; the device is probed before each test AND every POLL_S seconds during it
# (2 failed probes = lost); a lost device or an exhausted budget writes DEVICE_LOST / BUDGET_EXCEEDED with the
# test concerned into results.txt and audit-results.txt, lists what was NOT run, and exits at once (codes 3 / 4).
INSTR_LIMIT="${INSTR_LIMIT:-1500}"; POLL_S="${POLL_S:-15}"; BUDGET_S="${BUDGET_S:-7800}"; ADB_LIMIT="${ADB_LIMIT:-60}"; PROBE_S="${PROBE_S:-20}"
T_START=$(date +%s); CUR=setup
A() { timeout "$ADB_LIMIT" adb "$@"; }
alive() { timeout "$PROBE_S" adb get-state 2>/dev/null | grep -qx device && timeout "$PROBE_S" adb shell echo ok 2>/dev/null | tr -d '\r' | grep -qx ok; }
stop_run() {   # $1 = DEVICE_LOST | BUDGET_EXCEEDED, $2 = detail; never returns
  local line="$1 at $CUR: $2 — remaining tests NOT_RUN (API $api, $(( $(date +%s) - T_START )) s after start)"
  echo "$line" | tee -a "$out/results.txt" >> "$out/audit-results.txt"
  { echo "$line"; echo "--- adb devices -l"; timeout 10 adb devices -l 2>&1; echo "--- adb get-state"; timeout 10 adb get-state 2>&1
    echo "--- host"; uptime; free -m 2>/dev/null | head -3; } > "$out/device-diagnostic.txt" 2>&1
  echo "::error title=JARVIS V3 API $api::$line" >&2
  exit "$([ "$1" = DEVICE_LOST ] && echo 3 || echo 4)"; }
precheck() { CUR="$1"
  [ $(( $(date +%s) - T_START )) -lt "$BUDGET_S" ] || stop_run BUDGET_EXCEEDED "budget of ${BUDGET_S}s for this API level used up"
  alive || stop_run DEVICE_LOST "emulator not responding before the test"; }
instr() {   # bounded instrumentation, device probed while it runs; the caller redirects stdout
  timeout "$INSTR_LIMIT" adb shell am instrument "$@" & local pid=$! miss=0
  while kill -0 "$pid" 2>/dev/null; do sleep "$POLL_S"; kill -0 "$pid" 2>/dev/null || break
    if alive; then miss=0; else miss=$((miss + 1)); if [ "$miss" -ge 2 ]; then kill "$pid" 2>/dev/null; wait "$pid" 2>/dev/null; stop_run DEVICE_LOST "emulator stopped responding during the test"; fi; fi
  done
  wait "$pid"; local rc=$?
  if [ $rc -eq 124 ]; then echo "INSTRUMENTATION_TIMEOUT after ${INSTR_LIMIT}s"; A shell am force-stop fr.jarvis.v3 >/dev/null 2>&1 || true; fi
  return $rc; }
run() { local name="$1"; shift; precheck "$name"
  A shell am broadcast -a android.intent.action.CLOSE_SYSTEM_DIALOGS > /dev/null 2>&1 || true
  timeout 20 adb logcat -c || true
  instr -w -r -e class "$T#$name" "$@" "$R" > "$out/instr-$name.txt" 2>&1
  if grep -q '^OK (1 test)' "$out/instr-$name.txt"; then echo "PASS $name" | tee -a "$out/results.txt"
  else echo "FAIL $name" | tee -a "$out/results.txt"; fail=1
       # Evidence of the failure: screen at that moment, end of the instrumentation output, errors in logcat.
       timeout 30 adb exec-out screencap -p > "$out/shots/api$api-FAIL-$name.png" || true
       { echo "--- instrumentation (tail)"; tail -n 25 "$out/instr-$name.txt"; echo "--- logcat errors"; timeout 30 adb logcat -d -t 400 '*:E' | grep -vE 'emulator|goldfish|ranchu' | tail -n 25; } | tee -a "$out/results.txt"; fi; }
precheck setup
A shell cmd connectivity airplane-mode enable || A shell settings put global airplane_mode_on 1
A shell settings get global airplane_mode_on | tee "$out/airplane.txt"; grep -qx 1 "$out/airplane.txt" || { echo "airplane mode not enabled" >&2; exit 1; }
anim() { for k in window_animation_scale transition_animation_scale animator_duration_scale; do A shell settings put global $k "$1"; done; }
anim 1
# Show the soft keyboard even though the emulator declares a hardware keyboard (keyboard state captures).
A shell settings put secure show_ime_with_hard_keyboard 1
A shell wm size | tee "$out/screen.txt"; A shell wm density | tee -a "$out/screen.txt"
timeout 300 adb install -r "$apk" || { echo "V3 APK install failed (without -t)" >&2; exit 1; }
timeout 300 adb install -r -t "$tapk"
for f in llm.gguf stt.bin tts.zip utt1.wav; do timeout 600 adb push "$res/$f" "/data/local/tmp/v3-$f" >/dev/null; A shell chmod 644 "/data/local/tmp/v3-$f"; done
pull() { timeout 30 adb exec-out run-as fr.jarvis.v3 cat "files/$1" > "$out/$1" 2>/dev/null || rm -f "$out/$1"; }
run a_manifestSinglePermissionNoNetworkInstallable
run b_firstLaunchThenVerifiedImport -e llmPath /data/local/tmp/v3-llm.gguf -e sttPath /data/local/tmp/v3-stt.bin -e ttsZip /data/local/tmp/v3-tts.zip; pull v3-first-launch.json
# Owner request 15/11: CAPTURES_ONLY=1 = real screenshots of every screen (ScreenTourTest), no campaign.
if [ "${CAPTURES_ONLY:-0}" = 1 ]; then
  precheck w_screenTour
  instr -w -r -e class "fr.jarvis.v3.ScreenTourTest#w_screenTour" -e afterImport 1 "$R" > "$out/instr-tour.txt" 2>&1
  tour_rc=$?
  if [ "$tour_rc" = 0 ] && grep -q '^OK (1 test)' "$out/instr-tour.txt"; then echo "PASS w_screenTour" | tee -a "$out/results.txt"; else echo "FAIL w_screenTour" | tee -a "$out/results.txt"; tail -n 30 "$out/instr-tour.txt" | tee -a "$out/results.txt"; fail=1; fi
  # A successful instrumentation is not proof that its images reached the host. Detect a device lost
  # just after the tour, keep every transfer bounded, and reject missing/empty/incomplete evidence.
  precheck capture_collection
  if ! A shell run-as fr.jarvis.v3 ls -1 files/v3shots > "$out/capture-files.txt"; then
    echo "FAIL capture_listing" | tee -a "$out/results.txt"; fail=1
  fi
  # adb reads stdin even for exec-out. Give the list its own descriptor so the first transfer/probe
  # cannot consume the remaining filenames (reproduced on API29/35 by run 44031444326).
  while IFS= read -r f <&3; do
    f="${f%$'\r'}"
    [[ "$f" =~ ^tour-[a-zA-Z0-9_-]+\.png$ ]] || continue
    precheck "capture:$f" < /dev/null
    if ! timeout 30 adb exec-out run-as fr.jarvis.v3 cat "files/v3shots/$f" < /dev/null > "$out/shots/api$api-$f"; then
      echo "FAIL capture_transfer:$f" | tee -a "$out/results.txt"; fail=1
    fi
  done 3< "$out/capture-files.txt"
  precheck capture_index
  if ! A exec-out run-as fr.jarvis.v3 cat files/audit-tour.json > "$out/audit-tour.json"; then
    echo "FAIL capture_index" | tee -a "$out/results.txt"; fail=1
  fi
  precheck captures_complete
  if python3 "$(dirname "$0")/validate_captures.py" --api "$api" --output "$out"; then
    echo "PASS captures_verified" | tee -a "$out/results.txt"
  else echo "FAIL captures_verified" | tee -a "$out/results.txt"; fail=1; fi
  if A logcat -d -b crash > "$out/crash.txt" && ! grep -q "fr.jarvis.v3" "$out/crash.txt"; then
    echo "PASS no_crash" | tee -a "$out/results.txt"
  else echo "FAIL no_crash" | tee -a "$out/results.txt"; fail=1; fi
  A shell rm -f /data/local/tmp/v3-* || true
  echo "=== API $api captures"; ls "$out/shots"; exit $fail
fi
run c_realEngineStatesCardsMenuLandscapeBackground -e afterImport 1 -e wav1 /data/local/tmp/v3-utt1.wav; pull v3-states.json
run f_conversationContextPersonaResume -e afterImport 1; pull v3-conv.json
run g_localKnowledgeCitedAnswer -e afterImport 1; pull v3-knowledge.json
run h_consentedMemoryProposeConfirmRecallDelete -e afterImport 1; pull v3-memory.json
run i_longConversationStaysCoherentAndBounded -e afterImport 1; pull v3-long.json
# AUDIT-GLOBAL-01 campaign T1: tests written to EXPOSE defects; recorded in audit-results.txt, never in the verdict.
audit() { local name="$1"; shift; precheck "$name"
  instr -w -r -e class "fr.jarvis.v3.AuditCampaignTest#$name" "$@" "$R" > "$out/audit-$name.txt" 2>&1
  if grep -q '^OK (1 test)' "$out/audit-$name.txt"; then echo "PASS $name" >> "$out/audit-results.txt"
  else { echo "FAIL $name"; grep -m3 -E "AssertionError|Exception|Timeout" "$out/audit-$name.txt"; } >> "$out/audit-results.txt"; fi
  for f in $(timeout 30 adb shell run-as fr.jarvis.v3 ls files 2>/dev/null | tr -d '\r' | grep '^audit-'); do timeout 30 adb exec-out run-as fr.jarvis.v3 cat "files/$f" > "$out/$f"; done; }
for t in b3_ambiguousMemoriesAreNotInjectedWithoutChoice c3_unknownQuestionsAreNotInvented c8_generalAnswerIsNeverPresentedAsDocumentary \
         d2_voiceErrorsLeaveTheSessionUsableWithoutDuplicates d3_interruptionAtEachStage f2f3f4_documentsContradictoryCorruptAndInjected \
         f6_deletionIsVerifiedOnDisk g6_modelOrDocumentCannotCreateAMemory; do audit "$t" -e afterImport 1; done
audit2() { local name="$1"; shift; precheck "$name"
  instr -w -r -e class "fr.jarvis.v3.AuditCampaignT2Test#$name" "$@" "$R" > "$out/audit-$name.txt" 2>&1
  if grep -q '^OK (1 test)' "$out/audit-$name.txt"; then echo "PASS $name" >> "$out/audit-results.txt"
  else { echo "FAIL $name"; grep -m3 -E "AssertionError|Exception|Timeout" "$out/audit-$name.txt"; } >> "$out/audit-results.txt"; fi
  for f in $(timeout 30 adb shell run-as fr.jarvis.v3 ls files 2>/dev/null | tr -d '\r' | grep '^audit-'); do timeout 30 adb exec-out run-as fr.jarvis.v3 cat "files/$f" > "$out/$f"; done; }
for t in a3_knowledgeImportFormatsAndLimits a4_corruptFutureAndSymlinkDatabasesRefusedAndKept a5_memoryConsentMismatchAndExpiry \
         e2e3e6e7_layoutStressAccessibilityAvatar g1_microphoneRevokedTextStillWorks g2_thermalSevereRefusesTurn h4_loadUnloadCyclesDoNotLeak; do audit2 "$t" -e afterImport 1; done
A shell pm grant fr.jarvis.v3 android.permission.RECORD_AUDIO || true
audit3() { local name="$1"; shift; precheck "$name"
  instr -w -r -e class "fr.jarvis.v3.AuditCampaignT3Test#$name" "$@" "$R" > "$out/audit-$name.txt" 2>&1
  if grep -q '^OK (1 test)' "$out/audit-$name.txt"; then echo "PASS $name" >> "$out/audit-results.txt"
  else { echo "FAIL $name"; grep -m3 -E "AssertionError|Exception|Timeout" "$out/audit-$name.txt"; } >> "$out/audit-results.txt"; fi
  for f in $(timeout 30 adb shell run-as fr.jarvis.v3 ls files 2>/dev/null | tr -d '\r' | grep '^audit-'); do timeout 30 adb exec-out run-as fr.jarvis.v3 cat "files/$f" > "$out/$f"; done; }
for t in j_sessionLifecycleNeverDeadlocks k_sphereStillAtEveryNonAnswerTransition l_hiddenInjectionsInADocument \
         m_longConversationMemoryAndLatency n_resumeKeepsSystemNotices; do audit3 "$t" -e afterImport 1; done
precheck o_historyWriteFailureKeepsTheConversation
instr -w -r -e class "fr.jarvis.v3.AuditCampaignT4Test#o_historyWriteFailureKeepsTheConversation" -e afterImport 1 "$R" > "$out/audit-o.txt" 2>&1
if grep -q '^OK (1 test)' "$out/audit-o.txt"; then echo "PASS o_historyWriteFailureKeepsTheConversation" >> "$out/audit-results.txt"
else { echo "FAIL o_historyWriteFailureKeepsTheConversation"; grep -m3 -E "AssertionError|Exception|Timeout" "$out/audit-o.txt"; } >> "$out/audit-results.txt"; fi
A exec-out run-as fr.jarvis.v3 cat files/audit-o.json > "$out/audit-o.json" 2>/dev/null || true
# Campaign T5 (owner instructions 15/11): sphere still AND cheap outside the answer (two separate measures); documents
# end to end: unseen injections never become instructions, absent values never invented.
audit5() { local name="$1"; shift; precheck "$name"
  instr -w -r -e class "fr.jarvis.v3.AuditCampaignT5Test#$name" "$@" "$R" > "$out/audit-$name.txt" 2>&1
  if grep -q '^OK (1 test)' "$out/audit-$name.txt"; then echo "PASS $name" >> "$out/audit-results.txt"
  else { echo "FAIL $name"; grep -m3 -E "AssertionError|Exception|Timeout" "$out/audit-$name.txt"; } >> "$out/audit-results.txt"; fi
  for f in $(timeout 30 adb shell run-as fr.jarvis.v3 ls files 2>/dev/null | tr -d '\r' | grep '^audit-'); do timeout 30 adb exec-out run-as fr.jarvis.v3 cat "files/$f" > "$out/$f"; done; }
for t in p_sphereStillAndCheapOutsideTheAnswer q_unseenInjectionsNeverBecomeInstructions r_absentValueInAFoundDocumentIsNotInvented \
         s_tornJournalLineNeverLosesTheNextExchange t_storageFullIsRefusedCleanlyAndRecovers; do audit5 "$t" -e afterImport 1; done
A shell run-as fr.jarvis.v3 rm -f files/g3-filler.bin || true   # G3 filler removed even if its test process died
anim 0; run d_reducedMotionStaticSphereAndFirstText -e afterImport 1; pull v3-reduced.json; anim 1
run e_galleryCapturesAndBeforeAfterBench; pull v3-bench.json
for f in $(timeout 30 adb shell run-as fr.jarvis.v3 ls files/v3shots 2>/dev/null | tr -d '\r'); do timeout 30 adb exec-out run-as fr.jarvis.v3 cat "files/v3shots/$f" > "$out/shots/api$api-$f"; done
A shell am force-stop fr.jarvis.v3
A shell am start -W -n fr.jarvis.v3/.CockpitActivity > "$out/reopen.txt"; sleep 6
if A shell pidof fr.jarvis.v3 > /dev/null; then echo "PASS reopen_after_force_stop" | tee -a "$out/results.txt"; else echo "FAIL reopen_after_force_stop" | tee -a "$out/results.txt"; fail=1; fi
A logcat -d -b crash > "$out/crash.txt" || true
if grep -q "fr.jarvis.v3" "$out/crash.txt"; then echo "FAIL no_crash" | tee -a "$out/results.txt"; fail=1; else echo "PASS no_crash" | tee -a "$out/results.txt"; fi
A shell rm -f /data/local/tmp/v3-* || true
echo "=== API $api results"; cat "$out/results.txt"
exit $fail
