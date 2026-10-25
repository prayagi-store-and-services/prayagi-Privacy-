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
        val intent=Intent(c,com.example.NetraMainActivity::class.java).putExtra(UpdateAlert.EXTRA_AUTO_UPDATE,true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ActivityScenario.launch<com.example.NetraMainActivity>(intent).use {
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
            val deadline=System.currentTimeMillis()+15000;var seen=false
            while(!seen && System.currentTimeMillis()<deadline) {
                val texts=nodes(ins.uiAutomation.rootInActiveWindow).mapNotNull { it.text?.toString() }
                val tree=nodes(ins.uiAutomation.rootInActiveWindow)
                seen=tree.any { it.packageName?.toString()?.contains("packageinstaller")==true } && texts.any { it.contains("SensorGuard",true) } && texts.any { it.equals("Update",true) }
                if(!seen)Thread.sleep(100)
            }
            assertTrue("External installer decoded real production app and update action",seen)
            android.os.ParcelFileDescriptor.AutoCloseInputStream(ins.uiAutomation.executeShellCommand("screencap -p /data/local/tmp/updater-installer.png")).use { it.readBytes() }
            val cancel=nodes(ins.uiAutomation.rootInActiveWindow).firstOrNull { it.text?.toString()?.equals("Cancel",true)==true }
            assertNotNull("Cancel without updating fixture",cancel);assertTrue(cancel!!.performAction(AccessibilityNodeInfo.ACTION_CLICK))
        } finally { assertTrue(!f.exists()||f.delete()) }
    }
}
