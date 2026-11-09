package com.example

import com.example.data.model.RaceDistance
import com.example.util.RiegelCalculator
import org.junit.Assert.assertEquals
import org.junit.Test

class RiegelCalculatorTest {
    @Test
    fun halfMarathon_usesDedicatedExponentForTargetsAndTrend() {
        val predicted = RiegelCalculator.predictRaceTimeSeconds(
            refDistanceMeters = 5_310.0,
            refDurationSeconds = 2_157L,
            targetDistanceMeters = RaceDistance.HALF_MARATHON.meters
        )

        assertEquals(9_837L, predicted)
        assertEquals("2:43:57", formatDuration(predicted))
    }

    @Test
    fun nonHalfMarathonGoals_keepStandardExponent() {
        assertEquals(
            4_219L,
            RiegelCalculator.predictRaceTimeSeconds(5_310.0, 2_157L, 10_000.0)
        )
    }

    private fun formatDuration(seconds: Long): String =
        "%d:%02d:%02d".format(seconds / 3600, (seconds % 3600) / 60, seconds % 60)
}
