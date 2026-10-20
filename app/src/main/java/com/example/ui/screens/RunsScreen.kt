package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.health.connect.client.contracts.ExerciseRouteRequestContract
import androidx.health.connect.client.records.ExerciseRoute
import com.example.data.repository.SplitSource
import com.example.data.model.HeartRatePoint
import com.example.data.model.RunSession
import com.example.data.model.RunSplit
import com.example.ui.components.HeartRateChart
import com.example.ui.components.PaceSplitsTable
import com.example.ui.theme.AthleticBorder
import com.example.ui.theme.AthleticDarkCard
import com.example.ui.theme.AthleticDarkSurface
import com.example.ui.theme.StatusDanger
import com.example.ui.theme.StatusWarning
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TextTertiary
import com.example.ui.theme.TextWhite
import com.example.ui.theme.VoltOnPrimary
import com.example.ui.theme.VoltPrimary
import com.example.util.PaceUtils
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun RunsScreen(
    sessions: List<RunSession>,
    duplicateCount: Int,
    selectedSession: RunSession?,
    selectedSplits: List<RunSplit>,
    selectedHrPoints: List<HeartRatePoint>,
    isLoadingDetail: Boolean,
    onSelectSession: (RunSession?) -> Unit,
    onDeleteSession: (String) -> Unit,
    onResolveDuplicates: () -> Unit,
    modifier: Modifier = Modifier,
    splitSource: SplitSource = SplitSource.NONE,
    routeConsentRequired: Boolean = false,
    routeElevationGain: Double? = null,
    onRouteGranted: (ExerciseRoute?) -> Unit = {}
) {
    var sessionToDelete by remember { mutableStateOf<RunSession?>(null) }

    if (sessions.isEmpty()) {
        Box(
            modifier = modifier.fillMaxSize().padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Default.DirectionsRun,
                    contentDescription = null,
                    tint = TextTertiary,
                    modifier = Modifier.size(64.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "No Recorded Runs Yet",
                    style = MaterialTheme.typography.titleLarge,
                    color = TextWhite
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Sync with Health Connect or load realistic demo data to view past workouts and split statistics.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
        return
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Header
        item {
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Running Activities",
                    style = MaterialTheme.typography.titleLarge,
                    color = TextWhite,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${sessions.size} sessions",
                    style = MaterialTheme.typography.labelMedium,
                    color = VoltPrimary
                )
            }
        }

        // Duplicate Workouts Notification Banner
        if (duplicateCount > 0) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, StatusWarning, RoundedCornerShape(14.dp)),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = AthleticDarkCard)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = StatusWarning,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "$duplicateCount Duplicate Workout${if (duplicateCount > 1) "s" else ""}",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = TextWhite,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Overlapping sessions from multiple trackers.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary
                                )
                            }
                        }

                        Button(
                            onClick = onResolveDuplicates,
                            colors = ButtonDefaults.buttonColors(containerColor = StatusWarning, contentColor = AthleticDarkSurface),
                            modifier = Modifier.testTag("resolve_duplicates_button")
                        ) {
                            Text("Clean Up", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        // Sessions List
        items(sessions, key = { it.id }) { session ->
            RunSessionCard(
                session = session,
                onClick = { onSelectSession(session) },
                onDelete = { sessionToDelete = session }
            )
        }

        item {
            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    // Detail Bottom Sheet
    if (selectedSession != null) {
        RunDetailBottomSheet(
            session = selectedSession,
            splits = selectedSplits,
            hrPoints = selectedHrPoints,
            isLoading = isLoadingDetail,
            splitSource = splitSource,
            routeConsentRequired = routeConsentRequired,
            routeElevationGain = routeElevationGain,
            onRouteGranted = onRouteGranted,
            onDismiss = { onSelectSession(null) },
            onDelete = {
                onDeleteSession(selectedSession.id)
                onSelectSession(null)
            }
        )
    }

    // Confirmation Alert Dialog for Deletion
    sessionToDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { sessionToDelete = null },
            title = {
                Text(text = "Delete Workout?", color = TextWhite, fontWeight = FontWeight.Bold)
            },
            text = {
                Text(
                    text = "This will remove the session (${PaceUtils.formatDistanceKm(target.distanceMeters)}) from your local PaceLab records.",
                    color = TextSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteSession(target.id)
                        sessionToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = StatusDanger)
                ) {
                    Text("Delete", color = TextWhite, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { sessionToDelete = null }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            containerColor = AthleticDarkSurface
        )
    }
}

@Composable
fun RunSessionCard(
    session: RunSession,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val zone = ZoneId.systemDefault()
    val localDateTime = session.startTime.atZone(zone)
    val dateFormatter = DateTimeFormatter.ofPattern("EEE, MMM d, yyyy · HH:mm", Locale.US)
    val dateString = localDateTime.format(dateFormatter)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                1.dp,
                if (session.isDuplicate) StatusWarning.copy(alpha = 0.6f) else AthleticBorder,
                RoundedCornerShape(16.dp)
            )
            .clickable(onClick = onClick)
            .testTag("run_session_${session.id}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = AthleticDarkCard)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header: Date & Badges
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = dateString,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (session.isDuplicate) {
                        Box(
                            modifier = Modifier
                                .background(StatusWarning.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "Duplicate",
                                style = MaterialTheme.typography.labelSmall,
                                color = StatusWarning,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                    }

                    if (session.isSeed) {
                        Box(
                            modifier = Modifier
                                .background(VoltPrimary.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "Reference Data",
                                style = MaterialTheme.typography.labelSmall,
                                color = VoltPrimary,
                                fontSize = 10.sp
                            )
                        }
                    }

                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(24.dp).testTag("delete_item_${session.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete Workout",
                            tint = TextTertiary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Main stats: Distance + Pace + Time + HR
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = PaceUtils.formatDistanceKm(session.distanceMeters),
                        style = MaterialTheme.typography.displayMedium,
                        color = TextWhite,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Distance",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextTertiary
                    )
                }

                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = TextSecondary,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Metrics row: Duration, Avg Pace, Avg HR
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Timer, contentDescription = null, tint = VoltPrimary, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = PaceUtils.formatDuration(session.durationSeconds),
                        style = MaterialTheme.typography.labelLarge,
                        color = TextWhite
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Speed, contentDescription = null, tint = VoltPrimary, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = PaceUtils.formatPace(session.avgPaceSeconds),
                        style = MaterialTheme.typography.labelLarge,
                        color = TextWhite
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Favorite, contentDescription = null, tint = VoltPrimary, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = session.avgHeartRate?.let { "$it bpm" } ?: "-- bpm",
                        style = MaterialTheme.typography.labelLarge,
                        color = TextWhite
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RunDetailBottomSheet(
    session: RunSession,
    splits: List<RunSplit>,
    hrPoints: List<HeartRatePoint>,
    isLoading: Boolean,
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
    splitSource: SplitSource = SplitSource.NONE,
    routeConsentRequired: Boolean = false,
    routeElevationGain: Double? = null,
    onRouteGranted: (ExerciseRoute?) -> Unit = {}
) {
    // Health Connect asks for route access per run, so this launcher takes the session id.
    val routeLauncher = rememberLauncherForActivityResult(ExerciseRouteRequestContract()) { route ->
        onRouteGranted(route)
    }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val zone = ZoneId.systemDefault()
    val localDateTime = session.startTime.atZone(zone)
    val dateFormatter = DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy", Locale.US)
    var showDeleteConfirm by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = AthleticDarkSurface
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Workout Summary",
                            style = MaterialTheme.typography.titleLarge,
                            color = TextWhite,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = localDateTime.format(dateFormatter),
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                    }

                    Row {
                        IconButton(onClick = { showDeleteConfirm = true }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = StatusDanger)
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = TextSecondary)
                        }
                    }
                }
            }

            // Stat Strip: Distance, Duration, Pace, HR, Elevation, Calories
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
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            DetailStatItem("Distance", PaceUtils.formatDistanceKm(session.distanceMeters), VoltPrimary)
                            DetailStatItem("Duration", PaceUtils.formatDuration(session.durationSeconds), TextWhite)
                            DetailStatItem("Avg Pace", PaceUtils.formatPace(session.avgPaceSeconds), VoltPrimary)
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            DetailStatItem("Avg HR", session.avgHeartRate?.let { "$it bpm" } ?: "--", TextWhite)
                            DetailStatItem("Elevation", (session.elevationGainedMeters ?: routeElevationGain)?.let { String.format(Locale.US, "%.0f m", it) } ?: "--", TextWhite)
                            DetailStatItem("Calories", session.totalCalories?.let { String.format(Locale.US, "%.0f kcal", it) } ?: "--", TextWhite)
                        }
                    }
                }
            }

            if (isLoading) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().height(140.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = VoltPrimary)
                    }
                }
            } else {
                // Per-KM Splits Table
                item {
                    PaceSplitsTable(
                        splits = splits,
                        avgPaceSec = session.avgPaceSeconds,
                        modifier = Modifier.fillMaxWidth(),
                        source = splitSource,
                        routeConsentRequired = routeConsentRequired,
                        onRequestRoute = { routeLauncher.launch(session.id) }
                    )
                }

                // Heart Rate Interactive Canvas Chart with Scrubber
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, AthleticBorder, RoundedCornerShape(16.dp)),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = AthleticDarkCard)
                    ) {
                        Box(modifier = Modifier.padding(16.dp)) {
                            HeartRateChart(
                                points = hrPoints,
                                avgHr = session.avgHeartRate,
                                sessionDurationSeconds = session.durationSeconds,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(36.dp))
            }
        }

        if (showDeleteConfirm) {
            AlertDialog(
                onDismissRequest = { showDeleteConfirm = false },
                title = { Text("Delete This Workout?", color = TextWhite) },
                text = { Text("Are you sure you want to delete this session? It will be removed from all weekly and target calculations.", color = TextSecondary) },
                confirmButton = {
                    Button(
                        onClick = {
                            showDeleteConfirm = false
                            onDelete()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = StatusDanger)
                    ) {
                        Text("Delete", color = TextWhite)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteConfirm = false }) {
                        Text("Cancel", color = TextSecondary)
                    }
                },
                containerColor = AthleticDarkSurface
            )
        }
    }
}

@Composable
private fun DetailStatItem(label: String, value: String, valueColor: androidx.compose.ui.graphics.Color) {
    Column {
        Text(text = label.uppercase(), style = MaterialTheme.typography.labelSmall, color = TextTertiary, letterSpacing = 0.5.sp)
        Spacer(modifier = Modifier.height(2.dp))
        Text(text = value, style = MaterialTheme.typography.titleMedium, color = valueColor, fontWeight = FontWeight.Bold)
    }
}
