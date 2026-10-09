package com.example.siteblock

import android.content.Context
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Optional list update. It runs only when the user taps "Update lists now" and contacts only github.com over HTTPS.
 * It downloads one file from this project's own releases, checks its SHA-256 against the published value, checks that it
 * parses and is a sensible size, and only then replaces the list. If anything fails the bundled list stays in use.
 */
object BlocklistUpdater {
    private const val BASE = "https://github.com/prayagi-store-and-services/prayagi-Privacy-/releases/download/blocklist-latest/"
    const val FILE = "blocklist_update.bin"
    private const val MAX_BYTES = 40L * 1024 * 1024
    private const val MIN_NAMES = 200_000

    sealed class Result {
        data class Ok(val names: Int) : Result()
        data class Failed(val reason: String) : Result()
    }

    fun updateFile(c: Context) = File(c.filesDir, FILE)

    fun lastUpdated(c: Context): Long = updateFile(c).takeIf { it.exists() }?.lastModified() ?: 0L

    /** Run off the main thread. */
    fun update(c: Context): Result {
        return try {
            val shaLine = String(download(BASE + "domains.bin.sha256", 1024), Charsets.UTF_8).trim()
            val want = shaLine.split(Regex("\\s+")).firstOrNull()?.lowercase().orEmpty()
            if (!Regex("[0-9a-f]{64}").matches(want)) return Result.Failed("The published checksum was not valid.")
            val data = download(BASE + "domains.bin", MAX_BYTES)
            val got = MessageDigest.getInstance("SHA-256").digest(data).joinToString("") { "%02x".format(it) }
            if (got != want) return Result.Failed("The download did not match its checksum, so it was not used.")
            val hashes = ListParser.hashesFrom(data.inputStream(), gzip = true)
            if (hashes.size < MIN_NAMES) return Result.Failed("The new list looked too small, so it was not used.")
            val tmp = File(c.filesDir, "$FILE.tmp")
            tmp.writeBytes(data)
            if (!tmp.renameTo(updateFile(c))) return Result.Failed("Could not save the new list.")
            File(c.filesDir, "blocklist_v1.stamp").delete()
            Result.Ok(hashes.size)
        } catch (e: Exception) {
            Result.Failed("Could not reach GitHub. The built-in list is still in use.")
        }
    }

    private fun download(url: String, max: Long): ByteArray {
        val con = URL(url).openConnection() as HttpURLConnection
        con.connectTimeout = 15_000; con.readTimeout = 60_000; con.instanceFollowRedirects = true
        try {
            if (con.responseCode != 200) throw IllegalStateException("HTTP " + con.responseCode)
            if (con.url.protocol != "https") throw IllegalStateException("not https")
            val out = java.io.ByteArrayOutputStream()
            con.inputStream.use { s ->
                val buf = ByteArray(64 * 1024)
                while (true) {
                    val n = s.read(buf); if (n < 0) break
                    out.write(buf, 0, n)
                    if (out.size() > max) throw IllegalStateException("too large")
                }
            }
            return out.toByteArray()
        } finally { con.disconnect() }
    }
}
