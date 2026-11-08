package com.example.siteblock

import java.io.InputStream
import java.util.zip.GZIPInputStream

/** Offline binary range lookup. No DNS calls, network I/O, history or app attribution. */
class IpCountryIndex private constructor(private val data: ByteArray, private val v4: Int, private val v6: Int) {
    fun country(ip: ByteArray): String? {
        val n = ip.size
        if ((n != 4 && n != 16) || nonGlobal(ip)) return null
        val base = if (n == 4) 12 else 12 + v4 * 10
        var low = 0
        var high = (if (n == 4) v4 else v6) - 1
        while (low <= high) {
            val mid = (low + high) ushr 1
            val offset = base + mid * (n * 2 + 2)
            if (compare(ip, data, offset) < 0) high = mid - 1
            else if (compare(ip, data, offset + n) > 0) low = mid + 1
            else return String(data, offset + n * 2, 2, Charsets.US_ASCII).takeUnless { it == "ZZ" }
        }
        return null
    }

    private fun nonGlobal(ip: ByteArray): Boolean {
        val b = ip.map { it.toInt() and 255 }
        if (ip.size == 4) return b[0] == 0 || b[0] == 10 || b[0] == 127 || b[0] >= 224 ||
            (b[0] == 100 && b[1] in 64..127) || (b[0] == 169 && b[1] == 254) ||
            (b[0] == 172 && b[1] in 16..31) || (b[0] == 192 && b[1] == 168) ||
            (b[0] == 192 && b[1] == 0) || (b[0] == 198 && b[1] in 18..19) ||
            (b[0] == 198 && b[1] == 51 && b[2] == 100) || (b[0] == 203 && b[1] == 0 && b[2] == 113)
        return b[0] !in 0x20..0x3f || (b[0] == 0x20 && b[1] == 1 && b[2] < 2) ||
            (b[0] == 0x20 && b[1] == 1 && b[2] == 0x0d && b[3] == 0xb8)
    }

    companion object {
        private const val MAX_BYTES = 32 * 1024 * 1024
        private fun compare(a: ByteArray, b: ByteArray, offset: Int): Int {
            for (i in a.indices) {
                val d = (a[i].toInt() and 255) - (b[offset + i].toInt() and 255)
                if (d != 0) return d
            }
            return 0
        }
        /** Fail closed on malformed, overlapping, unordered or oversized assets. */
        fun load(input: InputStream): IpCountryIndex? = try {
            val bytes = GZIPInputStream(input).use { stream ->
                val out = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (out.size() <= MAX_BYTES) {
                    val n = stream.read(buffer, 0, minOf(buffer.size, MAX_BYTES + 1 - out.size()))
                    if (n < 0) break
                    out.write(buffer, 0, n)
                }
                out.toByteArray()
            }
            parse(bytes)
        } catch (_: Exception) { null }

        private fun parse(b: ByteArray): IpCountryIndex? {
            if (b.size > MAX_BYTES || b.size < 12 || String(b, 0, 4, Charsets.US_ASCII) != "NCG1") return null
            fun count(o: Int) = (0..3).fold(0) { n, i -> (n shl 8) or (b[o + i].toInt() and 255) }
            val v4 = count(4); val v6 = count(8)
            if (v4 < 0 || v6 < 0 || 12L + v4 * 10L + v6 * 34L != b.size.toLong()) return null
            var offset = 12
            for ((n, total) in listOf(4 to v4, 16 to v6)) {
                var previousEnd: ByteArray? = null
                repeat(total) {
                    val start = b.copyOfRange(offset, offset + n)
                    val end = b.copyOfRange(offset + n, offset + n * 2)
                    if (compare(start, end, 0) > 0 || (previousEnd != null && compare(start, previousEnd!!, 0) <= 0)) return null
                    if ((0..1).any { b[offset + n * 2 + it].toInt() !in 65..90 }) return null
                    previousEnd = end
                    offset += n * 2 + 2
                }
            }
            return IpCountryIndex(b, v4, v6)
        }
    }
}
