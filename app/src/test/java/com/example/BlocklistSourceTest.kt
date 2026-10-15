package com.example

import com.example.siteblock.BlocklistUpdater
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class BlocklistSourceTest {
    private val now = 1_800_000_000_000L
    private val sha = "ab".repeat(32)
    private fun json() = JSONObject().put("schema", 1).put("builtAtEpochSeconds", now / 1000 - 86400)
        .put("sha256", sha).put("size", 7000000).put("names", 250000).put("file", "domains-$sha.bin")

    @Test fun validSourceUsesBuildDateNotDownloadDate() {
        val source = BlocklistUpdater.parseSource(json().toString(), now)!!
        assertEquals(now - 86400000L, source.builtAtMs)
        assertEquals(250000, source.names)
    }
    @Test fun invalidMetadataIsRejected() {
        for (bad in listOf(
            json().put("schema", 2), json().put("size", 0), json().put("size", 50000000),
            json().put("names", 12), json().put("sha256", "bad"),
            json().put("file", "../domains.bin"), json().put("file", "https://evil.invalid/a"),
            json().put("builtAtEpochSeconds", now / 1000 + 301), json().put("builtAtEpochSeconds", -1)
        )) assertNull(BlocklistUpdater.parseSource(bad.toString(), now))
        assertNull(BlocklistUpdater.parseSource("not json", now))
        assertNull(BlocklistUpdater.parseSource("{}", now))
    }
    @Test fun unknownSourceNeverPretendsDownloadDateIsFresh() {
        val text = BlocklistUpdater.sourceStatus(0, now) { "T$it" }
        assertTrue(text.contains("Unavailable"))
        assertTrue(text.contains("not source freshness"))
    }
    @Test fun staleSourceWarnsAt48Hours() {
        assertFalse(BlocklistUpdater.sourceStatus(now - 47 * 3600000L, now) { "T$it" }.contains("may be delayed"))
        assertTrue(BlocklistUpdater.sourceStatus(now - 48 * 3600000L, now) { "T$it" }.contains("may be delayed"))
    }
}
