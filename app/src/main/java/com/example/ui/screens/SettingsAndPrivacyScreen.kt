package com.example.ui.screens

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
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
import com.example.data.model.UserSettings
import com.example.ui.theme.AthleticBorder
import com.example.ui.theme.AthleticDarkCard
import com.example.ui.theme.StatusSuccess
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TextTertiary
import com.example.ui.theme.TextWhite
import com.example.ui.theme.VoltOnPrimary
import com.example.ui.theme.VoltPrimary
import com.example.BuildConfig
import com.example.util.HrZoneCalculator

@Composable
fun SettingsAndPrivacyScreen(
    userSettings: UserSettings,
    hasHealthPermissions: Boolean,
    hasAllHealthPermissions: Boolean,
    syncError: String?,
    autoSyncEnabled: Boolean,
    hasBackgroundPermission: Boolean,
    lastSyncEpochMs: Long,
    onToggleAutoSync: (Boolean) -> Unit,
    onRequestPermissions: () -> Unit,
    onOpenPlayStore: () -> Unit,
    onLoadDemoData: () -> Unit,
    onUpdateHrZones: (z1Max: Int, z2Max: Int, z3Max: Int, z4Max: Int, maxHr: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var maxHrText by remember(userSettings.maxHr) { mutableStateOf("${userSettings.maxHr}") }
    var z1MaxText by remember(userSettings.z1Max) { mutableStateOf("${userSettings.z1Max}") }
    var z2MaxText by remember(userSettings.z2Max) { mutableStateOf("${userSettings.z2Max}") }
    var z3MaxText by remember(userSettings.z3Max) { mutableStateOf("${userSettings.z3Max}") }
    var z4MaxText by remember(userSettings.z4Max) { mutableStateOf("${userSettings.z4Max}") }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Settings & Heart Rate Zones",
                style = MaterialTheme.typography.titleLarge,
                color = TextWhite,
                fontWeight = FontWeight.Bold
            )
        }

        // Heart Rate Zone Customization Card
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
                            Icon(Icons.Default.Favorite, contentDescription = null, tint = VoltPrimary, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "HEART RATE THRESHOLDS (BPM)",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary,
                                letterSpacing = 1.sp
                            )
                        }

                        Button(
                            onClick = {
                                val max = maxHrText.toIntOrNull() ?: 190
                                val calculated = HrZoneCalculator.fromMaxHr(max)
                                z1MaxText = "${calculated.z1Max}"
                                z2MaxText = "${calculated.z2Max}"
                                z3MaxText = "${calculated.z3Max}"
                                z4MaxText = "${calculated.z4Max}"
                                onUpdateHrZones(calculated.z1Max, calculated.z2Max, calculated.z3Max, calculated.z4Max, max)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = VoltPrimary, contentColor = VoltOnPrimary),
                            modifier = Modifier.testTag("auto_calc_zones_button")
                        ) {
                            Text("Auto from Max HR", fontSize = 12.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = maxHrText,
                        onValueChange = {
                            maxHrText = it
                            val max = it.toIntOrNull() ?: userSettings.maxHr
                            val z1 = z1MaxText.toIntOrNull() ?: userSettings.z1Max
                            val z2 = z2MaxText.toIntOrNull() ?: userSettings.z2Max
                            val z3 = z3MaxText.toIntOrNull() ?: userSettings.z3Max
                            val z4 = z4MaxText.toIntOrNull() ?: userSettings.z4Max
                            onUpdateHrZones(z1, z2, z3, z4, max)
                        },
                        label = { Text("Maximum Heart Rate (Max HR)") },
                        modifier = Modifier.fillMaxWidth().testTag("input_max_hr"),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = VoltPrimary,
                            unfocusedBorderColor = AthleticBorder,
                            focusedLabelColor = VoltPrimary
                        )
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = z1MaxText,
                            onValueChange = {
                                z1MaxText = it
                                it.toIntOrNull()?.let { z1 ->
                                    val max = maxHrText.toIntOrNull() ?: 190
                                    val z2 = z2MaxText.toIntOrNull() ?: 140
                                    val z3 = z3MaxText.toIntOrNull() ?: 160
                                    val z4 = z4MaxText.toIntOrNull() ?: 179
                                    onUpdateHrZones(z1, z2, z3, z4, max)
                                }
                            },
                            label = { Text("Z1 (<)") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = VoltPrimary, unfocusedBorderColor = AthleticBorder)
                        )
                        OutlinedTextField(
                            value = z2MaxText,
                            onValueChange = {
                                z2MaxText = it
                                it.toIntOrNull()?.let { z2 ->
                                    val max = maxHrText.toIntOrNull() ?: 190
                                    val z1 = z1MaxText.toIntOrNull() ?: 117
                                    val z3 = z3MaxText.toIntOrNull() ?: 160
                                    val z4 = z4MaxText.toIntOrNull() ?: 179
                                    onUpdateHrZones(z1, z2, z3, z4, max)
                                }
                            },
                            label = { Text("Z2 Max") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = VoltPrimary, unfocusedBorderColor = AthleticBorder)
                        )
                        OutlinedTextField(
                            value = z3MaxText,
                            onValueChange = {
                                z3MaxText = it
                                it.toIntOrNull()?.let { z3 ->
                                    val max = maxHrText.toIntOrNull() ?: 190
                                    val z1 = z1MaxText.toIntOrNull() ?: 117
                                    val z2 = z2MaxText.toIntOrNull() ?: 140
                                    val z4 = z4MaxText.toIntOrNull() ?: 179
                                    onUpdateHrZones(z1, z2, z3, z4, max)
                                }
                            },
                            label = { Text("Z3 Max") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = VoltPrimary, unfocusedBorderColor = AthleticBorder)
                        )
                        OutlinedTextField(
                            value = z4MaxText,
                            onValueChange = {
                                z4MaxText = it
                                it.toIntOrNull()?.let { z4 ->
                                    val max = maxHrText.toIntOrNull() ?: 190
                                    val z1 = z1MaxText.toIntOrNull() ?: 117
                                    val z2 = z2MaxText.toIntOrNull() ?: 140
                                    val z3 = z3MaxText.toIntOrNull() ?: 160
                                    onUpdateHrZones(z1, z2, z3, z4, max)
                                }
                            },
                            label = { Text("Z4 Max") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = VoltPrimary, unfocusedBorderColor = AthleticBorder)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Defaults: Z1 (< 118), Z2 (118-140), Z3 (141-160), Z4 (161-179), Z5 (180+)",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextTertiary
                    )
                }
            }
        }

        // Health Connect Integration Actions
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
                        Icon(Icons.Default.Security, contentDescription = null, tint = VoltPrimary, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "HEALTH CONNECT STATUS",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary,
                            letterSpacing = 1.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = when {
                            hasHealthPermissions && hasAllHealthPermissions ->
                                "Status: Connected. All read permissions granted (heart rate, calories, elevation, history)."
                            hasHealthPermissions ->
                                "Status: Connected with limited access. Runs sync, but some data (heart rate, calories, elevation or history older than 30 days) is not permitted. Tap Manage Permissions to allow it."
                            else ->
                                "Status: Permissions not yet granted or Health Connect not configured."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (hasHealthPermissions) StatusSuccess else TextWhite
                    )

                    if (syncError != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Sync error: $syncError",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = onRequestPermissions,
                            colors = ButtonDefaults.buttonColors(containerColor = VoltPrimary, contentColor = VoltOnPrimary),
                            modifier = Modifier.weight(1f).testTag("settings_grant_perms_button")
                        ) {
                            Text("Manage Permissions")
                        }

                        if (BuildConfig.DEBUG) {
                            OutlinedButton(
                                onClick = onLoadDemoData,
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = VoltPrimary),
                                modifier = Modifier.weight(1f).testTag("settings_reload_demo_button")
                            ) {
                                Text("Seed Demo Data")
                            }
                        }
                    }
                }
            }
        }

        // Automatic background sync
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
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "AUTO SYNC",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary,
                                letterSpacing = 1.sp
                            )
                            Text(
                                text = "Tarik lari baru tiap 6 jam",
                                style = MaterialTheme.typography.titleMedium,
                                color = TextWhite,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Switch(
                            checked = autoSyncEnabled,
                            onCheckedChange = onToggleAutoSync,
                            colors = SwitchDefaults.colors(checkedTrackColor = VoltPrimary),
                            modifier = Modifier.testTag("settings_auto_sync_switch")
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    val lastText = if (lastSyncEpochMs > 0L) {
                        "Sync terakhir: " + java.text.SimpleDateFormat("d MMM yyyy HH:mm", java.util.Locale.getDefault())
                            .format(java.util.Date(lastSyncEpochMs))
                    } else {
                        "Belum pernah sync."
                    }
                    Text(
                        text = lastText,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )

                    if (autoSyncEnabled && !hasBackgroundPermission) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Izin baca di background belum diberikan. Tanpa itu, sync otomatis cuma jalan kalau Android mengizinkan. Tap Manage Permissions dan izinkan. Di versi Health Connect lama, izin ini mungkin tidak tersedia.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                }
            }
        }

        // Privacy Policy Screen / Rationale Section
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
                        Icon(Icons.Default.Lock, contentDescription = null, tint = VoltPrimary, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "PRIVACY POLICY & HEALTH DATA NOTICE",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary,
                            letterSpacing = 1.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "100% On-Device Data Storage",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextWhite,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "• PaceLab accesses Health Connect strictly in READ-ONLY mode for exercise sessions (running), distance, speed, heart rate, elevation, and calories.\n" +
                                "• Zero External Transmissions: All pace predictions, split calculations, and weekly workload charts run locally on your device. PaceLab has NO backend server, NO advertising SDKs, and NO cloud telemetry.\n" +
                                "• Historical Access (READ_HEALTH_DATA_HISTORY): Optional. If you allow it, PaceLab can read runs older than 30 days for long-term trends. Without it, only the recent window Health Connect exposes is available.\n" +
                                "• Runs are cached in a local database on this device. Android backup is disabled for this app, so this data is not copied to cloud backup.\n" +
                                "• You can revoke permission at any time in Android Settings > Health Connect.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        lineHeight = 20.sp
                    )
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
