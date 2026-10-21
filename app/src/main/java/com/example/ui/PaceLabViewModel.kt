package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.health.HealthConnectStatus
import com.example.data.health.HealthPermissions
import com.example.data.local.PlanStateEntity
import com.example.data.model.HeartRatePoint
import com.example.data.model.RaceDistance
import com.example.data.model.RaceEvent
import com.example.data.model.RunSession
import com.example.data.model.RunSplit
import com.example.data.model.UserSettings
import com.example.data.model.WeeklySummary
import com.example.data.repository.HealthPermissionMissingException
import com.example.data.repository.PaceLabRepository
import com.example.data.repository.SplitSource
import com.example.data.sync.SyncPrefs
import com.example.data.sync.SyncScheduler
import com.example.util.FourWeekTrainingLoadSummary
import com.example.util.HrZones
import com.example.util.PeriodizedPlanWeek
import com.example.util.PeriodizedPlanner
import com.example.util.PlanGenerator
import com.example.util.PredictionPoint
import com.example.util.PredictionTrendCalculator
import com.example.util.PredictedTargets
import com.example.util.RiegelCalculator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

data class PaceLabUiState(
    val currentTab: Int = 0, // 0: Weekly, 1: Runs, 2: Targets, 3: Plan, 4: Settings
    val currentWeekMonday: LocalDate = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)),
    val weeklySummary: WeeklySummary? = null,
    val fourWeekTrainingLoad: FourWeekTrainingLoadSummary? = null,
    val allSessions: List<RunSession> = emptyList(),
    val duplicateSessionCount: Int = 0,
    val selectedSession: RunSession? = null,
    val selectedSessionSplits: List<RunSplit> = emptyList(),
    val selectedSessionHrPoints: List<HeartRatePoint> = emptyList(),
    val selectedSplitSource: SplitSource = SplitSource.NONE,
    val selectedRouteConsentRequired: Boolean = false,
    val selectedRouteElevationGain: Double? = null,
    val isLoadingSessionDetail: Boolean = false,
    val userSettings: UserSettings = UserSettings(),
    val targetRace: RaceEvent? = null,
    val targets: PredictedTargets? = null,
    val periodizedPlanWeeks: List<PeriodizedPlanWeek> = emptyList(),
    val selectedPlanWeekIndex: Int = 1,
    val healthConnectStatus: HealthConnectStatus = HealthConnectStatus.Available,
    val hasHealthPermissions: Boolean = false,
    val hasAllHealthPermissions: Boolean = false,
    val syncError: String? = null,
    val autoSyncEnabled: Boolean = true,
    val hasBackgroundPermission: Boolean = false,
    val lastSyncEpochMs: Long = 0L,
    val planNote: String? = null,
    val predictionTrend: List<PredictionPoint> = emptyList(),
    val suggestedReferenceRun: RunSession? = null,
    val isUsingDemo: Boolean = false,
    val isSyncing: Boolean = false,
    val showPrivacyPolicy: Boolean = false,
    val showRaceSetupDialog: Boolean = false
)

class PaceLabViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = PaceLabRepository(application.applicationContext)

    /** Permissions to request from Health Connect. */
    val requiredPermissions: Set<String> = repository.healthRepo.requiredPermissions

    private val _uiState = MutableStateFlow(PaceLabUiState())
    val uiState: StateFlow<PaceLabUiState> = _uiState.asStateFlow()

    init {
        val enabled = SyncPrefs.isAutoSyncEnabled(application)
        SyncScheduler.apply(application, enabled)
        _uiState.update {
            it.copy(autoSyncEnabled = enabled, lastSyncEpochMs = SyncPrefs.lastSyncEpochMs(application))
        }
        refreshHealthStatus()
        observeData()
        triggerInitialSync()
    }

    private fun observeData() {
        viewModelScope.launch {
            combine(
                repository.sessionsFlow,
                repository.userSettingsFlow,
                repository.planStatesFlow,
                repository.targetRaceFlow
            ) { sessions, settings, planStates, targetRace ->
                Tuple4(sessions, settings, planStates, targetRace)
            }.collect { (sessions, settings, planStates, targetRace) ->
                // The summary follows the week the user is browsing, but the plan is always
                // anchored to the real current week so browsing never shifts the schedule.
                val viewedMonday = _uiState.value.currentWeekMonday
                val planMonday = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                val weeklySummary = repository.computeWeeklySummary(sessions, viewedMonday, settings)
                val fourWeekLoad = repository.compute4WeekTrainingLoad(sessions, viewedMonday, settings)
                val activeSessions = sessions.filter { !it.isDuplicate }

                val goalDistance = targetRace?.distance?.meters ?: settings.targetRaceDistance.meters

                // Compute targets with Riegel formula
                val targets = RiegelCalculator.computeTargets(
                    refDistanceMeters = settings.refDistanceMeters,
                    refDurationSeconds = settings.refDurationSeconds,
                    targetDistanceMeters = goalDistance
                )

                // Generate periodized training schedule leading up to race date
                val zones = HrZones(
                    z1Max = settings.z1Max,
                    z2Min = settings.z2Min,
                    z2Max = settings.z2Max,
                    z3Min = settings.z3Min,
                    z3Max = settings.z3Max,
                    z4Min = settings.z4Min,
                    z4Max = settings.z4Max,
                    maxHr = settings.maxHr
                )

                val volumeAdvice = PeriodizedPlanner.adaptiveBaseVolume(
                    sessions = activeSessions,
                    planMonday = planMonday,
                    daysPerWeek = settings.trainingDaysPerWeek
                )
                val baseVolume = volumeAdvice.baseKm

                val generatedWeeks = PeriodizedPlanner.generatePeriodizedPlan(
                    raceEvent = targetRace,
                    baseWeeklyVolumeKm = baseVolume,
                    daysPerWeek = settings.trainingDaysPerWeek,
                    targets = targets,
                    zones = zones,
                    currentMonday = planMonday
                )

                // Match with actual sessions & Room plan states
                val zone = ZoneId.systemDefault()
                val realSessionPairs = activeSessions.map { session ->
                    val date = session.startTime.atZone(zone).toLocalDate()
                    Pair(date, Pair(session.distanceMeters, session.avgPaceSeconds))
                }

                val matchedWeeks = generatedWeeks.map { week ->
                    val matchedDays = PlanGenerator.matchRealSessions(week.days, week.weekStartMonday, realSessionPairs)
                    val finalizedDays = matchedDays.map { day ->
                        val key = planKey(week.weekStartMonday, day.dayOfWeek)
                        val storedState = planStates[key]
                        if (storedState != null) {
                            day.copy(isDone = storedState.isDone)
                        } else {
                            day
                        }
                    }
                    week.copy(days = finalizedDays)
                }

                val duplicatesCount = sessions.count { it.isDuplicate }

                // Suggest a new reference only when a recent run predicts a clearly faster race
                // (more than 1%) than the reference currently in use.
                val currentPrediction = RiegelCalculator
                    .predictRaceTimeSeconds(settings.refDistanceMeters, settings.refDurationSeconds, goalDistance)
                val suggestion = PredictionTrendCalculator.bestRecentRun(sessions, goalDistance)
                    ?.takeIf { PredictionTrendCalculator.predictSeconds(it, goalDistance) < currentPrediction * 0.99 }

                _uiState.update { current ->
                    current.copy(
                        allSessions = sessions,
                        duplicateSessionCount = duplicatesCount,
                        userSettings = settings,
                        targetRace = targetRace,
                        weeklySummary = weeklySummary,
                        fourWeekTrainingLoad = fourWeekLoad,
                        planNote = volumeAdvice.note,
                        predictionTrend = PredictionTrendCalculator.trend(sessions, goalDistance),
                        suggestedReferenceRun = suggestion,
                        targets = targets,
                        periodizedPlanWeeks = matchedWeeks,
                        isUsingDemo = sessions.any { it.isSeed }
                    )
                }
            }
        }
    }

    fun refreshHealthStatus() {
        viewModelScope.launch {
            val status = repository.getSdkStatus()
            val granted = repository.getGrantedPermissions()
            _uiState.update {
                it.copy(
                    healthConnectStatus = status,
                    hasHealthPermissions = granted.containsAll(HealthPermissions.core),
                    hasAllHealthPermissions = granted.containsAll(HealthPermissions.all),
                    hasBackgroundPermission = HealthPermissions.BACKGROUND in granted
                )
            }
        }
    }

    private fun triggerInitialSync() {
        viewModelScope.launch {
            runSync()
        }
    }

    fun syncFromHealthConnect() {
        viewModelScope.launch {
            runSync()
            refreshHealthStatus()
        }
    }

    private suspend fun runSync() {
        _uiState.update { it.copy(isSyncing = true, syncError = null) }
        val result = repository.syncData()
        val error = result.exceptionOrNull()
        if (error == null) SyncPrefs.markSynced(getApplication())
        _uiState.update {
            it.copy(
                isSyncing = false,
                lastSyncEpochMs = SyncPrefs.lastSyncEpochMs(getApplication()),
                // A missing permission is shown by the status text, not as an error.
                syncError = if (error == null || error is HealthPermissionMissingException) null
                else (error.message ?: "Sync failed")
            )
        }
    }

    fun setAutoSync(enabled: Boolean) {
        SyncPrefs.setAutoSyncEnabled(getApplication(), enabled)
        SyncScheduler.apply(getApplication(), enabled)
        _uiState.update { it.copy(autoSyncEnabled = enabled) }
    }

    fun loadDemoData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isSyncing = true) }
            repository.seedDemoData()
            _uiState.update { it.copy(isSyncing = false, isUsingDemo = true) }
        }
    }

    fun selectTab(tabIndex: Int) {
        _uiState.update { it.copy(currentTab = tabIndex) }
    }

    fun selectWeekOffset(offsetWeeks: Long) {
        _uiState.update { current ->
            val newMonday = current.currentWeekMonday.plusWeeks(offsetWeeks)
            val newSummary = repository.computeWeeklySummary(
                allSessions = current.allSessions,
                weekStartMonday = newMonday,
                userSettings = current.userSettings
            )
            val newLoad = repository.compute4WeekTrainingLoad(
                allSessions = current.allSessions,
                anchorMonday = newMonday,
                userSettings = current.userSettings
            )
            current.copy(
                currentWeekMonday = newMonday,
                weeklySummary = newSummary,
                fourWeekTrainingLoad = newLoad
            )
        }
    }

    /** Called with the route the user just allowed (null if they refused or closed the dialog). */
    fun onRouteGranted(route: androidx.health.connect.client.records.ExerciseRoute?) {
        val session = _uiState.value.selectedSession ?: return
        if (route == null) return
        loadSessionDetails(session, route)
    }

    fun selectSession(session: RunSession?) {
        if (session == null) {
            _uiState.update {
                it.copy(
                    selectedSession = null,
                    selectedSessionSplits = emptyList(),
                    selectedSessionHrPoints = emptyList(),
                    selectedSplitSource = SplitSource.NONE,
                    selectedRouteConsentRequired = false,
                    selectedRouteElevationGain = null
                )
            }
            return
        }

        _uiState.update { it.copy(selectedSession = session) }
        loadSessionDetails(session, null)
    }

    private fun loadSessionDetails(
        session: RunSession,
        grantedRoute: androidx.health.connect.client.records.ExerciseRoute?
    ) {
        _uiState.update { it.copy(isLoadingSessionDetail = true) }
        viewModelScope.launch {
            val details = repository.getSessionDetails(session, grantedRoute)
            _uiState.update {
                // Ignore a late result if the user already opened another run.
                if (it.selectedSession?.id != session.id) return@update it
                it.copy(
                    selectedSessionSplits = details.splits,
                    selectedSessionHrPoints = details.hrPoints,
                    selectedSplitSource = details.splitSource,
                    selectedRouteConsentRequired = details.routeConsentRequired,
                    selectedRouteElevationGain = details.routeElevationGainMeters,
                    isLoadingSessionDetail = false
                )
            }
        }
    }

    fun deleteSession(sessionId: String) {
        viewModelScope.launch {
            repository.deleteSession(sessionId)
            if (_uiState.value.selectedSession?.id == sessionId) {
                selectSession(null)
            }
        }
    }

    fun resolveDuplicateWorkouts() {
        viewModelScope.launch {
            val duplicateIds = _uiState.value.allSessions.filter { it.isDuplicate }.map { it.id }
            if (duplicateIds.isNotEmpty()) {
                repository.deleteSessions(duplicateIds)
            }
        }
    }

    fun selectPlanWeek(weekIndex: Int) {
        _uiState.update { it.copy(selectedPlanWeekIndex = weekIndex) }
    }

    fun togglePlanDayDone(weekIndex: Int, dayOfWeek: DayOfWeek, isDone: Boolean) {
        // Keyed by the real week start, so the state stays on its date when the plan is re-anchored.
        val week = _uiState.value.periodizedPlanWeeks.firstOrNull { it.weekIndex == weekIndex } ?: return
        viewModelScope.launch {
            repository.togglePlanDayDone(planKey(week.weekStartMonday, dayOfWeek), isDone)
        }
    }

    private fun planKey(weekMonday: LocalDate, day: DayOfWeek) = "${weekMonday}_${day.name}"

    fun setRaceSetupDialogVisible(visible: Boolean) {
        _uiState.update { it.copy(showRaceSetupDialog = visible) }
    }

    fun saveTargetRace(
        raceName: String,
        raceDate: LocalDate,
        distance: RaceDistance,
        targetDurationSeconds: Long?,
        location: String? = null
    ) {
        viewModelScope.launch {
            val targetPace = if (targetDurationSeconds != null && distance.meters > 0) {
                ((targetDurationSeconds.toDouble() / (distance.meters / 1000.0))).toInt()
            } else null

            val raceEvent = RaceEvent(
                id = 1,
                raceName = raceName.ifBlank { "${distance.label} Target Race" },
                raceDate = raceDate,
                distance = distance,
                targetDurationSeconds = targetDurationSeconds,
                targetPaceSeconds = targetPace,
                location = location
            )
            repository.saveTargetRace(raceEvent)
            _uiState.update { it.copy(showRaceSetupDialog = false) }
        }
    }

    fun deleteTargetRace() {
        viewModelScope.launch {
            repository.deleteTargetRace()
        }
    }

    fun updateReferenceRace(distanceMeters: Double, durationSeconds: Long, avgHeartRate: Int) {
        viewModelScope.launch {
            val updated = _uiState.value.userSettings.copy(
                refDistanceMeters = distanceMeters,
                refDurationSeconds = durationSeconds,
                refAvgHeartRate = avgHeartRate
            )
            repository.saveUserSettings(updated)
        }
    }

    /**
     * Uses the recent run that predicts the fastest race at the current goal distance as the
     * reference. Only real, non duplicate runs of at least 4.8 km from the last 12 weeks count.
     */
    fun useBestRecentRunAsReference() {
        val state = _uiState.value
        val goal = state.targetRace?.distance?.meters ?: state.userSettings.targetRaceDistance.meters
        val best = PredictionTrendCalculator.bestRecentRun(state.allSessions, goal) ?: return
        updateReferenceRace(
            distanceMeters = best.distanceMeters,
            durationSeconds = best.durationSeconds,
            avgHeartRate = best.avgHeartRate ?: state.userSettings.refAvgHeartRate
        )
    }

    fun updateTargetRaceDistance(distance: RaceDistance) {
        viewModelScope.launch {
            val updated = _uiState.value.userSettings.copy(targetRaceDistance = distance)
            repository.saveUserSettings(updated)
        }
    }

    fun updateTrainingDays(days: Int) {
        viewModelScope.launch {
            val updated = _uiState.value.userSettings.copy(trainingDaysPerWeek = days.coerceIn(2, 4))
            repository.saveUserSettings(updated)
        }
    }

    fun updateHrZones(z1Max: Int, z2Max: Int, z3Max: Int, z4Max: Int, maxHr: Int) {
        // Ignore half-typed or inconsistent thresholds instead of saving broken zones.
        if (!(z1Max > 0 && z1Max < z2Max && z2Max < z3Max && z3Max < z4Max && z4Max < maxHr)) return
        viewModelScope.launch {
            val updated = _uiState.value.userSettings.copy(
                z1Max = z1Max,
                z2Min = z1Max + 1,
                z2Max = z2Max,
                z3Min = z2Max + 1,
                z3Max = z3Max,
                z4Min = z3Max + 1,
                z4Max = z4Max,
                maxHr = maxHr
            )
            repository.saveUserSettings(updated)
        }
    }

    fun setPrivacyPolicyVisible(visible: Boolean) {
        _uiState.update { it.copy(showPrivacyPolicy = visible) }
    }
}

private data class Tuple4<A, B, C, D>(
    val a: A,
    val b: B,
    val c: C,
    val d: D
)
