# Targets Screen Restructure: Minimalist Hero & Dedicated Baseline Tab

Restructure the PaceLab **Targets** screen (`TargetsScreen.kt`) into a clean, modern two-tab athletic dashboard. The first tab features a **Minimalist Hero Summary** with a modal configuration sheet for goal targets and pacing zones; the second tab is a dedicated **Baseline & HR Settings** hub for calibrating the reference performance run and physiological heart rate thresholds.

---

### User Review & Critical Decisions

> [!IMPORTANT]
> Based on your confirmed choices:
> 1. **Layout Strategy**: A minimalist hero summary on the main view with a modal configuration sheet (`ModalBottomSheet`) for configuring race distance and pacing goals without cluttering the screen.
> 2. **Baseline & HR Placement**: Relocated into a dedicated second tab (`Baseline & HR Settings`) within the Targets screen so the main view stays focused on race predictions and daily training pace zones.
> 3. **Heart Rate Integration**: Seamlessly connect HR threshold updates (`onUpdateHrZones`) in the Baseline tab so zone changes reflect immediately across all training zone cards.

---

### 1. Overview & Core Concept

- **What It Does**: Transforms the previously monolithic scrolling screen into a structured two-view interface:
  - **Tab 1: Target & Zones**: A streamlined hero card showing predicted finish time, target pace, speed in km/h, quick modal button for changing race distances, 28-day prediction trend progression, and individualized training pace zones (Easy, Long Run, Tempo, Intervals, Repetitions).
  - **Tab 2: Baseline & HR Settings**: Dedicated performance calibration center featuring reference run inputs (distance, time, average HR), 1-tap "Use Best Run" detection, VDOT calculations (Jack Daniels formula), and physiological Heart Rate zone configuration (Z1–Z5 thresholds).
- **Target Audience**: Runners training for 5K, 10K, 15K, or Half Marathons who need clear, distraction-free pace guidance for their workouts and easy access to calibration settings.
- **Key Value**: Eliminates cognitive overload on the main screen while keeping deep configuration and physiological calibration accessible in an organized, dedicated tab.

---

### 2. User Experience & Visual Design

#### Visual Identity & Theme
- **Theme**: Athletic High-Performance Dark (`AthleticDarkBg: #0A0C10`, `AthleticDarkCard: #12151C`, `VoltPrimary: #D2FF00`, `VoltOnPrimary: #0A0C10`).
- **Zone Accents**:
  - Zone 1 & 2 (Easy & Long Run): `#38EF7D` (Aerobic Green) / `#2196F3` (Endurance Blue)
  - Zone 3 (Tempo / Threshold): `#FFB300` (Amber)
  - Zone 4 (Intervals / VO2 Max): `#FF6D00` (Orange)
  - Zone 5 (Repetitions / Anaerobic): `#FF3366` (Speed Red)

#### Key User Flows & Screen Architecture

```
┌──────────────────────────────────────────────────────────────┐
│                    TARGETS & TRAINING PACES                  │
├──────────────────────────────────────────────────────────────┤
│  [  Target & Zones  ]  |  [  Baseline & HR Settings  ]       │
├──────────────────────────────────────────────────────────────┤
│  TAB 1: TARGET & ZONES                                       │
│  ┌────────────────────────────────────────────────────────┐  │
│  │ ⚡ PREDICTED 10K FINISH          [ ⚙ Customize Goal ] │  │
│  │                                                        │  │
│  │   44:28                        4:27 /km                │  │
│  │   Predicted Finish Time        Target Pace · 13.5 km/h │  │
│  └────────────────────────────────────────────────────────┘  │
│                                                              │
│  ┌─ Faster Run Detected Banner (Conditional) ─────────────┐  │
│  │ Recent 5.31 km in 34:10 is faster. [ Use This Run ]    │  │
│  └────────────────────────────────────────────────────────┘  │
│                                                              │
│  ┌─ 28-Day Prediction Trend Card ─────────────────────────┐  │
│  │ +0:45 faster than baseline · Rolling 4-week progression│  │
│  └────────────────────────────────────────────────────────┘  │
│                                                              │
│  ─── INDIVIDUALIZED TRAINING PACE ZONES ───────────────────  │
│  [Zone 1-2 Easy] 5:07 - 5:34 /km  |  < 140 bpm               │
│  [Zone 2 Long]   4:54 - 5:20 /km  |  118 - 140 bpm           │
│  [Zone 3 Tempo]  4:32 - 4:46 /km  |  141 - 160 bpm           │
│  [Zone 4 Interv] 4:14 - 4:27 /km  |  161 - 179 bpm           │
│  [Zone 5 Reps]   3:55 - 4:11 /km  |  > 179 bpm               │
└──────────────────────────────────────────────────────────────┘
```

#### Modal Configuration Sheet (`GoalConfigurationSheet`)
Tapping **"Customize Goal"** or the target race badge opens an M3 `ModalBottomSheet`:
- Distance Selector chips: `5K`, `10K`, `15K`, `21K Half Marathon`.
- Riegel formula multiplier summary and projected completion time calculator.
- Quick save and dismiss action.

#### Tab 2: Baseline & HR Settings
```
┌──────────────────────────────────────────────────────────────┐
│  TAB 2: BASELINE & HR SETTINGS                               │
│  ┌─ Reference Performance Baseline ───────────────────────┐  │
│  │ Distance (km)   Time (MM:SS)   Avg HR     [Use Best]  │  │
│  │ [ 5.31       ] [ 35:57      ] [ 151  ]                 │  │
│  │ Baseline Pace: 6:46 /km · Est. VDOT: 38 (Daniels)      │  │
│  └────────────────────────────────────────────────────────┘  │
│                                                              │
│  ┌─ Heart Rate & Physiology Calibration ──────────────────┐  │
│  │ Max Heart Rate: 190 bpm                                │  │
│  │ Z1 Active Recovery : < 118 bpm                         │  │
│  │ Z2 Aerobic Base     : 118 – 140 bpm                    │  │
│  │ Z3 Tempo Threshold : 141 – 160 bpm                    │  │
│  │ Z4 VO2 Max Interval : 161 – 179 bpm                    │  │
│  │ Z5 Anaerobic Power  : > 179 bpm                        │  │
│  │ [ Save HR Settings ]                                   │  │
│  └────────────────────────────────────────────────────────┘  │
│                                                              │
│  ┌─ Calculation Principles & Methodology ─────────────────┐  │
│  │ • Riegel Power Formula: T2 = T1 × (D2 / D1)^1.06       │  │
│  │ • Daniels VDOT: Metabolic running economy index        │  │
│  └────────────────────────────────────────────────────────┘  │
└──────────────────────────────────────────────────────────────┘
```

---

### 3. Key Product Decisions & Trade-Offs

- **Decision 1: Two-Tab Segmented Control vs. Full Multi-screen Navigation**:
  - *Chosen Approach*: In-screen top tab bar (`TabRow` / `PrimaryTabRow` or custom pill switch) within `TargetsScreen`.
  - *Why*: Preserves the 5-tab main bottom navigation (`Weekly`, `Runs`, `Targets`, `Plan`, `Settings`) while neatly segregating workout-day guidance from setup/calibration.
  - *Alternatives Considered*: Moving Baseline to the global Settings tab was considered, but having it right next to Targets in a dedicated sub-tab lets runners instantly see how adjusting their baseline run changes their race prediction.
- **Decision 2: Modal Bottom Sheet for Target Race Configuration**:
  - *Chosen Approach*: An M3 `ModalBottomSheet` launched from the hero card's "Customize Goal" button.
  - *Why*: Gives the main screen a sleek, minimalist aesthetic while allowing rich distance selection and race explanations when needed.
- **Decision 3: Direct HR Zone Updating from Targets Screen**:
  - *Chosen Approach*: Pass `onUpdateHrZones` into `TargetsScreen` so runners can adjust their HR boundaries in Tab 2 without jumping to the global settings screen.

---

### 4. Technical Architecture & Component Structure

```
                    ┌───────────────────────────┐
                    │       MainActivity        │
                    │  (UiState & Event Handlers)│
                    └─────────────┬─────────────┘
                                  │
                                  ▼
                    ┌───────────────────────────┐
                    │       TargetsScreen       │
                    │   (selectedSubTab state)  │
                    └──────┬─────────────┬──────┘
                           │             │
              ┌────────────┘             └────────────┐
              ▼                                       ▼
 ┌──────────────────────────┐            ┌──────────────────────────┐
 │      TargetsTabContent   │            │   BaselineHrTabContent   │
 │                          │            │                          │
 │ • Minimalist Hero Card   │            │ • Reference Run Card     │
 │ • Faster Run Banner      │            │   (Distance/Time/Avg HR) │
 │ • 28-Day Trend Card      │            │ • "Use Best Run" Action  │
 │ • TrainingPaceCard (Z1-5)│            │ • VDOT & Riegel Guide    │
 └────────────┬─────────────┘            │ • HeartRateSettingsCard  │
              │                          └──────────────────────────┘
              ▼
 ┌──────────────────────────┐
 │  GoalConfigurationSheet  │
 │  (M3 ModalBottomSheet)   │
 │ • RaceDistance Chips     │
 │ • Projected summary      │
 └──────────────────────────┘
```

#### State & Event Handlers Mapping
1. `selectedSubTab: Int` (0 = Target & Zones, 1 = Baseline & HR Settings).
2. `showGoalSheet: Boolean` controlling `GoalConfigurationSheet`.
3. `onUpdateTargetRace(RaceDistance)`: Dispatched from the sheet or hero.
4. `onUpdateReferenceRace(Double, Long, Int)`: Dispatched when baseline inputs change or when "Use This Run" / "Use Best Run" is clicked.
5. `onUpdateHrZones(Int, Int, Int, Int, Int)`: Dispatched when HR thresholds are saved in Tab 2.

---

### 5. Verification Plan

1. **Static Analysis & Compilation**:
   - Run `compile_applet` to ensure syntax, parameter alignments, and imports are valid.
2. **Unit Tests**:
   - Run `gradle :app:testDebugUnitTest` to verify calculators (`RiegelCalculator`, `VdotCalculator`, `EasyRunCheck`) and view models pass without regressions.
3. **UI & Interaction Verification**:
   - Check switching between "Target & Zones" and "Baseline & HR Settings" tabs.
   - Verify opening and dismissing the `GoalConfigurationSheet` via the "Customize Goal" button.
   - Verify updating reference distance/duration recalculates predicted times and pace ranges dynamically.
