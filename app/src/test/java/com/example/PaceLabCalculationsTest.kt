package com.example

import com.example.util.HrZoneCalculator
import com.example.util.HrZones
import com.example.util.PaceUtils
import com.example.util.PlanGenerator
import com.example.util.RiegelCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.roundToInt

class PaceLabCalculationsTest {

    @Test
    fun testPaceFormatter_NeverUsesDecimalMinutes() {
        val pace1 = PaceUtils.formatPace(342) // 5 min 42 sec
        assertEquals("5'42\"/km", pace1)
        assertFalse("Pace format should not contain decimal point", pace1.contains("5."))

        // User's default reference pace: 5.31km in 35:57 -> 406 sec/km = 6'46"/km
        val pace2 = PaceUtils.formatPace(406)
        assertEquals("6'46\"/km", pace2)

        val paceRange = PaceUtils.formatPaceRange(390, 420)
        assertEquals("6'30\" - 7'00\"/km", paceRange)
    }

    @Test
    fun testPaceAndDurationCalculations() {
        // 5.31 km in 35:57 (2157 sec)
        val secPerKm = PaceUtils.calculatePaceSeconds(5310.0, 2157L)
        assertEquals(406, secPerKm) // 406 seconds = 6'46"/km

        val durationFormatted = PaceUtils.formatDuration(2157L)
        assertEquals("35:57", durationFormatted)

        val parsedSec = PaceUtils.parseDurationToSeconds("35:57")
        assertEquals(2157L, parsedSec)
    }

    @Test
    fun testRiegelFormulaPrediction_And_TrainingPaces() {
        // T2 = T1 * (D2 / D1)^1.06
        // Reference race: 5.31 km in 35:57 (2157 sec)
        val refDist = 5310.0
        val refTime = 2157L
        val targetDist = 10000.0 // 10K

        val predictedTime = RiegelCalculator.predictRaceTimeSeconds(refDist, refTime, targetDist)
        // 10000 / 5310 = 1.88324; 1.88324^1.06 ≈ 1.95679; 2157 * 1.95679 ≈ 4221 sec (~1:10:21)
        assertTrue("Predicted time should be within 4215..4225 sec", predictedTime in 4215L..4225L)

        val targets = RiegelCalculator.computeTargets(refDist, refTime, targetDist)
        assertEquals(targetDist, targets.targetDistanceMeters, 0.01)

        val predictedPace = targets.predictedPaceSeconds
        // ~422 sec/km (approx 7'02"/km)
        assertTrue("Predicted 10K pace should be around 422 sec/km", predictedPace in 420..425)

        // Verify training pace ranges:
        // Easy run (115% - 125%): slower than race pace
        assertTrue("Easy pace should be slower than race pace", targets.easyPaceRange.first > predictedPace)
        assertEquals((predictedPace * 1.15).roundToInt(), targets.easyPaceRange.first)
        assertEquals((predictedPace * 1.25).roundToInt(), targets.easyPaceRange.second)

        // Interval run (95% - 100%): faster than race pace
        assertTrue("Interval pace should be faster or equal to race pace", targets.intervalPaceRange.first <= predictedPace)
        assertEquals((predictedPace * 0.95).roundToInt(), targets.intervalPaceRange.first)
        assertEquals((predictedPace * 1.00).roundToInt(), targets.intervalPaceRange.second)
    }

    @Test
    fun testHeartRateZoneBucketing() {
        val zones = HrZones(
            z1Max = 117,
            z2Min = 118,
            z2Max = 140,
            z3Min = 141,
            z3Max = 160,
            z4Min = 161,
            z4Max = 179,
            maxHr = 190
        )

        assertEquals(1, HrZoneCalculator.determineZone(110, zones))
        assertEquals(2, HrZoneCalculator.determineZone(118, zones))
        assertEquals(2, HrZoneCalculator.determineZone(135, zones))
        assertEquals(2, HrZoneCalculator.determineZone(140, zones))
        assertEquals(3, HrZoneCalculator.determineZone(141, zones))
        assertEquals(3, HrZoneCalculator.determineZone(155, zones))
        assertEquals(4, HrZoneCalculator.determineZone(161, zones))
        assertEquals(4, HrZoneCalculator.determineZone(175, zones))
        assertEquals(5, HrZoneCalculator.determineZone(185, zones))

        // Test sample distribution bucketing
        val samples = listOf(
            Pair(0L, 115),   // Z1 for 10s
            Pair(10L, 130),  // Z2 for 10s
            Pair(20L, 150),  // Z3 for 10s
            Pair(30L, 170),  // Z4 for 10s
            Pair(40L, 185)   // Z5 for 5s (default tail)
        )

        val dist = HrZoneCalculator.bucketHrSamples(samples, zones, defaultIntervalSeconds = 5L)
        assertEquals(10L, dist.z1Seconds)
        assertEquals(10L, dist.z2Seconds)
        assertEquals(10L, dist.z3Seconds)
        assertEquals(10L, dist.z4Seconds)
        assertEquals(5L, dist.z5Seconds)
        assertEquals(45L, dist.totalSeconds)
    }

    @Test
    fun testWeeklyVolumeProgressionRule_Max10PercentAnd4thWeekDeload() {
        val baseVolumeKm = 20.0
        val volumes = PlanGenerator.calculateWeeklyVolumes(baseVolumeKm)

        assertEquals(4, volumes.size)
        val w1 = volumes[0]
        val w2 = volumes[1]
        val w3 = volumes[2]
        val w4 = volumes[3]

        // Week 1 is base
        assertEquals(20.0, w1, 0.01)

        // Week 2: Max 10% increase
        val w2Increase = (w2 - w1) / w1
        assertTrue("Week 2 increase must be <= 10%", w2Increase <= 0.1001)
        assertEquals(22.0, w2, 0.05)

        // Week 3: Max 10% increase from Week 2
        val w3Increase = (w3 - w2) / w2
        assertTrue("Week 3 increase must be <= 10%", w3Increase <= 0.1001)
        assertEquals(24.2, w3, 0.05)

        // Week 4: Deload recovery week (approx 20% drop from week 3)
        assertTrue("Week 4 must be lighter than Week 3", w4 < w3)
        val deloadRatio = w4 / w3
        assertTrue("Week 4 should be approximately 80% of Week 3 volume", abs(deloadRatio - 0.80) < 0.02)
    }
}
