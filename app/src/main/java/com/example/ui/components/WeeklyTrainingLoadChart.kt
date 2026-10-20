package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingFlat
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.AthleticBorder
import com.example.ui.theme.AthleticDarkCard
import com.example.ui.theme.AthleticDarkSurfaceVariant
import com.example.ui.theme.StatusDanger
import com.example.ui.theme.StatusSuccess
import com.example.ui.theme.StatusWarning
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TextTertiary
import com.example.ui.theme.TextWhite
import com.example.ui.theme.VoltPrimary
import com.example.ui.theme.Zone1Blue
import com.example.ui.theme.Zone2Green
import com.example.util.FourWeekTrainingLoadSummary
import com.example.util.PaceUtils
import com.example.util.TrainingLoadStatus
import com.example.util.WeeklyTrainingLoadItem
import java.util.Locale
import kotlin.math.max

/**
 * Recharts-inspired interactive Bar Chart visualizing Weekly Training Load
 * across the last 4 weeks calculated from Health Connect running session data.
 */
@Composable
fun WeeklyTrainingLoadBarChart(
    summary: FourWeekTrainingLoadSummary,
    modifier: Modifier = Modifier
) {
    val weeks = summary.weeks
    var selectedWeekIndex by remember { mutableStateOf<Int?>(weeks.lastIndex) }
    val selectedItem = selectedWeekIndex?.let { weeks.getOrNull(it) } ?: weeks.lastOrNull()

    val maxLoad = max(100.0, (weeks.maxOfOrNull { it.loadScore } ?: 100).toDouble())

    Card(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, AthleticBorder, RoundedCornerShape(16.dp))
            .testTag("training_load_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = AthleticDarkCard)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header: Title & Overall Status Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Weekly Training Load",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextWhite,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Beban latihan 4 minggu terakhir (Health Connect)",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }

                StatusChip(status = summary.status)
            }

            // Interactive Tooltip / Detail Strip
            selectedItem?.let { item ->
                TrainingLoadDetailTooltip(item = item)
            }

            // Canvas Bar Chart
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
            ) {
                val averageLoad = summary.fourWeekAverageLoad
                val barCount = weeks.size // 4
                val avgRatio = if (maxLoad > 0) (averageLoad.toFloat() / maxLoad.toFloat()).coerceIn(0f, 1f) else 0f

                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                        .pointerInput(weeks) {
                            detectTapGestures { offset ->
                                val width = size.width
                                val segmentWidth = width / barCount.toFloat()
                                val tappedIndex = (offset.x / segmentWidth).toInt().coerceIn(0, weeks.lastIndex)
                                selectedWeekIndex = tappedIndex
                            }
                        }
                ) {
                    val width = size.width
                    val height = size.height
                    val bottomPadding = 28.dp.toPx()
                    val chartHeight = height - bottomPadding
                    val barWidth = (width / (barCount * 2f)).coerceIn(24.dp.toPx(), 44.dp.toPx())
                    val slotWidth = width / barCount.toFloat()

                    // Average reference line
                    if (averageLoad > 0) {
                        val avgY = chartHeight - (avgRatio * chartHeight * 0.85f)
                        drawLine(
                            color = VoltPrimary.copy(alpha = 0.35f),
                            start = Offset(0f, avgY),
                            end = Offset(width, avgY),
                            strokeWidth = 1.5.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f), 0f)
                        )
                    }

                    // Baseline
                    drawLine(
                        color = Color(0xFF263342),
                        start = Offset(0f, chartHeight),
                        end = Offset(width, chartHeight),
                        strokeWidth = 1.dp.toPx()
                    )

                    // Draw the 4 bars
                    weeks.forEachIndexed { index, item ->
                        val centerX = (index * slotWidth) + (slotWidth / 2f)
                        val loadRatio = if (maxLoad > 0) (item.loadScore.toFloat() / maxLoad.toFloat()).coerceIn(0f, 1f) else 0f
                        val barH = (loadRatio * chartHeight * 0.85f).coerceAtLeast(6.dp.toPx())
                        val barLeft = centerX - (barWidth / 2f)
                        val barTop = chartHeight - barH
                        val isSelected = (index == selectedWeekIndex)

                        val cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())

                        // Bar background track (translucent)
                        drawRoundRect(
                            color = Color(0xFF1B2330),
                            topLeft = Offset(barLeft, 0f),
                            size = Size(barWidth, chartHeight),
                            cornerRadius = cornerRadius
                        )

                        // Bar fill
                        val barBrush = when {
                            isSelected -> Brush.verticalGradient(
                                colors = listOf(VoltPrimary, Zone2Green),
                                startY = barTop,
                                endY = chartHeight
                            )
                            item.isCurrentWeek -> Brush.verticalGradient(
                                colors = listOf(VoltPrimary.copy(alpha = 0.85f), Color(0xFF709900)),
                                startY = barTop,
                                endY = chartHeight
                            )
                            else -> Brush.verticalGradient(
                                colors = listOf(Zone1Blue.copy(alpha = 0.7f), Color(0xFF1E3A5F)),
                                startY = barTop,
                                endY = chartHeight
                            )
                        }

                        drawRoundRect(
                            brush = barBrush,
                            topLeft = Offset(barLeft, barTop),
                            size = Size(barWidth, barH),
                            cornerRadius = cornerRadius
                        )

                        // Highlight border for selected bar
                        if (isSelected) {
                            drawRoundRect(
                                color = Color.White,
                                topLeft = Offset(barLeft - 1.dp.toPx(), barTop - 1.dp.toPx()),
                                size = Size(barWidth + 2.dp.toPx(), barH + 2.dp.toPx()),
                                cornerRadius = cornerRadius,
                                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx())
                            )
                        }
                    }
                }
            }

            // X-Axis Labels (W-3, W-2, W-1, Current)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                weeks.forEachIndexed { index, item ->
                    val isSelected = (index == selectedWeekIndex)
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { selectedWeekIndex = index }
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = item.weekLabel,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) VoltPrimary else TextSecondary
                        )
                        Text(
                            text = "${item.loadScore} pts",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isSelected) TextWhite else TextTertiary
                        )
                    }
                }
            }

            // Metric Summary Strip (ACWR, 4-Week Average, Delta)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(AthleticDarkSurfaceVariant, RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // ACWR
                Column {
                    Text(
                        text = "Rasio Beban (ACWR)",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextTertiary
                    )
                    Text(
                        text = summary.acuteChronicRatio?.let { String.format(Locale.US, "%.2f", it) } ?: "--",
                        style = MaterialTheme.typography.titleSmall,
                        color = VoltPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }

                // 4-Week Average
                Column {
                    Text(
                        text = "Rata-rata 4 Mgg",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextTertiary
                    )
                    Text(
                        text = "${summary.fourWeekAverageLoad} pts",
                        style = MaterialTheme.typography.titleSmall,
                        color = TextWhite,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                // Progression vs W-1
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "Progres vs W-1",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextTertiary
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val pct = summary.progressionPercent
                        if (pct != null) {
                            val icon = when {
                                pct > 1.0 -> Icons.AutoMirrored.Filled.TrendingUp
                                pct < -1.0 -> Icons.AutoMirrored.Filled.TrendingDown
                                else -> Icons.AutoMirrored.Filled.TrendingFlat
                            }
                            val color = when {
                                pct > 25.0 -> StatusDanger
                                pct < -20.0 -> Zone1Blue
                                else -> StatusSuccess
                            }
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = color,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = String.format(Locale.US, "%+.1f%%", pct),
                                style = MaterialTheme.typography.titleSmall,
                                color = color,
                                fontWeight = FontWeight.Bold
                            )
                        } else {
                            Text(
                                text = "--",
                                style = MaterialTheme.typography.titleSmall,
                                color = TextTertiary
                            )
                        }
                    }
                }
            }

            // Coach Feedback / Insight Text
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF131922), RoundedCornerShape(10.dp))
                    .padding(10.dp),
                verticalAlignment = Alignment.Top
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = VoltPrimary,
                    modifier = Modifier
                        .size(16.dp)
                        .padding(top = 2.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = summary.feedbackMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    lineHeight = 18.sp
                )
            }
        }
    }
}

@Composable
private fun TrainingLoadDetailTooltip(item: WeeklyTrainingLoadItem) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF1B2332), RoundedCornerShape(10.dp))
            .border(1.dp, Color(0xFF2B3A4F), RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = "${item.weekLabel} • ${item.dateRangeLabel}",
                style = MaterialTheme.typography.labelMedium,
                color = VoltPrimary,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "${item.sessionCount} sesi lari • ${PaceUtils.formatDuration(item.durationMinutes * 60L)}",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
        }

        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = "${item.loadScore} Load Pts",
                style = MaterialTheme.typography.titleSmall,
                color = TextWhite,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = String.format(Locale.US, "%.1f km", item.distanceKm),
                style = MaterialTheme.typography.labelSmall,
                color = Zone2Green
            )
        }
    }
}

@Composable
private fun StatusChip(status: TrainingLoadStatus) {
    val (bgColor, textColor) = when (status) {
        TrainingLoadStatus.OPTIMAL -> Color(0xFF1B331E) to Zone2Green
        TrainingLoadStatus.MAINTAINING -> Color(0xFF1C2B3A) to Zone1Blue
        TrainingLoadStatus.OVERREACHING -> Color(0xFF382414) to StatusWarning
        TrainingLoadStatus.RECOVERY -> Color(0xFF1E2838) to Zone1Blue
        TrainingLoadStatus.INSUFFICIENT -> Color(0xFF22262C) to TextTertiary
    }

    Box(
        modifier = Modifier
            .background(bgColor, RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = status.label,
            style = MaterialTheme.typography.labelSmall,
            color = textColor,
            fontWeight = FontWeight.Bold
        )
    }
}
