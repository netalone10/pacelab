package com.example.data.health

import com.example.data.model.HeartRatePoint
import com.example.data.model.RunSession
import com.example.data.model.RunSplit
import com.example.util.PaceUtils
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import kotlin.random.Random

object SeedDataGenerator {

    /**
     * Generates realistic running data spanning 4 weeks, including the user's
     * default reference race: 5.31 km in 35:57 with avg HR 151 bpm.
     */
    fun generateDemoSessions(): List<RunSession> {
        val now = LocalDate.now()
        val zone = ZoneId.systemDefault()
        val sessions = mutableListOf<RunSession>()

        // Current week Monday
        val currentMon = now.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

        // Week -3 (3 weeks ago)
        val w3Mon = currentMon.minusWeeks(3)
        sessions.add(createSession("seed_w3_1", w3Mon.plusDays(1), 5000.0, 34 * 60 + 10, 134, 146, 360.0, 24.0)) // Tue 5k Easy
        sessions.add(createSession("seed_w3_2", w3Mon.plusDays(3), 6000.0, 36 * 60 + 45, 154, 168, 440.0, 38.0)) // Thu 6k Tempo
        sessions.add(createSession("seed_w3_3", w3Mon.plusDays(5), 8000.0, 53 * 60 + 20, 138, 152, 590.0, 45.0)) // Sat 8k Long

        // Week -2 (2 weeks ago)
        val w2Mon = currentMon.minusWeeks(2)
        sessions.add(createSession("seed_w2_1", w2Mon.plusDays(1), 5500.0, 37 * 60 + 25, 136, 149, 400.0, 28.0)) // Tue 5.5k Easy
        sessions.add(createSession("seed_w2_2", w2Mon.plusDays(3), 5000.0, 29 * 60 + 10, 163, 178, 385.0, 18.0)) // Thu 5k Intervals
        sessions.add(createSession("seed_w2_3", w2Mon.plusDays(5), 9000.0, 60 * 60 + 40, 139, 155, 660.0, 55.0)) // Sat 9k Long

        // Week -1 (last week) — includes the user's reference race!
        val w1Mon = currentMon.minusWeeks(1)
        sessions.add(createSession("seed_w1_1", w1Mon.plusDays(1), 5000.0, 33 * 60 + 50, 135, 148, 365.0, 22.0)) // Tue 5k Easy
        // Reference race: 5.31 km in 35:57, avg HR 151 bpm
        sessions.add(
            RunSession(
                id = "seed_ref_race",
                startTime = w1Mon.plusDays(3).atTime(7, 15).atZone(zone).toInstant(),
                endTime = w1Mon.plusDays(3).atTime(7, 15).atZone(zone).toInstant().plusSeconds(2157L),
                distanceMeters = 5310.0,
                durationSeconds = 2157L, // 35:57
                avgPaceSeconds = 406,   // 6'46"/km
                avgHeartRate = 151,
                maxHeartRate = 169,
                totalCalories = 408.0,
                elevationGainedMeters = 34.0,
                isSeed = true
            )
        )
        sessions.add(createSession("seed_w1_3", w1Mon.plusDays(5), 10000.0, 67 * 60 + 15, 142, 158, 735.0, 62.0)) // Sat 10k Long

        // Current week
        // Tuesday run (completed)
        if (now.dayOfWeek.value >= DayOfWeek.TUESDAY.value) {
            sessions.add(createSession("seed_w0_1", currentMon.plusDays(1), 5200.0, 35 * 60 + 10, 136, 149, 380.0, 26.0))
        }
        // Thursday run (completed if today is Thu or later)
        if (now.dayOfWeek.value >= DayOfWeek.THURSDAY.value) {
            sessions.add(createSession("seed_w0_2", currentMon.plusDays(3), 6000.0, 36 * 60 + 30, 156, 171, 445.0, 32.0))
        }

        return sessions.sortedByDescending { it.startTime }
    }

    private fun createSession(
        id: String,
        date: LocalDate,
        distanceMeters: Double,
        durationSeconds: Long,
        avgHr: Int,
        maxHr: Int,
        calories: Double,
        elevation: Double
    ): RunSession {
        val zone = ZoneId.systemDefault()
        val startInstant = date.atTime(6, 45).atZone(zone).toInstant()
        val endInstant = startInstant.plusSeconds(durationSeconds)
        val paceSec = PaceUtils.calculatePaceSeconds(distanceMeters, durationSeconds)
        return RunSession(
            id = id,
            startTime = startInstant,
            endTime = endInstant,
            distanceMeters = distanceMeters,
            durationSeconds = durationSeconds,
            avgPaceSeconds = paceSec,
            avgHeartRate = avgHr,
            maxHeartRate = maxHr,
            totalCalories = calories,
            elevationGainedMeters = elevation,
            isSeed = true
        )
    }

    /**
     * Generates realistic per-kilometer splits for a session.
     */
    fun generateSplitsForSession(session: RunSession): List<RunSplit> {
        val totalKm = (session.distanceMeters / 1000.0).toInt().coerceAtLeast(1)
        val avgPace = session.avgPaceSeconds
        val avgHr = session.avgHeartRate ?: 145
        val random = Random(session.id.hashCode())

        return (1..totalKm).map { km ->
            // Slight natural variance per km: warm up slower in km 1, finish strong or fade
            val paceOffset = when (km) {
                1 -> random.nextInt(8, 16)   // first km slightly slower
                totalKm -> random.nextInt(-12, -4) // final kick
                else -> random.nextInt(-8, 8)
            }
            val splitSec = (avgPace + paceOffset).coerceAtLeast(180)
            val hrOffset = (km * 2) - 4 + random.nextInt(-3, 3)
            val splitHr = (avgHr + hrOffset).coerceIn(100, 195)

            RunSplit(
                kilometer = km,
                splitSeconds = splitSec,
                avgHeartRate = splitHr
            )
        }
    }

    /**
     * Generates a time series of HeartRatePoints for the line graph visualizer.
     */
    fun generateHeartRatePoints(session: RunSession): List<HeartRatePoint> {
        val duration = session.durationSeconds.toInt()
        val avgHr = (session.avgHeartRate ?: 145).toDouble()
        val maxHr = (session.maxHeartRate ?: (avgHr + 15).toInt()).toDouble()
        val random = Random(session.id.hashCode() + 42)
        val points = mutableListOf<HeartRatePoint>()

        // Sample every 15 to 30 seconds
        val step = 15
        var currentHr = avgHr - 20.0 // start warmup

        for (offset in 0..duration step step) {
            val progress = offset.toDouble() / duration.toDouble()
            // Trend rises from warm-up to steady state and climbs near the end
            val targetTrend = if (progress < 0.15) {
                avgHr - 18.0 + (progress / 0.15) * 15.0
            } else if (progress < 0.85) {
                avgHr + ((progress - 0.5) * 6.0)
            } else {
                avgHr + 4.0 + ((progress - 0.85) / 0.15) * (maxHr - avgHr - 4.0)
            }

            // Add smooth physiological noise
            val noise = (random.nextDouble() - 0.5) * 4.0
            currentHr = (currentHr * 0.7) + ((targetTrend + noise) * 0.3)
            val bpm = currentHr.toInt().coerceIn(85, maxHr.toInt())

            points.add(HeartRatePoint(offset.toLong(), bpm))
        }

        return points
    }
}
