package com.example.siteblock

/** DNS answer addresses only. These are DNS results, not proof of an app connecting. */
data class DnsAddressAnswer(val family: Int, val bytes: List<Int>, val ttlSeconds: Long)

object DnsAnswers {
    private fun u16(b: ByteArray, o: Int): Int = ((b[o].toInt() and 255) shl 8) or (b[o + 1].toInt() and 255)

    /**
     * Reject malformed, truncated, failed or unrelated replies. No network I/O.
     * Only IN A/AAAA records in the answer section are returned, never additional
     * glue records. Only records owned by the queried name are returned; CNAME
     * targets are omitted until chain validation is implemented. A caller must still treat them as DNS observations, not traffic.
     */
    fun parse(query: DnsQuery, reply: ByteArray): List<DnsAddressAnswer> {
        if (reply.size < 12 || query.dnsPayload.size < 12) return emptyList()
        if (u16(reply, 0) != u16(query.dnsPayload, 0)) return emptyList()
        val flags = u16(reply, 2)
        if (flags and 0x8000 == 0 || flags and 0x7800 != 0 || flags and 0x0200 != 0 || flags and 15 != 0) return emptyList()
        if (u16(reply, 4) != 1) return emptyList()
        val question = name(reply, 12) ?: return emptyList()
        val qEnd = question.second
        if (qEnd + 4 > reply.size || !question.first.equals(query.name, ignoreCase = true)) return emptyList()
        val original = name(query.dnsPayload, 12) ?: return emptyList()
        if (original.second + 4 > query.dnsPayload.size) return emptyList()
        if (u16(reply, qEnd) != u16(query.dnsPayload, original.second) || u16(reply, qEnd + 2) != 1) return emptyList()
        var offset = qEnd + 4
        val count = u16(reply, 6)
        if (count > 256) return emptyList()
        val out = mutableListOf<DnsAddressAnswer>()
        repeat(count) {
            val owner = name(reply, offset) ?: return emptyList()
            offset = owner.second
            if (offset + 10 > reply.size) return emptyList()
            val type = u16(reply, offset)
            val clazz = u16(reply, offset + 2)
            val ttl = (0..3).fold(0L) { value, i -> (value shl 8) or (reply[offset + 4 + i].toLong() and 255) }
            val length = u16(reply, offset + 8)
            offset += 10
            if (length > reply.size - offset) return emptyList()
            if (clazz == 1 && owner.first.equals(query.name, ignoreCase = true) && ((type == 1 && length == 4) || (type == 28 && length == 16))) {
                out.add(DnsAddressAnswer(if (type == 1) 4 else 6, reply.copyOfRange(offset, offset + length).map { it.toInt() and 255 }, ttl))
            } else if (clazz == 1 && (type == 1 || type == 28)) return emptyList()
            offset += length
        }
        return out.distinct()
    }

    /** Bounded compression decoding; cycles, oversized names and bad pointers fail closed. */
    private fun name(b: ByteArray, start: Int): Pair<String, Int>? {
        var offset = start
        var end = -1
        val visited = HashSet<Int>()
        val labels = mutableListOf<String>()
        var wireLength = 1
        while (true) {
            if (offset !in b.indices || !visited.add(offset) || visited.size > 128) return null
            val length = b[offset].toInt() and 255
            if (length == 0) return labels.joinToString(".") to (if (end < 0) offset + 1 else end)
            if (length and 0xc0 == 0xc0) {
                if (offset + 1 >= b.size) return null
                if (end < 0) end = offset + 2
                val target = ((length and 63) shl 8) or (b[offset + 1].toInt() and 255)
                if (target >= offset) return null
                offset = target
            } else {
                if (length > 63 || offset + 1 + length > b.size) return null
                wireLength += length + 1
                if (wireLength > 255) return null
                val label = b.copyOfRange(offset + 1, offset + 1 + length)
                if (label.any { (it.toInt() and 255) !in 33..126 || it.toInt() == 46 }) return null
                labels.add(label.toString(Charsets.US_ASCII))
                offset += length + 1
            }
        }
    }
}
