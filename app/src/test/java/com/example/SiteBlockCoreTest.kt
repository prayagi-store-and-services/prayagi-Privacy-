package com.example

import com.example.siteblock.BlockSet
import com.example.siteblock.BlockSetFile
import com.example.siteblock.DnsPacket
import com.example.siteblock.DomainNames
import com.example.siteblock.ListParser
import com.example.siteblock.SiteMatcher
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SiteBlockCoreTest {
    private fun set(vararg names: String) = BlockSet(
        ListParser.sortedUnique(names.map { DomainNames.hash(it) }.toLongArray(), names.size)
    )

    @Test fun listedDomainAndItsSubdomainsAreBlocked() {
        val m = SiteMatcher(set("bad-example.com"), emptyList())
        assertTrue(m.isBlocked("bad-example.com"))
        assertTrue(m.isBlocked("www.bad-example.com"))
        assertTrue(m.isBlocked("a.b.c.bad-example.com."))
        assertTrue(m.isBlocked("WWW.BAD-EXAMPLE.COM"))
    }

    @Test fun otherSitesAreNotBlocked() {
        val m = SiteMatcher(set("bad-example.com"), emptyList())
        assertFalse(m.isBlocked("example.com"))
        assertFalse(m.isBlocked("notbad-example.com"))
        assertFalse(m.isBlocked("bad-example.com.good.org"))
        assertFalse(m.isBlocked("com"))
        assertFalse(m.isBlocked(""))
    }

    @Test fun allowListAlwaysWins() {
        val m = SiteMatcher(set("bad-example.com"), listOf("Bad-Example.com"))
        assertFalse(m.isBlocked("bad-example.com"))
        assertFalse(m.isBlocked("www.bad-example.com"))
        val sub = SiteMatcher(set("bad-example.com"), listOf("ok.bad-example.com"))
        assertFalse(sub.isBlocked("ok.bad-example.com"))
        assertFalse(sub.isBlocked("x.ok.bad-example.com"))
        assertTrue(sub.isBlocked("www.bad-example.com"))
    }

    @Test fun parsesHostsAndPlainAndAdblockLines() {
        assertEquals("a.com", ListParser.parseLine("0.0.0.0 a.com"))
        assertEquals("a.com", ListParser.parseLine("127.0.0.1\tA.COM  # note"))
        assertEquals("a.com", ListParser.parseLine("a.com"))
        assertEquals("a.com", ListParser.parseLine("||a.com^"))
        assertNull(ListParser.parseLine("# comment"))
        assertNull(ListParser.parseLine(""))
        assertNull(ListParser.parseLine("0.0.0.0 localhost"))
        assertNull(ListParser.parseLine("0.0.0.0 1.2.3.4"))
        assertNull(ListParser.parseLine("0.0.0.0 bad host.com extra"))
    }

    @Test fun readsGzipListIntoSortedUniqueHashes() {
        val text = "# h\n0.0.0.0 a.com\n0.0.0.0 b.com\n0.0.0.0 a.com\nc.org\n"
        val bos = ByteArrayOutputStream()
        GZIPOutputStream(bos).use { it.write(text.toByteArray()) }
        val h = ListParser.hashesFrom(ByteArrayInputStream(bos.toByteArray()), gzip = true)
        assertEquals(3, h.size)
        assertTrue(h.toList() == h.sorted())
        val s = BlockSet(h)
        assertTrue(s.contains("a.com") && s.contains("b.com") && s.contains("c.org"))
        assertFalse(s.contains("d.com"))
    }

    @Test fun hashCacheRoundTrips() {
        val h = longArrayOf(-5L, 1L, 99L)
        val bos = ByteArrayOutputStream()
        BlockSetFile.write(bos, h)
        assertArrayEquals(h, BlockSetFile.read(ByteArrayInputStream(bos.toByteArray())))
        assertNull(BlockSetFile.read(ByteArrayInputStream(byteArrayOf(1, 2, 3, 4, 0, 0, 0, 0))))
    }

    private fun dnsQuestion(name: String, id: Int = 0x1234): ByteArray {
        val o = ByteArrayOutputStream()
        o.write(id shr 8); o.write(id and 0xff)
        o.write(0x01); o.write(0x00)           // RD
        o.write(0); o.write(1); o.write(0); o.write(0); o.write(0); o.write(0); o.write(0); o.write(0)
        for (label in name.split('.')) { o.write(label.length); o.write(label.toByteArray()) }
        o.write(0); o.write(0); o.write(1); o.write(0); o.write(1) // type A, class IN
        return o.toByteArray()
    }

    private fun udpPacket(dns: ByteArray): ByteArray {
        val total = 28 + dns.size
        val p = ByteArray(total)
        p[0] = 0x45; p[2] = (total shr 8).toByte(); p[3] = total.toByte(); p[8] = 64; p[9] = 17
        byteArrayOf(10, 111.toByte(), 222.toByte(), 1).copyInto(p, 12)
        byteArrayOf(10, 111.toByte(), 222.toByte(), 2).copyInto(p, 16)
        p[20] = 0x9c.toByte(); p[21] = 0x40; p[22] = 0; p[23] = 53
        val ul = 8 + dns.size; p[24] = (ul shr 8).toByte(); p[25] = ul.toByte()
        dns.copyInto(p, 28)
        return p
    }

    @Test fun readsTheQuestionFromATunnelPacket() {
        val pkt = udpPacket(dnsQuestion("www.bad-example.com"))
        val q = DnsPacket.parse(pkt, pkt.size)
        assertNotNull(q)
        assertEquals("www.bad-example.com", q!!.name)
        assertEquals(40000, q.srcPort)
        assertEquals(53, q.dstPort)
    }

    @Test fun ignoresPacketsThatAreNotDnsQuestions() {
        val pkt = udpPacket(dnsQuestion("a.com"))
        pkt[23] = 80 // port 80, not 53
        assertNull(DnsPacket.parse(pkt, pkt.size))
        val tcp = udpPacket(dnsQuestion("a.com")); tcp[9] = 6
        assertNull(DnsPacket.parse(tcp, tcp.size))
        assertNull(DnsPacket.parse(ByteArray(10), 10))
    }

    @Test fun blockedAnswerIsNxdomainWithSameIdAndQuestion() {
        val dns = dnsQuestion("bad-example.com", 0xBEEF)
        val ans = DnsPacket.nxdomain(dns)
        assertEquals(0xBE, ans[0].toInt() and 0xff); assertEquals(0xEF, ans[1].toInt() and 0xff)
        assertEquals(0x80, ans[2].toInt() and 0x80)      // response
        assertEquals(3, ans[3].toInt() and 0x0f)          // NXDOMAIN
        assertEquals(1, ans[5].toInt())                   // one question kept
        assertEquals(0, ans[7].toInt())                   // no answers
        assertEquals(dns.size, ans.size)
    }

    @Test fun replyPacketSwapsAddressesAndHasValidHeaderChecksum() {
        val pkt = udpPacket(dnsQuestion("bad-example.com"))
        val q = DnsPacket.parse(pkt, pkt.size)!!
        val r = DnsPacket.reply(q, DnsPacket.nxdomain(q.dnsPayload))
        assertEquals(2, r[15].toInt())  // from the fake DNS address .2
        assertEquals(1, r[19].toInt())  // to the asker .1
        assertEquals(0, DnsPacket.checksum(r, 0, 20))
        assertEquals(40000, ((r[22].toInt() and 0xff) shl 8) or (r[23].toInt() and 0xff)) // back to the asking port
    }
}
