package com.example

import com.example.data.model.RaceDistance
import com.example.data.model.RaceEvent
import com.example.util.PeriodizedPlanner
import com.example.util.WorkoutType
import com.example.util.RiegelCalculator
import com.example.util.TrainingPhase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class HalfMarathonPlannerTest {
    private val monday = LocalDate.of(2026, 1, 5)
    private val race = RaceEvent(
        raceName = "Test Half Marathon",
        raceDate = monday.plusWeeks(15).plusDays(6),
        distance = RaceDistance.HALF_MARATHON
    )
    private val targets = RiegelCalculator.computeTargets(
        5_310.0,
        2_157L,
        RaceDistance.HALF_MARATHON.meters
    )
    private val weeks = PeriodizedPlanner.generatePeriodizedPlan(
        raceEvent = race,
        baseWeeklyVolumeKm = 12.0,
        daysPerWeek = 3,
        targets = targets,
        currentMonday = monday
    )

    @Test
    fun longRun_reachesFourteenKmWithBoundedWeeklyProgressionAndDeloads() {
        val trainingWeeks = weeks.filterNot { it.isRaceWeek }
        val longRuns = trainingWeeks.map { week ->
            week.days.single { it.type == WorkoutType.LONG_RUN }.targetDistanceMeters
        }

        assertTrue(longRuns.max() >= 14_000.0)
        longRuns.zipWithNext().forEachIndexed { index, (previous, next) ->
            assertTrue(
                "Week ${index + 2} long run increased by ${(next - previous) / 1000.0} km",
                next - previous <= 1_500.0 + 0.01
            )
        }
        assertEquals(listOf(4, 8, 12), trainingWeeks.filter { it.isDeloadWeek }.map { it.weekIndex })
        trainingWeeks.filter { it.isDeloadWeek }.forEach { week ->
            val before = weeks[week.weekIndex - 2]
            assertTrue(week.totalTargetDistanceMeters < before.totalTargetDistanceMeters)
        }
    }

    @Test
    fun volume_reachesTwiceBaselineWithoutBreakingLoadingProgression() {
        val trainingWeeks = weeks.filterNot { it.isRaceWeek }
        assertTrue(trainingWeeks.maxOf { it.totalTargetDistanceMeters } >= 24_000.0)

        var previousLoadingVolume = 12_000.0
        trainingWeeks.drop(1).forEach { week ->
            if (!week.isDeloadWeek) {
                assertTrue(
                    "Week ${week.weekIndex} exceeded 10% loading progression",
                    week.totalTargetDistanceMeters <= previousLoadingVolume * 1.10 + 1.0
                )
                previousLoadingVolume = week.totalTargetDistanceMeters
            }
        }
    }

    @Test
    fun raceEve_isShortEasyWithStrides_andPeakIncludesHalfMarathonPace() {
        val raceWeek = weeks.last()
        val raceEve = raceWeek.days.single { it.dayOfWeek == DayOfWeek.SATURDAY }
        assertEquals(WorkoutType.EASY, raceEve.type)
        assertTrue(raceEve.targetDistanceMeters <= 4_000.0)
        assertTrue(raceEve.targetHrZone.contains("strides", ignoreCase = true))
        assertEquals(targets.easyPaceRange, raceEve.minPaceSec to raceEve.maxPaceSec)

        val peakWeek = weeks.single { it.phase == TrainingPhase.PEAK }
        assertTrue(peakWeek.days.any {
            it.type == WorkoutType.TEMPO &&
                it.minPaceSec == targets.predictedPaceSeconds &&
                it.maxPaceSec == targets.predictedPaceSeconds
        })
    }
}
