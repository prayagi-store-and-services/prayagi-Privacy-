# Security notes - SensorGuard

## Policy screen cleanup (v1.2.4)
- Policy screen: every card now has the same shape, colour and outline. Text in the usage count, app version, crash report, permissions and phone security cards had very loose line spacing; it now uses a normal spacing.
- Developer names (DevicePolicyManager, setCameraDisabled, TelephonyCallback) are replaced by plain words. The meaning of each line is unchanged.
- No new permission, library or network call.

## UI cleanup, step 2 (v1.2.3)
- Ledger: the time and the three status badges (action, state, risk) no longer share one row, so a badge cannot wrap letter by letter. Badges and filter chips are one line.
- Audit filter chips and the Report sub-tabs keep their labels on one line.
- No new permission, library or network call.

## Standard header (v1.1.0)
The header is the Netra standard: 56 dp, fixed, only the app name, the installed version (Android package info, "Unavailable" if missing) and the device date/time. All screens scroll; only this header and the bottom bar stay fixed. No new permission, network call or library. Every shown value needs a real evidence source, otherwise "Unavailable".

## In-app update (new)

What it does: on app open, at most once a day, the app asks `https://github.com/prayagi-store-and-services/prayagi-Privacy-/releases/latest/download/latest.json` whether a newer version exists. If yes, it shows the version and what changed, and the user taps Update. The app downloads `app-release.apk` from the same release, checks its size and SHA-256 against latest.json, and only then opens the Android package installer. The user confirms with one system tap.

What is protected:
- Only https://github.com/prayagi-store-and-services/prayagi-Privacy-/ release URLs are used; the download URL is built from the release tag, never taken from the metadata.
- The file is deleted and not installed if its size or SHA-256 does not match.
- Android installs the update only if it is signed with the same key as the installed app (the Netra release key), so a different signer is rejected by the system.
- Nothing about the user or device is sent: the check is a plain download of a small public file. No account, no ID, no location.
- Permission added: REQUEST_INSTALL_PACKAGES (needed to open the installer; the user must also allow installs from this app once in Android settings). A separate FileProvider (`<applicationId>.updates`) exposes only the app cache folder `updates/`.

Release process: the Signed Release workflow publishes `app-release.apk`, a named copy, and `latest.json` together. The tag must equal `v` plus the versionName in app/build.gradle.kts.

Limits: Android does not allow silent installs, so the user always taps once. Versions installed before this feature existed cannot update themselves and must be installed manually once.

## Anonymous usage count (new in 1.0.3)

Once per UTC day (and once per month) the app adds 1 to a public counter in Firestore (`netra_active/prayagi-privacy_<yyyyMMdd>` and `_<yyyyMM>`), so the Netra Eco website can show approximate active users. The request contains only the counter document name and "increment by 1". No device ID, install ID, account, location or app data is sent, and the app keeps no ID for this. A local flag stops repeats on the same day; a failed send is retried at the next open. It is on by default and can be turned off with the "Share anonymous usage count" switch. Firestore rules allow only creating a counter with value 1 or raising it by exactly 1; counters are public to read. Anyone could in theory script extra +1s, so the number is approximate, and reinstalling or clearing data can count one person twice.

## Automatic crash reports
- If the app crashes, it saves a short report on the device. The next time the app opens, it sends that report by itself (no button, no question) and then deletes it. If the send fails, it is kept and retried at the next start.
- Version 1.1.1: Settings has a manual "Send crash report" button. It shows the exact text first (app, app version, phone model, Android version, the last crash trace) and sends only if the user taps Send; "Share instead" lets the user pick any app. If no crash is saved it says Unavailable. The automatic send now counts as sent only when the forwarding service confirms; before, an HTTP 200 reply was enough, so a report could be deleted without any email being sent.
- The report contains only: the app name, phone model, Android version, app version, and the crash stack trace (exception class names and code locations; exception messages are dropped on purpose).
- It contains no name, email, location, files, contacts, device IDs or usage history.
- It is sent through the same form pipeline as the website forms (FormSubmit) to the developer's email.

## Audio focus and media (changed in 1.0.6)
- In the default Max Security (hardware) mode the app no longer asks Android for audio focus. Before, turning the screen off made the system pause the user's music or video. Now playback continues; only the microphone line is reserved.
- Power Saver (Focus) mode still uses audio focus and can pause other apps' media. The settings text now says so.

## Installer file cleanup
After an in-app update installs, the app restarts and, on start, deletes every downloaded installer file from its cache folder (`cache/updates/`). Nothing from the update is left in storage. A new download also removes older files first. If the user cancels the install, the file is removed the next time the app starts.

## Manual update check
- Settings has a "Check for update" button. It reads the same latest.json as the automatic check, shows "You are on the latest version", "Update available: vX" or "Unavailable: could not check", and never installs anything without the user tapping Update and confirming in the Android installer. The file is still checked for size and SHA-256 first.

## Travel Checking: hidden camera check and Wi-Fi device scan (added in 1.0.8)
- New "Travel Checking" mode, opened by the button in the app header (always visible, never starts by itself). It is a 5-step guide with:  a magnetic field meter, a torch "lens finder" guide, an infrared check guide (opens the phone's own camera app), a Wi-Fi device scan and a step-by-step room checklist.
- Battery: nothing runs in the background. The magnetic meter reads the sensor only while step 1 is on screen, the torch is on only while step 2 is on screen (it is switched off when you leave), and the Wi-Fi scan runs only when you tap Scan.
- It gives hints only. It cannot prove that a room has no hidden camera, and the screen says so. A camera on another network, on mobile data or recording to a card does not appear in the scan.
- Wi-Fi device scan: starts only when you tap "Scan this Wi-Fi network". It looks only at the phone's own private network (10.x, 172.16-31.x, 192.168.x), never more than 254 addresses, and refuses public or unusual addresses. For each address it makes short connection attempts to nine common ports (80, 443, 554, 8080, 8554, 8000, 37777, 34567, 22) and a ping, then reads the device name the network gives. It does not log in to anything, does not send data, and keeps no results: the list is gone when you leave the screen.
- Android does not let apps read other devices' MAC addresses, so brand and MAC are shown as Unavailable. "Possible camera" appears only when a camera-style port answers or the device name contains a word like cam, ipc, dvr or nvr. It is a hint, not proof.
- Torch: uses the phone's torch only while you press the button, no camera permission. Magnetic meter: reads the phone's magnetic sensor on screen only, nothing is recorded.
- Permissions: INTERNET and ACCESS_NETWORK_STATE (the scan needs network sockets and the phone's own address range). No camera, location, storage or contacts permission is added. No new library.

## Mic and camera watchdog, honest limits (added in 1.0.9)
- What it does: when a microphone recording session or a camera session is detected while the screen is OFF and no phone call explains it, SensorGuard shows one alert (at most one per sensor per minute) and records an event. With the screen ON it only records the event, with no alert.
- What it cannot do: a normal Android app is not told which other app is using the microphone or camera. The alert therefore says "Which app: Unavailable" unless Android itself supplies a name. It is a signal, not proof, and it can miss sessions Android does not report to normal apps.
- The cross-app app-name alerts that already existed depend on an Android API that is documented for system apps with special permissions (see AOSP AppOps notes). On a normal phone this may not report other apps at all. We have not tested it on a real device, so do not rely on it. Android's own green mic/camera indicator (Android 12 and later) and the Privacy dashboard are the reliable source.
- Permissions: none added. No new library.

## Permission and backup cleanup (version 1.1.2)

- Removed PACKAGE_USAGE_STATS: it was declared but no code reads usage statistics, so it granted nothing real. Nothing the app shows depended on it.
- Turned off Android app backup (allowBackup=false). The old setting copied the app's saved settings and history to Google backup with sample rules that limited nothing. A reinstall now starts clean; the app keeps no account, so nothing is lost on the server.
- No new permission, network call or library.

## Home screen widget (version 1.1.3)

- New widget "SensorGuard": shows the guard status, whether the microphone is locked, whether the camera is blocked and whether device admin is active, exactly as the guard service last reported them, with the time of that report. Before the guard has reported anything it shows "Unavailable". It does not claim more than the guard knows, and it does not show which apps used the mic or camera.
- Stored on this phone only (local preferences, not backed up because backup is off): the status name, the three yes/no values and the time. Nothing is sent anywhere.
- No timer or background work: the widget redraws only when the guard service reports a change.
- No new permission, network call or library. The widget receiver is exported because Android's launcher must send it update events; it handles only that action. Tapping the widget opens the app.

## Permissions list (version 1.1.4)

- New "Permissions" card in Policy settings: lists each permission the app uses, the plain reason, and the live status read from Android when you open the screen (no timer). Tapping a row opens the matching Android page where you can allow or stop it. Normal permissions that cannot be switched off (see installed apps, internet, start after reboot) are shown as always allowed.
- No new permission, network call or library.

## Phone security check (v1.2.0)
Settings has a "Phone security check" card. When the screen opens it reads, once, from Android itself: the security patch date (`Build.VERSION.SECURITY_PATCH`) and its age in days, with a warning line when it is more than 90 days old (our own threshold, not an Android rule), and the non-system apps that ask to install other apps and are currently allowed to (App Ops, Android 10 and newer; older versions show Unavailable). It uses the existing see-installed-apps permission. No new permission, no network call, no library, nothing runs in the background and nothing is stored or sent. If a value cannot be read it shows Unavailable.

## Festival banner (version 1.2.1)

- A card near the top of the home screen shows today's festival (India calendar, bundled in the app, from timeanddate.com India 2026-2027) or "coming soon" for a festival within 3 days, with the live date and time. India's Independence Day (15 August) is shown too. It has no death anniversaries and no other country's days. After 2027 there is no data, so no banner is shown and nothing is invented. A date marked "may differ by a day" says so.
- It works offline. No new permission, network call or library.

## Cleaner screens, first step (version 1.2.2)
- The second fixed row under the header (the app name repeated, with a Travel Checking button) is gone. Only the 56 dp header and the bottom bar stay fixed.
- Travel Checking is now a button at the top of the Guard tab, and the Travel Checking screen has its own Back button.
- Bottom bar names stay on one line.
- No new permission, library or network call.
