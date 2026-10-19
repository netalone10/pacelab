package com.example.util

import com.example.data.model.RaceDistance
import com.example.data.model.RaceEvent
import com.example.data.model.RunSession
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

enum class TrainingPhase(val title: String, val badgeColorHex: Long) {
    BASE("Base Phase", 0xFF4ADE80),         // Aerobic Green
    BUILD("Build Phase", 0xFFFACC15),       // Threshold Amber
    PEAK("Peak Phase", 0xFFD4FF00),         // Neon Volt
    TAPER("1-Wk Sharp Taper", 0xFF38BDF8),  // Sky Blue
    RACE("Race Week", 0xFFF59E0B)           // Gold Accent
}

data class PeriodizedPlanWeek(
    val weekIndex: Int,
    val totalWeeks: Int,
    val weekStartMonday: LocalDate,
    val weekEndSunday: LocalDate,
    val phase: TrainingPhase,
    val isDeloadWeek: Boolean,
    val isRaceWeek: Boolean,
    val totalTargetDistanceMeters: Double,
    val days: List<PlanDayItem>
)

object PeriodizedPlanner {

    /**
     * Identifies duplicate sessions that were synced from multiple trackers.
     * Criteria:
     * - Start times within 15 minutes of each other
     * - Distances within 20% of each other
     * Returns: Set of secondary/duplicate session IDs to be resolved/removed.
     */
    fun findDuplicateSessionIds(sessions: List<RunSession>): Set<String> {
        val duplicates = mutableSetOf<String>()
        val sorted = sessions.sortedBy { it.startTime }

        for (i in 0 until sorted.size) {
            val a = sorted[i]
            if (a.id in duplicates) continue

            for (j in (i + 1) until sorted.size) {
                val b = sorted[j]
                if (b.id in duplicates) continue

                val timeDiffSeconds = abs(a.startTime.epochSecond - b.startTime.epochSecond)
                // If more than 15 minutes apart, skip further comparison
                if (timeDiffSeconds > 900) break

                val maxDist = max(a.distanceMeters, b.distanceMeters)
                val distDiff = abs(a.distanceMeters - b.distanceMeters)

                if (maxDist > 0 && (distDiff / maxDist) <= 0.20) {
                    // One is a duplicate! Determine which to keep as primary:
                    // Prefer session with heart rate data; otherwise prefer larger duration
                    val duplicateId = when {
                        a.avgHeartRate != null && b.avgHeartRate == null -> b.id
                        b.avgHeartRate != null && a.avgHeartRate == null -> a.id
                        a.durationSeconds >= b.durationSeconds -> b.id
                        else -> a.id
                    }
                    duplicates.add(duplicateId)
                }
            }
        }
        return duplicates
    }

    /**
     * Generates an adaptive multi-week periodized plan leading up to a target race date.
     * Features:
     * - Base, Build, Peak, and a 1-Week Sharp Taper immediately prior to race day.
     * - Max 10% volume progression across base/build phases.
     * - 4th-week recovery cycle during long blocks.
     */
    fun generatePeriodizedPlan(
        raceEvent: RaceEvent?,
        baseWeeklyVolumeKm: Double = 18.0,
        daysPerWeek: Int = 3,
        targets: PredictedTargets,
        zones: HrZones = HrZones(),
        currentMonday: LocalDate = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    ): List<PeriodizedPlanWeek> {
        if (raceEvent == null) {
            // Default 4-week progression if no race is scheduled
            return generateStandardFourWeekPlan(baseWeeklyVolumeKm, daysPerWeek, targets, currentMonday)
        }

        // Calculate weeks to target race
        val raceMonday = raceEvent.raceDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val daysUntilRaceMonday = ChronoUnit.DAYS.between(currentMonday, raceMonday)
        val computedWeeks = ((daysUntilRaceMonday / 7) + 1).toInt().coerceIn(2, 16)

        val totalWeeks = computedWeeks
        val validDays = daysPerWeek.coerceIn(2, 4)

        // Peak volume occurs at Week (totalWeeks - 1)
        val peakMultiplier = Math.pow(1.10, minOf(totalWeeks - 2, 4).toDouble())
        val peakVolumeKm = baseWeeklyVolumeKm * peakMultiplier

        return (1..totalWeeks).map { weekIndex ->
            val weekMonday = currentMonday.plusWeeks((weekIndex - 1).toLong())
            val weekSunday = weekMonday.plusDays(6)
            val isRaceWeek = weekIndex == totalWeeks
            val isPeakWeek = weekIndex == totalWeeks - 1 && totalWeeks >= 3

            val phase = when {
                isRaceWeek -> TrainingPhase.TAPER
                isPeakWeek -> TrainingPhase.PEAK
                weekIndex >= totalWeeks - 3 && totalWeeks >= 5 -> TrainingPhase.BUILD
                else -> TrainingPhase.BASE
            }

            val isDeloadWeek = weekIndex % 4 == 0 && !isPeakWeek && !isRaceWeek

            // Calculate weekly volume:
            val volumeKm = when {
                isRaceWeek -> {
                    // 1-WEEK SHARP TAPER: ~50% of peak volume (excluding the actual race)
                    (peakVolumeKm * 0.50 * 100).roundToInt() / 100.0
                }
                isPeakWeek -> {
                    (peakVolumeKm * 100).roundToInt() / 100.0
                }
                isDeloadWeek -> {
                    val prevVol = baseWeeklyVolumeKm * Math.pow(1.10, ((weekIndex - 2).coerceAtLeast(0)).toDouble())
                    (prevVol * 0.80 * 100).roundToInt() / 100.0
                }
                else -> {
                    val progressCount = (weekIndex - 1) - (weekIndex / 4)
                    val v = baseWeeklyVolumeKm * Math.pow(1.10, progressCount.coerceAtLeast(0).toDouble())
                    (minOf(v, peakVolumeKm) * 100).roundToInt() / 100.0
                }
            }

            val weeklyMeters = volumeKm * 1000.0
            val days = generateDaysForPeriodizedWeek(
                daysPerWeek = validDays,
                weeklyMeters = weeklyMeters,
                targets = targets,
                phase = phase,
                isRaceWeek = isRaceWeek,
                raceEvent = raceEvent
            )

            PeriodizedPlanWeek(
                weekIndex = weekIndex,
                totalWeeks = totalWeeks,
                weekStartMonday = weekMonday,
                weekEndSunday = weekSunday,
                phase = phase,
                isDeloadWeek = isDeloadWeek,
                isRaceWeek = isRaceWeek,
                totalTargetDistanceMeters = if (isRaceWeek) (weeklyMeters + raceEvent.distance.meters) else weeklyMeters,
                days = days
            )
        }
    }

    private fun generateStandardFourWeekPlan(
        baseVolumeKm: Double,
        daysPerWeek: Int,
        targets: PredictedTargets,
        currentMonday: LocalDate
    ): List<PeriodizedPlanWeek> {
        val volumes = PlanGenerator.calculateWeeklyVolumes(baseVolumeKm)
        return volumes.mapIndexed { index, volKm ->
            val weekIndex = index + 1
            val weekMonday = currentMonday.plusWeeks((weekIndex - 1).toLong())
            val weekSunday = weekMonday.plusDays(6)
            val isDeload = weekIndex == 4
            val phase = if (isDeload) TrainingPhase.TAPER else TrainingPhase.BASE
            val meters = volKm * 1000.0

            val days = when (daysPerWeek) {
                2 -> listOf(
                    PlanDayItem(
                        dayOfWeek = DayOfWeek.TUESDAY,
                        type = WorkoutType.TEMPO,
                        targetDistanceMeters = roundTo500m(meters * 0.40),
                        minPaceSec = targets.tempoPaceRange.first,
                        maxPaceSec = targets.tempoPaceRange.second,
                        targetHrZone = "Z3 (141-160 bpm)"
                    ),
                    PlanDayItem(
                        dayOfWeek = DayOfWeek.SATURDAY,
                        type = WorkoutType.LONG_RUN,
                        targetDistanceMeters = roundTo500m(meters * 0.60),
                        minPaceSec = targets.longRunPaceRange.first,
                        maxPaceSec = targets.longRunPaceRange.second,
                        targetHrZone = "Z2 (118-140 bpm)"
                    )
                )
                4 -> listOf(
                    PlanDayItem(DayOfWeek.TUESDAY, WorkoutType.EASY, roundTo500m(meters * 0.22), targets.easyPaceRange.first, targets.easyPaceRange.second, "Z2 (118-140 bpm)"),
                    PlanDayItem(DayOfWeek.THURSDAY, if (isDeload) WorkoutType.TEMPO else WorkoutType.INTERVALS, roundTo500m(meters * 0.25), targets.tempoPaceRange.first, targets.tempoPaceRange.second, "Z3-Z4"),
                    PlanDayItem(DayOfWeek.SATURDAY, WorkoutType.EASY, roundTo500m(meters * 0.18), targets.easyPaceRange.first, targets.easyPaceRange.second, "Z1-Z2 (<140 bpm)"),
                    PlanDayItem(DayOfWeek.SUNDAY, WorkoutType.LONG_RUN, roundTo500m(meters * 0.35), targets.longRunPaceRange.first, targets.longRunPaceRange.second, "Z2 (118-140 bpm)")
                )
                else -> listOf(
                    PlanDayItem(DayOfWeek.TUESDAY, WorkoutType.EASY, roundTo500m(meters * 0.30), targets.easyPaceRange.first, targets.easyPaceRange.second, "Z2 (118-140 bpm)"),
                    PlanDayItem(DayOfWeek.THURSDAY, if (isDeload) WorkoutType.TEMPO else WorkoutType.INTERVALS, roundTo500m(meters * 0.25), targets.tempoPaceRange.first, targets.tempoPaceRange.second, "Z3-Z4"),
                    PlanDayItem(DayOfWeek.SATURDAY, WorkoutType.LONG_RUN, roundTo500m(meters * 0.45), targets.longRunPaceRange.first, targets.longRunPaceRange.second, "Z2 (118-140 bpm)")
                )
            }

            PeriodizedPlanWeek(
                weekIndex = weekIndex,
                totalWeeks = 4,
                weekStartMonday = weekMonday,
                weekEndSunday = weekSunday,
                phase = phase,
                isDeloadWeek = isDeload,
                isRaceWeek = false,
                totalTargetDistanceMeters = meters,
                days = days
            )
        }
    }

    private fun generateDaysForPeriodizedWeek(
        daysPerWeek: Int,
        weeklyMeters: Double,
        targets: PredictedTargets,
        phase: TrainingPhase,
        isRaceWeek: Boolean,
        raceEvent: RaceEvent?
    ): List<PlanDayItem> {
        if (isRaceWeek && raceEvent != null) {
            // 1-WEEK SHARP TAPER WORKOUTS:
            // Short shakeout runs and light stride openers to arrive primed on race day
            val raceDayOfWeek = raceEvent.raceDate.dayOfWeek
            val racePaceSec = targets.predictedPaceSeconds
            val raceDayItem = PlanDayItem(
                dayOfWeek = raceDayOfWeek,
                type = WorkoutType.LONG_RUN,
                targetDistanceMeters = raceEvent.distance.meters,
                minPaceSec = racePaceSec,
                maxPaceSec = (racePaceSec * 1.03).roundToInt(),
                targetHrZone = "Target Race Pace · ${raceEvent.raceName}"
            )

            val shakeoutDistance = roundTo500m(weeklyMeters * 0.60).coerceAtLeast(3000.0)
            val easyShakeout = PlanDayItem(
                dayOfWeek = if (raceDayOfWeek == DayOfWeek.SUNDAY) DayOfWeek.TUESDAY else DayOfWeek.MONDAY,
                type = WorkoutType.EASY,
                targetDistanceMeters = shakeoutDistance,
                minPaceSec = targets.easyPaceRange.first,
                maxPaceSec = targets.easyPaceRange.second,
                targetHrZone = "Z1-Z2 Light Shakeout"
            )

            val stridesDistance = roundTo500m(weeklyMeters * 0.40).coerceAtLeast(2500.0)
            val preRaceStrides = PlanDayItem(
                dayOfWeek = if (raceDayOfWeek == DayOfWeek.SUNDAY) DayOfWeek.THURSDAY else DayOfWeek.WEDNESDAY,
                type = WorkoutType.TEMPO,
                targetDistanceMeters = stridesDistance,
                minPaceSec = targets.tempoPaceRange.first,
                maxPaceSec = targets.tempoPaceRange.second,
                targetHrZone = "Short Strides / Neuromuscular Tune-up"
            )

            return listOf(easyShakeout, preRaceStrides, raceDayItem)
        }

        // Standard phase workouts
        val qualityType = when (phase) {
            TrainingPhase.PEAK -> WorkoutType.INTERVALS
            TrainingPhase.BUILD -> WorkoutType.TEMPO
            else -> WorkoutType.TEMPO
        }

        val qualityPaces = if (qualityType == WorkoutType.INTERVALS) targets.intervalPaceRange else targets.tempoPaceRange
        val qualityHr = if (qualityType == WorkoutType.INTERVALS) "Z4 (161-179 bpm)" else "Z3 (141-160 bpm)"

        return when (daysPerWeek) {
            2 -> listOf(
                PlanDayItem(DayOfWeek.TUESDAY, qualityType, roundTo500m(weeklyMeters * 0.40), qualityPaces.first, qualityPaces.second, qualityHr),
                PlanDayItem(DayOfWeek.SATURDAY, WorkoutType.LONG_RUN, roundTo500m(weeklyMeters * 0.60), targets.longRunPaceRange.first, targets.longRunPaceRange.second, "Z2 (118-140 bpm)")
            )
            4 -> listOf(
                PlanDayItem(DayOfWeek.TUESDAY, WorkoutType.EASY, roundTo500m(weeklyMeters * 0.22), targets.easyPaceRange.first, targets.easyPaceRange.second, "Z2 (118-140 bpm)"),
                PlanDayItem(DayOfWeek.THURSDAY, qualityType, roundTo500m(weeklyMeters * 0.25), qualityPaces.first, qualityPaces.second, qualityHr),
                PlanDayItem(DayOfWeek.SATURDAY, WorkoutType.EASY, roundTo500m(weeklyMeters * 0.18), targets.easyPaceRange.first, targets.easyPaceRange.second, "Z1-Z2 (<140 bpm)"),
                PlanDayItem(DayOfWeek.SUNDAY, WorkoutType.LONG_RUN, roundTo500m(weeklyMeters * 0.35), targets.longRunPaceRange.first, targets.longRunPaceRange.second, "Z2 (118-140 bpm)")
            )
            else -> listOf(
                PlanDayItem(DayOfWeek.TUESDAY, WorkoutType.EASY, roundTo500m(weeklyMeters * 0.30), targets.easyPaceRange.first, targets.easyPaceRange.second, "Z2 (118-140 bpm)"),
                PlanDayItem(DayOfWeek.THURSDAY, qualityType, roundTo500m(weeklyMeters * 0.25), qualityPaces.first, qualityPaces.second, qualityHr),
                PlanDayItem(DayOfWeek.SATURDAY, WorkoutType.LONG_RUN, roundTo500m(weeklyMeters * 0.45), targets.longRunPaceRange.first, targets.longRunPaceRange.second, "Z2 (118-140 bpm)")
            )
        }
    }

    private fun roundTo500m(meters: Double): Double {
        return (meters / 500.0).roundToInt() * 500.0
    }
}
