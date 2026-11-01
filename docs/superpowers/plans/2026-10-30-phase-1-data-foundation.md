# Phase 1 Data Foundation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Local data, seed content, pure calculation rules, and reusable UI primitives are in place and tested.

**Architecture:** One `app` module. Pure Kotlin domain rules first, then Room schema plus a deterministic idempotent seed, DataStore profile/settings, one repository and manual container, then theme and shared Compose primitives. No screens, no ViewModels, no networking.

**Tech Stack:** Kotlin 2.1.0, Compose BOM 2025.01.00, Room 2.7.1 with KSP, DataStore Preferences 1.1.2, JUnit4, Compose UI test.

**Spec:** `planning/buhat-tracker-roadmap.md` sections 4.2, 4.3, 4.4, 4.7, Phase 1.

## Global Constraints

- minSdk 26, compile/target 35, package `com.buhat.tracker`, versionCode 1/versionName 1.0.0.
- All IDs are UUID strings. Timestamps are epoch milliseconds UTC. Weights are kilograms.
- Dependencies: Room 2.7.1, DataStore 1.1.2, KSP 2.1.0-1.0.29, coroutines 1.9.0. No Hilt, no chart library, no serialization yet.
- Validation ranges: sets 1..10, repMin 1..30, repMax >= repMin, RIR 0..5, weight 0..1000 kg, reps 0..100, rest 0..600s.
- Seed: 6 premade templates with total sets 9, 16, 17, 13, 12, 16 and the 56 exercises from section 4.7.
- No INTERNET permission. `allowBackup="false"` stays.
- Every task: failing test, run it red, minimal code, run it green.

## Review Focus

- Empty history, or a source session with only warmups, must return a first-time target, never a crash.
- Exercise/template names are unique case-insensitively; seed and tests must not create case variants.
- Time windows at the exact 28-day and 90-day boundary must include the boundary values.
- Weight 0 is bodyweight: no e1RM point and the target never adds weight.
- A missing working-set position falls back to the last working set in that session, never to another position.

---

### Task 0: Build configuration

**Files:**
- Modify: `build.gradle.kts`, `app/build.gradle.kts`

**Interfaces:**
- Produces: KSP + Room, DataStore Preferences, JUnit4, and coroutines-test available to later tasks. Schema output at `app/schemas`.

- [ ] **Step 1: Add** KSP 2.1.0-1.0.29, Room 2.7.1, DataStore 1.1.2, JUnit 4.13.2, coroutines-test 1.9.0. Set `room.schemaLocation`.
- [ ] **Step 2: Run** `gradlew.bat help` and confirm the build config resolves.
- [ ] **Step 3: Commit** `build: add room and datastore dependencies`.

### Task 1: Domain models and per-set progression

**Files:**
- Create: `app/src/main/java/com/buhat/tracker/domain/Models.kt`
- Create: `app/src/main/java/com/buhat/tracker/domain/Progression.kt`
- Test: `app/src/test/java/com/buhat/tracker/domain/ProgressionTest.kt`

**Interfaces:**
- Produces: `enum class SetType { WARMUP, WORKING, FAILURE }`, `enum class PreviousSetSource { ANY_WORKOUT, SAME_TEMPLATE }`
- Produces: `data class LoggedSetRecord(id: String, setType: SetType, workingIndex: Int?, weightKg: Double, reps: Int, rir: Int?, completedAt: Long)`
- Produces: `data class HistoryExercise(exerciseId: String, sets: List<LoggedSetRecord>)`
- Produces: `data class HistorySession(id: String, templateIdSnapshot: String, finishedAt: Long, exercises: List<HistoryExercise>)`
- Produces: `data class SetAtPosition(val set: LoggedSetRecord, val position: Int)`
- Produces: `fun numberWorkingSets(sets: List<LoggedSetRecord>): List<SetAtPosition>`
- Produces: `fun sourceSetForPosition(history: List<HistorySession>, exerciseId: String, position: Int, source: PreviousSetSource, templateIdSnapshot: String): LoggedSetRecord?`
- Produces: `data class ProgressionTarget(val weightKg: Double?, val reps: Int, val message: String)`
- Produces: `fun nextTarget(source: LoggedSetRecord?, repMin: Int, repMax: Int): ProgressionTarget`

- [ ] **Step 1: Write failing tests** covering working numbering (warmups skipped, failure counts), per-position independence (10/9/8 yields 82.5x6, 80x10, 80x9), missing position fallback, any-workout vs same-template fallback, bodyweight, and first time.

- [ ] **Step 2: Run** `gradlew.bat test --tests "*ProgressionTest"` and confirm failure (unresolved symbols).

- [ ] **Step 3: Implement** the types and functions. Source session = most recent finished session containing the exercise; same template falls back to any workout. Target rules follow the section 4.3 table exactly, with message copy from the table.

- [ ] **Step 4: Run** the same command and confirm all pass.

- [ ] **Step 5: Commit** `feat: add per-set progression rules`.

### Task 2: Analytics and units

**Files:**
- Create: `app/src/main/java/com/buhat/tracker/domain/Analytics.kt`
- Create: `app/src/main/java/com/buhat/tracker/domain/Units.kt`
- Test: `app/src/test/java/com/buhat/tracker/domain/AnalyticsTest.kt`
- Test: `app/src/test/java/com/buhat/tracker/domain/UnitsTest.kt`

**Interfaces:**
- Produces: `fun e1rm(weightKg: Double, reps: Int): Double?` (Epley, null when either is not > 0; returns `Double` otherwise)
- Produces: `fun prSetIds(history: List<HistorySession>): Set<String>` (strictly greater e1RM, chronological, warmups and weight 0 excluded)
- Produces: `data class WindowStat(val current: Double, val previous: Double, val deltaPercent: Double?)`
- Produces: `data class FourWeekStats(val workouts: WindowStat, val workingSets: WindowStat, val volumeKg: WindowStat, val newPrs: WindowStat)`
- Produces: `fun fourWeekStats(history: List<HistorySession>, nowMillis: Long): FourWeekStats`
- Produces: `data class TrendPoint(val atMillis: Long, val e1rm: Double)`
- Produces: `fun e1rmTrend(history: List<HistorySession>, exerciseId: String, rangeDays: Int?, nowMillis: Long): List<TrendPoint>`
- Produces: `enum class WeightUnit { KG, LB }`, `fun kgToLb(kg: Double): Double`, `fun lbToKg(lb: Double): Double`, `fun formatWeight(kg: Double, unit: WeightUnit): String`, `fun formatKg(kg: Double): String`

- [x] **Step 1: Write failing tests** for Epley, strict PR ties, warmup/bodyweight exclusion, 28/56/90/365-day boundaries, delta when previous is zero, and kg/lb display round trips.
- [x] **Step 2: Run** `gradlew.bat test --tests "*AnalyticsTest" --tests "*UnitsTest"` and confirm red.
- [x] **Step 3: Implement.** Current window is `[now - 28d, now]`, prior is `[now - 56d, now - 28d)`. Trend windows are 30/90/365/All days, point per session = max e1RM. One point per finished session. Display pounds at 0.1 lb.
- [x] **Step 4: Run** and confirm green.
- [x] **Step 5: Commit** `feat: add analytics and unit conversion`.

### Task 3: Seed catalog

**Files:**
- Create: `app/src/main/java/com/buhat/tracker/data/SeedContent.kt`
- Test: `app/src/test/java/com/buhat/tracker/data/SeedContentTest.kt`

**Interfaces:**
- Produces: `data class SeedExercise(val name: String, val muscleGroup: String, val equipment: String)`
- Produces: `data class SeedTemplateExercise(val exerciseName: String, val workingSets: Int, val repMin: Int, val repMax: Int, val targetRir: Int?)`
- Produces: `data class SeedTemplate(val name: String, val exercises: List<SeedTemplateExercise>)`
- Produces: `object SeedContent { const val VERSION = 1; val exercises: List<SeedExercise>; val templates: List<SeedTemplate> }`

- [x] **Step 1: Write failing test** asserting 6 template names, totals `[9, 16, 17, 13, 12, 16]`, 56 exercises, 10 muscle groups, and every template exercise exists in the library.
- [x] **Step 2: Run** `gradlew.bat test --tests "*SeedContentTest"` and confirm red.
- [x] **Step 3: Implement** the exact rows from roadmap 4.7.
- [x] **Step 4: Run** and confirm green.
- [x] **Step 5: Commit** `feat: add seed catalog`.

### Task 4: Room schema, DAO, seed transaction

**Files:**
- Create: `app/src/main/java/com/buhat/tracker/data/local/Entities.kt`
- Create: `app/src/main/java/com/buhat/tracker/data/local/Converters.kt`
- Create: `app/src/main/java/com/buhat/tracker/data/local/BuhatDao.kt`
- Create: `app/src/main/java/com/buhat/tracker/data/local/BuhatDatabase.kt`
- Create: `app/src/main/java/com/buhat/tracker/data/BuhatRepository.kt`
- Create: `app/schemas/com.buhat.tracker.data.local.BuhatDatabase/1.json` (generated, committed)
- Test: `app/src/androidTest/java/com/buhat/tracker/data/BuhatDatabaseTest.kt`

**Interfaces:**
- Produces: entities `Exercise`, `Template`, `TemplateExercise`, `WorkoutSession`, `SessionExercise`, `LoggedSet` exactly per roadmap 4.2.
- Produces: `class BuhatDatabase : RoomDatabase` with `dao: BuhatDao`.
- Produces: `class BuhatRepository(database: BuhatDatabase, settingsStore: SettingsStore)` with `suspend fun seedIfNeeded()` and flows for exercises and templates.
- Deterministic seed IDs: `UUID.nameUUIDFromBytes("<type>:<name>".toByteArray()).toString()` so re-running uses INSERT OR IGNORE.

- [x] **Step 1: Write failing device tests** for NOCASE unique exercise names, cascade template delete, unique `(sessionExerciseId, workingIndex)` with multiple null warmups allowed, and seeding twice leaving 6 templates / 56 exercises / correct totals.
- [x] **Step 2: Run** `gradlew.bat connectedDebugAndroidTest` and confirm red (classes missing).
- [x] **Step 3: Implement** entities, converters, DAO (`OnConflictStrategy.IGNORE` inserts, counts, queries), database with `exportSchema = true`, repository seeding inside `withTransaction`, then `settingsStore.markSeeded(VERSION)`.
- [x] **Step 4: Run** `gradlew.bat connectedDebugAndroidTest` and confirm green. Verify `app/schemas/.../1.json` exists.
- [x] **Step 5: Commit** `feat: add room schema and seeded content`.

### Task 5: DataStore profile and settings

**Files:**
- Create: `app/src/main/java/com/buhat/tracker/data/ProfileStore.kt`
- Create: `app/src/main/java/com/buhat/tracker/data/SettingsStore.kt`
- Test: `app/src/androidTest/java/com/buhat/tracker/data/ProfileSettingsStoreTest.kt`

**Interfaces:**
- Produces: `data class Profile(name: String?, gender: String?, age: Int?, weightKg: Double?, heightCm: Int?)`
- Produces: `data class Settings(theme: AppTheme = DARK, unit: WeightUnit = KG, previousSetSource: ANY_WORKOUT, restWarmupSeconds: Int = 60, restWorkingSeconds: Int = 120, restFailureSeconds: Int = 180, restIncrementSeconds: Int = 15, restEndsAt: Long? = null, seedVersion: Int = 0)`
- Produces: `enum class AppTheme { DARK, LIGHT }`
- Produces: `class ProfileStore(dataStore) { val profile: Flow<Profile>; suspend fun save(profile) }`
- Produces: `class SettingsStore(dataStore) { val settings: Flow<Settings>; suspend fun save(settings); suspend fun markSeeded(version: Int) }`

- [x] **Step 1: Write failing device test** that reads defaults, saves a full profile/settings, recreates the DataStore from the same file, and reads identical values. Also assert every default above.
- [x] **Step 2: Run** `gradlew.bat connectedDebugAndroidTest --tests "*ProfileSettingsStoreTest"` and confirm red.
- [x] **Step 3: Implement** preference keys and mapping. Defaults documented in KDoc on `Profile`/`Settings`.
- [x] **Step 4: Run** and confirm green.
- [x] **Step 5: Commit** `feat: add profile and settings stores`.

### Task 6: Application and manual container

**Files:**
- Create: `app/src/main/java/com/buhat/tracker/BuhatApplication.kt`
- Create: `app/src/main/java/com/buhat/tracker/AppContainer.kt`
- Modify: `app/src/main/AndroidManifest.xml` (`android:name=".BuhatApplication"`)

**Interfaces:**
- Produces: `class AppContainer(context: Context)` with `database`, `repository`, `profileStore`, `settingsStore`.
- Produces: `class BuhatApplication : Application` with `val container: AppContainer` and a background seed on create.

- [x] **Step 1: Implement** the container and application; no new test class (compile + Task 4 seed test cover it).
- [x] **Step 2: Run** `gradlew.bat assembleDebug` and confirm success.
- [x] **Step 3: Commit** `feat: wire application container and seed on start`.

### Task 7: Theme and shared UI primitives

**Files:**
- Modify: `app/src/main/java/com/buhat/tracker/ui/theme/Color.kt`
- Modify: `app/src/main/java/com/buhat/tracker/ui/theme/Theme.kt`
- Create: `app/src/main/java/com/buhat/tracker/ui/components/BuhatComponents.kt`
- Test: `app/src/androidTest/java/com/buhat/tracker/ui/components/BuhatComponentsTest.kt`

**Interfaces:**
- Produces: `BuhatTheme(darkTheme: Boolean = true, content)`, dark and light schemes from the same token set.
- Produces: `BuhatCard`, `BuhatPill`, `BuhatSegmentedControl(options, selectedIndex, onSelect)`, `BuhatStepper(value, onValueChange, step, min, max, format)`, `BuhatBadge`, `SetTypeBadge(type)`, `PrBadge`, `BuhatLineChart(values, contentDescription)`, `BuhatEmptyState(title, message)`.
- All colors come from `MaterialTheme.colorScheme`; no hard-coded component colors. Touch targets >= 48dp. Set type always rendered as text.

- [x] **Step 1: Write failing device test** rendering both themes and asserting segmented selection, stepper increment, badge/pill/empty-state text, and chart content description.
- [x] **Step 2: Run** `gradlew.bat connectedDebugAndroidTest --tests "*BuhatComponentsTest"` and confirm red.
- [x] **Step 3: Implement** the tokens, both schemes, and the primitives. Chart is a Compose Canvas polyline.
- [x] **Step 4: Run** and confirm green.
- [x] **Step 5: Commit** `feat: add light theme and shared components`.

### Task 8: Verification

- [x] **Step 1: Run** `gradlew.bat test` and capture output; all unit tests pass.
- [x] **Step 2: Run** `gradlew.bat connectedDebugAndroidTest` and capture output; all device tests pass.
- [x] **Step 3: Run** `gradlew.bat assembleDebug` and capture output.
- [x] **Step 4: Update** Phase 1 checkboxes and status line in `planning/buhat-tracker-roadmap.md`.
- [x] **Step 5: Commit** `chore: complete phase 1`.
