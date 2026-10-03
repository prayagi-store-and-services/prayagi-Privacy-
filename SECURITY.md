# Security notes - SensorGuard

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
