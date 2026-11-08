package com.example

import com.example.siteblock.DnsObservation
import com.example.siteblock.DnsObservationLog
import java.io.File
import org.junit.Assert.*
import org.junit.Test

class DnsObservationLogTest {
    private fun tmp(): File {
        val f = File.createTempFile("dnsobs", ".log"); f.delete(); return f
    }

    @Test fun appendAndReadRoundTrip() {
        val log = DnsObservationLog(tmp())
        log.append(DnsObservation(1000L, "example.com", false, listOf("US", "IN"), "com.bank.app"))
        log.append(DnsObservation(2000L, "blocked.example", true, emptyList(), null))
        val all = log.read()
        assertEquals(2, all.size)
        assertEquals(DnsObservation(1000L, "example.com", false, listOf("US", "IN"), "com.bank.app"), all[0])
        assertEquals(DnsObservation(2000L, "blocked.example", true, emptyList(), null), all[1])
    }

    @Test fun capKeepsNewest() {
        val log = DnsObservationLog(tmp())
        for (i in 1..(DnsObservationLog.MAX + 25)) {
            log.append(DnsObservation(i.toLong(), "n$i.example", false, emptyList(), null))
        }
        val all = log.read()
        assertEquals(DnsObservationLog.MAX, all.size)
        assertEquals("n${DnsObservationLog.MAX + 25}.example", all.last().name)
        assertEquals("n26.example", all.first().name)
    }

    @Test fun corruptLinesAreSkipped() {
        val f = tmp()
        f.writeText("not-a-log-line\n1000\tok.example\t0\tUS\t\nabc\tbad\t0\t\t\n")
        val all = DnsObservationLog(f).read()
        assertEquals(1, all.size)
        assertEquals("ok.example", all[0].name)
        assertEquals(listOf("US"), all[0].countries)
        assertNull(all[0].app)
    }

    @Test fun clearRemovesEverything() {
        val f = tmp()
        val log = DnsObservationLog(f)
        log.append(DnsObservation(1L, "a.example", false, emptyList(), null))
        log.clear()
        assertTrue(log.read().isEmpty())
        assertFalse(f.exists())
    }

    @Test fun missingFileReadsEmpty() {
        assertTrue(DnsObservationLog(File("/nonexistent-dir/x.log")).read().isEmpty())
    }
}
