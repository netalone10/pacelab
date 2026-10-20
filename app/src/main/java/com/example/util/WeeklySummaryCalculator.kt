package com.example.util

import com.example.data.model.RunSession
import com.example.data.model.UserSettings
import com.example.data.model.WeeklySummary
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToInt

/**
 * Pure calculation of a Monday to Sunday summary. Duplicate sessions (the same run recorded by
 * two trackers) never count, and heart rate zones come from real per-session histograms.
 */
object WeeklySummaryCalculator {

    fun compute(
        allSessions: List<RunSession>,
        weekStartMonday: LocalDate,
        userSettings: UserSettings,
        zone: ZoneId = ZoneId.systemDefault()
    ): WeeklySummary {
        val sessions = allSessions.filter { !it.isDuplicate }

        val weekEndSunday = weekStartMonday.plusDays(6)
        val weekStartInstant = weekStartMonday.atStartOfDay(zone).toInstant()
        val weekEndInstant = weekEndSunday.plusDays(1).atStartOfDay(zone).toInstant()

        val weekSessions = sessions.filter {
            it.startTime >= weekStartInstant && it.startTime < weekEndInstant
        }

        val totalDistMeters = weekSessions.sumOf { it.distanceMeters }
        val totalDurationSec = weekSessions.sumOf { it.durationSeconds }
        val runCount = weekSessions.size

        val avgPace = if (totalDistMeters > 0) {
            PaceUtils.calculatePaceSeconds(totalDistMeters, totalDurationSec)
        } else 0

        // Duration weighted so a 10 minute jog does not count as much as a 90 minute long run.
        val hrSessions = weekSessions.filter { it.avgHeartRate != null }
        val hrDuration = hrSessions.sumOf { it.durationSeconds }
        val avgHr = if (hrDuration > 0L) {
            val weighted = hrSessions.sumOf { (it.avgHeartRate ?: 0).toLong() * it.durationSeconds }
            (weighted.toDouble() / hrDuration.toDouble()).roundToInt()
        } else null

        val dailyMap = mutableMapOf<DayOfWeek, Double>()
        DayOfWeek.values().forEach { dailyMap[it] = 0.0 }
        weekSessions.forEach { session ->
            val day = session.startTime.atZone(zone).dayOfWeek
            dailyMap[day] = (dailyMap[day] ?: 0.0) + (session.distanceMeters / 1000.0)
        }

        val prevWeekStartInstant = weekStartMonday.minusWeeks(1).atStartOfDay(zone).toInstant()
        val prevDist = sessions
            .filter { it.startTime >= prevWeekStartInstant && it.startTime < weekStartInstant }
            .sumOf { it.distanceMeters }
        val diffPercent = if (prevDist > 0) {
            ((totalDistMeters - prevDist) / prevDist) * 100.0
        } else null

        val zones = HrZones(
            z1Max = userSettings.z1Max,
            z2Min = userSettings.z2Min,
            z2Max = userSettings.z2Max,
            z3Min = userSettings.z3Min,
            z3Max = userSettings.z3Max,
            z4Min = userSettings.z4Min,
            z4Max = userSettings.z4Max,
            maxHr = userSettings.maxHr
        )

        // Seconds per zone, index 1..5. A run without recorded heart rate samples adds nothing;
        // only demo sessions fall back to a single zone taken from their average.
        val zoneSeconds = LongArray(6)
        weekSessions.forEach { session ->
            val histogram = HrHistogram.decode(session.hrHistogram)
            if (histogram.isNotEmpty()) {
                val d = HrHistogram.bucket(histogram, zones)
                zoneSeconds[1] += d.z1Seconds
                zoneSeconds[2] += d.z2Seconds
                zoneSeconds[3] += d.z3Seconds
                zoneSeconds[4] += d.z4Seconds
                zoneSeconds[5] += d.z5Seconds
            } else if (session.isSeed) {
                val demoZone = HrZoneCalculator.determineZone(session.avgHeartRate ?: 140, zones)
                zoneSeconds[demoZone] += session.durationSeconds
            }
        }

        return WeeklySummary(
            weekStartMonday = weekStartMonday,
            weekEndSunday = weekEndSunday,
            totalDistanceMeters = totalDistMeters,
            runCount = runCount,
            totalDurationSeconds = totalDurationSec,
            avgPaceSeconds = avgPace,
            avgHeartRate = avgHr,
            dailyDistanceKm = dailyMap,
            hrDistribution = HrZoneDistribution(
                z1Seconds = zoneSeconds[1],
                z2Seconds = zoneSeconds[2],
                z3Seconds = zoneSeconds[3],
                z4Seconds = zoneSeconds[4],
                z5Seconds = zoneSeconds[5]
            ),
            comparisonPercentVsPrevious = diffPercent
        )
    }
}
