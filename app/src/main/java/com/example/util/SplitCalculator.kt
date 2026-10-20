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
 * Cumulative distance reached at [sec] seconds from the start of the run.
 * A list of these must be sorted by time with non-decreasing [meters].
 */
data class DistancePoint(
    val sec: Double,
    val meters: Double
)

/**
 * Builds real per-kilometre splits from distance data Health Connect holds for a run.
 * Nothing here is simulated: when the data is too coarse to split reliably the result is empty.
 */
object SplitCalculator {

    /** Fewer distance intervals than this cannot support a meaningful per-km split. */
    private const val MIN_INTERVALS = 3

    /** A leftover shorter than this is GPS noise, not a split worth showing. */
    private const val MIN_PARTIAL_METERS = 50.0

    fun compute(
        intervals: List<DistanceInterval>,
        hrSamples: List<Pair<Long, Int>>
    ): List<RunSplit> {
        val cleaned = removeOverlap(intervals)
        if (cleaned.size < MIN_INTERVALS) return emptyList()
        return fromCumulative(toCumulative(cleaned), hrSamples)
    }

    /**
     * Splits a cumulative distance curve. Every full kilometre becomes a split, and the leftover
     * stretch (for example the last 80 m of a 5.08 km run) becomes a final partial split whose
     * [RunSplit.distanceMeters] is below 1000.
     */
    fun fromCumulative(
        points: List<DistancePoint>,
        hrSamples: List<Pair<Long, Int>>
    ): List<RunSplit> {
        if (points.size < 2) return emptyList()
        val total = points.last().meters
        val fullKm = (total / 1000.0).toInt()
        val rest = total - fullKm * 1000.0

        val targets = ArrayList<Double>(fullKm + 1)
        for (km in 1..fullKm) targets.add(km * 1000.0)
        if (rest >= MIN_PARTIAL_METERS) targets.add(total)
        if (targets.isEmpty()) return emptyList()

        var previousSec = points.first().sec
        var previousMeters = 0.0
        var idx = 1
        val splits = ArrayList<RunSplit>(targets.size)
        for ((i, target) in targets.withIndex()) {
            while (idx < points.size - 1 && points[idx].meters < target) idx++
            val a = points[idx - 1]
            val b = points[idx]
            val span = b.meters - a.meters
            val currentSec = if (span > 0.0) {
                a.sec + ((target - a.meters) / span).coerceIn(0.0, 1.0) * (b.sec - a.sec)
            } else b.sec

            val windowHr = hrSamples.filter { it.first >= previousSec && it.first <= currentSec }
            val avgHr = if (windowHr.isNotEmpty()) windowHr.map { it.second }.average().roundToInt() else null
            splits.add(
                RunSplit(
                    kilometer = i + 1,
                    splitSeconds = (currentSec - previousSec).roundToInt(),
                    avgHeartRate = avgHr,
                    distanceMeters = target - previousMeters
                )
            )
            previousSec = currentSec
            previousMeters = target
        }
        return splits
    }

    private fun toCumulative(intervals: List<DistanceInterval>): List<DistancePoint> {
        val points = ArrayList<DistancePoint>(intervals.size * 2 + 1)
        var cumulative = 0.0
        points.add(DistancePoint(intervals.first().startSec, 0.0))
        for (interval in intervals) {
            // A gap between records carries no distance: hold the cumulative value flat.
            if (interval.startSec > points.last().sec) {
                points.add(DistancePoint(interval.startSec, cumulative))
            }
            cumulative += interval.meters
            points.add(DistancePoint(interval.endSec, cumulative))
        }
        return points
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
