#!/usr/bin/env bash
set -euo pipefail
# Disposable CI emulator only. Production fixture and suffixed candidate are distinct.
pkg=com.aistudio.sensorguard.prvsec
proof=$pkg.securityproof
mkdir -p proof-evidence app/src/androidTest/assets app/src/debug
python3 - <<'PY'
p='app/build.gradle.kts'
s=open(p).read();old='applicationId = "com.aistudio.sensorguard.prvsec"';assert s.count(old)==1;s=s.replace(old,old.replace('prvsec"','prvsec.securityproof"'));open(p,'w').write(s)
PY
cat > app/src/debug/AndroidManifest.xml <<'XML'
<manifest xmlns:android="http://schemas.android.com/apk/res/android"><queries><package android:name="com.aistudio.sensorguard.prvsec" /></queries></manifest>
XML
get_fixture() {
  curl --fail --location --retry 1 "$1" -o "app/src/androidTest/assets/$2"
  echo "$3  app/src/androidTest/assets/$2" | sha256sum --check
}
get_fixture https://github.com/prayagi-store-and-services/prayagi-Privacy-/releases/download/v1.4.23/app-release.apk production-old.apk e611151a2056b60786903e42d1a40f861c1322cb273f812306c8b9c6dcbb8dad
get_fixture https://github.com/prayagi-store-and-services/prayagi-Privacy-/releases/download/v1.4.24/app-release.apk production-new.apk 53e5008822acd444092934601d739f34249e8bfe80e453ebd0dcccaca3cf533d
get_fixture https://github.com/prayagi-store-and-services/KBC/releases/download/v1.1.37/app-release.apk production-foreign.apk e1349cb3ad8688587b52d7c4125ee67c3472a55cd3fa85229a58734f24671c16
status=0
cleanup() {
 local original=$?;trap - EXIT
 adb logcat -d > proof-evidence/logcat.txt || status=1
 adb pull /data/local/tmp/updater-choice.png proof-evidence/ || true
 adb pull /data/local/tmp/updater-installer.png proof-evidence/ || true
 adb uninstall "$pkg" > proof-evidence/fixture-uninstall.txt 2>&1 || true
 local out proof_out
 if ! out=$(adb shell pm list packages --user 0 "$pkg");then status=1;out=QUERY_FAILED;fi
 printf '%s\n' "$out" > proof-evidence/fixture-absence.txt
 if printf '%s\n' "$out" | tr -d '\r' | grep -Fxq "package:$pkg"; then status=1; fi
 if printf '%s\n' "$out" | tr -d '\r' | grep -Ev '^(package:[A-Za-z0-9._]+)?$' | grep -q .; then status=1; fi
 if proof_out=$(adb shell pm list packages --user 0 "$proof");then
  proof_out=$(printf '%s' "$proof_out"|tr -d '\r\n')
  case "$proof_out" in
   "") printf 'Candidate absent: permission reset inapplicable\n' > proof-evidence/permission-cleanup.txt ;;
   "package:$proof")
    adb shell appops set "$proof" REQUEST_INSTALL_PACKAGES default > proof-evidence/permission-cleanup.txt 2>&1 || status=1
    adb shell appops get "$proof" REQUEST_INSTALL_PACKAGES >> proof-evidence/permission-cleanup.txt 2>&1 || status=1
    grep -Eq 'default|No operations' proof-evidence/permission-cleanup.txt || status=1
    grep -Eq 'Error|No UID|Exception' proof-evidence/permission-cleanup.txt && status=1 ;;
   *) printf 'Invalid candidate package query: %s\n' "$proof_out" > proof-evidence/permission-cleanup.txt;status=1 ;;
  esac
 else status=1;printf 'Candidate package query failed\n' > proof-evidence/permission-cleanup.txt
 fi
 cp -r app/build/reports/androidTests proof-evidence/ 2>/dev/null || true
 cp -r app/build/outputs/androidTest-results proof-evidence/ 2>/dev/null || true
 printf 'original_exit=%s cleanup_status=%s\n' "$original" "$status" > proof-evidence/outcome.txt
 if (( original != 0 ));then exit "$original";fi
 exit "$status"
}
trap cleanup EXIT
adb install app/src/androidTest/assets/production-old.apk
# Permission is granted only to the disposable suffixed test candidate, then reset by host cleanup.
# connectedDebugAndroidTest installs that candidate itself, so provider test handles any system prompt explicitly.
gradle --no-daemon :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.example.update.SelfUpdateProductionFixtureTest,com.example.update.SelfUpdateChoiceDeviceTest --stacktrace
