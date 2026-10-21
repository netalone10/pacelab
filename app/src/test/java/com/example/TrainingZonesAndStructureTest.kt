package com.example

import com.example.data.model.RaceDistance
import com.example.data.model.RaceEvent
import com.example.util.PeriodizedPlanner
import com.example.util.RiegelCalculator
import com.example.util.WorkoutType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class TrainingZonesAndStructureTest {

    private val monday = LocalDate.of(2026, 1, 5) // a Monday
    private val targets = RiegelCalculator.computeTargets(5310.0, 2157L, 10000.0)

    @Test
    fun trainingZones_doNotChangeWithGoalDistance() {
        val tenK = RiegelCalculator.computeTargets(5310.0, 2157L, 10000.0)
        val half = RiegelCalculator.computeTargets(5310.0, 2157L, 21097.5)

        // The race prediction follows the goal, the training zones do not.
        assertTrue(half.predictedPaceSeconds > tenK.predictedPaceSeconds)
        assertEquals(tenK.easyPaceRange, half.easyPaceRange)
        assertEquals(tenK.longRunPaceRange, half.longRunPaceRange)
        assertEquals(tenK.tempoPaceRange, half.tempoPaceRange)
        assertEquals(tenK.intervalPaceRange, half.intervalPaceRange)
        assertEquals(tenK.repetitionPaceRange, half.repetitionPaceRange)
    }

    @Test
    fun longRun_staysWithinItsShareOfTheWeek() {
        for (days in 2..4) {
            val weeks = PeriodizedPlanner.generatePeriodizedPlan(
                raceEvent = null, baseWeeklyVolumeKm = 20.0, daysPerWeek = days,
                targets = targets, currentMonday = monday
            )
            val limit = if (days == 2) 0.50 else 0.35
            for (week in weeks) {
                val longRun = week.days.first { it.type == WorkoutType.LONG_RUN }
                // 250 m of slack for rounding to the nearest 500 m.
                assertTrue(
                    "$days days, week ${week.weekIndex}: long run ${longRun.targetDistanceMeters} m " +
                        "of ${week.totalTargetDistanceMeters} m",
                    longRun.targetDistanceMeters <= week.totalTargetDistanceMeters * limit + 250.0
                )
            }
        }
    }

    @Test
    fun qualityStructure_tempoHasWarmUpWorkAndCoolDown() {
        assertEquals(
            "Warm-up 1.5 km easy, 3.5 km at tempo pace, cool-down 1 km easy",
            PeriodizedPlanner.qualityStructure(6000.0, WorkoutType.TEMPO)
        )
    }

    @Test
    fun qualityStructure_intervalsFitInsideTheSessionDistance() {
        assertEquals(
            "Warm-up 2 km easy, 4 x 800 m at interval pace with 400 m easy jog between, cool-down 1 km easy",
            PeriodizedPlanner.qualityStructure(8000.0, WorkoutType.INTERVALS)
        )
    }

    @Test
    fun qualityStructure_isNullForPlainRunsAndTooShortSessions() {
        assertNull(PeriodizedPlanner.qualityStructure(6000.0, WorkoutType.EASY))
        assertNull(PeriodizedPlanner.qualityStructure(10000.0, WorkoutType.LONG_RUN))
        assertNull(PeriodizedPlanner.qualityStructure(2500.0, WorkoutType.TEMPO))
        assertNull(PeriodizedPlanner.qualityStructure(3000.0, WorkoutType.INTERVALS))
    }

    @Test
    fun plan_qualityDaysCarryStructureAndPlainDaysDoNot() {
        val race = RaceEvent(
            raceName = "Test 10K",
            raceDate = monday.plusWeeks(9).plusDays((DayOfWeek.SATURDAY.value - 1).toLong()),
            distance = RaceDistance.TEN_K
        )
        val weeks = PeriodizedPlanner.generatePeriodizedPlan(
            raceEvent = race, baseWeeklyVolumeKm = 20.0, daysPerWeek = 3,
            targets = targets, currentMonday = monday
        )
        for (week in weeks.filter { !it.isRaceWeek }) {
            for (day in week.days) {
                if (day.type == WorkoutType.TEMPO || day.type == WorkoutType.INTERVALS) {
                    assertNotNull("week ${week.weekIndex} ${day.dayOfWeek} needs a structure", day.structure)
                } else {
                    assertNull(day.structure)
                }
            }
        }
    }
}
