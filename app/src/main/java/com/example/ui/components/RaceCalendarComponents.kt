package com.example.ui.components

import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.RaceDistance
import com.example.data.model.RaceEvent
import com.example.ui.theme.AthleticBorder
import com.example.ui.theme.AthleticDarkCard
import com.example.ui.theme.AthleticDarkSurface
import com.example.ui.theme.StatusDanger
import com.example.ui.theme.StatusSuccess
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TextTertiary
import com.example.ui.theme.TextWhite
import com.example.ui.theme.VoltOnPrimary
import com.example.ui.theme.VoltPrimary
import com.example.util.PaceUtils
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

@Composable
fun RaceCountdownCard(
    raceEvent: RaceEvent?,
    onOpenSetupDialog: () -> Unit,
    onDeleteRace: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    Card(
        modifier = modifier
            .fillMaxWidth()
            .border(
                1.5.dp,
                if (raceEvent != null) VoltPrimary else AthleticBorder,
                RoundedCornerShape(18.dp)
            ),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = AthleticDarkCard)
    ) {
        if (raceEvent == null) {
            // Empty State: Prompt to add target race
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.EmojiEvents,
                    contentDescription = null,
                    tint = VoltPrimary,
                    modifier = Modifier.size(36.dp)
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "No Target Race Scheduled",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextWhite,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Add your goal race date to generate an adaptive periodized plan with peak volume and a 1-week sharp taper.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Spacer(modifier = Modifier.height(14.dp))
                Button(
                    onClick = onOpenSetupDialog,
                    colors = ButtonDefaults.buttonColors(containerColor = VoltPrimary, contentColor = VoltOnPrimary),
                    modifier = Modifier.testTag("add_target_race_button")
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Set Target Race", fontWeight = FontWeight.Bold)
                }
            }
        } else {
            // Active Target Race Hero with Countdown
            val today = LocalDate.now()
            val daysUntil = ChronoUnit.DAYS.between(today, raceEvent.raceDate)
            val weeksUntil = ((daysUntil + 6) / 7).coerceAtLeast(0)
            val dateFormatter = DateTimeFormatter.ofPattern("EEEE, MMM d, yyyy", Locale.US)

            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .background(VoltPrimary.copy(alpha = 0.15f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.EmojiEvents,
                                contentDescription = null,
                                tint = VoltPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = raceEvent.raceName,
                                style = MaterialTheme.typography.titleMedium,
                                color = TextWhite,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = raceEvent.raceDate.format(dateFormatter),
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary
                            )
                        }
                    }

                    Row {
                        IconButton(
                            onClick = onOpenSetupDialog,
                            modifier = Modifier.size(32.dp).testTag("edit_race_button")
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit Race", tint = TextSecondary, modifier = Modifier.size(18.dp))
                        }
                        IconButton(
                            onClick = onDeleteRace,
                            modifier = Modifier.size(32.dp).testTag("delete_race_button")
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete Race", tint = StatusDanger, modifier = Modifier.size(18.dp))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Hero Countdown
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Column {
                        val countdownText = when {
                            daysUntil < 0 -> "Completed"
                            daysUntil == 0L -> "RACE DAY!"
                            daysUntil == 1L -> "1 DAY TO GO"
                            else -> "$daysUntil DAYS"
                        }
                        Text(
                            text = countdownText,
                            style = MaterialTheme.typography.displayMedium,
                            color = VoltPrimary,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (daysUntil > 0) "$weeksUntil weeks remaining · ${raceEvent.distance.label}" else raceEvent.distance.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary
                        )
                    }

                    raceEvent.targetDurationSeconds?.let { durSec ->
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = PaceUtils.formatDuration(durSec),
                                style = MaterialTheme.typography.titleLarge,
                                color = TextWhite,
                                fontWeight = FontWeight.Bold
                            )
                            val paceSec = raceEvent.targetPaceSeconds ?: PaceUtils.calculatePaceSeconds(raceEvent.distance.meters, durSec)
                            Text(
                                text = "Goal: ${PaceUtils.formatPace(paceSec)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Action Bar: Export to Android Calendar
                OutlinedButton(
                    onClick = { exportRaceToDeviceCalendar(context, raceEvent) },
                    modifier = Modifier.fillMaxWidth().testTag("export_calendar_button"),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = VoltPrimary),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.CalendarMonth, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Export Race to Device Calendar", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

private fun exportRaceToDeviceCalendar(context: Context, raceEvent: RaceEvent) {
    try {
        val startMillis = raceEvent.raceDate.atTime(6, 0)
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()

        val endMillis = startMillis + (raceEvent.targetDurationSeconds?.times(1000L) ?: (2 * 3600 * 1000L))

        val intent = Intent(Intent.ACTION_INSERT)
            .setData(CalendarContract.Events.CONTENT_URI)
            .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, startMillis)
            .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, endMillis)
            .putExtra(CalendarContract.Events.TITLE, raceEvent.raceName)
            .putExtra(
                CalendarContract.Events.DESCRIPTION,
                "Goal: ${raceEvent.distance.label} race. Target time: ${raceEvent.targetDurationSeconds?.let { PaceUtils.formatDuration(it) } ?: "PaceLab target"}"
            )
            .putExtra(CalendarContract.Events.EVENT_LOCATION, raceEvent.location ?: "Race Venue")

        context.startActivity(intent)
    } catch (_: Exception) {
        // Fallback intent if calendar contract data is not accepted
        val fallbackIntent = Intent(Intent.ACTION_EDIT).apply {
            type = "vnd.android.cursor.item/event"
            putExtra(CalendarContract.Events.TITLE, raceEvent.raceName)
        }
        context.startActivity(fallbackIntent)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RaceSetupDialog(
    initialRace: RaceEvent?,
    defaultPredictedDuration: Long?,
    onDismiss: () -> Unit,
    onSave: (name: String, date: LocalDate, distance: RaceDistance, targetTimeSec: Long?, location: String?) -> Unit
) {
    var name by remember { mutableStateOf(initialRace?.raceName ?: "My Target 10K") }
    var selectedDistance by remember { mutableStateOf(initialRace?.distance ?: RaceDistance.TEN_K) }
    var selectedDate by remember {
        mutableStateOf(initialRace?.raceDate ?: LocalDate.now().plusWeeks(8))
    }
    var showDatePicker by remember { mutableStateOf(false) }
    var targetTimeText by remember {
        val seconds = initialRace?.targetDurationSeconds ?: defaultPredictedDuration ?: 2700L
        mutableStateOf(PaceUtils.formatDuration(seconds))
    }
    var location by remember { mutableStateOf(initialRace?.location ?: "") }

    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, AthleticBorder, RoundedCornerShape(20.dp)),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = AthleticDarkSurface)
        ) {
            Column(modifier = Modifier.padding(22.dp)) {
                Text(
                    text = if (initialRace != null) "Edit Target Race" else "Schedule Target Race",
                    style = MaterialTheme.typography.titleLarge,
                    color = TextWhite,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(14.dp))

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Race Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("race_name_input"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = VoltPrimary,
                        unfocusedBorderColor = AthleticBorder,
                        focusedLabelColor = VoltPrimary
                    )
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Distance Selector Chips
                Text("RACE DISTANCE", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    RaceDistance.values().forEach { dist ->
                        val isSel = selectedDistance == dist
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .border(1.dp, if (isSel) VoltPrimary else AthleticBorder, RoundedCornerShape(8.dp))
                                .background(if (isSel) VoltPrimary.copy(alpha = 0.15f) else Color.Transparent, RoundedCornerShape(8.dp))
                                .clickable { selectedDistance = dist }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = dist.label,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isSel) VoltPrimary else TextWhite,
                                fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Date Picker trigger button
                Text("RACE DATE", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, AthleticBorder, RoundedCornerShape(10.dp))
                        .clickable { showDatePicker = true }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = selectedDate.format(DateTimeFormatter.ofPattern("EEEE, MMM d, yyyy", Locale.US)),
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextWhite,
                        fontWeight = FontWeight.SemiBold
                    )
                    Icon(Icons.Default.Event, contentDescription = null, tint = VoltPrimary)
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Target finish time input
                OutlinedTextField(
                    value = targetTimeText,
                    onValueChange = { targetTimeText = it },
                    label = { Text("Goal Time (HH:MM:SS or MM:SS)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("race_target_time_input"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = VoltPrimary,
                        unfocusedBorderColor = AthleticBorder,
                        focusedLabelColor = VoltPrimary
                    )
                )

                Spacer(modifier = Modifier.height(18.dp))

                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = TextSecondary)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val durationSec = PaceUtils.parseDurationToSeconds(targetTimeText)
                            onSave(name, selectedDate, selectedDistance, durationSec, location.ifBlank { null })
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = VoltPrimary, contentColor = VoltOnPrimary),
                        modifier = Modifier.testTag("save_race_button")
                    ) {
                        Text("Save Race", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = selectedDate.atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        selectedDate = Instant.ofEpochMilli(millis).atZone(ZoneId.of("UTC")).toLocalDate()
                    }
                    showDatePicker = false
                }) {
                    Text("OK", color = VoltPrimary)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}
