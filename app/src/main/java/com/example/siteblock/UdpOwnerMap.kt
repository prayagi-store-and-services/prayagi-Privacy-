package com.example.siteblock

/**
 * Best-effort reader of the kernel UDP socket table (/proc/net/udp text) so a DNS
 * question reaching the local VPN can be attributed to the app that asked it.
 * Pure text parsing only; no file or network I/O, so it is fully unit-testable.
 * Attribution can be wrong when apps share a uid, and empty when the socket has
 * already closed, so callers must treat a missing entry as normal.
 */
object UdpOwnerMap {
    /** Parses /proc/net/udp content into source-port -> uid. Malformed lines are skipped. */
    fun parse(text: String): Map<Int, Int> {
        val out = HashMap<Int, Int>()
        for (line in text.lineSequence()) {
            val t = line.trim()
            if (t.isEmpty() || t.startsWith("sl")) continue
            val parts = t.split(Regex("\\s+"))
            if (parts.size < 8) continue
            val local = parts[1].split(':')
            if (local.size != 2) continue
            val port = local[1].toIntOrNull(16) ?: continue
            val uid = parts[7].toIntOrNull() ?: continue
            out[port] = uid
        }
        return out
    }
}
