package com.example.data.repository

import android.content.Context
import com.example.data.health.HealthConnectStatus
import com.example.data.health.SeedDataGenerator
import com.example.data.local.CachedSessionEntity
import com.example.data.local.IgnoredSessionEntity
import com.example.data.local.PaceLabDao
import com.example.data.local.PaceLabDatabase
import com.example.data.local.PlanStateEntity
import com.example.data.local.UserSettingsEntity
import com.example.data.model.RaceDistance
import com.example.data.model.RunSession
import com.example.data.model.UserSettings
import com.example.data.model.WeeklySummary
import com.example.util.FourWeekTrainingLoadSummary
import com.example.util.WeeklySummaryCalculator
import com.example.util.WeeklyTrainingLoadCalculator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate

class PaceLabRepository(
    private val context: Context,
    private val healthConnectRepository: HealthConnectRepository = HealthConnectRepositoryImpl(context),
    private val dao: PaceLabDao = PaceLabDatabase.getDatabase(context).dao()
) {

    val healthRepo: HealthConnectRepository
        get() = healthConnectRepository

    val sessionsFlow: Flow<List<RunSession>> = dao.getAllSessionsFlow().map { entities ->
        val rawSessions = entities.map { it.toDomainModel() }
        val dupIds = com.example.util.PeriodizedPlanner.findDuplicateSessionIds(rawSessions)
        rawSessions.map { it.copy(isDuplicate = it.id in dupIds) }
    }.flowOn(Dispatchers.IO)

    val targetRaceFlow: Flow<com.example.data.model.RaceEvent?> = dao.getTargetRaceFlow().map { entity ->
        entity?.toDomainModel()
    }.flowOn(Dispatchers.IO)

    val planStatesFlow: Flow<Map<String, PlanStateEntity>> = dao.getPlanStatesFlow().map { list ->
        list.associateBy { it.planKey }
    }.flowOn(Dispatchers.IO)

    val userSettingsFlow: Flow<UserSettings> = dao.getUserSettingsFlow().map { entity ->
        entity?.toDomainModel() ?: UserSettings()
    }.flowOn(Dispatchers.IO)

    suspend fun getSdkStatus(): HealthConnectStatus {
        return healthConnectRepository.getAvailabilityStatus()
    }

    suspend fun getGrantedPermissions(): Set<String> {
        return healthConnectRepository.getGrantedPermissions()
    }

    suspend fun syncData(): Result<SyncSummary> {
        return healthConnectRepository.syncAllRunningData()
    }

    /** Demo data is only inserted while no real run is cached, so it never mixes with real data. */
    suspend fun seedDemoData() {
        withContext(Dispatchers.IO) {
            if (dao.countRealSessions() == 0) {
                val demoSessions = SeedDataGenerator.generateDemoSessions()
                dao.insertSessions(demoSessions.map { it.toEntity() })
            }
        }
    }

    suspend fun clearSessions() {
        withContext(Dispatchers.IO) {
            dao.clearAllSessions()
        }
    }

    suspend fun getSessionDetails(
        session: RunSession,
        grantedRoute: androidx.health.connect.client.records.ExerciseRoute? = null
    ): SessionDetails {
        return healthConnectRepository.getSessionSplitsAndHr(session, grantedRoute)
    }

    suspend fun deleteSession(sessionId: String) {
        withContext(Dispatchers.IO) {
            dao.ignoreSessions(listOf(IgnoredSessionEntity(sessionId)))
            dao.deleteSession(sessionId)
        }
    }

    suspend fun deleteSessions(sessionIds: List<String>) {
        withContext(Dispatchers.IO) {
            dao.ignoreSessions(sessionIds.map { IgnoredSessionEntity(it) })
            sessionIds.chunked(500).forEach { dao.deleteSessions(it) }
        }
    }

    suspend fun saveTargetRace(event: com.example.data.model.RaceEvent) {
        withContext(Dispatchers.IO) {
            dao.saveTargetRace(event.toEntity())
        }
    }

    suspend fun deleteTargetRace() {
        withContext(Dispatchers.IO) {
            dao.deleteTargetRace()
        }
    }

    suspend fun togglePlanDayDone(
        planKey: String,
        isDone: Boolean,
        distanceMeters: Double? = null,
        paceSec: Int? = null,
        epochDay: Long? = null
    ) {
        withContext(Dispatchers.IO) {
            dao.savePlanState(
                PlanStateEntity(
                    planKey = planKey,
                    isDone = isDone,
                    matchedDistanceMeters = distanceMeters,
                    matchedPaceSec = paceSec,
                    matchedDateEpochDay = epochDay
                )
            )
        }
    }

    suspend fun saveUserSettings(settings: UserSettings) {
        withContext(Dispatchers.IO) {
            dao.saveUserSettings(settings.toEntity())
        }
    }

    /** Monday to Sunday summary; see [WeeklySummaryCalculator]. */
    fun computeWeeklySummary(
        allSessions: List<RunSession>,
        weekStartMonday: LocalDate,
        userSettings: UserSettings
    ): WeeklySummary = WeeklySummaryCalculator.compute(allSessions, weekStartMonday, userSettings)

    /** 4-week progression of weekly training load. */
    fun compute4WeekTrainingLoad(
        allSessions: List<RunSession>,
        anchorMonday: LocalDate,
        userSettings: UserSettings
    ): FourWeekTrainingLoadSummary = WeeklyTrainingLoadCalculator.compute4WeekProgression(
        allSessions = allSessions,
        anchorMonday = anchorMonday,
        userSettings = userSettings
    )

    private fun CachedSessionEntity.toDomainModel() = RunSession(
        id = id,
        startTime = Instant.ofEpochMilli(startTimeEpochMs),
        endTime = Instant.ofEpochMilli(endTimeEpochMs),
        distanceMeters = distanceMeters,
        durationSeconds = durationSeconds,
        avgPaceSeconds = avgPaceSeconds,
        avgHeartRate = avgHeartRate,
        maxHeartRate = maxHeartRate,
        totalCalories = totalCalories,
        elevationGainedMeters = elevationGainedMeters,
        isSeed = isSeed,
        hrHistogram = hrHistogram
    )

    private fun RunSession.toEntity() = CachedSessionEntity(
        id = id,
        startTimeEpochMs = startTime.toEpochMilli(),
        endTimeEpochMs = endTime.toEpochMilli(),
        distanceMeters = distanceMeters,
        durationSeconds = durationSeconds,
        avgPaceSeconds = avgPaceSeconds,
        avgHeartRate = avgHeartRate,
        maxHeartRate = maxHeartRate,
        totalCalories = totalCalories,
        elevationGainedMeters = elevationGainedMeters,
        isSeed = isSeed,
        hrHistogram = hrHistogram
    )

    private fun UserSettingsEntity.toDomainModel() = UserSettings(
        refDistanceMeters = refDistanceMeters,
        refDurationSeconds = refDurationSeconds,
        refAvgHeartRate = refAvgHeartRate,
        vo2Max = vo2Max,
        targetRaceDistance = RaceDistance.values().find { it.meters == targetDistanceMeters } ?: RaceDistance.TEN_K,
        trainingDaysPerWeek = trainingDaysPerWeek,
        z1Max = z1Max,
        z2Min = z2Min,
        z2Max = z2Max,
        z3Min = z3Min,
        z3Max = z3Max,
        z4Min = z4Min,
        z4Max = z4Max,
        maxHr = maxHr
    )

    private fun UserSettings.toEntity() = UserSettingsEntity(
        id = 1,
        refDistanceMeters = refDistanceMeters,
        refDurationSeconds = refDurationSeconds,
        refAvgHeartRate = refAvgHeartRate,
        vo2Max = vo2Max,
        targetDistanceMeters = targetRaceDistance.meters,
        trainingDaysPerWeek = trainingDaysPerWeek,
        z1Max = z1Max,
        z2Min = z2Min,
        z2Max = z2Max,
        z3Min = z3Min,
        z3Max = z3Max,
        z4Min = z4Min,
        z4Max = z4Max,
        maxHr = maxHr
    )

    private fun com.example.data.local.RaceEventEntity.toDomainModel() = com.example.data.model.RaceEvent(
        id = id,
        raceName = raceName,
        raceDate = LocalDate.ofEpochDay(raceDateEpochDay),
        distance = RaceDistance.values().find { it.meters == distanceMeters } ?: RaceDistance.TEN_K,
        targetDurationSeconds = targetDurationSeconds,
        targetPaceSeconds = targetPaceSeconds,
        location = location
    )

    private fun com.example.data.model.RaceEvent.toEntity() = com.example.data.local.RaceEventEntity(
        id = 1,
        raceName = raceName,
        raceDateEpochDay = raceDate.toEpochDay(),
        distanceMeters = distance.meters,
        targetDurationSeconds = targetDurationSeconds,
        targetPaceSeconds = targetPaceSeconds,
        location = location
    )
}
