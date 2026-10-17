package com.example

import com.example.service.SecurityPatchCheck
import org.junit.Assert.assertEquals
import org.junit.Test

class SecurityPatchCheckTest {

    @Test fun sameMonthIsUpToDate() {
        val r = SecurityPatchCheck.evaluate("2026-09-05", "2026-09-05")
        assertEquals(SecurityPatchCheck.Status.UP_TO_DATE, r.status)
        assertEquals(0, r.monthsBehind)
    }

    @Test fun newerDevicePatchCountsAsUpToDate() {
        val r = SecurityPatchCheck.evaluate("2026-10-01", "2026-09-05")
        assertEquals(SecurityPatchCheck.Status.UP_TO_DATE, r.status)
    }

    @Test fun sameMonthEarlierDayIsUpToDate() {
        // Patch levels within one month are the same bulletin month.
        val r = SecurityPatchCheck.evaluate("2026-09-01", "2026-09-05")
        assertEquals(SecurityPatchCheck.Status.UP_TO_DATE, r.status)
    }

    @Test fun olderMonthCountsMonthsBehind() {
        val r = SecurityPatchCheck.evaluate("2026-06-05", "2026-09-05")
        assertEquals(SecurityPatchCheck.Status.BEHIND, r.status)
        assertEquals(3, r.monthsBehind)
    }

    @Test fun yearBoundaryCountsCorrectly() {
        val r = SecurityPatchCheck.evaluate("2025-11-05", "2026-09-05")
        assertEquals(SecurityPatchCheck.Status.BEHIND, r.status)
        assertEquals(10, r.monthsBehind)
    }

    @Test fun unreadableLevelIsUnknownNeverGuessed() {
        assertEquals(SecurityPatchCheck.Status.UNKNOWN, SecurityPatchCheck.evaluate(null).status)
        assertEquals(SecurityPatchCheck.Status.UNKNOWN, SecurityPatchCheck.evaluate("").status)
        assertEquals(SecurityPatchCheck.Status.UNKNOWN, SecurityPatchCheck.evaluate("unknown").status)
        assertEquals(SecurityPatchCheck.Status.UNKNOWN, SecurityPatchCheck.evaluate("2026-13-01").status)
    }
}
