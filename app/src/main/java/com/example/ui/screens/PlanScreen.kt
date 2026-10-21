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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.RaceEvent
import com.example.data.model.UserSettings
import com.example.ui.components.RaceCountdownCard
import com.example.ui.theme.AthleticBorder
import com.example.ui.theme.AthleticDarkCard
import com.example.ui.theme.StatusSuccess
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TextTertiary
import com.example.ui.theme.TextWhite
import com.example.ui.theme.VoltPrimary
import com.example.ui.theme.Zone1Blue
import com.example.ui.theme.Zone2Green
import com.example.ui.theme.Zone3Amber
import com.example.ui.theme.Zone4Orange
import com.example.util.PaceUtils
import com.example.util.PeriodizedPlanWeek
import com.example.util.PlanDayItem
import com.example.util.TrainingPhase
import com.example.util.WorkoutType
import java.time.DayOfWeek
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun PlanScreen(
    userSettings: UserSettings,
    targetRace: RaceEvent?,
    planWeeks: List<PeriodizedPlanWeek>,
    selectedWeekIndex: Int,
    onSelectWeekIndex: (Int) -> Unit,
    onUpdateTrainingDays: (Int) -> Unit,
    onToggleDayDone: (weekIndex: Int, dayOfWeek: DayOfWeek, isDone: Boolean) -> Unit,
    onOpenRaceSetup: () -> Unit,
    onDeleteRace: () -> Unit,
    planNote: String? = null,
    modifier: Modifier = Modifier
) {
    val currentWeek = planWeeks.find { it.weekIndex == selectedWeekIndex } ?: planWeeks.firstOrNull()
    val monthDayFormatter = DateTimeFormatter.ofPattern("MMM d", Locale.US)

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (planNote != null) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, Zone3Amber, RoundedCornerShape(12.dp)),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = AthleticDarkCard)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "PLAN DISESUAIKAN",
                            style = MaterialTheme.typography.labelSmall,
                            color = Zone3Amber,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = planNote,
                            style = MaterialTheme.typography.bodySmall,
                            color = TextWhite
                        )
                    }
                }
            }
        }
        item {
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = if (targetRace != null) "Race Periodized Plan" else "10K Progression Plan",
                        style = MaterialTheme.typography.titleLarge,
                        color = TextWhite,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (targetRace != null) {
                            "Base · Build · Peak · 1-Wk Sharp Taper before Race Day"
                        } else {
                            "Max 10% weekly volume bump · 4th-week recovery"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                }
            }
        }

        // Target Race Countdown & Calendar Card
        item {
            RaceCountdownCard(
                raceEvent = targetRace,
                onOpenSetupDialog = onOpenRaceSetup,
                onDeleteRace = onDeleteRace,
                modifier = Modifier.testTag("race_countdown_card")
            )
        }

        // Training Days Per Week Selector (2, 3, or 4 days)
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, AthleticBorder, RoundedCornerShape(16.dp)),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = AthleticDarkCard)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "WEEKLY TRAINING DAYS",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary,
                        letterSpacing = 1.sp
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(2, 3, 4).forEach { days ->
                            val isSelected = userSettings.trainingDaysPerWeek == days
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .border(
                                        1.dp,
                                        if (isSelected) VoltPrimary else AthleticBorder,
                                        RoundedCornerShape(10.dp)
                                    )
                                    .background(
                                        if (isSelected) VoltPrimary.copy(alpha = 0.15f) else Color.Transparent,
                                        RoundedCornerShape(10.dp)
                                    )
                                    .clickable { onUpdateTrainingDays(days) }
                                    .padding(vertical = 10.dp)
                                    .testTag("plan_days_$days"),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = "$days Days",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = if (isSelected) VoltPrimary else TextWhite,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = when (days) {
                                            2 -> "Quality + Long"
                                            3 -> "Easy, Tempo, Long"
                                            else -> "Balanced 4-Day"
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (isSelected) VoltPrimary else TextTertiary,
                                        fontSize = 10.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Periodized Progression Weeks Ribbon
        if (planWeeks.isNotEmpty()) {
            item {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "PLAN TIMELINE (${planWeeks.size} WEEKS)",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary,
                            letterSpacing = 1.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(planWeeks) { week ->
                            val isSelected = week.weekIndex == selectedWeekIndex
                            val weekDistKm = week.totalTargetDistanceMeters / 1000.0
                            val phaseColor = Color(week.phase.badgeColorHex)

                            Box(
                                modifier = Modifier
                                    .width(96.dp)
                                    .border(
                                        1.5.dp,
                                        if (isSelected) VoltPrimary else AthleticBorder,
                                        RoundedCornerShape(12.dp)
                                    )
                                    .background(
                                        if (isSelected) VoltPrimary.copy(alpha = 0.12f) else AthleticDarkCard,
                                        RoundedCornerShape(12.dp)
                                    )
                                    .clickable { onSelectWeekIndex(week.weekIndex) }
                                    .padding(vertical = 10.dp, horizontal = 6.dp)
                                    .testTag("week_tab_${week.weekIndex}"),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    // Phase badge pill
                                    Box(
                                        modifier = Modifier
                                            .background(phaseColor.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                                            .padding(horizontal = 4.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = when (week.phase) {
                                                TrainingPhase.BASE -> "BASE"
                                                TrainingPhase.BUILD -> "BUILD"
                                                TrainingPhase.PEAK -> "PEAK"
                                                TrainingPhase.TAPER -> "TAPER"
                                                TrainingPhase.RACE -> "RACE"
                                            },
                                            style = MaterialTheme.typography.labelSmall,
                                            color = phaseColor,
                                            fontSize = 8.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(4.dp))

                                    Text(
                                        text = "W${week.weekIndex}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = if (isSelected) VoltPrimary else TextWhite,
                                        fontWeight = FontWeight.Bold
                                    )

                                    Spacer(modifier = Modifier.height(2.dp))

                                    Text(
                                        text = String.format(Locale.US, "%.1f km", weekDistKm),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (isSelected) VoltPrimary else TextSecondary,
                                        fontWeight = FontWeight.SemiBold
                                    )

                                    Spacer(modifier = Modifier.height(2.dp))

                                    Text(
                                        text = week.weekStartMonday.format(monthDayFormatter),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TextTertiary,
                                        fontSize = 9.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Current Selected Week Overview Banner
        currentWeek?.let { week ->
            val phaseColor = Color(week.phase.badgeColorHex)
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, phaseColor.copy(alpha = 0.35f), RoundedCornerShape(14.dp)),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = AthleticDarkCard)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = when (week.phase) {
                                    TrainingPhase.BASE -> Icons.Default.FitnessCenter
                                    TrainingPhase.BUILD -> Icons.Default.TrendingUp
                                    TrainingPhase.PEAK -> Icons.Default.Speed
                                    TrainingPhase.TAPER -> Icons.Default.SelfImprovement
                                    TrainingPhase.RACE -> Icons.Default.CheckCircle
                                },
                                contentDescription = null,
                                tint = phaseColor,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "Week ${week.weekIndex} of ${week.totalWeeks} · ${week.phase.title}",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = TextWhite,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "${week.weekStartMonday.format(monthDayFormatter)} - ${week.weekEndSunday.format(monthDayFormatter)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextSecondary
                                )
                            }
                        }

                        val doneCount = week.days.count { it.isDone }
                        Text(
                            text = "$doneCount/${week.days.size} done",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (doneCount == week.days.size) StatusSuccess else TextSecondary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            // Planned Days for Selected Week
            items(week.days) { dayItem ->
                PlannedDayCard(
                    dayItem = dayItem,
                    onToggleDone = {
                        onToggleDayDone(week.weekIndex, dayItem.dayOfWeek, !dayItem.isDone)
                    }
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
fun PlannedDayCard(
    dayItem: PlanDayItem,
    onToggleDone: () -> Unit
) {
    val typeColor = when (dayItem.type) {
        WorkoutType.EASY -> Zone2Green
        WorkoutType.TEMPO -> Zone3Amber
        WorkoutType.INTERVALS -> Zone4Orange
        WorkoutType.LONG_RUN -> Zone1Blue
    }

    val dayName = dayItem.dayOfWeek.name.lowercase()
        .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.US) else it.toString() }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                1.dp,
                if (dayItem.isDone) StatusSuccess.copy(alpha = 0.5f) else AthleticBorder,
                RoundedCornerShape(16.dp)
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = AthleticDarkCard)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header: Day + Type Badge + Checkmark Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(typeColor, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "$dayName · ${dayItem.type.title}",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextWhite,
                        fontWeight = FontWeight.Bold
                    )
                }

                IconButton(
                    onClick = onToggleDone,
                    modifier = Modifier.size(36.dp).testTag("check_day_${dayItem.dayOfWeek.name}")
                ) {
                    Icon(
                        imageVector = if (dayItem.isDone) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                        contentDescription = "Toggle Complete",
                        tint = if (dayItem.isDone) StatusSuccess else TextSecondary,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Main stats: Target Distance & Target Pace Range
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = PaceUtils.formatDistanceKm(dayItem.targetDistanceMeters),
                        style = MaterialTheme.typography.displayMedium,
                        color = TextWhite,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Target Volume",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextTertiary
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = PaceUtils.formatPaceRange(dayItem.minPaceSec, dayItem.maxPaceSec),
                        style = MaterialTheme.typography.titleLarge,
                        color = VoltPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Target Pace Range",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextTertiary
                    )
                }
            }

            dayItem.structure?.let { structure ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = structure,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Footer: Target Heart Rate Zone Chip & Matched Session Info
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .background(typeColor.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "Target: ${dayItem.targetHrZone}",
                        style = MaterialTheme.typography.labelSmall,
                        color = typeColor,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                if (dayItem.isDone) {
                    val matchDist = dayItem.matchedDistanceMeters?.let { PaceUtils.formatDistanceKm(it) }
                    val matchPace = dayItem.matchedPaceSec?.let { PaceUtils.formatPace(it) }
                    Text(
                        text = if (matchDist != null && matchPace != null) {
                            "Synced: $matchDist @ $matchPace"
                        } else "Marked Done",
                        style = MaterialTheme.typography.labelSmall,
                        color = StatusSuccess,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}
