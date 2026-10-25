package com.example.update

import android.content.Intent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.core.app.ActivityScenario
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class SelfUpdateChoiceDeviceTest {
    private fun nodes(n:AccessibilityNodeInfo?):List<AccessibilityNodeInfo> = if(n==null) emptyList() else listOf(n)+(0 until n.childCount).flatMap { nodes(n.getChild(it)) }
    @Test fun notificationOffersDownloadAndNotNowDoesNotCreateApk() {
        val ins=InstrumentationRegistry.getInstrumentation();val c=ins.targetContext
        val dir=File(c.cacheDir,"updates")
        val before=dir.listFiles()?.filter { it.extension=="apk" }?.map { it.name to it.length() }?.sortedBy { it.first }.orEmpty()
        val intent=Intent(c,com.example.NetraMainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ActivityScenario.launch<com.example.NetraMainActivity>(intent).use {
            // Exercise the PendingIntent actually produced by production notification code.
            android.os.ParcelFileDescriptor.AutoCloseInputStream(ins.uiAutomation.executeShellCommand("pm grant ${c.packageName} android.permission.POST_NOTIFICATIONS")).use { it.readBytes() }
            c.getSharedPreferences("netra_update_alert",android.content.Context.MODE_PRIVATE).edit().clear().commit()
            val nm=c.getSystemService(android.content.Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            nm.cancelAll()
            UpdateAlert.notifyIfNew(c,AppRelease("v9.9.9","9.9.9",999999,"","","0".repeat(64),1),"",false)
            assertEquals(android.content.pm.PackageManager.PERMISSION_GRANTED,c.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS))
            assertTrue("Notifications enabled on disposable candidate",nm.areNotificationsEnabled())
            // NotificationManager queues publication through system_server; wait for actual visibility.
            val notificationDeadline=System.currentTimeMillis()+10000
            var posted=nm.activeNotifications.firstOrNull { it.id==7431 }
            while(posted==null && System.currentTimeMillis()<notificationDeadline) {
                Thread.sleep(100);posted=nm.activeNotifications.firstOrNull { it.id==7431 }
            }
            assertNotNull("Production update notification 7431 posted within 10s",posted)
            val notification=posted!!.notification
            assertNotNull("Production notification PendingIntent",notification.contentIntent)
            notification.contentIntent.send()
            var cancel:AccessibilityNodeInfo?=null
            val deadline=System.currentTimeMillis()+15000
            while(cancel==null && System.currentTimeMillis()<deadline) {
                cancel=nodes(ins.uiAutomation.rootInActiveWindow).firstOrNull { it.text?.toString()?.equals("Not now",true)==true }
                if(cancel==null)Thread.sleep(100)
            }
            assertNotNull("Notification must offer Not now",cancel)
            val text=nodes(ins.uiAutomation.rootInActiveWindow).mapNotNull { it.text?.toString() }
            assertTrue("Explicit Download",text.any { it.equals("Download",true) })
            assertTrue("No security bypass advice",text.any { it.contains("Keep Play Protect enabled") })
            android.os.ParcelFileDescriptor.AutoCloseInputStream(ins.uiAutomation.executeShellCommand("screencap -p /data/local/tmp/updater-choice.png")).use { it.readBytes() }
            assertTrue(cancel!!.performAction(AccessibilityNodeInfo.ACTION_CLICK));ins.waitForIdleSync()
            val after=dir.listFiles()?.filter { it.extension=="apk" }?.map { it.name to it.length() }?.sortedBy { it.first }.orEmpty()
            assertEquals(before,after)
            assertEquals(c.packageName,ins.uiAutomation.rootInActiveWindow?.packageName?.toString())
        }
    }
    /** Actual candidate provider URI readability, deliberately NOT AppUpdater.install acceptance proof. */
    @Test fun realProductionArchiveCanBeReadByExternalInstallerThroughCandidateProvider() {
        val ins=InstrumentationRegistry.getInstrumentation();val c=ins.targetContext
        val f=File(File(c.cacheDir,"updates").apply{mkdirs()},"provider-production.apk")
        ins.context.assets.open("production-new.apk").use { input -> f.outputStream().use { input.copyTo(it) } }
        try {
            fun shell(command:String)=android.os.ParcelFileDescriptor.AutoCloseInputStream(ins.uiAutomation.executeShellCommand(command)).use { String(it.readBytes()) }
            shell("appops set ${c.packageName} REQUEST_INSTALL_PACKAGES allow")
            assertTrue("Disposable candidate source permission", c.packageManager.canRequestPackageInstalls())
            val uri=androidx.core.content.FileProvider.getUriForFile(c,c.packageName+".updates",f)
            c.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri,"application/vnd.android.package-archive").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK))
            val archive=c.packageManager.getPackageArchiveInfo(f.path,0) ?: error("Production archive missing")
            assertEquals("com.aistudio.sensorguard.prvsec",archive.packageName)
            val info=archive.applicationInfo ?: error("Production archive application missing")
            info.sourceDir=f.path;info.publicSourceDir=f.path
            val expectedLabel=info.loadLabel(c.packageManager).toString()
            assertTrue("Real production label available",expectedLabel.isNotBlank())
            val deadline=System.currentTimeMillis()+15000;var seen=false
            while(!seen && System.currentTimeMillis()<deadline) {
                val texts=nodes(ins.uiAutomation.rootInActiveWindow).mapNotNull { it.text?.toString() }
                val tree=nodes(ins.uiAutomation.rootInActiveWindow)
                seen=tree.any { it.packageName?.toString()?.contains("packageinstaller")==true } && texts.any { it.equals(expectedLabel,true) } && texts.any { it.equals("Update",true) }
                if(!seen)Thread.sleep(100)
            }
            android.os.ParcelFileDescriptor.AutoCloseInputStream(ins.uiAutomation.executeShellCommand("screencap -p /data/local/tmp/updater-installer.png")).use { it.readBytes() }
            assertTrue("External installer decoded real production label: $expectedLabel and update action",seen)
            val cancel=nodes(ins.uiAutomation.rootInActiveWindow).firstOrNull { it.text?.toString()?.equals("Cancel",true)==true }
            assertNotNull("Cancel without updating fixture",cancel);assertTrue(cancel!!.performAction(AccessibilityNodeInfo.ACTION_CLICK))
        } finally { assertTrue(!f.exists()||f.delete()) }
    }
    /** Own debug signer/package end-to-end install path on disposable candidate only, not production signing proof. */
    @Test fun actualInstallValidatesOwnNewerApkAndOpensSystemConfirmation() {
        val ins=InstrumentationRegistry.getInstrumentation();val c=ins.targetContext
        assertTrue("Disposable candidate only",c.packageName.endsWith(".securityproof"))
        fun shell(cmd:String)=android.os.ParcelFileDescriptor.AutoCloseInputStream(ins.uiAutomation.executeShellCommand(cmd)).use { String(it.readBytes()) }
        val f=File(File(c.cacheDir,"updates").apply{mkdirs()},"own-debug-new.apk")
        ins.context.assets.open("own-debug-new.apk").use { input->f.outputStream().use { input.copyTo(it) } }
        val pm=c.packageManager
        val archive=pm.getPackageArchiveInfo(f.path,android.content.pm.PackageManager.GET_SIGNING_CERTIFICATES)!!
        val before=AppUpdater.installedCode(c)
        assertEquals("Genuine newer APK",before+1,archive.longVersionCode)
        assertEquals(c.packageName,archive.packageName)
        val sha=java.security.MessageDigest.getInstance("SHA-256").digest(f.readBytes()).joinToString("") { "%02x".format(it) }
        val metadata=org.json.JSONObject().put("tag","v${archive.versionName}").put("name",archive.versionName).put("code",archive.longVersionCode).put("sha",sha).put("size",f.length())
        assertTrue(c.getSharedPreferences("verified_update_artifacts",android.content.Context.MODE_PRIVATE).edit().putString(f.name,metadata.toString()).commit())
        try {
            shell("appops set ${c.packageName} REQUEST_INSTALL_PACKAGES allow")
            assertTrue(c.packageManager.canRequestPackageInstalls())
            AppUpdater.install(c,f)
            val deadline=System.currentTimeMillis()+15000;var seen=false
            while(!seen && System.currentTimeMillis()<deadline) {
                val tree=nodes(ins.uiAutomation.rootInActiveWindow)
                seen=tree.any { it.packageName?.toString()?.contains("packageinstaller")==true } && tree.any { it.text?.toString()?.equals("Update",true)==true }
                if(!seen)Thread.sleep(100)
            }
            assertTrue("Actual validated install opens system confirmation",seen)
            shell("screencap -p /data/local/tmp/updater-own-install.png")
            val cancel=nodes(ins.uiAutomation.rootInActiveWindow).firstOrNull { it.text?.toString()?.equals("Cancel",true)==true }
            assertNotNull(cancel);assertTrue(cancel!!.performAction(AccessibilityNodeInfo.ACTION_CLICK))
            assertEquals("No update installed",before,AppUpdater.installedCode(c))
        } finally { f.delete();c.getSharedPreferences("verified_update_artifacts",android.content.Context.MODE_PRIVATE).edit().remove(f.name).commit() }
    }
}
