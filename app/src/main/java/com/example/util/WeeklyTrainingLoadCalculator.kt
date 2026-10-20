package com.example.util

import com.example.data.model.RunSession
import com.example.data.model.UserSettings
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

enum class TrainingLoadStatus(val label: String, val description: String) {
    OPTIMAL("Optimal", "Peningkatan beban aman dan memicu adaptasi kebugaran yang ideal."),
    MAINTAINING("Stabil (Maintaining)", "Beban stabil, menjaga kapasitas aerobik dan kebugaran yang sudah ada."),
    OVERREACHING("Tinggi (Overreaching)", "Lonjakan beban cukup tinggi. Perhatikan tanda kelelahan otot."),
    RECOVERY("Pemulihan (Deload)", "Beban lebih rendah, memberikan waktu regenerasi otot dan glikogen."),
    INSUFFICIENT("Belum Ada Data", "Belum ada volume latihan yang cukup pada periode ini.")
}

data class WeeklyTrainingLoadItem(
    val weekMonday: LocalDate,
    val weekSunday: LocalDate,
    val weekIndex: Int, // 1 (4 weeks ago) to 4 (current / reference week)
    val weekLabel: String, // e.g. "W1", "W2", "W3", "Current"
    val dateRangeLabel: String, // e.g. "Sep 29 - Oct 5"
    val loadScore: Int,
    val distanceKm: Double,
    val durationMinutes: Long,
    val sessionCount: Int,
    val avgHeartRate: Int?,
    val deltaPercentVsPrevious: Double?,
    val status: TrainingLoadStatus,
    val isCurrentWeek: Boolean
)

data class FourWeekTrainingLoadSummary(
    val weeks: List<WeeklyTrainingLoadItem>, // exactly 4 items, oldest to newest
    val currentWeekLoad: Int,
    val fourWeekAverageLoad: Int,
    val acuteChronicRatio: Double?, // Acute (current week) / Chronic (past 3 weeks avg)
    val progressionPercent: Double?, // current week vs previous week %
    val status: TrainingLoadStatus,
    val feedbackMessage: String
)

/**
 * Calculates Weekly Training Load from Health Connect running session data
 * and analyzes progression over the last 4 weeks.
 *
 * Uses heart rate zone distribution (TRIMP-weighted) when available,
 * falling back gracefully to session average HR or pace-distance volume.
 */
object WeeklyTrainingLoadCalculator {

    private val shortDateFormatter = DateTimeFormatter.ofPattern("d MMM", Locale.US)

    /**
     * Compute training load points for a single session.
     */
    fun calculateSessionLoad(session: RunSession, zones: HrZones): Double {
        if (session.isDuplicate) return 0.0

        // 1. Try real HR histogram
        val histogram = HrHistogram.decode(session.hrHistogram)
        if (histogram.isNotEmpty()) {
            val dist = HrHistogram.bucket(histogram, zones)
            val z1Min = dist.z1Seconds / 60.0
            val z2Min = dist.z2Seconds / 60.0
            val z3Min = dist.z3Seconds / 60.0
            val z4Min = dist.z4Seconds / 60.0
            val z5Min = dist.z5Seconds / 60.0
            return (z1Min * 1.0) + (z2Min * 1.5) + (z3Min * 2.2) + (z4Min * 3.2) + (z5Min * 4.5)
        }

        // 2. Average Heart Rate weighting
        val avgHr = session.avgHeartRate
        val durationMin = session.durationSeconds / 60.0
        if (avgHr != null && avgHr > 0) {
            val zone = HrZoneCalculator.determineZone(avgHr, zones)
            val factor = when (zone) {
                1 -> 1.0
                2 -> 1.5
                3 -> 2.2
                4 -> 3.2
                else -> 4.5
            }
            return durationMin * factor
        }

        // 3. Fallback: volume & duration load
        val distKm = session.distanceMeters / 1000.0
        return (distKm * 8.0) + (durationMin * 0.8)
    }

    /**
     * Computes the 4-week progression of Weekly Training Load ending on [anchorMonday].
     */
    fun compute4WeekProgression(
        allSessions: List<RunSession>,
        anchorMonday: LocalDate,
        userSettings: UserSettings,
        zone: ZoneId = ZoneId.systemDefault()
    ): FourWeekTrainingLoadSummary {
        val nonDuplicateSessions = allSessions.filter { !it.isDuplicate }
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

        val mondayList = listOf(
            anchorMonday.minusWeeks(3),
            anchorMonday.minusWeeks(2),
            anchorMonday.minusWeeks(1),
            anchorMonday
        )

        val items = ArrayList<WeeklyTrainingLoadItem>(4)
        var previousWeekLoad: Int? = null

        for ((index, mon) in mondayList.withIndex()) {
            val sun = mon.plusDays(6)
            val startInstant = mon.atStartOfDay(zone).toInstant()
            val endInstant = sun.plusDays(1).atStartOfDay(zone).toInstant()

            val weekSessions = nonDuplicateSessions.filter {
                it.startTime >= startInstant && it.startTime < endInstant
            }

            val totalLoad = weekSessions.sumOf { calculateSessionLoad(it, zones) }.roundToInt()
            val totalDistanceKm = weekSessions.sumOf { it.distanceMeters } / 1000.0
            val totalDurationMin = weekSessions.sumOf { it.durationSeconds } / 60L
            val runCount = weekSessions.size

            val hrSessions = weekSessions.filter { it.avgHeartRate != null }
            val hrDuration = hrSessions.sumOf { it.durationSeconds }
            val avgHr = if (hrDuration > 0L) {
                val weighted = hrSessions.sumOf { (it.avgHeartRate ?: 0).toLong() * it.durationSeconds }
                (weighted.toDouble() / hrDuration.toDouble()).roundToInt()
            } else null

            val deltaPercent: Double? = if (previousWeekLoad != null && previousWeekLoad > 0) {
                ((totalLoad - previousWeekLoad.toDouble()) / previousWeekLoad.toDouble()) * 100.0
            } else null

            val isCurrent = (index == 3)
            val weekLabel = when (index) {
                0 -> "W-3"
                1 -> "W-2"
                2 -> "W-1"
                else -> "Minggu Ini"
            }
            val dateRangeStr = "${mon.format(shortDateFormatter)} - ${sun.format(shortDateFormatter)}"

            val weekStatus = when {
                totalLoad == 0 -> TrainingLoadStatus.INSUFFICIENT
                deltaPercent == null -> TrainingLoadStatus.OPTIMAL
                deltaPercent > 25.0 -> TrainingLoadStatus.OVERREACHING
                deltaPercent < -20.0 -> TrainingLoadStatus.RECOVERY
                deltaPercent in 0.0..18.0 -> TrainingLoadStatus.OPTIMAL
                else -> TrainingLoadStatus.MAINTAINING
            }

            items.add(
                WeeklyTrainingLoadItem(
                    weekMonday = mon,
                    weekSunday = sun,
                    weekIndex = index + 1,
                    weekLabel = weekLabel,
                    dateRangeLabel = dateRangeStr,
                    loadScore = totalLoad,
                    distanceKm = totalDistanceKm,
                    durationMinutes = totalDurationMin,
                    sessionCount = runCount,
                    avgHeartRate = avgHr,
                    deltaPercentVsPrevious = deltaPercent,
                    status = weekStatus,
                    isCurrentWeek = isCurrent
                )
            )

            previousWeekLoad = totalLoad
        }

        val currentLoad = items.last().loadScore
        val pastThree = items.take(3)
        val pastThreeWithData = pastThree.filter { it.loadScore > 0 }
        val chronicAvg = if (pastThreeWithData.isNotEmpty()) {
            pastThreeWithData.map { it.loadScore }.average()
        } else null

        val acwr: Double? = if (chronicAvg != null && chronicAvg > 0) {
            currentLoad / chronicAvg
        } else null

        val w3Load = items[2].loadScore
        val progPercent: Double? = if (w3Load > 0) {
            ((currentLoad - w3Load.toDouble()) / w3Load.toDouble()) * 100.0
        } else null

        val overallStatus = when {
            currentLoad == 0 -> TrainingLoadStatus.INSUFFICIENT
            acwr != null && acwr > 1.45 -> TrainingLoadStatus.OVERREACHING
            acwr != null && acwr < 0.75 -> TrainingLoadStatus.RECOVERY
            progPercent != null && progPercent > 25.0 -> TrainingLoadStatus.OVERREACHING
            progPercent != null && progPercent < -20.0 -> TrainingLoadStatus.RECOVERY
            progPercent != null && progPercent in -5.0..20.0 -> TrainingLoadStatus.OPTIMAL
            else -> TrainingLoadStatus.MAINTAINING
        }

        val feedback = when (overallStatus) {
            TrainingLoadStatus.OPTIMAL -> {
                val pctStr = progPercent?.let { String.format(Locale.US, "%+.1f%%", it) } ?: "stabil"
                "Progres latihan seimbang ($pctStr vs minggu lalu). Rasio beban berada di zona aman untuk adaptasi aerobik optimal."
            }
            TrainingLoadStatus.OVERREACHING -> {
                val pctStr = progPercent?.let { String.format(Locale.US, "%+.1f%%", it) } ?: "+25%+"
                "Peningkatan beban cukup tajam ($pctStr). Waspadai kelelahan otot berlebih, prioritaskan tidur dan lari recovery Zone 2."
            }
            TrainingLoadStatus.RECOVERY -> {
                val pctStr = progPercent?.let { String.format(Locale.US, "%.1f%%", it) } ?: "-20%"
                "Beban latihan berkurang ($pctStr). Periode deload/pemulihan yang bagus untuk regenerasi jaringan otot."
            }
            TrainingLoadStatus.MAINTAINING -> {
                "Volume latihan konsisten dan stabil. Kebugaran aerobik dasar terjaga dengan baik."
            }
            TrainingLoadStatus.INSUFFICIENT -> {
                "Belum ada aktivitas lari tercatat untuk minggu ini. Selesaikan sesi lari untuk mulai membangun ritme latihan."
            }
        }

        val fourWeekAvg = items.map { it.loadScore }.average().roundToInt()

        return FourWeekTrainingLoadSummary(
            weeks = items,
            currentWeekLoad = currentLoad,
            fourWeekAverageLoad = fourWeekAvg,
            acuteChronicRatio = acwr,
            progressionPercent = progPercent,
            status = overallStatus,
            feedbackMessage = feedback
        )
    }
}
