package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.PaceLabUiState
import com.example.ui.components.DailyVolumeBarChart
import com.example.ui.components.HealthStatusBanner
import com.example.ui.components.HrZoneDistributionBar
import com.example.ui.components.MetricCard
import com.example.ui.components.WeeklyTrainingLoadBarChart
import com.example.ui.theme.AthleticBorder
import com.example.ui.theme.AthleticDarkCard
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TextWhite
import com.example.ui.theme.VoltPrimary
import com.example.util.PaceUtils
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun WeeklyScreen(
    state: PaceLabUiState,
    onPreviousWeek: () -> Unit,
    onNextWeek: () -> Unit,
    onSync: () -> Unit,
    onRequestPermissions: () -> Unit,
    onOpenPlayStore: () -> Unit,
    onLoadDemoData: () -> Unit,
    modifier: Modifier = Modifier
) {
    val summary = state.weeklySummary
    val weekFormatter = DateTimeFormatter.ofPattern("MMM d", Locale.US)

    val weekTitle = if (summary != null) {
        val startStr = summary.weekStartMonday.format(weekFormatter)
        val endStr = summary.weekEndSunday.format(weekFormatter)
        "$startStr – $endStr, ${summary.weekEndSunday.year}"
    } else "Current Week"

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(4.dp))
            // Health Connect banner if needed
            HealthStatusBanner(
                status = state.healthConnectStatus,
                hasPermissions = state.hasHealthPermissions,
                isUsingDemo = state.isUsingDemo,
                onRequestPermissions = onRequestPermissions,
                onOpenPlayStore = onOpenPlayStore,
                onLoadDemoData = onLoadDemoData
            )
        }

        // Week Selector Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, AthleticBorder, RoundedCornerShape(16.dp)),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = AthleticDarkCard)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onPreviousWeek,
                        modifier = Modifier.testTag("prev_week_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Previous Week",
                            tint = VoltPrimary
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.CalendarToday,
                            contentDescription = null,
                            tint = TextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = weekTitle,
                            style = MaterialTheme.typography.titleMedium,
                            color = TextWhite,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = onNextWeek,
                            modifier = Modifier.testTag("next_week_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = "Next Week",
                                tint = VoltPrimary
                            )
                        }

                        IconButton(
                            onClick = onSync,
                            modifier = Modifier.testTag("sync_button")
                        ) {
                            if (state.isSyncing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = VoltPrimary
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Sync",
                                    tint = VoltPrimary
                                )
                            }
                        }
                    }
                }
            }
        }

        // Headline Metrics Grid
        item {
            val distKm = (summary?.totalDistanceMeters ?: 0.0) / 1000.0
            val runsCount = summary?.runCount ?: 0
            val totalDurationSec = summary?.totalDurationSeconds ?: 0L
            val paceSec = summary?.avgPaceSeconds ?: 0
            val avgHr = summary?.avgHeartRate

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Top Row: Distance + Runs Count
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    MetricCard(
                        title = "Total Distance",
                        value = String.format(Locale.US, "%.2f", distKm),
                        unit = "km",
                        deltaPercent = summary?.comparisonPercentVsPrevious,
                        icon = Icons.Default.DirectionsRun,
                        modifier = Modifier.weight(1.3f).testTag("metric_distance")
                    )
                    MetricCard(
                        title = "Sessions",
                        value = "$runsCount",
                        unit = "runs",
                        modifier = Modifier.weight(0.9f).testTag("metric_runs")
                    )
                }

                // Middle Row: Total Time + Average Pace
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    MetricCard(
                        title = "Total Time",
                        value = PaceUtils.formatDuration(totalDurationSec),
                        icon = Icons.Default.Timer,
                        modifier = Modifier.weight(1f).testTag("metric_duration")
                    )
                    MetricCard(
                        title = "Average Pace",
                        value = PaceUtils.formatPace(paceSec),
                        icon = Icons.Default.Speed,
                        modifier = Modifier.weight(1.2f).testTag("metric_avg_pace")
                    )
                }

                // Bottom Row: Average Heart Rate
                MetricCard(
                    title = "Average Heart Rate",
                    value = avgHr?.let { "$it" } ?: "--",
                    unit = "bpm",
                    icon = Icons.Default.Favorite,
                    modifier = Modifier.fillMaxWidth().testTag("metric_avg_hr")
                )
            }
        }

        // 4-Week Progression Training Load Bar Chart (Recharts-style)
        item {
            state.fourWeekTrainingLoad?.let { loadSummary ->
                WeeklyTrainingLoadBarChart(
                    summary = loadSummary,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // Daily Volume Bar Chart
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, AthleticBorder, RoundedCornerShape(16.dp)),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = AthleticDarkCard)
            ) {
                Box(modifier = Modifier.padding(16.dp)) {
                    DailyVolumeBarChart(
                        dailyKmMap = summary?.dailyDistanceKm ?: emptyMap(),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        // Heart Rate Zone Breakdown
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, AthleticBorder, RoundedCornerShape(16.dp)),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = AthleticDarkCard)
            ) {
                Box(modifier = Modifier.padding(16.dp)) {
                    summary?.hrDistribution?.let { distribution ->
                        HrZoneDistributionBar(
                            distribution = distribution,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
