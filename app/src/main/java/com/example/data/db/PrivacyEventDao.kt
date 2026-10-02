package com.example.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.model.LedgerCategory
import com.example.data.model.PrivacyEvent
import kotlinx.coroutines.flow.Flow

@Dao
interface PrivacyEventDao {
    @Query("SELECT * FROM privacy_events ORDER BY timestamp DESC")
    fun getAllEvents(): Flow<List<PrivacyEvent>>

    @Query("SELECT * FROM privacy_events ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentEvents(limit: Int): Flow<List<PrivacyEvent>>

    @Query("SELECT * FROM privacy_events WHERE category = :category ORDER BY timestamp DESC")
    fun getEventsByCategory(category: LedgerCategory): Flow<List<PrivacyEvent>>

    @Query("SELECT * FROM privacy_events WHERE eventType = :type ORDER BY timestamp DESC")
    fun getEventsByType(type: String): Flow<List<PrivacyEvent>>

    @Query("SELECT * FROM privacy_events WHERE timestamp >= :startTime AND timestamp <= :endTime ORDER BY timestamp DESC")
    fun getEventsBetween(startTime: Long, endTime: Long): Flow<List<PrivacyEvent>>

    @Query("SELECT COUNT(*) FROM privacy_events WHERE timestamp >= :startTime AND timestamp <= :endTime AND enforcementAction = :action")
    fun getEnforcementCount(startTime: Long, endTime: Long, action: com.example.data.model.EnforcementAction): Flow<Int>

    @Query("SELECT COUNT(*) FROM privacy_events")
    fun getEventCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM privacy_events WHERE category = :category")
    fun getCategoryCount(category: LedgerCategory): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvent(event: PrivacyEvent): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertEvents(events: List<PrivacyEvent>): List<Long>

    @Query("DELETE FROM privacy_events WHERE id = :id")
    suspend fun deleteEventById(id: Long)

    @Query("DELETE FROM privacy_events")
    suspend fun clearAllEvents()
}

