#!/usr/bin/env bash
# Non-regression of A-029 (owner rule 15/11): a lost emulator stops the V3 run fast with a clear diagnostic.
# Both modes use explicit inputs: a CAPTURES_ONLY inherited from CI cannot change the campaign oracle.
# Runs tools/run_emulator.sh against a fake adb; no emulator needed. Usage: bash tests/test_run_emulator.sh
set -u
here="$(cd "$(dirname "$0")" && pwd)"; script="$here/../run_emulator.sh"; failures=0
scenario() {   # name, expected exit code, max seconds, required text in results.txt, [env...]
  local name="$1" want="$2" max="$3" text="$4"; shift 4
  local w; w="$(mktemp -d)"; mkdir -p "$w/bin" "$w/state" "$w/res"; cp "$here/fake_adb" "$w/bin/adb"
  local t0; t0=$(date +%s)
  ( cd "$w" && env PATH="$w/bin:$PATH" FAKE_ADB_DIR="$w/state" FAKE_CAPTURE_FIXTURE="$here/capture_fixture.py" \
      CAPTURES_ONLY=0 POLL_S=0.05 PROBE_S=2 ADB_LIMIT=5 "$@" bash "$script" 29 "$w/res" "$w/out" > "$w/log" 2>&1 )
  local rc=$? dt=$(( $(date +%s) - t0 ))
  local ok=1
  [ "$rc" -eq "$want" ] || { echo "  exit code $rc, expected $want"; ok=0; }
  [ "$dt" -le "$max" ] || { echo "  took ${dt}s, limit ${max}s"; ok=0; }
  grep -q -- "$text" "$w/out/results.txt" 2>/dev/null || { echo "  results.txt lacks '$text'"; ok=0; }
  if [ "$want" -ge 3 ]; then
    grep -q -- "$text" "$w/out/audit-results.txt" 2>/dev/null || { echo "  audit-results.txt lacks '$text'"; ok=0; }
    [ -s "$w/out/device-diagnostic.txt" ] || { echo "  no device-diagnostic.txt"; ok=0; }
  fi
  if [ "$name" = healthy_run_completes ]; then
    [ "$(wc -l < "$w/state/instr.log")" -eq 35 ] || { echo "  full campaign invocation count changed"; ok=0; }
    ! grep -q ScreenTourTest "$w/state/instr.log" || { echo "  tour leaked into full campaign"; ok=0; }
    for suite in AuditCampaignTest AuditCampaignT2Test AuditCampaignT3Test AuditCampaignT4Test AuditCampaignT5Test; do
      grep -q "$suite#" "$w/state/instr.log" || { echo "  missing $suite"; ok=0; }
    done
  fi
  if [ "$name" = captures_complete ]; then
    [ "$(wc -l < "$w/state/instr.log")" -eq 3 ] || { echo "  capture mode must execute only a, b, tour"; ok=0; }
    grep -q 'ScreenTourTest#w_screenTour' "$w/state/instr.log" || { echo "  tour missing"; ok=0; }
    [ -s "$w/out/capture-summary.json" ] || { echo "  validated image manifest missing"; ok=0; }
    grep -q 'PASS no_crash' "$w/out/results.txt" || { echo "  crash check missing"; ok=0; }
  fi
  if [ $ok = 1 ]; then echo "PASS $name (${dt}s, exit $rc)"; else echo "FAIL $name"; tail -n 15 "$w/log"; cat "$w/out/results.txt" 2>/dev/null; failures=$((failures + 1)); fi
  rm -rf "$w"; }
scenario healthy_run_completes            0 120 "PASS no_crash"                         FAKE_SCENARIO=healthy
scenario device_lost_between_tests        3  30 "DEVICE_LOST at f_conversationContextPersonaResume: emulator not responding before the test" FAKE_SCENARIO=lost_before FAKE_AFTER=3
scenario device_lost_during_a_test        3  30 "DEVICE_LOST at c_realEngineStatesCardsMenuLandscapeBackground: emulator stopped responding during the test" FAKE_SCENARIO=lost_during FAKE_AFTER=3
scenario shell_hangs_while_state_is_device 3 40 "DEVICE_LOST at f_conversationContextPersonaResume" FAKE_SCENARIO=shell_hang FAKE_AFTER=3
scenario lost_at_setup                    3  20 "DEVICE_LOST at setup"                   FAKE_SCENARIO=lost_before FAKE_OFFLINE_AT_START=1
scenario budget_exhausted                 4  20 "BUDGET_EXCEEDED at"                     FAKE_SCENARIO=healthy BUDGET_S=0
scenario hung_instrumentation_is_bounded  1 120 "INSTRUMENTATION_TIMEOUT\|FAIL c_realEngine" FAKE_SCENARIO=healthy FAKE_HANG=3 INSTR_LIMIT=3
scenario captures_complete               0 30 "PASS captures_verified" CAPTURES_ONLY=1 FAKE_SCENARIO=healthy
scenario captures_adb_stdin_is_isolated   0 30 "PASS captures_verified" CAPTURES_ONLY=1 FAKE_SCENARIO=healthy FAKE_DRAIN_STDIN=1
scenario captures_lost_before_tour       3 20 "DEVICE_LOST at w_screenTour: emulator not responding before the test" CAPTURES_ONLY=1 FAKE_SCENARIO=lost_before FAKE_AFTER=2
scenario captures_lost_during_tour       3 20 "DEVICE_LOST at w_screenTour: emulator stopped responding during the test" CAPTURES_ONLY=1 FAKE_SCENARIO=lost_during FAKE_AFTER=3
scenario captures_lost_after_tour        3 20 "DEVICE_LOST at capture_collection" CAPTURES_ONLY=1 FAKE_SCENARIO=lost_before FAKE_AFTER=3
scenario captures_shell_hangs_after_tour 3 20 "DEVICE_LOST at capture_collection" CAPTURES_ONLY=1 FAKE_SCENARIO=shell_hang FAKE_AFTER=3
scenario captures_hung_instrumentation   1 20 "FAIL w_screenTour" CAPTURES_ONLY=1 FAKE_SCENARIO=healthy FAKE_HANG=3 INSTR_LIMIT=3
scenario captures_missing_index          1 20 "FAIL captures_verified" CAPTURES_ONLY=1 FAKE_SCENARIO=healthy FAKE_EVIDENCE=missing_index
scenario captures_missing_png            1 20 "FAIL captures_verified" CAPTURES_ONLY=1 FAKE_SCENARIO=healthy FAKE_EVIDENCE=missing_png
scenario captures_empty_png              1 20 "FAIL captures_verified" CAPTURES_ONLY=1 FAKE_SCENARIO=healthy FAKE_EVIDENCE=empty_png
scenario captures_pull_error             1 20 "FAIL capture_transfer:" CAPTURES_ONLY=1 FAKE_SCENARIO=healthy FAKE_EVIDENCE=pull_error
scenario captures_nonzero_with_ok        1 20 "FAIL w_screenTour" CAPTURES_ONLY=1 FAKE_SCENARIO=healthy FAKE_EVIDENCE=nonzero_ok
echo "failures: $failures"; exit $failures
