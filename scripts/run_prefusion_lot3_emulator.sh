#!/usr/bin/env bash
# Clean hosted emulator only. Never run against a personal/USB device.
set -euo pipefail
cd "$(dirname "$0")/.."
lot3_api="${1:?API level required}"
case "$lot3_api" in 26|35) ;; *) echo 'Only the reviewed API26/35 matrix is supported' >&2; exit 2;; esac
lot3_device="$(adb get-serialno)"
case "$lot3_device" in emulator-*) ;; *) echo 'Refusing non-emulator device' >&2; exit 2;; esac
test "$(adb shell getprop ro.build.version.sdk | tr -d '\r')" = "$lot3_api"
lot3_report="app/build/reports/prefusion-lot3/api-$lot3_api"
mkdir -p "$lot3_report"
lot3_classes='com.jarvis.interfaceapp.prefusion.PrefusionConsumerBoundaryTest,com.jarvis.interfaceapp.prefusion.PrefusionOffUiTest,com.jarvis.interfaceapp.ui.chat.core.CoreConnectionDiagnosticsTest,com.jarvis.interfaceapp.ui.chat.ChatInteractionTest,com.jarvis.interfaceapp.fusion.FusionInteractionTest'
./gradlew connectedDebugAndroidTest --no-daemon --max-workers=2 \
  "-Pandroid.testInstrumentationRunnerArguments.class=$lot3_classes" \
  -Pandroid.testInstrumentationRunnerArguments.prefusionCapture=true
cp -a app/build/outputs/androidTest-results/connected "$lot3_report/instrumentation"
python3 scripts/check_prefusion_lot3.py --instrumented "$lot3_report/instrumentation" --api "$lot3_api" --output "$lot3_report/result.json"
# Exercise an actual process restart, then re-run only the two OFF UI checks.
adb shell am force-stop com.jarvis.interfaceapp
adb shell am start -n com.jarvis.interfaceapp/.MainActivity
adb shell am instrument -w \
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
adb shell run-as com.jarvis.interfaceapp cat files/prefusion-lot3-off.png > "$lot3_report/public-off.png"
test -s "$lot3_report/public-off.png"
