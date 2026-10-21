package com.example

import com.example.data.health.SeedDataGenerator
import com.example.data.model.RunSession
import com.example.data.model.UserSettings
import com.example.util.HrZones
import com.example.util.TrainingLoadStatus
import com.example.util.WeeklyTrainingLoadCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

class WeeklyTrainingLoadTest {

    private val zone = ZoneId.systemDefault()
    private val defaultZones = HrZones()
    private val settings = UserSettings()

    @Test
    fun calculateSessionLoad_withAvgHeartRate() {
        val now = Instant.now()
        // 30 min run with avg HR 130 (Zone 2 -> factor 1.5)
        val session = RunSession(
            id = "test_z2",
            startTime = now,
            endTime = now.plusSeconds(1800),
            distanceMeters = 5000.0,
            durationSeconds = 1800,
            avgPaceSeconds = 360,
            avgHeartRate = 130,
            isSeed = false
        )
        val load = WeeklyTrainingLoadCalculator.calculateSessionLoad(session, defaultZones)
        // 30 mins * 1.5 = 45.0
        assertEquals(45.0, load, 0.01)
    }

    @Test
    fun calculateSessionLoad_duplicateIgnored() {
        val now = Instant.now()
        val session = RunSession(
            id = "test_dup",
            startTime = now,
            endTime = now.plusSeconds(1800),
            distanceMeters = 5000.0,
            durationSeconds = 1800,
            avgPaceSeconds = 360,
            avgHeartRate = 150,
            isDuplicate = true
        )
        val load = WeeklyTrainingLoadCalculator.calculateSessionLoad(session, defaultZones)
        assertEquals(0.0, load, 0.001)
    }

    @Test
    fun compute4WeekProgression_returnsExact4Weeks() {
        val demoSessions = SeedDataGenerator.generateDemoSessions()
        val currentMon = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

        val summary = WeeklyTrainingLoadCalculator.compute4WeekProgression(
            allSessions = demoSessions,
            anchorMonday = currentMon,
            userSettings = settings,
            zone = zone
        )

        assertEquals(4, summary.weeks.size)
        assertEquals("W-3", summary.weeks[0].weekLabel)
        assertEquals("W-2", summary.weeks[1].weekLabel)
        assertEquals("W-1", summary.weeks[2].weekLabel)
        assertEquals("This Week", summary.weeks[3].weekLabel)
        assertTrue(summary.weeks[3].isCurrentWeek)

        // Demo sessions have runs in all 4 weeks
        assertTrue(summary.weeks[0].loadScore > 0)
        assertTrue(summary.weeks[1].loadScore > 0)
        assertTrue(summary.weeks[2].loadScore > 0)
        assertTrue(summary.fourWeekAverageLoad > 0)
        assertNotNull(summary.feedbackMessage)
    }

    @Test
    fun compute4WeekProgression_detectsOverreachingWhenLoadSurges() {
        val anchorMon = LocalDate.of(2026, 3, 23) // Monday
        val sessions = mutableListOf<RunSession>()

        // Week 1 (anchor - 3 weeks): 100 load
        sessions.add(createTestSession(anchorMon.minusWeeks(3).plusDays(1), 5000.0, 1800, 130)) // ~45 load
        sessions.add(createTestSession(anchorMon.minusWeeks(3).plusDays(3), 5000.0, 1800, 130)) // ~45 load -> ~90 total

        // Week 2: ~90 load
        sessions.add(createTestSession(anchorMon.minusWeeks(2).plusDays(1), 5000.0, 1800, 130))
        sessions.add(createTestSession(anchorMon.minusWeeks(2).plusDays(3), 5000.0, 1800, 130))

        // Week 3: ~90 load
        sessions.add(createTestSession(anchorMon.minusWeeks(1).plusDays(1), 5000.0, 1800, 130))
        sessions.add(createTestSession(anchorMon.minusWeeks(1).plusDays(3), 5000.0, 1800, 130))

        // Week 4: Surge to ~250 load (>200% increase)
        for (i in 0 until 5) {
            sessions.add(createTestSession(anchorMon.plusDays(i.toLong()), 8000.0, 2400, 165)) // Zone 4 (factor 3.2): 40 * 3.2 = 128 load each
        }

        val summary = WeeklyTrainingLoadCalculator.compute4WeekProgression(
            allSessions = sessions,
            anchorMonday = anchorMon,
            userSettings = settings,
            zone = zone
        )

        assertEquals(TrainingLoadStatus.OVERREACHING, summary.status)
        assertTrue(summary.acuteChronicRatio != null && summary.acuteChronicRatio!! > 1.4)
    }

    private fun createTestSession(date: LocalDate, distMeters: Double, durSec: Long, avgHr: Int): RunSession {
        val start = date.atTime(7, 0).atZone(zone).toInstant()
        return RunSession(
            id = "test_${date}_${distMeters.toInt()}",
            startTime = start,
            endTime = start.plusSeconds(durSec),
            distanceMeters = distMeters,
            durationSeconds = durSec,
            avgPaceSeconds = (durSec.toDouble() / (distMeters / 1000.0)).toInt(),
            avgHeartRate = avgHr,
            isSeed = false
        )
    }
}
