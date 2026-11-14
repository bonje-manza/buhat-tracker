# Phase 19 Unilateral Tracking Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans. Steps use checkbox (`- [ ]`) syntax.

**Goal:** Left and Right log, progress, rest, history, and analytics independently while each side counts as one set.

**Architecture:** `side` is already stored on `LoggedSet` and survives backup. Phase 19 only threads it through writing, domain lookups, analytics, and the three screens that show exercise data.

**Tech Stack:** Kotlin, Compose, Room, JUnit, Compose UI tests.

**Spec:** `planning/buhat-tracker-roadmap.md` section 4.4, "Unilateral tracking (Phase 19)".

## Execution record

**Complete:** All six tasks and the review fix pass are verified. Final `run-e2e.ps1`: 133 unit tests, 254 device tests, zero failures or skips. The checklists below preserve the original implementation instructions; this execution record records their outcome.

- Tasks 1–4 committed as `cb5a8bd`, `0cc690c`, `c80e554`, and `ae431df`.
- Task 5 initially failed both side UI cases, then passed all 15 Progress tests. Removing side filtering made both cases fail again.
- The spec also requires the Exercise settings switch, which this original plan omitted. Added it with a failing UI regression.
- Additional six-set and source-position UI tests verify manual overrides resume alternation, completion requires both sides, and fallback/prefill/RIR stay side-specific.
- SQL seed regression verifies independent unsided/Left/Right records using literal 144/96/48 e1RM values. Removing side grouping made it fail.
- Review regressions first failed for null-side edits, side changes resetting set type, missing per-side history numbers, and archived unilateral prescription copy.
- Expanded history retains both sides and unsided legacy rows, while charts, statistics, and collapsed summaries filter the selected side.
- Device commands below describe the original test targets. Execution uses `run-e2e.ps1 -Feature <class>` because Gradle-managed device tests use instrumentation class filtering.
- Review fixes passed the full suite: untouched unsided edits stay null, side switches retain the chosen set type, history displays per-side positions, and archived template rows retain per-side prescription copy.
- A first full-run Compose layout exception in the existing Settings active-session backup guard did not reproduce in the isolated test or final full suite.

## Global Constraints

- Storage stays kilograms. `side` is `LEFT`, `RIGHT`, or null; null means bilateral, unsided legacy, or imported.
- `workingIndex` stays the global completion-order index. Per-side positions are computed by filtering.
- Bilateral views use unsided sets only.
- Rest starts after every logged set, including each side. `logSet` already does this; no pair branch.
- Do not change the schema, backup format, or migration.
- Do not touch Phase 20-22 work: whole-workout history mode, Strong CSV import, release checks.

## Review Focus

- A set's side is corrected in the editor; progression positions and PRs must follow the new side without renumbering global indices.
- Old unsided sets must never supply a side target.
- Side switches must re-render prefill and target for the newly selected side.
- Analytics `bestBefore` seeds must not compare Left against Right.
- Ten-plus side sets in one session must keep unique `(sessionExerciseId, workingIndex)`.

---

### Task 1: Domain per-side positions, source lookup, prefill, default side

**Files:**
- Modify: `app/src/main/java/com/buhat/tracker/domain/Progression.kt`
- Modify: `app/src/main/java/com/buhat/tracker/domain/SessionRules.kt`
- Test: `app/src/test/java/com/buhat/tracker/domain/ProgressionTest.kt`
- Test: `app/src/test/java/com/buhat/tracker/domain/SessionRulesTest.kt`

**Interfaces:**
- `numberWorkingSets(sets: List<LoggedSetRecord>, side: SetSide? = null): List<SetAtPosition>`
- `sourceSetForPosition(history, exerciseId, position, source, templateIdSnapshot, side: SetSide? = null): LoggedSetRecord?`
- `prefillFor(source: LoggedSetRecord?, lastSetInSession: LoggedSetRecord?, side: SetSide? = null): Prefill`
- `defaultSide(sets: List<LoggedSetRecord>): SetSide`

- [ ] **Step 1: Write failing tests**

`ProgressionTest`:
- `perSideNumberingCountsOnlyThatSidesWorkingSets`: left/right/left rows numbered 1,2,3 globally; `numberWorkingSets(sets, LEFT)` returns the two left rows at positions 1,2.
- `sideLookupUsesThePositionsOwnSideNotTheGlobalWorkingIndex`: left idx 1, right idx 2, left idx 3; `sourceSetForPosition(..., position = 2, side = LEFT)` returns the second left row (idx 3).
- `sourceLookupFlattensOccurrencesAndFiltersBySide`: one session, two `HistoryExercise` rows for the same exercise; right row supplies right position 1.
- `unsidedSetsNeverSourceASideTarget`: only an unsided set exists; side LEFT returns null.

`SessionRulesTest`:
- `prefillFallbackOnlyUsesTheSelectedSide`: no source, last set LEFT, side RIGHT gives blank weight and reps.
- `defaultSideAlternatesFromTheMostRecentSide`: last RIGHT gives LEFT, last LEFT gives RIGHT, none or unsided gives LEFT.

- [ ] **Step 2: Run tests and confirm failure**

Run: `.\gradlew.bat testDebugUnitTest --tests "com.buhat.tracker.domain.ProgressionTest" --tests "com.buhat.tracker.domain.SessionRulesTest" --console=plain`
Expected: FAIL on unresolved `side` arguments and `defaultSide`.

- [ ] **Step 3: Implement**

- `numberWorkingSets` filters `side` then numbers in completion order.
- `sourceSetForPosition` flattens every matching `HistoryExercise` row in order, filters progression sets by `side`, uses `getOrNull(position - 1) ?: lastOrNull()`.
- `prefillFor` filters the fallback last set by `side`. Source is already side-filtered by the caller.
- `defaultSide` returns the opposite of the latest completed set with a side, else `LEFT`.

- [ ] **Step 4: Run tests and confirm pass**

Run the Step 2 command. Expected: PASS.

- [ ] **Step 5: Commit**

`git add` the four files; `git commit -m "feat: compute progression per side"`.

### Task 2: Side-aware analytics and PR seed

**Files:**
- Modify: `app/src/main/java/com/buhat/tracker/domain/Analytics.kt`
- Modify: `app/src/main/java/com/buhat/tracker/data/local/BuhatDao.kt`
- Modify: `app/src/main/java/com/buhat/tracker/data/BuhatRepository.kt`
- Test: `app/src/test/java/com/buhat/tracker/domain/AnalyticsTest.kt`
- Test: `app/src/androidTest/java/com/buhat/tracker/data/HomeDashboardTest.kt`

**Interfaces:**
- `fun sideKey(exerciseId: String, side: SetSide?): String` (unsided key stays `exerciseId`)
- `exerciseStats(history, exerciseId, side: SetSide? = null)`
- `e1rmTrend(history, exerciseId, rangeDays, nowMillis, side: SetSide? = null)`
- `prSetIds(history, bestBefore: Map<String, Double> = emptyMap())` stays all-sides
- `prSetIds(history, side: SetSide?, bestBefore: Map<String, Double> = emptyMap())` filters one side or unsided
- `ExerciseBestE1rm` gains `side: SetSide?`

- [ ] **Step 1: Write failing unit tests**

- `prsCompareWithinTheSameExerciseAndSide`: Left 100x5 then Right 50x5; next session Left 90x5 (not a PR) and Right 60x5 (PR). Assert exactly the first Left, first Right, and later Right ids.
- `sideFilteredStatsAndTrendUseOnlyThatSide`: Right-only stats/trend ignore Left sets.
- `sideFilteredPrsIgnoreTheOtherSide`: `prSetIds(history, SetSide.LEFT)` returns only Left PRs.
- `seededPrMapUsesTheSideKey`: seed `sideKey("bench", LEFT)` at 100; Left 90 is not a PR, Right 60 is.

- [ ] **Step 2: Run and confirm failure**

Run: `.\gradlew.bat testDebugUnitTest --tests "com.buhat.tracker.domain.AnalyticsTest" --console=plain`
Expected: FAIL on unresolved side arguments.

- [ ] **Step 3: Implement**

- `prSetIds` keys by `sideKey`; the side overload filters sets to the requested side (null means unsided).
- `exerciseStats` and `e1rmTrend` filter each set with `it.side == side` and keep flattening all occurrences.
- `observeBestE1rmBefore` adds `ls.side AS side`, groups by `se.exerciseId, ls.side`; repository maps `sideKey(exerciseId, side)`.
- `fourWeekStats` unchanged except PRs now separate by side.

- [ ] **Step 4: Run unit tests, then the HomeDashboard device test**

Run: `.\gradlew.bat testDebugUnitTest --tests "com.buhat.tracker.domain.AnalyticsTest" --console=plain`
Expected: PASS.
Run: `.\gradlew.bat pixel6api35DebugAndroidTest --tests "com.buhat.tracker.data.HomeDashboardTest" --rerun`
Expected: PASS.

- [ ] **Step 5: Commit**

`git commit -m "feat: keep analytics separate per side"`.

### Task 3: Persist and edit the side

**Files:**
- Modify: `app/src/main/java/com/buhat/tracker/data/BuhatRepository.kt`
- Test: `app/src/androidTest/java/com/buhat/tracker/data/SessionRepositoryTest.kt`

**Interfaces:**
- `logSet(sessionExerciseId, setType, weightKg, reps, rir, side: SetSide? = null): String`
- `updateSet(setId, setType, weightKg, reps, rir, side: SetSide? = null)`

- [ ] **Step 1: Write failing repository tests**

- `loggedSidesAreStoredInCompletionOrderAndRestStartsAfterEach`: log Left then Right with rest 120; stored sides match, working indices are 1 and 2, `restEndsAt` is future after each call.
- `correctingASideKeepsTheWorkingIndex`: log Left and Right, update the first to Right; both rows read Right and indices stay 1, 2.

- [ ] **Step 2: Run and confirm failure**

Run: `.\gradlew.bat pixel6api35DebugAndroidTest --tests "com.buhat.tracker.data.SessionRepositoryTest" --rerun`
Expected: FAIL because `logSet` does not accept `side`.

- [ ] **Step 3: Implement**

- Insert `side` in `logSet`.
- Copy `side` in `updateSet`. No renumbering on a side change; `workingIndex` is completion order.

- [ ] **Step 4: Run and confirm pass**

Run the Step 2 command. Expected: PASS.

- [ ] **Step 5: Commit**

`git commit -m "feat: store and edit set side"`.

### Task 4: Active workout side selector, labels, editor, per-side prescriptions

**Files:**
- Modify: `app/src/main/java/com/buhat/tracker/ui/screens/ActiveWorkoutScreen.kt`
- Modify: `app/src/main/java/com/buhat/tracker/ui/screens/TemplateEditorScreen.kt`
- Test: `app/src/androidTest/java/com/buhat/tracker/ui/ActiveWorkoutTest.kt`

**Interfaces:**
- Logging tags: `segmented-Left`, `segmented-Right`, `log-set`.
- Prescription label is `Sets/side` when the exercise is unilateral, otherwise `Sets`.

- [ ] **Step 1: Write failing UI tests**

- `unilateralLoggingAlternatesSidesAndKeepsEachSet`: mark Bench unilateral, start Push Day A; Left selected, log, selection becomes Right, log; rows show L and R; stored sides are LEFT then RIGHT.
- `sideSelectionSurvivesRecreation`: log Left, select Right, recreate; Right is still selected.
- `editingASetCanCorrectItsSide`: open a logged Left set, choose Right, save; row reads R and the stored side is RIGHT.
- `perSidePrescriptionCopy`: mark Bench unilateral, open Add exercise, assert `Sets/side`; a bilateral exercise keeps `Sets`.

- [ ] **Step 2: Run and confirm failure**

Run: `.\gradlew.bat pixel6api35DebugAndroidTest --tests "com.buhat.tracker.ui.ActiveWorkoutTest" --rerun`
Expected: FAIL on missing side controls.

- [ ] **Step 3: Implement**

- Derive `side` from `exerciseRow?.isUnilateral`; default via `defaultSide(sets)`, re-derived when the latest logged set changes.
- Position and source lookups use the selected side. Prefill filters by the selected side.
- Pass side to `logSet` and `updateSet`.
- Logged rows and history rows prefix `L` or `R`.
- `SetEditorDialog` gets `isUnilateral`, `side`, and returns the corrected side.
- `SessionPrescriptionDialog` and `DraftRowCard` use `Sets/side` for unilateral rows.

- [ ] **Step 4: Run and confirm pass**

Run the Step 2 command. Expected: PASS.

- [ ] **Step 5: Commit**

`git commit -m "feat: log left and right in the active workout"`.

### Task 5: Progress and Home side controls

**Files:**
- Modify: `app/src/main/java/com/buhat/tracker/ui/screens/ProgressScreen.kt`
- Modify: `app/src/main/java/com/buhat/tracker/ui/screens/HomeScreen.kt`
- Test: `app/src/androidTest/java/com/buhat/tracker/ui/ProgressScreenTest.kt`

**Interfaces:**
- Controls: `segmented-Left` / `segmented-Right`, visible only when the exercise is unilateral.
- Default side: the side of the most recent set for that exercise.

- [ ] **Step 1: Write failing UI tests**

- `unilateralProgressShowsOneSideAndHistoryLabels`: log Left 100x5 and Right 50x5 in one finished workout; Progress summary and trend use the latest side (Right); rows read R; switching to Left shows 100x5 and L.
- `homeTrendUsesTheSelectedSideForUnilateralExercises`: Home shows the side control for a unilateral exercise; Right shows the Right trend, Left shows the Left trend.
- `bilateralExercisesHideTheSideControl`: no `segmented-Left` in Progress or Home.

- [ ] **Step 2: Run and confirm failure**

Run: `.\gradlew.bat pixel6api35DebugAndroidTest --tests "com.buhat.tracker.ui.ProgressScreenTest" --rerun`
Expected: FAIL on missing side controls.

- [ ] **Step 3: Implement**

- Progress: resolve unilateral from `allExercises`; default the side from the most recent set; pass the side to `exerciseStats`, `e1rmTrend`, and `prSetIds`; label history rows L/R.
- Home: same control beside the strength trend; pass the side to `e1rmTrend`.
- Keep aggregate volume and global unit untouched.

- [ ] **Step 4: Run and confirm pass**

Run the Step 2 command. Expected: PASS.

- [ ] **Step 5: Commit**

`git commit -m "feat: show left and right in progress and home"`.

### Task 6: Catalog and full verification

**Files:**
- Modify: `.opencode/skills/e2e-testing/references/test-catalog.md`

- [ ] **Step 1:** Add the new unit, repository, and UI cases to the catalog.
- [ ] **Step 2:** `.\gradlew.bat testDebugUnitTest --rerun-tasks --console=plain` — PASS.
- [ ] **Step 3:** `.\gradlew.bat pixel6api35DebugAndroidTest --rerun` — PASS.
- [ ] **Step 4:** Commit the catalog.

## Self-Review

- Spec coverage: side storage and converter existed; positions, source, prefill, analytics, both side controls, history labels, editing, and per-side copy are covered.
- No schema, backup, or migration change.
- Templates and sessions use the existing `workingSets` value; only the label changes.
