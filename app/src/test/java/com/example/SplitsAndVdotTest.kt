package com.example

import com.example.util.DistanceInterval
import com.example.util.PredictionTrendCalculator
import com.example.util.RiegelCalculator
import com.example.util.RoutePoint
import com.example.util.SplitCalculator
import com.example.util.TrackMath
import com.example.util.VdotCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Sample run from the bug report: 5.08 km in 43:31 (2611 s). */
class SplitsAndVdotTest {

    private val totalMeters = 5080.0
    private val totalSeconds = 2611.0
    private val metersPerDegreeLat = 111_194.9

    private fun assertSampleSplits(splits: List<com.example.data.model.RunSplit>) {
        assertEquals(6, splits.size)
        assertEquals(1000.0, splits[4].distanceMeters, 0.01)
        assertEquals(80.0, splits[5].distanceMeters, 0.5)
        assertEquals(totalSeconds, splits.sumOf { it.splitSeconds }.toDouble(), 3.0)
        // Constant pace: every full split is about 514 s, and the partial split has the same pace.
        assertEquals(514, splits[0].splitSeconds.toDouble().toInt(), 2)
        assertEquals(514, splits[5].paceSeconds, 3)
    }

    private fun assertEquals(expected: Int, actual: Int, delta: Int) =
        assertTrue("expected $expected +-$delta but was $actual", kotlin.math.abs(expected - actual) <= delta)

    @Test
    fun distanceIntervals_giveFullAndPartialSplits() {
        val step = totalSeconds / 508
        val intervals = (0 until 508).map {
            DistanceInterval(it * step, (it + 1) * step, 10.0)
        }
        assertSampleSplits(SplitCalculator.compute(intervals, emptyList()))
    }

    @Test
    fun distanceIntervals_tooCoarse_returnsEmpty() {
        val intervals = listOf(DistanceInterval(0.0, totalSeconds, totalMeters))
        assertTrue(SplitCalculator.compute(intervals, emptyList()).isEmpty())
    }

    @Test
    fun routeOnly_givesSplits() {
        val speed = totalMeters / totalSeconds
        val points = (0..522).map {
            val t = it * 5.0
            RoutePoint(t, -6.2 + speed * t / metersPerDegreeLat, 106.8, 10.0, 8.0)
        }
        val cumulative = TrackMath.cumulativeFromRoute(points)
        val scaled = TrackMath.scaleTo(cumulative, totalMeters)
        assertNotNull(scaled)
        assertSampleSplits(SplitCalculator.fromCumulative(scaled!!, emptyList()))
    }

    @Test
    fun route_dropsGpsJumpAndBadAccuracy() {
        val speed = 3.0
        val good = (0..100).map {
            val t = it * 5.0
            RoutePoint(t, -6.2 + speed * t / metersPerDegreeLat, 106.8, null, 5.0)
        }
        val jump = RoutePoint(252.5, -6.2 + 5000.0 / metersPerDegreeLat, 106.8, null, 5.0)
        val inaccurate = RoutePoint(253.0, -6.19, 106.8, null, 200.0)
        val cumulative = TrackMath.cumulativeFromRoute(good + jump + inaccurate)
        assertEquals(1500.0, cumulative.last().meters, 5.0)
    }

    @Test
    fun speedOnly_givesSplits() {
        val speed = totalMeters / totalSeconds
        val samples = (0..522).map { it * 5.0 to speed }
        val scaled = TrackMath.scaleTo(TrackMath.cumulativeFromSpeed(samples), totalMeters)
        assertNotNull(scaled)
        assertSampleSplits(SplitCalculator.fromCumulative(scaled!!, emptyList()))
    }

    @Test
    fun scaleTo_rejectsSourceFarFromTotal() {
        val samples = (0..100).map { it * 5.0 to 2.0 } // ~1000 m
        assertNull(TrackMath.scaleTo(TrackMath.cumulativeFromSpeed(samples), 5080.0))
    }

    @Test
    fun shortSession_underOneKm() {
        val samples = (0..160).map { it * 5.0 to 1.0 } // 800 m
        val splits = SplitCalculator.fromCumulative(TrackMath.cumulativeFromSpeed(samples), emptyList())
        assertEquals(1, splits.size)
        assertEquals(800.0, splits[0].distanceMeters, 1.0)

        val tiny = (0..6).map { it * 5.0 to 1.0 } // 30 m
        assertTrue(SplitCalculator.fromCumulative(TrackMath.cumulativeFromSpeed(tiny), emptyList()).isEmpty())
    }

    @Test
    fun emptyData_givesEmpty() {
        assertTrue(SplitCalculator.compute(emptyList(), emptyList()).isEmpty())
        assertTrue(SplitCalculator.fromCumulative(emptyList(), emptyList()).isEmpty())
        assertTrue(TrackMath.cumulativeFromRoute(emptyList()).isEmpty())
        assertTrue(TrackMath.cumulativeFromSpeed(emptyList()).isEmpty())
        assertNull(TrackMath.elevationGain(emptyList()))
    }

    @Test
    fun elevationGain_ignoresNoise() {
        // Climb 20 m over 100 fixes with +-0.4 m noise.
        val points = (0 until 100).map {
            val noise = if (it % 2 == 0) 0.4 else -0.4
            RoutePoint(it * 5.0, 0.0, 0.0, 100.0 + it * 0.2 + noise, 5.0)
        }
        val gain = TrackMath.elevationGain(points)!!
        assertEquals(20.0, gain, 2.0)
        val flat = (0 until 100).map {
            RoutePoint(it * 5.0, 0.0, 0.0, 100.0 + if (it % 2 == 0) 0.4 else -0.4, 5.0)
        }
        assertEquals(0.0, TrackMath.elevationGain(flat)!!, 0.5)
    }

    @Test
    fun vdot_forBaselineRun_isAbout28() {
        val vdot = VdotCalculator.vdot(5180.0, 33 * 60 + 51L)!!
        assertEquals(28.0, vdot, 0.5)
        assertNull(VdotCalculator.vdot(0.0, 100))
    }

    @Test
    fun riegel_baselineTo5k() {
        val t = RiegelCalculator.predictRaceTimeSeconds(5180.0, 33 * 60 + 51L, 5000.0)
        assertTrue("was $t", t in 1956L..1957L) // 32:36 or 32:37
    }

    @Test
    fun trendWindow_dropsOldRunWhenItAgesOut() {
        // A strong run 20 Aug stays in the 28-day window for the week of 08-31 but not for 09-14.
        val zone = java.time.ZoneId.of("Asia/Jakarta")
        fun run(date: String, meters: Double, sec: Long) = com.example.data.model.RunSession(
            id = date, startTime = java.time.LocalDate.parse(date).atTime(6, 0).atZone(zone).toInstant(),
            endTime = java.time.LocalDate.parse(date).atTime(7, 0).atZone(zone).toInstant(),
            distanceMeters = meters, durationSeconds = sec, avgPaceSeconds = (sec / (meters / 1000)).toInt()
        )
        val sessions = listOf(run("2026-08-20", 5000.0, 2021), run("2026-09-10", 6000.0, 2800))
        val trend = PredictionTrendCalculator.trend(
            sessions, 5000.0, today = java.time.LocalDate.parse("2026-09-14"), weeks = 3, zone = zone
        )
        val byWeek = trend.associate { it.weekMonday.toString() to it.predictedSeconds }
        assertTrue(byWeek["2026-09-07"]!! < byWeek["2026-09-14"]!!)
    }
}
