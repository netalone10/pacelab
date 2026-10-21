package com.example.util

import com.example.data.model.RunSession
import com.example.data.model.UserSettings
import kotlin.math.roundToInt

/** The user's configured heart rate thresholds as [HrZones]. */
fun UserSettings.toHrZones(): HrZones = HrZones(
    z1Max = z1Max,
    z2Min = z2Min,
    z2Max = z2Max,
    z3Min = z3Min,
    z3Max = z3Max,
    z4Min = z4Min,
    z4Max = z4Max,
    maxHr = maxHr
)

/**
 * Flags runs that were slow on pace but hard on the heart: an easy day that ran hot (heat, fatigue,
 * or simply going out too fast) costs recovery and takes the edge off the quality days.
 *
 * A run counts as easy-intent when its average pace is slower than the tempo zone. Faster runs are
 * treated as deliberate hard efforts and are never flagged. The effort is read from the stored
 * heart rate histogram, so it follows the user's current zone thresholds.
 */
object EasyRunCheck {

    /** More than this share of a run above Z2 makes an easy-intent run "too hard". */
    const val MAX_ABOVE_Z2_PERCENT = 25

    /** Shorter runs are too noisy to judge (warm-up heart rate lag dominates). */
    const val MIN_DURATION_SECONDS = 15 * 60L

    data class Result(
        /** Share of recorded heart rate time spent in Z3 or higher, 0 to 100. */
        val aboveZ2Percent: Int,
        /** True when the average pace is slower than the tempo zone. */
        val easyIntent: Boolean
    ) {
        val tooHard: Boolean
            get() = easyIntent && aboveZ2Percent > MAX_ABOVE_Z2_PERCENT
    }

    /** Null when the run is too short or has no heart rate data to judge. */
    fun assess(session: RunSession, zones: HrZones, targets: PredictedTargets): Result? {
        if (session.durationSeconds < MIN_DURATION_SECONDS) return null
        val histogram = HrHistogram.decode(session.hrHistogram)
        if (histogram.isEmpty()) return null

        val distribution = HrHistogram.bucket(histogram, zones)
        val total = distribution.totalSeconds
        if (total <= 0L) return null

        val above = distribution.z3Seconds + distribution.z4Seconds + distribution.z5Seconds
        return Result(
            aboveZ2Percent = (above * 100.0 / total).roundToInt(),
            easyIntent = session.avgPaceSeconds >= targets.tempoPaceRange.second
        )
    }
}
