package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.db.AppDatabase
import com.example.data.model.EventSeverity
import com.example.data.model.PrivacyEvent
import com.example.data.repository.PrivacyRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var repository: PrivacyRepository

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = PrivacyRepository(context, db.privacyEventDao())
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `read string from context`() {
        val appName = context.getString(R.string.app_name)
        assertEquals("SensorGuard", appName)
    }

    @Test
    fun `insert and retrieve privacy event`() = runBlocking {
        val event = PrivacyEvent(
            eventType = "GUARD_ARMED",
            title = "SHIELD ARMED: Screen Off",
            description = "Microphone exclusive guard lock engaged.",
            severity = EventSeverity.SHIELD_ON
        )
        val id = db.privacyEventDao().insertEvent(event)
        assertNotNull(id)

        val events = db.privacyEventDao().getAllEvents().first()
        val found = events.find { it.title == "SHIELD ARMED: Screen Off" }
        assertNotNull(found)
        assertEquals("GUARD_ARMED", found?.eventType)
    }

    @Test
    fun `test formatEventsAsCsv generates correct csv structure`() {
        val event = PrivacyEvent(
            id = 1,
            timestamp = 1700000000000L,
            eventType = "CALL_EXCEPTION",
            title = "Voice Call Exception Active",
            description = "Microphone released for phone call",
            severity = EventSeverity.CALL_EXCEPTION,
            relatedPackage = "com.android.dialer"
        )
        val csv = repository.formatEventsAsCsv(listOf(event))
        assertNotNull(csv)
        assert(csv.contains("ID,Timestamp_Epoch,Timestamp_12H_MS,Category,Tri_State,Enforcement_Action"))
        assert(csv.contains("CALL_EXCEPTION"))
        assert(csv.contains("Voice Call Exception Active"))
        assert(csv.contains("com.android.dialer"))
    }

    @Test
    fun `test enforcement action date isolation queries`() = runBlocking {
        val now = System.currentTimeMillis()
        val yesterday = now - 86400000L

        // Insert event for today
        db.privacyEventDao().insertEvent(
            PrivacyEvent(
                timestamp = now,
                eventType = "GUARD_ARMED",
                title = "Hardware Shield Armed",
                description = "Screen off microphone lock",
                enforcementAction = com.example.data.model.EnforcementAction.BLOCKED
            )
        )

        // Insert event for yesterday
        db.privacyEventDao().insertEvent(
            PrivacyEvent(
                timestamp = yesterday,
                eventType = "CAMERA_POLICY_BLOCKED",
                title = "Camera blocked",
                description = "Device admin policy",
                enforcementAction = com.example.data.model.EnforcementAction.BLOCKED
            )
        )

        // Query today's count
        val todayBlocked = db.privacyEventDao().getEnforcementCount(
            action = com.example.data.model.EnforcementAction.BLOCKED,
            startTime = now - 3600000L,
            endTime = now + 3600000L
        ).first()
        assertEquals(1, todayBlocked)

        // Query yesterday's count
        val yesterdayBlocked = db.privacyEventDao().getEnforcementCount(
            action = com.example.data.model.EnforcementAction.BLOCKED,
            startTime = yesterday - 3600000L,
            endTime = yesterday + 3600000L
        ).first()
        assertEquals(1, yesterdayBlocked)
    }

    @Test
    fun `test update emergency lockdown state`() {
        repository.updateEmergencyLockdown(true)
        val state = repository.guardState.value
        assertEquals(true, state.isEmergencyLockdown)

        repository.updateEmergencyLockdown(false)
        val disarmedState = repository.guardState.value
        assertEquals(false, disarmedState.isEmergencyLockdown)
    }
}
