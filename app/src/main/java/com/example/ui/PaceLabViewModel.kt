package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.health.HealthConnectStatus
import com.example.data.local.PlanStateEntity
import com.example.data.model.HeartRatePoint
import com.example.data.model.RaceDistance
import com.example.data.model.RaceEvent
import com.example.data.model.RunSession
import com.example.data.model.RunSplit
import com.example.data.model.UserSettings
import com.example.data.model.WeeklySummary
import com.example.data.repository.PaceLabRepository
import com.example.util.HrZones
import com.example.util.PeriodizedPlanWeek
import com.example.util.PeriodizedPlanner
import com.example.util.PlanGenerator
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
    val allSessions: List<RunSession> = emptyList(),
    val duplicateSessionCount: Int = 0,
    val selectedSession: RunSession? = null,
    val selectedSessionSplits: List<RunSplit> = emptyList(),
    val selectedSessionHrPoints: List<HeartRatePoint> = emptyList(),
    val isLoadingSessionDetail: Boolean = false,
    val userSettings: UserSettings = UserSettings(),
    val targetRace: RaceEvent? = null,
    val targets: PredictedTargets? = null,
    val periodizedPlanWeeks: List<PeriodizedPlanWeek> = emptyList(),
    val selectedPlanWeekIndex: Int = 1,
    val healthConnectStatus: HealthConnectStatus = HealthConnectStatus.Available,
    val hasHealthPermissions: Boolean = false,
    val isUsingDemo: Boolean = false,
    val isSyncing: Boolean = false,
    val showPrivacyPolicy: Boolean = false,
    val showRaceSetupDialog: Boolean = false
)

class PaceLabViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = PaceLabRepository(application.applicationContext)

    private val _uiState = MutableStateFlow(PaceLabUiState())
    val uiState: StateFlow<PaceLabUiState> = _uiState.asStateFlow()

    init {
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
                val currentMonday = _uiState.value.currentWeekMonday
                val weeklySummary = repository.computeWeeklySummary(sessions, currentMonday, settings)

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

                val baseVolume = (weeklySummary.totalDistanceMeters / 1000.0).coerceIn(16.0, 35.0)

                val generatedWeeks = PeriodizedPlanner.generatePeriodizedPlan(
                    raceEvent = targetRace,
                    baseWeeklyVolumeKm = baseVolume,
                    daysPerWeek = settings.trainingDaysPerWeek,
                    targets = targets,
                    zones = zones,
                    currentMonday = currentMonday
                )

                // Match with actual sessions & Room plan states
                val zone = ZoneId.systemDefault()
                val realSessionPairs = sessions.map { session ->
                    val date = session.startTime.atZone(zone).toLocalDate()
                    Pair(date, Pair(session.distanceMeters, session.avgPaceSeconds))
                }

                val matchedWeeks = generatedWeeks.map { week ->
                    val weekStart = currentMonday.plusWeeks((week.weekIndex - 1).toLong())
                    val matchedDays = PlanGenerator.matchRealSessions(week.days, weekStart, realSessionPairs)
                    val finalizedDays = matchedDays.map { day ->
                        val key = "w${week.weekIndex}_${day.dayOfWeek.name}"
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

                _uiState.update { current ->
                    current.copy(
                        allSessions = sessions,
                        duplicateSessionCount = duplicatesCount,
                        userSettings = settings,
                        targetRace = targetRace,
                        weeklySummary = weeklySummary,
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
            val hasPerms = repository.hasHealthPermissions()
            _uiState.update {
                it.copy(
                    healthConnectStatus = status,
                    hasHealthPermissions = hasPerms
                )
            }
        }
    }

    private fun triggerInitialSync() {
        viewModelScope.launch {
            _uiState.update { it.copy(isSyncing = true) }
            repository.syncData(forceSeedDemo = false)
            _uiState.update { it.copy(isSyncing = false) }
        }
    }

    fun syncFromHealthConnect() {
        viewModelScope.launch {
            _uiState.update { it.copy(isSyncing = true) }
            repository.syncData(forceSeedDemo = false)
            refreshHealthStatus()
            _uiState.update { it.copy(isSyncing = false) }
        }
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
            current.copy(
                currentWeekMonday = newMonday,
                weeklySummary = newSummary
            )
        }
    }

    fun selectSession(session: RunSession?) {
        if (session == null) {
            _uiState.update {
                it.copy(
                    selectedSession = null,
                    selectedSessionSplits = emptyList(),
                    selectedSessionHrPoints = emptyList()
                )
            }
            return
        }

        _uiState.update { it.copy(selectedSession = session, isLoadingSessionDetail = true) }
        viewModelScope.launch {
            val (splits, hrPoints) = repository.getSessionDetails(session)
            _uiState.update {
                it.copy(
                    selectedSessionSplits = splits,
                    selectedSessionHrPoints = hrPoints,
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
        viewModelScope.launch {
            val key = "w${weekIndex}_${dayOfWeek.name}"
            repository.togglePlanDayDone(key, isDone)
        }
    }

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

    fun useBestRecentRunAsReference() {
        val sessions = _uiState.value.allSessions
        if (sessions.isEmpty()) return

        val bestRun = sessions
            .filter { it.distanceMeters >= 3000.0 }
            .minByOrNull { it.avgPaceSeconds } ?: sessions.firstOrNull()

        bestRun?.let { run ->
            updateReferenceRace(
                distanceMeters = run.distanceMeters,
                durationSeconds = run.durationSeconds,
                avgHeartRate = run.avgHeartRate ?: 151
            )
        }
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
