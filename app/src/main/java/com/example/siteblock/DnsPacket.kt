package com.example.siteblock

/**
 * Just enough IPv4 + UDP + DNS to answer or forward the DNS questions that reach the local VPN.
 * Only UDP DNS questions with one question are handled; anything else is ignored (it never carries other traffic,
 * because the VPN routes only the fake DNS address).
 */
class DnsQuery(
    val srcIp: ByteArray, val dstIp: ByteArray, val srcPort: Int, val dstPort: Int,
    val dnsPayload: ByteArray, val name: String, val ipHeaderLen: Int
)

object DnsPacket {
    private fun u16(b: ByteArray, o: Int) = ((b[o].toInt() and 0xff) shl 8) or (b[o + 1].toInt() and 0xff)

    /** Reads a packet from the tunnel. Returns null when it is not an IPv4 UDP DNS question. */
    fun parse(pkt: ByteArray, len: Int): DnsQuery? {
        if (len < 28) return null
        val ver = (pkt[0].toInt() and 0xf0) shr 4
        if (ver != 4) return null
        val ihl = (pkt[0].toInt() and 0x0f) * 4
        if (ihl < 20 || len < ihl + 8) return null
        if ((pkt[9].toInt() and 0xff) != 17) return null // UDP
        val totalLen = u16(pkt, 2)
        val end = if (totalLen in (ihl + 8)..len) totalLen else len
        val srcPort = u16(pkt, ihl)
        val dstPort = u16(pkt, ihl + 2)
        if (dstPort != 53) return null
        val payload = pkt.copyOfRange(ihl + 8, end)
        val name = questionName(payload) ?: return null
        return DnsQuery(pkt.copyOfRange(12, 16), pkt.copyOfRange(16, 20), srcPort, dstPort, payload, name, ihl)
    }

    /** The queried name from a DNS message with a single question, or null. */
    fun questionName(dns: ByteArray): String? {
        if (dns.size < 13) return null
        val flags = u16(dns, 2)
        if ((flags and 0x8000) != 0) return null // a response, not a question
        if (u16(dns, 4) != 1) return null
        var o = 12
        val sb = StringBuilder()
        while (true) {
            if (o >= dns.size) return null
            val l = dns[o].toInt() and 0xff
            if (l == 0) break
            if (l and 0xc0 != 0) return null
            if (o + 1 + l > dns.size) return null
            if (sb.isNotEmpty()) sb.append('.')
            for (i in 0 until l) sb.append((dns[o + 1 + i].toInt() and 0xff).toChar())
            o += 1 + l
        }
        return sb.toString()
    }

    /** "Name does not exist" answer built from the question: same id and question, QR=1, RA=1, RCODE=3. */
    fun nxdomain(dns: ByteArray): ByteArray {
        var o = 12
        while (o < dns.size && dns[o].toInt() != 0) o += (dns[o].toInt() and 0xff) + 1
        val qEnd = minOf(o + 1 + 4, dns.size) // name end + type + class
        val out = dns.copyOfRange(0, qEnd)
        val rd = out[2].toInt() and 0x01
        out[2] = (0x80 or rd).toByte()      // QR=1, opcode 0, AA=0, TC=0, RD copied
        out[3] = 0x83.toByte()              // RA=1, RCODE=3 (NXDOMAIN)
        out[6] = 0; out[7] = 0; out[8] = 0; out[9] = 0; out[10] = 0; out[11] = 0 // no answer/authority/extra records
        return out
    }

    /** Wraps a DNS answer into an IPv4 + UDP packet going back to the asker (addresses and ports swapped). */
    fun reply(q: DnsQuery, dnsAnswer: ByteArray): ByteArray {
        val total = 20 + 8 + dnsAnswer.size
        val p = ByteArray(total)
        p[0] = 0x45; p[1] = 0
        p[2] = (total shr 8).toByte(); p[3] = total.toByte()
        p[4] = 0; p[5] = 0; p[6] = 0x40; p[7] = 0 // don't fragment
        p[8] = 64; p[9] = 17
        System.arraycopy(q.dstIp, 0, p, 12, 4) // source = the fake DNS address
        System.arraycopy(q.srcIp, 0, p, 16, 4) // destination = the asker
        val c = checksum(p, 0, 20)
        p[10] = (c shr 8).toByte(); p[11] = c.toByte()
        p[20] = (q.dstPort shr 8).toByte(); p[21] = q.dstPort.toByte()
        p[22] = (q.srcPort shr 8).toByte(); p[23] = q.srcPort.toByte()
        val ul = 8 + dnsAnswer.size
        p[24] = (ul shr 8).toByte(); p[25] = ul.toByte()
        p[26] = 0; p[27] = 0 // UDP checksum 0 is allowed over IPv4
        System.arraycopy(dnsAnswer, 0, p, 28, dnsAnswer.size)
        return p
    }

    fun checksum(b: ByteArray, off: Int, len: Int): Int {
        var sum = 0
        var i = off
        while (i < off + len) {
            sum += ((b[i].toInt() and 0xff) shl 8) or (b[i + 1].toInt() and 0xff)
            i += 2
        }
        while (sum shr 16 != 0) sum = (sum and 0xffff) + (sum shr 16)
        return sum.inv() and 0xffff
    }
}
