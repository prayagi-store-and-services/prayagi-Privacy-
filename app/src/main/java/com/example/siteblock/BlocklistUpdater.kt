package com.example.siteblock

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * List update used by the existing automatic workers and the manual update button. Uses project GitHub releases over HTTPS.
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

    /** This is the device download time, never the source build date. */
    fun lastUpdated(c: Context): Long = updateFile(c).takeIf { it.exists() }?.lastModified() ?: 0L

    data class Source(val builtAtMs: Long, val sha256: String, val size: Long, val names: Int, val file: String)

    fun parseSource(json: String, nowMs: Long): Source? = try {
        val o = JSONObject(json)
        val sha = o.getString("sha256")
        val seconds = o.getLong("builtAtEpochSeconds")
        val size = o.getLong("size")
        val names = o.getInt("names")
        val file = o.getString("file")
        if (o.getInt("schema") != 1 || !Regex("[0-9a-f]{64}").matches(sha) ||
            seconds < 1_700_000_000L || seconds > nowMs / 1000L + 300L ||
            size <= 0L || size > MAX_BYTES || names < MIN_NAMES ||
            file != "domains-$sha.bin") null
        else Source(seconds * 1000L, sha, size, names, file)
    } catch (_: Exception) { null }

    /** Fail closed on old, missing or mismatched local provenance. Never substitute file mtime. */
    fun sourceBuiltAt(c: Context): Long = try {
        val dataFile = updateFile(c)
        val meta = JSONObject(File(c.filesDir, "$FILE.source.json").readText())
        val source = parseSource(meta.toString(), System.currentTimeMillis())
        if (source != null && dataFile.exists() && dataFile.length() == source.size &&
            meta.optLong("installedAtMs", -1L) == dataFile.lastModified()) source.builtAtMs else 0L
    } catch (_: Exception) { 0L }

    fun sourceStatus(builtAtMs: Long, nowMs: Long, fmt: (Long) -> String): String {
        if (builtAtMs <= 0L || builtAtMs > nowMs + 300_000L) return "Source build date: Unavailable. Download time is not source freshness."
        val ageHours = ((nowMs - builtAtMs).coerceAtLeast(0L) / 3_600_000L)
        return "Source built: " + fmt(builtAtMs) + " (" + ageHours + " hours ago). " +
            if (ageHours >= 48L) "Source is over 48 hours old; the daily refresh may be delayed. New upstream sites are not guaranteed included." else
                "Daily build target; this does not guarantee every newly blocked site is listed."
    }

    /** Run off the main thread. */
    fun update(c: Context): Result {
        return try {
            val rawMeta = String(download(BASE + "blocklist.json", 4096), Charsets.UTF_8)
            val source = parseSource(rawMeta, System.currentTimeMillis())
                ?: return Result.Failed("The source metadata was invalid. The previous list was kept.")
            val oldBuiltAt = sourceBuiltAt(c)
            if (oldBuiltAt > source.builtAtMs) return Result.Failed("An older source was offered. The previous list was kept.")
            val data = download(BASE + source.file, MAX_BYTES)
            val got = MessageDigest.getInstance("SHA-256").digest(data).joinToString("") { "%02x".format(it) }
            if (data.size.toLong() != source.size || got != source.sha256)
                return Result.Failed("The download did not match its metadata, so it was not used.")
            val hashes = ListParser.hashesFrom(data.inputStream(), gzip = true)
            if (hashes.size != source.names || hashes.size < MIN_NAMES)
                return Result.Failed("The new list count did not match, so it was not used.")
            val tmp = File(c.filesDir, "$FILE.tmp")
            tmp.writeBytes(data)
            // Prepare both files before replacing the validated working list.
            val meta = JSONObject(rawMeta).put("installedAtMs", tmp.lastModified())
            val metaTmp = File(c.filesDir, "$FILE.source.tmp")
            metaTmp.writeText(meta.toString())
            if (!tmp.renameTo(updateFile(c))) return Result.Failed("Could not save the new list. The previous list was kept.")
            // Provenance is installed last. On interruption the UI reports Unavailable, never a guessed source date.
            if (!metaTmp.renameTo(File(c.filesDir, "$FILE.source.json")))
                File(c.filesDir, "$FILE.source.json").delete()
            File(c.filesDir, "blocklist_v1.stamp").delete()
            Result.Ok(hashes.size)
        } catch (e: Exception) {
            Result.Failed("Could not reach GitHub. The previous list is still in use.")
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
