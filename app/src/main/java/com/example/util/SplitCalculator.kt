package com.example.util

import com.example.data.model.RunSplit
import kotlin.math.roundToInt

/**
 * Distance covered between two moments, expressed in seconds from the start of the run.
 */
data class DistanceInterval(
    val startSec: Double,
    val endSec: Double,
    val meters: Double
)

/**
 * Builds real per-kilometre splits from the distance records Health Connect holds for a run.
 * Nothing here is simulated: when the data is too coarse to split reliably the result is empty.
 */
object SplitCalculator {

    /** Fewer distance intervals than this cannot support a meaningful per-km split. */
    private const val MIN_INTERVALS = 3

    fun compute(
        intervals: List<DistanceInterval>,
        hrSamples: List<Pair<Long, Int>>
    ): List<RunSplit> {
        val cleaned = removeOverlap(intervals)
        if (cleaned.size < MIN_INTERVALS) return emptyList()

        val totalMeters = cleaned.sumOf { it.meters }
        val fullKm = (totalMeters / 1000.0).toInt()
        if (fullKm < 1) return emptyList()

        // Moment (seconds from start) at which each full kilometre is completed.
        val crossings = DoubleArray(fullKm)
        var cumulative = 0.0
        var nextKm = 1
        for (interval in cleaned) {
            val startCumulative = cumulative
            val endCumulative = cumulative + interval.meters
            while (nextKm <= fullKm && nextKm * 1000.0 <= endCumulative) {
                val fraction = (nextKm * 1000.0 - startCumulative) / interval.meters
                crossings[nextKm - 1] =
                    interval.startSec + fraction * (interval.endSec - interval.startSec)
                nextKm++
            }
            cumulative = endCumulative
        }

        val splits = ArrayList<RunSplit>(fullKm)
        var previous = cleaned.first().startSec
        for (index in 0 until fullKm) {
            val current = crossings[index]
            val windowHr = hrSamples.filter { it.first >= previous && it.first <= current }
            val avgHr = if (windowHr.isNotEmpty()) windowHr.map { it.second }.average().roundToInt() else null
            splits.add(
                RunSplit(
                    kilometer = index + 1,
                    splitSeconds = (current - previous).roundToInt(),
                    avgHeartRate = avgHr
                )
            )
            previous = current
        }
        return splits
    }

    /**
     * Two apps can write overlapping distance records for the same run. Keep the first record
     * for any stretch of time and clip the later one, scaling its distance proportionally.
     */
    private fun removeOverlap(intervals: List<DistanceInterval>): List<DistanceInterval> {
        val sorted = intervals
            .filter { it.endSec > it.startSec && it.meters > 0.0 }
            .sortedBy { it.startSec }
        val result = ArrayList<DistanceInterval>(sorted.size)
        var lastEnd = Double.NEGATIVE_INFINITY
        for (interval in sorted) {
            val start = maxOf(interval.startSec, lastEnd)
            if (interval.endSec <= start) continue
            val fraction = (interval.endSec - start) / (interval.endSec - interval.startSec)
            result.add(DistanceInterval(start, interval.endSec, interval.meters * fraction))
            lastEnd = interval.endSec
        }
        return result
    }
}
