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
    val intervalPaceRange: Pair<Int, Int>   // 95% - 100%
)

object RiegelCalculator {

    private const val RIEGEL_EXPONENT = 1.06

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

    /**
     * Computes the complete predicted target and training pace ranges for a given target distance.
     */
    fun computeTargets(
        refDistanceMeters: Double,
        refDurationSeconds: Long,
        targetDistanceMeters: Double
    ): PredictedTargets {
        val predictedDuration = predictRaceTimeSeconds(refDistanceMeters, refDurationSeconds, targetDistanceMeters)
        val targetKm = targetDistanceMeters / 1000.0
        val targetPaceSec = if (targetKm > 0.0) (predictedDuration.toDouble() / targetKm).roundToInt() else 0

        // In running, higher seconds/km = slower pace.
        // Easy run: 115% to 125% of 10K race pace
        val easyMin = (targetPaceSec * 1.15).roundToInt()
        val easyMax = (targetPaceSec * 1.25).roundToInt()

        // Long run: 110% to 120%
        val longMin = (targetPaceSec * 1.10).roundToInt()
        val longMax = (targetPaceSec * 1.20).roundToInt()

        // Tempo / Threshold: 102% to 107%
        val tempoMin = (targetPaceSec * 1.02).roundToInt()
        val tempoMax = (targetPaceSec * 1.07).roundToInt()

        // Interval / VO2max: 95% to 100%
        val intervalMin = (targetPaceSec * 0.95).roundToInt()
        val intervalMax = (targetPaceSec * 1.00).roundToInt()

        return PredictedTargets(
            targetDistanceMeters = targetDistanceMeters,
            predictedDurationSeconds = predictedDuration,
            predictedPaceSeconds = targetPaceSec,
            easyPaceRange = Pair(easyMin, easyMax),
            longRunPaceRange = Pair(longMin, longMax),
            tempoPaceRange = Pair(tempoMin, tempoMax),
            intervalPaceRange = Pair(intervalMin, intervalMax)
        )
    }
}
