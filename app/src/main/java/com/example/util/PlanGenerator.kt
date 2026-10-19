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
}
