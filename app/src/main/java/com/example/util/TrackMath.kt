package com.example.util

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** One GPS fix. [sec] is seconds from the start of the run. */
data class RoutePoint(
    val sec: Double,
    val lat: Double,
    val lon: Double,
    val altitude: Double?,
    val accuracyMeters: Double?
)

/**
 * Turns raw GPS and speed samples into a cumulative distance curve and an elevation gain.
 * Used when a tracker (Samsung Health for example) writes no usable interval DistanceRecords.
 */
object TrackMath {

    private const val EARTH_RADIUS_M = 6_371_000.0

    /** Fixes less accurate than this are dropped. */
    private const val MAX_ACCURACY_M = 50.0

    /** Anything faster than this between two fixes is a GPS jump, not running. */
    private const val MAX_SPEED_MPS = 12.0

    /** Longer silence between speed samples is a pause: it adds time but no distance. */
    private const val MAX_SPEED_GAP_SEC = 15.0

    /** Elevation changes below this are treated as altimeter noise. */
    private const val ELEVATION_NOISE_M = 1.0
    private const val SMOOTHING_WINDOW = 5

    fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).let { it * it } +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).let { it * it }
        return 2 * EARTH_RADIUS_M * asin(min(1.0, sqrt(a)))
    }

    fun cumulativeFromRoute(points: List<RoutePoint>): List<DistancePoint> {
        val fixes = points
            .filter { (it.accuracyMeters ?: 0.0) <= MAX_ACCURACY_M }
            .sortedBy { it.sec }
        if (fixes.size < 2) return emptyList()
        val result = ArrayList<DistancePoint>(fixes.size)
        var last = fixes.first()
        var cumulative = 0.0
        result.add(DistancePoint(last.sec, 0.0))
        for (fix in fixes.drop(1)) {
            val dt = fix.sec - last.sec
            if (dt <= 0.0) continue
            val d = haversineMeters(last.lat, last.lon, fix.lat, fix.lon)
            if (d / dt > MAX_SPEED_MPS) continue // skip the jump, keep the previous good fix
            cumulative += d
            result.add(DistancePoint(fix.sec, cumulative))
            last = fix
        }
        return result
    }

    /** [samples] are (seconds from start, metres per second). Trapezoid integration. */
    fun cumulativeFromSpeed(samples: List<Pair<Double, Double>>): List<DistancePoint> {
        val sorted = samples.sortedBy { it.first }
        if (sorted.size < 2) return emptyList()
        val result = ArrayList<DistancePoint>(sorted.size)
        var cumulative = 0.0
        result.add(DistancePoint(sorted.first().first, 0.0))
        for (i in 1 until sorted.size) {
            val (t0, v0) = sorted[i - 1]
            val (t1, v1) = sorted[i]
            val dt = t1 - t0
            if (dt <= 0.0) continue
            if (dt <= MAX_SPEED_GAP_SEC) {
                cumulative += (v0.coerceAtLeast(0.0) + v1.coerceAtLeast(0.0)) / 2.0 * dt
            }
            result.add(DistancePoint(t1, cumulative))
        }
        return result
    }

    /**
     * Rescales a curve so it ends at [totalMeters], the distance the app already shows for the run.
     * Returns null when the source disagrees with that total by more than 30%: the data is bad.
     */
    fun scaleTo(points: List<DistancePoint>, totalMeters: Double): List<DistancePoint>? {
        val end = points.lastOrNull()?.meters ?: return null
        if (end <= 0.0 || totalMeters <= 0.0) return null
        val ratio = totalMeters / end
        if (ratio < 0.7 || ratio > 1.3) return null
        return points.map { DistancePoint(it.sec, it.meters * ratio) }
    }

    /** Total climb in metres from GPS altitude, or null when there is too little altitude data. */
    fun elevationGain(points: List<RoutePoint>): Double? {
        val altitudes = points.sortedBy { it.sec }.mapNotNull { it.altitude }
        if (altitudes.size < SMOOTHING_WINDOW) return null
        val half = SMOOTHING_WINDOW / 2
        val smooth = altitudes.indices.map { i ->
            val from = maxOf(0, i - half)
            val to = min(altitudes.lastIndex, i + half)
            altitudes.subList(from, to + 1).average()
        }
        var gain = 0.0
        var reference = smooth.first()
        for (v in smooth) {
            when {
                v - reference >= ELEVATION_NOISE_M -> { gain += v - reference; reference = v }
                reference - v >= ELEVATION_NOISE_M -> reference = v
            }
        }
        return gain
    }
}
