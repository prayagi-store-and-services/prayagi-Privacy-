package com.example.siteblock

/**
 * Banking guard. Two rules from the owner:
 * 1. Official Indian bank domains are never blocked. Every name under .bank.in is official (RBI: .bank.in is an exclusive
 *    namespace for banks, registered only through IDRBT), plus the listed legacy domains.
 * 2. A name that looks like a bank but is not official gets a loud warning (see [lookalike]).
 * Sources are in docs/BANKS.md. Add a domain only from an official source or a live redirect proof, never from memory:
 * a wrong entry here lets a fake site through. Pure functions, no network, nothing stored.
 */
data class Bank(val id: String, val name: String, val brand: String, val domains: List<String>)

object BankGuard {
    val BANKS: List<Bank> = listOf(
        Bank("sbi", "State Bank of India", "sbi", listOf("sbi.bank.in", "sbi.co.in", "onlinesbi.sbi", "onlinesbi.sbi.bank.in", "sbicard.com")),
        Bank("hdfc", "HDFC Bank", "hdfcbank", listOf("hdfc.bank.in", "hdfcbank.com")),
        Bank("icici", "ICICI Bank", "icicibank", listOf("icici.bank.in", "icicibank.com")),
        Bank("axis", "Axis Bank", "axisbank", listOf("axis.bank.in", "axisbank.com")),
        Bank("kotak", "Kotak Mahindra Bank", "kotak", listOf("kotak.bank.in", "kotak.com", "kotaksecurities.com", "kotakneo.com")),
        Bank("pnb", "Punjab National Bank", "pnb", listOf("pnb.bank.in", "pnbindia.in")),
        Bank("bob", "Bank of Baroda", "bankofbaroda", listOf("bankofbaroda.bank.in", "bankofbaroda.in", "bankofbaroda.com")),
        Bank("canara", "Canara Bank", "canarabank", listOf("canarabank.bank.in", "canarabank.com")),
        Bank("union", "Union Bank of India", "unionbankofindia", listOf("unionbankonline.bank.in", "unionbankofindia.bank.in", "unionbankofindia.co.in")),
        Bank("boi", "Bank of India", "bankofindia", listOf("bankofindia.bank.in", "bankofindia.co.in", "bankofindia.com")),
        Bank("indian", "Indian Bank", "indianbank", listOf("indianbank.bank.in", "indianbank.in")),
        Bank("cbi", "Central Bank of India", "centralbankofindia", listOf("centralbank.bank.in", "centralbankofindia.co.in")),
        Bank("idbi", "IDBI Bank", "idbibank", listOf("idbi.bank.in", "idbibank.in")),
        Bank("uco", "UCO Bank", "ucobank", listOf("uco.bank.in", "ucobank.com")),
        Bank("iob", "Indian Overseas Bank", "iob", listOf("iob.bank.in", "iob.in")),
        Bank("bom", "Bank of Maharashtra", "bankofmaharashtra", listOf("bankofmaharashtra.bank.in", "bankofmaharashtra.in")),
        Bank("psb", "Punjab & Sind Bank", "punjabandsind", listOf("punjabandsind.bank.in")),
        Bank("yes", "YES BANK", "yesbank", listOf("yes.bank.in", "yesbank.in")),
        Bank("idfc", "IDFC FIRST Bank", "idfcfirstbank", listOf("idfcfirst.bank.in", "idfcfirstbank.com")),
        Bank("indusind", "IndusInd Bank", "indusind", listOf("indusind.bank.in", "indusind.com")),
        Bank("federal", "Federal Bank", "federalbank", listOf("federal.bank.in", "federalbank.co.in")),
        Bank("rbl", "RBL Bank", "rblbank", listOf("rbl.bank.in", "rblbank.com")),
        Bank("bandhan", "Bandhan Bank", "bandhanbank", listOf("bandhan.bank.in", "bandhanbank.com")),
        Bank("csb", "CSB Bank", "csb", listOf("csb.bank.in", "csb.co.in")),
        Bank("cub", "City Union Bank", "cityunionbank", listOf("cityunionbank.bank.in", "cityunionbank.com")),
        Bank("dcb", "DCB Bank", "dcbbank", listOf("dcb.bank.in", "dcbbank.com")),
        Bank("dhan", "Dhanlaxmi Bank", "dhanbank", listOf("dhan.bank.in", "dhanbank.com")),
        Bank("jk", "Jammu & Kashmir Bank", "jkbank", listOf("jkb.bank.in", "jkbank.com")),
        Bank("karnataka", "Karnataka Bank", "karnatakabank", listOf("karnatakabank.bank.in", "karnatakabank.com")),
        Bank("kvb", "Karur Vysya Bank", "kvb", listOf("kvb.bank.in", "kvb.co.in")),
        Bank("nainital", "Nainital Bank", "nainitalbank", listOf("nainitalbank.bank.in", "nainitalbank.co.in")),
        Bank("sib", "South Indian Bank", "southindianbank", listOf("southindianbank.bank.in", "southindianbank.com")),
        Bank("tmb", "Tamilnad Mercantile Bank", "tmb", listOf("tmb.bank.in", "tmb.in"))
    )

    private val SECOND_LEVEL = setOf("co.in", "org.in", "net.in", "com.in", "gen.in", "firm.in", "ind.in", "co.uk", "org.uk", "com.au", "net.au", "com.np", "com.pk", "co.nz", "com.bd", "co.lk", "com.sg", "com.my", "co.ae", "com.sa", "com.ng", "co.ke", "co.za")

    private fun norm(host: String): String = host.trim().lowercase().trimEnd('.')

    /** True for a .bank.in name or one of the listed bank domains (or a name under them). */
    fun isOfficial(host: String): Boolean {
        val h = norm(host)
        if (h == "bank.in" || h.endsWith(".bank.in")) return true
        for (b in BANKS) for (d in b.domains) if (h == d || h.endsWith(".$d")) return true
        return false
    }

    fun baseDomain(host: String): String {
        val l = norm(host).split('.')
        if (l.size <= 2) return l.joinToString(".")
        val last2 = l[l.size - 2] + "." + l[l.size - 1]
        return if (last2 in SECOND_LEVEL) l.takeLast(3).joinToString(".") else last2
    }

    private fun lev(a: String, b: String): Int {
        val m = a.length; val n = b.length
        if (Math.abs(m - n) > 2) return 3
        var prev = IntArray(n + 1) { it }
        for (i in 1..m) {
            val cur = IntArray(n + 1); cur[0] = i
            for (j in 1..n) cur[j] = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1)
            prev = cur
        }
        return prev[n]
    }

    /** Long brands (5+ letters) rarely collide with real words, so a plain match is fine. Short ones need a word-ish start. */
    private fun brandHit(h: String, brand: String): Boolean {
        if (brand.length >= 5) return h.replace(".", "").replace("-", "").contains(brand)
        return Regex("(^|[.\\-0-9])" + Regex.escape(brand)).containsMatchIn(h)
    }

    /** The bank this name pretends to be, or null. Official names are never lookalikes. */
    fun lookalike(host: String): Bank? {
        val h = norm(host)
        if (h.isEmpty() || !h.contains('.')) return null
        if (isOfficial(h)) return null
        if (OfficialDomains.isOfficial(h)) return null
        val base = baseDomain(h).substringBefore('.')
        for (b in BANKS) {
            if (brandHit(h, b.brand)) return b
            if (base.length >= 4 && Math.abs(base.length - b.brand.length) <= 2 && lev(base, b.brand) <= 2) return b
        }
        return null
    }
}
