package com.example

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Source contract, not a claim of real-network/device acceptance. */
class SiteBlockNetworkPolicyTest {
    private fun source(): String = File("src/main/java/com/example/siteblock/SiteBlockVpnService.kt").readText()

    @Test fun dnsOnlyTunnelDoesNotBlockUnconfiguredIpv6Family() {
        assertTrue(source().contains(".allowFamily(android.system.OsConstants.AF_INET6)"))
    }

    @Test fun dnsOnlyTunnelDoesNotRouteAllInternetWithoutAForwarder() {
        val s = source()
        assertTrue(s.contains(".addRoute(FAKE_DNS, 32)"))
        assertFalse(s.contains(".addRoute(\"0.0.0.0\", 0)"))
        assertFalse(s.contains(".addRoute(\"::\", 0)"))
    }
}
