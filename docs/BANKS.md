Note: the domain list below is shared with the Netra Privacy Guard PC extension (src/banks.js there). Phone code: app/src/main/java/com/example/siteblock/BankGuard.kt.

# Banking guard - domain provenance

Verified 2026-10-10:

1. `.bank.in` - exclusive namespace for banks; IDRBT is the only registrar
   (RBI circular, rbi.org.in/scripts/NotificationUser.aspx?Id=12837; migration
   deadline 2025-10-31). Every .bank.in host is treated as an official bank.
2. RBI "Websites of Banks in India" (rbi.org.in/scripts/banklinks.aspx,
   fetched 2026-10-10) - the per-bank .bank.in domains in src/banks.js are the
   exact domains RBI links to.
3. Legacy domains (.com / .co.in / .in) come from the Ministry of Finance
   Department of Financial Services pages (financialservices.gov.in) or were
   verified live to redirect to the bank's .bank.in domain:
   - redirects verified 2026-10-10: bankofbaroda.in, centralbankofindia.co.in,
     canarabank.com, indianbank.in, sbi.co.in, onlinesbi.sbi, kotaksecurities.com (-> kotakneo.com)
   - answered on their own host with bank content/tls: bankofbaroda.com,
     bankofindia.co.in, bankofindia.com, pnbindia.in, unionbankofindia.co.in,
     ucobank.com, iob.in, sbicard.com, bankofmaharashtra.in
4. A wrong entry here is a security hole (allow-listed domains are never
   blocked), so new domains are added only from an official source or a live
   redirect proof, never from memory.
