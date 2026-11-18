#!/usr/bin/env bash
# Non-regression of A-029 (owner rule 15/11): a lost emulator stops the V3 run fast with a clear diagnostic.
# Runs tools/run_emulator.sh against a fake adb; no emulator needed. Usage: bash tests/test_run_emulator.sh
set -u
here="$(cd "$(dirname "$0")" && pwd)"; script="$here/../run_emulator.sh"; failures=0
scenario() {   # name, expected exit code, max seconds, required text in results.txt, [env...]
  local name="$1" want="$2" max="$3" text="$4"; shift 4
  local w; w="$(mktemp -d)"; mkdir -p "$w/bin" "$w/state" "$w/res"; cp "$here/fake_adb" "$w/bin/adb"
  local t0; t0=$(date +%s)
  ( cd "$w" && env -u CAPTURES_ONLY -u INSTR_LIMIT -u POLL_S -u BUDGET_S -u ADB_LIMIT -u PROBE_S PATH="$w/bin:$PATH" FAKE_ADB_DIR="$w/state" POLL_S=1 PROBE_S=2 ADB_LIMIT=5 "$@" bash "$script" 29 "$w/res" "$w/out" > "$w/log" 2>&1 )
  local rc=$? dt=$(( $(date +%s) - t0 ))
  local ok=1
  [ "$rc" -eq "$want" ] || { echo "  exit code $rc, expected $want"; ok=0; }
  [ "$dt" -le "$max" ] || { echo "  took ${dt}s, limit ${max}s"; ok=0; }
  grep -q -- "$text" "$w/out/results.txt" 2>/dev/null || { echo "  results.txt lacks '$text'"; ok=0; }
  if [ "$want" -ge 3 ]; then
    grep -q -- "$text" "$w/out/audit-results.txt" 2>/dev/null || { echo "  audit-results.txt lacks '$text'"; ok=0; }
    [ -s "$w/out/device-diagnostic.txt" ] || { echo "  no device-diagnostic.txt"; ok=0; }
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
# A-036 (run 43709555526): the job-level CAPTURES_ONLY leaked into this harness. Each scenario now ignores the job's
# environment; the captures mode has its own scenario.
scenario captures_mode_runs_only_the_tour 0 60 "PASS w_screenTour"               FAKE_SCENARIO=healthy CAPTURES_ONLY=1
echo "failures: $failures"; exit $failures
