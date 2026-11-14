# Phase 18 Mid-Workout Exercise Changes Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add, edit, replace, remove, and reorder exercises during an active workout, and offer a transactional template save-back only when the session structure changed.

**Architecture:** Structure changes are repository operations inside `sessionWriteMutex` and one Room transaction. Each operation sets `WorkoutSession.structureDirty`. Finish reads that flag in the UI, offers "Update template" or "This workout only", and the repository writes the template and finishes the session in one transaction.

**Tech Stack:** Kotlin, Jetpack Compose, Room, JUnit4, Compose UI tests on the managed Pixel 6 / API 35 emulator.

**Spec:** `planning/buhat-tracker-roadmap.md` sections 4.4, 4.5, and Phase 18.

## Global Constraints

- Prescriptions: sets 1..10, reps 1..30, `repMin <= repMax`, RIR 0..5.
- Session order is `SessionExercise.position`, unique per session, written as 1..N.
- A replaced row keeps its logged sets; the replacement row points back through `replacesSessionExerciseId`.
- Template save-back excludes rows referenced by another row's `replacesSessionExerciseId`.
- Finish + template save-back is one transaction. Any failure leaves the workout ACTIVE.
- No Room schema, backup format, or DataStore change.
- No new dependency.

## Review Focus

- Removing an exercise that has any logged set must fail, including warmups.
- Replacing before any set updates in place and creates no link or extra history row.
- A failed finish must keep the prompt open with a visible error and the session ACTIVE.
- Reorder alone must set the dirty flag and still finish through "This workout only".
- Duplicate exercises in one session are rejected so template save-back validation cannot fail after the fact.

---

### Task 1: Repository structure operations

**Files:**
- Modify: `app/src/main/java/com/buhat/tracker/domain/ContentRules.kt`
- Modify: `app/src/main/java/com/buhat/tracker/data/local/BuhatDao.kt`
- Modify: `app/src/main/java/com/buhat/tracker/data/BuhatRepository.kt`
- Test: `app/src/androidTest/java/com/buhat/tracker/data/SessionRepositoryTest.kt`

**Interfaces:**
- Produces:
  - `fun validatePrescription(workingSets: Int, repMin: Int, repMax: Int, targetRir: Int?): String?`
  - `suspend fun addSessionExercise(sessionId: String, exerciseId: String, workingSets: Int, repMin: Int, repMax: Int, targetRir: Int?): String`
  - `suspend fun editSessionExercise(sessionExerciseId: String, workingSets: Int, repMin: Int, repMax: Int, targetRir: Int?)`
  - `suspend fun replaceSessionExercise(sessionExerciseId: String, exerciseId: String): String`
  - `suspend fun removeSessionExercise(sessionExerciseId: String)`
  - `WorkoutSession.structureDirty` is true after add, edit, replace, remove, or reorder.
- DAO additions: `markSessionStructureDirty`, `updateSessionExercise`, `deleteSessionExerciseById`, `clearReplacementLinks`.

- [ ] **Step 1: Write the failing data tests**

Add to `SessionRepositoryTest`:
  1. `addingAnExerciseAppendsItAndMarksTheSessionDirty`: start Push Day A, add Seated Cable Row with 4/6/8/RIR 1; assert last row name, position 4, prescriptions, `session.structureDirty`, and the same values through a new `BuhatRepository`.
  2. `editingPrescriptionsKeepsLoggedSetsAndWorkingIndices`: log two working sets on Bench; edit to 4/6/8/RIR 1; assert row values, the two sets unchanged, dirty true.
  3. `removingAnUnloggedExerciseRenumbersPositionsAndRejectsLoggedRows`: remove Incline Dumbbell Press; assert positions 1..2 and dirty; then log a set on Bench and assert removal throws `SessionValidationException("An exercise with logged sets cannot be removed.")` and the row still exists.
  4. `replacingAfterSetsInsertsALinkedRowAndKeepsOriginalSets`: log two Bench sets, replace with Seated Cable Row; assert row order `[Bench, Seated Cable Row, Incline, Cable Fly]`, link to Bench, 2 sets still on Bench, 0 on the replacement, dirty true; log two replacement sets; `finishSession()`; history has Bench 2 sets and Seated Cable Row 2 sets as separate occurrences.
  5. `replacingBeforeAnySetsUpdatesTheRowInPlace`: replace Bench with Seated Cable Row; assert same row id, no replacement link, no extra row.
  6. `reorderMarksTheStructureDirtyAndFinishWithoutUpdateLeavesTheTemplate`: add dirty assertions to the existing reorder test.
  7. `structureOperationsRejectDuplicatesAndInactiveSessions`: adding Bench again and replacing with an exercise already in the session fail; structure edits after finish fail; rows unchanged.

- [ ] **Step 2: Run the data tests and confirm they fail**

Run: `.\gradlew.bat pixel6api35DebugAndroidTest --tests "com.buhat.tracker.data.SessionRepositoryTest" --rerun`
Expected: compile failure on the missing methods.

- [ ] **Step 3: Implement the domain validator and DAO queries**

Move the per-exercise checks in `validateTemplate` into `validatePrescription` and call it from both places. Add the four DAO methods. `markSessionStructureDirty` sets `structureDirty = 1`.

- [ ] **Step 4: Implement the repository operations**

All four run in `sessionWriteMutex.withLock` and `database.withTransaction`:
- Load the row/session, require `ACTIVE`.
- Validate prescriptions with `validatePrescription`.
- Reject an exercise already in the session unless it is the row being replaced.
- Add: insert at `max(position) + 1`, mark dirty, return the new id.
- Edit: update the prescription columns only, mark dirty.
- Replace: no sets -> update exercise id and name snapshot in place; sets -> negate positions, insert the new row at 0 after the original with the link, rewrite 1..N, mark dirty. Return the row to select.
- Remove: reject when `selectSetsFor(id)` is non-empty; clear links, delete, negate + rewrite 1..N, mark dirty.
- Reorder: mark dirty after writing the order.

- [ ] **Step 5: Run the data tests and confirm they pass**

Run: `.\gradlew.bat pixel6api35DebugAndroidTest --tests "com.buhat.tracker.data.SessionRepositoryTest" --rerun`
Expected: PASS.

### Task 2: Finish prompt and transactional template save-back

**Files:**
- Modify: `app/src/main/java/com/buhat/tracker/data/BuhatRepository.kt`
- Test: `app/src/androidTest/java/com/buhat/tracker/data/SessionRepositoryTest.kt`

**Interfaces:**
- Produces: `suspend fun finishSession(sessionId: String, updateTemplate: Boolean = false)`
- `true`: build drafts from position order excluding replaced rows, then update the custom template, recreate a missing one from `templateNameSnapshot`, or copy a premade with a unique `"(copy)"` / `"(copy N)"` name.
- `false`: finish and clear the dirty flag without touching the template.

- [ ] **Step 1: Write the failing finish tests**

  1. `finishThisWorkoutOnlyLeavesTemplateAndOrderUnchanged`: edit + reorder, `finishSession(sessionId)`; template rows unchanged, session FINISHED, dirty false.
  2. `finishUpdateTemplateReplacesTheCustomTemplate`: custom 2-exercise template; remove one, add another, finish with update; template rows match the session rows and order.
  3. `finishUpdateTemplateCopiesAPremadeWithAUniqueName`: finish a changed Push Day A with update; `Push Day A (copy)` exists with the session rows; premade unchanged; a second changed session creates `Push Day A (copy 2)`.
  4. `finishUpdateTemplateRecreatesADeletedCustomTemplate`: start a custom template, delete it, change the session, finish with update; a same-named custom template holds the session rows.
  5. `finishUpdateTemplateExcludesReplacedRows`: replace after sets, finish with update; template has the replacement, not the replaced row.
  6. `failedTemplateSaveBackLeavesTheWorkoutActive`: create a trigger that aborts `TemplateExercise` insert/delete, finish with update, assert throw, session ACTIVE, dirty true, no template change; drop the trigger and finish again.
  7. `finishingWithoutStructureChangesDoesNotTouchTheTemplate`: log sets only; finish; template unchanged and dirty false.

- [ ] **Step 2: Run and confirm the tests fail**

Run: `.\gradlew.bat pixel6api35AndroidTest --tests "com.buhat.tracker.data.SessionRepositoryTest" --rerun`
Expected: FAIL on the new finish behavior.

- [ ] **Step 3: Implement finish inside one transaction**

Load the session, require at least one working set, optionally save back the template via the existing `saveTemplate`, then set FINISHED, `finishedAt`, and `structureDirty = false`. Clear rest after the transaction commits.

- [ ] **Step 4: Run and confirm the tests pass**

Run: `.\gradlew.bat pixel6api35DebugAndroidTest --tests "com.buhat.tracker.data.SessionRepositoryTest" --rerun`
Expected: PASS.

### Task 3: Active-workout UI

**Files:**
- Modify: `app/src/main/java/com/buhat/tracker/ui/screens/ActiveWorkoutScreen.kt`
- Modify: `app/src/main/java/com/buhat/tracker/ui/screens/TemplateEditorScreen.kt` (make `NumberField` internal for reuse)
- Test: `app/src/androidTest/java/com/buhat/tracker/ui/ActiveWorkoutTest.kt`

**Interfaces:**
- Menu: `add-session-exercise` ("Add exercise").
- Selected exercise: `edit-session-exercise`, `replace-session-exercise`, `remove-session-exercise`.
- Picker: `session-picker-search`, `session-picker-<name>`.
- Prescription dialog: `session-sets`, `session-rep-min`, `session-rep-max`, `session-rir`, `save-session-exercise`, `session-dialog-error`.
- Finish prompt: `finish-prompt`, `finish-update-template`, `finish-this-workout-only`, `finish-error`.

- [ ] **Step 1: Write the failing UI tests**

  1. `addExerciseMidWorkoutPersistsAndPromptsOnFinish`: menu > Add exercise > pick Seated Cable Row > set 4/6/8/RIR 1 > save; chip appears, session row and dirty flag match; Finish shows the prompt.
  2. `editExerciseUpdatesTheTargetRangeAndThisWorkoutOnlyLeavesTheTemplate`: log a set, Edit Bench to 4/6/8, save; target card reads 4–8; Finish > `finish-this-workout-only`; history keeps 4/6/8 and the premade template stays 3/6/8.
  3. `replaceAfterSetsKeepsEarlierSetsAndSelectsTheReplacement`: log two sets, Replace with Seated Cable Row, log two sets; finish with this workout only; history rows keep 2 + 2 under the right names.
  4. `unloggedExerciseCanBeRemovedAndALoggedOneCannot`: select Incline, Remove, chip disappears; select Bench, log a set, Remove is disabled.
  5. `finishWithUpdateTemplateCreatesACustomCopyOfThePremade`: edit Bench, log a set, Finish > `finish-update-template`; premade unchanged, `Push Day A (copy)` has the edited row.
  6. `failedTemplateSaveBackShowsTheErrorAndKeepsTheWorkoutActive`: abort template writes with a trigger; Finish > Update template; `finish-error` appears, session still ACTIVE; drop the trigger, retry, workout finishes.
  7. Update `reorderKeepsSelectionSetsAndRestThroughRecreationAndFinish`: after Finish, click `finish-this-workout-only`; template assertions stay unchanged.

- [ ] **Step 2: Run and confirm the UI tests fail**

Run: `.\gradlew.bat pixel6api35DebugAndroidTest --tests "com.buhat.tracker.ui.ActiveWorkoutTest" --rerun`
Expected: FAIL on the missing controls.

- [ ] **Step 3: Implement the UI**

- Add the menu item, selected-exercise action row, picker dialog, and prescription dialog.
- Feed repository errors back into the dialogs.
- Finish menu: prompt when `session.structureDirty`, otherwise finish directly.
- Finish prompt keeps its error and stays open until the write succeeds or the user cancels.

- [ ] **Step 4: Run and confirm the UI tests pass**

Run: `.\gradlew.bat pixel6api35DebugAndroidTest --tests "com.buhat.tracker.ui.ActiveWorkoutTest" --rerun`
Expected: PASS.

### Task 4: Catalog, full suite, and release checks

**Files:**
- Modify: `.opencode/skills/e2e-testing/references/test-catalog.md`

- [ ] **Step 1: Update the test catalog** with the new repository and UI cases and the updated reorder finish path.
- [ ] **Step 2: Run the full unit suite** `.\gradlew.bat testDebugUnitTest --console=plain`; expected PASS.
- [ ] **Step 3: Run the full device suite** `.\gradlew.bat pixel6api35DebugAndroidTest --rerun`; expected PASS with no regressions.
- [ ] **Step 4: Confirm no schema or backup change** with `git diff --stat app/schemas app/src/main/java/com/buhat/tracker/data/Backup.kt`.
