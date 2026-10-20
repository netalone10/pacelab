# PaceLab: Activity KM Splits & Training Target Zones Revision

Revise the activity KM splits detail view with interactive sync guidance for Samsung Health and Health Connect, and comprehensively redesign the Target screen with crystal-clear visual training pace zones, heart rate targets, and standardized athletic English terminology.

## User Review & Critical Decisions

> [!IMPORTANT]
> The following directions were confirmed during interactive clarification and govern this implementation:

- **Confirmed Decision 1 (KM Split Handling)**: When split records are unavailable from third-party trackers (e.g. Samsung Health only syncing an aggregate session without detailed distance milestones), the app will present an informative, actionable sync guide card with direct shortcuts to Health Connect permissions, rather than fabricating mock data.
- **Confirmed Decision 2 (Target Screen Focus)**: Redesign the Target screen to emphasize visual clarity and depth for training pace zones (Easy Run, Long Run, Tempo, Interval, Repetitions), with visual pace indicators, % HR max & BPM targets, purpose explanation, and practical workout structures.
- **Confirmed Decision 3 (Language Standardization)**: Standardize all copy in the Targets screen and Activity Details on clean, modern international athletic English (e.g. "Target Race & Paces", "Training Pace Zones", "Kilometer Splits", "Sync Troubleshooting Guide").

---

## 1. Overview & Core Concept

### What It Delivers
1. **Activity Detail KM Splits & Sync Diagnostics**:
   - An enhanced `PaceSplitsTable` and `RunDetailBottomSheet` that accurately displays per-kilometer pace and heart rate when distance records are present.
   - When distance intervals are insufficient or missing from the fitness provider (a common occurrence with Samsung Health and wearable exports), an integrated **Sync & Permissions Diagnostic Guide** explains why the tracker didn't write interval distance records, provides step-by-step instructions for Samsung Health / Health Connect settings, and includes an interactive button to open Health Connect system permissions.
2. **Redesigned Target & Training Zones Screen (`TargetsScreen`)**:
   - An intuitive, cohesive hierarchy: **Target Race Selection** (5K, 10K, Half, Full) ➔ **Reference Performance Input** (distance, duration, HR, with "Use Best Run" chip) ➔ **Predicted Race Finish Banner** ➔ **Interactive Training Pace Zones**.
   - Each training pace zone (Easy, Long Run, Tempo/Threshold, VO2 Max Intervals, Repetitions) receives a high-contrast card featuring:
     - Color-coded zone pill and distinct left accent border (M3 Zone colors).
     - Target pace range (`MM:SS /km`) and speed equivalent.
     - Target Heart Rate range (BPM and % Max HR).
     - Recommended weekly volume allocation (e.g., "70–80% of weekly mileage").
     - Concrete session blueprint (e.g., "45–60 min steady aerobic effort" or "5 × 1,000m @ 4:15/km with 2:00 recovery").
3. **Consistent Athletic English**:
   - Replaces mixed Indonesian/English labels with clean, professional athletic terminology matching Garmin Connect, Strava, and TrainingPeaks.

---

## 2. User Experience & Visual Design

### User Flows

```
[ Activities List ] ──── Tap Session ───► [ Workout Summary Sheet ]
                                                │
                                                ├─ Has Distance Intervals? ──► [ Full KM Splits Table (Pace + HR) ]
                                                │
                                                └─ Missing Intervals? ───────► [ Health Connect Sync Guide Card ]
                                                                                   │
                                                                                   └─ Tap "Open Health Connect" ──► System Settings

[ Target Tab ] ─────────────────────────► [ Target Race & Paces Screen ]
                                                │
                                                ├─ Step 1: Goal Race Chips (5K · 10K · 21.1K · 42.2K)
                                                ├─ Step 2: Reference Performance Card (Input or "Use Best Run")
                                                ├─ Step 3: Predicted Finish Time & Target Race Pace Hero Card
                                                └─ Step 4: Visual Training Pace Zones
                                                    ├─ Easy / Aerobic (Zone 1-2)
                                                    ├─ Long Run / Endurance (Zone 2)
                                                    ├─ Tempo / Lactate Threshold (Zone 3)
                                                    ├─ Interval / VO2 Max (Zone 4)
                                                    └─ Speed / Repetition (Zone 5)
```

### Visual Styling & Theme

- **Color Tokens**:
  - `VoltPrimary` (`#D4FF00`): Primary athletic neon highlight for target race pace and active selections.
  - `AthleticDarkCard` (`#1A1D24`) & `AthleticDarkSurface` (`#12141A`): Deep obsidian athletic surfaces.
  - `Zone1Blue` (`#3B82F6`): Recovery / Easy pace accents.
  - `Zone2Green` (`#10B981`): Aerobic / Long run endurance accents.
  - `Zone3Amber` (`#F59E0B`): Tempo / Threshold accents.
  - `Zone4Orange` (`#F97316`): VO2 Max interval speedwork accents.
  - `Zone5Red` (`#EF4444`): Anaerobic speed / repetition accents.
- **Typography & Cards**:
  - Big, bold monospace numerals for pace ranges and predicted race durations.
  - M3 card shapes (`RoundedCornerShape(16.dp)`) with crisp `AthleticBorder` outlines.

---

## 3. Key Product Decisions & Trade-Offs

- **No Synthetic Data Generation for Health Connect Sessions**:
  - *Decision*: In compliance with project guidelines, the app does not fabricate simulated kilometer splits for real Health Connect runs when Samsung Health didn't record distance intervals.
  - *Rationale*: Runners rely on split analysis for true pacing fidelity; fabricating numbers misleads athletes. Instead, educating the user on Samsung Health configuration and providing a direct system intent to Health Connect permissions empowers the user to fix the data stream.
- **SplitCalculator Robustness**:
  - *Decision*: Relax `SplitCalculator`'s strict `MIN_INTERVALS = 3` limitation when distance records cover >= 1000m with valid non-zero timestamps, allowing sessions with 1 or 2 coarse records to compute valid full-km crossings if mathematically sound.
- **Comprehensive Training Zone Framework**:
  - *Decision*: Expand the 4 training pace cards to 5 distinct athletic zones derived from Jack Daniels' VDOT and Riegel principles, adding clear HR ranges (% HR Max and absolute BPM based on user's max HR / resting HR) and sample workouts.

---

## 4. Technical Architecture & Data Strategy

### System & Component Architecture

```
┌────────────────────────────────────────────────────────────────────────┐
│ UI Layer                                                               │
│                                                                        │
│   ┌───────────────────────────┐      ┌──────────────────────────────┐  │
│   │ RunsScreen                │      │ TargetsScreen                │  │
│   │  ├─ RunDetailBottomSheet  │      │  ├─ GoalRaceChips            │  │
│   │  ├─ PaceSplitsTable       │      │  ├─ ReferencePerformanceCard │  │
│   │  └─ SyncDiagnosticCard    │      │  ├─ PredictedHeroCard        │  │
│   │                           │      │  └─ TrainingPaceZoneCard(x5) │  │
│   └─────────────┬─────────────┘      └──────────────┬───────────────┘  │
└─────────────────┼───────────────────────────────────┼──────────────────┘
                  │                                   │
┌─────────────────▼───────────────────────────────────▼──────────────────┐
│ ViewModel & Utilities                                                  │
│                                                                        │
│   PaceLabViewModel                                                     │
│    ├─ selectSession(session) -> loads splits & HR points              │
│    ├─ updateReferenceRace(distance, duration, hr)                      │
│    └─ updateTargetRace(distance)                                       │
│                                                                        │
│   Calculators & Formatters                                             │
│    ├─ SplitCalculator: computes km splits from DistanceIntervals       │
│    ├─ TargetCalculator: computes Riegel target times & 5 pace zones    │
│    └─ PaceUtils: pace parsing, duration formatting, and speed formulas │
└─────────────────┬──────────────────────────────────────────────────────┘
                  │
┌─────────────────▼──────────────────────────────────────────────────────┐
│ Data Layer                                                             │
│                                                                        │
│   HealthConnectRepository                                              │
│    ├─ getSessionSplitsAndHr(session)                                   │
│    └─ readRecords(DistanceRecord, HeartRateRecord)                     │
│                                                                        │
│   PaceLabRepository & Room Database                                    │
│    └─ UserSettings, CachedSessions, IgnoredSessions, TargetRace        │
└────────────────────────────────────────────────────────────────────────┘
```

### Key Changes by File

1. `app/src/main/java/com/example/util/SplitCalculator.kt`:
   - Refactor `SplitCalculator.compute` to be resilient to varying distance record granularities written by different vendors (Samsung Health, Google Fit, Garmin).
   - Ensure clean time calculation without negative delta or division-by-zero crashes.
2. `app/src/main/java/com/example/ui/components/PaceComponents.kt`:
   - Enhance `PaceSplitsTable` with an integrated `SyncDiagnosticCard` when splits are unavailable:
     - Clear explanation: "Why is split data unavailable?"
     - Instructions: "Samsung Health requires 'Distance' and 'Exercise' read & write permissions enabled in Health Connect settings."
     - Action button: Launches `androidx.health.connect.client.HealthConnectClient.getHealthConnectIntent()` or system Health Connect settings.
3. `app/src/main/java/com/example/ui/screens/TargetsScreen.kt`:
   - Replace mixed language with clean international English.
   - Restructure into 4 clear visual sections:
     - Header & Goal Selection: 5K, 10K, Half Marathon, Marathon.
     - Reference Performance: Input fields + "Auto-fill with Best Run" CTA + explanation banner.
     - Target Hero Card: Predicted Finish Time, Target Pace, Estimated Speed (km/h).
     - Derived Training Pace Zones: 5 polished cards (Easy, Long Run, Tempo, VO2 Max Intervals, Repetition) complete with target pace, % HR max, BPM range, purpose, and structured workout example.
4. `app/src/main/java/com/example/util/TargetCalculator.kt` (or `PaceUtils.kt`):
   - Ensure calculations for all 5 training zones (Easy, Long Run, Tempo, Interval, Repetition) include proper pace ranges, target HR percentages (50-60%, 60-70%, 70-80%, 80-90%, 90-100%), and workout suggestions.
5. `app/src/test/java/com/example/PlanAndDataLogicTest.kt`:
   - Update and extend unit tests to verify training pace zone generation and split calculations.
