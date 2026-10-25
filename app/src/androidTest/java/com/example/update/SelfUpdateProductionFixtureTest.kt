package com.example.update

import android.content.Context
import android.content.ContextWrapper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.MessageDigest

/** Genuine production archive validation, not production-app self-install proof.
 * Host installs N-1 and uses a test-only applicationId suffix for this candidate.
 * PackageManager and signingInfo are real; no signer override or production bypass.
 */
@RunWith(AndroidJUnit4::class)
class SelfUpdateProductionFixtureTest {
    @Test fun genuineOlderToCurrentAndRefusalCases() {
        val ins = InstrumentationRegistry.getInstrumentation()
        val target = ins.targetContext
        val fixturePackage = "com.aistudio.sensorguard.prvsec"
        assertNotEquals("Host must isolate test candidate from real production fixture", fixturePackage, target.packageName)
        val real = object : ContextWrapper(target) { override fun getPackageName() = fixturePackage }
        val dir = File(target.cacheDir,"updates").apply { mkdirs() }
        fun asset(name:String):File = File(dir,name).also { f -> ins.context.assets.open(name).use { input -> f.outputStream().use { input.copyTo(it) } } }
        val newer = asset("production-new.apk")
        val older = asset("production-old.apk")
        val foreign = asset("production-foreign.apk")
        fun digest(f:File)=MessageDigest.getInstance("SHA-256").digest(f.readBytes()).joinToString("") { "%02x".format(it) }
        fun release(f:File,code:Long,name:String)=AppRelease("v$name",name,code,"","",digest(f),f.length())
        val current = release(newer,54,"1.4.24")
        fun refused(block:()->Unit) { try { block(); fail("Archive unexpectedly accepted") } catch(expected:IllegalStateException) { assertFalse(expected.message.isNullOrBlank()) } }
        try {
            assertEquals(53L,AppUpdater.installedCode(real))
            AppUpdater.verifyArchive(real,newer,current)
            refused { AppUpdater.verifyArchive(real,older,release(older,53,"1.4.23")) }
            refused { AppUpdater.verifyArchive(real,newer,current.copy(sha256="0".repeat(64))) }
            refused { AppUpdater.verifyArchive(real,newer,current.copy(versionCode=55)) }
            refused { AppUpdater.verifyArchive(real,newer,current.copy(versionName="wrong")) }
            refused { AppUpdater.verifyArchive(real,foreign,release(foreign,46,"1.1.37")) }
            val legacy=File(dir,"${AppUpdater.APP_LABEL}-${current.tag}.apk")
            newer.copyTo(legacy,true)
            target.getSharedPreferences("verified_update_artifacts",Context.MODE_PRIVATE).edit().remove(legacy.name).commit()
            assertNull(AppUpdater.savedFile(real,current))
            assertFalse("Legacy file deleted",legacy.exists())
            newer.appendBytes(byteArrayOf(1))
            refused { AppUpdater.verifyArchive(real,newer,current) }
        } finally {
            listOf(newer,older,foreign).forEach { assertTrue("Fixture cache removed", !it.exists() || it.delete()) }
        }
    }
}
