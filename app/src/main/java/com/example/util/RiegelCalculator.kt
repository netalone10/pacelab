package com.example.util

import kotlin.math.pow
import kotlin.math.roundToInt

data class PredictedTargets(
    val targetDistanceMeters: Double,
    val predictedDurationSeconds: Long,
    val predictedPaceSeconds: Int,
    val easyPaceRange: Pair<Int, Int>,      // (faster, slower) sec/km e.g. 115% - 125%
    val longRunPaceRange: Pair<Int, Int>,   // 110% - 120%
    val tempoPaceRange: Pair<Int, Int>,     // 102% - 107%
    val intervalPaceRange: Pair<Int, Int>,  // 95% - 100%
    val repetitionPaceRange: Pair<Int, Int> = Pair(
        (predictedPaceSeconds * 0.88).roundToInt(),
        (predictedPaceSeconds * 0.94).roundToInt()
    )
)

object RiegelCalculator {

    private const val RIEGEL_EXPONENT = 1.06

    /**
     * Training paces are anchored to the predicted 10K race pace, whatever the goal distance is.
     * Easy, tempo and interval work depends on current fitness, not on the race being trained for,
     * so switching the goal from 10K to a half marathon must not slow down every training zone.
     */
    const val ZONE_REFERENCE_METERS = 10000.0

    /**
     * Calculates predicted race time using Riegel's formula:
     * T2 = T1 * (D2 / D1) ^ 1.06
     */
    fun predictRaceTimeSeconds(
        refDistanceMeters: Double,
        refDurationSeconds: Long,
        targetDistanceMeters: Double
    ): Long {
        if (refDistanceMeters <= 0.0 || refDurationSeconds <= 0L || targetDistanceMeters <= 0.0) {
            return 0L
        }
        val distanceRatio = targetDistanceMeters / refDistanceMeters
        val predictedSeconds = refDurationSeconds.toDouble() * distanceRatio.pow(RIEGEL_EXPONENT)
        return predictedSeconds.roundToInt().toLong()
    }

    /** Predicted pace in seconds per kilometre for [distanceMeters], rounded to whole seconds. */
    private fun predictedPaceSeconds(
        refDistanceMeters: Double,
        refDurationSeconds: Long,
        distanceMeters: Double
    ): Int {
        val km = distanceMeters / 1000.0
        if (km <= 0.0) return 0
        val duration = predictRaceTimeSeconds(refDistanceMeters, refDurationSeconds, distanceMeters)
        return (duration.toDouble() / km).roundToInt()
    }

    /**
     * Computes the predicted race result for the target distance and the training pace ranges.
     * The race prediction follows [targetDistanceMeters]; the training zones follow the predicted
     * 10K pace ([ZONE_REFERENCE_METERS]) so they stay stable when the goal distance changes.
     */
    fun computeTargets(
        refDistanceMeters: Double,
        refDurationSeconds: Long,
        targetDistanceMeters: Double
    ): PredictedTargets {
        val predictedDuration = predictRaceTimeSeconds(refDistanceMeters, refDurationSeconds, targetDistanceMeters)
        val targetPaceSec = predictedPaceSeconds(refDistanceMeters, refDurationSeconds, targetDistanceMeters)
        val zonePaceSec = predictedPaceSeconds(refDistanceMeters, refDurationSeconds, ZONE_REFERENCE_METERS)

        // In running, higher seconds/km = slower pace. All ranges are a share of 10K race pace.
        // Easy run: 115% to 125%
        val easyMin = (zonePaceSec * 1.15).roundToInt()
        val easyMax = (zonePaceSec * 1.25).roundToInt()

        // Long run: 110% to 120%
        val longMin = (zonePaceSec * 1.10).roundToInt()
        val longMax = (zonePaceSec * 1.20).roundToInt()

        // Tempo / Threshold: 102% to 107%
        val tempoMin = (zonePaceSec * 1.02).roundToInt()
        val tempoMax = (zonePaceSec * 1.07).roundToInt()

        // Interval / VO2max: 95% to 100%
        val intervalMin = (zonePaceSec * 0.95).roundToInt()
        val intervalMax = (zonePaceSec * 1.00).roundToInt()

        // Repetition / Speed: 88% to 94%
        val repMin = (zonePaceSec * 0.88).roundToInt()
        val repMax = (zonePaceSec * 0.94).roundToInt()

        return PredictedTargets(
            targetDistanceMeters = targetDistanceMeters,
            predictedDurationSeconds = predictedDuration,
            predictedPaceSeconds = targetPaceSec,
            easyPaceRange = Pair(easyMin, easyMax),
            longRunPaceRange = Pair(longMin, longMax),
            tempoPaceRange = Pair(tempoMin, tempoMax),
            intervalPaceRange = Pair(intervalMin, intervalMax),
            repetitionPaceRange = Pair(repMin, repMax)
        )
    }
}
