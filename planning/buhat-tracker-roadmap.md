# Buhat Roadmap

## 1. Status, goal, and success criteria

**Status:** Phases 0-10 are complete and shipped as the current baseline (`versionCode 3`, `versionName 1.0.1`). Phases 11-14 below are planned and ready for implementation. They add per-exercise weight-unit and working-rest preferences, active-workout exercise reordering, and the Room migration and backup compatibility those require. No new feature code exists yet. Baseline recorded on 2026-11-08: 101 unit tests and 181 device tests, 0 failed. Rerun both suites before and after each phase.

**Goal:** A private, offline Android strength log for you and your friends. Each install owns its data. You train from premade or custom templates, log every set with weight, reps, RIR, and type, get per-set double-progression targets, run a rest timer, and review e1RM progress. Any exercise can show and accept its own weight unit and its own working rest, and exercises can be reordered during an active workout. Data moves by JSON export/import. Delivery is a signed APK.

**Users:** You and a small group of friends. No accounts, no shared server, no cross-device sync.

**Design source:** `docs/design/home-profile.png`, `workouts.png`, `active-workout.png`, `progress.png`, `settings.png`. These are the visual reference for every screen.

**Success criteria:**

1. A fresh install shows 6 premade templates and a seeded exercise library, fully usable in airplane mode.
2. A custom exercise and custom template can be created, edited, reordered, and used in a session.
3. A session logs warmup, working, and failure sets with weight, reps, and RIR. Each working-set position gets its own next target from its own history. The active session, logged sets, and rest timer survive app process death.
4. Rest timer auto-starts after a set, continues with the screen off or app backgrounded, and alerts at zero within ±2 seconds on the test phone. Skip and finish cancel it.
5. Finishing a workout writes history. Home and Progress show hand-verifiable e1RM, volume, working-set, session, and PR numbers. Warmups are excluded.
6. Export to JSON, clear app data, import, and get identical profile, settings, exercises, templates, sessions, and sets. Invalid or newer files leave existing data unchanged.
7. A signed release APK installs and runs on the test phone. The manifest has no INTERNET permission.
8. All five designs are implemented in dark and light themes at phone size, with readable contrast and 48dp touch targets.
9. Any exercise, premade or custom, can override the global kg/lb setting. Bench Press in lb and Leg Extension in kg display and accept the right unit in Active Workout, target messages, logged sets, past workouts, and that exercise's Progress chart. Stored weights stay kilograms, and aggregate volume and body weight stay in the global unit.
10. Any exercise can override its working rest. The override applies after a working set; warmup and failure rests keep the global defaults. Changing it mid-workout affects the next set, not a running timer.
11. Exercises can be reordered inside an active workout. The new order survives process death, appears in the finished workout, and never changes the template.

## 2. Scope

**Included**

- Native Android app, offline only, one local database per install.
- Profile: name, gender, age, weight, height. Editable, optional fields.
- Exercise library: seeded common exercises plus custom exercises with name, muscle group, and equipment.
- Templates: 6 read-only premade templates, custom create/edit/duplicate/delete, exercise order, working-set count, rep range, optional target RIR.
- Active workout: start from template, exercise order, per-set logging (weight, reps, RIR, type), set edit/delete, add set, finish/discard, resume after process death, elapsed session time.
- Active-workout exercise reordering through an "Edit order" dialog. Session only; the template is unchanged.
- Per-exercise weight unit for premade and custom exercises. Nullable override with the global kg/lb setting as fallback. Display and input only; storage stays kilograms.
- Per-exercise working-rest override. Nullable seconds value with the global working rest as fallback. Warmup and failure rests stay global.
- Per-set double progression: each working-set position has its own weight and rep target derived from the same position in the previous source session.
- Rest timer: per-type defaults, auto-start after a set, countdown, skip, background notification, sound/vibration, restore after process death.
- Home: profile card, 3-month e1RM trend, 4-week analytics (workouts, working sets, volume, new PRs).
- Progress: exercise picker, 1M/3M/1Y/All e1RM chart, best e1RM, heaviest set, session count, past workouts, PR badges, expandable sets, delete workout.
- Settings: dark/light theme, kg/lb, previous-set source, rest defaults and increment, export/import.
- JSON export/import through Android's file picker, full replace on import with confirmation and validation.
- Signed APK for direct install, no Play Store.

**Excluded**

- Accounts, login, cloud sync, shared data between devices, social features.
- Play Store listing, iOS, web, Wear OS, widgets.
- Health Connect, Google Fit, or other health-platform integration.
- Body-weight history, nutrition, cardio, 1RM test workflows.
- Exercise images, instructions, muscle diagrams, video.
- Percentage-based programs, auto-deloads, periodization, RPE-based progression.
- Editing a finished workout (deleting one is allowed).
- Per-exercise warmup or failure rest overrides. Only working rest is configurable per exercise.
- A per-exercise weight increment setting. kg exercises step 2.5 kg; lb exercises step 5 lb.
- Reordering a finished workout, or changing a template from the active session.
- Exercise-specific units for aggregate volume, body weight, height, or other whole-app numbers.
- Snapshotting unit or rest into historical sessions. History renders with the exercise's current preference and falls back to the global setting when the exercise no longer exists.
- Localization beyond English.
- App lock, encryption at rest, cloud auto-backup.
- Automatic folder export.
- Encrypted backup files.
- Network crash reporting or analytics.

## 3. Decisions and rationale

| Area | Decision | Basis | Reason |
|---|---|---|---|
| Platform | Kotlin, Jetpack Compose, Material 3, Room, DataStore | User-confirmed | Android-only, local data, reliable notifications, no JS runtime |
| Data ownership | Local-only, no accounts. Profile stored on device | User-confirmed | Private personal tracker. No server cost or sync conflicts |
| Data movement | Versioned JSON export/import via Storage Access Framework | User-confirmed | Works offline, no permissions, user controls the file |
| Import failure | Room replace, then DataStore write | User-confirmed | A settings write failure is reported and retryable. Temporary mismatch is accepted |
| Scope | All five designs in v1 | User-confirmed | Home, Workouts, Active, Progress, Settings all ship |
| Progression | Per-set double progression | User-confirmed | Each set position advances independently. Focus on beating that set |
| Distribution | Signed APK shared directly | User-confirmed | Small friend group. No store review or account |
| Test target | Gradle-managed Pixel 6 / API 35 emulator for automated E2E; physical phone for hardware acceptance | User-confirmed | Reproducible clean device for the suite; real timer, vibration, and lock-screen checks stay on the phone |
| Template targets | Working-set count + rep range + optional target RIR | User-confirmed | Small editable template, supports double progression |
| Previous-set source | Any workout (default) or Same template | Design + assumption | Drives which session supplies per-set history and prefill |
| Weight model | Store kilograms, display kg or lb | Design + assumption | One canonical value, no drift. Step follows the effective unit (see below) |
| Rest defaults | Warmup 1:00, Working 2:00, Failure 3:00. Increment 15s | Design + assumption | Matches Settings design. Increment value was cut off |
| Per-exercise unit | Nullable `weightUnitOverride` on `Exercise`; effective unit = override else global. Display and input only | User-confirmed | Bench Press lb, Leg Extension kg. Must work for premade rows. Aggregate analytics stay global |
| Per-exercise rest | Nullable `restWorkingSecondsOverride` on `Exercise`; applies to working sets only | User-confirmed | One rest value per exercise. Warmup and failure keep global defaults |
| Exercise settings entry | One shared dialog from Exercise Library and Active Workout | User-confirmed | Set preferences before or during training |
| Weight step by unit | kg: 2.5 kg; lb: 5 lb, stored as the exact kg conversion | User-confirmed | Natural plate jumps without adding an increment setting |
| Active-workout reorder | "Edit order" dialog with up/down buttons; session only | User-confirmed | Mirrors the template editor. No drag-and-drop. Template untouched |
| Preference compatibility | Room schema 3 migration; backup format 2; import accepts v1 and v2 | User-confirmed | Existing installs and old backups must keep working |
| Premade editability | Premade rows allow only unit and rest overrides; name, muscle group, equipment stay read-only | Derived from user-confirmed entry point | Preserves the read-only content rule |
| Analytics | Epley e1RM. Warmups excluded. Volume = weight × reps for working/failure | Assumption | Standard, simple, matches design intent |
| Progress ranges | 1M = 30 days, 3M = 90 days, 1Y = 365 days, All = no cutoff | User-confirmed | Windows include today |
| App identity | App name "Buhat", package `com.buhat.tracker` | User-confirmed | Launcher label only. Package stays `com.buhat.tracker` |
| Android range | minSdk 26, compile/target 35 | Assumption | Covers Android 8+ friends. SDK 35 is installed |
| Bodyweight moves | Log external weight only. e1RM shown only when weight > 0 | Assumption | Avoids body-weight history in v1. Reps still tracked |
| Android auto-backup | Disabled. Explicit export is the backup path | Assumption | Keeps profile data off cloud services |
| Build versions | Gradle 8.11.1, AGP 8.7.3, Kotlin 2.1.0, Compose BOM 2025.01.00, Room 2.7.1 | Assumption | Known-good baseline. Verify and adjust at scaffold time |

## 4. Implementation plan

### 4.1 User flows

**First launch**

1. Database seeds 6 premade templates and the exercise library.
2. Home shows an empty profile card, an empty strength card, and empty 4-week stats.
3. Workouts is immediately usable. No onboarding gate.

**Create content**

1. Workouts > Library. Search or create a custom exercise (name, muscle group, equipment).
2. Workouts > New. Name the template, add exercises from the library, set working-set count, rep range, optional target RIR, reorder, save.
3. Premade templates are read-only. "Duplicate" creates an editable custom copy.

**Run a workout**

1. Workouts > Start workout. The session copies the template structure into `SessionExercise` rows, so later template edits do not change the session.
2. Active screen: template name, session elapsed pill, exercise chips, logged sets for the selected exercise, next-set target card, logging card, rest timer.
3. Logging card: type (Warmup/Working/Failure), weight, reps, RIR. Fields prefill from the same working-set position in the previous source session, else the last set in this session, else blank. A "Use target" action fills the recommended values.
4. Log set. It is persisted immediately. The rest timer starts from the type's default. The next target updates.
5. Warmups are ad hoc: they never count as template working sets, never feed progression, and never enter analytics.
6. Finish saves the session. Discard deletes it after confirmation. Only one active session exists.

**Configure an exercise**

1. Workouts > Exercise library > row menu > Exercise settings. The action exists for premade and custom rows.
2. Unit offers Default (shows the current global unit), kg, and lb. Working rest offers Default (shows the global working rest) and a value from 0 to 600 seconds in 15-second steps.
3. Save. Preferences persist immediately and apply to the next set, the target card, and the rest timer. Logged sets keep their stored kilograms.
4. The same dialog opens from the Active Workout target card for the selected exercise.

**Reorder during a workout**

1. Active workout overflow > Edit exercise order.
2. The dialog lists the session's exercises with up/down buttons. Each move saves; the dialog stays open.
3. The template is not changed. Logged sets stay attached to their exercise. The selected exercise and any running rest are unaffected.
4. Finishing stores the final order; past workouts show it.

**Review progress**

1. Home shows the profile, a 3-month e1RM trend for a selected exercise, and 4-week analytics with deltas against the prior 4 weeks.
2. Progress lets the user pick any exercise, switch 1M/3M/1Y/All, see the e1RM chart and all-time stats, and open past workouts with PR badges and expandable sets.
3. A finished workout can be deleted with confirmation; derived analytics recalculate.

**Back up and restore**

1. Settings > Export data. Pick a location. JSON contains profile, settings, exercises, templates, sessions, and sets; it excludes an active session.
2. Settings > Import data. Pick a file. The app validates everything, shows counts and export date, and requires "Replace all local data".
3. Validation runs before any write. Room data replaces in one transaction; profile and settings are written after it. A validation failure leaves existing data untouched. A write failure is reported and the same file can be retried.
4. Export and import are blocked while a workout is active.

### 4.2 Data model

All IDs are UUID strings. Timestamps are epoch milliseconds UTC. Weights are kilograms.

| Entity | Fields | Notes |
|---|---|---|
| `Exercise` | `id`, `name`, `muscleGroup`, `equipment`, `isCustom`, `archivedAt`, `createdAt`, `weightUnitOverride`, `restWorkingSecondsOverride` | Name unique case-insensitive. Seeded and custom share one table. Overrides are nullable; null means use the global setting. Premade rows may change only the two override fields. |
| `Template` | `id`, `name`, `isPremade`, `createdAt`, `updatedAt`, `archivedAt` | Premade rows are read-only. Custom rows editable. |
| `TemplateExercise` | `id`, `templateId`, `exerciseId`, `position`, `workingSets`, `repMin`, `repMax`, `targetRir` | Unique positions and unique exercise per template. Cascade on template delete. |
| `WorkoutSession` | `id`, `templateIdSnapshot`, `templateNameSnapshot`, `startedAt`, `finishedAt`, `status` | `status`: ACTIVE, FINISHED. Template ID is a string snapshot, not an FK. |
| `SessionExercise` | `id`, `sessionId`, `exerciseId`, `exerciseNameSnapshot`, `position`, `workingSets`, `repMin`, `repMax`, `targetRir` | Snapshot of the template at start. Cascade on session delete. |
| `LoggedSet` | `id`, `sessionExerciseId`, `setType`, `workingIndex`, `weightKg`, `reps`, `rir`, `completedAt` | `workingIndex` is 1-based among working/failure sets; null for warmups. |

`weightUnitOverride` stores `"KG"`, `"LB"`, or null. `restWorkingSecondsOverride` stores 0..600 or null. Null means "use global". These are preferences, not content: premade rows may change them while every other premade field stays read-only. They are not snapshotted into `SessionExercise`. Logging, targets, history, and charts resolve the exercise's current preference and fall back to the global setting when the exercise row no longer exists.

Room schema version becomes 3. Migration 2->3 adds the two nullable columns. Backup format version becomes 2 and adds the same two nullable fields to every `BackupExercise`. Import accepts format 1 by defaulting both fields to null, and format 2 as written.

`Profile` and `Settings` live in DataStore: name, gender, age, weightKg, heightCm; theme, unit, previous-set source, rest seconds per type, rest increment, rest timer end state.

Indices and uniqueness: `LoggedSet(sessionExerciseId)`, unique `LoggedSet(sessionExerciseId, workingIndex)` where `workingIndex` is not null, unique `SessionExercise(sessionId, position)`, unique `TemplateExercise(templateId, position)` and `(templateId, exerciseId)`, `WorkoutSession(status)`, `WorkoutSession(finishedAt)`. Foreign keys are enforced. Room schema JSON is committed to the repo for future migrations.

### 4.3 Progression, analytics, and units

**Working-set position**

- Warmups are ignored for progression and analytics.
- Working and failure sets are numbered 1..M in completion order for each exercise in a session.
- Each position P has its own history. Position 1 never borrows from position 2.

**Source session**

- "Any workout": the most recent FINISHED session containing the exercise.
- "Same template": the most recent FINISHED session whose `templateIdSnapshot` matches the active session's template snapshot. Falls back to Any workout if none.
- Within the source session, use the working/failure set at position P. If P does not exist, use the last working/failure set in that session. If none exists, this is a first-time exercise.

**Target for the next set at position P**

| Source state | Suggested weight | Suggested reps | Card text |
|---|---|---|---|
| No history | blank | `repMin` | "First time. Set your working weight." |
| Last reps < `repMax` | same weight | `min(lastReps + 1, repMax)` | "Beat N reps at X kg." or "Beat N reps at X lb." |
| Last reps >= `repMax` | weight + one step (2.5 kg or 5 lb) | `repMin` | "Top of range reached. Add 2.5 kg, restart at repMin." (or "Add 5 lb" in lb) |
| Weight is 0 (bodyweight) | 0 | `min(lastReps + 1, repMax)` | Reps target only; no e1RM point |

The bodyweight row overrides the add-weight row: weight 0 never increases.

- The logging fields prefill the raw source values (weight, reps, RIR), as requested. The target card is advisory; "Use target" applies the suggested weight and reps.
- RIR prefill: source RIR, else 2. Target RIR from the template is displayed but never enforced.
- If all M positions are logged, show "All prescribed sets logged" and allow Add set. The added position is M+1 and gets its own history.
- This per-position rule changes the Active design's exercise-level "beat your lowest set" copy into a target for the current position. The layout stays the same; the comparison is like-for-like by set number. The target-versus-prefill difference is confirmed for v1.

**Estimated 1RM**

- Epley: `weightKg × (1 + reps / 30)`. Only working/failure sets with `weightKg > 0` and `reps > 0`.
- Chart point per finished session: maximum e1RM for the selected exercise in that session.
- Latest value = last point. Change = latest minus first point in the selected range, plus percent.
- Progress ranges: 1M = 30 days, 3M = 90 days, 1Y = 365 days, All = no cutoff. Windows include today.
- Heaviest set = highest `weightKg`, tie-break by reps.

**PR rule**

- Walk finished sessions chronologically per exercise. A working/failure set is a PR when its e1RM is strictly greater than every earlier e1RM for that exercise. Ties are not PRs.
- A session gets a PR badge if it contains at least one PR set for the selected exercise.
- "New PRs" in the 4-week card = count of PR sets in the window. This is an assumption; change to distinct exercises if the number feels inflated.

**4-week analytics**

- Current window: last 28 days including today. Prior window: the 28 days before that.
- Workouts: distinct FINISHED sessions with at least one working/failure set.
- Working sets: count of working/failure sets. Warmups excluded.
- Volume: sum of `weightKg × reps` over working/failure sets. Warmups excluded.
- Show percent deltas where meaningful; otherwise "vs prior 4".

**Units**

- Store kilograms. Every exercise has an effective unit: its `weightUnitOverride`, else the global Settings unit.
- Display pounds as `kg × 2.20462`, rounded to 0.1 lb. Display kilograms with trailing zeros stripped.
- Weight steps follow the effective unit: 2.5 kg for kg, 5 lb for lb. The lb step is stored as `lbToKg(5.0)` (2.26796... kg) so repeated display conversions stay at whole pounds. The top-of-range bump uses the same step.
- Use the effective unit for every exercise-specific value: Active Workout target, prefill, logged rows, weight input and steps, set editor, past workouts, the exercise's Progress chart and stats, and the Home strength-trend card for that exercise.
- Keep the global unit for whole-app values: body weight, height, 4-week volume, Settings copy, and any aggregate.
- When an exercise row is missing (deleted custom exercise), fall back to the global unit for its history.
- Height stays in centimeters in both unit modes. Age is an integer.
- RIR is an integer 0..5. Set types are Warmup, Working, Failure.

**Per-exercise rest**

- Resolve rest by set type. Warmup uses `restWarmupSeconds`. Failure uses `restFailureSeconds`. Working uses `restWorkingSecondsOverride` when set, else `restWorkingSeconds`.
- An override of 0 means no rest for that exercise's working sets.
- Validate overrides at 0..600 seconds. The editor uses the existing rest increment (default 15 s).
- Changing an override while a rest is running does not change the current `restEndsAt`. The next logged set uses the new value.

### 4.4 Technical approach

**Current code map (read first)**

- `app/src/main/java/com/buhat/tracker/data/local/Entities.kt`: Room entities. `Exercise` gains the two nullable override columns. `SessionExercise.position` is the active-session order.
- `BuhatDao.kt`: queries. `observeSessionExercises` is ordered by position. Add a session-exercises suspend read and position updates for reordering, plus an all-exercises flow that includes archived rows for preference lookup.
- `BuhatDatabase.kt`: currently version 2 with `MIGRATION_1_2`. Add version 3 and `MIGRATION_2_3`.
- `data/BuhatRepository.kt`: `startSession`, `logSet` (computes rest from type), `updateSet`, `deleteSet`, finish/discard, export/import, and `sessionWriteMutex`. Per-exercise rest resolution belongs in `logSet`.
- `data/SettingsStore.kt`: global `unit`, rest defaults, `restEndsAt`, and rest timer persistence. No new keys are needed.
- `data/Backup.kt`: DTOs, `BACKUP_FORMAT_VERSION = 1`, strict decode and validation. Extend exercises and accept v1.
- `domain/Units.kt`: kg/lb conversion and formatting. Add the effective-unit helper here.
- `domain/Progression.kt`: `nextTarget` and `WEIGHT_INCREMENT_KG`. Make the increment unit-aware.
- `domain/RestTimer.kt`: `restSecondsFor`. Add the exercise working-rest override.
- `domain/SessionRules.kt`: prefill, working-index renumbering, set validation.
- `ui/screens/ActiveWorkoutScreen.kt`: selected exercise, target card, rest card, logged rows, logging card, overflow menu.
- `ui/screens/ExerciseLibraryScreen.kt`: exercise rows and menus. `ExerciseEditorScreen.kt`: custom-only editor.
- `ui/screens/ProgressScreen.kt` and `HomeScreen.kt`: per-exercise e1RM/stat display and global volume.
- `app/schemas/com.buhat.tracker.data.local.BuhatDatabase/`: committed Room schemas 1 and 2. Commit 3 after the migration.
- Tests: `app/src/test/java/com/buhat/tracker/` (unit), `app/src/androidTest/java/com/buhat/tracker/` (device, including `MigrationTest.kt`).
- Commands: `.\gradlew.bat testDebugUnitTest --console=plain` and `.\gradlew.bat pixel6api35DebugAndroidTest --rerun`. Follow `.opencode/skills/e2e-testing/SKILL.md` for the full suite. Release build: `.\gradlew.bat assembleRelease`; signing requires an external `keystore.properties`.
- No new dependency is needed. Use the existing Compose dialog, segmented control, text button, and stepper components.

**Stack and structure**

- Single `app` module, single activity, Compose + Navigation Compose.
- `data/local` (Room entities, DAOs, database, seed), `data/repo`, `domain` (pure progression, e1RM, PR, analytics), `ui` (theme, components, screens), `timer`.
- Repository + ViewModel + Flow. Manual dependency container in `BuhatApplication`; no Hilt, no networking library.
- Kotlin serialization for backup DTOs. KSP for Room.
- The app exists and Phases 0-10 are shipped. Reuse the existing repository, Compose state patterns, dialogs, segmented control, steppers, and test conventions. Do not add a chart library: the line chart is a small Compose Canvas.

**UI system**

- Dark palette from the designs: near-black background, dark gray cards, white text, lime working accent, amber warmup, orange primary actions and PR badges. Light palette derived with the same accents and checked contrast.
- Bundle Inter (OFL) at 400/600/800 with tabular figures. Fall back to system sans if the asset cannot be fetched.
- Shared components: rounded card, pill chip, segmented control, large number stepper, set-type badge, PR badge, line chart, empty state.
- Edge-to-edge; status bar icons follow theme. Bottom tab bar is a custom four-pill row, not Material NavigationBar.
- Touch targets at least 48dp. Set type is always shown as text, not color alone. Charts have text summaries.

**Screens**

- Home: profile card, 3-month trend with exercise chips, 4-week stat grid. Settings button in the profile card.
- Workouts: count, All/Premade/My templates filters, template cards, Start. Overflow opens Exercise library.
- Active: header, elapsed pill, exercise chips, target card, rest card, logged sets, logging card. Overflow: Finish workout, Discard workout, Edit exercise order. Target card: Exercise settings.
- Progress: exercise picker, range segmented control, e1RM card, three stat tiles, past workouts with expand and delete.
- Settings: appearance, global units, previous set, rest timer defaults and increment, data export/import, profile link. Per-exercise unit and working-rest overrides live in Exercise settings.
- Full-screen editors: template, exercise, profile. Back arrow, no bottom nav.

**Active session persistence**

- Logged sets are written on tap, not on finish.
- The active session is a normal Room row. On launch, the Active tab shows it.
- Starting another session prompts Resume or Discard.
- Session elapsed time derives from `startedAt`, so it survives backgrounding.

**Per-exercise preferences**

- Add the two nullable columns to `Exercise`. Add these helpers in `domain/Units.kt` and `domain/RestTimer.kt`:
  - `Exercise.effectiveUnit(global: WeightUnit): WeightUnit = weightUnitOverride?.let(WeightUnit::valueOf) ?: global`
  - `Exercise.effectiveWorkingRest(globalSeconds: Int): Int = restWorkingSecondsOverride ?: globalSeconds`
- `saveExercisePreferences(exerciseId, unitOverride, restWorkingSecondsOverride)` updates only the two columns. It validates rest at 0..600 and works for premade and custom rows. It never touches name, `nameLower`, muscle group, equipment, or the custom flag.
- Active Workout observes the selected exercise row by ID and derives `unit`. All existing `formatWeight`, `formatWeightValue`, `displayToKg`, step, target, and set-editor calls use that value. If the row is gone, use the global unit.
- `logSet` reads the session exercise's override in the same write path, then calls the extended rest resolver. Do not pass a rest value from the UI.
- Progress and Home add a small all-exercises flow that includes archived rows to resolve effective units; the existing `repository.exercises()` flow filters archived rows. Keep volume, profile, and height on `settings.unit`.
- The shared dialog is one small composable, opened from both entry points. It offers Unit: Default (current global), kg, lb. Working rest: Default (current global), or 0..600 with +/− using `restIncrementSeconds`, plus "Use default" to clear. Save calls the repository. Suggested tags: `exercise-settings`, `pref-unit-default|kg|lb`, `pref-rest-value|plus|minus|default`, `save-exercise-prefs`.
- Exercise Library rows gain a menu for every row. Custom rows keep Edit, Archive, and Delete, plus Exercise settings. Premade rows get only Exercise settings.
- The Active target card gets a 48dp "Settings" text button for the selected exercise.

**Active session reorder**

- Add DAO methods:
  - `SELECT * FROM SessionExercise WHERE sessionId = :sessionId ORDER BY position` (suspend).
  - `UPDATE SessionExercise SET position = -position WHERE sessionId = :sessionId`.
  - `UPDATE SessionExercise SET position = :position WHERE id = :id`.
- Repository `reorderSessionExercises(sessionId, orderedIds)` runs inside `sessionWriteMutex` and a Room transaction:
  1. Load the session; require `status = ACTIVE`.
  2. Require `orderedIds` to contain exactly the session's exercise IDs, with no duplicates.
  3. Move all positions to their negative values, then write 1..N in the given order. Negatives are unique, so the unique `(sessionId, position)` index never collides mid-update.
- The dialog calls the repository on each move with the swapped ID list and disables the arrows while a write is in flight. It reads the same `sessionExercises` Flow, so the list updates live. Up/down buttons are disabled at the ends. Do not touch `TemplateExercise`.
- Reordering does not move logged sets because sets reference `sessionExerciseId`. Keep the selected exercise by ID. A running rest is untouched.

**Rest timer**

- On set log, compute `restEndsAt = now + resolved rest for the set's type and exercise` and persist it. If the duration is 0, do not start.
- In-app countdown ticks from `restEndsAt` using elapsed-realtime math, so it does not drift.
- Background alert: post an ongoing notification with a native countdown chronometer and schedule an exact `AlarmManager.setAlarmClock` broadcast at `restEndsAt`. The broadcast posts a completion notification with sound and vibration, cancels the ongoing notification, and clears timer state.
- Use `setAlarmClock` for the exact background alert. Android 13+ uses `USE_EXACT_ALARM` for this direct-APK app. Android 12/12L needs `SCHEDULE_EXACT_ALARM`; the app opens the system access screen, while the in-app countdown remains available if access is denied. The alarm icon appears during rest. If device testing shows unreliable delivery or the icon is unacceptable, replace with a foreground service (`specialUse` on Android 14+) behind the same timer interface. This is the Phase 4 decision gate.
- Skip, finish, discard, and starting a new rest cancel the alarm and notification.
- Notification permission is requested when the first rest starts. If denied, the in-app timer still works and a one-time hint explains that background alerts need permission.
- On app restart, read `restEndsAt`; if future, show the countdown, otherwise clear it. A reboot loses the alarm; opening the app clears stale state. Acceptable for v1.

**Export/import**

- Format version 2. Import accepts version 1 and version 2; version 1 exercises get null overrides. Root: `formatVersion`, `appVersion`, `exportedAt`, `profile`, `settings`, `exercises`, `templates`, `templateExercises`, `sessions`, `sessionExercises`, `sets`. Each exercise carries `weightUnitOverride` and `restWorkingSecondsOverride`.
- SAF contracts: CreateDocument and OpenDocument, MIME `application/json`, suggested name `buhat-backup-YYYY-MM-DD.json`.
- Validate before touching the database: version, required fields, enum values, numeric ranges, unique IDs, referential integrity, timestamps.
- Read the whole file with a size cap (20 MB). Reject with a specific message.
- Import validates fully, replaces all Room data in one transaction, then writes profile/settings to DataStore and clears rest timer state. Room rolls back on failure. A DataStore failure after the Room commit is reported and retryable. Accepted: imported training data may remain with the previous profile/settings until the retry.
- File contents are plain text and include profile data. The UI warns that the file is not encrypted.

**Privacy and release**

- No INTERNET permission. No analytics, ads, or crash-reporting SDK.
- `android:allowBackup="false"` so profile and training data do not silently leave the device.
- Release signing key and `keystore.properties` live outside the repo and are gitignored. Losing the key means friends must uninstall and lose local data before installing an update.
- Disable Google backup; explicit JSON export is the only backup path.

### 4.5 Edge cases

- **Template changes during a session:** session snapshots exercises and targets at start. Editing or deleting the template does not change the active session or history.
- **Exercise rename/archive:** history uses `exerciseNameSnapshot`. Archiving hides an exercise from pickers but keeps history and templates working. Hard delete only when unreferenced; otherwise archive.
- **Deleted template:** sessions keep `templateIdSnapshot` and name; "Same template" history still works for sessions that used it.
- **Set edit/retype:** editing weight, reps, or RIR recalculates nothing stored. Changing type renumbers working indices chronologically for that exercise. Only the current session is editable.
- **Delete set:** deleting the last set recalculates the next target. Deleting a middle working/failure set renumbers later working indices so positions stay contiguous. Finished workouts cannot be edited.
- **Process death:** active session, logged sets, selected exercise, and rest end time restore. An unlogged input row is lost.
- **Clock changes/reboot:** timer may drift or disappear; opening the app reconciles. Documented limitation.
- **Bodyweight sets:** weight 0 is valid. They count for sets and reps but produce no e1RM point.
- **Zero working sets:** finishing prompts to discard. A session with only warmups does not appear in analytics.
- **Import during active session:** blocked with "Finish or discard the workout first."
- **Invalid import:** validation writes nothing; the Room transaction rolls back on failure. A post-commit settings write failure is reported and retryable, and can leave imported training data with the previous profile/settings until retry. Newer format rejected.
- **Duplicate names:** exercise and template names unique case-insensitive. Blank names rejected.
- **Validation:** sets 1..10, repMin 1..30, repMax >= repMin, RIR 0..5, weight 0..1000 kg, reps 0..100, rest 0..600s. Template must have at least one exercise.
- **Unit change mid-session:** stored kg is untouched; all fields re-render in the new unit. Prefill uses converted values and rounds only for display. Changing an exercise override resets the unlogged form to that unit's prefill; logged sets stay kilograms.
- **Exercise preference change mid-session:** applies to the next set, target card, and rest timer. Previously logged sets and a running `restEndsAt` are unchanged.
- **Exercise deleted or archived:** archiving keeps the preferences. Hard delete removes them; history and charts for that exercise fall back to the global unit.
- **Rest override 0:** no rest starts for that exercise's working sets; warmup and failure keep their global defaults.
- **Reorder with logged sets:** sets stay attached by `sessionExerciseId`; only display order changes.
- **Reorder with one exercise:** arrows are disabled and no write occurs.
- **Reorder during rest or process death:** the timer is unaffected; positions are Room rows and restore on launch.
- **Old backup import:** format 1 exercises import with null overrides, so they follow the global unit and working rest.
- **New backup on an old app:** older builds reject format 2 as newer. Export before installing the update.
- **Mixed units in one list:** a finished workout can show Bench Press in lb and Leg Extension in kg. Each row resolves its own exercise's unit.
- **Notification permission denied:** timer remains usable in-app; no background alert until permission is granted.
- **Storage revoked during export/import:** operation fails cleanly; database untouched.

### 4.6 Validation approach

**Automated**

- Unit tests for progression: no history, below range, mid-range, top of range, per-position independence, fallback when a position is missing, Any workout vs Same template, bodyweight.
- Unit tests for Epley e1RM, PR chronology (strictly greater), 4-week, 30-day, 90-day, and 365-day window boundaries, volume excluding warmups.
- Unit tests for kg/lb conversion round trips and display formatting.
- Import tests: valid round trip, malformed JSON, missing references, duplicate IDs, newer version, transaction rollback.
- Unit tests for effective unit: override wins, null follows global, missing row follows global, kg and lb formatting.
- Unit tests for unit-aware progression: 2.5 kg bump, 5 lb bump, message text, bodyweight still never adds weight.
- Unit tests for rest resolution: working override, null fallback, warmup and failure always global, 0 override.
- Backup tests: format 1 import defaults both overrides to null; format 2 round trip; invalid unit string and rest outside 0..600 rejected.
- Room migration tests: 2->3 keeps rows and sets null defaults; 1->3 chain works; commit schema 3 JSON.
- Room in-memory tests for DAO constraints, cascade deletes, working-index uniqueness, active-session queries.
- Device tests: shared dialog from Library and Active, premade row allowed and name unchanged, lb logging and target, rest override starts the right countdown, reorder persists across process death and appears in history, template unchanged.
- `.\gradlew.bat testDebugUnitTest --console=plain` and `.\gradlew.bat assembleRelease` must pass before release.

**Manual on the test phone**

1. Fresh install, airplane mode, seeded content visible.
2. Create custom exercise and template; edit, reorder, duplicate, delete.
3. Start workout; log warmup, working, failure; verify each set position's target and prefill.
4. Kill the app from recents mid-session; reopen; session and logged sets restore.
5. Start a 1:00 rest; background and lock the screen; confirm notification countdown and completion sound/vibration within ±2s; repeat with Skip.
6. Finish; verify history, charts, volume, working sets, sessions, and PR badges against a hand calculation.
7. Change theme and units; verify every screen and conversion.
8. Export, clear app data, import, compare counts and values. Try a corrupted file and a newer-version file.
9. Install the signed release over the previous release with a higher versionCode; confirm data survives.
10. Set Bench Press to lb and Leg Extension to kg. Log both. Verify input, target, logged rows, past workouts, and each exercise's Progress chart use the right unit while volume stays global.
11. Give one exercise a working-rest override. Log a warmup, a working set, and a failure set. Verify only the working rest uses the override.
12. Reorder exercises mid-session. Finish. Verify the finished workout order. Reopen the template and confirm its order is unchanged.
13. Install over the current v1.0.1 release. Confirm exercises, templates, sessions, and preferences survive. Import a format-1 backup and confirm overrides default to global.

**Visual**

- Compare each of the five designs at the test phone's resolution in dark and light themes.
- Check 1.3× font scaling, long exercise/template names, empty states, and one-handed logging.

### 4.7 Seed content

**Premade templates**

| Template | Exercises (sets × rep range) | Total sets |
|---|---|---|
| Push Day A | Bench Press 3×6-8, Incline Dumbbell Press 3×8-10, Cable Fly 3×10-12 | 9 |
| Pull Day A | Barbell Row 3×6-8, Lat Pulldown 3×8-10, Seated Cable Row 3×8-10, Face Pull 3×12-15, Barbell Curl 4×8-12 | 16 |
| Leg Day | Back Squat 3×5-8, Romanian Deadlift 3×6-8, Leg Press 4×10-12, Leg Curl 3×10-12, Standing Calf Raise 4×12-15 | 17 |
| Push Day B | Overhead Press 3×6-8, Incline Barbell Press 3×8-10, Lateral Raise 4×12-15, Triceps Pushdown 3×10-12 | 13 |
| Pull Day B | Deadlift 3×5-6, Chin-up 3×6-10, Chest-Supported Row 3×8-10, Hammer Curl 3×10-12 | 12 |
| Leg Day B | Front Squat 3×6-8, Hip Thrust 3×8-10, Walking Lunge 3×10-12, Leg Extension 3×10-12, Seated Calf Raise 4×12-15 | 16 |

The first three match the counts visible in the Workouts design: 3/9, 5/16, 5/17.

**Exercise library**

- Chest: Bench Press, Incline Bench Press, Dumbbell Bench Press, Incline Dumbbell Press, Cable Fly, Pec Deck, Push-up, Dip.
- Back: Barbell Row, Pendlay Row, Lat Pulldown, Pull-up, Chin-up, Seated Cable Row, Chest-Supported Row, Single-Arm Dumbbell Row, Face Pull, Straight-Arm Pulldown, Back Extension.
- Shoulders: Overhead Press, Seated Dumbbell Press, Arnold Press, Lateral Raise, Rear Delt Fly, Upright Row.
- Biceps: Barbell Curl, Dumbbell Curl, Hammer Curl, Preacher Curl, Cable Curl.
- Triceps: Close-Grip Bench Press, Triceps Pushdown, Overhead Triceps Extension, Skull Crusher, Bench Dip.
- Quads: Back Squat, Front Squat, Leg Press, Hack Squat, Leg Extension, Walking Lunge, Bulgarian Split Squat.
- Hamstrings: Romanian Deadlift, Good Morning, Leg Curl, Nordic Curl, Stiff-Leg Deadlift.
- Glutes: Hip Thrust, Glute Bridge, Cable Kickback.
- Calves: Standing Calf Raise, Seated Calf Raise.
- Core: Plank, Hanging Leg Raise, Cable Crunch, Ab Wheel.

Equipment values: Barbell, Dumbbell, Cable, Machine, Bodyweight, Other. Muscle group values: Chest, Back, Shoulders, Biceps, Triceps, Quads, Hamstrings, Glutes, Calves, Core. All seed rows are `isCustom = false` and have null unit/rest overrides. Users can create custom exercises. Premade names, muscle groups, and equipment stay read-only; only the unit and working-rest preferences are editable. No seed version bump is needed.

## 5. Phase-by-phase roadmap

Each phase ends with a working build and a verifiable outcome. Do not start a phase until its prerequisites pass.

### Phase 0 - Build and device gate

**Outcome:** An empty but themed app builds, installs, and launches on the phone; the build toolchain is reproducible.

**Prerequisites:** None.

**Work**

- [x] Install Temurin JDK 21 and set `JAVA_HOME`. Verify `java -version` reports 21.
- [x] ADB works over USB: `adb devices -l` lists `SM-S711W` as `device` (Android 16, 1080x2340, 450 dpi).
- [x] Scaffold the Gradle project: Kotlin DSL, `app` module, package `com.buhat.tracker`, minSdk 26, compile/target 35, versionCode 1, versionName 1.0.0.
- [x] Add `.gitignore` for `.gradle`, `build`, `local.properties`, `.idea`, `keystore.properties`, `*.jks`, `*.keystore`.
- [x] Create `local.properties` pointing at `%LOCALAPPDATA%\Android\Sdk` (gitignored).
- [x] Add Gradle wrapper 8.11.1. Gradle is not installed; fetch a distribution once or generate the wrapper from a temporary install.
- [x] Theme shell, Inter font, four-tab bottom bar, empty Home/Workouts/Active/Progress screens, edge-to-edge.
- [x] Build and install a debug APK on the phone.
- [x] Record the phone model, Android version, and screen size in this document.

**Acceptance checks**

- `./gradlew assembleDebug` succeeds from a clean checkout with JDK 21.
- `adb devices -l` shows the phone as `device`.
- App launches, tabs switch, app survives rotation, no crash.
- No INTERNET permission in the merged debug manifest.

**Decision gate:** ADB works over USB. If it fails before Phase 3, try wireless debugging on the same network. Do not proceed to timer work without a real device.

### Phase 1 - Data foundation and design system

**Outcome:** Local data, seed content, pure calculation rules, and reusable UI primitives are in place and tested.

**Prerequisites:** Phase 0.

**Work**

- [x] Room entities, DAOs, indices, foreign keys, and `exportSchema = true` with the schema directory committed.
- [x] Repositories and manual dependency container in `BuhatApplication`.
- [x] DataStore for profile and settings with documented defaults.
- [x] Idempotent seed of the exercise library and 6 premade templates, guarded by a seed version.
- [x] Domain functions: working-position numbering, source-session lookup, per-set target, Epley e1RM, PR chronology, 4-week and 90-day analytics, unit conversion.
- [x] Unit tests for every rule in section 4.3, especially per-position independence and source fallback.
- [x] Dark and light color schemes, typography, spacing, and shared components: card, pill, segmented control, stepper, badge, chart, empty state.
- [x] Settings/profile persistence smoke test.

**Acceptance checks**

- `./gradlew test` passes.
- Fresh install has 6 templates, the seeded exercise list, and correct total-set counts (9, 16, 17, 13, 12, 16).
- Profile and settings values persist across process death.
- Theme switch renders both palettes without hard-coded colors.
- Progression test matrix passes: no history, 7/10, 10/10, missing position, same-template vs any-workout, bodyweight.

### Phase 2 - Exercise library, templates, and profile

**Outcome:** The user can manage all content without touching the database.

**Prerequisites:** Phase 1.

**Work**

- [x] Workouts screen: header count, All/Premade/My templates filters, cards, empty states, Start button placeholder.
- [x] Template editor: create, rename, duplicate premade, delete custom, add/remove/reorder exercises, sets, rep range, optional target RIR, validation.
- [x] Exercise library: search, muscle-group filter, custom create/edit, archive, delete rules, custom badge.
- [x] Premade rows read-only; duplicate available.
- [x] Profile view/edit: name, gender, age, weight, height; training-since and total-session fields derive from data.
- [x] Wire Settings entry from the Home profile card.

**Acceptance checks**

- Create a custom exercise, then a template using it; edit both; restart; values persist.
- Premade template cannot be edited; Duplicate creates a custom copy with the same exercises.
- Reordering and removing exercises save correctly.
- Validation blocks blank names, zero exercises, sets outside 1..10, and min > max.
- Archiving a custom exercise hides it from pickers but keeps it in existing templates.
- Deleting an unreferenced custom exercise works; deleting a referenced one offers archive instead.

### Phase 3 - Active workout and per-set progression

**Outcome:** The core logging loop works end to end with per-set double progression and survives process death.

**Prerequisites:** Phase 2, Phase 1 domain functions.

**Work**

- [x] Start session: copy template into `SessionExercise` snapshots; enforce one active session; Resume/Discard prompt.
- [x] Active header: template name, elapsed time, overflow Finish/Discard.
- [x] Exercise chips and selected exercise.
- [x] Logged-set list: type badge, weight × reps, type label, RIR; edit and delete current-session sets; retype with working-index renumbering.
- [x] Target card for the next working position: source set, suggested weight/reps, progression message, target RIR, "Use target".
- [x] Logging card: segmented type, weight stepper and numeric entry, reps stepper and entry, RIR 0..5, Log set.
- [x] Prefill from the same position in the source session; fallback to the last set in the current session; fallback to blank.
- [x] Add set beyond the prescribed count; finish and discard flows.
- [x] Session elapsed time and active-session restore on launch.
- [x] Unit tests for prefill and target computation, including warmup exclusion and type changes.

**Acceptance checks**

- A 3 × 6-10 exercise with last session 10/9/8 at 80 kg shows targets 82.5×6, 80×10, 80×9 for positions 1/2/3.
- Prefill shows last session's raw values for each position, not the target.
- Logging a warmup does not change working positions or analytics; it uses the warmup rest default.
- Failure sets count as working positions and in analytics.
- Kill the app from recents after logging two sets; reopen; both sets and the active session restore.
- Finish writes a FINISHED session; starting another session prompts Resume/Discard.
- A template edit after starting does not change the active session.
- `./gradlew test` passes.

### Phase 4 - Rest timer and notifications

**Outcome:** A reliable rest timer with background alerts and configurable defaults.

**Prerequisites:** Phase 3 set-logging hook. Timer infrastructure can start after Phase 1; the phone is required for acceptance.

**Work**

- [x] Timer controller with `restEndsAt`, in-app countdown, skip, cancel on finish/discard, and restore on launch.
- [x] Rest settings section: Warmup, Working, Failure seconds; increment; 15s default; 0..600s validation.
- [x] Notification channels: ongoing rest (low importance) and rest complete (high importance, sound).
- [x] Ongoing notification with native countdown chronometer; exact `setAlarmClock` broadcast at zero.
- [x] Completion notification, sound, vibration, and cleanup of timer state.
- [x] Runtime POST_NOTIFICATIONS request on first rest; denial path keeps the in-app timer.
- [x] Replace/cancel behavior when a new set is logged, skipped, finished, or discarded.
- [x] Airplane-mode and lock-screen behavior implemented; physical acceptance checks remain.

**Acceptance checks**

- Logging a set starts the configured rest for its type and shows a live countdown.
- With the app backgrounded and screen locked, the completion alert fires within ±2 seconds of zero and vibrates.
- Skip cancels the notification and alarm; logging a new set replaces them; finishing cancels them.
- Kill the app during rest; reopen; remaining time is correct.
- Notification permission denied: in-app timer still works and the hint appears once.
- `setAlarmClock` behavior is confirmed on the test phone, including OEM battery settings.

**Decision gate:** If `setAlarmClock` is late, silenced unexpectedly, or the alarm icon is unacceptable, replace the background mechanism with a foreground service behind the same timer controller. Do not ship Phase 4 until the phone passes the lock-screen test.

### Phase 5 - Home dashboard and Progress

**Outcome:** The Home and Progress designs are functional against real logged data.

**Prerequisites:** Phase 3 data, Phase 1 analytics functions.

**Work**

- [x] Home profile card: name, training since, total workouts, gender/age/weight/height, Settings button.
- [x] Strength trend card: recent-exercise chips (most recent workout's exercises, max 4), 3-month e1RM chart, latest value, change, and a "View progress" link.
- [x] 4-week analytics grid: workouts, working sets, volume, new PRs, deltas vs prior 4 weeks.
- [x] Progress screen: exercise picker, 1M/3M/1Y/All selector defaulting to 3M, e1RM chart, best e1RM, heaviest set, sessions.
- [x] Past workouts: date, template snapshot, best set, e1RM, PR badge, expandable sets, delete workout with confirmation.
- [x] Empty states for no profile, no history, and no data in range.
- [x] Chart accessibility: text summary, content descriptions, no color-only meaning.
- [x] Fixture-based tests for analytics and PR badges.

**Acceptance checks**

- With a hand-built fixture of sessions, Home and Progress numbers match hand calculations.
- Warmup sets appear in history but never in volume, working sets, e1RM, or PRs.
- PR badges reflect the all-time chronology, not only the selected range.
- Range switches update the chart and change value; 1M with no data shows the empty state.
- Deleting a workout recalculates all derived values.
- The Home chips match the most recent workout and open the corresponding Progress exercise.

### Phase 6 - Settings, export, and import

**Outcome:** All Settings controls work and data can round-trip through a file.

**Prerequisites:** Phase 1 DataStore, Phase 5 history data, Phase 3 active-session guard.

**Work**

- [x] Settings screen: appearance, units, previous-set source, rest defaults/increment (complete Phase 4), data section, profile link.
- [x] Export JSON via CreateDocument; include profile, settings, exercises, templates, template exercises, sessions, session exercises, and sets; exclude rest state (an active session cannot be exported).
- [x] Import JSON via OpenDocument; validate version, fields, enums, ranges, IDs, references, timestamps; show counts and export date.
- [x] Replace-all confirmation; Room transaction; then profile/settings; mark seed complete; clear rest state; reload flows.
- [x] Block export/import while a session is active.
- [x] Specific errors for not-a-backup, newer version, corrupt file, and write failure.
- [x] Round-trip and failure tests.

**Acceptance checks**

- Export on the phone, clear app data, import, and compare all counts and values.
- Import preserves templates, custom exercises, sessions, profile, and settings exactly.
- Malformed, truncated, and newer-version files are rejected with no data change.
- Importing during an active session is blocked.
- kg/lb and theme changes apply immediately across every screen.
- Previous-set source changes future targets only, never stored history.

### Phase 7 - Visual pass and release

**Outcome:** A signed APK that matches the designs and passes the full device checklist.

**Prerequisites:** Phases 0-6.

**Work**

- [x] Compare all five screens plus editors with the designs in dark and light themes; fix spacing, type scale, and color drift.
- [x] Font scaling, long names, empty states, contrast, and 48dp touch targets.
- [x] Full E2E checklist from section 4.6 on the phone.
- [x] Release keystore outside the repo; `keystore.properties` gitignored; signing config fails clearly when absent.
- [x] `allowBackup="false"`, no INTERNET permission, R8 minify and resource shrink; verify release build, not only debug.
- [x] versionCode 2 / versionName 1.0.0; build signed APK; install on the phone; test in airplane mode.
- [x] Back up the keystore and credentials in two places; record the update steps.
- [x] Share the APK with one friend; confirm install and launch.

**Acceptance checks**

- Signed release APK installs on the test phone and one other Android device.
- App runs fully in airplane mode; no network permission.
- All Phase 3-6 acceptance checks pass on the release build.
- Data survives installing a higher-versionCode release over the previous release.
- Designs match at phone size; no clipped text at 1.3× font scale.
- Keystore and `keystore.properties` are outside git; `git status` is clean of secrets.

### Phase 8 - Personal release hardening

**Outcome:** The app protects its own data and explains failures without a network or cloud service. A missing or stale backup is visible, a crash leaves a local report, a database that cannot open shows a recovery path, and an app update asks for a backup.

**Prerequisites:** Phase 7.

**Work**

- [x] Add a local diagnostics log under app-private `filesDir/diagnostics`. Debug builds record informational events, release builds record warnings and errors only. Rotate at a fixed size and keep the previous file. Never record profile fields, exercise names, weights, reps, or backup contents.
- [x] Install a default uncaught exception handler that writes a crash report and a pending-crash marker, then delegates to the previous handler. Keep the last few crash reports and prune older ones.
- [x] Add "Export diagnostics" to Settings. The file contains app version, versionCode, Android version, device model, recent log lines, and crash reports. It uses the Storage Access Framework and contains no training data. The action works during an active workout.
- [x] Check the database explicitly on startup and expose loading, ready, database-error, and seed-error states. Do not render the main UI when the database cannot open.
- [x] Add a recovery screen for database open failures. Offer "Export diagnostics", "Import backup", and "Reset local data". Validate a chosen backup before resetting anything, reset the database and DataStore, recreate the container, then import. A reset requires confirmation. A downgrade gets a specific "created by a newer version" message, not a crash.
- [x] Catch seed failures, log them, and show a retry action instead of crashing.
- [x] Store `lastExportAt` only after the export file write succeeds. Show the last backup date in Settings.
- [x] Show a Home reminder when the user has at least one finished workout and has never exported or the last export is 14 days old. The reminder opens export directly. "Remind me in 7 days" stores a snooze.
- [x] Store the acknowledged app versionCode. On the first launch after a version change, show a one-time dialog with "Export backup" and "Not now". Fresh installs do not show it. A successful export or "Not now" acknowledges the version. If a workout is active, explain that export needs the workout finished.
- [x] Log export, import, seed, database, and crash failures with event names and counts. Keep user-facing messages unchanged.
- [x] Add unit tests for the reminder threshold and snooze, log level gating, rotation, crash formatting, and the no-PII rule.
- [x] Add device tests for the last-export timestamp, reminder and snooze, update prompt, seed-failure retry, crash notice, diagnostics export, and the recovery screen for a higher-version database. Cover reset and import recovery end to end.

**Acceptance checks**

- A fresh install with no finished workouts shows no backup reminder. After the first finished workout, Home shows it. Export clears it. It returns after 14 days or after a 7-day snooze expires.
- A cancelled, failed, or oversized export does not update the last backup date.
- A debug crash writes a local report and the next launch shows a one-time notice. Diagnostics export contains the report and no profile, exercise, or workout values.
- A database file with a higher schema version opens to the recovery screen instead of crashing. Diagnostics export works. Reset reseeds. Import backup restores the previous data.
- The first launch after a versionCode change shows the update prompt once. Fresh installs and later launches do not.
- A forced seed failure is logged, shows a retry action, and does not crash.
- The full E2E suite passes on the current head. The release build has no INTERNET permission, no new permissions, and writes diagnostics only to app-private storage or a user-chosen file.
- Existing export/import, timer, active-session, analytics, and visual behavior is unchanged.

### Phase 9 - Recovery and diagnostics hardening

**Outcome:** The Phase 8 recovery and diagnostics paths hold under failure. Reset and recovery reopen the same DataStore without a collision, a failed restore is visible, large backups are validated off the main thread, and a failed preference write cannot crash the app.

**Prerequisites:** Phase 8.

**Work**

- [x] Make `AppContainer.close()` suspend and join the DataStore scope before returning. Use it in reset and recovery so a second DataStore cannot open `buhat.preferences_pb` before the first releases it.
- [x] Disable the Home backup reminder's "Export now" during an active workout and show the same "Finish or discard the workout first." hint as Settings.
- [x] Show a recovery import failure in the new app. State that local data was reset, the backup file is unchanged, and it can be imported again from Settings.
- [x] Move recovery backup decoding and validation onto `Dispatchers.IO`.
- [x] Open a recovery container without seeding, then import, so a seed failure cannot leave a false setup error. If the import fails, seed the fresh app before showing it.
- [x] Wrap the update-prompt acknowledgement and Home snooze writes so a failed preference write cannot crash the app.
- [x] Guard the `MainActivity` theme flow with a fallback so a closed DataStore cannot crash the app.
- [x] Use a loaded state for the Home backup reminder and the Settings last-backup label so neither shows "No backup yet" before the DataStore emits.
- [x] Replace the two date formatters with one shared helper.
- [x] Add tests: end-to-end recovery reset/import, failed export leaves the last-export date null, no-PII diagnostics after a real workout, update prompt active-session and successful-export branches, and the active-workout Home reminder.

**Acceptance checks**

- A reset or recovery on an unreadable database completes without a DataStore collision and leaves a usable app.
- A failed post-reset import shows a message, keeps the backup file, and does not crash.
- The Home reminder's export button is disabled with a clear hint while a workout is active.
- A 20 MB recovery backup validates without blocking the main thread.
- A failed preference write does not crash the app on "Not now," a successful update export, or "Remind me in 7 days."
- A successful recovery import restores the backup, and the app does not seed over it.
- Diagnostics exported after a workout contains no exercise name, weight, or profile value.
- The full E2E suite and release verifier pass.
- Existing export/import, timer, active-session, analytics, and visual behavior is unchanged.

### Phase 10 - Post-release UI refinements

**Outcome:** The deployed UI matches the Active Workout RIR selector and Home profile banner designs. RIR offers 0, 1, 2, 3, 4+. Home shows Weight, Height, and Sessions in equal-height cards. No database or backup-format change.

**Prerequisites:** Phase 9. Existing installs and backups must keep working.

**Work**

- [x] Active Workout RIR selector: replace the 0..5 stepper with five design-matched choices, 0, 1, 2, 3, 4+, in the logging card and the set edit dialog. 4+ stores 4. Stored 4 and 5 render 4+ in logged rows, the target card, and Progress history. Keep the Room schema and backup validation at 0..5.
- [x] Add a 16dp vertical gap between the RIR selector and the Log set button so the logging card does not read as compressed.
- [x] Home profile banner: keep the avatar, name, and Settings button. The subtext becomes "Training since MMM yyyy" with no workout count. Replace the three tiles with Weight, Height, and Sessions.
- [x] Equal-height stat cards: give all three cards one height and align labels and values. Sessions shows total finished workouts with a "total" suffix. Weight uses the current kg/lb setting.
- [x] Height display follows the weight unit: cm for kg, feet/inches for lb to the nearest inch. Stored height stays cm and the profile editor stays cm-only.
- [x] Update unit and device tests for the selector, subtext, values, and equal card height. Update the E2E test catalog.
- [x] Visual check against `docs/design/active-workout.png` and `docs/design/home-profile.png` on the Pixel 6 / API 35 emulator in dark theme, including null weight/height and a finished workout. Light theme, 1.3x font scale, and long names stay in the release checklist.

**Acceptance checks**

- The RIR selector shows exactly 0, 1, 2, 3, 4+. There is no 5 choice. Logging 4+ stores `rir = 4`, and the logged row reads "RIR 4+".
- An existing or imported RIR 5 still loads, displays "RIR 4+", and does not break import. Template target RIR and validation remain 0..5.
- Home subtext reads "Training since Nov 2024" for a first finished workout in November 2024 and shows no workout count. With no finished workouts it reads "No workouts yet".
- Home shows only Weight, Height, and Sessions cards. Gender and age are not shown in the banner.
- kg mode shows the stored kilogram value. lb mode shows the converted pound value. Height shows "178 cm" in kg mode and "5'10\"" in lb mode for 178 cm.
- All three cards have equal height at 1.0x and 1.3x font scale. Labels and values line up across cards. Long names do not clip the Settings button.
- `./gradlew test` and the full E2E suite pass. The release verifier still passes with no new permissions.
- Side-by-side check against both updated designs passes in dark and light themes.
- No Room schema version, entity, DAO, DataStore key, or backup DTO changes.

**Result:** 2026-11-08. Full E2E suite passes: 101 unit tests and 181 device tests, 0 failed. The RIR selector shows exactly 0, 1, 2, 3, 4+; logging 4+ stores `rir = 4` and the row reads "RIR 4+". A stored RIR 5 reads "RIR 4+" in the logged row and Progress history, and a target RIR 5 reads "Target RIR 4+"; validation and backup import remain 0..5. Home shows only Weight, Height, and Sessions in equal-height cards, with no gender or age, and 178 cm renders as `5'10"` in lb mode. Manual dark-theme flows on the emulator passed. No schema or backup-format change.

### Phase 11 - Preference storage, migration, and backup compatibility

**Outcome:** Exercise rows can store optional unit and working-rest overrides. Existing installs migrate without data loss, and both old and new backups round-trip.

**Prerequisites:** Phase 10. Export current data before testing the migration.

**Work**

- [x] Add `weightUnitOverride: String? = null` and `restWorkingSecondsOverride: Int? = null` to `Exercise`. Keep `nameLower` and every existing field unchanged.
- [x] Bump `BuhatDatabase` to version 3. Add `MIGRATION_2_3` with `ALTER TABLE Exercise ADD COLUMN weightUnitOverride TEXT` and `ALTER TABLE Exercise ADD COLUMN restWorkingSecondsOverride INTEGER`. Register both migrations. Commit the generated `3.json`.
- [x] Extend `BackupExercise` with `@Required val weightUnitOverride: String? = null` and `@Required val restWorkingSecondsOverride: Int? = null`. Extend `Exercise.toBackup()` and `BackupExercise.toExercise()`.
- [x] Bump `BACKUP_FORMAT_VERSION` to 2. `decodeBackup` accepts 1 and 2. For a version-1 file, add both keys with JSON null to every exercise object, then use that patched root for both decoding and `validateJsonTypes`. Export writes version 2 only.
- [x] Validate the new fields: unit override is null, `"KG"`, or `"LB"`; rest override is null or 0..600.
- [x] Add `saveExercisePreferences(exerciseId, unitOverride, restWorkingSecondsOverride)` to the repository. It updates only the two columns, validates rest, and works for premade and custom rows.
- [x] Add unit tests for v1 import defaults, v2 round trip, and invalid overrides. Add a migration test for 2->3 and the 1->3 chain.

**Acceptance checks**

- A version-2 database with exercises upgrades to version 3; rows, names, and references survive; both new columns are null.
- A format-1 backup imports with null overrides and unchanged everything else.
- A format-2 backup round-trips overrides exactly.
- Invalid unit strings or rest outside 0..600 are rejected before any write.
- `.\gradlew.bat testDebugUnitTest --console=plain` passes, the migration device test passes, and `3.json` is committed.

**Result:** 2026-11-08. Full E2E suite passes: 106 unit tests and 186 device tests, 0 failed. `Exercise` stores nullable `weightUnitOverride` and `restWorkingSecondsOverride`; database version 3 migrates 1->2->3 with both columns null on existing rows, and `3.json` is committed. Backup format 2 round-trips overrides, format-1 files import with null overrides, and invalid units or rest outside 0..600 are rejected before any write. `saveExercisePreferences` updates only the two columns for premade and custom rows. Effective-unit fallback stays in Phase 12, where the helper is introduced.

### Phase 12 - Per-exercise unit and working rest

**Outcome:** Any exercise can use its own display unit and working rest in logging, targets, history, Progress, and Home, with global fallback.

**Prerequisites:** Phase 11.

**Work**

- [x] Add `Exercise.effectiveUnit(global)` and `Exercise.effectiveWorkingRest(globalSeconds)`. Add `weightIncrementKg(unit)` returning 2.5 kg for kg and `lbToKg(5.0)` for lb.
- [x] Change `nextTarget` to use the unit-aware increment and to format its message with the effective unit.
- [x] Extend `restSecondsFor` or add a resolver: working uses the exercise override when non-null, else global; warmup and failure always global. Use it in `logSet`.
- [x] Active Workout: observe the selected exercise row; use the effective unit for target, prefill, logged rows, set editor, weight labels, and step buttons. Add the Settings button to the target card.
- [x] Build one Exercise Settings dialog and open it from the Exercise Library row menu and the Active target card. Unit: Default (current global), kg, lb. Working rest: Default (current global), 15-second steps 0..600, and "Use default". Save through `saveExercisePreferences`.
- [x] Exercise Library: show the menu on every row. Premade rows offer only Exercise settings; custom rows keep Edit, Archive, Delete, and Exercise settings.
- [x] Progress: use the selected exercise's effective unit for the e1RM chart, best e1RM, heaviest set, and past sets. Home: use the selected trend exercise's effective unit. Add a DAO flow for all exercises including archived rows so archived exercises keep their unit. Keep volume, body weight, and height on the global unit.
- [x] Update the Settings Units copy: kg steps 2.5 kg, lb steps 5 lb, and exercises can override the unit and working rest from Exercise settings.
- [x] Tests: unit helpers, kg and lb progression, rest resolution, dialog from both entry points, premade name unchanged, lb logging, override 0, global fallback, mixed units in one history list.

**Acceptance checks**

- Bench Press set to lb and Leg Extension to kg: logging 135 shows 135 lb, logging 60 shows 60 kg, stored values remain kilograms, and each row renders its own unit.
- At the top of the rep range, an lb exercise is bumped 5 lb and a kg exercise 2.5 kg. The target message matches.
- A 3:00 working-rest override starts 3:00 after a working set. Warmup and failure still use their global defaults. A 0 override starts no rest.
- Bench Press Progress uses lb; Home volume stays in the global unit.
- A premade exercise's name, muscle group, and equipment cannot be changed. Changing the global unit does not change explicit overrides.
- Changing a preference mid-session changes the next set and target, not logged sets or a running timer.

**Result:** 2026-11-09. Full E2E suite passes: 113 unit tests and 195 device tests, 0 failed. `Exercise.effectiveUnit` and `effectiveWorkingRest` resolve overrides with global fallback, and `nextTarget` bumps 2.5 kg or 5 lb with a matching message. One Exercise Settings dialog opens from every Exercise Library row and the Active target card, and premade rows stay read-only. Logging applies the working-rest override to working sets only; a 0 override starts no rest. Active Workout, Progress, and Home render each exercise's effective unit, archived exercises keep theirs, and volume, body weight, and height stay global. No Room schema, DataStore key, or backup-format change.

### Phase 13 - Active workout exercise reorder

**Outcome:** Exercises can be reordered during an active workout, and the new order survives finish and process death without changing the template.

**Prerequisites:** Phase 10. Phase 12 is not a prerequisite, but both touch `ActiveWorkoutScreen.kt`; land them serially.

**Work**

- [ ] Add DAO reads and position updates for a session's exercises. Add `reorderSessionExercises(sessionId, orderedIds)` to the repository inside `sessionWriteMutex` and one transaction.
- [ ] Validate that the session is ACTIVE and that `orderedIds` is exactly the session's exercise IDs with no duplicates. Move positions to negatives, then write 1..N.
- [ ] Add "Edit exercise order" to the Active overflow menu. Open a dialog listing the session's exercises with up/down buttons. Save on each move; disable the arrows at the ends and while a write is in flight; keep the selected exercise selected.
- [ ] Add test tags such as `edit-order`, `order-up-<name>`, `order-down-<name>`, and `close-order`.
- [ ] Tests: live reorder, finish order, process death, logged sets stay attached, template unchanged, one exercise, invalid ID list rejected.

**Acceptance checks**

- Moving the third exercise to first updates the dialog and chip order immediately.
- Finishing stores the new order and past workouts show it. The source template keeps its original order.
- Killing the app after a reorder restores the new order.
- Logged sets stay under their exercise, and a running rest is unaffected.
- Reordering one exercise is a no-op with disabled arrows.
- Unit and device suites pass.

### Phase 14 - Full validation and release

**Outcome:** A signed update that passes the full suite, upgrades the installed release with data intact, and restores an old backup.

**Prerequisites:** Phases 11-13.

**Work**

- [ ] Update the E2E test catalog and `.opencode/skills/e2e-testing` references for exercise settings, lb logging, rest overrides, and reorder.
- [ ] Run `.\gradlew.bat testDebugUnitTest --console=plain` and `.\gradlew.bat pixel6api35DebugAndroidTest --rerun`.
- [ ] Run manual checks 10-13 from section 4.6 on the test phone.
- [ ] Visual check in dark and light themes at 1.0x and 1.3x font scale, long exercise names, mixed units in one list, dialog height, and 48dp targets.
- [ ] Bump `versionCode` from 3 to 4 and `versionName` from 1.0.1 to 1.1.0. Build and sign. Install over the current release and confirm data and preferences survive.
- [ ] Verify the release manifest has no new permissions and no INTERNET permission.

**Acceptance checks**

- All Phase 11-13 acceptance checks pass on the signed release.
- Installing over v1.0.1 keeps exercises, templates, history, and new preferences.
- A format-1 backup restores on the new release.
- Existing timer, active-session, analytics, export/import, recovery, diagnostics, and visual behavior is unchanged.
- The release verifier passes.

### 5.1 Traceability

| Requirement | Planned work | Acceptance evidence |
|---|---|---|
| Offline, local, no accounts | Phases 1, 7 | Fresh install in airplane mode; no INTERNET permission |
| Profile fields | Phases 2, 5, 6 | Profile persists, appears on Home, survives export/import |
| Seeded and custom exercises | Phase 2 | Search, create, edit, archive, delete rules pass |
| Premade and custom templates | Phase 2 | 6 seeds visible; custom create/edit/duplicate/delete pass |
| Sets, rep range, target RIR | Phases 1, 2 | Editor validation and saved values pass |
| Exercise order in session | Phases 3, 13 | Session snapshot keeps order after template edits; active reorder persists to history |
| Weight, reps, RIR, set type | Phase 3 | Logged list, edit, retype, warmup exclusion pass |
| Rest timer and defaults | Phase 4 | Lock-screen alert within ±2s; skip/cancel pass |
| Per-set double progression | Phases 1, 3 | 10/9/8 fixture yields 82.5×6, 80×10, 80×9 |
| Five designs | Phases 2, 3, 5, 6, 7 | Side-by-side visual check dark and light |
| Dashboard, e1RM, PRs, history | Phase 5 | Hand-calculated fixture matches |
| Units, theme, previous source, rest settings | Phases 4, 6, 12 | Changes apply live and persist; per-exercise unit and rest override pass |
| Export/import | Phase 6 | Round trip on phone; invalid files rejected |
| Signed APK distribution | Phase 7 | Release APK installs on phone and a friend's device |
| Physical-phone validation | Phases 0, 7 | ADB device listed; full E2E checklist passes |
| Backup age and reminder | Phase 8 | Reminder appears after a finished workout, export clears it, 7-day snooze works |
| Local diagnostics and crash reports | Phase 8 | Crash notice appears, diagnostics export contains no training data |
| Recovery and rollback | Phase 8 | Higher-version database shows recovery screen, reset and import restore data |
| Update backup prompt | Phase 8 | Prompt appears once after versionCode change, not on fresh install |
| Recovery DataStore safety | Phase 9 | Reset and recovery reopen the same DataStore without a collision |
| Visible recovery failures | Phase 9 | A failed post-reset import shows a message and keeps the backup |
| Backup reminder guard | Phase 9 | Export is disabled during an active workout |
| Diagnostics privacy | Phase 9 | Export after a workout contains no exercise or profile values |
| Per-exercise unit | Phase 12 | Bench lb and leg kg render correctly; storage stays kg; aggregates stay global |
| Per-exercise working rest | Phase 12 | Override starts after working sets; warmup and failure fall back; 0 means no rest |
| Active workout reorder | Phase 13 | Order persists to history; template unchanged |
| Schema and backup compatibility | Phase 11 | v2 database upgrades; v1 backup defaults; v2 round trip |

## 6. Dependencies, remaining uncertainties, and next step

### Dependencies and parallel work

- Completed critical path: Phase 0 -> 1 -> 3 -> 5 -> 6 -> 7 -> 8 -> 9 -> 10.
- New critical path: Phase 11 -> 12 -> 14. Phase 11 is the schema/backup gate for Phase 12.
- Phase 13 depends only on Phase 10. It touches `ActiveWorkoutScreen.kt` and the session DAO, which Phase 12 also touches, so land the two serially or freeze the file split first.
- Phase 14 follows all three feature phases.
- Shared contracts to freeze before parallel work: the two `Exercise` override columns, `effectiveUnit` and `effectiveWorkingRest` signatures, the unit-aware increment, `BACKUP_FORMAT_VERSION = 2`, the reorder DAO and repository signature, and the dialog test tags.

### Remaining uncertainties and assumptions

**Blocking environment items**

None. Temurin JDK 21 is installed and `JAVA_HOME` points at it.

**Product assumptions already in the shipped baseline, none blocks Phases 11-14**

- Premade template list and exercise library contents in section 4.7.
- Epley formula, PR = strictly greater e1RM, and "new PRs" counts PR sets rather than distinct exercises.
- Bodyweight exercises log external weight only; e1RM is omitted at weight 0.
- Height stays in centimeters when units are pounds.
- Default RIR is 2 when there is no history; target RIR is advisory.
- Rest increment is 15 seconds; per-type defaults are 1:00, 2:00, 3:00.
- Finished workouts can be deleted but not edited.
- Home shows total finished workouts instead of the design's "sessions per lift" wording, which is ambiguous.
- Delta colors use improvement green and decline muted/orange. The design's green negative volume number is treated as a mockup artifact.
- Default theme is Dark, default unit is kg, default previous-set source is Any workout, and Progress defaults to the 3M range.
- The rest card shows Skip only while a rest is running. The design's always-visible Skip has no action when idle.
- The Settings copy for "Previous set" says the same set number from the most recent session, not the design's "most recent set", because progression is per position.
- The Settings data section and the Active "Use target" action are additions needed by the agreed export/import and per-set progression behavior.
- JSON backups are unencrypted and manual; a lost phone loses data since Android auto-backup is disabled.

**New feature assumptions to confirm during review, none blocks Phase 11**

- "Default" means inherit the current global setting. Changing Settings updates every inheriting exercise.
- An explicit kg or lb override stays until cleared, even when the global unit changes.
- Only working rest is per exercise. Warmup and failure rest remain global by design.
- Unit and rest changes affect future sets and rendering, never stored history.
- A deleted exercise's history falls back to the global unit.
- lb exercises step and bump 5 lb; kg exercises keep 2.5 kg.
- Format-1 backups import with null overrides. Format-2 backups require the updated app; older builds reject them as newer.
- No seed version bump. Fresh seeds have null overrides.
- Next release is `versionCode 4`, `versionName 1.1.0`.
- Reorder uses up/down buttons, not drag-and-drop.

**Technical risks**

- The Room migration must be tested over the installed v1.0.1 release, not only on a fresh test database.
- Mixed units make screenshots and support questions harder; every weight string must include its unit.
- Format 2 is one-way for backups. Friends on older builds cannot import new files, so the release note must say to update first.
- Reordering and unit/rest edits both touch `ActiveWorkoutScreen.kt`; parallel work there will conflict.
- OEM battery management may delay or suppress the alarm on some phones. Phase 4 tests this; foreground service is the fallback.
- Per-set progression can produce different weights across sets in one session. This is intentional, but confirm it feels right after the first real workout.
- Room schema changes after v1 need migrations. Export before installing a schema-changing update.
- Losing the release keystore forces friends to uninstall and lose local data unless they export first.

### Next step

Phase 12 is complete. Start Phase 13: add session exercise reorder with DAO reads and position updates, the edit-order dialog, and persistence across finish and process death.
