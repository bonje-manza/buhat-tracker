# Phase 12 Per-Exercise Unit and Working Rest Implementation Plan

> **For agentic workers:** Use `executing-plans` and `test-driven-development` to implement each task. Steps use `- [ ]` checkboxes.

**Goal:** Any exercise can display and accept its own weight unit and working rest, with the global setting as fallback, without changing stored kilograms or historical data.

**Architecture:** Resolve preferences at read time from the `Exercise` row. Domain helpers turn an override plus the global setting into an effective unit or rest. Active Workout observes the selected exercise row; Progress and Home resolve the selected exercise from an all-exercises flow that includes archived rows. Writes stay kilograms and no schema or backup format changes.

**Tech Stack:** Kotlin, Compose Material 3, Room, DataStore, JUnit, Android instrumentation.

**Spec:** `planning/buhat-tracker-roadmap.md`, Phase 12 and sections 4.1–4.6.

## Global Constraints

- Stored weights stay kilograms. Every exercise-specific weight display and input uses the exercise's effective unit; aggregates, body weight, and height stay on the global unit.
- Only working rest is per-exercise. Warmup and failure rest always use the global defaults.
- Premade rows may change only `weightUnitOverride` and `restWorkingSecondsOverride`.
- A missing exercise row falls back to the global unit and global working rest.
- No Room schema version, entity column, DataStore key, or backup DTO change. Phase 11 already shipped those.
- No roadmap Excluded work: no per-exercise warmup/failure rest, no per-exercise increment, no snapshotting preferences into sessions, no finished-workout editing, no drag-and-drop.
- Branch: `phase-12-per-exercise-unit-and-working-rest`.

## Review Focus

- A deleted exercise row falls back to the global unit in Active, Progress, and Home.
- Whole-pound values survive input, step, and top-of-range bump without drift.
- A 0-second working-rest override starts nothing, while warmup and failure keep their globals.
- An archived exercise keeps its unit in Progress and Home.
- A preference change mid-session updates the next set and target, not logged kilograms or a running timer.

---

## Task 1: Domain unit and rest helpers

**Files:**
- Modify: `app/src/main/java/com/buhat/tracker/domain/Units.kt`
- Modify: `app/src/main/java/com/buhat/tracker/domain/Progression.kt`
- Modify: `app/src/main/java/com/buhat/tracker/domain/RestTimer.kt`
- Test: `app/src/test/java/com/buhat/tracker/domain/UnitsTest.kt`, `ProgressionTest.kt`, `RestTimerTest.kt`

**Interfaces:**
- Produces: `fun Exercise.effectiveUnit(global: WeightUnit): WeightUnit`
- Produces: `fun weightIncrementKg(unit: WeightUnit): Double` (2.5 for KG, `lbToKg(5.0)` for LB)
- Produces: `fun Exercise.effectiveWorkingRest(globalSeconds: Int): Int`
- `restSecondsFor` keeps its signature. Callers pass the effective working rest.
- `nextTarget(source, repMin, repMax, unit)` keeps its signature and uses `weightIncrementKg(unit)` for the bump and message.

- [ ] **Step 1: Write failing unit tests**

In `UnitsTest`, add:
```kotlin
@Test fun effectiveUnitUsesOverrideThenGlobal() {
    assertEquals(WeightUnit.LB, exercise(unit = "LB").effectiveUnit(WeightUnit.KG))
    assertEquals(WeightUnit.KG, exercise(unit = null).effectiveUnit(WeightUnit.KG))
}

@Test fun weightIncrementFollowsTheUnit() {
    assertEquals(2.5, weightIncrementKg(WeightUnit.KG), 0.0)
    assertEquals(2.26796, weightIncrementKg(WeightUnit.LB), 0.00001)
}
```
Build the `Exercise` fixture with the real constructor and explicit values.

In `ProgressionTest`, add:
```kotlin
@Test fun lbTopOfRangeAddsFivePounds() {
    val target = nextTarget(set("top", weight = lbToKg(135.0), reps = 10), 6, 10, WeightUnit.LB)
    assertEquals(140.0, kgToLb(target.weightKg!!), 0.0001)
    assertEquals("Top of range reached. Add 5 lb, restart at 6.", target.message)
}

@Test fun lbBelowTopMessageUsesPounds() {
    val target = nextTarget(set("mid", weight = lbToKg(135.0), reps = 9), 6, 10, WeightUnit.LB)
    assertEquals("Beat 9 reps at 135 lb.", target.message)
}
```

In `RestTimerTest`, add:
```kotlin
@Test fun workingOverrideWinsButWarmupAndFailureStayGlobal() {
    val working = exercise(rest = 180).effectiveWorkingRest(120)
    assertEquals(180, restSecondsFor(SetType.WORKING, 60, working, 180))
    assertEquals(60, restSecondsFor(SetType.WARMUP, 60, working, 180))
    assertEquals(180, restSecondsFor(SetType.FAILURE, 60, working, 180))
}

@Test fun nullOverrideFallsBackToGlobalAndZeroIsKept() {
    assertEquals(120, exercise(rest = null).effectiveWorkingRest(120))
    assertEquals(0, exercise(rest = 0).effectiveWorkingRest(120))
    assertEquals(0, restSecondsFor(SetType.WORKING, 60, exercise(rest = 0).effectiveWorkingRest(120), 180))
}
```

- [ ] **Step 2: Run unit tests and confirm red**

Run: `.\gradlew.bat testDebugUnitTest --console=plain`
Expected: compile failure for the missing helpers, or assertion failures.

- [ ] **Step 3: Implement the helpers**

`Units.kt`:
```kotlin
fun Exercise.effectiveUnit(global: WeightUnit): WeightUnit =
    weightUnitOverride?.let(WeightUnit::valueOf) ?: global

fun weightIncrementKg(unit: WeightUnit): Double =
    if (unit == WeightUnit.LB) lbToKg(5.0) else 2.5
```
`RestTimer.kt`:
```kotlin
fun Exercise.effectiveWorkingRest(globalSeconds: Int): Int =
    restWorkingSecondsOverride ?: globalSeconds
```
Leave `restSecondsFor` unchanged.

`Progression.kt`: replace `WEIGHT_INCREMENT_KG` with `weightIncrementKg(unit)` in the top-of-range branch and message. Delete the constant if no use remains.

- [ ] **Step 4: Run unit tests and confirm green**

Run: `.\gradlew.bat testDebugUnitTest --console=plain`
Expected: all unit tests pass.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/buhat/tracker/domain app/src/test/java/com/buhat/tracker/domain
git commit -m "feat: add effective unit and working rest helpers"
```

---

## Task 2: Rest override in set logging and exercise flows

**Files:**
- Modify: `app/src/main/java/com/buhat/tracker/data/local/BuhatDao.kt`
- Modify: `app/src/main/java/com/buhat/tracker/data/BuhatRepository.kt`
- Test: `app/src/androidTest/java/com/buhat/tracker/data/RestTimerRepositoryTest.kt`

**Interfaces:**
- Produces: `BuhatDao.sessionExerciseById(id: String): SessionExercise?`
- Produces: `BuhatDao.observeExercise(id: String): Flow<Exercise?>`
- Produces: `BuhatDao.observeAllExercises(): Flow<List<Exercise>>`
- Produces: `BuhatRepository.observeExercise(id: String): Flow<Exercise?>`
- Produces: `BuhatRepository.allExercises(): Flow<List<Exercise>>`
- Produces: `HomeDashboard.exercises: List<Exercise>`
- `logSet` resolves working rest from the exercise override in the same write path.

- [ ] **Step 1: Write failing data tests**

In `RestTimerRepositoryTest`, set the first session exercise's preferences through `repo.saveExercisePreferences`, then log each type and assert the scheduled end time:
```kotlin
@Test fun workingRestOverrideAppliesOnlyToWorkingSets() = runTest {
    val sessionId = repo.startSession(seededId("template", "Push Day A"))
    val exercise = repo.sessionExercises(sessionId).first().first()
    repo.saveExercisePreferences(exercise.exerciseId, null, 180)

    repo.logSet(exercise.id, SetType.WARMUP, 40.0, 10, 4)
    assertRestNear(45)
    repo.logSet(exercise.id, SetType.WORKING, 80.0, 8, 2)
    assertRestNear(180)
    repo.logSet(exercise.id, SetType.FAILURE, 80.0, 6, 0)
    assertRestNear(150)
}

@Test fun zeroWorkingOverrideStartsNoRest() = runTest {
    val sessionId = repo.startSession(seededId("template", "Push Day A"))
    val exercise = repo.sessionExercises(sessionId).first().first()
    repo.saveExercisePreferences(exercise.exerciseId, null, 0)

    repo.logSet(exercise.id, SetType.WORKING, 80.0, 8, 2)

    assertNull(restEndsAt())
}
```
Use a small helper that brackets the write with `System.currentTimeMillis()` the way the existing test does; do not reimplement `restEndsAt`.

Add a flow assertion in `ContentRepositoryTest` or here:
```kotlin
@Test fun allExercisesKeepsArchivedRows() = runTest {
    val id = repo.saveExercise(null, "Belt Squat", "Quads", "Machine")
    repo.archiveExercise(id)
    assertTrue(repo.allExercises().first().any { it.id == id })
    assertNull(repo.observeExercise(id).first()?.archivedAt == null ?: null) // observeExercise still returns archived rows
}
```
Prefer two clear assertions: the archived row is present in `allExercises`, and `observeExercise` returns it.

- [ ] **Step 2: Run the focused data tests and confirm red**

Run: `.\gradlew.bat pixel6api35DebugAndroidTest --rerun "-Pandroid.testInstrumentationRunnerArguments.class=com.buhat.tracker.data.RestTimerRepositoryTest,com.buhat.tracker.data.ContentRepositoryTest" --console=plain`
Expected: compile failure or assertion failure.

- [ ] **Step 3: Implement the DAO and repository plumbing**

Add the three DAO methods. In `logSet`, after the active check:
```kotlin
val sessionExercise = dao.sessionExerciseById(sessionExerciseId)
val exercise = sessionExercise?.let { dao.exerciseById(it.exerciseId) }
val effectiveWorking = exercise?.effectiveWorkingRest(current.restWorkingSeconds) ?: current.restWorkingSeconds
...
restSecondsFor(setType, current.restWarmupSeconds, effectiveWorking, current.restFailureSeconds)
```
Add `HomeDashboard.exercises` and include `database.dao().observeAllExercises()` in `homeDashboard`'s combine.

- [ ] **Step 4: Run the focused data tests and confirm green**

Run the same command. Expected: pass.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/buhat/tracker/data app/src/androidTest/java/com/buhat/tracker/data
git commit -m "feat: resolve per-exercise working rest when logging"
```

---

## Task 3: Exercise Settings dialog and library menu

**Files:**
- Modify: `app/src/main/java/com/buhat/tracker/ui/screens/ExerciseLibraryScreen.kt`
- Modify: `app/src/main/java/com/buhat/tracker/ui/screens/ActiveWorkoutScreen.kt`
- Modify: `app/src/main/java/com/buhat/tracker/ui/screens/SettingsScreen.kt`
- Test: `app/src/androidTest/java/com/buhat/tracker/ui/ScreenTest.kt`, `SettingsScreenTest.kt`

**Interfaces:**
- Produces: `internal fun ExerciseSettingsDialog(repository, exercise, settings, onDismiss)` in `ExerciseLibraryScreen.kt`.
- Library rows: every row opens a menu. Premade rows offer only "Exercise settings"; custom rows keep Edit, Archive, Delete plus "Exercise settings".
- Active target card: a 48dp "Settings" text button tagged `target-exercise-settings`.
- Dialog tags: `exercise-settings`, `segmented-Default`, `segmented-kg`, `segmented-lb`, `pref-rest-value-minus`, `pref-rest-value-plus`, `pref-rest-default`, `save-exercise-prefs`.

- [ ] **Step 1: Write failing UI tests**

In `ScreenTest`:
```kotlin
@Test fun premadeExerciseSettingsChangesUnitAndKeepsContent() {
    openWorkouts()
    compose.onNodeWithTag("workouts-menu").performClick()
    clickMenuTag("open-library")
    compose.onNodeWithTag("exercise-menu-Bench Press").performClick()
    awaitMenuItem("Exercise settings")
    compose.onNodeWithText("Edit exercise").assertDoesNotExist()
    compose.onNodeWithText("Exercise settings").performClick()

    compose.onNodeWithTag("segmented-lb").performClick()
    compose.onNodeWithTag("save-exercise-prefs").performClick()
    compose.waitUntil(5_000) { /* dialog gone */ }

    val bench = runBlocking {
        ApplicationProvider.getApplicationContext<BuhatApplication>().container.repository
            .exercise(seededId("exercise", "Bench Press"))!!
    }
    assertEquals("LB", bench.weightUnitOverride)
    assertEquals("Bench Press", bench.name)
    assertEquals("Chest", bench.muscleGroup)
    assertEquals("Barbell", bench.equipment)
}
```
Add a custom-row case asserting all four menu items exist. In `SettingsScreenTest`, replace the units copy expectation with the new copy.

- [ ] **Step 2: Run the focused UI tests and confirm red**

Run: `.\gradlew.bat pixel6api35DebugAndroidTest --rerun "-Pandroid.testInstrumentationRunnerArguments.class=com.buhat.tracker.ui.ScreenTest,com.buhat.tracker.ui.SettingsScreenTest" --console=plain`
Expected: missing menu item or missing tags.

- [ ] **Step 3: Implement the dialog and entry points**

Dialog state: `unitOverride: String?` and `restOverride: Int?`, seeded from the exercise. Unit segmented control: `Default`, `kg`, `lb`; the supporting line names the current global unit. Working rest: `BuhatStepper` with `step = settings.restIncrementSeconds`, `min = 0`, `max = REST_SECONDS_MAX`, `tagPrefix = "pref-rest-value"`; its value is `restOverride ?: settings.restWorkingSeconds`. A `pref-rest-default` text button clears the override and is shown only when one is set. Save calls `repository.saveExercisePreferences` in a coroutine and dismisses on success; show `ContentValidationException.message` on failure.

Library: always render the menu. Build the item list by `isCustom`. Track `settingsExercise` and show the dialog.

Active: add the Settings button to the target card. It opens the same dialog with the selected exercise's row; hide it when the row is missing.

Settings copy: `"Weight steps are 2.5 kg or 5 lb. Set a per-exercise unit and working rest in Exercise settings."`

- [ ] **Step 4: Run the focused UI tests and confirm green**

Run the same command. Expected: pass.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/buhat/tracker/ui/screens app/src/androidTest/java/com/buhat/tracker/ui
git commit -m "feat: add exercise settings dialog"
```

---

## Task 4: Active Workout effective unit

**Files:**
- Modify: `app/src/main/java/com/buhat/tracker/ui/screens/ActiveWorkoutScreen.kt`
- Test: `app/src/androidTest/java/com/buhat/tracker/ui/ActiveWorkoutTest.kt`

**Interfaces:**
- Consumes: `repository.observeExercise(id)`, `Exercise.effectiveUnit(global)`, `weightIncrementKg(unit)`.
- Target, prefill, logged rows, set editor, weight label, and step buttons all use the selected exercise's effective unit.
- Missing row falls back to `settings.unit`.

- [ ] **Step 1: Write failing UI tests**

```kotlin
@Test fun lbExerciseLogsAndTargetsInPounds() {
    runBlocking {
        val repo = ApplicationProvider.getApplicationContext<BuhatApplication>().container.repository
        repo.saveExercisePreferences(seededId("exercise", "Bench Press"), "LB", null)
        val session = repo.startSession(seededId("template", "Push Day A"))
        val bench = repo.sessionExercises(session).first().first()
        repo.logSet(bench.id, SetType.WORKING, weightKg = lbToKg(135.0), reps = 10, rir = 2)
        repo.finishSession(session)
    }
    startPushDayA()

    awaitText("140 lb × 6")
    compose.onNodeWithText("Top of range reached. Add 5 lb, restart at 6.").assertExists()
    compose.onNodeWithTag("weight-input").assertTextContains("135")
    compose.onNodeWithTag("weight-input-plus").performClick()
    compose.onNodeWithTag("weight-input").assertTextContains("140")

    compose.onNodeWithTag("weight-input").performTextReplacement("135")
    compose.onNodeWithTag("reps-input").performTextReplacement("8")
    compose.onNodeWithTag("log-set").performScrollTo().performClick()
    awaitText("135 lb × 8")
    val stored = runBlocking {
        val repo = ApplicationProvider.getApplicationContext<BuhatApplication>().container.repository
        val session = repo.activeSession().first()!!
        val exercise = repo.sessionExercises(session.id).first().first()
        repo.loggedSets(exercise.id).first().last().weightKg
    }
    assertEquals(lbToKg(135.0), stored, 0.0001)
}
```
Add a mixed-unit test: create a custom template with Bench Press and Leg Extension, set Bench to LB and Leg Extension to KG, start it, log one working set each, and assert each selected exercise's row renders its own unit.

- [ ] **Step 2: Run the focused UI test and confirm red**

Run: `.\gradlew.bat pixel6api35DebugAndroidTest --rerun "-Pandroid.testInstrumentationRunnerArguments.class=com.buhat.tracker.ui.ActiveWorkoutTest" --console=plain`
Expected: kg text where lb is expected.

- [ ] **Step 3: Implement effective-unit observation**

```kotlin
val exerciseRow by remember(selected?.exerciseId) {
    selected?.let { repository.observeExercise(it.exerciseId) } ?: flowOf(null)
}.collectAsState(null)
val unit = exerciseRow?.effectiveUnit(settings.unit) ?: settings.unit
```
Pass `unit` to `nextTarget`. Replace `WEIGHT_INCREMENT_KG` with `weightIncrementKg(unit)` in both step handlers. The existing target, prefill, logged row, and editor calls already take `unit`; they now receive the effective value.

- [ ] **Step 4: Run the focused UI test and confirm green**

Run the same command. Expected: pass.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/buhat/tracker/ui/screens/ActiveWorkoutScreen.kt app/src/androidTest/java/com/buhat/tracker/ui/ActiveWorkoutTest.kt
git commit -m "feat: use the exercise unit in active workout"
```

---

## Task 5: Progress and Home effective unit

**Files:**
- Modify: `app/src/main/java/com/buhat/tracker/ui/screens/ProgressScreen.kt`
- Modify: `app/src/main/java/com/buhat/tracker/ui/screens/HomeScreen.kt`
- Modify: `app/src/main/java/com/buhat/tracker/data/BuhatRepository.kt`
- Test: `app/src/androidTest/java/com/buhat/tracker/ui/ProgressScreenTest.kt`

**Interfaces:**
- Consumes: `repository.allExercises()`, `HomeDashboard.exercises`, `Exercise.effectiveUnit(global)`.
- Progress uses the selected exercise's effective unit for the chart, best e1RM, heaviest set, and past sets.
- Home uses the selected trend exercise's effective unit. Volume, body weight, and height stay global.

- [ ] **Step 1: Write failing UI tests**

```kotlin
@Test fun benchProgressAndHomeTrendUseTheExerciseUnitWhileVolumeStaysGlobal() {
    runBlocking {
        val repo = container.repository
        repo.saveExercisePreferences(benchId, "LB", null)
        container.recordBenchWorkout()
    }
    awaitValue("home-trend-latest", "235.2 lb")
    homeValue("home-volume-value", "2160 kg")

    openProgress()
    progressItem("progress-best-value").assertTextEquals("235.2 lb")
    progressItem("progress-heaviest-value").assertTextEquals("176.4 lb × 10")
    progressItem("history-${/* recorded id */}").performClick()
    compose.onNodeWithText("176.4 lb × 10").assertExists()
}
```
Use the captured `recordBenchWorkout` fixture and its literal 80 kg × 10 values; compute 80 × 2.20462 = 176.3696 → 176.4 lb and 106.6667 × 2.20462 = 235.2 lb by hand.

Add an archived-exercise case: set an override, archive the exercise, and assert Progress still renders its unit.

- [ ] **Step 2: Run the focused UI test and confirm red**

Run: `.\gradlew.bat pixel6api35DebugAndroidTest --rerun "-Pandroid.testInstrumentationRunnerArguments.class=com.buhat.tracker.ui.ProgressScreenTest" --console=plain`
Expected: kg text where lb is expected.

- [ ] **Step 3: Implement effective-unit resolution**

Progress: load `repository.allExercises()`; after the selected ID is known:
```kotlin
val exerciseUnit = loaded?.exercises?.firstOrNull { it.id == exerciseId }
    ?.effectiveUnit(loaded.unit) ?: unit
```
Use `exerciseUnit` for `StrengthTrend`, best e1RM, heaviest set, and `WorkoutHistoryCard`. Keep `unit` for nothing exercise-specific.

Home: resolve `dashboard.exercises.firstOrNull { it.id == exercise.exerciseId }?.effectiveUnit(loaded.unit) ?: loaded.unit` and pass it to `StrengthTrend`. Keep `loaded.unit` for profile and volume.

- [ ] **Step 4: Run the focused UI test and confirm green**

Run the same command. Expected: pass.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/buhat/tracker/ui/screens app/src/main/java/com/buhat/tracker/data/BuhatRepository.kt app/src/androidTest/java/com/buhat/tracker/ui/ProgressScreenTest.kt
git commit -m "feat: use the exercise unit in progress and home"
```

---

## Task 6: Full validation and review

**Files:**
- Modify: `.opencode/skills/e2e-testing/references/test-catalog.md`

- [ ] **Step 1: Run the full E2E suite**

Run: `powershell -NoProfile -ExecutionPolicy Bypass -File .opencode/skills/e2e-testing/scripts/run-e2e.ps1`
Expected: unit and device counts printed with 0 failed, no INTERNET permission.

- [ ] **Step 2: Mutation-check the new tests**

For each new behavior, break the implementation on purpose (return the global unit, ignore the rest override, remove the library menu item), confirm the matching test goes red, then restore. Record the red output.

- [ ] **Step 3: Update the test catalog**

Add rows for the effective-unit helpers, lb progression, rest resolution, exercise settings dialog, lb logging, and Progress/Home units.

- [ ] **Step 4: Review**

Run `ponytail-review` on the diff, then `requesting-code-review`. Security pass is skipped: no auth, network, file, SQL, crypto, or dependency change, and the two new inputs (unit string, rest 0..600) are already validated by `saveExercisePreferences`.

- [ ] **Step 5: Show evidence**

Paste the final unit and E2E terminal output. Do not claim done without it.
