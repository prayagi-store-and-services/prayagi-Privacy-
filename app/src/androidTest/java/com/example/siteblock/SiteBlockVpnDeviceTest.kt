package com.example.siteblock

import android.content.Context
import android.content.Intent
import android.net.VpnService
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL

/**
 * Owner-reported bug (1 Nov 2026): "no internet when the VPN is on". This is the
 * acceptance proof on a real Android network stack: with site blocking ON,
 * ordinary domains must still resolve, HTTPS must still work, and only listed
 * domains must answer "does not exist".
 */
@RunWith(AndroidJUnit4::class)
class SiteBlockVpnDeviceTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun grantVpnAndStart() {
        val ui = InstrumentationRegistry.getInstrumentation().uiAutomation
        ui.executeShellCommand("appops set ${context.packageName} ACTIVATE_VPN allow").close()
        assertNull("VPN consent should be pre-granted via appops", VpnService.prepare(context))
        SiteBlockPrefs(context).enabled = true
        ui.executeShellCommand(
            "am start-foreground-service -n ${context.packageName}/com.example.siteblock.SiteBlockVpnService"
        ).close()
        val deadline = System.currentTimeMillis() + 15000
        while (!SiteBlockVpnService.tunnelActive && System.currentTimeMillis() < deadline) Thread.sleep(250)
        assertTrue("VPN tunnel did not come up in 15s: ${SiteBlockPrefs(context).lastError}", SiteBlockVpnService.tunnelActive)
    }

    @After
    fun stopVpn() {
        val ui = InstrumentationRegistry.getInstrumentation().uiAutomation
        ui.executeShellCommand(
            "am startservice -n ${context.packageName}/com.example.siteblock.SiteBlockVpnService -a com.example.siteblock.STOP"
        ).close()
    }

    @Test
    fun ordinaryDomainsResolveWhileVpnOn() {
        for (host in listOf("example.com", "github.com", "wikipedia.org")) {
            val addrs = try { InetAddress.getAllByName(host) } catch (e: Exception) { null }
            assertTrue("$host must resolve while blocking is on", addrs != null && addrs.isNotEmpty())
        }
    }

    @Test
    fun httpsWorksWhileVpnOn() {
        val conn = (URL("https://example.com/").openConnection() as HttpURLConnection).apply {
            connectTimeout = 10000; readTimeout = 10000
        }
        try {
            val code = conn.responseCode
            assertTrue("HTTPS fetch failed while VPN on: HTTP $code", code in 200..399)
        } finally {
            conn.disconnect()
        }
    }

    @Test
    fun listedDomainIsAnsweredAsNotExisting() {
        val set = SiteBlockLoader.load(context)
        assertNotNull("block list must load", set)
        val matcher = SiteMatcher(set!!, emptySet())
        val candidates = listOf("pornhub.com", "xvideos.com", "xnxx.com", "bet365.com", "1xbet.com", "888casino.com")
        val blocked = candidates.firstOrNull { matcher.isBlocked(it) }
        assertTrue("none of the well-known listed domains were blocked - list content unexpected", blocked != null)
        var threw = false
        try {
            InetAddress.getAllByName(blocked!!)
        } catch (e: java.net.UnknownHostException) {
            threw = true
        }
        assertTrue("blocked domain $blocked must not resolve while blocking is on", threw)
    }

    @Test
    fun allowListedDomainResolvesEvenWhenListed() {
        val set = SiteBlockLoader.load(context)
        assertNotNull(set)
        val matcher = SiteMatcher(set!!, emptySet())
        val blocked = listOf("pornhub.com", "xvideos.com", "bet365.com").firstOrNull { matcher.isBlocked(it) } ?: return
        val prefs = SiteBlockPrefs(context)
        prefs.allow = prefs.allow + blocked
        val ui = InstrumentationRegistry.getInstrumentation().uiAutomation
        try {
            ui.executeShellCommand(
                "am startservice -n ${context.packageName}/com.example.siteblock.SiteBlockVpnService -a com.example.siteblock.STOP"
            ).close()
            var deadline = System.currentTimeMillis() + 10000
            while (SiteBlockVpnService.tunnelActive && System.currentTimeMillis() < deadline) Thread.sleep(250)
            prefs.enabled = true
            ui.executeShellCommand(
                "am start-foreground-service -n ${context.packageName}/com.example.siteblock.SiteBlockVpnService"
            ).close()
            deadline = System.currentTimeMillis() + 15000
            while (!SiteBlockVpnService.tunnelActive && System.currentTimeMillis() < deadline) Thread.sleep(250)
            val addrs = try { InetAddress.getAllByName(blocked) } catch (e: Exception) { null }
            assertTrue("allow-listed $blocked must resolve", addrs != null && addrs.isNotEmpty())
        } finally {
            prefs.allow = prefs.allow - blocked
        }
    }
}
