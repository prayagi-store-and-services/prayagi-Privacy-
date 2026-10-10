package com.example

import com.example.siteblock.BlockSet
import com.example.siteblock.DomainNames
import com.example.siteblock.ListParser
import com.example.siteblock.OfficialDomains
import com.example.siteblock.SiteMatcher
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OfficialDomainsTest {
    private fun set(vararg names: String) = BlockSet(
        ListParser.sortedUnique(names.map { DomainNames.hash(it) }.toLongArray(), names.size)
    )

    @Test fun officialNamesAreRecognised() {
        for (n in listOf("india.gov.in", "www.rbi.org.in.gov.in", "uidai.gov.in", "ecourts.gov.in", "x.nic.in", "iitd.ac.in", "du.edu.in", "isro.res.in", "whitehouse.gov", "mit.edu"))
            assertTrue(n, OfficialDomains.isOfficial(n))
    }

    @Test fun lookalikesAreNotOfficial() {
        for (n in listOf("gov.in.evil.com", "notgov.in", "fakegov.in", "nic.in.example.com", "example.com", "ac.in.phish.net"))
            assertFalse(n, OfficialDomains.isOfficial(n))
    }

    @Test fun neverBlockedEvenWhenListedAndEvenWithEmptyAllowList() {
        val m = SiteMatcher(set("india.gov.in", "gov.in", "somesite.nic.in", "bad-example.com"), emptyList())
        assertFalse(m.isBlocked("india.gov.in"))
        assertFalse(m.isBlocked("www.somesite.nic.in"))
        assertTrue(m.isBlocked("bad-example.com"))
    }
}
