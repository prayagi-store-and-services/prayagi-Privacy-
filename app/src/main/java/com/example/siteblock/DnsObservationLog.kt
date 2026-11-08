package com.example.siteblock

import java.io.File

/**
 * One observed DNS answer (or block). DNS observations only: they show that a name
 * was looked up, never that an app actually connected to it. `app` is a best-effort
 * guess from the UDP socket table and can be wrong when apps share a uid.
 */
data class DnsObservation(
    val epochMs: Long,
    val name: String,
    val blocked: Boolean,
    val countries: List<String>,
    val app: String?
)

/**
 * Opt-in, on-device-only observation log. When the user turns it on, recent DNS
 * observations are kept in a small capped file inside the app's private folder.
 * Nothing is sent anywhere; there is no network I/O here. When the setting is off
 * (the default) nothing is written and the old promise stands: visited names are
 * never stored.
 */
class DnsObservationLog(private val file: File) {
    fun append(o: DnsObservation) {
        val line = buildString {
            append(o.epochMs).append('\t')
            append(esc(o.name)).append('\t')
            append(if (o.blocked) '1' else '0').append('\t')
            append(o.countries.joinToString(",") { esc(it) }).append('\t')
            append(esc(o.app ?: ""))
        }
        synchronized(this) {
            val old = readLines()
            val kept = if (old.size >= MAX) old.subList(old.size - MAX + 1, old.size) else old
            file.writeText(kept.joinToString("") { it + "\n" } + line + "\n")
        }
    }

    fun read(): List<DnsObservation> = synchronized(this) { readLines().mapNotNull { parse(it) } }

    fun clear() = synchronized(this) { file.delete() }

    private fun readLines(): List<String> =
        try { if (file.exists()) file.readLines().filter { it.isNotBlank() } else emptyList() } catch (e: Exception) { emptyList() }

    companion object {
        const val MAX = 200
        private fun esc(s: String): String = s.replace("\\", "\\\\").replace("\t", "\\t").replace("\n", "\\n")
        private fun unesc(s: String): String {
            val out = StringBuilder(); var i = 0
            while (i < s.length) {
                if (s[i] == '\\' && i + 1 < s.length) {
                    when (s[i + 1]) { 't' -> out.append('\t'); 'n' -> out.append('\n'); '\\' -> out.append('\\'); else -> { out.append(s[i]); out.append(s[i + 1]) } }
                    i += 2
                } else { out.append(s[i]); i += 1 }
            }
            return out.toString()
        }
        private fun parse(line: String): DnsObservation? {
            val f = line.split('\t')
            if (f.size != 5) return null
            val ts = f[0].toLongOrNull() ?: return null
            val name = unesc(f[1])
            if (name.isEmpty()) return null
            val countries = if (f[3].isEmpty()) emptyList() else f[3].split(",").map { unesc(it) }
            val app = unesc(f[4]).takeUnless { it.isEmpty() }
            return DnsObservation(ts, name, f[2] == "1", countries, app)
        }
    }
}
