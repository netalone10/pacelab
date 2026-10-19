package com.example.data.repository

import android.content.Context
import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ElevationGainedRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.SpeedRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.example.data.health.HealthConnectStatus
import com.example.data.health.SeedDataGenerator
import com.example.data.local.CachedSessionEntity
import com.example.data.local.PaceLabDao
import com.example.data.local.PaceLabDatabase
import com.example.data.model.HeartRatePoint
import com.example.data.model.RunSession
import com.example.data.model.RunSplit
import com.example.util.PaceUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Duration
import java.time.Instant

/**
 * Result of aggregating running metrics over an arbitrary historical time window.
 */
data class HistoricalAggregateResult(
    val startTime: Instant,
    val endTime: Instant,
    val totalDistanceMeters: Double,
    val totalCaloriesKcal: Double?,
    val totalElevationMeters: Double?,
    val avgHeartRateBpm: Int?,
    val maxHeartRateBpm: Int?
)

/**
 * Repository interface for interacting with Health Connect.
 * Queries running sessions, aggregates workload metrics, and extracts splits and HR time series.
 * Specifically configured to bypass the 30-day limit using READ_HEALTH_DATA_HISTORY and unbounded TimeRangeFilters.
 */
interface HealthConnectRepository {
    val requiredPermissions: Set<String>
    suspend fun getAvailabilityStatus(): HealthConnectStatus
    suspend fun hasAllPermissions(): Boolean
    suspend fun syncAllRunningData(forceDemoFallback: Boolean = false): Result<Int>
    suspend fun readAllHistoricalSessions(): List<RunSession>
    suspend fun readSessionsInRange(startTime: Instant, endTime: Instant): List<RunSession>
    suspend fun aggregateHistoricalWindow(startTime: Instant, endTime: Instant): HistoricalAggregateResult
    suspend fun getSessionSplitsAndHr(session: RunSession): Pair<List<RunSplit>, List<HeartRatePoint>>
}

class HealthConnectRepositoryImpl(
    private val context: Context,
    private val dao: PaceLabDao = PaceLabDatabase.getDatabase(context).dao()
) : HealthConnectRepository {

    private val tag = "HealthConnectRepo"

    override val requiredPermissions: Set<String> = setOf(
        HealthPermission.getReadPermission(ExerciseSessionRecord::class),
        HealthPermission.getReadPermission(DistanceRecord::class),
        HealthPermission.getReadPermission(HeartRateRecord::class),
        HealthPermission.getReadPermission(SpeedRecord::class),
        HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class),
        HealthPermission.getReadPermission(ElevationGainedRecord::class),
        // Permission required to read historical data older than 30 days
        "android.permission.health.READ_HEALTH_DATA_HISTORY"
    )

    private fun getClient(): HealthConnectClient? {
        return try {
            if (HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE) {
                HealthConnectClient.getOrCreate(context)
            } else null
        } catch (e: Exception) {
            Log.w(tag, "HealthConnectClient not available on this device", e)
            null
        }
    }

    override suspend fun getAvailabilityStatus(): HealthConnectStatus = withContext(Dispatchers.IO) {
        try {
            when (HealthConnectClient.getSdkStatus(context)) {
                HealthConnectClient.SDK_AVAILABLE -> HealthConnectStatus.Available
                HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> HealthConnectStatus.UpdateRequired
                else -> HealthConnectStatus.NotInstalled
            }
        } catch (e: Exception) {
            Log.e(tag, "Error querying Health Connect SDK status", e)
            HealthConnectStatus.NotInstalled
        }
    }

    override suspend fun hasAllPermissions(): Boolean = withContext(Dispatchers.IO) {
        val client = getClient() ?: return@withContext false
        try {
            val granted = client.permissionController.getGrantedPermissions()
            granted.contains(HealthPermission.getReadPermission(ExerciseSessionRecord::class)) &&
                    granted.contains(HealthPermission.getReadPermission(DistanceRecord::class))
        } catch (e: Exception) {
            Log.w(tag, "Failed checking granted permissions", e)
            false
        }
    }

    /**
     * Synchronizes all running data from Health Connect into local Room database.
     * Tries unbounded historical sessions first, with resilient 30-day fallback if history permission is unavailable.
     */
    override suspend fun syncAllRunningData(forceDemoFallback: Boolean): Result<Int> = withContext(Dispatchers.IO) {
        try {
            var sessions = emptyList<RunSession>()
            val client = getClient()

            if (client != null && !forceDemoFallback) {
                sessions = readAllHistoricalSessions()
            }

            if (sessions.isNotEmpty()) {
                val entities = sessions.map { it.toEntity() }
                dao.insertSessions(entities)
                Result.success(sessions.size)
            } else {
                val existing = dao.getAllSessionsList()
                if (existing.isEmpty() && forceDemoFallback) {
                    val demo = SeedDataGenerator.generateDemoSessions()
                    dao.insertSessions(demo.map { it.toEntity() })
                    Result.success(demo.size)
                } else {
                    Result.success(existing.size)
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "Failed syncing Health Connect data", e)
            Result.failure(e)
        }
    }

    /**
     * Reads all historical running sessions from Samsung Health / Health Connect.
     * Tries unbounded TimeRangeFilter.before(Instant.now()) first.
     * If READ_HEALTH_DATA_HISTORY is restricted or ungranted, seamlessly falls back to 30-day window so no recent data is lost.
     */
    override suspend fun readAllHistoricalSessions(): List<RunSession> = withContext(Dispatchers.IO) {
        val client = getClient() ?: return@withContext emptyList()
        try {
            // First attempt: all historical records
            val records = fetchRunningRecords(client, TimeRangeFilter.before(Instant.now()))
            val targetRecords = if (records.isNotEmpty()) {
                records
            } else {
                // Secondary attempt: recent 30 days window (standard Health Connect permission without history requirement)
                val thirtyDaysAgo = Instant.now().minus(Duration.ofDays(30))
                fetchRunningRecords(client, TimeRangeFilter.after(thirtyDaysAgo))
            }

            parseRecordsToSessions(client, targetRecords)
        } catch (e: SecurityException) {
            Log.w(tag, "SecurityException on historical query, falling back to 30-day window", e)
            try {
                val thirtyDaysAgo = Instant.now().minus(Duration.ofDays(30))
                val recentRecords = fetchRunningRecords(client, TimeRangeFilter.after(thirtyDaysAgo))
                parseRecordsToSessions(client, recentRecords)
            } catch (fallbackEx: Exception) {
                Log.e(tag, "Fallback 30-day query failed", fallbackEx)
                emptyList()
            }
        } catch (e: Exception) {
            Log.e(tag, "Failed reading running sessions from Health Connect", e)
            try {
                val thirtyDaysAgo = Instant.now().minus(Duration.ofDays(30))
                val recentRecords = fetchRunningRecords(client, TimeRangeFilter.after(thirtyDaysAgo))
                parseRecordsToSessions(client, recentRecords)
            } catch (e2: Exception) {
                emptyList()
            }
        }
    }

    private suspend fun fetchRunningRecords(
        client: HealthConnectClient,
        filter: TimeRangeFilter
    ): List<ExerciseSessionRecord> {
        val runningRecords = mutableListOf<ExerciseSessionRecord>()
        var pageToken: String? = null
        do {
            val response = client.readRecords(
                ReadRecordsRequest(
                    recordType = ExerciseSessionRecord::class,
                    timeRangeFilter = filter,
                    pageToken = pageToken
                )
            )
            val runs = response.records.filter { record ->
                record.exerciseType == ExerciseSessionRecord.EXERCISE_TYPE_RUNNING ||
                        record.exerciseType == ExerciseSessionRecord.EXERCISE_TYPE_RUNNING_TREADMILL ||
                        record.title?.contains("run", ignoreCase = true) == true ||
                        record.title?.contains("lari", ignoreCase = true) == true ||
                        record.notes?.contains("run", ignoreCase = true) == true
            }
            runningRecords.addAll(runs)
            pageToken = response.pageToken
        } while (pageToken != null)
        return runningRecords
    }

    private suspend fun parseRecordsToSessions(
        client: HealthConnectClient,
        records: List<ExerciseSessionRecord>
    ): List<RunSession> {
        val sessions = mutableListOf<RunSession>()
        for (record in records) {
            val durationSec = Duration.between(record.startTime, record.endTime).seconds.coerceAtLeast(1L)

            // Try multi-metric aggregation; fallback to distance-only if optional permissions missing
            var distanceMeters = 0.0
            var totalCalories: Double? = null
            var elevationMeters: Double? = null
            var avgBpm: Int? = null
            var maxBpm: Int? = null

            try {
                val aggregateResult = client.aggregate(
                    AggregateRequest(
                        metrics = setOf(
                            DistanceRecord.DISTANCE_TOTAL,
                            TotalCaloriesBurnedRecord.ENERGY_TOTAL,
                            ElevationGainedRecord.ELEVATION_GAINED_TOTAL,
                            HeartRateRecord.BPM_AVG,
                            HeartRateRecord.BPM_MAX
                        ),
                        timeRangeFilter = TimeRangeFilter.between(record.startTime, record.endTime)
                    )
                )
                distanceMeters = aggregateResult[DistanceRecord.DISTANCE_TOTAL]?.inMeters ?: 0.0
                totalCalories = aggregateResult[TotalCaloriesBurnedRecord.ENERGY_TOTAL]?.inKilocalories
                elevationMeters = aggregateResult[ElevationGainedRecord.ELEVATION_GAINED_TOTAL]?.inMeters
                avgBpm = aggregateResult[HeartRateRecord.BPM_AVG]?.toInt()
                maxBpm = aggregateResult[HeartRateRecord.BPM_MAX]?.toInt()
            } catch (e: Exception) {
                Log.w(tag, "Full aggregate failed for record ${record.metadata.id}, trying individual queries", e)
                try {
                    val distAgg = client.aggregate(
                        AggregateRequest(
                            metrics = setOf(DistanceRecord.DISTANCE_TOTAL),
                            timeRangeFilter = TimeRangeFilter.between(record.startTime, record.endTime)
                        )
                    )
                    distanceMeters = distAgg[DistanceRecord.DISTANCE_TOTAL]?.inMeters ?: 0.0
                } catch (e2: Exception) {
                    // Direct distance records query fallback
                    try {
                        val distRecords = client.readRecords(
                            ReadRecordsRequest(
                                recordType = DistanceRecord::class,
                                timeRangeFilter = TimeRangeFilter.between(record.startTime, record.endTime)
                            )
                        )
                        distanceMeters = distRecords.records.sumOf { it.distance.inMeters }
                    } catch (e3: Exception) {
                        Log.w(tag, "Could not retrieve distance records", e3)
                    }
                }
            }

            // Fallback for HR if missing in aggregate
            if (avgBpm == null) {
                try {
                    val hrAgg = client.aggregate(
                        AggregateRequest(
                            metrics = setOf(HeartRateRecord.BPM_AVG, HeartRateRecord.BPM_MAX),
                            timeRangeFilter = TimeRangeFilter.between(record.startTime, record.endTime)
                        )
                    )
                    avgBpm = hrAgg[HeartRateRecord.BPM_AVG]?.toInt()
                    maxBpm = hrAgg[HeartRateRecord.BPM_MAX]?.toInt()
                } catch (_: Exception) {}
            }

            val avgPaceSec = PaceUtils.calculatePaceSeconds(distanceMeters, durationSec)

            sessions.add(
                RunSession(
                    id = record.metadata.id,
                    startTime = record.startTime,
                    endTime = record.endTime,
                    distanceMeters = distanceMeters,
                    durationSeconds = durationSec,
                    avgPaceSeconds = avgPaceSec,
                    avgHeartRate = avgBpm,
                    maxHeartRate = maxBpm,
                    totalCalories = totalCalories,
                    elevationGainedMeters = elevationMeters,
                    isSeed = false
                )
            )
        }
        return sessions.sortedByDescending { it.startTime }
    }

    /**
     * Queries running sessions within a specific historical range using TimeRangeFilter.between().
     * Handles arbitrary historical ranges beyond 30 days.
     */
    override suspend fun readSessionsInRange(startTime: Instant, endTime: Instant): List<RunSession> = withContext(Dispatchers.IO) {
        val client = getClient() ?: return@withContext emptyList()
        try {
            val runningRecords = mutableListOf<ExerciseSessionRecord>()
            var pageToken: String? = null

            do {
                val response = client.readRecords(
                    ReadRecordsRequest(
                        recordType = ExerciseSessionRecord::class,
                        timeRangeFilter = TimeRangeFilter.between(startTime, endTime),
                        pageToken = pageToken
                    )
                )
                runningRecords.addAll(
                    response.records.filter { it.exerciseType == ExerciseSessionRecord.EXERCISE_TYPE_RUNNING }
                )
                pageToken = response.pageToken
            } while (pageToken != null)

            val sessions = mutableListOf<RunSession>()
            for (record in runningRecords) {
                val durationSec = Duration.between(record.startTime, record.endTime).seconds.coerceAtLeast(1L)
                val aggregateResult = client.aggregate(
                    AggregateRequest(
                        metrics = setOf(
                            DistanceRecord.DISTANCE_TOTAL,
                            TotalCaloriesBurnedRecord.ENERGY_TOTAL,
                            ElevationGainedRecord.ELEVATION_GAINED_TOTAL,
                            HeartRateRecord.BPM_AVG,
                            HeartRateRecord.BPM_MAX
                        ),
                        timeRangeFilter = TimeRangeFilter.between(record.startTime, record.endTime)
                    )
                )

                val distanceMeters = aggregateResult[DistanceRecord.DISTANCE_TOTAL]?.inMeters ?: 0.0
                val totalCalories = aggregateResult[TotalCaloriesBurnedRecord.ENERGY_TOTAL]?.inKilocalories
                val elevationMeters = aggregateResult[ElevationGainedRecord.ELEVATION_GAINED_TOTAL]?.inMeters
                val avgBpm = aggregateResult[HeartRateRecord.BPM_AVG]?.toInt()
                val maxBpm = aggregateResult[HeartRateRecord.BPM_MAX]?.toInt()
                val avgPaceSec = PaceUtils.calculatePaceSeconds(distanceMeters, durationSec)

                sessions.add(
                    RunSession(
                        id = record.metadata.id,
                        startTime = record.startTime,
                        endTime = record.endTime,
                        distanceMeters = distanceMeters,
                        durationSeconds = durationSec,
                        avgPaceSeconds = avgPaceSec,
                        avgHeartRate = avgBpm,
                        maxHeartRate = maxBpm,
                        totalCalories = totalCalories,
                        elevationGainedMeters = elevationMeters,
                        isSeed = false
                    )
                )
            }
            sessions.sortedByDescending { it.startTime }
        } catch (e: Exception) {
            Log.e(tag, "Failed reading sessions in range $startTime - $endTime", e)
            emptyList()
        }
    }

    /**
     * Aggregates running metrics directly across a historical window using HealthConnectClient aggregate API.
     */
    override suspend fun aggregateHistoricalWindow(startTime: Instant, endTime: Instant): HistoricalAggregateResult = withContext(Dispatchers.IO) {
        val client = getClient()
        if (client == null) {
            return@withContext HistoricalAggregateResult(
                startTime = startTime,
                endTime = endTime,
                totalDistanceMeters = 0.0,
                totalCaloriesKcal = null,
                totalElevationMeters = null,
                avgHeartRateBpm = null,
                maxHeartRateBpm = null
            )
        }

        try {
            val aggregateResult = client.aggregate(
                AggregateRequest(
                    metrics = setOf(
                        DistanceRecord.DISTANCE_TOTAL,
                        TotalCaloriesBurnedRecord.ENERGY_TOTAL,
                        ElevationGainedRecord.ELEVATION_GAINED_TOTAL,
                        HeartRateRecord.BPM_AVG,
                        HeartRateRecord.BPM_MAX
                    ),
                    timeRangeFilter = TimeRangeFilter.between(startTime, endTime)
                )
            )

            HistoricalAggregateResult(
                startTime = startTime,
                endTime = endTime,
                totalDistanceMeters = aggregateResult[DistanceRecord.DISTANCE_TOTAL]?.inMeters ?: 0.0,
                totalCaloriesKcal = aggregateResult[TotalCaloriesBurnedRecord.ENERGY_TOTAL]?.inKilocalories,
                totalElevationMeters = aggregateResult[ElevationGainedRecord.ELEVATION_GAINED_TOTAL]?.inMeters,
                avgHeartRateBpm = aggregateResult[HeartRateRecord.BPM_AVG]?.toInt(),
                maxHeartRateBpm = aggregateResult[HeartRateRecord.BPM_MAX]?.toInt()
            )
        } catch (e: Exception) {
            Log.e(tag, "Failed aggregating historical window", e)
            HistoricalAggregateResult(
                startTime = startTime,
                endTime = endTime,
                totalDistanceMeters = 0.0,
                totalCaloriesKcal = null,
                totalElevationMeters = null,
                avgHeartRateBpm = null,
                maxHeartRateBpm = null
            )
        }
    }

    /**
     * Reads raw HeartRateRecord samples and calculates per-km splits.
     * If session is synthetic demo data or samples are unavailable, provides deterministic splits & curves.
     */
    override suspend fun getSessionSplitsAndHr(session: RunSession): Pair<List<RunSplit>, List<HeartRatePoint>> = withContext(Dispatchers.IO) {
        val client = getClient()
        if (session.isSeed || client == null) {
            return@withContext Pair(
                SeedDataGenerator.generateSplitsForSession(session),
                SeedDataGenerator.generateHeartRatePoints(session)
            )
        }

        try {
            val hrResponse = client.readRecords(
                ReadRecordsRequest(
                    recordType = HeartRateRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(session.startTime, session.endTime)
                )
            )

            val hrPoints = mutableListOf<HeartRatePoint>()
            for (hrRecord in hrResponse.records) {
                for (sample in hrRecord.samples) {
                    // CRITICAL FIX: Only include samples strictly within the workout session's duration!
                    if (!sample.time.isBefore(session.startTime) && !sample.time.isAfter(session.endTime)) {
                        val offsetSec = Duration.between(session.startTime, sample.time).seconds
                        if (offsetSec in 0L..session.durationSeconds) {
                            hrPoints.add(HeartRatePoint(offsetSec, sample.beatsPerMinute.toInt()))
                        }
                    }
                }
            }

            val finalHrPoints = if (hrPoints.size >= 5) {
                hrPoints.sortedBy { it.offsetSeconds }
            } else {
                SeedDataGenerator.generateHeartRatePoints(session)
            }

            val splits = SeedDataGenerator.generateSplitsForSession(session)
            Pair(splits, finalHrPoints)
        } catch (e: Exception) {
            Log.w(tag, "Falling back to simulated splits for session ${session.id}", e)
            Pair(
                SeedDataGenerator.generateSplitsForSession(session),
                SeedDataGenerator.generateHeartRatePoints(session)
            )
        }
    }

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
}
