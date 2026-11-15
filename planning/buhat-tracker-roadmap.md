# Buhat Roadmap

## 1. Status, goal, and success criteria

**Status:** Phases 0-20 are complete except the Phase 14 manual checks 10-13 and the Phase 14 visual pass, which remain. Physical-phone process-kill and release checks are part of Phase 22. The previous baseline shipped as `versionCode 3`, `versionName 1.0.1`. Phase 14 automated validation passes, the signed `versionCode 4` / `versionName 1.1.0` build is verified, and it installed over v1.0.1 on SM-S711W with data intact. Baseline recorded on 2026-11-08: 101 unit tests and 181 device tests, 0 failed. Latest recorded on 2026-11-15: 133 unit tests and 256 device tests, 0 failed. Rerun both suites before and after each phase. Phases 21-22 are planned and not started.

**Goal:** A private, offline Android strength log for you and your friends. Each install owns its data. You train from premade or custom templates, log every set with weight, reps, RIR, and type, get per-set double-progression targets, run a rest timer, and review e1RM progress. Any exercise can show and accept its own weight unit and its own working rest, and exercises can be reordered during an active workout. Each exercise can carry a persistent note and a unilateral flag. Exercises can be added, edited, replaced, or removed during a workout, with an optional template save-back at finish. Data moves by JSON export/import, and Strong CSV exports can be imported. Progress can show whole workouts. Delivery is a signed APK.

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
11. Exercises can be reordered inside an active workout. The new order survives process death and appears in the finished workout. The template changes only when the finish prompt chooses Update template.
12. Each exercise has one persistent, editable note that follows it into future workouts.
13. Exercises can be added, edited, replaced, or removed during a workout. Completed sets stay under the exercise that was actually performed.
14. Finishing a workout with structural changes asks whether to update the template. Updating a premade creates an editable custom copy. Choosing "This workout only" leaves the template unchanged.
15. A unilateral exercise records Left and Right independently for weight, reps, RIR, and progression targets. Rest starts after each side. Each side counts as one set.
16. Progress offers Exercise progress and Workout history modes. Workout history shows complete sessions without choosing an exercise first.
17. Strong-exported workouts can be imported from a CSV file with preview, duplicate skipping, exercise mapping, RPE-to-RIR conversion, and notes.
18. Imported Strong exercise notes stay attached to their original workouts and are visible in history.
19. Converted exercise weights display with at most two decimal places. Storage keeps canonical kilograms. The existing one-decimal pound display is unchanged.
20. Schema 3 installs migrate to schema 4 without data loss. Backups from formats 1 and 2 still restore. New data survives backup round trips.

## 2. Scope

**Included**

- Native Android app, offline only, one local database per install.
- Profile: name, gender, age, weight, height. Editable, optional fields.
- Exercise library: seeded common exercises plus custom exercises with name, muscle group, and equipment.
- Templates: 6 read-only premade templates, custom create/edit/duplicate/delete, exercise order, working-set count, rep range, optional target RIR.
- Active workout: start from template, exercise order, per-set logging (weight, reps, RIR, type), set edit/delete, add set, finish/discard, resume after process death, elapsed session time.
- Active-workout exercise reordering through an "Edit order" dialog. Session only by default; the finish prompt can save structural changes to the template.
- Per-exercise weight unit for premade and custom exercises. Nullable override with the global kg/lb setting as fallback. Display and input only; storage stays kilograms.
- Per-exercise working-rest override. Nullable seconds value with the global working rest as fallback. Warmup and failure rests stay global.
- Persistent exercise note, editable from the library and active workout, with no snapshot into sessions.
- Mid-workout add, edit, replace, and remove for session exercises, with a finish-time choice between this workout only and updating the template.
- Unilateral Left/Right tracking with independent weight, reps, RIR, and progression targets.
- Progress modes: Exercise progress and Workout history.
- Strong CSV import with preview, duplicate skipping, exercise mapping, RPE-to-RIR conversion, and imported notes.
- Weight display with at most two decimal places after a unit conversion.
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
- Reordering a finished workout. Template changes from the active session happen only through the explicit finish prompt.
- Exercise-specific units for aggregate volume, body weight, height, or other whole-app numbers.
- Snapshotting unit or rest into historical sessions. History renders with the exercise's current preference and falls back to the global setting when the exercise no longer exists.
- Localization beyond English.
- App lock, encryption at rest, cloud auto-backup.
- Automatic folder export.
- Encrypted backup files.
- Network crash reporting or analytics.
- Importers for other apps. Strong is the only external source in this roadmap.
- Editable notes on native workouts. Imported Strong workout notes are preserved read-only.
- Generating templates from imported workouts. The export contains performed sets, not prescriptions.
- Replacing previously imported workouts when Strong data changes. Repeat imports add new workouts only.

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
| Active-workout reorder | "Edit order" dialog with up/down buttons; session only by default | User-confirmed | Mirrors the template editor. No drag-and-drop. Template changes only through the finish prompt |
| Preference compatibility | Room schema migrations through 4; backup format 3; import accepts v1, v2, and v3 | User-confirmed | Existing installs and old backups must keep working |
| Premade editability | Premade rows allow only preference fields: unit, rest, note, unilateral. Name, muscle group, equipment stay read-only | Derived from user-confirmed entry point | Preserves the read-only content rule |
| Analytics | Epley e1RM. Warmups excluded. Volume = weight × reps for working/failure | Assumption | Standard, simple, matches design intent |
| Progress ranges | 1M = 30 days, 3M = 90 days, 1Y = 365 days, All = no cutoff | User-confirmed | Windows include today |
| App identity | App name "Buhat", package `com.buhat.tracker` | User-confirmed | Launcher label only. Package stays `com.buhat.tracker` |
| Android range | minSdk 26, compile/target 35 | Assumption | Covers Android 8+ friends. SDK 35 is installed |
| Bodyweight moves | Log external weight only. e1RM shown only when weight > 0 | Assumption | Avoids body-weight history in v1. Reps still tracked |
| Android auto-backup | Disabled. Explicit export is the backup path | Assumption | Keeps profile data off cloud services |
| Build versions | Gradle 8.11.1, AGP 8.7.3, Kotlin 2.1.0, Compose BOM 2025.01.00, Room 2.7.1 | Assumption | Known-good baseline. Verify and adjust at scaffold time |

**Workout flexibility and Strong import decisions (Phases 15-22)**

| Area | Decision | Basis | Reason |
|---|---|---|---|
| Finish prompt | Ask at Finish whether changes apply only to this workout or update the template | User-confirmed | User specified the timing and choices |
| Replacement sets | Completed sets stay under the original exercise. Later sets go to the replacement | User-confirmed | Preserve what was actually performed |
| Premade update | Updating a premade template creates an editable custom copy | User-confirmed | Reuse custom-template behavior |
| Exercise note | One persistent, editable note per exercise | User-confirmed | The note follows the exercise into future workouts |
| Unilateral data | Independent Left and Right weight, reps, RIR, and progression targets | User-confirmed | Distinguish performance on each side |
| Unilateral rest | Rest starts after every side, not after a pair | User-confirmed | User selected separate rest periods |
| Unilateral sets | Each side counts as one set. Three Left plus three Right equals six sets | User-confirmed | Both sides contribute to workload totals |
| Import source | Initial external source is Strong, using its exported CSV | User-confirmed | User supplied an actual export |
| RPE conversion | Convert imported Strong RPE to whole-number RIR | User-confirmed | Accept approximation for half-step ratings |
| Imported notes | Keep imported Strong exercise notes attached to their original workouts | User-confirmed | Preserve the date and context of each note |
| Unmatched names | Review unmatched Strong exercise names and map them or create custom exercises | User-confirmed | User controls which exercise histories are combined |
| History entry point | Put whole-workout history under Progress, with Exercise progress and Workout history modes | User-confirmed | User selected Progress as the entry point |
| Old unsided logs | Start fresh side-specific targets when older logs have no side labels | User-confirmed | Older weights may represent both sides combined |
| Repeat import | Repeat imports add new workouts only and skip workouts already imported | User-confirmed | Simple, predictable repeat imports |
| Strong dates | Interpret Strong export dates as local time on the importing device | User-confirmed | Common case, no extra import step |
| Side order | Alternate sides set by set during a unilateral exercise | User-confirmed | Keeps fatigue comparable between sides |
| Data versioning | Room schema 4 and backup format 3. Backup import accepts formats 1, 2, and 3 | Derived | New fields need storage and old backups must keep restoring |
| Side storage | Store `side` on each set; compute per-side progression positions in domain code | Derived | Keeps the existing unique working-index index and chronological renumbering |
| Note cap | Notes are capped at 2,000 characters. Blank input stores null | Derived | Bounds backup and UI size without truncating data silently |

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
6. Finish saves the session. If the session structure changed (add, edit, replace, remove, or reorder), Finish first asks "Update template" or "This workout only". Discard deletes the session after confirmation. Only one active session exists.

**Configure an exercise**

1. Workouts > Exercise library > row menu > Exercise settings. The action exists for premade and custom rows.
2. Unit offers Default (shows the current global unit), kg, and lb. Working rest offers Default (shows the global working rest) and a value from 0 to 600 seconds in 15-second steps. Note is a free-text field capped at 2,000 characters. Unilateral is a switch.
3. Save. Preferences persist immediately and apply to the next set, the target card, the rest timer, and the active exercise card. Logged sets keep their stored kilograms and side.
4. The same dialog opens from the Active Workout target card for the selected exercise.

**Reorder during a workout**

1. Active workout overflow > Edit exercise order.
2. The dialog lists the session's exercises with up/down buttons. Each move saves; the dialog stays open.
3. The template is not changed immediately. Logged sets stay attached to their exercise. The selected exercise and any running rest are unaffected.
4. Finishing stores the final order; if the structure changed, Finish asks whether to update the template. Past workouts show the final order.

**Change exercises during a workout**

1. Active workout overflow > Add exercise. The exercise picker and prescription fields reuse the template editor.
2. The selected exercise offers Edit, Replace, and Remove. Edit changes sets, rep range, or target RIR for this workout. Replace keeps completed sets under the original exercise and attaches later sets to the replacement.
3. Remove is available only when the exercise has no logged sets.
4. Finish asks "Update template" or "This workout only". Updating a custom template replaces its exercises and order. Updating a premade creates a uniquely named editable copy.

**Track a unilateral exercise**

1. Turn on Unilateral in Exercise settings. The logging card shows a Left/Right selector.
2. The default side alternates from the last logged side for that exercise, starting with Left. The side can be changed before saving.
3. Logging a side starts rest. Each side is its own set, target, and progression position.
4. Exercise progress and Home show separate Left and Right data for that exercise.

**Review progress**

1. Home shows the profile, a 3-month e1RM trend for a selected exercise, and 4-week analytics with deltas against the prior 4 weeks.
2. Progress offers Exercise progress and Workout history modes. Exercise progress lets the user pick any exercise, switch 1M/3M/1Y/All, see the e1RM chart and all-time stats, and open past workouts with PR badges and expandable sets. Workout history lists complete finished sessions newest first without choosing an exercise.
3. A finished workout can be deleted with confirmation from either mode; derived analytics recalculate.

**Back up and restore**

1. Settings > Export data. Pick a location. JSON contains profile, settings, exercises, templates, sessions, and sets; it excludes an active session.
2. Settings > Import data. Pick a file. The app validates everything, shows counts and export date, and requires "Replace all local data".
3. Validation runs before any write. Room data replaces in one transaction; profile and settings are written after it. A validation failure leaves existing data untouched. A write failure is reported and the same file can be retried.
4. Export and import are blocked while a workout is active.

**Import from Strong**

1. Settings > Import from Strong. Pick a Strong CSV export.
2. The preview shows new workouts, skipped duplicates, date range, set counts, and unsupported rows. Unmatched exercise names must be mapped to an existing exercise or given a new custom exercise.
3. Confirm writes all new exercises and workouts in one transaction. Reimporting the same file reports zero new workouts.

### 4.2 Data model

All IDs are UUID strings. Timestamps are epoch milliseconds UTC. Weights are kilograms.

| Entity | Fields | Notes |
|---|---|---|
| `Exercise` | `id`, `name`, `muscleGroup`, `equipment`, `isCustom`, `archivedAt`, `createdAt`, `weightUnitOverride`, `restWorkingSecondsOverride`, `note`, `isUnilateral` | Name unique case-insensitive. Seeded and custom share one table. Overrides are nullable; null means use the global setting. Premade rows may change only the preference fields: unit, rest, note, and unilateral. |
| `Template` | `id`, `name`, `isPremade`, `createdAt`, `updatedAt`, `archivedAt` | Premade rows are read-only. Custom rows editable. |
| `TemplateExercise` | `id`, `templateId`, `exerciseId`, `position`, `workingSets`, `repMin`, `repMax`, `targetRir` | Unique positions and unique exercise per template. Cascade on template delete. |
| `WorkoutSession` | `id`, `templateIdSnapshot`, `templateNameSnapshot`, `startedAt`, `finishedAt`, `status`, `note`, `importKey`, `structureDirty` | `status`: ACTIVE, FINISHED. Template ID is a string snapshot, not an FK. Imported sessions use `importKey`; native sessions leave it null. `structureDirty` drives the finish prompt. |
| `SessionExercise` | `id`, `sessionId`, `exerciseId`, `exerciseNameSnapshot`, `position`, `workingSets`, `repMin`, `repMax`, `targetRir`, `note`, `replacesSessionExerciseId` | Snapshot of the template at start. `note` is an imported historical note. `replacesSessionExerciseId` links a replacement row to the row it replaced. Cascade on session delete. |
| `LoggedSet` | `id`, `sessionExerciseId`, `setType`, `workingIndex`, `weightKg`, `reps`, `rir`, `completedAt`, `side` | `workingIndex` is 1-based among working/failure sets; null for warmups. `side` is LEFT, RIGHT, or null for bilateral, unsided legacy, or imported sets. |

`weightUnitOverride` stores `"KG"`, `"LB"`, or null. `restWorkingSecondsOverride` stores 0..600 or null. Null means "use global". These are preferences, not content: premade rows may change unit, rest, note, and unilateral preferences while name, muscle group, and equipment stay read-only. Preferences are not snapshotted into `SessionExercise`. Logging, targets, history, and charts resolve the exercise's current preference and fall back to the global setting when the exercise row no longer exists.

`Exercise.note` is nullable and capped at 2,000 characters. It follows the exercise into every future workout and is not copied into sessions. `Exercise.isUnilateral` defaults to false. A unilateral exercise's sets are logged one side at a time.

`WorkoutSession.importKey` identifies an imported workout and is null for native sessions. `structureDirty` records add, edit, replace, remove, or reorder inside an active workout and persists across process death. `WorkoutSession.note` holds an imported Strong workout note and is null for native sessions. `SessionExercise.note` holds an imported historical note for that exercise occurrence. `LoggedSet.side` is `LEFT`, `RIGHT`, or null.

Room schema version becomes 4. Migration 2->3 added the two override columns; migration 3->4 adds the note, unilateral, import, dirty, replacement, and side fields plus the unique import-key index. Backup format becomes 3 and adds the same fields. Import accepts formats 1, 2, and 3; formats 1 and 2 default the new fields.

`Profile` and `Settings` live in DataStore: name, gender, age, weightKg, heightCm; theme, unit, previous-set source, rest seconds per type, rest increment, rest timer end state.

Indices and uniqueness: `LoggedSet(sessionExerciseId)`, unique `LoggedSet(sessionExerciseId, workingIndex)` where `workingIndex` is not null, unique `SessionExercise(sessionId, position)`, unique `TemplateExercise(templateId, position)` and `(templateId, exerciseId)`, `WorkoutSession(status)`, `WorkoutSession(finishedAt)`, unique `WorkoutSession(importKey)` where `importKey` is not null. SQLite allows multiple null import keys. Foreign keys are enforced. Room schema JSON is committed to the repo for future migrations.

### 4.3 Progression, analytics, and units

**Working-set position**

- Warmups are ignored for progression and analytics.
- Bilateral working and failure sets are numbered 1..M in completion order for each exercise in a session.
- A unilateral exercise is numbered per side. Filter its working/failure sets by `side`, then number each side 1..M in completion order. The stored `workingIndex` remains the completion-order index for that session exercise row; per-side positions are computed in domain code.
- Each position P has its own history. Position 1 never borrows from position 2. An unsided set never supplies a side-specific position.

**Source session**

- "Any workout": the most recent FINISHED session containing the exercise.
- "Same template": the most recent FINISHED session whose `templateIdSnapshot` matches the active session's template snapshot. Falls back to Any workout if none.
- Within the source session, flatten all occurrences of the exercise in row order, then filter to the requested side. Bilateral target lookup uses only unsided sets. A unilateral lookup uses only the matching side.
- Within that filtered list, use the working/failure set at position P. If P does not exist, use the last working/failure set. If none exists, this is a first-time exercise for that side.

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
- For a unilateral exercise, P is the position for the side about to be logged. The target card and prefill show that side's history.
- If all M positions are logged, show "All prescribed sets logged" and allow Add set. The added position is M+1 and gets its own history.
- This per-position rule changes the Active design's exercise-level "beat your lowest set" copy into a target for the current position. The layout stays the same; the comparison is like-for-like by set number. The target-versus-prefill difference is confirmed for v1.

**Estimated 1RM**

- Epley: `weightKg × (1 + reps / 30)`. Only working/failure sets with `weightKg > 0` and `reps > 0`.
- Chart point per finished session and selected side: maximum e1RM for the selected exercise in that session.
- Latest value = last point. Change = latest minus first point in the selected range, plus percent.
- Progress ranges: 1M = 30 days, 3M = 90 days, 1Y = 365 days, All = no cutoff. Windows include today.
- Heaviest set = highest `weightKg`, tie-break by reps.
- Left and Right are separate series. Unsided sets are excluded when a side is selected.

**PR rule**

- Walk finished sessions chronologically per exercise and side. A working/failure set is a PR when its e1RM is strictly greater than every earlier e1RM for that exercise and side. Ties are not PRs. Bilateral sets compare only with unsided sets.
- A session gets a PR badge if it contains at least one PR set for the selected exercise and side.
- "New PRs" in the 4-week card = count of PR sets in the window. This is an assumption; change to distinct exercises if the number feels inflated.

**4-week analytics**

- Current window: last 28 days including today. Prior window: the 28 days before that.
- Workouts: distinct FINISHED sessions with at least one working/failure set.
- Working sets: count each working/failure row. Warmups excluded. Each logged unilateral side counts as one set.
- Volume: sum of `weightKg × reps` over working/failure rows. Warmups excluded. Left and Right each contribute their own weight times reps with no pair multiplier.
- Show percent deltas where meaningful; otherwise "vs prior 4".

**Units**

- Store kilograms. Every exercise has an effective unit: its `weightUnitOverride`, else the global Settings unit.
- Display pounds as `kg × 2.20462`, rounded to 0.1 lb. Display kilograms rounded half-up to at most two decimal places, then strip trailing zeros.
- The two-decimal cap applies after any conversion, so switching an exercise between kg and lb never shows long floating-point tails. Stored kilograms stay exact for progression, prefills, and editing.
- Weight steps follow the effective unit: 2.5 kg for kg, 5 lb for lb. The lb step is stored as `lbToKg(5.0)` (2.26796... kg) so repeated display conversions stay at whole pounds. The top-of-range bump uses the same step.
- Use the effective unit for every exercise-specific value: Active Workout target, prefill, logged rows, weight input and steps, set editor, past workouts, the exercise's Progress chart and stats, and the Home strength-trend card for that exercise.
- Keep the global unit for whole-app values: body weight, height, 4-week volume, Settings copy, and any aggregate.
- When an exercise row is missing (deleted custom exercise), fall back to the global unit for its history.
- Height stays in centimeters in both unit modes. Age is an integer.
- RIR is an integer 0..5. Set types are Warmup, Working, Failure.

**Unilateral tracking**

- `Exercise.isUnilateral` is a preference switch, available for premade and custom rows. It applies from the next logged set.
- The logging card offers Left and Right. The default is the opposite of the most recent logged side for this exercise, or Left when none exists. The user can change it before saving.
- Rest runs after every logged set, including each side. `logSet` already starts rest after each set; no pair branch is added.
- Template working sets are per side for a unilateral exercise. Three prescribed sets means three Left and three Right.
- Switching an exercise to unilateral with only old unsided sets seeds neither side. Old unsided sets keep their volume and set counts and remain valid display rows.
- Switching back to bilateral ignores side-specific sets for targets. Side history remains in storage and in workout history.
- History and whole-workout history show L/R labels per set. Side-specific set numbers remain visible.

**Per-exercise rest**

- Resolve rest by set type. Warmup uses `restWarmupSeconds`. Failure uses `restFailureSeconds`. Working uses `restWorkingSecondsOverride` when set, else `restWorkingSeconds`.
- An override of 0 means no rest for that exercise's working sets.
- Validate overrides at 0..600 seconds. The editor uses the existing rest increment (default 15 s).
- Changing an override while a rest is running does not change the current `restEndsAt`. The next logged set uses the new value.

### 4.4 Technical approach

**Current code map (read first)**

- `app/src/main/java/com/buhat/tracker/data/local/Entities.kt`: Room entities. Phase 16 adds `Exercise.note`, `Exercise.isUnilateral`, `WorkoutSession.note`, `WorkoutSession.importKey`, `WorkoutSession.structureDirty`, `SessionExercise.note`, `SessionExercise.replacesSessionExerciseId`, and `LoggedSet.side`. `SessionExercise.position` is the active-session order.
- `BuhatDao.kt`: queries. `observeSessionExercises` is ordered by position. Add a session-exercises suspend read and position updates for reordering, an all-exercises flow that includes archived rows for preference lookup, an import-key lookup, and a whole-workout history read.
- `BuhatDatabase.kt`: currently version 3 with migrations 1->2 and 2->3. Add version 4 and `MIGRATION_3_4`.
- `data/BuhatRepository.kt`: `startSession`, `logSet` (computes rest from type), `updateSet`, `deleteSet`, finish/discard, export/import, and `sessionWriteMutex`. New work adds add/edit/replace/remove session exercises, the finish prompt's transactional template save-back, side-aware logging, and Strong import preview/append. Per-exercise rest resolution belongs in `logSet`.
- `data/SettingsStore.kt`: global `unit`, rest defaults, `restEndsAt`, and rest timer persistence. No new keys are needed.
- `data/Backup.kt`: DTOs, `BACKUP_FORMAT_VERSION = 3`, strict decode and validation. Extend all affected DTOs and keep formats 1 and 2 importable.
- `domain/Units.kt`: kg/lb conversion and formatting. Add the effective-unit helper here and cap kg display at two decimals.
- `domain/Progression.kt`: `nextTarget`, `WEIGHT_INCREMENT_KG`, `formatKg`, and `sourceSetForPosition`. Make the increment unit-aware, cap `formatKg`, and make source lookup side-aware and occurrence-safe.
- `domain/RestTimer.kt`: `restSecondsFor`. Add the exercise working-rest override.
- `domain/SessionRules.kt`: prefill, working-index renumbering, set validation. Add side selection and per-side prefill.
- `domain/Analytics.kt`: set counts, volume, PRs, e1RM, and trends. Add a side dimension to exercise-specific analytics.
- `ui/screens/ActiveWorkoutScreen.kt`: selected exercise, target card, rest card, logged rows, logging card, overflow menu. Add add/edit/replace/remove actions, side selection, and the finish dialog.
- `ui/screens/ExerciseLibraryScreen.kt`: exercise rows and menus. `ExerciseEditorScreen.kt`: custom-only editor. Extend `ExerciseSettingsDialog` with note and unilateral controls.
- `ui/screens/ProgressScreen.kt`: add Exercise progress and Workout history modes. Its existing history card uses `first { exerciseId }`, so repeated occurrences of one exercise in a session need explicit handling.
- `ui/screens/HomeScreen.kt`: per-exercise e1RM/stat display, global volume, and a Left/Right selector for unilateral exercises.
- `ui/BuhatApp.kt`: Navigation Compose routes. Add the Strong import route.
- `app/schemas/com.buhat.tracker.data.local.BuhatDatabase/`: committed Room schemas 1-3. Commit 4 after the migration.
- Tests: `app/src/test/java/com/buhat/tracker/` (unit), `app/src/androidTest/java/com/buhat/tracker/` (device, including `MigrationTest.kt`).
- Commands: `.\gradlew.bat testDebugUnitTest --console=plain` and `.\gradlew.bat pixel6api35DebugAndroidTest --rerun`. Follow `.opencode/skills/e2e-testing/SKILL.md` for the full suite. Release build: `.\gradlew.bat assembleRelease`; signing requires an external `keystore.properties`.
- No new dependency is needed. Use the existing Compose dialog, segmented control, text button, and stepper components. Parse Strong CSV with a small in-repo parser.

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
- Active: header, elapsed pill, exercise chips, target card, rest card, logged sets, logging card. Overflow: Finish workout, Discard workout, Edit exercise order, Add exercise. Selected exercise: Edit, Replace, Remove, Exercise settings. Logging card: side selector for unilateral exercises.
- Progress: Exercise progress and Workout history modes. Exercise progress keeps the exercise picker, range segmented control, e1RM card, three stat tiles, past workouts with expand and delete, and a Left/Right selector for unilateral exercises. Workout history lists complete finished sessions.
- Settings: appearance, global units, previous set, rest timer defaults and increment, data export/import, Import from Strong, profile link. Per-exercise unit, working rest, note, and unilateral switches live in Exercise settings.
- Full-screen editors: template, exercise, profile. Back arrow, no bottom nav.

**Active session persistence**

- Logged sets are written on tap, not on finish.
- The active session is a normal Room row. On launch, the Active tab shows it.
- Starting another session prompts Resume or Discard.
- Session elapsed time derives from `startedAt`, so it survives backgrounding.

**Per-exercise preferences**

- Phase 12 added the two nullable columns to `Exercise`. Add these helpers in `domain/Units.kt` and `domain/RestTimer.kt`:
  - `Exercise.effectiveUnit(global: WeightUnit): WeightUnit = weightUnitOverride?.let(WeightUnit::valueOf) ?: global`
  - `Exercise.effectiveWorkingRest(globalSeconds: Int): Int = restWorkingSecondsOverride ?: globalSeconds`
- `saveExercisePreferences(exerciseId, unitOverride, restWorkingSecondsOverride)` in Phase 12 updates only the two override columns. It validates rest at 0..600 and works for premade and custom rows. It never touches name, `nameLower`, muscle group, equipment, or the custom flag. Phase 17 extends it to the note and unilateral preference.
- Phase 17 extends this to `saveExercisePreferences(exerciseId, unitOverride, restWorkingSecondsOverride, note, isUnilateral)`. Note is capped at 2,000 characters and blank stores null. Unilateral is a boolean preference.
- Active Workout observes the selected exercise row by ID and derives `unit`. All existing `formatWeight`, `formatWeightValue`, `displayToKg`, step, target, and set-editor calls use that value. If the row is gone, use the global unit.
- `logSet` reads the session exercise's override in the same write path, then calls the extended rest resolver. Do not pass a rest value from the UI.
- Progress and Home add a small all-exercises flow that includes archived rows to resolve effective units; the existing `repository.exercises()` flow filters archived rows. Keep volume, profile, and height on `settings.unit`.
- The shared dialog is one small composable, opened from both entry points. It offers Unit: Default (current global), kg, lb. Working rest: Default (current global), or 0..600 with +/− using `restIncrementSeconds`, plus "Use default" to clear. Note is a multiline text field. Unilateral is a switch. Save calls the repository. Suggested tags: `exercise-settings`, `pref-unit-default|kg|lb`, `pref-rest-value|plus|minus|default`, `pref-note`, `pref-unilateral`, `save-exercise-prefs`.
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

**Workout notes and structure edits (Phases 17-18)**

- The persistent exercise note is read from the `Exercise` row in Active Workout and Exercise Library. It is never copied into `SessionExercise`. Imported historical notes live on `SessionExercise.note`.
- Add session structure operations to the repository, all inside `sessionWriteMutex` and one Room transaction:
  - **Add:** choose an exercise and prescriptions, insert at the end, mark `structureDirty`.
  - **Edit:** change sets, rep range, or target RIR, validate with the template rules, mark dirty. Logged sets and working indices are unchanged.
  - **Replace:** if the original has no logged sets, update it in place. Otherwise insert a new row immediately after it with `replacesSessionExerciseId` set, shift later positions, mark dirty, and keep the original row and its sets.
  - **Remove:** allowed only when the row has no logged sets. Delete it, clear links that point to it, renumber positions, mark dirty.
  - **Reorder:** the existing reorder operation also marks dirty.
- The finish flow reads `structureDirty`. When false, finish normally. When true, show a dialog with "Update template", "This workout only", and cancel.
- "This workout only" clears the dirty flag and finishes without touching the template.
- "Update template" builds a draft from the session rows in position order, excluding rows referenced by another row's `replacesSessionExerciseId`. It updates the custom template, recreates a missing custom template from the session name, or creates a custom copy of a premade with the existing unique-name pattern.
- Template save-back and session finish run in one transaction. A validation or write failure leaves the workout active and shows the error. The dirty flag persists across process death.
- Edits made to a template outside the active workout still do not change the session.

**Unilateral tracking (Phase 19)**

- Add `enum class SetSide { LEFT, RIGHT }` and a Room converter storing the enum name. Null means bilateral, unsided legacy, or imported.
- Side selection default: the opposite of the most recent logged side for the selected exercise in this session, else LEFT. The selector remains editable before logging.
- `workingIndex` continues to number all working/failure sets in completion order. Domain helpers compute per-side positions by filtering `side`, and source lookup flattens all occurrences of the exercise before filtering.
- `prefillFor` and the target card take the side about to be logged. A side switch re-renders the prefill target for that side.
- Analytics keys include the side. `exerciseStats`, `e1rmTrend`, `prSetIds`, and best-e1RM maps accept a side filter. Bilateral views use unsided sets only.
- Progress and Home add a Left/Right segmented control for unilateral exercises, defaulting to the side with the most recent set. Other exercises hide it.
- History rows show L/R labels. Set editing preserves the side and allows correcting it.
- Template and session prescriptions show "sets per side" for unilateral exercises.

**Whole-workout history (Phase 20)**

- `repository.history()` already returns complete FINISHED sessions. Add a mode enum in `ProgressScreen` and preserve the selected mode with `rememberSaveable`.
- Workout history cards show workout name, date, session note when present, exercises in position order, sets with side labels, and imported exercise notes.
- Replaced exercises appear as separate occurrences with their own sets. Do not collapse rows by exercise id.
- Reuse the existing unit resolution, weight formatting, PR badge, expandable sets, and delete-confirmation paths. Deleting from either mode removes the workout from both.
- Keep the existing exercise-filtered mode unchanged except for side labels and side filtering.

**Strong CSV import (Phase 21)**

- Add a small CSV parser in `data/` or `domain/` that handles quoted semicolons, escaped quotes, multiline fields, BOM, CRLF, and empty fields. Do not add a dependency.
- Parse into rows, group by `Workout #` while preserving order, and validate each group before mapping. Unsupported rows block the preview with row-level messages.
- Convert RPE with `10 - RPE`, half-up, clamped to the valid RIR range. Blank RPE stays null.
- Build a preview model: new workouts, skipped duplicate identities, date range, set counts by type, distinct source exercise names, and errors. Duplicate identity is `strong|<raw exported date-time>|<workout name>`; the raw date text avoids timezone-dependent identity changes.
- Exercise mapping: exact case-insensitive matches preselect. Seed names often differ from Strong names, for example `Seated Cable Row` versus `Seated Row (Cable)`, so unmatched names are expected. Unmatched names require mapping to an existing exercise or a new custom exercise with explicit name, muscle group, and equipment. Mapping two source names to one exercise is allowed; occurrences remain separate rows and exercise-specific views merge their sets. The exercise editor's defaults are not valid evidence for classifying imported exercises.
- Append only after preview succeeds. Missing custom exercises and all sessions/sets are inserted in one transaction. Use sentinel `templateIdSnapshot = "strong-import"` and the Strong workout name as `templateNameSnapshot`.
- Block import while a workout is active, matching backup import. Reuse the file-size guard and SAF picker.
- Add the route in `BuhatApp.kt` and "Import from Strong" in Settings below backup restore.

**Rest timer**

- On set log, compute `restEndsAt = now + resolved rest for the set's type and exercise` and persist it. If the duration is 0, do not start.
- In-app countdown ticks from `restEndsAt` using elapsed-realtime math, so it does not drift.
- Background alert: post an ongoing notification with a native countdown chronometer and schedule an exact `AlarmManager.setAlarmClock` broadcast at `restEndsAt`. The broadcast posts a completion notification with sound and vibration, cancels the ongoing notification, and clears timer state.
- Use `setAlarmClock` for the exact background alert. Android 13+ uses `USE_EXACT_ALARM` for this direct-APK app. Android 12/12L needs `SCHEDULE_EXACT_ALARM`; the app opens the system access screen, while the in-app countdown remains available if access is denied. The alarm icon appears during rest. If device testing shows unreliable delivery or the icon is unacceptable, replace with a foreground service (`specialUse` on Android 14+) behind the same timer interface. This is the Phase 4 decision gate.
- Skip, finish, discard, and starting a new rest cancel the alarm and notification.
- Notification permission is requested when the first rest starts. If denied, the in-app timer still works and a one-time hint explains that background alerts need permission.
- On app restart, read `restEndsAt`; if future, show the countdown, otherwise clear it. A reboot loses the alarm; opening the app clears stale state. Acceptable for v1.

**Export/import**

- Format version 3. Import accepts versions 1, 2, and 3. Formats 1 and 2 are patched with the new keys before JSON type validation. Root: `formatVersion`, `appVersion`, `exportedAt`, `profile`, `settings`, `exercises`, `templates`, `templateExercises`, `sessions`, `sessionExercises`, `sets`. Exercises carry `weightUnitOverride`, `restWorkingSecondsOverride`, `note`, and `isUnilateral`. Sessions carry `note` and `importKey`. Session exercises carry `note` and `replacesSessionExerciseId`. Sets carry `side`. `structureDirty` is omitted because exported sessions are finished and the flag is always false.
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

- **Template changes during a session:** session snapshots exercises and targets at start. Editing or deleting the template outside the workout does not change the active session or history. In-workout add, edit, replace, remove, or reorder changes the session immediately and reaches the template only through the finish prompt.
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
- **Replace before any logged sets:** the session exercise is updated in place. No replacement link or extra history row is created.
- **Replace after sets:** completed sets stay under the original row. The replacement row holds later sets and links back through `replacesSessionExerciseId`. Template save-back excludes the replaced row.
- **Remove with sets:** blocked with a clear message. Remove without sets deletes the row, clears links pointing at it, and renumbers positions.
- **Finish with a deleted template:** if a custom template was deleted elsewhere, "Update template" recreates it from the session name using the unique-name pattern.
- **Failed template save-back:** the transaction rolls back, the workout stays active, and the error is shown.
- **Dirty session after process death:** `structureDirty` is persisted, so the finish prompt still appears.
- **Note cleared:** blank input stores null and the note disappears from the active card. It is not inherited from history.
- **Note too long:** 2,000 characters accepted. Longer input and imported notes block save or preview instead of truncating.
- **Unilateral with old unsided logs:** unsided sets keep their volume and set counts but seed neither Left nor Right targets.
- **Unilateral side switch mid-exercise:** the next logged set uses the selected side. Earlier sets keep their side. Rest is unaffected.
- **Unilateral failure and warmup:** rest still follows the global type defaults. Only working rest uses the per-exercise override.
- **Unilateral off after side history:** side sets remain in history and whole-workout history. Bilateral targets use only unsided sets.
- **Repeated exercise in one workout:** history and workout history show each occurrence separately. Exercise-specific progress merges sets across occurrences.
- **Imported workout identity:** the sentinel template id is not a real template. Repeat imports skip the same raw date-time plus workout name even after a timezone change.
- **Unsupported Strong row:** the preview shows the row and writes nothing. There are no partial imports.
- **Old backup import:** formats 1 and 2 default notes and sides to null, replacement links to null, import keys to null, and `isUnilateral` to false.
- **New backup on an old app:** older builds reject format 3 as newer. Export before installing the update.
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
- Unit tests for kg display: two-decimal half-up rounding, trailing-zero stripping, zero, long converted values, and pound formatting unchanged.
- Unit tests for side positions and source selection: per-side numbering, unsided sets excluded, side switch, fallback position, repeated exercise occurrences.
- Unit tests for side-aware analytics: PR keys include side, e1RM series separate, set count and volume add each side once.
- Unit tests for note validation: 2,000 accepted, 2,001 rejected, blank stores null.
- Unit tests for template draft exclusion: replaced rows excluded, replacement rows included, order preserved, missing template recreated, premade copied.
- Unit tests for the finish dirty flag: add, edit, replace, remove, and reorder set it; set logging and notes do not.
- Unit tests for the Strong parser: quoted semicolons, escaped quotes, multiline notes, BOM, CRLF, empty fields, and malformed rows.
- Unit tests for Strong mapping: RPE-to-RIR table, blank RPE null, failure does not add a rep, rest rows ignored, workout grouping, duplicate identity, date parsing.
- Backup tests: format 1 and 2 import defaults; format 3 round trip; invalid side, duplicate import key, overlong note, and dangling replacement link rejected.
- Room migration tests: 3->4 keeps rows and sets defaults; 1->4 and 2->4 chains work; commit schema 4 JSON.
- Room in-memory tests for DAO constraints, cascade deletes, working-index uniqueness, import-key uniqueness, active-session queries.
- Device tests: shared dialog from Library and Active, premade note and preferences allowed with content unchanged, lb logging and target, rest override starts the right countdown, reorder persists across process death and appears in history, template unchanged for This workout only, add/edit/replace/remove flows, finish prompt branches, unilateral logging and rest, history modes, Strong preview and rollback.
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
12. Reorder exercises mid-session. Finish and choose "This workout only". Verify the finished workout order and that the template order is unchanged.
13. Install over the current v1.0.1 release. Confirm exercises, templates, sessions, and preferences survive. Import a format-1 backup and confirm overrides default to global.
14. Set a note on a premade exercise, start a workout, verify the note appears, edit and clear it, restart, and confirm the final value.
15. Add, edit, replace, and remove exercises mid-workout. Finish with "This workout only" and confirm the template is unchanged. Repeat with "Update template" on a custom template and on a premade, and confirm the custom copy behavior.
16. Mark an exercise unilateral. Log three Left and three Right sets. Confirm rest after each side, six working sets, separate targets, L/R labels, and separate Progress data.
17. Open Workout history, open a session without using the exercise picker, verify mixed units and repeated exercise occurrences, and delete it from both modes.
18. Import a Strong CSV. Verify preview counts, mapping, notes, RPE conversion, and unsided sets. Reimport and confirm zero new workouts. Try an unsupported row and confirm nothing is written.
19. Enter 225 lb in an exercise, switch its unit to kg, and switch back. Confirm the display shows at most two decimals and returns to 225 lb without changing the stored kilogram value.

**Visual**

- Compare each of the five designs at the test phone's resolution in dark and light themes.
- Check 1.3× font scaling, long exercise/template names, empty states, and one-handed logging.
- Check the note field, unilateral switch, finish prompt, add/edit/replace/remove dialogs, Progress mode control, side selector, and Strong import preview at 1.0× and 1.3× font scale.

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

Equipment values: Barbell, Dumbbell, Cable, Machine, Bodyweight, Other. Muscle group values: Chest, Back, Shoulders, Biceps, Triceps, Quads, Hamstrings, Glutes, Calves, Core. All seed rows are `isCustom = false`, have null unit/rest overrides, null notes, and `isUnilateral = false`. Users can create custom exercises. Premade names, muscle groups, and equipment stay read-only; only the preference fields (unit, working rest, note, unilateral) are editable. No seed version bump is needed.

### 4.8 Schema, backup, and Strong import details (Phases 15-22)

**Schema 4 migration**

Room schema becomes 4. All new fields are nullable or have defaults. Migration 3->4:

```sql
ALTER TABLE Exercise ADD COLUMN note TEXT;
ALTER TABLE Exercise ADD COLUMN isUnilateral INTEGER NOT NULL DEFAULT 0;
ALTER TABLE WorkoutSession ADD COLUMN note TEXT;
ALTER TABLE WorkoutSession ADD COLUMN importKey TEXT;
ALTER TABLE WorkoutSession ADD COLUMN structureDirty INTEGER NOT NULL DEFAULT 0;
ALTER TABLE SessionExercise ADD COLUMN note TEXT;
ALTER TABLE SessionExercise ADD COLUMN replacesSessionExerciseId TEXT;
ALTER TABLE LoggedSet ADD COLUMN side TEXT;
CREATE UNIQUE INDEX index_WorkoutSession_importKey ON WorkoutSession(importKey);
```

Commit the generated schema 4 JSON. Do not edit or replace the existing schema 1-3 files. The existing unique `(sessionExerciseId, workingIndex)` index is unchanged. `HistorySetRow`, `HistoryExercise`, `HistorySession`, and `LoggedSetRecord` carry the new note, side, and import metadata needed by the UI.

**Backup format 3**

- `BackupExercise`: `note`, `isUnilateral`.
- `BackupSession`: `note`, `importKey`.
- `BackupSessionExercise`: `note`, `replacesSessionExerciseId`.
- `BackupSet`: `side`.
- `structureDirty` is omitted because backup validation only accepts finished sessions, where it is always false.
- `BACKUP_FORMAT_VERSION` becomes 3. New fields use `@Required` with defaults, so a format 3 file must contain them. Formats 1 and 2 are patched with the new keys before JSON type validation, the same way format 1 is currently patched for format 2 fields.
- Validation adds: note length at most 2,000 characters; `side` null or a valid `SetSide`; `importKey` nonblank when present, bounded in length, and unique across non-null keys; `replacesSessionExerciseId` references another session exercise in the same session and not itself.
- A format 3 backup restores all new fields. A format 2 backup defaults notes, side, and links to null and `isUnilateral` to false. A format 1 backup does the same and also defaults the unit and rest overrides.

**Weight display fix**

Change `formatKg` to round half-up to two decimal places, then strip trailing zeros. Keep pound formatting at one decimal. Do not round stored kilograms; targets, prefills, and editing keep the canonical value. Extend `UnitsTest` with converted values and two-decimal cases.

**Strong CSV source evidence**

The supplied text contains workout numbers 1 through 33, dated 2026-06-24 through 2026-08-30. No local copy was found in the repository or the approved temporary directory. Original bytes, encoding, BOM, and line endings have not been inspected, and no exhaustive parser run has been made. The format below is the evidence to implement against.

**Strong columns**

13 named columns, semicolon delimiter, double-quoted fields, `.` decimal separator, empty fields as `""`. A record is an exercise event, not always a set.

| Column | Format and meaning | Mapping |
|---|---|---|
| `Workout #` | Integer-like text repeated within a workout | Group rows in one file. Not a cross-export identity |
| `Date` | `yyyy-MM-dd HH:mm:ss`, repeated, no zone | Parse as device local time. Assume start time |
| `Workout Name` | Text such as `FBEOD` or `FB x3` | Display name. Does not create a template |
| `Duration (sec)` | Whole seconds, repeated | Read once per workout. Finish is start plus duration |
| `Exercise Name` | Full source name | Preserve in history. Map to a Buhat exercise for data keys |
| `Set Order` | Number, `W`, `F`, `Rest Timer`, or `Note` | Row discriminator |
| `Weight (kg)` | Decimal kilograms, sometimes long tails | Store as kg. Blank on a set row means bodyweight 0 |
| `Reps` | Integer text on set rows | Completed reps. Failure does not add a rep |
| `RPE` | Optional decimal including `8.5` and `9.5` | Convert to integer RIR |
| `Distance (meters)` | Empty in the sample | Populated value blocks the import |
| `Seconds` | Decimal seconds on rest rows | Rest metadata, never a set |
| `Notes` | Text on `Note` rows, including an empty row | Preserve under the session exercise |
| `Workout Notes` | Empty in the sample | Preserve on the session when nonempty |

**Strong row types**

| `Set Order` | Treatment |
|---|---|
| Positive integer | Working set |
| `W` | Warmup. Excluded from progression and totals |
| `F` | Failure set. Counts as working and uses the recorded reps |
| `Rest Timer` | Ignore as a set. Do not add its seconds to duration |
| `Note` | Exercise note metadata |
| Unknown tag, including drop sets | Block the import with a row-level error |

Group by `Workout #` while preserving row order. Each imported session is `FINISHED`, with `startedAt` parsed from `Date` as device local time and `finishedAt = startedAt + duration`. Convert RPE with `10 - RPE`, half-up, clamped to 0..5. Expected RIR for RPE 6, 6.5, 7, 7.5, 8, 8.5, 9, 9.5, 10 is 4, 4, 3, 3, 2, 2, 1, 1, 0. Blank RPE stays null. Invalid values block the import. Blank weight on a set row becomes 0.0. Missing reps on a set row block the import. Validate that date, name, and duration are consistent within a group. Skip groups with no working/failure sets and report them. Ignore source set numbers for indexing and assign working indices in row order. Assign synthetic completion timestamps in row order inside the workout's start and finish. Imported sets stay unsided and never seed Left or Right targets. Join multiple note rows for one exercise occurrence with blank lines. A blank note stores null and never inherits from another workout. Use the first nonblank workout note; differing nonblank values in one workout block the import.

Import identity is `strong|<raw exported date-time>|<workout name>`. The raw date text avoids timezone-dependent identity changes. Reject duplicate identities inside one file. Preview skips identities already in the database. Preview everything before writing. Any parse, mapping, range, or unsupported-field error blocks the whole import with no partial writes. Import creates missing custom exercises and all sessions in one transaction and does not call the destructive backup restore path. Only exercises present in the export can be recovered; unused Strong custom exercises remain absent.

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
- A template edit made outside the workout after starting does not change the active session. In-workout edits follow the later finish prompt.
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

- [x] Add DAO reads and position updates for a session's exercises. Add `reorderSessionExercises(sessionId, orderedIds)` to the repository inside `sessionWriteMutex` and one transaction.
- [x] Validate that the session is ACTIVE and that `orderedIds` is exactly the session's exercise IDs with no duplicates. Move positions to negatives, then write 1..N.
- [x] Add "Edit exercise order" to the Active overflow menu. Open a dialog listing the session's exercises with up/down buttons. Save on each move; disable the arrows at the ends and while a write is in flight; keep the selected exercise selected.
- [x] Add test tags such as `edit-order`, `order-up-<name>`, `order-down-<name>`, and `close-order`.
- [x] Tests: live reorder, finish order, process death, logged sets stay attached, template unchanged, one exercise, invalid ID list rejected.

**Acceptance checks**

- Moving the third exercise to first updates the dialog and chip order immediately.
- Finishing stores the new order and past workouts show it. The source template keeps its original order unless the Phase 18 finish prompt chooses Update template.
- Killing the app after a reorder restores the new order.
- Logged sets stay under their exercise, and a running rest is unaffected.
- Reordering one exercise is a no-op with disabled arrows.
- Unit and device suites pass.

**Result:** 2026-11-09. Full E2E suite passes: 113 unit tests and 205 device tests, 0 failed. `reorderSessionExercises` validates ACTIVE and an exact ID set under `sessionWriteMutex`, then rewrites positions to negatives and 1..N in one transaction; failure rolls back every position and finish/discard serialize with it. The Active overflow menu opens an "Edit exercise order" dialog with 48dp up/down buttons that save each move, stay disabled at the ends and during writes, and keep the selected exercise, logged sets, running rest, and template unchanged. Verified through repository recreation, Activity recreation, and finished history/Home order. Automated restore coverage is Room and Activity recreation, not an actual process kill; the manual check remains in the test catalog.

### Phase 14 - Full validation and release

**Outcome:** A signed update that passes the full suite, upgrades the installed release with data intact, and restores an old backup.

**Prerequisites:** Phases 11-13.

**Work**

- [x] Update the E2E test catalog and `.opencode/skills/e2e-testing` references for exercise settings, lb logging, rest overrides, and reorder.
- [x] Run `.\gradlew.bat testDebugUnitTest --console=plain` and `.\gradlew.bat pixel6api35DebugAndroidTest --rerun`.
- [ ] Run manual checks 10-13 from section 4.6 on the test phone.
- [ ] Visual check in dark and light themes at 1.0x and 1.3x font scale, long exercise names, mixed units in one list, dialog height, and 48dp targets.
- [x] Bump `versionCode` from 3 to 4 and `versionName` from 1.0.1 to 1.1.0. Build and sign. Install over the current release and confirm data and preferences survive.
- [x] Verify the release manifest has no new permissions and no INTERNET permission.

**Acceptance checks**

- All Phase 11-13 acceptance checks pass on the signed release.
- Installing over v1.0.1 keeps exercises, templates, history, and new preferences.
- A format-1 backup restores on the new release.
- Existing timer, active-session, analytics, export/import, recovery, diagnostics, and visual behavior is unchanged.
- The release verifier passes.

**Result (automated):** 2026-11-10. Full suite passes: 113 unit tests and 205 device tests, 0 failed, no INTERNET permission. The signed v1.1.0 APK passes the release verifier (signer matches the approved key, R8 and resource shrinking ran, versionCode 4 / versionName 1.1.0, no INTERNET). A release-signed install over v1.0.1 on the API 35 emulator kept 1 workout, 3 working sets, 800 lb volume, and the lb preference, and showed the update prompt once. A format-1 backup imported on the release build with 1 exercise, 1 template, 1 workout, and 3 sets restored. On SM-S711W the same release installed over v1.0.1 with the profile, 1 finished workout, the active Push Day A session, and the lb preference intact. Pending: manual checks 10-13 and the visual pass on SM-S711W.

### Phase 15 - Weight display fix

**Outcome:** Converted weights display with at most two decimal places without changing stored values.

**Prerequisites:** None. This is independent of every other new phase.

**Work**

- [x] Round `formatKg` to two decimals half-up, then strip trailing zeros.
- [x] Keep pound formatting at one decimal.
- [x] Add unit tests for long converted kg values, already-short values, zero, and pound round trips.
- [x] Verify the Active workout unit-change path on a device.

**Acceptance checks**

- `formatKg(102.058283...)` returns `102.06`.
- `formatKg(54.884631999999996)` returns `54.88`.
- `80.0`, `82.5`, and `0.0` render as `80`, `82.5`, and `0`.
- Entering 225 lb, switching to kg, and switching back shows `102.06` and then `225` without changing the stored kilogram value.
- `./gradlew test` passes.

### Phase 16 - Schema 4 and backup 3 foundation

**Outcome:** Schema 4 installs over existing data, and backup format 3 round-trips while formats 1 and 2 still restore.

**Prerequisites:** Phase 15.

**Work**

- [x] Add the new entity fields, `SetSide`, and the converter.
- [x] Add migration 3->4 with defaults and the unique import-key index.
- [x] Commit the generated schema 4 JSON.
- [x] Extend backup DTOs to format 3 with backward-compatible patches and validation.
- [x] Map the new fields through repository history models and DAO projections.
- [x] Add migration, backup compatibility, and invalid-field tests.

**Acceptance checks**

- A schema 3 database with exercises, templates, sessions, and sets upgrades with identical counts and values.
- Backup v3 exports and restores every new field.
- Backup v2 restores with new fields defaulting to null or false.
- Backup v1 still restores.
- Invalid side, duplicate import key, overlong note, and a dangling replacement link are rejected.
- Existing unit and device suites pass.

**Result (automated):** 2026-11-14. Full suite passes: 123 unit tests and 238 device tests, 0 failed, no INTERNET permission. `BuhatDatabase` is version 4 with `MIGRATION_3_4` adding every new field with defaults and the unique `importKey` index; `4.json` is committed. Backup format 3 round-trips notes, unilateral flags, session notes, import keys, replacement links, and sides; formats 1 and 2 patch in null and false defaults. `MigrationTest` covers 3->4 and the 1->3 chain, and backup tests cover v1/v2/v3 round trips plus rejection of an invalid side, duplicate import key, overlong note, and dangling replacement link. Real-data device migration and the release install remain in Phase 22.

### Phase 17 - Persistent exercise notes

**Outcome:** One note per exercise, visible during workouts and editable from the library or active workout.

**Prerequisites:** Phase 16.

**Work**

- [x] Extend Exercise settings with a note field and 2,000-character validation.
- [x] Extend `saveExercisePreferences` and the DAO update for the note and unilateral flag.
- [x] Show nonblank notes on the active exercise card.
- [x] Add persistence, premade-edit, clearing, and backup tests.

**Acceptance checks**

- A note set on a premade or custom exercise survives restart and appears in the next workout.
- Clearing the note removes it from the active card.
- Premade name, muscle group, and equipment remain read-only.
- 2,000 characters are accepted and 2,001 are rejected.
- The note survives a backup round trip.

**Result (automated):** 2026-11-14. Full suite passes: 123 unit tests and 215 device tests, 0 failed, 0 skipped, no INTERNET permission. Tests cover premade/custom notes from Library and Active, Activity/repository recreation, next-workout visibility, clearing, 2,000/2,001-character bounds, failed-write retry, backup restore, and exercise identity during draft restoration and pending Room reads. Tests were observed failing before implementation. Both review findings have passing regression tests. Physical-phone process-kill and release checks remain in Phase 22.

### Phase 18 - Mid-workout exercise changes and template save-back

**Outcome:** Add, edit, replace, remove, or reorder exercises during a workout. Finish offers a template update only when the structure changed.

**Prerequisites:** Phase 16. Phase 17 is independent but shipped first for notes in the new dialogs.

**Work**

- [x] Add repository operations for add, edit, replace, remove, and dirty marking.
- [x] Add the replacement link and template-draft exclusion.
- [x] Add active-workout UI for add, edit, replace, and remove.
- [x] Add the finish dialog and transactional template save-back.
- [x] Handle custom update, premade copy, missing template, and validation failure.
- [x] Add process-death, replacement, premade, and rollback tests.

**Acceptance checks**

- Logging two sets of A, replacing with B, and logging more keeps the first two sets under A and the later sets under B.
- "This workout only" leaves the template unchanged.
- "Update template" replaces the custom template with the session's current exercises and order.
- Updating a premade creates a uniquely named custom copy and leaves the premade unchanged.
- Reorder-only changes also trigger the prompt and can update the template order.
- Changing a session exercise from 3 × 8-12 to 4 × 6-8 updates the target range, triggers the prompt, and persists through "Update template".
- An exercise added but never logged is included when updating the template.
- An unlogged exercise can be removed. An exercise with sets cannot.
- No prompt appears when only sets or notes changed.
- Process death after an edit still prompts on finish.
- A failed template write leaves the workout active with a visible error.

**Result (automated):** 2026-11-14. Full suite passes: 123 unit tests and 238 device tests, 0 failed, 0 skipped, no INTERNET permission. Tests cover add, edit, replace, remove, and reorder with position renumbering; logged sets staying under the performed exercise; replacement links; duplicate and inactive-session rejection; the persisted dirty flag through repository recreation; this-workout-only; custom update in session order; premade copy names `(copy)` and `(copy 2)`; recreation of a deleted template and the `(copy)` fallback when its name was reused; replaced-row exclusion; repeated finish as a no-op; and rollback of a failed save-back with the workout still active. Tests were observed failing before implementation. Review findings on double finish, reused template names, and hidden finish errors have passing regression tests. Physical-phone process-kill and release checks remain in Phase 22.

### Phase 19 - Unilateral tracking

**Outcome:** Left and Right are logged and progressed independently. Each side is one set, rest runs after each side, and history and analytics keep the side.

**Completed:** 2026-11-14. Exercise settings enables unilateral logging. Manual side selection resumes alternation after saving, both sides must finish their prescriptions, and targets/prefill/RIR stay per-side. Progress and Home have independent side charts and statistics. Expanded history retains legacy unsided rows and shows L1/R1/L2 positions for sided working/failure sets. Set editing preserves an untouched null side and allows explicit correction. Archived template rows retain per-side labels. Schema 4 and backup format 3 are unchanged.

**Verification:** `.opencode/skills/e2e-testing/scripts/run-e2e.ps1` passed with 133 unit tests and 254 managed-device tests, 0 failed or skipped. New feature and review regressions were observed failing before their fixes. Mutation checks caught dropped sides, incorrect progression lookup, shared PR keys, missing chart filters, and missing SQL side grouping. Existing backup round-trip and migration suites passed. The first full run hit a Compose layout exception in `SettingsScreenTest.exportAndImportAreDisabledWhileASessionIsActive`; its isolated rerun and the final full run passed.

**Prerequisites:** Phase 16. Phase 17 is independent. Phase 18 is complete; this phase continues in the same screen.

**Work**

- [x] Add the unilateral switch to Exercise settings.
- [x] Add side selection, alternating default, side-aware target and prefill, and L/R labels.
- [x] Add per-side progression positions and source filtering.
- [x] Add side-aware PR, e1RM, stats, and trend handling.
- [x] Add side filters to Progress and Home for unilateral exercises.
- [x] Add "per side" prescription labeling.
- [x] Add unit and device tests for side selection, rest, analytics, and backup.

**Acceptance checks**

- Three prescribed sets produce six logged sets, alternating Left and Right by default.
- Rest starts after Left and again after Right.
- Right-side targets read Right history. Left history never supplies a Right target.
- Left and Right PRs and charts are separate.
- Each side adds one working set and its own weight times reps to volume.
- An exercise switched to unilateral with only old unsided sets starts both sides at first-time targets. Old volume and set counts are unchanged.
- Switching back to bilateral ignores side-specific sets for targets.
- Exercise history shows L/R labels.
- Sides survive a backup round trip.

### Phase 20 - Whole-workout history

**Outcome:** Progress can show every finished workout without choosing an exercise.

**Completed:** 2026-11-15. Progress has an Exercise/History mode control saved with `rememberSaveable`. History loads complete finished sessions newest first and shows a card per workout with its name, date, session note, and on expand each exercise occurrence in position order with its note, effective unit, every set, L/R labels, and PR badge. Replaced exercises stay as separate occurrences. Delete reuses the existing confirmation and removes the workout from both modes.

**Verification:** `.opencode/skills/e2e-testing/scripts/run-e2e.ps1` passed with 133 unit tests and 256 managed-device tests, 0 failed or skipped. New tests and the empty state were observed failing before implementation. A mutation check on the per-exercise unit lookup was caught by the lb assertion. The first full run hit an unrelated Compose layout exception in `ActiveWorkoutTest.setEditDialogUsesTheSameFourPlusSelector`; its isolated rerun and the final full run passed. No schema or backup-format change.

**Prerequisites:** Phases 16, 17, and 19 for notes and side labels. Phase 18 for replaced-exercise display.

**Work**

- [x] Add the Progress mode control and state.
- [x] Load complete finished sessions for Workout history.
- [x] Add a whole-session card with session notes, exercise notes, side labels, and repeated exercise occurrences.
- [x] Reuse deletion, formatting, and unit resolution.
- [x] Add mode, deletion, mixed-unit, imported, and empty-state tests.

**Acceptance checks**

- A workout that does not contain the selected progress exercise still appears in Workout history.
- Mode selection survives rotation and process death.
- Replaced exercises appear as separate occurrences with their own sets.
- Imported notes appear on the correct exercise occurrence.
- Imported unsided sets show no L/R label. Side-labelled native sets show L/R labels.
- Mixed-unit workouts format each exercise in its effective unit.
- Deleting a workout removes it from both modes.

### Phase 21 - Strong CSV import

**Outcome:** A Strong export imports offline with preview, exercise mapping, duplicate skipping, notes, and RPE conversion.

**Prerequisites:** Phases 16, 17, 19, and 20.

**Work**

- [ ] Add the CSV parser and parser tests.
- [ ] Add the Strong parser, validator, row mapper, identity, and RPE conversion.
- [ ] Add repository preview and transactional append with duplicate skipping.
- [ ] Add the Settings entry and import screen with file picker, preview, mapping, and confirm.
- [ ] Add import tests for the supplied layout, notes, duplicate imports, unsupported rows, and rollback.

**Acceptance checks**

- The supplied export parses 33 workouts across its date range.
- Rest Timer and Note rows never become sets.
- Warmups, working sets, and failure sets map correctly. A failure does not add a rep.
- RPE 6 through 10 in half steps maps to the RIR table. Blank RPE stays null. A numeric RPE-10 set stays a working set.
- Imported exercise notes stay with their original workouts. A blank note never inherits an earlier note.
- Reimporting the same file reports 0 new and all workouts skipped.
- An overlapping export adds only new workout identities.
- Mapping two source names to one exercise preserves both occurrences.
- A new custom exercise requires name, muscle group, and equipment.
- Populated distance or an unknown set tag blocks the import with a row-level message and writes nothing.
- Import during an active workout is blocked.
- The import is one transaction. A forced failure leaves no new exercises or sessions.
- Imported data survives a backup round trip and appears in Workout history.

**Decision gate:** Confirm the real export's encoding, line endings, date meaning, row coverage, and blank-weight semantics. If no local copy of the original file is available, use a sanitized fixture matching the supplied schema and run the real file before release.

### Phase 22 - Verification and release

**Outcome:** All new features hold on a real device and in a release build.

**Prerequisites:** Phases 15-21. Phase 14 manual checks 10-13 and the visual pass must also be complete before release.

**Work**

- [ ] Run the full unit and device suites.
- [ ] Run the new manual checks 14-19 on a device.
- [ ] Complete the pending Phase 14 manual checks 10-13 and visual pass.
- [ ] Test the schema 3 to 4 upgrade on a device with real data.
- [ ] Install over the current release build and verify data survives.
- [ ] Run the release verification script and signed build.
- [ ] Update the roadmap status only after verification.

**Acceptance checks**

- Unit suite and device suite pass with no regressions.
- Upgrade from the current release preserves exercises, templates, sessions, sets, and preferences.
- Every acceptance check above passes on the release build.
- The merged manifest still has no INTERNET permission.
- Release build installs and launches.
- New preferences, notes, sides, import keys, and replacement links survive a backup round trip.

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
| Active workout reorder | Phase 13 | Order persists to history; template unchanged unless the finish prompt chooses Update template |
| Schema 3 and backup 2 compatibility (shipped) | Phase 11 | v2 database upgrades; v1 backup defaults; v2 round trip |
| Weight display rounding | Phase 15 | 225 lb to kg shows at most two decimals and returns to 225 lb |
| Schema 4 and backup 3 | Phase 16 | `MigrationTest` covers 3->4 and the 1->3 chain; `BackupTest` and `BackupRepositoryTest` cover format 1, 2, and 3 round trips and reject invalid new fields. Real-data upgrade remains in Phase 22 |
| Persistent exercise note | Phase 17 | Unit and device tests cover restart, next-workout visibility, clearing, 2,000/2,001 bounds, failed-write retry, and backup round trips |
| Mid-workout exercise changes | Phase 18 | `SessionRepositoryTest` and `ActiveWorkoutTest` cover add, edit, replace, remove, and reorder; set retention; prompt branches; unique copy names; repeated finish; and save-back rollback |
| Unilateral tracking | Phase 19 | Left and Right targets, PRs, charts, rest, set counts, and volume are independent |
| Whole-workout history | Phase 20 | `ProgressScreenTest` covers a workout without the selected exercise, mode survival, session and exercise notes, mixed units, PR badges, repeated occurrences with side labels, deletion, and the empty state |
| Strong CSV import | Phase 21 | Preview, mapping, notes, RPE conversion, duplicate skip, and transactional rollback pass |
| New feature release | Phase 22 | Full suites, real-data migration, release install, and backup round trip pass |

## 6. Dependencies, remaining uncertainties, and next step

### Dependencies and parallel work

- Completed: Phases 0-20, except Phase 14 manual checks 10-13 and the visual pass, which remain. Physical-phone process-kill and release checks are part of Phase 22.
- Remaining critical path: Phase 21 -> 22. Phase 21 can start now because Phase 20 is complete.
- Phase 20 depends on notes, sides, and replacement display. Phase 21 depends on the data model and whole-workout history. Phase 22 follows all new phases.
- Shared contracts to freeze before parallel work: the side-aware domain signatures, the import identity string, and the new dialog and screen test tags. The schema 4 fields, `SetSide`, the note cap, `BACKUP_FORMAT_VERSION = 3`, and the finish prompt states are implemented and frozen.

### Remaining uncertainties and assumptions

**Blocking environment items**

None. Temurin JDK 21 is installed and `JAVA_HOME` points at it.

**Product assumptions already in the shipped baseline, none blocks Phases 19-22**

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

**New feature assumptions to confirm during review, none blocks Phase 19**

- The finish prompt appears only when the session structure changed. A persisted dirty flag records add, edit, replace, remove, reorder, or prescription changes. Set logging and note edits do not trigger it.
- A replacement row links to the exercise it replaced. Template save-back excludes replaced rows but keeps them in workout history.
- Template prescriptions are per side for unilateral exercises. Three prescribed sets means three Left and three Right.
- Volume sums each logged side's weight times reps with no extra multiplier.
- Unilateral sets alternate sides by default. The user can change the side before saving a set.
- Progression positions are counted per side. The stored global working index stays a completion-order index.
- Side-specific PRs, e1RM, and charts are separate. Unsided sets do not seed either side and are excluded from side-filtered charts. Side-specific filtering in Home and Progress is part of this work.
- The persistent note appears on the active exercise card. History shows imported historical notes, not the current persistent note.
- Imported sessions use the sentinel template id `strong-import` and the Strong workout name as their display name. They do not create templates.
- An imported workout is identified by source app, raw exported date-time, and workout name. `Workout #` alone is not trusted as stable.
- Strong dates represent the workout start. Finish time is start plus duration. This needs confirmation against the user's Strong history before release.
- Unsupported Strong rows block the entire import with row-level messages. Partial-skip can be added later if a real export needs it.
- Exact-name exercise matches are reused and prior custom entries are reused on later imports. Persisting source-name mappings is deferred.
- A small in-repo CSV parser is used instead of a dependency. It must handle quoted separators, escaped quotes, multiline fields, BOM, and CRLF.
- Format 3 is one-way for backups. Friends on older builds reject it as newer, so the release note must say to update first.

**Validation gates for Phases 19-22**

- **Original Strong file:** Encoding, BOM, line endings, the meaning of `Date`, and whether a blank weight means bodyweight or missing data are unverified. Obtain the real file or confirm these assumptions against Strong's history before release.
- **Overlapping export:** Duplicate identity is validated against one export so far. Verify with a second overlapping export before relying on skip behavior.
- **Device migration:** The schema 3 to 4 upgrade must be tested on a device with real data, not only an in-memory database.
- **Unilateral history:** Confirm side-specific charts and PRs feel right with real Left/Right data before release.

**Technical risks**

- Schema and backup changes touch every stored feature. Phase 16 carries the highest regression risk.
- Replacement rows and repeated exercise occurrences break existing assumptions in history and progression. Fix the shared domain and DAO paths, not individual screens.
- Import fidelity depends on undocumented Strong behavior. Fail closed rather than silently dropping rows.
- Unilateral mode adds a second dimension to progression, PRs, and charts. Partial handling would produce misleading numbers.
- The finish prompt changes existing reorder behavior. The "This workout only" option keeps it safe.
- The Room migration must be tested over the installed release, not only on a fresh test database.
- Mixed units make screenshots and support questions harder; every weight string must include its unit.
- Format 3 is one-way for backups. Friends on older builds cannot import new files, so the release note must say to update first.
- Phase 19 continues in `ActiveWorkoutScreen.kt`; keep the existing finish prompt and structure-dirty paths intact.
- OEM battery management may delay or suppress the alarm on some phones. Phase 4 tests this; foreground service is the fallback.
- Per-set progression can produce different weights across sets in one session. This is intentional, but confirm it feels right after the first real workout.
- Room schema changes after v1 need migrations. Export before installing a schema-changing update.
- Losing the release keystore forces friends to uninstall and lose local data unless they export first.

### Next step

Phase 20 is complete. Next implementation phase is Phase 21: Strong CSV import with preview, exercise mapping, duplicate skipping, notes, and RPE conversion. The pending Phase 14 manual checks 10-13 and visual pass remain, with physical-phone process-kill and release validation in Phase 22.
