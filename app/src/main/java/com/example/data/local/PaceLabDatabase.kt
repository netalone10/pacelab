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
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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
    val isSeed: Boolean,
    // Encoded bpm -> seconds histogram. NULL = not processed yet, "" = processed, no HR data.
    val hrHistogram: String? = null
)

/**
 * Sessions the user deleted. Health Connect still holds them, so without this list every sync
 * would bring a deleted run (or a resolved duplicate) straight back.
 */
@Entity(tableName = "ignored_sessions")
data class IgnoredSessionEntity(
    @PrimaryKey val id: String
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

    /** Real (non demo) sessions that already have their heart rate histogram stored. */
    @Query("SELECT id FROM cached_sessions WHERE isSeed = 0 AND hrHistogram IS NOT NULL")
    suspend fun getSyncedSessionIds(): List<String>

    @Query("SELECT id FROM cached_sessions WHERE isSeed = 0")
    suspend fun getRealSessionIds(): List<String>

    @Query("SELECT COUNT(*) FROM cached_sessions WHERE isSeed = 0")
    suspend fun countRealSessions(): Int

    @Query("DELETE FROM cached_sessions WHERE id = :id")
    suspend fun deleteSession(id: String)

    @Query("DELETE FROM cached_sessions WHERE id IN (:ids)")
    suspend fun deleteSessions(ids: List<String>)

    @Query("DELETE FROM cached_sessions WHERE isSeed = 1")
    suspend fun deleteSeedSessions()

    @Query("DELETE FROM cached_sessions")
    suspend fun clearAllSessions()

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun ignoreSessions(sessions: List<IgnoredSessionEntity>)

    @Query("SELECT id FROM ignored_sessions")
    suspend fun getIgnoredIds(): List<String>

    @Query("DELETE FROM ignored_sessions")
    suspend fun clearIgnoredSessions()

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
        IgnoredSessionEntity::class,
        PlanStateEntity::class,
        UserSettingsEntity::class,
        RaceEventEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class PaceLabDatabase : RoomDatabase() {
    abstract fun dao(): PaceLabDao

    companion object {
        @Volatile
        private var INSTANCE: PaceLabDatabase? = null

        /** Keeps settings, plan progress and the target race when the schema grows. */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE cached_sessions ADD COLUMN hrHistogram TEXT")
                db.execSQL("CREATE TABLE IF NOT EXISTS ignored_sessions (id TEXT NOT NULL, PRIMARY KEY(id))")
            }
        }

        fun getDatabase(context: Context): PaceLabDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    PaceLabDatabase::class.java,
                    "pacelab_local.db"
                )
                    .addMigrations(MIGRATION_2_3)
                    // Only used when no migration path exists (for example an unknown old version).
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
