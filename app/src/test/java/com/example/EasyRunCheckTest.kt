package com.example

import com.example.data.model.RunSession
import com.example.data.model.UserSettings
import com.example.util.EasyRunCheck
import com.example.util.HrHistogram
import com.example.util.RiegelCalculator
import com.example.util.toHrZones
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class EasyRunCheckTest {

    private val zones = UserSettings().toHrZones() // Z2 tops out at 140 bpm
    private val targets = RiegelCalculator.computeTargets(5310.0, 2157L, 10000.0)

    private fun run(
        paceSec: Int,
        histogram: Map<Int, Long>?,
        durationSeconds: Long = 2400L
    ) = RunSession(
        id = "r",
        startTime = Instant.EPOCH,
        endTime = Instant.EPOCH.plusSeconds(durationSeconds),
        distanceMeters = durationSeconds * 1000.0 / paceSec,
        durationSeconds = durationSeconds,
        avgPaceSeconds = paceSec,
        hrHistogram = histogram?.let { HrHistogram.encode(it) }
    )

    @Test
    fun easyPaceMostlyInZ2_isFine() {
        val result = EasyRunCheck.assess(run(480, mapOf(130 to 1500L, 150 to 200L)), zones, targets)
        assertNotNull(result)
        assertTrue(result!!.easyIntent)
        assertEquals(12, result.aboveZ2Percent)
        assertFalse(result.tooHard)
    }

    @Test
    fun easyPaceButHighHeartRate_isFlagged() {
        val result = EasyRunCheck.assess(run(480, mapOf(130 to 600L, 150 to 900L)), zones, targets)
        assertNotNull(result)
        assertEquals(60, result!!.aboveZ2Percent)
        assertTrue(result.tooHard)
    }

    @Test
    fun deliberateHardEffort_isNeverFlagged() {
        // Faster than the tempo zone, so a hard heart rate is expected.
        val result = EasyRunCheck.assess(run(400, mapOf(150 to 900L, 170 to 900L)), zones, targets)
        assertNotNull(result)
        assertFalse(result!!.easyIntent)
        assertFalse(result.tooHard)
    }

    @Test
    fun shortOrHeartRateLessRuns_areNotJudged() {
        assertNull(EasyRunCheck.assess(run(480, mapOf(150 to 600L), durationSeconds = 600L), zones, targets))
        assertNull(EasyRunCheck.assess(run(480, null), zones, targets))
        assertNull(EasyRunCheck.assess(run(480, emptyMap()), zones, targets))
    }
}
