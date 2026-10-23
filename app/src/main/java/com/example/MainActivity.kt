package com.example

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Assessment
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.DirectionsRun
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.RaceDistance
import com.example.data.model.RunSession
import com.example.ui.PaceLabUiState
import com.example.ui.PaceLabViewModel
import com.example.ui.components.RaceSetupDialog
import com.example.ui.screens.PlanScreen
import com.example.ui.screens.RunsScreen
import com.example.ui.screens.SettingsAndPrivacyScreen
import com.example.ui.screens.TargetsScreen
import com.example.ui.screens.WeeklyScreen
import com.example.ui.theme.AthleticDarkBg
import com.example.ui.theme.AthleticDarkSurface
import com.example.ui.theme.PaceLabTheme
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TextWhite
import com.example.ui.theme.VoltOnPrimary
import com.example.ui.theme.VoltPrimary
import com.example.util.EasyRunCheck
import com.example.util.toHrZones
import java.time.DayOfWeek

class MainActivity : ComponentActivity() {

    private val viewModel: PaceLabViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Handle Health Connect rationale intent
        val action = intent?.action
        if (action == "androidx.health.ACTION_SHOW_PERMISSIONS_RATIONALE" ||
            action == Intent.ACTION_VIEW_PERMISSION_USAGE
        ) {
            viewModel.selectTab(4) // Show settings & privacy policy
        }

        setContent {
            PaceLabTheme(darkTheme = true) {
                val uiState by viewModel.uiState.collectAsStateWithLifecycle()

                // Health Connect permission request launcher
                val permissionsLauncher = rememberLauncherForActivityResult(
                    contract = PermissionController.createRequestPermissionResultContract()
                ) { _ ->
                    viewModel.refreshHealthStatus()
                    viewModel.syncFromHealthConnect()
                }

                val onOpenPlayStore: () -> Unit = {
                    try {
                        val playIntent = Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse("market://details?id=com.google.android.apps.healthdata&url=healthconnect%3A%2F%2Fonboarding")
                        )
                        startActivity(playIntent)
                    } catch (e: Exception) {
                        val webIntent = Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse("https://play.google.com/store/apps/details?id=com.google.android.apps.healthdata")
                        )
                        startActivity(webIntent)
                    }
                }

                PaceLabApp(
                    uiState = uiState,
                    onSelectTab = { viewModel.selectTab(it) },
                    onPreviousWeek = { viewModel.selectWeekOffset(-1) },
                    onNextWeek = { viewModel.selectWeekOffset(1) },
                    onSync = { viewModel.syncFromHealthConnect() },
                    onRequestPermissions = {
                        permissionsLauncher.launch(viewModel.requiredPermissions)
                    },
                    onOpenPlayStore = onOpenPlayStore,
                    onLoadDemoData = { viewModel.loadDemoData() },
                    onSelectSession = { viewModel.selectSession(it) },
                    onRouteGranted = { viewModel.onRouteGranted(it) },
                    onDeleteSession = { viewModel.deleteSession(it) },
                    onResolveDuplicates = { viewModel.resolveDuplicateWorkouts() },
                    onUpdateTargetRace = { viewModel.updateTargetRaceDistance(it) },
                    onUpdateReferenceRace = { dist, dur, hr -> viewModel.updateReferenceRace(dist, dur, hr) },
                    onUseBestRun = { viewModel.useBestRecentRunAsReference() },
                    onSelectPlanWeek = { viewModel.selectPlanWeek(it) },
                    onUpdateTrainingDays = { viewModel.updateTrainingDays(it) },
                    onTogglePlanDayDone = { w, d, done -> viewModel.togglePlanDayDone(w, d, done) },
                    onOpenRaceSetup = { viewModel.setRaceSetupDialogVisible(true) },
                    onDeleteRace = { viewModel.deleteTargetRace() },
                    onUpdateHrZones = { z1, z2, z3, z4, max -> viewModel.updateHrZones(z1, z2, z3, z4, max) },
                    onToggleAutoSync = { viewModel.setAutoSync(it) }
                )

                // Race Setup & Customization Dialog
                if (uiState.showRaceSetupDialog) {
                    RaceSetupDialog(
                        initialRace = uiState.targetRace,
                        defaultPredictedDuration = uiState.targets?.predictedDurationSeconds,
                        onDismiss = { viewModel.setRaceSetupDialogVisible(false) },
                        onSave = { name, date, dist, timeSec, loc ->
                            viewModel.saveTargetRace(name, date, dist, timeSec, loc)
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun PaceLabApp(
    uiState: PaceLabUiState,
    onSelectTab: (Int) -> Unit,
    onPreviousWeek: () -> Unit,
    onNextWeek: () -> Unit,
    onSync: () -> Unit,
    onRequestPermissions: () -> Unit,
    onOpenPlayStore: () -> Unit,
    onLoadDemoData: () -> Unit,
    onSelectSession: (RunSession?) -> Unit,
    onRouteGranted: (androidx.health.connect.client.records.ExerciseRoute?) -> Unit,
    onDeleteSession: (String) -> Unit,
    onResolveDuplicates: () -> Unit,
    onUpdateTargetRace: (RaceDistance) -> Unit,
    onUpdateReferenceRace: (Double, Long, Int) -> Unit,
    onUseBestRun: () -> Unit,
    onSelectPlanWeek: (Int) -> Unit,
    onUpdateTrainingDays: (Int) -> Unit,
    onTogglePlanDayDone: (Int, DayOfWeek, Boolean) -> Unit,
    onOpenRaceSetup: () -> Unit,
    onDeleteRace: () -> Unit,
    onUpdateHrZones: (Int, Int, Int, Int, Int) -> Unit,
    onToggleAutoSync: (Boolean) -> Unit
) {
    Scaffold(
        containerColor = AthleticDarkBg,
        bottomBar = {
            NavigationBar(
                containerColor = AthleticDarkSurface,
                modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars)
            ) {
                NavigationBarItem(
                    selected = uiState.currentTab == 0,
                    onClick = { onSelectTab(0) },
                    icon = {
                        Icon(
                            if (uiState.currentTab == 0) Icons.Filled.Assessment else Icons.Outlined.Assessment,
                            contentDescription = "Weekly"
                        )
                    },
                    label = { Text("Weekly") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = VoltOnPrimary,
                        indicatorColor = VoltPrimary,
                        unselectedIconColor = TextSecondary,
                        selectedTextColor = VoltPrimary,
                        unselectedTextColor = TextSecondary
                    ),
                    modifier = Modifier.testTag("nav_weekly")
                )

                NavigationBarItem(
                    selected = uiState.currentTab == 1,
                    onClick = { onSelectTab(1) },
                    icon = {
                        Icon(
                            if (uiState.currentTab == 1) Icons.Filled.DirectionsRun else Icons.Outlined.DirectionsRun,
                            contentDescription = "Runs"
                        )
                    },
                    label = { Text("Runs") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = VoltOnPrimary,
                        indicatorColor = VoltPrimary,
                        unselectedIconColor = TextSecondary,
                        selectedTextColor = VoltPrimary,
                        unselectedTextColor = TextSecondary
                    ),
                    modifier = Modifier.testTag("nav_runs")
                )

                NavigationBarItem(
                    selected = uiState.currentTab == 2,
                    onClick = { onSelectTab(2) },
                    icon = {
                        Icon(
                            if (uiState.currentTab == 2) Icons.Filled.EmojiEvents else Icons.Outlined.EmojiEvents,
                            contentDescription = "Targets"
                        )
                    },
                    label = { Text("Targets") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = VoltOnPrimary,
                        indicatorColor = VoltPrimary,
                        unselectedIconColor = TextSecondary,
                        selectedTextColor = VoltPrimary,
                        unselectedTextColor = TextSecondary
                    ),
                    modifier = Modifier.testTag("nav_targets")
                )

                NavigationBarItem(
                    selected = uiState.currentTab == 3,
                    onClick = { onSelectTab(3) },
                    icon = {
                        Icon(
                            if (uiState.currentTab == 3) Icons.Filled.CalendarMonth else Icons.Outlined.CalendarMonth,
                            contentDescription = "Plan"
                        )
                    },
                    label = { Text("Plan") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = VoltOnPrimary,
                        indicatorColor = VoltPrimary,
                        unselectedIconColor = TextSecondary,
                        selectedTextColor = VoltPrimary,
                        unselectedTextColor = TextSecondary
                    ),
                    modifier = Modifier.testTag("nav_plan")
                )

                NavigationBarItem(
                    selected = uiState.currentTab == 4,
                    onClick = { onSelectTab(4) },
                    icon = {
                        Icon(
                            if (uiState.currentTab == 4) Icons.Filled.Settings else Icons.Outlined.Settings,
                            contentDescription = "Settings"
                        )
                    },
                    label = { Text("Settings") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = VoltOnPrimary,
                        indicatorColor = VoltPrimary,
                        unselectedIconColor = TextSecondary,
                        selectedTextColor = VoltPrimary,
                        unselectedTextColor = TextSecondary
                    ),
                    modifier = Modifier.testTag("nav_settings")
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (uiState.currentTab) {
                0 -> WeeklyScreen(
                    state = uiState,
                    onPreviousWeek = onPreviousWeek,
                    onNextWeek = onNextWeek,
                    onSync = onSync,
                    onRequestPermissions = onRequestPermissions,
                    onOpenPlayStore = onOpenPlayStore,
                    onLoadDemoData = onLoadDemoData
                )
                1 -> RunsScreen(
                    sessions = uiState.allSessions,
                    duplicateCount = uiState.duplicateSessionCount,
                    selectedSession = uiState.selectedSession,
                    selectedSplits = uiState.selectedSessionSplits,
                    selectedHrPoints = uiState.selectedSessionHrPoints,
                    isLoadingDetail = uiState.isLoadingSessionDetail,
                    onSelectSession = onSelectSession,
                    onDeleteSession = onDeleteSession,
                    onResolveDuplicates = onResolveDuplicates,
                    splitSource = uiState.selectedSplitSource,
                    routeConsentRequired = uiState.selectedRouteConsentRequired,
                    routeElevationGain = uiState.selectedRouteElevationGain,
                    onRouteGranted = onRouteGranted,
                    effortCheck = uiState.selectedSession?.let { session ->
                        uiState.targets?.let { targets ->
                            EasyRunCheck.assess(session, uiState.userSettings.toHrZones(), targets)
                        }
                    }
                )
                2 -> TargetsScreen(
                    userSettings = uiState.userSettings,
                    targets = uiState.targets,
                    onUpdateTargetRace = onUpdateTargetRace,
                    onUpdateReferenceRace = onUpdateReferenceRace,
                    onUseBestRun = onUseBestRun,
                    predictionTrend = uiState.predictionTrend,
                    suggestedRun = uiState.suggestedReferenceRun,
                    goalLabel = (uiState.targetRace?.distance ?: uiState.userSettings.targetRaceDistance).label,
                    onUpdateHrZones = onUpdateHrZones
                )
                3 -> PlanScreen(
                    userSettings = uiState.userSettings,
                    targetRace = uiState.targetRace,
                    planWeeks = uiState.periodizedPlanWeeks,
                    selectedWeekIndex = uiState.selectedPlanWeekIndex,
                    onSelectWeekIndex = onSelectPlanWeek,
                    onUpdateTrainingDays = onUpdateTrainingDays,
                    onToggleDayDone = onTogglePlanDayDone,
                    onOpenRaceSetup = onOpenRaceSetup,
                    onDeleteRace = onDeleteRace,
                    planNote = uiState.planNote
                )
                4 -> SettingsAndPrivacyScreen(
                    userSettings = uiState.userSettings,
                    hasHealthPermissions = uiState.hasHealthPermissions,
                    hasAllHealthPermissions = uiState.hasAllHealthPermissions,
                    syncError = uiState.syncError,
                    autoSyncEnabled = uiState.autoSyncEnabled,
                    hasBackgroundPermission = uiState.hasBackgroundPermission,
                    lastSyncEpochMs = uiState.lastSyncEpochMs,
                    onToggleAutoSync = onToggleAutoSync,
                    onRequestPermissions = onRequestPermissions,
                    onOpenPlayStore = onOpenPlayStore,
                    onLoadDemoData = onLoadDemoData,
                    onUpdateHrZones = onUpdateHrZones
                )
            }
        }
    }
}
