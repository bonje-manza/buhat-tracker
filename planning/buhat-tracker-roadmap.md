# Buhat Roadmap

## 1. Status, goal, and success criteria

**Status:** Phase 0 complete. Temurin JDK 21 is installed with `JAVA_HOME` set, the Gradle 8.11.1 wrapper is committed, and a themed debug build is installed and running on the test phone. Phase 1 is next.

**Goal:** A private, offline Android strength log for you and your friends. Each install owns its data. You train from premade or custom templates, log every set with weight, reps, RIR, and type, get per-set double-progression targets, run a rest timer, and review e1RM progress. Data moves by JSON export/import. Delivery is a signed APK.

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

## 2. Scope

**Included**

- Native Android app, offline only, one local database per install.
- Profile: name, gender, age, weight, height. Editable, optional fields.
- Exercise library: seeded common exercises plus custom exercises with name, muscle group, and equipment.
- Templates: 6 read-only premade templates, custom create/edit/duplicate/delete, exercise order, working-set count, rep range, optional target RIR.
- Active workout: start from template, exercise order, per-set logging (weight, reps, RIR, type), set edit/delete, add set, finish/discard, resume after process death, elapsed session time.
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
- Localization beyond English.
- App lock, encryption at rest, cloud auto-backup.

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
| Test target | Physical phone over USB | User-confirmed | Real timer, vibration, lock-screen, and touch behavior |
| Template targets | Working-set count + rep range + optional target RIR | User-confirmed | Small editable template, supports double progression |
| Previous-set source | Any workout (default) or Same template | Design + assumption | Drives which session supplies per-set history and prefill |
| Weight model | Store kilograms, display kg or lb | Design + assumption | One canonical value, no drift. 2.5 kg step |
| Rest defaults | Warmup 1:00, Working 2:00, Failure 3:00. Increment 15s | Design + assumption | Matches Settings design. Increment value was cut off |
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
| `Exercise` | `id`, `name`, `muscleGroup`, `equipment`, `isCustom`, `archivedAt`, `createdAt` | Name unique case-insensitive. Seeded and custom share one table. |
| `Template` | `id`, `name`, `isPremade`, `createdAt`, `updatedAt`, `archivedAt` | Premade rows are read-only. Custom rows editable. |
| `TemplateExercise` | `id`, `templateId`, `exerciseId`, `position`, `workingSets`, `repMin`, `repMax`, `targetRir` | Unique positions and unique exercise per template. Cascade on template delete. |
| `WorkoutSession` | `id`, `templateIdSnapshot`, `templateNameSnapshot`, `startedAt`, `finishedAt`, `status` | `status`: ACTIVE, FINISHED. Template ID is a string snapshot, not an FK. |
| `SessionExercise` | `id`, `sessionId`, `exerciseId`, `exerciseNameSnapshot`, `position`, `workingSets`, `repMin`, `repMax`, `targetRir` | Snapshot of the template at start. Cascade on session delete. |
| `LoggedSet` | `id`, `sessionExerciseId`, `setType`, `workingIndex`, `weightKg`, `reps`, `rir`, `completedAt` | `workingIndex` is 1-based among working/failure sets; null for warmups. |

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
| Last reps < `repMax` | same weight | `min(lastReps + 1, repMax)` | "Beat N reps at X kg." |
| Last reps >= `repMax` | weight + 2.5 kg | `repMin` | "Top of range reached. Add 2.5 kg, restart at repMin." |
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

- Store kilograms. Display pounds as `kg × 2.20462`, rounded to 0.1 lb. Weight steps stay 2.5 kg; the Settings copy matches the design.
- Height stays in centimeters in both unit modes. Age is an integer.
- RIR is an integer 0..5. Set types are Warmup, Working, Failure.

### 4.4 Technical approach

**Stack and structure**

- Single `app` module, single activity, Compose + Navigation Compose.
- `data/local` (Room entities, DAOs, database, seed), `data/repo`, `domain` (pure progression, e1RM, PR, analytics), `ui` (theme, components, screens), `timer`.
- Repository + ViewModel + Flow. Manual dependency container in `BuhatApplication`; no Hilt, no networking library.
- Kotlin serialization for backup DTOs. KSP for Room.
- No app code exists yet, so there is nothing to reuse beyond AndroidX, Compose, Room, DataStore, and Kotlin serialization. Do not add a chart library: the line chart is a small Compose Canvas.

**UI system**

- Dark palette from the designs: near-black background, dark gray cards, white text, lime working accent, amber warmup, orange primary actions and PR badges. Light palette derived with the same accents and checked contrast.
- Bundle Inter (OFL) at 400/600/800 with tabular figures. Fall back to system sans if the asset cannot be fetched.
- Shared components: rounded card, pill chip, segmented control, large number stepper, set-type badge, PR badge, line chart, empty state.
- Edge-to-edge; status bar icons follow theme. Bottom tab bar is a custom four-pill row, not Material NavigationBar.
- Touch targets at least 48dp. Set type is always shown as text, not color alone. Charts have text summaries.

**Screens**

- Home: profile card, 3-month trend with exercise chips, 4-week stat grid. Settings button in the profile card.
- Workouts: count, All/Premade/My templates filters, template cards, Start. Overflow opens Exercise library.
- Active: header, elapsed pill, exercise chips, target card, rest card, logged sets, logging card. Overflow: Finish workout, Discard workout.
- Progress: exercise picker, range segmented control, e1RM card, three stat tiles, past workouts with expand and delete.
- Settings: appearance, units, previous set, rest timer defaults and increment, data export/import, profile link.
- Full-screen editors: template, exercise, profile. Back arrow, no bottom nav.

**Active session persistence**

- Logged sets are written on tap, not on finish.
- The active session is a normal Room row. On launch, the Active tab shows it.
- Starting another session prompts Resume or Discard.
- Session elapsed time derives from `startedAt`, so it survives backgrounding.

**Rest timer**

- On set log, compute `restEndsAt = now + seconds(setType)` and persist it with the type. If the duration is 0, do not start.
- In-app countdown ticks from `restEndsAt` using elapsed-realtime math, so it does not drift.
- Background alert: post an ongoing notification with a native countdown chronometer and schedule an exact `AlarmManager.setAlarmClock` broadcast at `restEndsAt`. The broadcast posts a completion notification with sound and vibration, cancels the ongoing notification, and clears timer state.
- `setAlarmClock` avoids the Android 12+ exact-alarm special-access prompt. It shows a system alarm icon during rest. If device testing shows unreliable delivery or the icon is unacceptable, replace with a foreground service (`specialUse` on Android 14+) behind the same timer interface. This is the Phase 4 decision gate.
- Skip, finish, discard, and starting a new rest cancel the alarm and notification.
- Notification permission is requested when the first rest starts. If denied, the in-app timer still works and a one-time hint explains that background alerts need permission.
- On app restart, read `restEndsAt`; if future, show the countdown, otherwise clear it. A reboot loses the alarm; opening the app clears stale state. Acceptable for v1.

**Export/import**

- Format version 1. Root: `formatVersion`, `appVersion`, `exportedAt`, `profile`, `settings`, `exercises`, `templates`, `templateExercises`, `sessions`, `sessionExercises`, `sets`.
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
- **Unit change mid-session:** stored kg is untouched; all fields re-render in the new unit. Prefill uses converted values and rounds only for display.
- **Notification permission denied:** timer remains usable in-app; no background alert until permission is granted.
- **Storage revoked during export/import:** operation fails cleanly; database untouched.

### 4.6 Validation approach

**Automated**

- Unit tests for progression: no history, below range, mid-range, top of range, per-position independence, fallback when a position is missing, Any workout vs Same template, bodyweight.
- Unit tests for Epley e1RM, PR chronology (strictly greater), 4-week, 30-day, 90-day, and 365-day window boundaries, volume excluding warmups.
- Unit tests for kg/lb conversion round trips and display formatting.
- Import tests: valid round trip, malformed JSON, missing references, duplicate IDs, newer version, transaction rollback.
- Room in-memory tests for DAO constraints, cascade deletes, working-index uniqueness, active-session queries.
- `./gradlew test` and `./gradlew assembleRelease` must pass before release.

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

Equipment values: Barbell, Dumbbell, Cable, Machine, Bodyweight, Other. Muscle group values: Chest, Back, Shoulders, Biceps, Triceps, Quads, Hamstrings, Glutes, Calves, Core. All seed rows are `isCustom = false`; users can create custom exercises but cannot edit premade rows.

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

- [ ] Room entities, DAOs, indices, foreign keys, and `exportSchema = true` with the schema directory committed.
- [ ] Repositories and manual dependency container in `BuhatApplication`.
- [ ] DataStore for profile and settings with documented defaults.
- [ ] Idempotent seed of the exercise library and 6 premade templates, guarded by a seed version.
- [ ] Domain functions: working-position numbering, source-session lookup, per-set target, Epley e1RM, PR chronology, 4-week and 90-day analytics, unit conversion.
- [ ] Unit tests for every rule in section 4.3, especially per-position independence and source fallback.
- [ ] Dark and light color schemes, typography, spacing, and shared components: card, pill, segmented control, stepper, badge, chart, empty state.
- [ ] Settings/profile persistence smoke test.

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

- [ ] Workouts screen: header count, All/Premade/My templates filters, cards, empty states, Start button placeholder.
- [ ] Template editor: create, rename, duplicate premade, delete custom, add/remove/reorder exercises, sets, rep range, optional target RIR, validation.
- [ ] Exercise library: search, muscle-group filter, custom create/edit, archive, delete rules, custom badge.
- [ ] Premade rows read-only; duplicate available.
- [ ] Profile view/edit: name, gender, age, weight, height; training-since and total-session fields derive from data.
- [ ] Wire Settings entry from the Home profile card.

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

- [ ] Start session: copy template into `SessionExercise` snapshots; enforce one active session; Resume/Discard prompt.
- [ ] Active header: template name, elapsed time, overflow Finish/Discard.
- [ ] Exercise chips and selected exercise.
- [ ] Logged-set list: type badge, weight × reps, type label, RIR; edit and delete current-session sets; retype with working-index renumbering.
- [ ] Target card for the next working position: source set, suggested weight/reps, progression message, target RIR, "Use target".
- [ ] Logging card: segmented type, weight stepper and numeric entry, reps stepper and entry, RIR 0..5, Log set.
- [ ] Prefill from the same position in the source session; fallback to the last set in the current session; fallback to blank.
- [ ] Add set beyond the prescribed count; finish and discard flows.
- [ ] Session elapsed time and active-session restore on launch.
- [ ] Unit tests for prefill and target computation, including warmup exclusion and type changes.

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

- [ ] Timer controller with `restEndsAt`, in-app countdown, skip, cancel on finish/discard, and restore on launch.
- [ ] Rest settings section: Warmup, Working, Failure seconds; increment; 15s default; 0..600s validation.
- [ ] Notification channels: ongoing rest (low importance) and rest complete (high importance, sound).
- [ ] Ongoing notification with native countdown chronometer; exact `setAlarmClock` broadcast at zero.
- [ ] Completion notification, sound, vibration, and cleanup of timer state.
- [ ] Runtime POST_NOTIFICATIONS request on first rest; denial path keeps the in-app timer.
- [ ] Replace/cancel behavior when a new set is logged, skipped, finished, or discarded.
- [ ] Airplane-mode and lock-screen checks.

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

- [ ] Home profile card: name, training since, total workouts, gender/age/weight/height, Settings button.
- [ ] Strength trend card: recent-exercise chips (most recent workout's exercises, max 4), 3-month e1RM chart, latest value, change, and a "View progress" link.
- [ ] 4-week analytics grid: workouts, working sets, volume, new PRs, deltas vs prior 4 weeks.
- [ ] Progress screen: exercise picker, 1M/3M/1Y/All selector defaulting to 3M, e1RM chart, best e1RM, heaviest set, sessions.
- [ ] Past workouts: date, template snapshot, best set, e1RM, PR badge, expandable sets, delete workout with confirmation.
- [ ] Empty states for no profile, no history, and no data in range.
- [ ] Chart accessibility: text summary, content descriptions, no color-only meaning.
- [ ] Fixture-based tests for analytics and PR badges.

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

- [ ] Settings screen: appearance, units, previous-set source, rest defaults/increment (complete Phase 4), data section, profile link.
- [ ] Export JSON via CreateDocument; include profile, settings, exercises, templates, template exercises, sessions, session exercises, and sets; exclude rest state (an active session cannot be exported).
- [ ] Import JSON via OpenDocument; validate version, fields, enums, ranges, IDs, references, timestamps; show counts and export date.
- [ ] Replace-all confirmation; Room transaction; then profile/settings; mark seed complete; clear rest state; reload flows.
- [ ] Block export/import while a session is active.
- [ ] Specific errors for not-a-backup, newer version, corrupt file, and write failure.
- [ ] Round-trip and failure tests.

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

- [ ] Compare all five screens plus editors with the designs in dark and light themes; fix spacing, type scale, and color drift.
- [ ] Font scaling, long names, empty states, contrast, and 48dp touch targets.
- [ ] Full E2E checklist from section 4.6 on the phone.
- [ ] Release keystore outside the repo; `keystore.properties` gitignored; signing config fails clearly when absent.
- [ ] `allowBackup="false"`, no INTERNET permission, R8 minify and resource shrink; verify release build, not only debug.
- [ ] versionCode 1 / versionName 1.0.0; build signed APK; install on the phone; test in airplane mode.
- [ ] Back up the keystore and credentials in two places; record the update steps.
- [ ] Share the APK with one friend; confirm install and launch.

**Acceptance checks**

- Signed release APK installs on the test phone and one other Android device.
- App runs fully in airplane mode; no network permission.
- All Phase 3-6 acceptance checks pass on the release build.
- Data survives installing a higher-versionCode release over the previous release.
- Designs match at phone size; no clipped text at 1.3× font scale.
- Keystore and `keystore.properties` are outside git; `git status` is clean of secrets.

### 5.1 Traceability

| Requirement | Planned work | Acceptance evidence |
|---|---|---|
| Offline, local, no accounts | Phases 1, 7 | Fresh install in airplane mode; no INTERNET permission |
| Profile fields | Phases 2, 5, 6 | Profile persists, appears on Home, survives export/import |
| Seeded and custom exercises | Phase 2 | Search, create, edit, archive, delete rules pass |
| Premade and custom templates | Phase 2 | 6 seeds visible; custom create/edit/duplicate/delete pass |
| Sets, rep range, target RIR | Phases 1, 2 | Editor validation and saved values pass |
| Exercise order in session | Phase 3 | Session snapshot keeps order after template edits |
| Weight, reps, RIR, set type | Phase 3 | Logged list, edit, retype, warmup exclusion pass |
| Rest timer and defaults | Phase 4 | Lock-screen alert within ±2s; skip/cancel pass |
| Per-set double progression | Phases 1, 3 | 10/9/8 fixture yields 82.5×6, 80×10, 80×9 |
| Five designs | Phases 2, 3, 5, 6, 7 | Side-by-side visual check dark and light |
| Dashboard, e1RM, PRs, history | Phase 5 | Hand-calculated fixture matches |
| Units, theme, previous source, rest settings | Phases 4, 6 | Changes apply live and persist |
| Export/import | Phase 6 | Round trip on phone; invalid files rejected |
| Signed APK distribution | Phase 7 | Release APK installs on phone and a friend's device |
| Physical-phone validation | Phases 0, 7 | ADB device listed; full E2E checklist passes |

## 6. Dependencies, remaining uncertainties, and next step

### Dependencies and parallel work

- Critical path: Phase 0 -> 1 -> 3 -> 5 -> 6 -> 7.
- Phase 2 can run parallel with Phase 3 after Phase 1. They share entities, repositories, theme components, and the Settings entry point.
- Phase 4 timer infrastructure can start after Phase 1; wire it to set logging in Phase 3. It shares Settings with Phase 6, so land the rest section once.
- Phase 5 depends on Phase 3 data and Phase 1 analytics. It can start with fixtures before the timer is finished.
- Shared contracts to freeze before parallel work: entity/DAO signatures, `SessionExercise` snapshot fields, `ProgressionEngine` input/output, DataStore keys, and theme component APIs.

### Remaining uncertainties and assumptions

**Blocking environment items**

None. Temurin JDK 21 is installed and `JAVA_HOME` points at it.

**Product assumptions to confirm during review, none blocks Phase 0**

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

**Technical risks**

- OEM battery management may delay or suppress the alarm on some phones. Phase 4 tests this; foreground service is the fallback.
- Per-set progression can produce different weights across sets in one session. This is intentional, but confirm it feels right after the first real workout.
- Room schema changes after v1 need migrations. Export before installing a schema-changing update.
- Losing the release keystore forces friends to uninstall and lose local data unless they export first.

### Next step

Start Phase 1: Room entities, DAOs, DataStore, seed content, domain functions, and their tests. The Phase 0 shell is installed and running on the phone.

This plan is ready for implementation. It does not authorize implementation; Phase 0 starts only after you approve.
