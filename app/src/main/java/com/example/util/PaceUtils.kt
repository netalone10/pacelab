package com.example.util

import java.util.Locale
import kotlin.math.roundToInt

object PaceUtils {

    /**
     * Formats seconds per kilometer into min'sec"/km, e.g. 342 -> 5'42"/km.
     * Never outputs decimal minutes.
     */
    fun formatPace(secondsPerKm: Int): String {
        if (secondsPerKm <= 0 || secondsPerKm > 3600) return "--'--\"/km"
        val minutes = secondsPerKm / 60
        val seconds = secondsPerKm % 60
        return String.format(Locale.US, "%d'%02d\"/km", minutes, seconds)
    }

    /**
     * Formats a pace range, e.g. 6'30" - 7'00"/km
     */
    fun formatPaceRange(minSec: Int, maxSec: Int): String {
        if (minSec <= 0 || maxSec <= 0) return "--'--\" - --'--\"/km"
        val minM = minSec / 60
        val minS = minSec % 60
        val maxM = maxSec / 60
        val maxS = maxSec % 60
        return String.format(Locale.US, "%d'%02d\" - %d'%02d\"/km", minM, minS, maxM, maxS)
    }

    /**
     * Calculates average pace in seconds per km from distance in meters and duration in seconds.
     */
    fun calculatePaceSeconds(distanceMeters: Double, durationSeconds: Long): Int {
        if (distanceMeters <= 0.0 || durationSeconds <= 0L) return 0
        val km = distanceMeters / 1000.0
        val secPerKm = durationSeconds.toDouble() / km
        return secPerKm.roundToInt()
    }

    /**
     * Formats duration in seconds into HH:MM:SS or MM:SS
     */
    fun formatDuration(totalSeconds: Long): String {
        if (totalSeconds <= 0L) return "00:00"
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) {
            String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.US, "%02d:%02d", minutes, seconds)
        }
    }

    /**
     * Formats distance in meters to kilometers string, e.g. "5.31 km"
     */
    fun formatDistanceKm(meters: Double): String {
        val km = meters / 1000.0
        return String.format(Locale.US, "%.2f km", km)
    }

    /**
     * Parses a string like "35:57" or "1:15:30" into total seconds.
     */
    fun parseDurationToSeconds(input: String): Long? {
        val parts = input.trim().split(":")
        return try {
            when (parts.size) {
                2 -> parts[0].toLong() * 60 + parts[1].toLong()
                3 -> parts[0].toLong() * 3600 + parts[1].toLong() * 60 + parts[2].toLong()
                else -> null
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Parses min'sec"/km or m:ss into seconds per km.
     */
    fun parsePaceToSeconds(input: String): Int? {
        val clean = input.replace("/km", "").replace("\"", "").trim()
        val parts = if (clean.contains("'")) clean.split("'") else clean.split(":")
        return try {
            if (parts.size == 2) {
                parts[0].toInt() * 60 + parts[1].toInt()
            } else null
        } catch (_: Exception) {
            null
        }
    }
}
