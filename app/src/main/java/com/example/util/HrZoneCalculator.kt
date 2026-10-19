package com.example.util

data class HrZones(
    val z1Max: Int = 117, // Z1 < 118
    val z2Min: Int = 118,
    val z2Max: Int = 140, // Z2 118-140
    val z3Min: Int = 141,
    val z3Max: Int = 160, // Z3 141-160
    val z4Min: Int = 161,
    val z4Max: Int = 179, // Z4 161-179
    val maxHr: Int = 190
)

data class HrZoneDistribution(
    val z1Seconds: Long = 0L,
    val z2Seconds: Long = 0L,
    val z3Seconds: Long = 0L,
    val z4Seconds: Long = 0L,
    val z5Seconds: Long = 0L
) {
    val totalSeconds: Long
        get() = z1Seconds + z2Seconds + z3Seconds + z4Seconds + z5Seconds

    fun percentageForZone(zone: Int): Float {
        val total = totalSeconds
        if (total == 0L) return 0f
        val sec = when (zone) {
            1 -> z1Seconds
            2 -> z2Seconds
            3 -> z3Seconds
            4 -> z4Seconds
            5 -> z5Seconds
            else -> 0L
        }
        return (sec.toFloat() / total.toFloat()) * 100f
    }
}

object HrZoneCalculator {

    /**
     * Determines which zone (1 to 5) an HR value belongs to.
     */
    fun determineZone(bpm: Int, zones: HrZones = HrZones()): Int {
        return when {
            bpm <= zones.z1Max -> 1
            bpm in zones.z2Min..zones.z2Max -> 2
            bpm in zones.z3Min..zones.z3Max -> 3
            bpm in zones.z4Min..zones.z4Max -> 4
            else -> 5
        }
    }

    /**
     * Human readable label for a zone that always reflects the user's own thresholds,
     * e.g. "Z2 (118-140 bpm)".
     */
    fun label(zone: Int, zones: HrZones): String = when (zone) {
        1 -> "Z1 (up to ${zones.z1Max} bpm)"
        2 -> "Z2 (${zones.z2Min}-${zones.z2Max} bpm)"
        3 -> "Z3 (${zones.z3Min}-${zones.z3Max} bpm)"
        4 -> "Z4 (${zones.z4Min}-${zones.z4Max} bpm)"
        else -> "Z5 (${zones.z4Max + 1}+ bpm)"
    }

    /** Label for easy / recovery effort that spans Z1 and Z2. */
    fun easyLabel(zones: HrZones): String = "Z1-Z2 (up to ${zones.z2Max} bpm)"

    /**
     * Given a list of (timestampSeconds, bpm) samples, buckets time spent in each zone.
     * Assuming samples are sequential with intervals.
     */
    fun bucketHrSamples(
        samples: List<Pair<Long, Int>>, // (timeOffsetSeconds, bpm)
        zones: HrZones = HrZones(),
        defaultIntervalSeconds: Long = 5L
    ): HrZoneDistribution {
        if (samples.isEmpty()) return HrZoneDistribution()

        var z1 = 0L
        var z2 = 0L
        var z3 = 0L
        var z4 = 0L
        var z5 = 0L

        for (i in samples.indices) {
            val (time, bpm) = samples[i]
            val duration = if (i < samples.size - 1) {
                val diff = samples[i + 1].first - time
                if (diff in 1..30) diff else defaultIntervalSeconds
            } else {
                defaultIntervalSeconds
            }

            when (determineZone(bpm, zones)) {
                1 -> z1 += duration
                2 -> z2 += duration
                3 -> z3 += duration
                4 -> z4 += duration
                5 -> z5 += duration
            }
        }

        return HrZoneDistribution(z1, z2, z3, z4, z5)
    }

    /**
     * Recalculates zone ranges based on Max HR (using standard Karvonen / percentage method if user updates Max HR).
     */
    fun fromMaxHr(maxHr: Int): HrZones {
        return HrZones(
            z1Max = (maxHr * 0.60).toInt() - 1,
            z2Min = (maxHr * 0.60).toInt(),
            z2Max = (maxHr * 0.70).toInt(),
            z3Min = (maxHr * 0.70).toInt() + 1,
            z3Max = (maxHr * 0.80).toInt(),
            z4Min = (maxHr * 0.80).toInt() + 1,
            z4Max = (maxHr * 0.90).toInt(),
            maxHr = maxHr
        )
    }
}

/**
 * Compact per-session heart rate histogram (bpm -> seconds spent at that bpm).
 *
 * It is stored as text in the local cache so heart rate zones can be recomputed at any time
 * from the user's current thresholds, without re-reading Health Connect.
 */
object HrHistogram {

    /**
     * Builds a bpm -> seconds map from (offsetSeconds, bpm) samples. Each sample is credited
     * with the time until the next sample, capped at [maxGapSeconds]; larger gaps (sensor
     * dropouts) and the final sample are credited with [defaultIntervalSeconds].
     */
    fun build(
        samples: List<Pair<Long, Int>>,
        defaultIntervalSeconds: Long = 5L,
        maxGapSeconds: Long = 30L
    ): Map<Int, Long> {
        if (samples.isEmpty()) return emptyMap()
        val sorted = samples.sortedBy { it.first }
        val histogram = HashMap<Int, Long>()
        for (i in sorted.indices) {
            val (time, bpm) = sorted[i]
            val duration = if (i < sorted.size - 1) {
                val diff = sorted[i + 1].first - time
                if (diff in 1..maxGapSeconds) diff else defaultIntervalSeconds
            } else {
                defaultIntervalSeconds
            }
            histogram[bpm] = (histogram[bpm] ?: 0L) + duration
        }
        return histogram
    }

    fun encode(histogram: Map<Int, Long>): String =
        histogram.entries.sortedBy { it.key }.joinToString(",") { "${it.key}:${it.value}" }

    fun decode(text: String?): Map<Int, Long> {
        if (text.isNullOrBlank()) return emptyMap()
        val histogram = HashMap<Int, Long>()
        for (part in text.split(",")) {
            val kv = part.split(":")
            if (kv.size != 2) continue
            val bpm = kv[0].toIntOrNull() ?: continue
            val seconds = kv[1].toLongOrNull() ?: continue
            histogram[bpm] = (histogram[bpm] ?: 0L) + seconds
        }
        return histogram
    }

    fun bucket(histogram: Map<Int, Long>, zones: HrZones): HrZoneDistribution {
        var z1 = 0L
        var z2 = 0L
        var z3 = 0L
        var z4 = 0L
        var z5 = 0L
        for ((bpm, seconds) in histogram) {
            when (HrZoneCalculator.determineZone(bpm, zones)) {
                1 -> z1 += seconds
                2 -> z2 += seconds
                3 -> z3 += seconds
                4 -> z4 += seconds
                else -> z5 += seconds
            }
        }
        return HrZoneDistribution(z1, z2, z3, z4, z5)
    }
}
