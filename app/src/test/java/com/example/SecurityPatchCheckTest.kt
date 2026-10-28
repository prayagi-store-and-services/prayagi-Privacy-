package com.example

import com.example.service.SecurityPatchCheck
import org.junit.Assert.assertEquals
import org.junit.Test

class SecurityPatchCheckTest {

    @Test fun sameFullDateIsUpToDate() {
        val r = SecurityPatchCheck.evaluate("2026-09-05", "2026-09-05")
        assertEquals(SecurityPatchCheck.Status.UP_TO_DATE, r.status)
        assertEquals(0, r.monthsBehind)
    }

    @Test fun newerDevicePatchCountsAsUpToDate() {
        val r = SecurityPatchCheck.evaluate("2026-10-01", "2026-09-05")
        assertEquals(SecurityPatchCheck.Status.UP_TO_DATE, r.status)
    }

    @Test fun sameMonthEarlierDayIsBehind() {
        // A later patch day can cover fixes absent from an earlier day.
        val r = SecurityPatchCheck.evaluate("2026-09-01", "2026-09-05")
        assertEquals(SecurityPatchCheck.Status.BEHIND, r.status)
        assertEquals(0, r.monthsBehind)
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
    @Test fun invalidOrPartialDatesAreUnknown() {
        listOf("2026-10", "2026-10-00", "2026-10-32", "2026-02-29", "2026-04-31",
            "2026-10-01-extra", "2026-1-01", "2026-10-1", "2026-10-99", " 2026-10-01 ",
            "2014-10-01", "2100-02-29").forEach { level ->
            assertEquals(level, SecurityPatchCheck.Status.UNKNOWN, SecurityPatchCheck.evaluate(level).status)
            assertEquals(level, SecurityPatchCheck.Status.UNKNOWN, SecurityPatchCheck.evaluate("2026-10-01", level).status)
        }
    }

    @Test fun actualLeapDayAndLaterSameMonthAreReadable() {
        assertEquals(SecurityPatchCheck.Status.UP_TO_DATE,
            SecurityPatchCheck.evaluate("2024-02-29", "2024-02-01").status)
        assertEquals(SecurityPatchCheck.Status.UP_TO_DATE,
            SecurityPatchCheck.evaluate("2026-09-05", "2026-09-01").status)
    }
}
