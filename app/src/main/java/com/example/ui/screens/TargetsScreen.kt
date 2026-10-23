package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.example.ui.theme.AthleticDarkSurface
import com.example.ui.theme.StatusSuccess
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
import com.example.util.RiegelCalculator
import com.example.util.VdotCalculator
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
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
    onUpdateHrZones: ((z1Max: Int, z2Max: Int, z3Max: Int, z4Max: Int, maxHr: Int) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var showGoalBottomSheet by rememberSaveable { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        // Top Athletic Segmented Tab Selector
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 12.dp)
                .border(1.dp, AthleticBorder, RoundedCornerShape(14.dp)),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = AthleticDarkCard)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Tab 0: Target & Paces
                val isPacesSelected = selectedTab == 0
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(
                            if (isPacesSelected) VoltPrimary else Color.Transparent,
                            RoundedCornerShape(10.dp)
                        )
                        .clickable { selectedTab = 0 }
                        .padding(vertical = 10.dp)
                        .testTag("tab_target_paces"),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.EmojiEvents,
                            contentDescription = null,
                            tint = if (isPacesSelected) VoltOnPrimary else TextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Target & Paces",
                            style = MaterialTheme.typography.labelLarge,
                            color = if (isPacesSelected) VoltOnPrimary else TextWhite,
                            fontWeight = if (isPacesSelected) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }

                // Tab 1: Baseline & HR
                val isBaselineSelected = selectedTab == 1
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(
                            if (isBaselineSelected) VoltPrimary else Color.Transparent,
                            RoundedCornerShape(10.dp)
                        )
                        .clickable { selectedTab = 1 }
                        .padding(vertical = 10.dp)
                        .testTag("tab_baseline_hr"),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = null,
                            tint = if (isBaselineSelected) VoltOnPrimary else TextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Baseline & HR",
                            style = MaterialTheme.typography.labelLarge,
                            color = if (isBaselineSelected) VoltOnPrimary else TextWhite,
                            fontWeight = if (isBaselineSelected) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
            }
        }

        // Tab Content Switching
        when (selectedTab) {
            0 -> TargetPacesTabContent(
                userSettings = userSettings,
                targets = targets,
                suggestedRun = suggestedRun,
                goalLabel = goalLabel,
                predictionTrend = predictionTrend,
                onUseBestRun = onUseBestRun,
                onOpenGoalSheet = { showGoalBottomSheet = true }
            )
            1 -> BaselineAndHrTabContent(
                userSettings = userSettings,
                onUpdateReferenceRace = onUpdateReferenceRace,
                onUseBestRun = onUseBestRun,
                onUpdateHrZones = onUpdateHrZones
            )
        }
    }

    // Modal Bottom Sheet: Goal Race Selection
    if (showGoalBottomSheet) {
        ModalBottomSheet(
            onDismissRequest = { showGoalBottomSheet = false },
            sheetState = sheetState,
            containerColor = AthleticDarkSurface,
            contentColor = TextWhite,
            dragHandle = {
                Box(
                    modifier = Modifier
                        .padding(vertical = 10.dp)
                        .size(width = 38.dp, height = 4.dp)
                        .background(TextTertiary.copy(alpha = 0.5f), CircleShape)
                )
            }
        ) {
            GoalRaceSelectorSheet(
                selectedDistance = userSettings.targetRaceDistance,
                onSelectDistance = { distance ->
                    onUpdateTargetRace(distance)
                    showGoalBottomSheet = false
                },
                onClose = { showGoalBottomSheet = false }
            )
        }
    }
}

/**
 * Tab 1: Minimalist Hero Summary, 28-day trend, and comprehensive Training Pace Zones.
 */
@Composable
private fun TargetPacesTabContent(
    userSettings: UserSettings,
    targets: PredictedTargets?,
    suggestedRun: RunSession?,
    goalLabel: String,
    predictionTrend: List<PredictionPoint>,
    onUseBestRun: () -> Unit,
    onOpenGoalSheet: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
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
                            colors = ButtonDefaults.buttonColors(
                                containerColor = VoltPrimary,
                                contentColor = VoltOnPrimary
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.testTag("use_suggested_run_button")
                        ) {
                            Text("Use This Run", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Minimalist Hero Summary Card
        item {
            val vdotVal = VdotCalculator.vdot(
                userSettings.refDistanceMeters,
                userSettings.refDurationSeconds
            )
            val vdotText = vdotVal?.let { String.format(Locale.US, "%.0f", it) } ?: "--"
            val fitnessCategory = when {
                vdotVal == null -> "Calibrating"
                vdotVal < 35.0 -> "Aerobic Base"
                vdotVal < 45.0 -> "Intermediate"
                vdotVal < 55.0 -> "Advanced Club"
                vdotVal < 65.0 -> "Regional Elite"
                else -> "National Class"
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.5.dp, VoltPrimary.copy(alpha = 0.8f), RoundedCornerShape(20.dp)),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = AthleticDarkCard)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    // Header row: Target Race Badge + Configure Goal Action
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .background(VoltPrimary.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.EmojiEvents,
                                        contentDescription = null,
                                        tint = VoltPrimary,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(modifier = Modifier.width(5.dp))
                                    Text(
                                        text = "${userSettings.targetRaceDistance.label} RACE GOAL",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = VoltPrimary,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 0.8.sp
                                    )
                                }
                            }
                        }

                        // Configure Goal Button
                        OutlinedButton(
                            onClick = onOpenGoalSheet,
                            shape = RoundedCornerShape(8.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, VoltPrimary),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = VoltPrimary),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                horizontal = 10.dp,
                                vertical = 4.dp
                            ),
                            modifier = Modifier.testTag("configure_target_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Configure Goal",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Main Metric Row: Predicted Finish Time & Target Pace
                    targets?.let { t ->
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
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 40.sp
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
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 28.sp
                                )
                                val speedKmh = if (t.predictedPaceSeconds > 0) 3600.0 / t.predictedPaceSeconds else 0.0
                                Text(
                                    text = String.format(Locale.US, "Target Pace · %.1f km/h", speedKmh),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextSecondary
                                )
                            }
                        }
                    } ?: run {
                        Text(
                            text = "--:--",
                            style = MaterialTheme.typography.displayLarge,
                            color = TextSecondary
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Athletic Chips: VDOT score + Baseline anchor
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .background(Color(0xFF131922), RoundedCornerShape(8.dp))
                                .padding(horizontal = 10.dp, vertical = 7.dp)
                        ) {
                            Column {
                                Text(
                                    text = "ESTIMATED VDOT",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextTertiary,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "$vdotText · $fitnessCategory",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = VoltPrimary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .weight(1.3f)
                                .background(Color(0xFF131922), RoundedCornerShape(8.dp))
                                .padding(horizontal = 10.dp, vertical = 7.dp)
                        ) {
                            Column {
                                Text(
                                    text = "REFERENCE ANCHOR",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextTertiary,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                val refKm = userSettings.refDistanceMeters / 1000.0
                                val refTime = PaceUtils.formatDuration(userSettings.refDurationSeconds)
                                Text(
                                    text = String.format(Locale.US, "%.1f km in %s", refKm, refTime),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = TextWhite,
                                    fontWeight = FontWeight.Medium
                                )
                            }
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
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Calculated from ${predictionTrend.size} qualifying training runs over the last 4 weeks.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextTertiary
                        )
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
        targets?.let { t ->
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

/**
 * Tab 2: Reference Run Baseline Calibration, Live Equivalent Race Projections,
 * Heart Rate Zone Customization, and Physiological Science.
 */
@Composable
private fun BaselineAndHrTabContent(
    userSettings: UserSettings,
    onUpdateReferenceRace: (distanceMeters: Double, durationSeconds: Long, avgHr: Int) -> Unit,
    onUseBestRun: () -> Unit,
    onUpdateHrZones: ((z1Max: Int, z2Max: Int, z3Max: Int, z4Max: Int, maxHr: Int) -> Unit)?
) {
    val coroutineScope = rememberCoroutineScope()

    // Reference Run Inputs state
    var refDistanceText by remember(userSettings.refDistanceMeters) {
        mutableStateOf(String.format(Locale.US, "%.2f", userSettings.refDistanceMeters / 1000.0))
    }
    var refDurationText by remember(userSettings.refDurationSeconds) {
        mutableStateOf(PaceUtils.formatDuration(userSettings.refDurationSeconds))
    }
    var refHrText by remember(userSettings.refAvgHeartRate) {
        mutableStateOf("${userSettings.refAvgHeartRate}")
    }

    // HR Zones Inputs state
    var maxHrText by remember(userSettings.maxHr) {
        mutableStateOf("${userSettings.maxHr}")
    }
    var z1MaxText by remember(userSettings.z1Max) {
        mutableStateOf("${userSettings.z1Max}")
    }
    var z2MaxText by remember(userSettings.z2Max) {
        mutableStateOf("${userSettings.z2Max}")
    }
    var z3MaxText by remember(userSettings.z3Max) {
        mutableStateOf("${userSettings.z3Max}")
    }
    var z4MaxText by remember(userSettings.z4Max) {
        mutableStateOf("${userSettings.z4Max}")
    }

    var hrSavedBannerVisible by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Card 1: Reference Performance Baseline
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
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.DirectionsRun,
                                contentDescription = null,
                                tint = VoltPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "REFERENCE BASELINE",
                                style = MaterialTheme.typography.labelSmall,
                                color = VoltPrimary,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                        }

                        // Use Best Run Button
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

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Your baseline run is used to calculate Daniels' VDOT and scale your predicted race finishes via Riegel's power curve.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // 3 Input Fields: Distance, Time, Avg HR
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = refDistanceText,
                            onValueChange = {
                                refDistanceText = it
                                val km = it.toDoubleOrNull()
                                val sec = PaceUtils.parseDurationToSeconds(refDurationText)
                                val hr = refHrText.toIntOrNull() ?: userSettings.refAvgHeartRate
                                if (km != null && sec != null && km > 0) {
                                    onUpdateReferenceRace(km * 1000.0, sec, hr)
                                }
                            },
                            label = { Text("Distance (km)") },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("input_ref_distance"),
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
                                val km = refDistanceText.toDoubleOrNull() ?: (userSettings.refDistanceMeters / 1000.0)
                                val sec = PaceUtils.parseDurationToSeconds(it)
                                val hr = refHrText.toIntOrNull() ?: userSettings.refAvgHeartRate
                                if (sec != null && sec > 0) {
                                    onUpdateReferenceRace(km * 1000.0, sec, hr)
                                }
                            },
                            label = { Text("Time (MM:SS)") },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("input_ref_duration"),
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
                                val km = refDistanceText.toDoubleOrNull() ?: (userSettings.refDistanceMeters / 1000.0)
                                val sec = PaceUtils.parseDurationToSeconds(refDurationText) ?: userSettings.refDurationSeconds
                                val hr = it.toIntOrNull()
                                if (hr != null && hr > 50) {
                                    onUpdateReferenceRace(km * 1000.0, sec, hr)
                                }
                            },
                            label = { Text("Avg HR") },
                            modifier = Modifier
                                .weight(0.8f)
                                .testTag("input_ref_hr"),
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
                    val vdotText = VdotCalculator
                        .vdot(userSettings.refDistanceMeters, userSettings.refDurationSeconds)
                        ?.let { String.format(Locale.US, "~%.0f", it) } ?: "--"

                    Text(
                        text = "Baseline Pace: ${PaceUtils.formatPace(refPace)} · Est. VDOT: $vdotText (Daniels formula)",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextTertiary
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Equivalent Performances Preview Grid
                    Text(
                        text = "EQUIVALENT RACE PERFORMANCES",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(
                            Triple("5K", RaceDistance.FIVE_K.meters, "5 km"),
                            Triple("10K", RaceDistance.TEN_K.meters, "10 km"),
                            Triple("15K", RaceDistance.FIFTEEN_K.meters, "15 km"),
                            Triple("21K Half", RaceDistance.HALF_MARATHON.meters, "21.1 km")
                        ).forEach { (label, distMeters, _) ->
                            val predictedSec = RiegelCalculator.predictRaceTimeSeconds(
                                userSettings.refDistanceMeters,
                                userSettings.refDurationSeconds,
                                distMeters
                            )
                            val paceSec = if (distMeters > 0) (predictedSec / (distMeters / 1000.0)).toInt() else 0

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .background(Color(0xFF11161F), RoundedCornerShape(8.dp))
                                    .padding(vertical = 8.dp, horizontal = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = VoltPrimary,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = PaceUtils.formatDuration(predictedSec),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = TextWhite,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp
                                    )
                                    Text(
                                        text = PaceUtils.formatPace(paceSec),
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

        // Card 2: Heart Rate Zones Calibration
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
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Favorite,
                                contentDescription = null,
                                tint = Zone5Red,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "HEART RATE ZONES (BPM)",
                                style = MaterialTheme.typography.labelSmall,
                                color = VoltPrimary,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                        }

                        AnimatedVisibility(
                            visible = hrSavedBannerVisible,
                            enter = fadeIn(),
                            exit = fadeOut()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = StatusSuccess,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = "Saved",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = StatusSuccess,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Customize zone cutoffs to match your cardiovascular profile. Easy runs target Zone 1-2, tempo targets Zone 3, and intervals target Zone 4-5.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Zone Color Strip Visualization
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .background(Color(0xFF1B2433), RoundedCornerShape(4.dp)),
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Box(modifier = Modifier.weight(1f).background(Zone1Blue, RoundedCornerShape(2.dp)).fillMaxSize())
                        Box(modifier = Modifier.weight(1.2f).background(Zone2Green, RoundedCornerShape(2.dp)).fillMaxSize())
                        Box(modifier = Modifier.weight(1.2f).background(Zone3Amber, RoundedCornerShape(2.dp)).fillMaxSize())
                        Box(modifier = Modifier.weight(1.2f).background(Zone4Orange, RoundedCornerShape(2.dp)).fillMaxSize())
                        Box(modifier = Modifier.weight(1f).background(Zone5Red, RoundedCornerShape(2.dp)).fillMaxSize())
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Max HR Input
                    OutlinedTextField(
                        value = maxHrText,
                        onValueChange = { maxHrText = it },
                        label = { Text("Maximum Heart Rate (Max HR)") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_max_hr"),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = VoltPrimary,
                            unfocusedBorderColor = AthleticBorder,
                            focusedLabelColor = VoltPrimary
                        )
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Zone Ceilings Inputs
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = z1MaxText,
                            onValueChange = { z1MaxText = it },
                            label = { Text("Z1 (<)") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Zone1Blue,
                                unfocusedBorderColor = AthleticBorder
                            )
                        )
                        OutlinedTextField(
                            value = z2MaxText,
                            onValueChange = { z2MaxText = it },
                            label = { Text("Z2 Max") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Zone2Green,
                                unfocusedBorderColor = AthleticBorder
                            )
                        )
                        OutlinedTextField(
                            value = z3MaxText,
                            onValueChange = { z3MaxText = it },
                            label = { Text("Z3 Max") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Zone3Amber,
                                unfocusedBorderColor = AthleticBorder
                            )
                        )
                        OutlinedTextField(
                            value = z4MaxText,
                            onValueChange = { z4MaxText = it },
                            label = { Text("Z4 Max") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Zone4Orange,
                                unfocusedBorderColor = AthleticBorder
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Save / Apply HR Zones Button
                    if (onUpdateHrZones != null) {
                        Button(
                            onClick = {
                                val max = maxHrText.toIntOrNull() ?: userSettings.maxHr
                                val z1 = z1MaxText.toIntOrNull() ?: userSettings.z1Max
                                val z2 = z2MaxText.toIntOrNull() ?: userSettings.z2Max
                                val z3 = z3MaxText.toIntOrNull() ?: userSettings.z3Max
                                val z4 = z4MaxText.toIntOrNull() ?: userSettings.z4Max
                                onUpdateHrZones(z1, z2, z3, z4, max)

                                coroutineScope.launch {
                                    hrSavedBannerVisible = true
                                    delay(2000)
                                    hrSavedBannerVisible = false
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = VoltPrimary,
                                contentColor = VoltOnPrimary
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("save_hr_zones_button")
                        ) {
                            Text("Save Heart Rate Calibration", fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Standard thresholds: Z1 (< 118 bpm), Z2 (118-140 bpm), Z3 (141-160 bpm), Z4 (161-179 bpm), Z5 (180+ bpm)",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextTertiary,
                        fontSize = 11.sp
                    )
                }
            }
        }

        // Card 3: Science & Methodology
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, AthleticBorder, RoundedCornerShape(16.dp)),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = AthleticDarkCard)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = VoltPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "SCIENCE & METHODOLOGY",
                            style = MaterialTheme.typography.labelSmall,
                            color = VoltPrimary,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "• Riegel's Power Law: Predicts race finish duration using T2 = T1 × (D2 / D1)^1.06, factoring natural fatigue decay across longer distances.\n\n" +
                                "• Jack Daniels' VDOT: Expresses aerobic capacity as a pseudo-VO2max score derived from one recent race effort, independent of race distance.\n\n" +
                                "• Polarized 80/20 Distribution: Keeps 80% of weekly volume in Zone 1-2 to stimulate mitochondrial density and capillary beds while leaving energy for hard threshold and interval sessions.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        lineHeight = 18.sp
                    )
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(28.dp))
        }
    }
}

/**
 * Modal Bottom Sheet for selecting Goal Race Distance.
 */
@Composable
private fun GoalRaceSelectorSheet(
    selectedDistance: RaceDistance,
    onSelectDistance: (RaceDistance) -> Unit,
    onClose: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Configure Goal Race",
                    style = MaterialTheme.typography.titleLarge,
                    color = TextWhite,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Select your target event to calibrate training paces",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }

            IconButton(onClick = onClose) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close",
                    tint = TextSecondary
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        RaceDistance.values().forEach { distance ->
            val isSelected = selectedDistance == distance
            val (desc, kmLabel) = when (distance) {
                RaceDistance.FIVE_K -> Pair("VO2 max & high-aerobic speed benchmark", "5.0 km")
                RaceDistance.TEN_K -> Pair("Standard endurance threshold & aerobic power", "10.0 km")
                RaceDistance.FIFTEEN_K -> Pair("Intermediate long-distance threshold benchmark", "15.0 km")
                RaceDistance.HALF_MARATHON -> Pair("Half marathon stamina & glycogen efficiency", "21.1 km")
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 5.dp)
                    .border(
                        1.dp,
                        if (isSelected) VoltPrimary else AthleticBorder,
                        RoundedCornerShape(12.dp)
                    )
                    .clickable { onSelectDistance(distance) }
                    .testTag("target_race_${distance.name}"),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isSelected) VoltPrimary.copy(alpha = 0.12f) else AthleticDarkCard
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = distance.label,
                                style = MaterialTheme.typography.titleMedium,
                                color = if (isSelected) VoltPrimary else TextWhite,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Box(
                                modifier = Modifier
                                    .background(Color(0xFF1B2330), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = kmLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextSecondary,
                                    fontSize = 10.sp
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = desc,
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                            fontSize = 11.sp
                        )
                    }

                    if (isSelected) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .background(VoltPrimary, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Selected",
                                tint = VoltOnPrimary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
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
