package com.example

import android.content.Context
import androidx.health.connect.client.records.ExerciseRoute
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.health.HealthConnectStatus
import com.example.data.local.CachedSessionEntity
import com.example.data.local.PaceLabDatabase
import com.example.data.local.RaceEventEntity
import com.example.data.local.UserSettingsEntity
import com.example.data.model.RunSession
import com.example.data.repository.HealthConnectRepository
import com.example.data.repository.PaceLabRepository
import com.example.data.repository.SessionDetails
import com.example.data.repository.SyncSummary
import com.example.data.repository.encodeHeartRateHistogramForSync
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.json.JSONObject
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HealthConnectSyncTest {
    private lateinit var database: PaceLabDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, PaceLabDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun histogramWithoutPermission_staysNullAndRunIsProcessedAgainAfterGrant() = runTest {
        val dao = database.dao()
        val session = cachedSession(hrHistogram = null)
        dao.insertSessions(listOf(session))

        assertNull(encodeHeartRateHistogramForSync(hasPermission = false, samples = emptyList()))
        assertTrue(dao.getSyncedSessionIds().isEmpty())

        val processedWithoutSamples = session.copy(
            hrHistogram = encodeHeartRateHistogramForSync(hasPermission = true, samples = emptyList())
        )
        dao.insertSessions(listOf(processedWithoutSamples))
        assertEquals(listOf(session.id), dao.getSyncedSessionIds())

        val processedWithSamples = session.copy(
            hrHistogram = encodeHeartRateHistogramForSync(
                hasPermission = true,
                samples = listOf(0L to 140, 5L to 150)
            )
        )
        dao.insertSessions(listOf(processedWithSamples))
        assertTrue(dao.getAllSessionsList().single().hrHistogram!!.isNotEmpty())
    }

    @Test
    fun syncAcrossRepositoryInstances_neverRunsConcurrently() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val healthConnect = FakeHealthConnectRepository()
        val first = PaceLabRepository(context, healthConnect, database.dao())
        val second = PaceLabRepository(context, healthConnect, database.dao())

        listOf(first, second).map { repository ->
            async(Dispatchers.Default) { repository.syncData() }
        }.awaitAll()

        assertEquals(2, healthConnect.syncCalls.get())
        assertEquals(1, healthConnect.maxConcurrentCalls.get())
    }

    @Test
    fun snapshot_containsOnlyTwelveWeekRunsAndUserConfiguration() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val now = Instant.parse("2026-11-08T00:00:00Z")
        val recent = cachedSession(null).copy(
            startTimeEpochMs = now.minusSeconds(7 * 86400).toEpochMilli()
        )
        val old = cachedSession(null).copy(
            id = "old-run",
            startTimeEpochMs = now.minusSeconds(85 * 86400).toEpochMilli()
        )
        val dao = database.dao()
        dao.insertSessions(listOf(recent, old))
        dao.saveUserSettings(UserSettingsEntity(trainingDaysPerWeek = 4, maxHr = 195))
        dao.saveTargetRace(
            RaceEventEntity(
                raceName = "Jakarta HM",
                raceDateEpochDay = 20_000,
                distanceMeters = 21_097.5,
                targetDurationSeconds = 7_200,
                targetPaceSeconds = 341,
                location = "Jakarta"
            )
        )

        val snapshot = JSONObject(
            PaceLabRepository(context, FakeHealthConnectRepository(), dao).createSnapshotJson(now)
        )
        assertEquals(1, snapshot.getJSONArray("runs").length())
        assertEquals("run-1", snapshot.getJSONArray("runs").getJSONObject(0).getString("id"))
        assertEquals("Jakarta HM", snapshot.getJSONObject("targetRace").getString("raceName"))
        assertEquals(4, snapshot.getInt("trainingDaysPerWeek"))
        assertEquals(195, snapshot.getJSONObject("hrZones").getInt("maxHr"))
    }

    private fun cachedSession(hrHistogram: String?) = CachedSessionEntity(
        id = "run-1",
        startTimeEpochMs = 1_000L,
        endTimeEpochMs = 3_601_000L,
        distanceMeters = 10_000.0,
        durationSeconds = 3_600L,
        avgPaceSeconds = 360,
        avgHeartRate = null,
        maxHeartRate = null,
        totalCalories = null,
        elevationGainedMeters = null,
        isSeed = false,
        hrHistogram = hrHistogram
    )

    private class FakeHealthConnectRepository : HealthConnectRepository {
        val syncCalls = AtomicInteger()
        val maxConcurrentCalls = AtomicInteger()
        private val activeCalls = AtomicInteger()

        override val requiredPermissions: Set<String> = emptySet()
        override suspend fun getAvailabilityStatus(): HealthConnectStatus = HealthConnectStatus.Available
        override suspend fun getGrantedPermissions(): Set<String> = emptySet()

        override suspend fun syncAllRunningData(): Result<SyncSummary> {
            syncCalls.incrementAndGet()
            val active = activeCalls.incrementAndGet()
            maxConcurrentCalls.accumulateAndGet(active) { previous, current -> maxOf(previous, current) }
            delay(100)
            activeCalls.decrementAndGet()
            return Result.success(SyncSummary(fetched = 0, added = 0))
        }

        override suspend fun getSessionSplitsAndHr(
            session: RunSession,
            grantedRoute: ExerciseRoute?
        ): SessionDetails = SessionDetails(emptyList(), emptyList())
    }
}
