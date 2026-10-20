package com.example.util

import com.example.data.model.RunSession
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import kotlin.math.pow

/** Best predicted 10K time seen in the 28 days ending at the end of [weekMonday]'s week. */
data class PredictionPoint(
    val weekMonday: LocalDate,
    val predictedSeconds: Long
)

/**
 * Builds a trend of predicted race time from real runs.
 *
 * Every run of at least [MIN_DISTANCE_METERS] gives a Riegel prediction. An easy run predicts a
 * slow race, so the single best prediction inside a trailing 28 day window is used as the
 * "current fitness" estimate: it is the run where the user pushed hardest.
 */
object PredictionTrendCalculator {

    const val MIN_DISTANCE_METERS = 4800.0
    private const val RIEGEL_EXPONENT = 1.06
    private const val WINDOW_DAYS = 28L

    fun predictSeconds(session: RunSession, targetMeters: Double): Long =
        (session.durationSeconds * (targetMeters / session.distanceMeters).pow(RIEGEL_EXPONENT)).toLong()

    private fun eligible(sessions: List<RunSession>): List<RunSession> =
        sessions.filter {
            !it.isDuplicate && it.distanceMeters >= MIN_DISTANCE_METERS && it.durationSeconds > 0
        }.let { all ->
            // Demo runs never mix with real ones.
            if (all.any { !it.isSeed }) all.filter { !it.isSeed } else all
        }

    /** Weekly points for the last [weeks] weeks, oldest first. Weeks without a qualifying run are skipped. */
    fun trend(
        sessions: List<RunSession>,
        targetMeters: Double,
        today: LocalDate = LocalDate.now(),
        weeks: Int = 12,
        zone: ZoneId = ZoneId.systemDefault()
    ): List<PredictionPoint> {
        val runs = eligible(sessions)
        if (runs.isEmpty()) return emptyList()
        val thisMonday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val points = ArrayList<PredictionPoint>()
        for (i in (weeks - 1) downTo 0) {
            val monday = thisMonday.minusWeeks(i.toLong())
            val windowEnd = monday.plusDays(7).atStartOfDay(zone).toInstant()
            val windowStart = windowEnd.minusSeconds(WINDOW_DAYS * 86400L)
            val best = runs
                .filter { it.startTime >= windowStart && it.startTime < windowEnd }
                .minOfOrNull { predictSeconds(it, targetMeters) }
            if (best != null) points.add(PredictionPoint(monday, best))
        }
        return points
    }

    /** The run with the fastest predicted race inside the last [days] days, or null. */
    fun bestRecentRun(
        sessions: List<RunSession>,
        targetMeters: Double,
        now: Instant = Instant.now(),
        days: Long = 84
    ): RunSession? {
        val cutoff = now.minusSeconds(days * 86400L)
        return eligible(sessions)
            .filter { it.startTime.isAfter(cutoff) }
            .minByOrNull { predictSeconds(it, targetMeters) }
    }
}
