# DNS-only VPN: IPv6 fall-through regression

4 November 2026. No release approval is implied by this patch.

## Finding

The service configures an IPv4 fake DNS address and route only. Android blocks an
unconfigured address family by default. Add `allowFamily(AF_INET6)` so other IPv6
traffic can use the underlying network rather than being blocked by this IPv4-only
DNS filter. This is a source-grounded risk, not a reproduced diagnosis of the
owner's device.

Official contract:
https://developer.android.com/reference/android/net/VpnService.Builder
https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/core/java/android/net/VpnService.java

## Limits

This does not implement an IPv6 packet forwarder or full-traffic inspection.
Encrypted DNS and connections outside the fake DNS route are not inspected.
Do not market the filter as complete network visibility or complete blocking.
The source-contract unit tests are not network acceptance evidence.

## Required acceptance, test account/emulator only

- Existing `SiteBlockVpnDeviceTest`: ordinary DNS, HTTPS, blocking, allow-list.
- IPv4-only, dual-stack and IPv6-only/NAT64 test networks: record baseline DNS and
  HTTPS before enabling; confirm the same connectivity while enabled and after
  disabling. A network not supplied by the emulator is unverified, not passed.
- Strict Private DNS: explicit refusal with internet remaining usable.
- Network change, airplane/offline recovery, upstream timeout/fail-open, repeated
  start/stop, and another VPN conflict. Confirm no misleading active state.
- Do not claim the owner's phone or mobile carrier is fixed without its own test.

Per-app DNS ownership and destination-country visibility remain separate work.
