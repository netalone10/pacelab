package com.example.data.model

import com.example.util.HrZoneDistribution
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate

data class RunSession(
    val id: String,
    val startTime: Instant,
    val endTime: Instant,
    val distanceMeters: Double,
    val durationSeconds: Long,
    val avgPaceSeconds: Int,
    val avgHeartRate: Int? = null,
    val maxHeartRate: Int? = null,
    val totalCalories: Double? = null,
    val elevationGainedMeters: Double? = null,
    val isSeed: Boolean = false,
    val isDuplicate: Boolean = false,
    /**
     * Encoded bpm -> seconds histogram built from the run's real heart rate samples.
     * Null means the run has not been processed yet, an empty string means it has no HR data.
     */
    val hrHistogram: String? = null
)

data class RunSplit(
    val kilometer: Int,
    val splitSeconds: Int,
    val avgHeartRate: Int?,
    /** Below 1000 only for the final partial split; [splitSeconds] then covers this distance. */
    val distanceMeters: Double = 1000.0
) {
    /** Seconds per kilometre, so a partial split compares fairly with full ones. */
    val paceSeconds: Int
        get() = (splitSeconds * 1000.0 / distanceMeters).toInt()
}

data class HeartRatePoint(
    val offsetSeconds: Long,
    val bpm: Int
)

data class WeeklySummary(
    val weekStartMonday: LocalDate,
    val weekEndSunday: LocalDate,
    val totalDistanceMeters: Double,
    val runCount: Int,
    val totalDurationSeconds: Long,
    val avgPaceSeconds: Int,
    val avgHeartRate: Int?,
    val dailyDistanceKm: Map<DayOfWeek, Double>,
    val hrDistribution: HrZoneDistribution,
    val comparisonPercentVsPrevious: Double? // e.g. +8.4 or -3.2
)

enum class RaceDistance(val label: String, val meters: Double) {
    FIVE_K("5K", 5000.0),
    TEN_K("10K", 10000.0),
    FIFTEEN_K("15K", 15000.0),
    HALF_MARATHON("21K Half", 21097.5)
}

data class RaceEvent(
    val id: Int = 1,
    val raceName: String,
    val raceDate: LocalDate,
    val distance: RaceDistance,
    val targetDurationSeconds: Long? = null,
    val targetPaceSeconds: Int? = null,
    val location: String? = null
)

data class UserSettings(
    val refDistanceMeters: Double = 5310.0, // 5.31 km default
    val refDurationSeconds: Long = 2157L,   // 35:57 (35*60 + 57 = 2157 sec) default
    val refAvgHeartRate: Int = 151,        // 151 bpm default
    val vo2Max: Double = 48.0,              // ~48 default
    val targetRaceDistance: RaceDistance = RaceDistance.TEN_K,
    val trainingDaysPerWeek: Int = 3,       // 3 days/week default
    val z1Max: Int = 117,                  // < 118
    val z2Min: Int = 118,
    val z2Max: Int = 140,                  // 118 - 140
    val z3Min: Int = 141,
    val z3Max: Int = 160,                  // 141 - 160
    val z4Min: Int = 161,
    val z4Max: Int = 179,                  // 161 - 179
    val maxHr: Int = 190
)
