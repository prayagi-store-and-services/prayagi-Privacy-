# ATIF v3.0 - applicable parts for Netra Privacy

ATIF v3.0 (Absolute Truth & Intelligence Framework) is the owner's permanent master rule, issued 9 Nov 2026. Canonical full text: private governance copy (`netra-intruder`, `docs/ATIF-v3.0.md`). This file lists only the parts that apply to THIS app.

## Applicable parts and why

- Part 01, 02: privacy and security status must be accurate before reassuring.
- Part 04, 06: the patch-status card uses real, validated dates; impossible or missing values show Unavailable instead of looking current (v1.4.27 fix is the standing precedent).
- Part 09: "current" security status is compared by exact day, with dates shown.
- Part 14: the app does not install Android OS patches and does not claim to fix Android CVEs.
- Part 15 (High-Stakes, cybersecurity): theoretical protections are distinguished from verified findings; DNS blocking was proven on a fresh emulator before release and is described as exactly that.
- Part 16: the on-device observation log is opt-in and off by default; private feedback goes through the consented web form only.
- Part 17, 20: limits stay in the copy.

## Data this app shows, and its status

- Patch-status card: real date comparison, Unavailable on bad data.
- Site blocking: emulator-verified behaviour described with its exact scope; failure feedback path named.
- Update card: only when a newer version is verified.

## Standing rule

No security status may be shown as stronger than its evidence. Physical-phone testing status is stated plainly when pending.

## Source labels (owner-approved 9 Nov 2026)

Every user-visible data claim carries a short source label. UI strings stay short; the label for each claim lives in this file's "Data this app shows" list and in WHATS_NEW/release notes (e.g. "Source: Android BatteryManager"). In-app visible source labels are planned and ship only after the owner's UI preview.

## Stale data: OLD marking (owner-approved 9 Nov 2026)

Any dated or versioned claim that stops matching reality is marked "OLD (as of <date>)" at the next check and corrected in the same or a following PR. Stale claims are never silently edited and never left unmarked.

## Weekly audit (owner-approved 9 Nov 2026)

A weekly audit records each material claim as VERIFIED or NOT VERIFIED in `docs/ATIF-AUDIT.md`, newest first. Failures and NOT VERIFIED items are listed first in every audit and every report.
