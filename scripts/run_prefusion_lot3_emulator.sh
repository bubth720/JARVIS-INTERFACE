#!/usr/bin/env bash
# Clean hosted emulator only. Never run against a personal/USB device.
set -euo pipefail
cd "$(dirname "$0")/.."
lot3_api="${1:?API level required}"
case "$lot3_api" in 26|35) ;; *) echo 'Only the reviewed API26/35 matrix is supported' >&2; exit 2;; esac
lot3_report="app/build/reports/prefusion-lot3/api-$lot3_api"
mkdir -p "$lot3_report"
lot3_stage=validate_emulator
lot3_finish() {
  lot3_exit="$1"
  trap - EXIT
  python3 - "$lot3_report/stage.json" "$lot3_stage" "$lot3_exit" <<'PY_STAGE' || echo 'Unable to persist public stage diagnostic' >&2
import json,sys
from pathlib import Path
Path(sys.argv[1]).write_text(json.dumps({'stage':sys.argv[2],'exit_code':int(sys.argv[3])})+'\n')
PY_STAGE
  exit "$lot3_exit"
}
trap 'lot3_finish "$?"' EXIT
lot3_device="$(adb get-serialno)"
[[ "$lot3_device" =~ ^emulator-[0-9]+$ ]] || { echo 'Refusing non-emulator device' >&2; exit 2; }
# Pin every subsequent ADB call and the Gradle device selector to this checked
# emulator. No implicit/default-device install is permitted by this runner.
export ANDROID_SERIAL="$lot3_device"
test "$(adb -s "$lot3_device" shell getprop ro.build.version.sdk | tr -d '\r')" = "$lot3_api"
lot3_classes='com.jarvis.interfaceapp.prefusion.PrefusionConsumerBoundaryTest,com.jarvis.interfaceapp.prefusion.PrefusionOffUiTest,com.jarvis.interfaceapp.ui.chat.core.CoreConnectionDiagnosticsTest,com.jarvis.interfaceapp.ui.chat.ChatInteractionTest,com.jarvis.interfaceapp.fusion.FusionInteractionTest'
lot3_stage=instrument
./gradlew connectedDebugAndroidTest --no-daemon --max-workers=2 \
  "-Pandroid.testInstrumentationRunnerArguments.class=$lot3_classes" \
  -Pandroid.testInstrumentationRunnerArguments.prefusionCapture=true
lot3_stage=check_xml
cp -a app/build/outputs/androidTest-results/connected "$lot3_report/instrumentation"
python3 scripts/check_prefusion_lot3.py --instrumented "$lot3_report/instrumentation" --api "$lot3_api" --output "$lot3_report/result.json"
# Exercise an actual process restart, then re-run only the two OFF UI checks.
# connectedAndroidTest owns its installation lifecycle and may uninstall both
# APKs during cleanup. Restore the compiled public test APKs before this separate
# restart experiment; never assume the Gradle-installed packages remain present.
lot3_stage=prepare_restart
lot3_app_apk=app/build/outputs/apk/debug/app-debug.apk
lot3_test_apk=app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
test -s "$lot3_app_apk" && test -s "$lot3_test_apk"
adb -s "$lot3_device" install -r -t "$lot3_app_apk"
adb -s "$lot3_device" install -r -t "$lot3_test_apk"
adb -s "$lot3_device" shell pm path com.jarvis.interfaceapp | grep -q '^package:'
adb -s "$lot3_device" shell pm path com.jarvis.interfaceapp.test | grep -q '^package:'
adb -s "$lot3_device" shell pm list instrumentation | grep -Fq \
  'instrumentation:com.jarvis.interfaceapp.test/androidx.test.runner.AndroidJUnitRunner (target=com.jarvis.interfaceapp)'
lot3_stage=restart
adb -s "$lot3_device" shell am force-stop com.jarvis.interfaceapp
adb -s "$lot3_device" shell am start -W -n com.jarvis.interfaceapp/.MainActivity
adb -s "$lot3_device" shell am instrument -w \
  -e class com.jarvis.interfaceapp.prefusion.PrefusionOffUiTest \
  -e prefusionCapture true \
  com.jarvis.interfaceapp.test/androidx.test.runner.AndroidJUnitRunner | tee "$lot3_report/process-restart.log"
python3 - "$lot3_report/process-restart.log" <<'PY'
from pathlib import Path
import re,sys
text=Path(sys.argv[1]).read_text()
assert re.search(r'OK \(2 tests?\)',text), 'Restart instrumentation did not report two successes'
assert 'FAILURES!!!' not in text and 'INSTRUMENTATION_FAILED' not in text, 'Restart instrumentation failed'
PY
# This named file comes only from the public OFF screen, never a private avatar.
lot3_stage=capture
python3 scripts/collect_prefusion_capture.py --serial "$lot3_device" --output "$lot3_report/public-off.png"
lot3_stage=complete
