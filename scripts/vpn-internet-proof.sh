#!/usr/bin/env bash
set -euo pipefail
gradle --no-daemon :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.example.siteblock.SiteBlockVpnDeviceTest --stacktrace
