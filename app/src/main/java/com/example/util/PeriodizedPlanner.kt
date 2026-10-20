package com.example.util

import com.example.data.model.RaceEvent
import com.example.data.model.RunSession
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
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

    /** A plan never spans more than this many weeks; farther races only plan the final block. */
    const val MAX_PLAN_WEEKS = 16

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
     * Starting weekly volume for a plan: the average of the completed weeks in the last four
     * weeks of real training (current, unfinished week excluded). The plan therefore starts from
     * what the runner actually does and stays stable while the current week fills up.
     */
    fun baseWeeklyVolumeKm(
        sessions: List<RunSession>,
        planMonday: LocalDate,
        daysPerWeek: Int,
        zone: ZoneId = ZoneId.systemDefault()
    ): Double {
        val fallback = daysPerWeek.coerceIn(2, 4) * 4.0
        if (sessions.isEmpty()) return fallback

        val windowStart = planMonday.minusWeeks(4)
        val inWindow = sessions
            .map { it.startTime.atZone(zone).toLocalDate() to it.distanceMeters }
            .filter { !it.first.isBefore(windowStart) && it.first.isBefore(planMonday) }
        if (inWindow.isEmpty()) return fallback

        val firstMonday = inWindow.minOf { it.first }.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val weeks = ChronoUnit.WEEKS.between(firstMonday, planMonday).toInt().coerceIn(1, 4)
        val totalKm = inWindow.sumOf { it.second } / 1000.0
        return (totalKm / weeks).coerceAtLeast(5.0)
    }

    /** Starting volume plus a human readable reason when it was adjusted. */
    data class VolumeAdvice(val baseKm: Double, val note: String?)

    /**
     * Adapts the starting volume to what actually happened recently:
     * - no run in the last 14 days: restart at 60% of the recent average
     * - last full week far below average (under 50%): come back at 75%
     * - last full week far above average (over 125%): hold at the average, no extra progression
     * Otherwise the plain recent average is used.
     */
    fun adaptiveBaseVolume(
        sessions: List<RunSession>,
        planMonday: LocalDate,
        daysPerWeek: Int,
        today: LocalDate = LocalDate.now(),
        zone: ZoneId = ZoneId.systemDefault()
    ): VolumeAdvice {
        val avg = baseWeeklyVolumeKm(sessions, planMonday, daysPerWeek, zone)
        if (sessions.isEmpty()) return VolumeAdvice(avg, null)

        val dates = sessions.map { it.startTime.atZone(zone).toLocalDate() }
        val lastRun = dates.max()
        val daysSinceLast = ChronoUnit.DAYS.between(lastRun, today)
        if (daysSinceLast >= 14) {
            return VolumeAdvice(
                (avg * 0.60).coerceAtLeast(5.0),
                "Sudah $daysSinceLast hari tanpa lari. Volume mulai dari 60% rata-rata terakhir."
            )
        }

        val lastWeekStart = planMonday.minusWeeks(1)
        val lastWeekKm = sessions
            .filter {
                val d = it.startTime.atZone(zone).toLocalDate()
                !d.isBefore(lastWeekStart) && d.isBefore(planMonday)
            }
            .sumOf { it.distanceMeters } / 1000.0
        val hasHistory = dates.any { it.isBefore(lastWeekStart) }

        return when {
            hasHistory && lastWeekKm < avg * 0.5 -> VolumeAdvice(
                (avg * 0.75).coerceAtLeast(5.0),
                String.format(java.util.Locale.US, "Minggu lalu cuma %.1f km (rata-rata %.1f km). Volume diturunkan 25%% biar masuk lagi pelan.", lastWeekKm, avg)
            )
            hasHistory && lastWeekKm > avg * 1.25 -> VolumeAdvice(
                avg,
                String.format(java.util.Locale.US, "Minggu lalu %.1f km, lonjakan di atas rata-rata %.1f km. Volume ditahan, tidak naik dulu.", lastWeekKm, avg)
            )
            else -> VolumeAdvice(avg, null)
        }
    }

    /**
     * Generates a periodized plan leading up to the target race date.
     *
     * Volume rules (kilometres per week):
     * - Loading weeks grow by at most 10% over the previous loading week.
     * - Every 4th week is a deload at 80% of the last loading week.
     * - The week after a deload resumes at the pre-deload volume, with no extra jump.
     * - Race week (taper) is 50% of the last loading week, plus the race itself.
     *
     * The plan is anchored to [currentMonday] (this week), or to the last [MAX_PLAN_WEEKS] weeks
     * before the race when the race is farther away. The final plan week always contains the race.
     */
    fun generatePeriodizedPlan(
        raceEvent: RaceEvent?,
        baseWeeklyVolumeKm: Double = 18.0,
        daysPerWeek: Int = 3,
        targets: PredictedTargets,
        zones: HrZones = HrZones(),
        currentMonday: LocalDate = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    ): List<PeriodizedPlanWeek> {
        val validDays = daysPerWeek.coerceIn(2, 4)
        if (raceEvent == null) {
            return generateStandardPlan(baseWeeklyVolumeKm, validDays, targets, zones, currentMonday)
        }

        val raceMonday = raceEvent.raceDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        if (raceMonday.isBefore(currentMonday)) {
            // The race is already in the past, fall back to a plain 4-week block.
            return generateStandardPlan(baseWeeklyVolumeKm, validDays, targets, zones, currentMonday)
        }

        val weeksToRace = (ChronoUnit.WEEKS.between(currentMonday, raceMonday) + 1).toInt()
        val totalWeeks = weeksToRace.coerceIn(1, MAX_PLAN_WEEKS)
        val startMonday = if (weeksToRace > MAX_PLAN_WEEKS) {
            raceMonday.minusWeeks((MAX_PLAN_WEEKS - 1).toLong())
        } else {
            currentMonday
        }

        // Highest volume the build is allowed to reach: +10% per loading week, at most 4 steps.
        val peakVolumeKm = baseWeeklyVolumeKm * Math.pow(1.10, (totalWeeks - 2).coerceIn(0, 4).toDouble())

        val weeks = ArrayList<PeriodizedPlanWeek>(totalWeeks)
        var lastLoadKm = baseWeeklyVolumeKm
        var previousWasDeload = false

        for (weekIndex in 1..totalWeeks) {
            val weekMonday = startMonday.plusWeeks((weekIndex - 1).toLong())
            val isRaceWeek = weekIndex == totalWeeks
            val isPeakWeek = weekIndex == totalWeeks - 1 && totalWeeks >= 3
            val isDeloadWeek = weekIndex % 4 == 0 && !isPeakWeek && !isRaceWeek

            val phase = when {
                isRaceWeek -> TrainingPhase.TAPER
                isPeakWeek -> TrainingPhase.PEAK
                weekIndex >= totalWeeks - 3 && totalWeeks >= 5 -> TrainingPhase.BUILD
                else -> TrainingPhase.BASE
            }

            val rawVolumeKm = when {
                isRaceWeek -> lastLoadKm * 0.50
                isDeloadWeek -> lastLoadKm * PlanGenerator.DELOAD_FACTOR
                previousWasDeload -> minOf(lastLoadKm, peakVolumeKm)
                isPeakWeek -> minOf(peakVolumeKm, lastLoadKm * PlanGenerator.MAX_PROGRESSION_RATE)
                weekIndex == 1 -> baseWeeklyVolumeKm
                else -> minOf(lastLoadKm * PlanGenerator.MAX_PROGRESSION_RATE, peakVolumeKm)
            }
            val volumeKm = (rawVolumeKm * 100).roundToInt() / 100.0

            if (!isRaceWeek && !isDeloadWeek) lastLoadKm = volumeKm
            previousWasDeload = isDeloadWeek

            val weeklyMeters = volumeKm * 1000.0
            val days = if (isRaceWeek) {
                raceWeekDays(weeklyMeters, targets, zones, raceEvent)
            } else {
                val qualityType = if (phase == TrainingPhase.PEAK && !isDeloadWeek) {
                    WorkoutType.INTERVALS
                } else {
                    WorkoutType.TEMPO
                }
                standardDays(validDays, weeklyMeters, targets, zones, qualityType)
            }

            weeks.add(
                PeriodizedPlanWeek(
                    weekIndex = weekIndex,
                    totalWeeks = totalWeeks,
                    weekStartMonday = weekMonday,
                    weekEndSunday = weekMonday.plusDays(6),
                    phase = phase,
                    isDeloadWeek = isDeloadWeek,
                    isRaceWeek = isRaceWeek,
                    totalTargetDistanceMeters = if (isRaceWeek) weeklyMeters + raceEvent.distance.meters else weeklyMeters,
                    days = days
                )
            )
        }
        return weeks
    }

    private fun generateStandardPlan(
        baseVolumeKm: Double,
        daysPerWeek: Int,
        targets: PredictedTargets,
        zones: HrZones,
        currentMonday: LocalDate
    ): List<PeriodizedPlanWeek> {
        val volumes = PlanGenerator.calculateWeeklyVolumes(baseVolumeKm)
        return volumes.mapIndexed { index, volKm ->
            val weekIndex = index + 1
            val isDeload = weekIndex == 4
            val weekMonday = currentMonday.plusWeeks(index.toLong())
            val meters = volKm * 1000.0
            val qualityType = if (isDeload) WorkoutType.TEMPO else WorkoutType.INTERVALS

            PeriodizedPlanWeek(
                weekIndex = weekIndex,
                totalWeeks = volumes.size,
                weekStartMonday = weekMonday,
                weekEndSunday = weekMonday.plusDays(6),
                phase = TrainingPhase.BASE,
                isDeloadWeek = isDeload,
                isRaceWeek = false,
                totalTargetDistanceMeters = meters,
                days = standardDays(daysPerWeek, meters, targets, zones, qualityType)
            )
        }
    }

    /**
     * Race week: a short shakeout early in the week, light openers the day before, then the race.
     * Workouts are only placed on days before the race, so nothing collides with race day.
     */
    private fun raceWeekDays(
        weeklyMeters: Double,
        targets: PredictedTargets,
        zones: HrZones,
        raceEvent: RaceEvent
    ): List<PlanDayItem> {
        val raceIndex = raceEvent.raceDate.dayOfWeek.value // Monday = 1 ... Sunday = 7
        val racePaceSec = targets.predictedPaceSeconds
        val items = ArrayList<PlanDayItem>(3)

        val shakeoutIndex = raceIndex - 3
        if (shakeoutIndex >= 1) {
            items.add(
                PlanDayItem(
                    dayOfWeek = DayOfWeek.of(shakeoutIndex),
                    type = WorkoutType.EASY,
                    targetDistanceMeters = roundTo500m(weeklyMeters * 0.60).coerceAtLeast(3000.0),
                    minPaceSec = targets.easyPaceRange.first,
                    maxPaceSec = targets.easyPaceRange.second,
                    targetHrZone = "${HrZoneCalculator.easyLabel(zones)} light shakeout"
                )
            )
        }

        val openersIndex = raceIndex - 1
        if (openersIndex >= 1) {
            items.add(
                PlanDayItem(
                    dayOfWeek = DayOfWeek.of(openersIndex),
                    type = WorkoutType.TEMPO,
                    targetDistanceMeters = roundTo500m(weeklyMeters * 0.40).coerceAtLeast(2500.0),
                    minPaceSec = targets.tempoPaceRange.first,
                    maxPaceSec = targets.tempoPaceRange.second,
                    targetHrZone = "Short strides, neuromuscular tune-up"
                )
            )
        }

        items.add(
            PlanDayItem(
                dayOfWeek = raceEvent.raceDate.dayOfWeek,
                type = WorkoutType.LONG_RUN,
                targetDistanceMeters = raceEvent.distance.meters,
                minPaceSec = racePaceSec,
                maxPaceSec = (racePaceSec * 1.03).roundToInt(),
                targetHrZone = "Target race pace, ${raceEvent.raceName}"
            )
        )
        return items
    }

    /**
     * Regular training week for 2, 3 or 4 running days. Heart rate zone text always follows the
     * thresholds the user configured, and pace ranges always match the workout type.
     */
    private fun standardDays(
        daysPerWeek: Int,
        weeklyMeters: Double,
        targets: PredictedTargets,
        zones: HrZones,
        qualityType: WorkoutType
    ): List<PlanDayItem> {
        val qualityIsIntervals = qualityType == WorkoutType.INTERVALS
        val qualityPaces = if (qualityIsIntervals) targets.intervalPaceRange else targets.tempoPaceRange
        val qualityHr = HrZoneCalculator.label(if (qualityIsIntervals) 4 else 3, zones)
        val aerobicHr = HrZoneCalculator.label(2, zones)
        val recoveryHr = HrZoneCalculator.easyLabel(zones)

        fun item(
            day: DayOfWeek,
            type: WorkoutType,
            share: Double,
            paces: Pair<Int, Int>,
            hr: String
        ) = PlanDayItem(
            dayOfWeek = day,
            type = type,
            targetDistanceMeters = roundTo500m(weeklyMeters * share),
            minPaceSec = paces.first,
            maxPaceSec = paces.second,
            targetHrZone = hr
        )

        return when (daysPerWeek) {
            2 -> listOf(
                item(DayOfWeek.TUESDAY, qualityType, 0.40, qualityPaces, qualityHr),
                item(DayOfWeek.SATURDAY, WorkoutType.LONG_RUN, 0.60, targets.longRunPaceRange, aerobicHr)
            )
            4 -> listOf(
                item(DayOfWeek.TUESDAY, WorkoutType.EASY, 0.22, targets.easyPaceRange, aerobicHr),
                item(DayOfWeek.THURSDAY, qualityType, 0.25, qualityPaces, qualityHr),
                item(DayOfWeek.SATURDAY, WorkoutType.EASY, 0.18, targets.easyPaceRange, recoveryHr),
                item(DayOfWeek.SUNDAY, WorkoutType.LONG_RUN, 0.35, targets.longRunPaceRange, aerobicHr)
            )
            else -> listOf(
                item(DayOfWeek.TUESDAY, WorkoutType.EASY, 0.30, targets.easyPaceRange, aerobicHr),
                item(DayOfWeek.THURSDAY, qualityType, 0.25, qualityPaces, qualityHr),
                item(DayOfWeek.SATURDAY, WorkoutType.LONG_RUN, 0.45, targets.longRunPaceRange, aerobicHr)
            )
        }
    }

    private fun roundTo500m(meters: Double): Double {
        return (meters / 500.0).roundToInt() * 500.0
    }
}
