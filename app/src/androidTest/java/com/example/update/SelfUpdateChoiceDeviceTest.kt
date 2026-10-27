package com.example.update
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.core.app.ActivityScenario
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File
@RunWith(AndroidJUnit4::class)
class SelfUpdateChoiceDeviceTest {
 @Test fun legacyNotificationIntentCannotOfferDownloadOrInstall() {
  val ins=InstrumentationRegistry.getInstrumentation();val c=ins.targetContext
  ins.runOnMainSync { com.example.Brand.applyLauncherName(c) }
  val dir=File(c.cacheDir,"updates").apply { mkdirs() };File(dir,"legacy.apk").writeText("old cache")
  val launch=android.content.Intent(c,com.example.NetraMainActivity::class.java).putExtra("netra_auto_update",true).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
  ActivityScenario.launch<com.example.NetraMainActivity>(launch).use {
   val deadline=System.currentTimeMillis()+10000
   while(dir.exists() && System.currentTimeMillis()<deadline)Thread.sleep(100)
   assertFalse("Legacy APK cache removed",dir.exists())
   fun text(n:android.view.accessibility.AccessibilityNodeInfo?):List<String> = if(n==null)emptyList() else listOfNotNull(n.text?.toString())+(0 until n.childCount).flatMap { text(n.getChild(it)) }
   val labels=text(ins.uiAutomation.rootInActiveWindow)
   assertFalse(labels.any { it=="Download" || it=="Install" || it.startsWith("Download KBC update") || it.startsWith("Download SensorGuard update") })
   android.os.ParcelFileDescriptor.AutoCloseInputStream(ins.uiAutomation.executeShellCommand("screencap -p /data/local/tmp/centralized-no-updater.png")).use { it.readBytes() }
  }
 }
 @Test fun ecoAbsentNeverRestoresOwnDownloader() {
  val c=InstrumentationRegistry.getInstrumentation().targetContext
  // Browser fallback is allowed. No installed-app/browser availability assumption here.
  // Exact destination, old-Eco/self-handler exclusion and no-browser result are deterministic Robolectric regressions.
  assertEquals("https", android.net.Uri.parse(AppUpdater.ECO_PAGE).scheme)
  assertFalse(AppUpdater.ECO_PAGE.endsWith(".apk"))
  assertFalse(AppUpdater::class.java.declaredMethods.any { it.name=="download" || it.name=="install" })
 }
}
