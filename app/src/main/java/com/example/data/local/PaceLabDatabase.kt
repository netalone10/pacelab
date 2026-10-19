package com.example.data.local

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "cached_sessions")
data class CachedSessionEntity(
    @PrimaryKey val id: String,
    val startTimeEpochMs: Long,
    val endTimeEpochMs: Long,
    val distanceMeters: Double,
    val durationSeconds: Long,
    val avgPaceSeconds: Int,
    val avgHeartRate: Int?,
    val maxHeartRate: Int?,
    val totalCalories: Double?,
    val elevationGainedMeters: Double?,
    val isSeed: Boolean
)

@Entity(tableName = "plan_states")
data class PlanStateEntity(
    @PrimaryKey val planKey: String, // e.g. "w1_TUESDAY"
    val isDone: Boolean,
    val matchedDistanceMeters: Double?,
    val matchedPaceSec: Int?,
    val matchedDateEpochDay: Long?
)

@Entity(tableName = "user_settings")
data class UserSettingsEntity(
    @PrimaryKey val id: Int = 1,
    val refDistanceMeters: Double = 5310.0,
    val refDurationSeconds: Long = 2157L,
    val refAvgHeartRate: Int = 151,
    val vo2Max: Double = 48.0,
    val targetDistanceMeters: Double = 10000.0,
    val trainingDaysPerWeek: Int = 3,
    val z1Max: Int = 117,
    val z2Min: Int = 118,
    val z2Max: Int = 140,
    val z3Min: Int = 141,
    val z3Max: Int = 160,
    val z4Min: Int = 161,
    val z4Max: Int = 179,
    val maxHr: Int = 190
)

@Entity(tableName = "target_race_events")
data class RaceEventEntity(
    @PrimaryKey val id: Int = 1,
    val raceName: String,
    val raceDateEpochDay: Long,
    val distanceMeters: Double,
    val targetDurationSeconds: Long?,
    val targetPaceSeconds: Int?,
    val location: String?
)

@Dao
interface PaceLabDao {

    @Query("SELECT * FROM cached_sessions ORDER BY startTimeEpochMs DESC")
    fun getAllSessionsFlow(): Flow<List<CachedSessionEntity>>

    @Query("SELECT * FROM cached_sessions ORDER BY startTimeEpochMs DESC")
    suspend fun getAllSessionsList(): List<CachedSessionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSessions(sessions: List<CachedSessionEntity>)

    @Query("DELETE FROM cached_sessions WHERE id = :id")
    suspend fun deleteSession(id: String)

    @Query("DELETE FROM cached_sessions WHERE id IN (:ids)")
    suspend fun deleteSessions(ids: List<String>)

    @Query("DELETE FROM cached_sessions WHERE isSeed = 1")
    suspend fun deleteSeedSessions()

    @Query("DELETE FROM cached_sessions")
    suspend fun clearAllSessions()

    @Query("SELECT * FROM plan_states")
    fun getPlanStatesFlow(): Flow<List<PlanStateEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun savePlanState(state: PlanStateEntity)

    @Query("SELECT * FROM user_settings WHERE id = 1")
    fun getUserSettingsFlow(): Flow<UserSettingsEntity?>

    @Query("SELECT * FROM user_settings WHERE id = 1")
    suspend fun getUserSettings(): UserSettingsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveUserSettings(settings: UserSettingsEntity)

    @Query("SELECT * FROM target_race_events WHERE id = 1")
    fun getTargetRaceFlow(): Flow<RaceEventEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveTargetRace(event: RaceEventEntity)

    @Query("DELETE FROM target_race_events WHERE id = 1")
    suspend fun deleteTargetRace()
}

@Database(
    entities = [
        CachedSessionEntity::class,
        PlanStateEntity::class,
        UserSettingsEntity::class,
        RaceEventEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class PaceLabDatabase : RoomDatabase() {
    abstract fun dao(): PaceLabDao

    companion object {
        @Volatile
        private var INSTANCE: PaceLabDatabase? = null

        fun getDatabase(context: Context): PaceLabDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    PaceLabDatabase::class.java,
                    "pacelab_local.db"
                ).fallbackToDestructiveMigration(dropAllTables = true).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
