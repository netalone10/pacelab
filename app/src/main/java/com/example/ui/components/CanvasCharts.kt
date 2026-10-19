package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.HeartRatePoint
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TextTertiary
import com.example.ui.theme.VoltPrimary
import com.example.ui.theme.Zone1Blue
import com.example.ui.theme.Zone2Green
import com.example.ui.theme.Zone3Amber
import com.example.ui.theme.Zone4Orange
import com.example.ui.theme.Zone5Red
import com.example.util.HrZoneCalculator
import com.example.util.HrZoneDistribution
import com.example.util.PaceUtils
import java.time.DayOfWeek
import java.util.Locale
import kotlin.math.max

@Composable
fun DailyVolumeBarChart(
    dailyKmMap: Map<DayOfWeek, Double>,
    modifier: Modifier = Modifier
) {
    val days = listOf(
        DayOfWeek.MONDAY to "Mon",
        DayOfWeek.TUESDAY to "Tue",
        DayOfWeek.WEDNESDAY to "Wed",
        DayOfWeek.THURSDAY to "Thu",
        DayOfWeek.FRIDAY to "Fri",
        DayOfWeek.SATURDAY to "Sat",
        DayOfWeek.SUNDAY to "Sun"
    )

    val maxKm = max(1.0, dailyKmMap.values.maxOrNull() ?: 1.0)
    var selectedDay by remember { mutableStateOf<DayOfWeek?>(null) }

    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Daily Distance (km)",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            selectedDay?.let { day ->
                val km = dailyKmMap[day] ?: 0.0
                Text(
                    text = String.format(Locale.US, "%s: %.2f km", day.name.take(3), km),
                    style = MaterialTheme.typography.labelLarge,
                    color = VoltPrimary
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
        ) {
            val primaryColor = MaterialTheme.colorScheme.primary
            val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant
            val outlineColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)

            Canvas(modifier = Modifier.fillMaxWidth().height(120.dp)) {
                val width = size.width
                val height = size.height
                val barWidth = width / 9f
                val spacing = (width - (barWidth * 7)) / 8f

                // Draw horizontal guideline
                drawLine(
                    color = outlineColor,
                    start = Offset(0f, height),
                    end = Offset(width, height),
                    strokeWidth = 2f
                )

                days.forEachIndexed { index, (day, _) ->
                    val km = dailyKmMap[day] ?: 0.0
                    val barHeight = ((km / maxKm) * (height - 20f)).toFloat()
                    val x = spacing + (index * (barWidth + spacing))
                    val y = height - barHeight

                    // Background track bar
                    drawRoundRect(
                        color = surfaceVariant,
                        topLeft = Offset(x, 10f),
                        size = Size(barWidth, height - 10f),
                        cornerRadius = CornerRadius(6f, 6f)
                    )

                    // Filled active bar
                    if (barHeight > 0) {
                        val barBrush = Brush.verticalGradient(
                            colors = listOf(VoltPrimary, VoltPrimary.copy(alpha = 0.7f)),
                            startY = y,
                            endY = height
                        )
                        drawRoundRect(
                            brush = barBrush,
                            topLeft = Offset(x, y),
                            size = Size(barWidth, barHeight),
                            cornerRadius = CornerRadius(6f, 6f)
                        )
                    }
                }
            }

            // Interactive hit-boxes and labels row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .height(24.dp),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                days.forEach { (day, label) ->
                    val isSelected = selectedDay == day
                    Box(
                        modifier = Modifier
                            .clickable { selectedDay = if (selectedDay == day) null else day }
                            .padding(horizontal = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isSelected) VoltPrimary else TextSecondary,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun HrZoneDistributionBar(
    distribution: HrZoneDistribution,
    modifier: Modifier = Modifier
) {
    val totalSeconds = distribution.totalSeconds

    Column(modifier = modifier) {
        Text(
            text = "Heart Rate Zone Distribution",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(10.dp))

        if (totalSeconds == 0L) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(18.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(9.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No HR data for this week",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextTertiary
                )
            }
        } else {
            // Stacked Bar in Canvas
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(18.dp)
            ) {
                val width = size.width
                val height = size.height
                val radius = CornerRadius(height / 2f, height / 2f)

                // Background container
                drawRoundRect(
                    color = Color(0xFF1E2633),
                    size = size,
                    cornerRadius = radius
                )

                var currentX = 0f
                val zonesData = listOf(
                    distribution.z1Seconds to Zone1Blue,
                    distribution.z2Seconds to Zone2Green,
                    distribution.z3Seconds to Zone3Amber,
                    distribution.z4Seconds to Zone4Orange,
                    distribution.z5Seconds to Zone5Red
                )

                zonesData.forEach { (sec, color) ->
                    if (sec > 0) {
                        val segWidth = (sec.toFloat() / totalSeconds.toFloat()) * width
                        drawRoundRect(
                            color = color,
                            topLeft = Offset(currentX, 0f),
                            size = Size(segWidth, height),
                            cornerRadius = radius
                        )
                        currentX += segWidth
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Legend rows
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                ZoneLegendItem("Z1 Recovery (<118)", distribution.z1Seconds, distribution.percentageForZone(1), Zone1Blue)
                ZoneLegendItem("Z2 Aerobic (118-140)", distribution.z2Seconds, distribution.percentageForZone(2), Zone2Green)
                ZoneLegendItem("Z3 Threshold (141-160)", distribution.z3Seconds, distribution.percentageForZone(3), Zone3Amber)
                ZoneLegendItem("Z4 Anaerobic (161-179)", distribution.z4Seconds, distribution.percentageForZone(4), Zone4Orange)
                if (distribution.z5Seconds > 0) {
                    ZoneLegendItem("Z5 Maximum (180+)", distribution.z5Seconds, distribution.percentageForZone(5), Zone5Red)
                }
            }
        }
    }
}

@Composable
private fun ZoneLegendItem(
    title: String,
    seconds: Long,
    percent: Float,
    color: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .background(color, CircleShape)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )
        }
        Text(
            text = String.format(Locale.US, "%s (%.0f%%)", PaceUtils.formatDuration(seconds), percent),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun HeartRateChart(
    points: List<HeartRatePoint>,
    avgHr: Int?,
    modifier: Modifier = Modifier,
    sessionDurationSeconds: Long? = null
) {
    val maxAllowedSec = sessionDurationSeconds ?: points.lastOrNull()?.offsetSeconds ?: 0L
    val displayPoints = if (sessionDurationSeconds != null && sessionDurationSeconds > 0) {
        val filtered = points.filter { it.offsetSeconds <= sessionDurationSeconds }
        if (filtered.isNotEmpty()) filtered else points
    } else {
        points
    }

    if (displayPoints.size < 2) {
        Box(
            modifier = modifier.height(160.dp),
            contentAlignment = Alignment.Center
        ) {
            Text("Insufficient HR samples", color = TextSecondary)
        }
        return
    }

    val minBpm = (displayPoints.minOfOrNull { it.bpm } ?: 100).coerceAtLeast(80)
    val maxBpm = (displayPoints.maxOfOrNull { it.bpm } ?: 180).coerceAtMost(210)
    val bpmRange = (maxBpm - minBpm).coerceAtLeast(20).toFloat()

    val maxDuration = (sessionDurationSeconds ?: displayPoints.last().offsetSeconds).coerceAtLeast(1L).toFloat()
    var scrubbedPoint by remember { mutableStateOf<HeartRatePoint?>(null) }
    val zones = remember { com.example.util.HrZones() }

    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Heart Rate Over Session",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            if (scrubbedPoint != null) {
                val pt = scrubbedPoint!!
                val zoneNumber = HrZoneCalculator.determineZone(pt.bpm, zones)
                val (zoneName, zoneColor) = when (zoneNumber) {
                    1 -> "Z1 Recovery" to Zone1Blue
                    2 -> "Z2 Aerobic" to Zone2Green
                    3 -> "Z3 Threshold" to Zone3Amber
                    4 -> "Z4 Anaerobic" to Zone4Orange
                    else -> "Z5 Max" to Zone5Red
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .background(zoneColor.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(zoneColor, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "${PaceUtils.formatDuration(pt.offsetSeconds)} · ${pt.bpm} bpm ($zoneName)",
                        style = MaterialTheme.typography.labelSmall,
                        color = zoneColor,
                        fontWeight = FontWeight.Bold
                    )
                }
            } else {
                avgHr?.let {
                    Text(
                        text = "Avg: $it bpm · Drag to inspect",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
        ) {
            val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)

            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(points) {
                        detectTapGestures(
                            onTap = { offset ->
                                val fraction = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                                val targetSec = (fraction * maxDuration).toLong()
                                scrubbedPoint = points.minByOrNull { kotlin.math.abs(it.offsetSeconds - targetSec) }
                            }
                        )
                    }
                    .pointerInput(points) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                val fraction = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                                val targetSec = (fraction * maxDuration).toLong()
                                scrubbedPoint = points.minByOrNull { kotlin.math.abs(it.offsetSeconds - targetSec) }
                            },
                            onDrag = { change, _ ->
                                change.consume()
                                val fraction = (change.position.x / size.width.toFloat()).coerceIn(0f, 1f)
                                val targetSec = (fraction * maxDuration).toLong()
                                scrubbedPoint = points.minByOrNull { kotlin.math.abs(it.offsetSeconds - targetSec) }
                            }
                        )
                    }
            ) {
                val width = size.width
                val height = size.height

                // Grid lines
                drawLine(gridColor, Offset(0f, 0f), Offset(width, 0f), 1f)
                drawLine(gridColor, Offset(0f, height), Offset(width, height), 1f)

                // Avg HR reference line
                avgHr?.let { avg ->
                    val avgY = height - (((avg - minBpm).toFloat() / bpmRange) * height)
                    drawLine(
                        color = VoltPrimary.copy(alpha = 0.4f),
                        start = Offset(0f, avgY),
                        end = Offset(width, avgY),
                        strokeWidth = 1.5.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                    )
                }

                // Construct HR curve
                val strokePath = Path()
                val fillPath = Path()

                fillPath.moveTo(0f, height)

                points.forEachIndexed { i, p ->
                    val x = (p.offsetSeconds.toFloat() / maxDuration) * width
                    val y = height - (((p.bpm - minBpm).toFloat() / bpmRange) * height)

                    if (i == 0) {
                        strokePath.moveTo(x, y)
                        fillPath.lineTo(x, y)
                    } else {
                        strokePath.lineTo(x, y)
                        fillPath.lineTo(x, y)
                    }
                }

                fillPath.lineTo(width, height)
                fillPath.close()

                // Draw gradient under graph
                drawPath(
                    path = fillPath,
                    brush = Brush.verticalGradient(
                        colors = listOf(VoltPrimary.copy(alpha = 0.35f), Color.Transparent),
                        startY = 0f,
                        endY = height
                    )
                )

                // Draw stroke line
                drawPath(
                    path = strokePath,
                    color = VoltPrimary,
                    style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                )

                // Draw interactive vertical timeline cursor if user is scrubbing/inspecting
                scrubbedPoint?.let { pt ->
                    val scrubX = (pt.offsetSeconds.toFloat() / maxDuration) * width
                    val scrubY = height - (((pt.bpm - minBpm).toFloat() / bpmRange) * height)

                    // Vertical timeline guideline
                    drawLine(
                        color = VoltPrimary.copy(alpha = 0.9f),
                        start = Offset(scrubX, 0f),
                        end = Offset(scrubX, height),
                        strokeWidth = 2.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f)
                    )

                    // Outer glowing target pulse
                    drawCircle(
                        color = VoltPrimary.copy(alpha = 0.35f),
                        radius = 10.dp.toPx(),
                        center = Offset(scrubX, scrubY)
                    )

                    // Mid circle
                    drawCircle(
                        color = VoltPrimary,
                        radius = 6.dp.toPx(),
                        center = Offset(scrubX, scrubY)
                    )

                    // Center white dot
                    drawCircle(
                        color = Color.White,
                        radius = 2.5.dp.toPx(),
                        center = Offset(scrubX, scrubY)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Time ticks
        val maxDurationSec = maxDuration.toLong()
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("00:00", style = MaterialTheme.typography.labelSmall, color = TextTertiary)
            Text(
                PaceUtils.formatDuration(maxDurationSec / 2),
                style = MaterialTheme.typography.labelSmall,
                color = TextTertiary
            )
            Text(
                PaceUtils.formatDuration(maxDurationSec),
                style = MaterialTheme.typography.labelSmall,
                color = TextTertiary
            )
        }
    }
}
