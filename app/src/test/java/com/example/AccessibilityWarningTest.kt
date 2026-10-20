package com.example

import com.example.data.model.AccessibilityRiskApp
import com.example.service.AccessibilityServiceScanner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AccessibilityWarningTest {

    private fun app(name: String, system: Boolean = false) =
        AccessibilityRiskApp(packageName = "com.test.${name.lowercase()}", appName = name, isSystemApp = system)

    @Test
    fun noEnabledServicesMeansNoWarning() {
        assertNull(AccessibilityServiceScanner.buildWarningSummary(emptyList()))
    }

    @Test
    fun userInstalledAppsAreNamedAndCounted() {
        val warning = AccessibilityServiceScanner.buildWarningSummary(
            listOf(app("Cleaner Pro"), app("Flash Tool"))
        )
        assertNotNull(warning)
        assertTrue(warning!!.startsWith("2 apps you installed can see and control your screen"))
        assertTrue(warning.contains("Cleaner Pro"))
        assertTrue(warning.contains("Flash Tool"))
        // Plain, honest explanation of what accessibility access means.
        assertTrue(warning.contains("passwords and OTPs"))
        assertTrue(warning.contains("turn its accessibility access off"))
    }

    @Test
    fun singleUserAppUsesSingular() {
        val warning = AccessibilityServiceScanner.buildWarningSummary(listOf(app("Cleaner Pro")))
        assertNotNull(warning)
        assertTrue(warning!!.startsWith("1 app you installed can see and control your screen"))
    }

    @Test
    fun onlySystemAppsAreCalledPreInstalled() {
        val warning = AccessibilityServiceScanner.buildWarningSummary(
            listOf(app("TalkBack", system = true), app("Select to Speak", system = true))
        )
        assertNotNull(warning)
        assertTrue(warning!!.startsWith("2 pre-installed apps can see and control your screen"))
    }

    @Test
    fun longListsAreAbbreviatedWithCount() {
        val apps = (1..6).map { app("App$it") }
        val warning = AccessibilityServiceScanner.buildWarningSummary(apps)
        assertNotNull(warning)
        assertTrue(warning!!.contains("and 2 more"))
    }

    @Test
    fun mixedSystemAndUserAppsPreferUserCountInLead() {
        val warning = AccessibilityServiceScanner.buildWarningSummary(
            listOf(app("TalkBack", system = true), app("Cleaner Pro"))
        )
        assertNotNull(warning)
        assertTrue(warning!!.startsWith("1 app you installed"))
        assertTrue(warning.contains("TalkBack"))
        assertTrue(warning.contains("Cleaner Pro"))
    }
}
