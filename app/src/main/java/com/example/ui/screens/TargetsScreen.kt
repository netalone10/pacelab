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
import androidx.compose.material.icons.filled.Timer
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
import com.example.data.model.RunSession
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
import com.example.ui.theme.Zone5Red
import com.example.util.PaceUtils
import com.example.util.PredictedTargets
import com.example.util.PredictionPoint
import java.util.Locale

@Composable
fun TargetsScreen(
    userSettings: UserSettings,
    targets: PredictedTargets?,
    onUpdateTargetRace: (RaceDistance) -> Unit,
    onUpdateReferenceRace: (distanceMeters: Double, durationSeconds: Long, avgHr: Int) -> Unit,
    onUseBestRun: () -> Unit,
    predictionTrend: List<PredictionPoint> = emptyList(),
    suggestedRun: RunSession? = null,
    goalLabel: String = "10K",
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
        // Notification Banner: Faster Recent Run Detected
        if (suggestedRun != null) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, VoltPrimary, RoundedCornerShape(14.dp)),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = AthleticDarkCard)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = VoltPrimary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "FASTER RECENT RUN DETECTED",
                                style = MaterialTheme.typography.labelSmall,
                                color = VoltPrimary,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = String.format(
                                Locale.US,
                                "Your recent run of %.2f km in %s predicts a faster %s finish than your current reference baseline. Apply as reference?",
                                suggestedRun.distanceMeters / 1000.0,
                                PaceUtils.formatDuration(suggestedRun.durationSeconds),
                                goalLabel
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = TextWhite,
                            lineHeight = 18.sp
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Button(
                            onClick = onUseBestRun,
                            colors = ButtonDefaults.buttonColors(containerColor = VoltPrimary, contentColor = VoltOnPrimary),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.testTag("use_suggested_run_button")
                        ) {
                            Text("Use This Run", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // 28-Day Prediction Progression Trend Card
        if (predictionTrend.size >= 2) {
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
                            text = "28-DAY $goalLabel PREDICTION TREND",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        val first = predictionTrend.first().predictedSeconds
                        val last = predictionTrend.last().predictedSeconds
                        val delta = last - first
                        val deltaText = when {
                            delta < 0 -> "${PaceUtils.formatDuration(-delta)} faster than initial baseline"
                            delta > 0 -> "${PaceUtils.formatDuration(delta)} slower than initial baseline"
                            else -> "Consistent with initial baseline"
                        }
                        Text(
                            text = deltaText,
                            style = MaterialTheme.typography.titleMedium,
                            color = if (delta <= 0) VoltPrimary else TextWhite,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Rolling 4-week best predicted race time derived from recorded running workouts.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        predictionTrend.takeLast(8).reversed().forEach { point ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 3.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Week of ${point.weekMonday}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary
                                )
                                Text(
                                    text = PaceUtils.formatDuration(point.predictedSeconds),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextWhite,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }
        }

        // Section Title: Target Race & Paces
        item {
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "Target Race & Training Paces",
                style = MaterialTheme.typography.titleLarge,
                color = TextWhite,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Calculated with Riegel's power formula (T2 = T1 × (D2 / D1)^1.06) to calibrate your race goals and individual training zones.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )
        }

        // Goal Race Distance Selector Chips
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
                        fontWeight = FontWeight.Bold,
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
                            text = "REFERENCE PERFORMANCE BASELINE",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary,
                            fontWeight = FontWeight.Bold,
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
                        text = "Baseline Pace: ${PaceUtils.formatPace(refPace)} · Est. VO2 Max: ~${userSettings.vo2Max.toInt()} ml/kg/min",
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
                                    text = "PREDICTED ${userSettings.targetRaceDistance.label} RACE FINISH",
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
                                    text = "Predicted Finish Time",
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
                                val speedKmh = if (t.predictedPaceSeconds > 0) 3600.0 / t.predictedPaceSeconds else 0.0
                                Text(
                                    text = String.format(Locale.US, "Target Pace · %.1f km/h", speedKmh),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextSecondary
                                )
                            }
                        }
                    }
                }
            }

            // Section Header: Derived Training Pace Zones
            item {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Individualized Training Pace Zones",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextWhite,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Calibrated specifically to your target race pace with heart rate targets, volume guidance, and structured session blueprints.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }

            // Zone 1 & 2: Easy Run
            item {
                TrainingPaceCard(
                    zoneTag = "ZONE 1 - 2 · AEROBIC BASE",
                    typeTitle = "Easy Run (Active Recovery & Aerobic)",
                    paceRange = PaceUtils.formatPaceRange(t.easyPaceRange.first, t.easyPaceRange.second),
                    speedRange = formatSpeedRange(t.easyPaceRange.first, t.easyPaceRange.second),
                    percentage = "115% – 125% race pace",
                    targetHr = "< ${userSettings.z2Max} bpm (60%–70% Max HR)",
                    volumeGuide = "70% – 80% of weekly mileage",
                    accentColor = Zone2Green,
                    purpose = "Builds capillary networks, mitochondrial density, and fat-burning efficiency while keeping systemic fatigue low.",
                    sampleWorkout = "45–60 min relaxed run. You should be able to speak in complete sentences comfortably without pausing."
                )
            }

            // Zone 2: Long Run
            item {
                TrainingPaceCard(
                    zoneTag = "ZONE 2 · ENDURANCE",
                    typeTitle = "Long Run (Stamina & Fuel Economy)",
                    paceRange = PaceUtils.formatPaceRange(t.longRunPaceRange.first, t.longRunPaceRange.second),
                    speedRange = formatSpeedRange(t.longRunPaceRange.first, t.longRunPaceRange.second),
                    percentage = "110% – 120% race pace",
                    targetHr = "${userSettings.z1Max + 1} – ${userSettings.z2Max} bpm (65%–75% Max HR)",
                    volumeGuide = "20% – 25% of weekly mileage",
                    accentColor = Zone1Blue,
                    purpose = "Enhances glycogen sparing, muscular durability, and mental fortitude needed for the final kilometers of your race.",
                    sampleWorkout = "75–90 min steady progression run. Focus on steady pacing, hydration, and nutrition practice throughout."
                )
            }

            // Zone 3: Tempo Run
            item {
                TrainingPaceCard(
                    zoneTag = "ZONE 3 · LACTATE THRESHOLD",
                    typeTitle = "Tempo Run (Threshold Stamina)",
                    paceRange = PaceUtils.formatPaceRange(t.tempoPaceRange.first, t.tempoPaceRange.second),
                    speedRange = formatSpeedRange(t.tempoPaceRange.first, t.tempoPaceRange.second),
                    percentage = "102% – 107% race pace",
                    targetHr = "${userSettings.z2Max + 1} – ${userSettings.z3Max} bpm (82%–88% Max HR)",
                    volumeGuide = "10% – 15% of weekly mileage",
                    accentColor = Zone3Amber,
                    purpose = "Comfortably hard sustained effort. Pushes your lactate clearance threshold higher so you can sustain faster speeds longer.",
                    sampleWorkout = "15 min warmup + 20–25 min sustained tempo block @ threshold pace + 10 min easy jog cooldown."
                )
            }

            // Zone 4: Intervals
            item {
                TrainingPaceCard(
                    zoneTag = "ZONE 4 · VO2 MAX",
                    typeTitle = "Intervals (Aerobic Power & Speedwork)",
                    paceRange = PaceUtils.formatPaceRange(t.intervalPaceRange.first, t.intervalPaceRange.second),
                    speedRange = formatSpeedRange(t.intervalPaceRange.first, t.intervalPaceRange.second),
                    percentage = "95% – 100% race pace",
                    targetHr = "${userSettings.z3Max + 1} – ${userSettings.z4Max} bpm (90%–95% Max HR)",
                    volumeGuide = "5% – 8% of weekly mileage",
                    accentColor = Zone4Orange,
                    purpose = "Expands your maximal oxygen uptake ceiling (VO2 Max), cardiovascular pumping capacity, and stride turnover.",
                    sampleWorkout = "5 × 1,000m @ interval pace with 2:00 slow jog recovery between repeats + thorough warmup and cooldown."
                )
            }

            // Zone 5: Repetitions
            item {
                TrainingPaceCard(
                    zoneTag = "ZONE 5 · ANAEROBIC SPEED",
                    typeTitle = "Repetitions (Neuromuscular Form & Speed)",
                    paceRange = PaceUtils.formatPaceRange(t.repetitionPaceRange.first, t.repetitionPaceRange.second),
                    speedRange = formatSpeedRange(t.repetitionPaceRange.first, t.repetitionPaceRange.second),
                    percentage = "88% – 94% race pace",
                    targetHr = "> ${userSettings.z4Max} bpm (95%+ Max HR)",
                    volumeGuide = "< 5% of weekly mileage",
                    accentColor = Zone5Red,
                    purpose = "Develops neuromuscular mechanics, running economy, and anaerobic power with complete rest between efforts.",
                    sampleWorkout = "8 × 400m fast repeats with 2:30 full standing or walking recovery. Focus on relaxed posture and quick turnover."
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(28.dp))
        }
    }
}

@Composable
private fun TrainingPaceCard(
    zoneTag: String,
    typeTitle: String,
    paceRange: String,
    speedRange: String,
    percentage: String,
    targetHr: String,
    volumeGuide: String,
    accentColor: Color,
    purpose: String,
    sampleWorkout: String
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, AthleticBorder, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = AthleticDarkCard)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Zone Tag & Percentage Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .background(accentColor.copy(alpha = 0.18f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = zoneTag,
                            style = MaterialTheme.typography.labelSmall,
                            color = accentColor,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp
                        )
                    }
                }

                Text(
                    text = percentage,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextTertiary,
                    fontSize = 11.sp
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Title
            Text(
                text = typeTitle,
                style = MaterialTheme.typography.titleMedium,
                color = TextWhite,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Main Stat Strip: Pace & HR
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF14171E), RoundedCornerShape(10.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = paceRange,
                        style = MaterialTheme.typography.titleLarge,
                        color = VoltPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = speedRange,
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                }

                Box(
                    modifier = Modifier
                        .background(accentColor.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = targetHr,
                        style = MaterialTheme.typography.labelSmall,
                        color = accentColor,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Recommended Volume Allocation Chip
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.FitnessCenter,
                    contentDescription = null,
                    tint = TextTertiary,
                    modifier = Modifier.size(13.dp)
                )
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                    text = "Recommended volume: $volumeGuide",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextTertiary,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Purpose
            Text(
                text = purpose,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                lineHeight = 17.sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Sample Workout Callout
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 7.dp)
            ) {
                Row(verticalAlignment = Alignment.Top) {
                    Text(
                        text = "Session: ",
                        style = MaterialTheme.typography.labelSmall,
                        color = VoltPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = sampleWorkout,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextWhite,
                        fontSize = 11.sp,
                        lineHeight = 15.sp
                    )
                }
            }
        }
    }
}

private fun formatSpeedRange(fasterSec: Int, slowerSec: Int): String {
    if (fasterSec <= 0 || slowerSec <= 0) return "-- km/h"
    val maxSpeed = 3600.0 / fasterSec
    val minSpeed = 3600.0 / slowerSec
    return String.format(Locale.US, "%.1f – %.1f km/h", minSpeed, maxSpeed)
}
