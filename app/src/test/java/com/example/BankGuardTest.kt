package com.example

import com.example.siteblock.BankGuard
import com.example.siteblock.BlockSet
import com.example.siteblock.DomainNames
import com.example.siteblock.ListParser
import com.example.siteblock.OfficialDomains
import com.example.siteblock.SiteMatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BankGuardTest {
    @Test fun officialBankNamesAreRecognised() {
        for (n in listOf("sbi.bank.in", "netbanking.hdfc.bank.in", "www.hdfcbank.com", "onlinesbi.sbi", "retail.onlinesbi.sbi", "icicibank.com", "someunknown.bank.in"))
            assertTrue(n, BankGuard.isOfficial(n))
    }

    @Test fun notOfficial() {
        for (n in listOf("hdfcbank.com.evil.com", "sbi.bank.in.evil.com", "hdfcbank-login.com", "bank.in.example.com", "example.com", "notbank.in"))
            assertFalse(n, BankGuard.isOfficial(n))
    }

    @Test fun lookalikesAreCaught() {
        assertEquals("hdfc", BankGuard.lookalike("hdfcbank-login.com")?.id)
        assertEquals("sbi", BankGuard.lookalike("sbi.co.in.verify-now.com")?.id)
        assertNotNull(BankGuard.lookalike("icicibannk.com"))
        assertNotNull(BankGuard.lookalike("axisbank-kyc.in"))
    }

    @Test fun realBanksAndOrdinarySitesAreNotLookalikes() {
        for (n in listOf("hdfcbank.com", "www.sbi.bank.in", "example.com", "wikipedia.org", "india.gov.in", "localhost", "casbin.org"))
            assertNull(n, BankGuard.lookalike(n))
    }

    @Test fun bankNamesAreNeverBlockedEvenWhenListed() {
        assertTrue(OfficialDomains.isOfficial("sbi.bank.in"))
        val names = listOf("sbi.bank.in", "hdfcbank.com", "bad-example.com")
        val m = SiteMatcher(BlockSet(ListParser.sortedUnique(names.map { DomainNames.hash(it) }.toLongArray(), names.size)), emptyList())
        assertFalse(m.isBlocked("sbi.bank.in"))
        assertFalse(m.isBlocked("netbanking.hdfcbank.com"))
        assertTrue(m.isBlocked("bad-example.com"))
    }

    @Test fun everyBankHasADomainAndBrand() {
        for (b in BankGuard.BANKS) { assertTrue(b.id, b.domains.isNotEmpty()); assertTrue(b.id, b.brand.length >= 3) }
    }
}
