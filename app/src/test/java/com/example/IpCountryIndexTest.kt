package com.example

import com.example.siteblock.IpCountryIndex
import java.io.*
import java.net.InetAddress
import java.util.zip.GZIPOutputStream
import org.junit.Assert.*
import org.junit.Test

class IpCountryIndexTest {
    private fun ip(s: String) = InetAddress.getByName(s).address
    private fun zip(raw: ByteArray): ByteArray {
        val out = ByteArrayOutputStream(); GZIPOutputStream(out).use { it.write(raw) }; return out.toByteArray()
    }
    private fun raw(): ByteArray {
        val out = ByteArrayOutputStream()
        DataOutputStream(out).use {
            it.writeBytes("NCG1"); it.writeInt(2); it.writeInt(1)
            it.write(ip("1.0.0.0")); it.write(ip("1.0.0.255")); it.writeBytes("AU")
            it.write(ip("8.8.8.0")); it.write(ip("8.8.8.255")); it.writeBytes("US")
            it.write(ip("2606:4700::")); it.write(ip("2606:4700::ffff")); it.writeBytes("US")
        }
        return out.toByteArray()
    }
    private fun index() = IpCountryIndex.load(ByteArrayInputStream(zip(raw())))!!
    @Test fun rangeBoundariesAndGaps() {
        assertEquals("AU", index().country(ip("1.0.0.0")))
        assertEquals("AU", index().country(ip("1.0.0.255")))
        assertEquals("US", index().country(ip("8.8.8.8")))
        assertNull(index().country(ip("1.0.1.0")))
        assertNull(index().country(ip("9.9.9.9")))
    }
    @Test fun ipv6UnsignedComparison() {
        assertEquals("US", index().country(ip("2606:4700::ffff")))
        assertNull(index().country(ip("2606:4700::1:0")))
    }
    @Test fun privateReservedAndInvalidStayUnknown() {
        for (s in listOf("10.0.0.1", "127.0.0.1", "192.168.1.1", "100.64.0.1", "224.0.0.1", "::1", "fe80::1", "fc00::1", "2001:db8::1")) assertNull(index().country(ip(s)))
        assertNull(index().country(byteArrayOf(1, 2, 3)))
    }
    @Test fun malformedAndTruncatedGzipRejected() {
        assertNull(IpCountryIndex.load(ByteArrayInputStream(byteArrayOf(1, 2, 3))))
        val b = zip(raw()); assertNull(IpCountryIndex.load(ByteArrayInputStream(b.copyOf(b.size - 4))))
    }
    @Test fun wrongMagicCountsAndCountryRejected() {
        for (offset in listOf(0, 7, 20)) {
            val b = raw(); b[offset] = 0
            assertNull(IpCountryIndex.load(ByteArrayInputStream(zip(b))))
        }
    }
    @Test fun reversedAndOverlappingRangesRejected() {
        val reversed = raw(); reversed[12] = 9
        assertNull(IpCountryIndex.load(ByteArrayInputStream(zip(reversed))))
        val overlap = raw(); for (i in 0..3) overlap[22 + i] = overlap[12 + i]
        assertNull(IpCountryIndex.load(ByteArrayInputStream(zip(overlap))))
    }
    @Test fun actualPinnedAssetLoadsAndFindsPublicDnsAddresses() {
        val asset = File("src/main/assets/ip-country/dbip-country-lite-2026-11.ncg.gz")
        val start = System.nanoTime()
        val real = asset.inputStream().use { IpCountryIndex.load(it) }
        assertNotNull(real)
        assertNotNull(real!!.country(ip("8.8.8.8")))
        assertNotNull(real.country(ip("2606:4700:4700::1111")))
        assertNull(real.country(ip("192.168.1.1")))
        println("Pinned country asset load/lookup ms=" + (System.nanoTime() - start) / 1_000_000)
    }
}
