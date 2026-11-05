# Offline IP-country foundation, 4 November 2026

Not integrated or released. No history, UI, DNS lookup, network I/O or app attribution.

Vendor source https://download.db-ip.com/free/dbip-country-lite-2026-11.csv.gz
Terms https://db-ip.com/db/download/ip-to-country-lite
CC BY4.0 attribution and modification notice bundled under assets/ip-country.
Source SHA256 e5c8459d638a272ece7f4d7cfa49dce18d91b4f403f6940ba9249bf0db3781fd.
Converted SHA2567e7ffe87cd3f692e7a55111a62ac36069cef20f888a02fe861597b4962815ab6.
362,998IPv4+369,798IPv6ranges.4,428,019bytes gzip,16,203,124bytes expanded.

Deterministic pinned converter validates order, non-overlap, family and country.
Runtime loader bounds size and validates counts/order/non-overlap/country syntax.
Raw-IP-only binary search returns Unknown for missing/non-global entries. It is
approximate destination-IP geography, never proof of where data is stored.

7local JVM tests passed, including corruption, range order and real bundled asset.
Real-asset load+lookup was about150ms on development JVM, not Android performance.
No Android compile or low-memory/device pass yet. Integration must load off main
thread, expose attribution and dataset age, and retain Unknown semantics. Peak
memory and Android network acceptance remain open. CNAME chains, contact history,
retention/deletion and truthful app attribution remain separate work.
