#!/usr/bin/env sh
# Run from any directory; preserve Gradle's exit status.
set -eu
cd "$(dirname "$0")/.."
exec ./gradlew assembleDebug testDebugUnitTest lintDebug assembleDebugAndroidTest "$@"
