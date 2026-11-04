# DNS visibility foundation, 4 November 2026

`DnsAnswers.parse` is a pure parser. It returns answer-section IN A/AAAA addresses
and TTL values only after transaction/question validation. Malformed, truncated,
failed and mismatched replies are rejected. No additional-section glue is shown.
Only exact queried-name owners are emitted. CNAME target records are deliberately
omitted until a tested chain validator exists. Unrelated answer owners are ignored.
It does not perform network I/O, save any history or change the running VPN.

Local Kotlin 2.2.10 compile and JUnit4 run: 9 tests passed. The tests cover A/AAAA,
compressed names, wrong transaction/question, truncated/error replies, short or
wrong-length address records, unsupported/additional records, bad compression,
and every truncated prefix of a valid reply. This is not Android build/device proof.

Not integrated yet: app attribution, country dataset/lookup, history retention and
deletion controls, UI, VPN forwarding, DNS-to-actual-connection matching. A DNS
answer must never be presented as proof the app contacted that address or where
its data is stored. System resolver/unknown ownership must stay Unknown.

This foundation alone does not meet the 5 November delivery scope. No release.
