#!/usr/bin/env bash
set -euo pipefail
mkdir -p proof-evidence
cleanup() {
 local original=$?;trap - EXIT
 adb logcat -d > proof-evidence/logcat.txt || true
 adb pull /data/local/tmp/centralized-no-updater.png proof-evidence/ || true
 cp -r app/build/outputs/androidTest-results proof-evidence/ 2>/dev/null || true
 printf 'original_exit=%s\n' "$original" > proof-evidence/outcome.txt
 exit "$original"
}
trap cleanup EXIT
gradle --no-daemon :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.example.update.SelfUpdateProductionFixtureTest,com.example.update.SelfUpdateChoiceDeviceTest --stacktrace
