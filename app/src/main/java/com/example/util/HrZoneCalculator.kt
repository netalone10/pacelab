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
