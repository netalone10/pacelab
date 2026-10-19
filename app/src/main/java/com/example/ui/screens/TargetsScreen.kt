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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.RaceDistance
import com.example.data.model.UserSettings
import com.example.ui.theme.AthleticBorder
import com.example.ui.theme.AthleticDarkCard
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TextTertiary
import com.example.ui.theme.TextWhite
import com.example.ui.theme.VoltOnPrimary
import com.example.ui.theme.VoltPrimary
import com.example.ui.theme.Zone1Blue
import com.example.ui.theme.Zone2Green
import com.example.ui.theme.Zone3Amber
import com.example.ui.theme.Zone4Orange
import com.example.util.PaceUtils
import com.example.util.PredictedTargets
import java.util.Locale

@Composable
fun TargetsScreen(
    userSettings: UserSettings,
    targets: PredictedTargets?,
    onUpdateTargetRace: (RaceDistance) -> Unit,
    onUpdateReferenceRace: (distanceMeters: Double, durationSeconds: Long, avgHr: Int) -> Unit,
    onUseBestRun: () -> Unit,
    modifier: Modifier = Modifier
) {
    var refDistanceText by remember(userSettings.refDistanceMeters) {
        mutableStateOf(String.format(Locale.US, "%.2f", userSettings.refDistanceMeters / 1000.0))
    }
    var refDurationText by remember(userSettings.refDurationSeconds) {
        mutableStateOf(PaceUtils.formatDuration(userSettings.refDurationSeconds))
    }
    var refHrText by remember(userSettings.refAvgHeartRate) {
        mutableStateOf("${userSettings.refAvgHeartRate}")
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Target Race & Paces",
                style = MaterialTheme.typography.titleLarge,
                color = TextWhite,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Calculated using Riegel's power formula: T2 = T1 × (D2 / D1)^1.06",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )
        }

        // Target Race Distance Selector Chips
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
                        text = "GOAL RACE DISTANCE",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary,
                        letterSpacing = 1.sp
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        RaceDistance.values().forEach { distance ->
                            val isSelected = userSettings.targetRaceDistance == distance
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
                                    .clickable { onUpdateTargetRace(distance) }
                                    .padding(vertical = 10.dp)
                                    .testTag("target_race_${distance.name}"),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = distance.label,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (isSelected) VoltPrimary else TextWhite,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }
        }

        // Reference Race Input Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, AthleticBorder, RoundedCornerShape(16.dp)),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = AthleticDarkCard)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "REFERENCE PERFORMANCE",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary,
                            letterSpacing = 1.sp
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clickable(onClick = onUseBestRun)
                                .background(VoltPrimary.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                .testTag("use_best_run_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = VoltPrimary,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Use Best Run",
                                style = MaterialTheme.typography.labelSmall,
                                color = VoltPrimary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = refDistanceText,
                            onValueChange = {
                                refDistanceText = it
                                val km = it.toDoubleOrNull()
                                val sec = PaceUtils.parseDurationToSeconds(refDurationText)
                                val hr = refHrText.toIntOrNull() ?: 151
                                if (km != null && sec != null && km > 0) {
                                    onUpdateReferenceRace(km * 1000.0, sec, hr)
                                }
                            },
                            label = { Text("Distance (km)") },
                            modifier = Modifier.weight(1f).testTag("input_ref_distance"),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = VoltPrimary,
                                unfocusedBorderColor = AthleticBorder,
                                focusedLabelColor = VoltPrimary
                            )
                        )

                        OutlinedTextField(
                            value = refDurationText,
                            onValueChange = {
                                refDurationText = it
                                val km = refDistanceText.toDoubleOrNull() ?: 5.31
                                val sec = PaceUtils.parseDurationToSeconds(it)
                                val hr = refHrText.toIntOrNull() ?: 151
                                if (sec != null && sec > 0) {
                                    onUpdateReferenceRace(km * 1000.0, sec, hr)
                                }
                            },
                            label = { Text("Time (MM:SS)") },
                            modifier = Modifier.weight(1f).testTag("input_ref_duration"),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = VoltPrimary,
                                unfocusedBorderColor = AthleticBorder,
                                focusedLabelColor = VoltPrimary
                            )
                        )

                        OutlinedTextField(
                            value = refHrText,
                            onValueChange = {
                                refHrText = it
                                val km = refDistanceText.toDoubleOrNull() ?: 5.31
                                val sec = PaceUtils.parseDurationToSeconds(refDurationText) ?: 2157L
                                val hr = it.toIntOrNull()
                                if (hr != null && hr > 50) {
                                    onUpdateReferenceRace(km * 1000.0, sec, hr)
                                }
                            },
                            label = { Text("Avg HR") },
                            modifier = Modifier.weight(0.8f).testTag("input_ref_hr"),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = VoltPrimary,
                                unfocusedBorderColor = AthleticBorder,
                                focusedLabelColor = VoltPrimary
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    val refPace = PaceUtils.calculatePaceSeconds(
                        userSettings.refDistanceMeters,
                        userSettings.refDurationSeconds
                    )
                    Text(
                        text = "Base Pace: ${PaceUtils.formatPace(refPace)} · VO2 Max: ~${userSettings.vo2Max.toInt()}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextTertiary
                    )
                }
            }
        }

        // Predicted Race Target Hero Card
        targets?.let { t ->
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.5.dp, VoltPrimary, RoundedCornerShape(18.dp)),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = AthleticDarkCard)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.EmojiEvents,
                                    contentDescription = null,
                                    tint = VoltPrimary,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "PREDICTED ${userSettings.targetRaceDistance.label} FINISH",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = VoltPrimary,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Bottom
                        ) {
                            Column {
                                Text(
                                    text = PaceUtils.formatDuration(t.predictedDurationSeconds),
                                    style = MaterialTheme.typography.displayLarge,
                                    color = TextWhite,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Predicted Race Time",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextSecondary
                                )
                            }

                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = PaceUtils.formatPace(t.predictedPaceSeconds),
                                    style = MaterialTheme.typography.displayMedium,
                                    color = VoltPrimary,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Target Race Pace",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextSecondary
                                )
                            }
                        }
                    }
                }
            }

            // Training Pace Ranges Breakdown
            item {
                Text(
                    text = "Derived Training Pace Ranges",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextWhite,
                    fontWeight = FontWeight.SemiBold
                )
            }

            item {
                TrainingPaceCard(
                    typeTitle = "Easy Run (Recovery & Aerobic)",
                    paceRange = PaceUtils.formatPaceRange(t.easyPaceRange.first, t.easyPaceRange.second),
                    percentage = "115% – 125% race pace",
                    targetHr = "Zone 1 - Zone 2 (< 140 bpm)",
                    accentColor = Zone2Green,
                    purpose = "Builds mitochondrial density, fat oxidation, and durability."
                )
            }

            item {
                TrainingPaceCard(
                    typeTitle = "Long Run (Endurance)",
                    paceRange = PaceUtils.formatPaceRange(t.longRunPaceRange.first, t.longRunPaceRange.second),
                    percentage = "110% – 120% race pace",
                    targetHr = "Zone 2 (118 – 140 bpm)",
                    accentColor = Zone1Blue,
                    purpose = "Develops glycogen storage and mental stamina for 10K completion."
                )
            }

            item {
                TrainingPaceCard(
                    typeTitle = "Tempo Run (Lactate Threshold)",
                    paceRange = PaceUtils.formatPaceRange(t.tempoPaceRange.first, t.tempoPaceRange.second),
                    percentage = "102% – 107% race pace",
                    targetHr = "Zone 3 (141 – 160 bpm)",
                    accentColor = Zone3Amber,
                    purpose = "Comfortably hard sustained effort. Pushes lactate clearance limit."
                )
            }

            item {
                TrainingPaceCard(
                    typeTitle = "Intervals (VO2 Max Speedwork)",
                    paceRange = PaceUtils.formatPaceRange(t.intervalPaceRange.first, t.intervalPaceRange.second),
                    percentage = "95% – 100% race pace",
                    targetHr = "Zone 4 (161 – 179 bpm)",
                    accentColor = Zone4Orange,
                    purpose = "Fast repeats (e.g. 800m-1km) improving cardiovascular ceiling and turnover."
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun TrainingPaceCard(
    typeTitle: String,
    paceRange: String,
    percentage: String,
    targetHr: String,
    accentColor: Color,
    purpose: String
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, AthleticBorder, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = AthleticDarkCard)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(accentColor, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = typeTitle,
                        style = MaterialTheme.typography.titleMedium,
                        color = TextWhite,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Text(
                    text = percentage,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextTertiary
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = paceRange,
                    style = MaterialTheme.typography.titleLarge,
                    color = VoltPrimary,
                    fontWeight = FontWeight.Bold
                )

                Box(
                    modifier = Modifier
                        .background(accentColor.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = targetHr,
                        style = MaterialTheme.typography.labelSmall,
                        color = accentColor,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = purpose,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
        }
    }
}
