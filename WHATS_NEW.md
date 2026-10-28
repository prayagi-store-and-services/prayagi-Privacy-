## Unreleased - community links

- Policy & Configuration has five official Netra social profiles, the Privacy website and a separate private complaint/feedback form.
- External browser links open only after a tap. No automatic follow, rating or post, new permission or background network request.
- Private feedback is sent through FormSubmit by the web form. No live delivery or physical-phone testing is claimed.

## 1.4.28
Automatic newer-version popup and notification. Metadata is checked at most once daily, subject to Android battery/network limits, not instantly. Update opens this app page in compatible Netra Ecosystem or the Eco website. No manual update check, APK download, installation or unknown-source permission request in this app. Notification denial is respected.

## Patch-status date validation (v1.4.27)

The phone patch card now requires a complete, real YYYY-MM-DD date and compares the exact day, not just the month. A missing day, impossible date or trailing text shows Unavailable instead of looking current. An earlier patch day in the same month is shown as behind with a day-specific explanation, not "0 months behind". Regression tests cover same-month patch days, leap dates and malformed values.

This fixes misleading security-status reporting. It does not install Android OS patches, change the known October bulletin level, or claim to fix an Android CVE. No new permission, library, network request or data collection. Phone testing remains pending.


## Metadata-only update notice
A newer version shows an Update button. It opens this app's exact page in compatible Netra Ecosystem or on the Eco website. This app does not download or install APKs.
