package com.example

import com.example.data.model.RunSession
import com.example.util.PeriodizedPlanner
import com.example.util.PredictionTrendCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class AdaptiveAndTrendTest {

    private val zone = ZoneId.of("UTC")
    private val planMonday = LocalDate.of(2026, 3, 9) // Monday

    private fun run(date: LocalDate, km: Double, minutes: Int, seed: Boolean = false): RunSession {
        val start = date.atStartOfDay(zone).toInstant().plusSeconds(8 * 3600L)
        return RunSession(
            id = "$date-$km", startTime = start, endTime = start.plusSeconds(minutes * 60L),
            distanceMeters = km * 1000, durationSeconds = minutes * 60L,
            avgPaceSeconds = (minutes * 60 / km).toInt(), isSeed = seed
        )
    }

    /** Three steady weeks of 15 km (3 x 5 km) before planMonday. */
    private fun steadyHistory(): List<RunSession> =
        (1..3).flatMap { w ->
            val monday = planMonday.minusWeeks(w.toLong())
            listOf(
                run(monday.plusDays(1), 5.0, 32),
                run(monday.plusDays(3), 5.0, 32),
                run(monday.plusDays(5), 5.0, 32)
            )
        }

    @Test
    fun adaptive_steadyHistoryUsesPlainAverage() {
        val advice = PeriodizedPlanner.adaptiveBaseVolume(
            steadyHistory(), planMonday, 3, today = planMonday, zone = zone
        )
        assertEquals(15.0, advice.baseKm, 0.5)
        assertNull(advice.note)
    }

    @Test
    fun adaptive_longBreakRestartsAtSixtyPercent() {
        val advice = PeriodizedPlanner.adaptiveBaseVolume(
            steadyHistory(), planMonday, 3, today = planMonday.plusWeeks(4), zone = zone
        )
        assertEquals(9.0, advice.baseKm, 0.6)
        assertNotNull(advice.note)
    }

    @Test
    fun adaptive_weakLastWeekDropsVolume() {
        val older = (2..4).flatMap { w ->
            val monday = planMonday.minusWeeks(w.toLong())
            listOf(
                run(monday.plusDays(1), 6.0, 38),
                run(monday.plusDays(3), 6.0, 38),
                run(monday.plusDays(5), 8.0, 50)
            )
        }
        val lastWeek = listOf(run(planMonday.minusWeeks(1).plusDays(5), 3.0, 20))
        val advice = PeriodizedPlanner.adaptiveBaseVolume(
            older + lastWeek, planMonday, 3, today = planMonday, zone = zone
        )
        assertNotNull(advice.note)
        assertTrue(advice.baseKm < 20.0 * 0.8)
    }

    @Test
    fun trend_usesBestRunPerWindowAndSkipsDemoWhenRealExists() {
        val today = planMonday.plusDays(2)
        val sessions = listOf(
            run(planMonday.minusWeeks(5), 5.0, 36),
            run(planMonday.minusWeeks(1), 5.0, 33),
            run(planMonday.minusWeeks(1), 5.0, 20, seed = true) // demo must be ignored
        )
        val trend = PredictionTrendCalculator.trend(sessions, 10000.0, today, 12, zone)
        assertTrue(trend.size >= 2)
        assertTrue("later point should be faster", trend.last().predictedSeconds < trend.first().predictedSeconds)
    }

    @Test
    fun bestRecentRun_ignoresShortRuns() {
        val now = planMonday.atStartOfDay(zone).toInstant()
        val sessions = listOf(
            run(planMonday.minusDays(3), 3.0, 14), // too short
            run(planMonday.minusDays(5), 5.0, 31)
        )
        val best = PredictionTrendCalculator.bestRecentRun(sessions, 10000.0, now)
        assertNotNull(best)
        assertEquals(5000.0, best!!.distanceMeters, 0.1)
    }
}
