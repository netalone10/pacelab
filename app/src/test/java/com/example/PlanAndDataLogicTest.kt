package com.example

import com.example.data.model.RaceDistance
import com.example.data.model.RaceEvent
import com.example.data.model.RunSession
import com.example.data.model.UserSettings
import com.example.util.DistanceInterval
import com.example.util.HrHistogram
import com.example.util.HrZones
import com.example.util.PeriodizedPlanner
import com.example.util.RiegelCalculator
import com.example.util.SplitCalculator
import com.example.util.WeeklySummaryCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId

class PlanAndDataLogicTest {

    private val monday = LocalDate.of(2026, 1, 5) // a Monday
    private val targets = RiegelCalculator.computeTargets(5310.0, 2157L, 10000.0)

    private fun race(weeksAhead: Long, day: DayOfWeek = DayOfWeek.SATURDAY) = RaceEvent(
        raceName = "Test 10K",
        raceDate = monday.plusWeeks(weeksAhead).plusDays((day.value - 1).toLong()),
        distance = RaceDistance.TEN_K
    )

    @Test
    fun hrHistogram_roundTripAndBuckets() {
        val samples = listOf(0L to 140, 5L to 140, 10L to 150)
        val histogram = HrHistogram.build(samples)
        assertEquals(10L, histogram[140])
        assertEquals(5L, histogram[150])

        val decoded = HrHistogram.decode(HrHistogram.encode(histogram))
        assertEquals(histogram, decoded)

        val dist = HrHistogram.bucket(decoded, HrZones())
        assertEquals(10L, dist.z2Seconds)
        assertEquals(5L, dist.z3Seconds)
    }

    @Test
    fun hrHistogram_decodeHandlesEmptyAndNull() {
        assertTrue(HrHistogram.decode(null).isEmpty())
        assertTrue(HrHistogram.decode("").isEmpty())
    }

    @Test
    fun splits_constantSpeedGivesEqualKilometres() {
        // 40 intervals x 60 s x 150 m = 6 km at 400 s/km
        val intervals = (0 until 40).map { DistanceInterval(it * 60.0, (it + 1) * 60.0, 150.0) }
        val splits = SplitCalculator.compute(intervals, emptyList())
        assertEquals(6, splits.size)
        splits.forEach { assertTrue(kotlin.math.abs(it.splitSeconds - 400) <= 1) }
    }

    @Test
    fun splits_tooCoarseDataGivesNoSplits() {
        val splits = SplitCalculator.compute(listOf(DistanceInterval(0.0, 1800.0, 5000.0)), emptyList())
        assertTrue(splits.isEmpty())
    }

    @Test
    fun plan_neverJumpsMoreThanTenPercent() {
        val weeks = PeriodizedPlanner.generatePeriodizedPlan(
            raceEvent = race(9), baseWeeklyVolumeKm = 20.0, daysPerWeek = 3,
            targets = targets, currentMonday = monday
        )
        var maxSoFar = 0.0
        for (i in weeks.indices) {
            val vol = weeks[i].totalTargetDistanceMeters
            if (i > 0 && !weeks[i].isRaceWeek) {
                val prev = weeks[i - 1]
                val limit = if (prev.isDeloadWeek) maxSoFar else prev.totalTargetDistanceMeters * 1.10
                assertTrue("week ${i + 1} volume $vol exceeds $limit", vol <= limit + 1.0)
            }
            if (!weeks[i].isRaceWeek) maxSoFar = maxOf(maxSoFar, vol)
        }
    }

    @Test
    fun plan_deloadIsEightyPercent() {
        val weeks = PeriodizedPlanner.generatePeriodizedPlan(
            raceEvent = race(9), baseWeeklyVolumeKm = 20.0, daysPerWeek = 3,
            targets = targets, currentMonday = monday
        )
        val deload = weeks.first { it.isDeloadWeek }
        val before = weeks[deload.weekIndex - 2]
        assertEquals(before.totalTargetDistanceMeters * 0.8, deload.totalTargetDistanceMeters, 5.0)
    }

    @Test
    fun plan_raceWeekWorkoutsComeBeforeRaceDay() {
        for (day in listOf(DayOfWeek.WEDNESDAY, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)) {
            val ev = race(5, day)
            val last = PeriodizedPlanner.generatePeriodizedPlan(
                raceEvent = ev, baseWeeklyVolumeKm = 20.0, daysPerWeek = 3,
                targets = targets, currentMonday = monday
            ).last()
            assertTrue(last.isRaceWeek)
            assertEquals(ev.raceDate.minusDays((ev.raceDate.dayOfWeek.value - 1).toLong()), last.weekStartMonday)
            assertEquals("one item per weekday only", last.days.size, last.days.map { it.dayOfWeek }.toSet().size)
            last.days.filter { it.dayOfWeek != ev.raceDate.dayOfWeek }
                .forEach { assertTrue(it.dayOfWeek.value < ev.raceDate.dayOfWeek.value) }
        }
    }

    @Test
    fun plan_farRaceIsCappedAtSixteenWeeks() {
        val ev = race(30)
        val weeks = PeriodizedPlanner.generatePeriodizedPlan(
            raceEvent = ev, baseWeeklyVolumeKm = 20.0, daysPerWeek = 3,
            targets = targets, currentMonday = monday
        )
        assertEquals(16, weeks.size)
        assertEquals(ev.raceDate.with(java.time.temporal.TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)), weeks.last().weekStartMonday)
    }

    @Test
    fun plan_pastRaceFallsBackToFourWeeks() {
        val past = RaceEvent(raceName = "Old", raceDate = monday.minusWeeks(3), distance = RaceDistance.TEN_K)
        val weeks = PeriodizedPlanner.generatePeriodizedPlan(
            raceEvent = past, baseWeeklyVolumeKm = 20.0, daysPerWeek = 3,
            targets = targets, currentMonday = monday
        )
        assertEquals(4, weeks.size)
        assertEquals(monday, weeks.first().weekStartMonday)
    }

    @Test
    fun weeklySummary_ignoresDuplicates() {
        val zone = ZoneId.systemDefault()
        val start = monday.atStartOfDay(zone).toInstant().plusSeconds(10 * 3600L)
        fun run(id: String, dup: Boolean) = RunSession(
            id = id, startTime = start, endTime = start.plusSeconds(1800),
            distanceMeters = 5000.0, durationSeconds = 1800, avgPaceSeconds = 360,
            avgHeartRate = 150, isDuplicate = dup
        )
        val summary = WeeklySummaryCalculator.compute(
            listOf(run("a", false), run("b", true)), monday, UserSettings(), zone
        )
        assertEquals(1, summary.runCount)
        assertEquals(5000.0, summary.totalDistanceMeters, 0.1)
    }

    @Test
    fun computeTargets_includesRepetitionAndAllFiveZones() {
        val t = RiegelCalculator.computeTargets(5000.0, 1500L, 10000.0) // 5k in 25:00
        assertTrue(t.predictedDurationSeconds > 1500L)
        assertTrue(t.predictedPaceSeconds > 0)
        assertTrue(t.easyPaceRange.first > t.predictedPaceSeconds)
        assertTrue(t.longRunPaceRange.first > t.predictedPaceSeconds)
        assertTrue(t.tempoPaceRange.first > t.predictedPaceSeconds)
        assertTrue(t.intervalPaceRange.first <= t.predictedPaceSeconds)
        assertTrue(t.repetitionPaceRange.first < t.intervalPaceRange.first)
    }
}

