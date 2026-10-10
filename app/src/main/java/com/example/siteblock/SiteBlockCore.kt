package com.example.siteblock

import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.util.Arrays
import java.util.zip.GZIPInputStream

/**
 * Pure logic for adult and gambling site blocking. No Android classes here, so it is unit tested on the JVM.
 * A name is blocked when it, or any parent name, is on the block list (so a listed domain blocks its subdomains too)
 * and it is not on the user's allow list. The allow list always wins.
 */
object DomainNames {
    /** Lower case, no trailing dot, no spaces. Returns null for anything that is not a plausible host name. */
    fun normalize(raw: String): String? {
        var s = raw.trim().lowercase()
        while (s.endsWith(".")) s = s.dropLast(1)
        if (s.isEmpty() || s.length > 253) return null
        for (ch in s) {
            val ok = ch in 'a'..'z' || ch in '0'..'9' || ch == '-' || ch == '.' || ch == '_' || ch.code > 127
            if (!ok) return null
        }
        if (s.startsWith(".") || s.contains("..")) return null
        return s
    }

    /** The name followed by each parent, longest first: a.b.c -> a.b.c, b.c (the bare last label is not checked). */
    fun withParents(name: String): List<String> {
        val out = ArrayList<String>(4)
        var cur = name
        while (true) {
            out.add(cur)
            val dot = cur.indexOf('.')
            if (dot < 0) break
            val parent = cur.substring(dot + 1)
            if (!parent.contains('.')) break
            cur = parent
        }
        return out
    }

    /** 64 bit FNV-1a with a final mix. Lists are stored as these hashes (1.3 million names fit in about 10 MB). */
    fun hash(name: String): Long {
        var h = -3750763034362895579L // FNV offset basis
        for (ch in name) {
            h = h xor ch.code.toLong()
            h *= 1099511628211L
        }
        h = h xor (h ushr 33)
        h *= -49064778989728563L
        h = h xor (h ushr 33)
        h *= -4265267296055464877L
        h = h xor (h ushr 33)
        return h
    }
}

object ListParser {
    /**
     * Reads one list line. Accepts hosts format ("0.0.0.0 example.com", "127.0.0.1 example.com"), a bare domain,
     * "||example.com^" style and comment lines. Returns null for blanks, comments and anything not a host name.
     */
    fun parseLine(line: String): String? {
        var s = line.trim()
        if (s.isEmpty() || s.startsWith("#") || s.startsWith("!")) return null
        val hash = s.indexOf('#')
        if (hash > 0) s = s.substring(0, hash).trim()
        if (s.startsWith("||")) s = s.removePrefix("||").substringBefore('^')
        val parts = s.split(' ', '\t').filter { it.isNotEmpty() }
        val host = when (parts.size) {
            1 -> parts[0]
            2 -> if (parts[0] == "0.0.0.0" || parts[0] == "127.0.0.1" || parts[0] == "::") parts[1] else return null
            else -> return null
        }
        val n = DomainNames.normalize(host) ?: return null
        if (n == "localhost" || !n.contains('.')) return null
        if (n.all { it.isDigit() || it == '.' }) return null // an IP address, not a name
        return n
    }

    /** Parses a (possibly gzip) list stream into a sorted, de-duplicated array of name hashes. */
    fun hashesFrom(stream: InputStream, gzip: Boolean): LongArray {
        val src = if (gzip) GZIPInputStream(stream, 64 * 1024) else stream
        var buf = LongArray(1 shl 20)
        var n = 0
        BufferedReader(InputStreamReader(src, Charsets.UTF_8), 64 * 1024).use { r ->
            while (true) {
                val line = r.readLine() ?: break
                val d = parseLine(line) ?: continue
                if (n == buf.size) buf = buf.copyOf(buf.size * 2)
                buf[n++] = DomainNames.hash(d)
            }
        }
        return sortedUnique(buf, n)
    }

    fun sortedUnique(a: LongArray, n: Int): LongArray {
        Arrays.sort(a, 0, n)
        var w = 0
        for (i in 0 until n) {
            if (i == 0 || a[i] != a[i - 1]) a[w++] = a[i]
        }
        return a.copyOf(w)
    }
}

/** The block list as sorted hashes. */
class BlockSet(private val sorted: LongArray) {
    val size: Int get() = sorted.size
    fun contains(name: String): Boolean = Arrays.binarySearch(sorted, DomainNames.hash(name)) >= 0
    fun toArray(): LongArray = sorted
}

/**
 * Official Indian institution domains are never blocked, whatever any list says. This is fixed in the app and cannot be
 * switched off. It covers government (gov.in, nic.in, gov), universities and research bodies (ac.in, edu.in, res.in, edu).
 * Courts are under gov.in and nic.in. Official bank domains (every .bank.in name plus the verified list in BankGuard) are never blocked either.
 */
object OfficialDomains {
    val SUFFIXES: Set<String> = setOf("gov", "gov.in", "nic.in", "ac.in", "edu.in", "res.in", "edu")

    /** True when [name] is one of the suffixes or a name under one. Expects a normalized name. */
    fun isOfficial(name: String): Boolean {
        for (o in SUFFIXES) if (name == o || name.endsWith("." + o)) return true
        return BankGuard.isOfficial(name)
    }
}

class SiteMatcher(private val block: BlockSet, allow: Collection<String>) {
    private val allowed: Set<String> = allow.mapNotNull { DomainNames.normalize(it) }.toSet()

    /** True when the name should be answered as "does not exist". The allow list wins, parents included. */
    fun isBlocked(rawName: String): Boolean {
        val name = DomainNames.normalize(rawName) ?: return false
        if (OfficialDomains.isOfficial(name)) return false
        val chain = DomainNames.withParents(name)
        for (c in chain) if (c in allowed) return false
        for (c in chain) if (block.contains(c)) return true
        return false
    }
}

/** Reads and writes the hash cache so the big text list is only parsed once. */
object BlockSetFile {
    private const val MAGIC = 0x53474231 // "SGB1"
    fun write(out: java.io.OutputStream, hashes: LongArray) {
        val d = java.io.DataOutputStream(java.io.BufferedOutputStream(out, 64 * 1024))
        d.writeInt(MAGIC); d.writeInt(hashes.size)
        for (h in hashes) d.writeLong(h)
        d.flush()
    }
    fun read(input: InputStream): LongArray? {
        val d = java.io.DataInputStream(java.io.BufferedInputStream(input, 64 * 1024))
        if (d.readInt() != MAGIC) return null
        val n = d.readInt()
        if (n < 0 || n > 20_000_000) return null
        val a = LongArray(n)
        for (i in 0 until n) a[i] = d.readLong()
        return a
    }
}
