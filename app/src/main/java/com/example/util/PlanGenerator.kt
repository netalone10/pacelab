package com.example.util

import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.roundToInt

enum class WorkoutType(val title: String) {
    EASY("Easy Run"),
    TEMPO("Tempo Run"),
    INTERVALS("Interval Quality"),
    LONG_RUN("Long Run")
}

data class PlanDayItem(
    val dayOfWeek: DayOfWeek,
    val type: WorkoutType,
    val targetDistanceMeters: Double,
    val minPaceSec: Int,
    val maxPaceSec: Int,
    val targetHrZone: String,
    val isDone: Boolean = false,
    val matchedDistanceMeters: Double? = null,
    val matchedPaceSec: Int? = null,
    val matchedDate: LocalDate? = null
)

data class TrainingPlanWeek(
    val weekIndex: Int, // 1, 2, 3, 4
    val isDeloadWeek: Boolean,
    val totalTargetDistanceMeters: Double,
    val days: List<PlanDayItem>
)

object PlanGenerator {

    const val MAX_PROGRESSION_RATE = 1.10 // Max 10% weekly increase
    const val DELOAD_FACTOR = 0.80        // 20% volume drop on week 4

    /**
     * Calculates the weekly volume progression across 4 weeks:
     * Week 1: baseVolume
     * Week 2: baseVolume * 1.10
     * Week 3: week2 * 1.10
     * Week 4: week3 * 0.80 (recovery deload week)
     */
    fun calculateWeeklyVolumes(baseVolumeKm: Double): List<Double> {
        val week1 = baseVolumeKm
        val week2 = (week1 * MAX_PROGRESSION_RATE * 100).roundToInt() / 100.0
        val week3 = (week2 * MAX_PROGRESSION_RATE * 100).roundToInt() / 100.0
        val week4 = (week3 * DELOAD_FACTOR * 100).roundToInt() / 100.0
        return listOf(week1, week2, week3, week4)
    }

    /**
     * Generates a 4-week training plan for a given training frequency (2, 3, or 4 days/week)
     * and target race pace ranges.
     */
    fun generatePlan(
        baseWeeklyVolumeKm: Double = 18.0,
        daysPerWeek: Int = 3,
        targets: PredictedTargets,
        zones: HrZones = HrZones(),
        startMonday: LocalDate = LocalDate.now().with(java.time.temporal.TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    ): List<TrainingPlanWeek> {
        val volumesKm = calculateWeeklyVolumes(baseWeeklyVolumeKm)
        val validDays = daysPerWeek.coerceIn(2, 4)

        return volumesKm.mapIndexed { index, volumeKm ->
            val weekIndex = index + 1
            val isDeload = weekIndex == 4
            val totalMeters = volumeKm * 1000.0
            val days = generateWeekDays(validDays, totalMeters, targets, isDeload)
            TrainingPlanWeek(
                weekIndex = weekIndex,
                isDeloadWeek = isDeload,
                totalTargetDistanceMeters = totalMeters,
                days = days
            )
        }
    }

    private fun generateWeekDays(
        daysPerWeek: Int,
        weeklyMeters: Double,
        targets: PredictedTargets,
        isDeload: Boolean
    ): List<PlanDayItem> {
        return when (daysPerWeek) {
            2 -> {
                // Tuesday: Tempo (40%), Saturday: Long Run (60%)
                listOf(
                    PlanDayItem(
                        dayOfWeek = DayOfWeek.TUESDAY,
                        type = WorkoutType.TEMPO,
                        targetDistanceMeters = roundTo500m(weeklyMeters * 0.40),
                        minPaceSec = targets.tempoPaceRange.first,
                        maxPaceSec = targets.tempoPaceRange.second,
                        targetHrZone = "Z3 (141-160 bpm)"
                    ),
                    PlanDayItem(
                        dayOfWeek = DayOfWeek.SATURDAY,
                        type = WorkoutType.LONG_RUN,
                        targetDistanceMeters = roundTo500m(weeklyMeters * 0.60),
                        minPaceSec = targets.longRunPaceRange.first,
                        maxPaceSec = targets.longRunPaceRange.second,
                        targetHrZone = "Z2 (118-140 bpm)"
                    )
                )
            }
            4 -> {
                // Tuesday: Easy (22%), Thursday: Intervals/Tempo (25%), Saturday: Easy (18%), Sunday: Long Run (35%)
                val qualityType = if (isDeload) WorkoutType.TEMPO else WorkoutType.INTERVALS
                val qualityPaces = if (isDeload) targets.tempoPaceRange else targets.intervalPaceRange
                val qualityHr = if (isDeload) "Z3 (141-160 bpm)" else "Z4 (161-179 bpm)"
                listOf(
                    PlanDayItem(
                        dayOfWeek = DayOfWeek.TUESDAY,
                        type = WorkoutType.EASY,
                        targetDistanceMeters = roundTo500m(weeklyMeters * 0.22),
                        minPaceSec = targets.easyPaceRange.first,
                        maxPaceSec = targets.easyPaceRange.second,
                        targetHrZone = "Z2 (118-140 bpm)"
                    ),
                    PlanDayItem(
                        dayOfWeek = DayOfWeek.THURSDAY,
                        type = qualityType,
                        targetDistanceMeters = roundTo500m(weeklyMeters * 0.25),
                        minPaceSec = qualityPaces.first,
                        maxPaceSec = qualityPaces.second,
                        targetHrZone = qualityHr
                    ),
                    PlanDayItem(
                        dayOfWeek = DayOfWeek.SATURDAY,
                        type = WorkoutType.EASY,
                        targetDistanceMeters = roundTo500m(weeklyMeters * 0.18),
                        minPaceSec = targets.easyPaceRange.first,
                        maxPaceSec = targets.easyPaceRange.second,
                        targetHrZone = "Z1-Z2 (<140 bpm)"
                    ),
                    PlanDayItem(
                        dayOfWeek = DayOfWeek.SUNDAY,
                        type = WorkoutType.LONG_RUN,
                        targetDistanceMeters = roundTo500m(weeklyMeters * 0.35),
                        minPaceSec = targets.longRunPaceRange.first,
                        maxPaceSec = targets.longRunPaceRange.second,
                        targetHrZone = "Z2 (118-140 bpm)"
                    )
                )
            }
            else -> {
                // 3 days per week default:
                // Tuesday: Easy Run (30%)
                // Thursday: Tempo / Intervals (25%)
                // Saturday: Long Run (45%)
                val qualityType = if (isDeload) WorkoutType.TEMPO else WorkoutType.INTERVALS
                val qualityPaces = if (isDeload) targets.tempoPaceRange else targets.intervalPaceRange
                val qualityHr = if (isDeload) "Z3 (141-160 bpm)" else "Z4 (161-179 bpm)"
                listOf(
                    PlanDayItem(
                        dayOfWeek = DayOfWeek.TUESDAY,
                        type = WorkoutType.EASY,
                        targetDistanceMeters = roundTo500m(weeklyMeters * 0.30),
                        minPaceSec = targets.easyPaceRange.first,
                        maxPaceSec = targets.easyPaceRange.second,
                        targetHrZone = "Z2 (118-140 bpm)"
                    ),
                    PlanDayItem(
                        dayOfWeek = DayOfWeek.THURSDAY,
                        type = qualityType,
                        targetDistanceMeters = roundTo500m(weeklyMeters * 0.25),
                        minPaceSec = qualityPaces.first,
                        maxPaceSec = qualityPaces.second,
                        targetHrZone = qualityHr
                    ),
                    PlanDayItem(
                        dayOfWeek = DayOfWeek.SATURDAY,
                        type = WorkoutType.LONG_RUN,
                        targetDistanceMeters = roundTo500m(weeklyMeters * 0.45),
                        minPaceSec = targets.longRunPaceRange.first,
                        maxPaceSec = targets.longRunPaceRange.second,
                        targetHrZone = "Z2 (118-140 bpm)"
                    )
                )
            }
        }
    }

    /**
     * Matches actual recorded running sessions to planned days.
     * A day is considered matched if there is an actual run on that day
     * (or +-1 day flexibility) with distance >= 70% of target.
     */
    fun matchRealSessions(
        days: List<PlanDayItem>,
        weekStartMonday: LocalDate,
        realSessions: List<Pair<LocalDate, Pair<Double, Int>>> // (Date, (distanceMeters, paceSec))
    ): List<PlanDayItem> {
        val usedIndices = mutableSetOf<Int>()

        return days.map { planDay ->
            val expectedDate = weekStartMonday.plusDays((planDay.dayOfWeek.value - 1).toLong())
            var matchedSession: Pair<LocalDate, Pair<Double, Int>>? = null
            var matchedIdx = -1

            // 1. Look for exact date match first
            for (i in realSessions.indices) {
                if (i in usedIndices) continue
                val (date, runData) = realSessions[i]
                if (date == expectedDate && runData.first >= planDay.targetDistanceMeters * 0.70) {
                    matchedSession = realSessions[i]
                    matchedIdx = i
                    break
                }
            }

            // 2. If no exact date, look within +-1 day
            if (matchedSession == null) {
                for (i in realSessions.indices) {
                    if (i in usedIndices) continue
                    val (date, runData) = realSessions[i]
                    val dayDiff = abs(date.toEpochDay() - expectedDate.toEpochDay())
                    if (dayDiff <= 1 && runData.first >= planDay.targetDistanceMeters * 0.70) {
                        matchedSession = realSessions[i]
                        matchedIdx = i
                        break
                    }
                }
            }

            if (matchedSession != null && matchedIdx != -1) {
                usedIndices.add(matchedIdx)
                planDay.copy(
                    isDone = true,
                    matchedDate = matchedSession.first,
                    matchedDistanceMeters = matchedSession.second.first,
                    matchedPaceSec = matchedSession.second.second
                )
            } else {
                planDay
            }
        }
    }

    private fun roundTo500m(meters: Double): Double {
        return (meters / 500.0).roundToInt() * 500.0
    }
}
