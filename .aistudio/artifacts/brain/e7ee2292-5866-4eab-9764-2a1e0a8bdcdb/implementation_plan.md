# PaceLab — Race Calendar, Periodized Training, Workout Deduplication & Interactive HR Scrubber

PaceLab is expanding to include an interactive **Race Calendar**, an **Adaptive Periodized Training Engine**, a **Workout Deduplication & Deletion Manager**, and an **Interactive Heart Rate Scrubber Graph** with a vertical timeline cursor for inspecting second-by-second BPM telemetry.

---

## User Review & Critical Decisions

> [!IMPORTANT]
> The following user preferences have been synthesized to guide the build:

- **Interactive Heart Rate Scrubber**: An interactive Canvas graph where dragging or tapping displays a **vertical timeline indicator line**, a highlighted sample dot on the graph curve, and an inspection readout displaying the exact timestamp (`MM:SS`), BPM, and active HR zone badge (Z1–Z5).
- **Plan Structure**: Multi-week periodized training program dynamically constructed from the current week up to the chosen race date, structuring Base, Build, Peak, and Taper phases.
- **Taper Strategy**: A **1-week sharp taper** immediately prior to race day, reducing weekly training volume by ~40–50% while preserving race-pace neuromuscular sharpness with shorter strides and shakeout runs.
- **Race Calendar Management & Export**: An interactive in-app race calendar with a prominent countdown widget, race goal cards, date picker, and an option to export scheduled race events directly into the Android device calendar via standard Calendar intents (zero permissions required).
- **Duplicate Workout Management & Deletion**:
  - Automated detection of overlapping sessions (e.g., runs recorded within 10 minutes with matching distances from different sync sources).
  - Visual indicator on duplicate workouts with a 1-tap action to keep the best recording and delete the duplicate.
  - Manual deletion capability on any individual session directly from the Runs list and Session Detail screen to clean up cached records.

---

## 1. Overview & Core Concept

1. **Interactive Heart Rate Scrubber Graph**:
   - The Heart Rate line chart in the Session Detail view becomes fully interactive via `pointerInput`.
   - Sliding a finger across the chart displays a high-visibility vertical cursor line that tracks the touch position.
   - Snaps to the nearest recorded `HeartRatePoint`.
   - Renders a floating readout bubble:
     - Exact timestamp into the run: e.g., `18:45`
     - Exact heart rate: e.g., `158 bpm`
     - Heart rate zone: e.g., `Z3 Threshold (141-160 bpm)`
   - Allows runners to pinpoint cardiac drift, hill climb surges, warmup stabilization, and interval recoveries.

2. **Race Calendar & Adaptive Training**:
   - Dedicated **Target Race** card and management sheet where runners set race dates, titles, distances (5K, 10K, 15K, 21.1K), and custom target finish times.
   - Generates an adaptive multi-week periodized schedule spanning from the current week to race week (supporting blocks from 2 to 16+ weeks).
   - Dynamically calculates phase blocks:
     - **Base Phase**: Aerobic foundation with steady 10% weekly volume progression.
     - **Build Phase**: Threshold tempo and VO2 max intervals tuned to target race pace.
     - **Peak Phase**: Highest volume week 2 weeks before the race with key race-pace rehearsal.
     - **Taper Week**: 1-week sharp volume reduction with light shakeouts to maximize glycogen stores and leg freshness for race day.
     - **Race Day**: Sunday race session with target split goals and paces.
   - 1-tap **"Add to Device Calendar"** action using `Intent(Intent.ACTION_INSERT)` with `CalendarContract.Events` to view race day on Google Calendar or Samsung Calendar without invasive permissions.

3. **Workout Deduplication & Deletion**:
   - Analyzes all sessions to flag potential duplicates (runs whose start times are within 15 minutes of each other and distances within 20%).
   - Displays a "Duplicate Workout" warning banner and chip on conflicting items.
   - Provides a "Clean Up Duplicates" dialog to keep the session with richer telemetry (HR/elevation) and delete the redundant entry from Room.
   - Provides an explicit **Delete Workout** button with confirmation in the session detail sheet so users can remove any unwanted or corrupted workouts.

---

## 2. User Experience & Visual Design

### Key User Flows

1. **Detailed Heart Rate Inspection Flow**:
   - Tap any workout from the **Runs** screen to open the **Run Detail Sheet**.
   - Scroll to the **Heart Rate Over Session** graph.
   - Tap or slide across the graph:
     - A vertical neon-volt guideline highlights the timeline position.
     - A glowing pulse dot marks the exact coordinate on the heart rate path.
     - A top readout bar dynamically reveals: `[ 18:32 ]  156 BPM  ·  Z3 Lactate Threshold`.
   - Releasing the touch smoothly retains or clears the cursor.

2. **Setting Up a Target Race**:
   - In the **Plan** screen or **Targets** screen, users see an interactive **"Target Race Calendar"** card.
   - Tapping opens the **Race Setup Dialog / Bottom Sheet**:
     - Race Name input (e.g., "Jakarta 10K", "Bali Half Marathon").
     - Race Distance selector (5K, 10K, 15K, Half Marathon).
     - Material 3 Date Picker to select the target race date.
     - Target Time input (auto-calculated from Riegel formula with option to customize).
   - Once saved to Room, the countdown updates instantly (e.g., `42 Days to Race Day · 6 Weeks Remaining`).

3. **Adaptive Training Program View**:
   - The Plan screen dynamically switches from the static 4-week loop to a countdown timeline:
     - `Week 1 (Base - 18.0 km)`
     - `...`
     - `Week N-1 (Peak Week - 24.0 km)`
     - `Race Week (1-Week Sharp Taper - 12.0 km + Race Day!)`
   - Each week clearly displays its training phase badge (`Base`, `Build`, `Peak`, `Taper`).
   - Real sessions synced from Health Connect continue to automatically match against scheduled days with completion badges.

4. **Managing & Deleting Duplicate Workouts**:
   - When duplicate sessions are detected, a discreet banner appears at the top of the **Runs** screen: `Duplicate Workouts Detected · Tap to Clean Up`.
   - On individual workout cards, duplicate sessions show an amber `Duplicate` badge.
   - In the Run Detail sheet, a prominent **"Delete Workout"** icon button allows removing the workout from local cache with immediate recalculation of weekly metrics.

---

## 3. Technical Architecture & Data Strategy

```
┌────────────────────────────────────────────────────────┐
│                   UI Layer (Jetpack Compose)           │
│     PlanScreen       TargetsScreen    RunsScreen       │
│  (Countdown Hero)   (Race Goal Card)  (Interactive HR) │
└───────────────────────────┬────────────────────────────┘
                            │ StateFlow / Actions
┌───────────────────────────▼────────────────────────────┐
│                    PaceLabViewModel                    │
│   - setTargetRace(name, date, distance, targetTime)    │
│   - deleteTargetRace()                                 │
│   - deleteSession(sessionId)                           │
│   - autoResolveDuplicates()                            │
│   - periodizedPlanWeeks: List<TrainingPlanWeek>        │
└─────────────┬──────────────────────────┬───────────────┘
              │                          │
┌─────────────▼──────────────┐ ┌─────────▼───────────────┐
│     PaceLabDatabase (Room) │ │     PeriodizedPlanner   │
│     - RaceEventEntity      │ │  - Phase partition math │
│     - CachedSessionEntity  │ │  - 10% progression rule │
│     - PlanStateEntity      │ │  - 1-week sharp taper   │
│     - UserSettingsEntity   │ │  - Duplicate detector   │
└────────────────────────────┘ └─────────────────────────┘
```

### Canvas Scrubber Mathematics

- On pointer input drag / tap with coordinate $X \in [0, \text{Width}]$:
  $$\text{TargetOffsetSec} = \frac{X}{\text{Width}} \times \text{TotalDurationSec}$$
- Binary search / nearest lookup in `List<HeartRatePoint>`:
  $$p^* = \arg\min_p |p.\text{offsetSeconds} - \text{TargetOffsetSec}|$$
- Canvas drawing:
  1. `drawLine(color = VoltPrimary.copy(alpha = 0.8f), start = (x, 0), end = (x, height), strokeWidth = 1.5.dp)`
  2. `drawCircle(color = VoltPrimary, center = (x, y), radius = 6.dp)`
  3. `drawCircle(color = Color.White, center = (x, y), radius = 2.5.dp)`

---

## 4. Implementation Steps

1. **Interactive HR Canvas Scrubber**:
   - Update `HeartRateChart` in `CanvasCharts.kt` to accept touch pointer events (`Modifier.pointerInput`).
   - Draw the vertical guideline, highlight pulse circle, and live inspection pill showing timestamp, BPM, and HR zone.
2. **Data Layer**:
   - Add `RaceEventEntity` to `PaceLabDatabase.kt` and DAO methods (`getTargetRaceFlow`, `saveTargetRace`, `deleteTargetRace`).
   - Add `deleteSession(sessionId: String)` and `deleteSessions(ids: List<String>)` to `PaceLabDao`.
   - Update domain models in `RunModels.kt` to include `RaceEvent` and duplicate flagging.
3. **Domain Logic (`PeriodizedPlanner.kt` & Deduplication)**:
   - Calculate periodized weeks from today until target race date with 1-week sharp taper.
   - Implement duplicate workout detection algorithm.
   - Add unit tests for periodization phases, 1-week taper volume, and duplicate detection.
4. **UI Components & Dialogs**:
   - `RaceCountdownCard`: Hero card showing race name, countdown days, target pace, and export action.
   - `RaceSetupDialog`: Material 3 DatePicker and distance/time inputs.
   - Update `RunsScreen`: Duplicate alert banner with 1-tap "Resolve All Duplicates", and trash/delete action on cards.
   - Update `RunDetailBottomSheet`: Add explicit "Delete Workout" button with confirmation alert.
   - Update `PlanScreen`: Dynamic periodized timeline displaying Base, Build, Peak, and 1-Week Taper phases leading up to Race Day.
5. **Verification**:
   - Run unit tests (`gradle :app:testDebugUnitTest`) and verify clean build (`compile_applet`).
