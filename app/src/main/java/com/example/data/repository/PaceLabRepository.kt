package com.example.data.repository

import android.content.Context
import com.example.data.health.HealthConnectManager
import com.example.data.health.HealthConnectStatus
import com.example.data.health.SeedDataGenerator
import com.example.data.local.CachedSessionEntity
import com.example.data.local.PaceLabDao
import com.example.data.local.PaceLabDatabase
import com.example.data.local.PlanStateEntity
import com.example.data.local.UserSettingsEntity
import com.example.data.model.HeartRatePoint
import com.example.data.model.RaceDistance
import com.example.data.model.RunSession
import com.example.data.model.RunSplit
import com.example.data.model.UserSettings
import com.example.data.model.WeeklySummary
import com.example.util.HrZoneCalculator
import com.example.util.HrZones
import com.example.util.PaceUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

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

    suspend fun hasHealthPermissions(): Boolean {
        return healthConnectRepository.hasAllPermissions()
    }

    suspend fun syncData(forceSeedDemo: Boolean = false): Boolean {
        val result = healthConnectRepository.syncAllRunningData(forceSeedDemo)
        return result.isSuccess && (result.getOrNull() ?: 0) > 0
    }

    suspend fun seedDemoData() {
        withContext(Dispatchers.IO) {
            val demoSessions = SeedDataGenerator.generateDemoSessions()
            dao.insertSessions(demoSessions.map { it.toEntity() })
        }
    }

    suspend fun clearSessions() {
        withContext(Dispatchers.IO) {
            dao.clearAllSessions()
        }
    }

    suspend fun getSessionDetails(session: RunSession): Pair<List<RunSplit>, List<HeartRatePoint>> {
        return healthConnectRepository.getSessionSplitsAndHr(session)
    }

    suspend fun deleteSession(sessionId: String) {
        withContext(Dispatchers.IO) {
            dao.deleteSession(sessionId)
        }
    }

    suspend fun deleteSessions(sessionIds: List<String>) {
        withContext(Dispatchers.IO) {
            dao.deleteSessions(sessionIds)
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

    /**
     * Computes the weekly summary for a given Monday-Sunday week interval.
     */
    fun computeWeeklySummary(
        allSessions: List<RunSession>,
        weekStartMonday: LocalDate,
        userSettings: UserSettings
    ): WeeklySummary {
        val weekEndSunday = weekStartMonday.plusDays(6)
        val zone = ZoneId.systemDefault()

        val weekStartInstant = weekStartMonday.atStartOfDay(zone).toInstant()
        val weekEndInstant = weekEndSunday.plusDays(1).atStartOfDay(zone).toInstant()

        val weekSessions = allSessions.filter {
            it.startTime >= weekStartInstant && it.startTime < weekEndInstant
        }

        val totalDistMeters = weekSessions.sumOf { it.distanceMeters }
        val totalDurationSec = weekSessions.sumOf { it.durationSeconds }
        val runCount = weekSessions.size

        val avgPace = if (totalDistMeters > 0) {
            PaceUtils.calculatePaceSeconds(totalDistMeters, totalDurationSec)
        } else 0

        val hrList = weekSessions.mapNotNull { it.avgHeartRate }
        val avgHr = if (hrList.isNotEmpty()) hrList.average().toInt() else null

        // Daily distance breakdown (Monday to Sunday)
        val dailyMap = mutableMapOf<DayOfWeek, Double>()
        DayOfWeek.values().forEach { dailyMap[it] = 0.0 }
        weekSessions.forEach { session ->
            val day = session.startTime.atZone(zone).dayOfWeek
            dailyMap[day] = (dailyMap[day] ?: 0.0) + (session.distanceMeters / 1000.0)
        }

        // Compare with previous week
        val prevWeekStart = weekStartMonday.minusWeeks(1)
        val prevWeekEndInstant = weekStartInstant
        val prevWeekStartInstant = prevWeekStart.atStartOfDay(zone).toInstant()
        val prevSessions = allSessions.filter {
            it.startTime >= prevWeekStartInstant && it.startTime < prevWeekEndInstant
        }
        val prevDist = prevSessions.sumOf { it.distanceMeters }
        val diffPercent = if (prevDist > 0) {
            ((totalDistMeters - prevDist) / prevDist) * 100.0
        } else null

        // Aggregate HR zone distribution
        val zones = HrZones(
            z1Max = userSettings.z1Max,
            z2Min = userSettings.z2Min,
            z2Max = userSettings.z2Max,
            z3Min = userSettings.z3Min,
            z3Max = userSettings.z3Max,
            z4Min = userSettings.z4Min,
            z4Max = userSettings.z4Max,
            maxHr = userSettings.maxHr
        )

        // Synthesize zone distribution across week runs
        var totalZ1 = 0L
        var totalZ2 = 0L
        var totalZ3 = 0L
        var totalZ4 = 0L
        var totalZ5 = 0L

        weekSessions.forEach { session ->
            val dur = session.durationSeconds
            val hr = session.avgHeartRate ?: 140
            when (HrZoneCalculator.determineZone(hr, zones)) {
                1 -> totalZ1 += dur
                2 -> totalZ2 += dur
                3 -> totalZ3 += dur
                4 -> totalZ4 += dur
                5 -> totalZ5 += dur
            }
        }

        val hrDistribution = com.example.util.HrZoneDistribution(
            z1Seconds = totalZ1,
            z2Seconds = totalZ2,
            z3Seconds = totalZ3,
            z4Seconds = totalZ4,
            z5Seconds = totalZ5
        )

        return WeeklySummary(
            weekStartMonday = weekStartMonday,
            weekEndSunday = weekEndSunday,
            totalDistanceMeters = totalDistMeters,
            runCount = runCount,
            totalDurationSeconds = totalDurationSec,
            avgPaceSeconds = avgPace,
            avgHeartRate = avgHr,
            dailyDistanceKm = dailyMap,
            hrDistribution = hrDistribution,
            comparisonPercentVsPrevious = diffPercent
        )
    }

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
        isSeed = isSeed
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
        isSeed = isSeed
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
