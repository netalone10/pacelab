package com.example.ui.components

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.health.HealthConnectStatus
import com.example.data.model.RunSplit
import com.example.data.repository.SplitSource
import com.example.ui.theme.AthleticBorder
import com.example.ui.theme.AthleticDarkCard
import com.example.ui.theme.StatusDanger
import com.example.ui.theme.StatusSuccess
import com.example.ui.theme.StatusWarning
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TextTertiary
import com.example.ui.theme.TextWhite
import com.example.ui.theme.VoltOnPrimary
import com.example.ui.theme.VoltPrimary
import com.example.util.PaceUtils
import java.util.Locale

@Composable
fun MetricCard(
    title: String,
    value: String,
    unit: String? = null,
    deltaPercent: Double? = null,
    icon: ImageVector? = null,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
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
                    text = title.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                    letterSpacing = 1.sp
                )
                icon?.let {
                    Icon(
                        imageVector = it,
                        contentDescription = null,
                        tint = VoltPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.displayMedium,
                    color = TextWhite,
                    fontWeight = FontWeight.Bold
                )
                unit?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.titleMedium,
                        color = VoltPrimary,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }
            }

            deltaPercent?.let { delta ->
                Spacer(modifier = Modifier.height(6.dp))
                val isPositive = delta >= 0
                val deltaColor = if (isPositive) StatusSuccess else StatusDanger
                val deltaIcon = if (isPositive) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .background(deltaColor.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Icon(
                        imageVector = deltaIcon,
                        contentDescription = null,
                        tint = deltaColor,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = String.format(Locale.US, "%.1f%% vs last week", kotlin.math.abs(delta)),
                        style = MaterialTheme.typography.labelSmall,
                        color = deltaColor,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
fun PaceSplitsTable(
    splits: List<RunSplit>,
    avgPaceSec: Int,
    modifier: Modifier = Modifier,
    source: SplitSource = SplitSource.NONE,
    routeConsentRequired: Boolean = false,
    onRequestRoute: (() -> Unit)? = null
) {
    Card(
        modifier = modifier.border(1.dp, AthleticBorder, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = AthleticDarkCard)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Kilometer Splits",
                style = MaterialTheme.typography.titleMedium,
                color = TextWhite
            )

            if (splits.isEmpty()) {
                val context = LocalContext.current
                Spacer(modifier = Modifier.height(10.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(StatusWarning.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
                        .border(1.dp, StatusWarning.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                        .padding(14.dp)
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = StatusWarning,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Split Data Unavailable",
                                style = MaterialTheme.typography.titleSmall,
                                color = TextWhite,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        if (routeConsentRequired && onRequestRoute != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Rute GPS lari ini tersedia. Izinkan PaceLab membaca rute untuk membuat split per kilometer.",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextWhite,
                                lineHeight = 18.sp
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = onRequestRoute,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = VoltPrimary,
                                    contentColor = AthleticDarkCard
                                ),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("allow_route_access_button")
                            ) {
                                Text(text = "Izinkan akses rute", fontWeight = FontWeight.Bold)
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "To generate per-kilometer splits, your fitness tracker (e.g. Samsung Health, Google Fit, Garmin) must write interval Distance records into Health Connect.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                            lineHeight = 18.sp
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Text(
                            text = "Samsung Health Sync Checklist:",
                            style = MaterialTheme.typography.labelSmall,
                            color = VoltPrimary,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            SyncCheckItem("1. Open Samsung Health ➔ Settings ➔ Health Connect (Connected Services).")
                            SyncCheckItem("2. Ensure Distance, Exercise, and Heart Rate permissions are turned ON.")
                            SyncCheckItem("3. Ensure workouts are tracked outdoors with GPS active so intermediate distance points are logged.")
                            SyncCheckItem("4. Verify PaceLab has read access to Distance and Exercise in Health Connect.")
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Button(
                            onClick = {
                                try {
                                    val hcIntent = Intent("androidx.health.ACTION_HEALTH_CONNECT_SETTINGS")
                                    context.startActivity(hcIntent)
                                } catch (_: Exception) {
                                    try {
                                        val settingsIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                            data = Uri.fromParts("package", context.packageName, null)
                                        }
                                        context.startActivity(settingsIntent)
                                    } catch (_: Exception) {}
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = VoltPrimary,
                                contentColor = AthleticDarkCard
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("open_health_connect_settings_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Open Health Connect Settings",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
                return@Column
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Header Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("KM", style = MaterialTheme.typography.labelSmall, color = TextTertiary, modifier = Modifier.width(36.dp))
                Text("SPLIT PACE", style = MaterialTheme.typography.labelSmall, color = TextTertiary)
                Text("AVG HR", style = MaterialTheme.typography.labelSmall, color = TextTertiary)
            }

            splits.forEach { split ->
                val splitPace = split.paceSeconds
                val isPartial = split.distanceMeters < 999.5
                val diffSec = splitPace - avgPaceSec
                val diffColor = when {
                    diffSec < -5 -> StatusSuccess // Faster than avg
                    diffSec > 5 -> StatusDanger   // Slower than avg
                    else -> TextSecondary
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${split.kilometer}",
                            style = MaterialTheme.typography.labelSmall,
                            color = VoltPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = PaceUtils.formatPace(splitPace),
                            style = MaterialTheme.typography.bodyLarge,
                            color = TextWhite,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (isPartial) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = String.format(Locale.US, "%.2f km", split.distanceMeters / 1000.0),
                                style = MaterialTheme.typography.labelSmall,
                                color = TextTertiary
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (diffSec == 0) "=" else String.format(Locale.US, "%+ds", diffSec),
                            style = MaterialTheme.typography.labelSmall,
                            color = diffColor
                        )
                    }

                    Text(
                        text = split.avgHeartRate?.let { "$it bpm" } ?: "-- bpm",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                }
            }

            if (source != SplitSource.NONE) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Sumber: ${source.label}",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextTertiary
                )
            }
        }
    }
}

@Composable
fun HealthStatusBanner(
    status: HealthConnectStatus,
    hasPermissions: Boolean,
    isUsingDemo: Boolean,
    onRequestPermissions: () -> Unit,
    onOpenPlayStore: () -> Unit,
    onLoadDemoData: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (hasPermissions && !isUsingDemo) return

    Card(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, if (status == HealthConnectStatus.Available) VoltPrimary.copy(alpha = 0.5f) else StatusWarning.copy(alpha = 0.5f), RoundedCornerShape(14.dp)),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = AthleticDarkCard)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (status == HealthConnectStatus.Available) Icons.Default.CheckCircle else Icons.Default.Warning,
                    contentDescription = null,
                    tint = if (status == HealthConnectStatus.Available) VoltPrimary else StatusWarning,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = when {
                        status != HealthConnectStatus.Available -> "Health Connect Setup"
                        !hasPermissions -> "Connect Running Data"
                        else -> "PaceLab Demo Mode"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = TextWhite,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = when {
                    status == HealthConnectStatus.NotInstalled ->
                        "Health Connect is required to sync running data from Samsung Health or other trackers."
                    status == HealthConnectStatus.UpdateRequired ->
                        "Health Connect needs to be updated from the Play Store."
                    !hasPermissions ->
                        "Allow PaceLab to read running sessions, heart rate, and distance."
                    else ->
                        "Viewing realistic 4-week running data with your 5.31 km reference race."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (status == HealthConnectStatus.NotInstalled || status == HealthConnectStatus.UpdateRequired) {
                    Button(
                        onClick = onOpenPlayStore,
                        colors = ButtonDefaults.buttonColors(containerColor = VoltPrimary, contentColor = VoltOnPrimary),
                        modifier = Modifier.testTag("play_store_button")
                    ) {
                        Text("Get Health Connect")
                    }
                } else if (!hasPermissions) {
                    Button(
                        onClick = onRequestPermissions,
                        colors = ButtonDefaults.buttonColors(containerColor = VoltPrimary, contentColor = VoltOnPrimary),
                        modifier = Modifier.testTag("connect_permissions_button")
                    ) {
                        Text("Grant Permissions")
                    }
                }

                OutlinedButton(
                    onClick = onLoadDemoData,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = VoltPrimary),
                    modifier = Modifier.testTag("demo_data_button")
                ) {
                    Text(if (isUsingDemo) "Reload Demo" else "Load Demo Data")
                }
            }
        }
    }
}

@Composable
private fun SyncCheckItem(text: String) {
    Row(verticalAlignment = Alignment.Top) {
        Text(
            text = "• ",
            color = StatusWarning,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = text,
            color = TextSecondary,
            fontSize = 12.sp,
            lineHeight = 16.sp
        )
    }
}

