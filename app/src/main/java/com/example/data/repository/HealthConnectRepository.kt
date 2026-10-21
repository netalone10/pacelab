package com.example.data.repository

import android.content.Context
import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.aggregate.AggregateMetric
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ElevationGainedRecord
import androidx.health.connect.client.records.ExerciseRoute
import androidx.health.connect.client.records.ExerciseRouteResult
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.SpeedRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.example.data.health.HealthConnectStatus
import com.example.data.health.HealthPermissions
import com.example.data.health.SeedDataGenerator
import com.example.data.local.CachedSessionEntity
import com.example.data.local.PaceLabDao
import com.example.data.local.PaceLabDatabase
import com.example.data.model.HeartRatePoint
import com.example.data.model.RunSession
import com.example.data.model.RunSplit
import com.example.util.DistanceInterval
import com.example.util.DistancePoint
import com.example.util.HrHistogram
import com.example.util.PaceUtils
import com.example.util.RoutePoint
import com.example.util.SplitCalculator
import com.example.util.TrackMath
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Duration
import java.time.Instant
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.roundToInt

/** Thrown when the minimum read permissions (exercise + distance) are not granted. */
class HealthPermissionMissingException : Exception("Permission to read running data has not been granted")

data class SyncSummary(val fetched: Int, val added: Int)

/** Where the per-km splits came from, shown in the UI so a missing split is easy to debug. */
enum class SplitSource(val label: String) {
    NONE(""),
    DISTANCE("Distance records"),
    ROUTE("GPS route"),
    SPEED("Speed records")
}

data class SessionDetails(
    val splits: List<RunSplit>,
    val hrPoints: List<HeartRatePoint>,
    val splitSource: SplitSource = SplitSource.NONE,
    /** The GPS route exists but the user has not yet allowed this app to read it for this run. */
    val routeConsentRequired: Boolean = false,
    /** Climb computed from the GPS route altitude, used when no ElevationGainedRecord exists. */
    val routeElevationGainMeters: Double? = null
)

/**
 * Reads running sessions from Health Connect into the local Room cache.
 * Splits and heart rate come only from real records; nothing is simulated for real runs.
 */
interface HealthConnectRepository {
    val requiredPermissions: Set<String>
    suspend fun getAvailabilityStatus(): HealthConnectStatus
    suspend fun getGrantedPermissions(): Set<String>
    suspend fun syncAllRunningData(): Result<SyncSummary>
    /** [grantedRoute] is the route the user just allowed through ExerciseRouteRequestContract. */
    suspend fun getSessionSplitsAndHr(session: RunSession, grantedRoute: ExerciseRoute? = null): SessionDetails
}

class HealthConnectRepositoryImpl(
    private val context: Context,
    private val dao: PaceLabDao = PaceLabDatabase.getDatabase(context).dao()
) : HealthConnectRepository {

    private val tag = "HealthConnectRepo"

    override val requiredPermissions: Set<String> = HealthPermissions.requestable

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

    override suspend fun getGrantedPermissions(): Set<String> = withContext(Dispatchers.IO) {
        val client = getClient() ?: return@withContext emptySet()
        try {
            client.permissionController.getGrantedPermissions()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(tag, "Failed checking granted permissions", e)
            emptySet()
        }
    }

    override suspend fun syncAllRunningData(): Result<SyncSummary> = withContext(Dispatchers.IO) {
        try {
            val client = getClient()
                ?: return@withContext Result.failure(IllegalStateException("Health Connect is not available on this device"))

            val granted = client.permissionController.getGrantedPermissions()
            if (!granted.containsAll(HealthPermissions.core)) {
                return@withContext Result.failure(HealthPermissionMissingException())
            }
            // Without the history permission Health Connect only exposes a limited window, so a
            // run missing from the result does not prove it was deleted at the source.
            val isComplete = HealthPermissions.HISTORY in granted

            val records = fetchRunningRecords(client, TimeRangeFilter.before(Instant.now()))
            val ignored = dao.getIgnoredIds().toSet()
            val knownRealIds = dao.getRealSessionIds().toSet()
            val syncedIds = dao.getSyncedSessionIds().toSet()
            val recentCutoff = Instant.now().minus(Duration.ofDays(3))

            val candidates = records.filter { it.metadata.id !in ignored }
            // Only new runs and runs that just finished (HR/distance may still be arriving).
            val toProcess = candidates.filter {
                it.metadata.id !in syncedIds || it.endTime.isAfter(recentCutoff)
            }

            val sessions = ArrayList<RunSession>(toProcess.size)
            for (record in toProcess) {
                val session = buildSession(client, record, granted)
                if (session != null) sessions.add(session)
            }
            if (sessions.isNotEmpty()) {
                dao.insertSessions(sessions.map { it.toEntity() })
            }

            if (isComplete) {
                val fetchedIds = candidates.map { it.metadata.id }.toSet()
                val stale = knownRealIds.filter { it !in fetchedIds }
                stale.chunked(500).forEach { dao.deleteSessions(it) }
            }

            if (dao.countRealSessions() > 0) {
                dao.deleteSeedSessions()
            }

            val added = sessions.count { it.id !in knownRealIds }
            Result.success(SyncSummary(fetched = candidates.size, added = added))
        } catch (e: CancellationException) {
            throw e
        } catch (e: SecurityException) {
            Log.w(tag, "Health Connect permission error", e)
            Result.failure(HealthPermissionMissingException())
        } catch (e: Exception) {
            Log.e(tag, "Failed syncing Health Connect data", e)
            Result.failure(e)
        }
    }

    private suspend fun fetchRunningRecords(
        client: HealthConnectClient,
        filter: TimeRangeFilter
    ): List<ExerciseSessionRecord> {
        val result = mutableListOf<ExerciseSessionRecord>()
        var pageToken: String? = null
        do {
            val response = client.readRecords(
                ReadRecordsRequest(
                    recordType = ExerciseSessionRecord::class,
                    timeRangeFilter = filter,
                    pageToken = pageToken
                )
            )
            result.addAll(response.records.filter {
                it.exerciseType == ExerciseSessionRecord.EXERCISE_TYPE_RUNNING ||
                    it.exerciseType == ExerciseSessionRecord.EXERCISE_TYPE_RUNNING_TREADMILL
            })
            pageToken = response.pageToken?.takeIf { it.isNotEmpty() }
        } while (pageToken != null)
        return result
    }

    private suspend fun readHrSamples(
        client: HealthConnectClient,
        start: Instant,
        end: Instant
    ): List<Pair<Long, Int>> {
        val samples = mutableListOf<Pair<Long, Int>>()
        var pageToken: String? = null
        do {
            val response = client.readRecords(
                ReadRecordsRequest(
                    recordType = HeartRateRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(start, end),
                    pageToken = pageToken
                )
            )
            for (record in response.records) {
                for (sample in record.samples) {
                    if (sample.time.isBefore(start) || sample.time.isAfter(end)) continue
                    val offset = Duration.between(start, sample.time).seconds
                    samples.add(offset to sample.beatsPerMinute.toInt())
                }
            }
            pageToken = response.pageToken?.takeIf { it.isNotEmpty() }
        } while (pageToken != null)
        // Two apps can write the same second twice; keep one sample per second.
        return samples.distinctBy { it.first }.sortedBy { it.first }
    }

    private suspend fun buildSession(
        client: HealthConnectClient,
        record: ExerciseSessionRecord,
        granted: Set<String>
    ): RunSession? {
        val durationSec = Duration.between(record.startTime, record.endTime).seconds.coerceAtLeast(1L)
        val range = TimeRangeFilter.between(record.startTime, record.endTime)

        val metrics = mutableSetOf<AggregateMetric<Any>>(DistanceRecord.DISTANCE_TOTAL)
        if (HealthPermissions.calories in granted) metrics.add(TotalCaloriesBurnedRecord.ENERGY_TOTAL)
        if (HealthPermissions.elevation in granted) metrics.add(ElevationGainedRecord.ELEVATION_GAINED_TOTAL)
        val hasHr = HealthPermissions.heartRate in granted
        if (hasHr) {
            metrics.add(HeartRateRecord.BPM_AVG)
            metrics.add(HeartRateRecord.BPM_MAX)
        }

        var distanceMeters = 0.0
        var calories: Double? = null
        var elevation: Double? = null
        var avgBpm: Int? = null
        var maxBpm: Int? = null

        try {
            val agg = client.aggregate(AggregateRequest(metrics = metrics, timeRangeFilter = range))
            distanceMeters = agg[DistanceRecord.DISTANCE_TOTAL]?.inMeters ?: 0.0
            calories = agg[TotalCaloriesBurnedRecord.ENERGY_TOTAL]?.inKilocalories
            elevation = agg[ElevationGainedRecord.ELEVATION_GAINED_TOTAL]?.inMeters
            avgBpm = agg[HeartRateRecord.BPM_AVG]?.toInt()
            maxBpm = agg[HeartRateRecord.BPM_MAX]?.toInt()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(tag, "Aggregate failed for ${record.metadata.id}, summing distance records", e)
            distanceMeters = try {
                client.readRecords(ReadRecordsRequest(DistanceRecord::class, range))
                    .records.sumOf { it.distance.inMeters }
            } catch (e2: CancellationException) {
                throw e2
            } catch (e2: Exception) {
                0.0
            }
        }

        // A run without distance is useless for pace and plan matching.
        if (distanceMeters <= 0.0) return null

        var histogramText = ""
        if (hasHr) {
            try {
                val samples = readHrSamples(client, record.startTime, record.endTime)
                if (samples.isNotEmpty()) {
                    histogramText = HrHistogram.encode(HrHistogram.build(samples))
                    if (avgBpm == null) avgBpm = samples.map { it.second }.average().roundToInt()
                    if (maxBpm == null) maxBpm = samples.maxOf { it.second }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(tag, "Could not read HR samples for ${record.metadata.id}", e)
            }
        }

        return RunSession(
            id = record.metadata.id,
            startTime = record.startTime,
            endTime = record.endTime,
            distanceMeters = distanceMeters,
            durationSeconds = durationSec,
            avgPaceSeconds = PaceUtils.calculatePaceSeconds(distanceMeters, durationSec),
            avgHeartRate = avgBpm,
            maxHeartRate = maxBpm,
            totalCalories = calories,
            elevationGainedMeters = elevation,
            isSeed = false,
            hrHistogram = histogramText
        )
    }

    /**
     * Real splits come from the first usable source: DistanceRecord intervals, then the GPS route,
     * then SpeedRecord samples. Missing data yields empty lists, never simulated values.
     * Demo sessions stay synthetic.
     */
    override suspend fun getSessionSplitsAndHr(
        session: RunSession,
        grantedRoute: ExerciseRoute?
    ): SessionDetails = withContext(Dispatchers.IO) {
        if (session.isSeed) {
            return@withContext SessionDetails(
                SeedDataGenerator.generateSplitsForSession(session),
                SeedDataGenerator.generateHeartRatePoints(session)
            )
        }
        val client = getClient() ?: return@withContext SessionDetails(emptyList(), emptyList())

        val hrSamples: List<Pair<Long, Int>> = try {
            readHrSamples(client, session.startTime, session.endTime)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(tag, "No HR samples for ${session.id}", e)
            emptyList()
        }

        var splits = safely("distance splits") { readDistanceSplits(client, session, hrSamples) } ?: emptyList()
        var source = if (splits.isNotEmpty()) SplitSource.DISTANCE else SplitSource.NONE

        // The route is needed for GPS splits, and for elevation when no elevation record exists.
        var routePoints: List<RoutePoint> = emptyList()
        var consentRequired = false
        if (splits.isEmpty() || session.elevationGainedMeters == null) {
            val route = grantedRoute ?: safely("route") {
                when (val result = client.readRecord(ExerciseSessionRecord::class, session.id)
                    .record.exerciseRouteResult) {
                    is ExerciseRouteResult.Data -> result.exerciseRoute
                    is ExerciseRouteResult.ConsentRequired -> { consentRequired = true; null }
                    else -> null
                }
            }
            if (route != null) {
                routePoints = route.route.map {
                    RoutePoint(
                        sec = Duration.between(session.startTime, it.time).toMillis() / 1000.0,
                        lat = it.latitude,
                        lon = it.longitude,
                        altitude = it.altitude?.inMeters,
                        accuracyMeters = it.horizontalAccuracy?.inMeters
                    )
                }
            }
        }

        if (splits.isEmpty() && routePoints.isNotEmpty()) {
            val curve = TrackMath.scaleTo(TrackMath.cumulativeFromRoute(routePoints), session.distanceMeters)
            if (curve != null) {
                splits = SplitCalculator.fromCumulative(curve, hrSamples)
                if (splits.isNotEmpty()) source = SplitSource.ROUTE
            }
        }

        if (splits.isEmpty()) {
            val curve = safely("speed splits") { readSpeedCurve(client, session) }
            if (curve != null) {
                splits = SplitCalculator.fromCumulative(curve, hrSamples)
                if (splits.isNotEmpty()) source = SplitSource.SPEED
            }
        }

        Log.d(tag, "Splits for ${session.id}: source=$source count=${splits.size} routeConsent=$consentRequired")

        SessionDetails(
            splits = splits,
            hrPoints = hrSamples.map { HeartRatePoint(it.first, it.second) },
            splitSource = source,
            routeConsentRequired = consentRequired && splits.isEmpty(),
            routeElevationGainMeters = TrackMath.elevationGain(routePoints)
        )
    }

    private suspend fun readDistanceSplits(
        client: HealthConnectClient,
        session: RunSession,
        hrSamples: List<Pair<Long, Int>>
    ): List<RunSplit> {
        val total = session.durationSeconds.toDouble()
        val intervals = mutableListOf<DistanceInterval>()
        var pageToken: String? = null
        do {
            val response = client.readRecords(
                ReadRecordsRequest(
                    recordType = DistanceRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(session.startTime, session.endTime),
                    pageToken = pageToken
                )
            )
            for (rec in response.records) {
                val rawStart = Duration.between(session.startTime, rec.startTime).toMillis() / 1000.0
                val rawEnd = Duration.between(session.startTime, rec.endTime).toMillis() / 1000.0
                if (rawEnd <= rawStart) continue
                val start = rawStart.coerceAtLeast(0.0)
                val end = rawEnd.coerceAtMost(total)
                if (end <= start) continue
                // Scale distance when the record sticks out of the session window.
                val fraction = (end - start) / (rawEnd - rawStart)
                intervals.add(DistanceInterval(start, end, rec.distance.inMeters * fraction))
            }
            pageToken = response.pageToken?.takeIf { it.isNotEmpty() }
        } while (pageToken != null)
        return SplitCalculator.compute(intervals, hrSamples)
    }

    private suspend fun readSpeedCurve(client: HealthConnectClient, session: RunSession): List<DistancePoint>? {
        val samples = mutableListOf<Pair<Double, Double>>()
        var pageToken: String? = null
        do {
            val response = client.readRecords(
                ReadRecordsRequest(
                    recordType = SpeedRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(session.startTime, session.endTime),
                    pageToken = pageToken
                )
            )
            for (rec in response.records) {
                for (sample in rec.samples) {
                    val sec = Duration.between(session.startTime, sample.time).toMillis() / 1000.0
                    if (sec < 0.0 || sec > session.durationSeconds) continue
                    samples.add(sec to sample.speed.inMetersPerSecond)
                }
            }
            pageToken = response.pageToken?.takeIf { it.isNotEmpty() }
        } while (pageToken != null)
        return TrackMath.scaleTo(TrackMath.cumulativeFromSpeed(samples), session.distanceMeters)
    }

    /** Runs a best-effort read: logs and returns null on any failure except cancellation. */
    private inline fun <T> safely(what: String, block: () -> T?): T? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(tag, "Could not read $what", e)
        null
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
        isSeed = isSeed,
        hrHistogram = hrHistogram
    )
}
