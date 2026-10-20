package com.example.util

import kotlin.math.exp

/** Daniels/Gilbert VDOT: a VO2max-equivalent score derived from one race-effort performance. */
object VdotCalculator {

    fun vdot(distanceMeters: Double, durationSeconds: Long): Double? {
        if (distanceMeters <= 0.0 || durationSeconds <= 0L) return null
        val minutes = durationSeconds / 60.0
        val velocity = distanceMeters / minutes // m/min
        val vo2 = -4.60 + 0.182258 * velocity + 0.000104 * velocity * velocity
        val fraction = 0.8 +
            0.1894393 * exp(-0.012778 * minutes) +
            0.2989558 * exp(-0.1932605 * minutes)
        return vo2 / fraction
    }
}
