# Phase 5 Home and Progress Implementation Plan

> **For agentic workers:** Use `executing-plans` and `test-driven-development` to implement each task. Track completion below.

**Goal:** Make Home and Progress display real finished workouts, correct analytics, and deletable history.

**Architecture:** Read finished sessions and their exercise/set snapshots transactionally through the existing Room repository. Reuse the existing analytics functions, Compose primitives, profile/settings stores, and navigation. Keep calculations derived so deletion updates every consumer.

**Tech Stack:** Kotlin, Compose Material 3, Room, DataStore, JUnit, Android instrumentation.

**Spec:** `planning/buhat-tracker-roadmap.md`, Phase 5 and sections 4.1–4.6. Visual references: `docs/design/home-profile.png`, `docs/design/progress.png`.

## Global Constraints

- Offline only, stored weights in kilograms, displayed kg/lb through existing settings.
- Warmups appear in history but never contribute to analytics or PRs.
- 1M/3M/1Y/All mean 30/90/365 days/no cutoff. Default is 3M.
- PRs use all-time chronology, strictly greater e1RM. Heaviest ties break by reps.
- Preserve session snapshots, active workouts, and rest state when deleting finished history.
- No dependencies, schema migration, finished-workout editing, export/import, or roadmap Excluded items.
- Work on the existing `phase-5-home-dashboard-progress` branch, based on completed Phase 4 (`bae2bd7`).

## Review Focus

- Archived/deleted exercises and templates retain readable snapshots and selectable history (Task 2).
- Bodyweight, zero reps, warmup-only exercise, and unlogged exercise have honest empty chart/stats (Tasks 1–2).
- Deletion cascades only through the chosen finished workout and leaves an active timer intact (Task 2).
- A PR before the selected range prevents a false in-range badge, and deletion recomputes chronology (Tasks 1–3).
- Unit changes, rotation, long labels, loading/error/retry, and no-profile/no-history states remain usable (Task 3).

## Task 1: All-time exercise statistics

**Files:** `app/src/main/java/com/buhat/tracker/domain/Analytics.kt`, `app/src/test/java/com/buhat/tracker/domain/AnalyticsTest.kt`.

**Interface:** `exerciseStats(history: List<HistorySession>, exerciseId: String): ExerciseStats`, with `bestE1rm: Double?`, `heaviestSet: LoggedSetRecord?`, `sessionCount: Int`.

- [x] Add failing tests using the existing recorded 80 kg × 10/9/8 session and explicit boundary cases. Assert best 106.6667, heaviest 80×10, 3 working sets, 2160 kg volume. Verify empty/bodyweight/zero-rep cases and heaviest ties by reps.
- [x] Run `gradlew.bat testDebugUnitTest --console=plain`, record red output.
- [x] Implement the minimum all-time summary, reuse `e1rm`, and verify 365-day boundary and all-time PR behavior.
- [x] Run the unit suite, record green output.

## Task 2: Finished history and safe deletion

**Files:** `data/local/BuhatDao.kt`, `data/BuhatRepository.kt`, `domain/Models.kt` under `app/src/main/java/com/buhat/tracker/`; new `app/src/androidTest/java/com/buhat/tracker/data/HistoryRepositoryTest.kt`.

**Interfaces:** `BuhatRepository.history(): Flow<List<HistorySession>>`; `deleteFinishedSession(sessionId: String): Unit`. Add snapshot names with defaults to domain history records. Room relation projections remain in `BuhatDao.kt` and require no schema change.

- [x] Write repository tests that log and finish sessions through the real repository. Assert snapshot order, names, warmups, skipped exercises, exclusion of active sessions, and literal analytics. Reuse recorded Phase 3 training values.
- [x] Write deletion tests asserting cascades, reactive history update, recomputed PRs/profile totals, and rejection of active-session deletion without changing its sets/rest state.
- [x] Run tests red on the test phone before implementation, or mutation-check once the phone is connected if unavailable initially.
- [x] Add transactional Room relation query and map into existing history records. Delete with `WHERE status = 'FINISHED'` in one SQL statement, report missing/non-finished sessions.
- [x] Run Room tests and existing session regressions green.

## Task 3: Home dashboard and Progress UI

**Files:** `ui/screens/HomeScreen.kt`, new `ui/screens/ProgressScreen.kt`, `ui/BuhatApp.kt`; new `app/src/androidTest/java/com/buhat/tracker/ui/ProgressScreenTest.kt`; test catalog `.opencode/skills/e2e-testing/references/test-catalog.md`.

**Interfaces:** Home receives `onOpenProgress: (String?) -> Unit`. Tabs retain selected Progress exercise and range. Share small trend/stat rendering functions between the two screens, using existing `BuhatCard`, `BuhatLineChart`, chips, badges, segmented control, and empty state.

- [x] Write Compose tests first: log through the UI, finish, verify Home 106.7 kg/2160 kg/3 sets/1 workout/1 PR, select recent exercise, open matching Progress, expand warmups, cancel then confirm delete, verify recalculated values.
- [x] Add fixture-based range/PR tests, empty/bodyweight/archived-history selection, max-four recent chips, profile age, settings units, and recreation state.
- [x] Observe red UI assertions before implementation, or mutation-check if device connection is delayed.
- [x] Complete Home profile including age and unit-aware weight, recent exercise trend, 4-week grid and deltas. Add Progress exercise picker, chart/ranges, all-time tiles, expandable history and confirmed deletion. Handle loading/error/retry and accessible text chart summaries.
- [x] Run new device tests, confirm they detect intentional missing data/deletion/PR regressions, restore code, then run the full E2E runner. Update the test catalog.
- [x] Review complexity then correctness, fix confirmed findings with regression evidence. Run final checks and show terminal output. Keep work on the Phase 5 branch unless integration is requested.

## Verification log

- Baseline: 51 JVM tests passed. JDK 21 and Gradle 8.11.1 verified. ADB initially reported no connected device.
- Task 1: new summary/boundary tests failed on assertions, then 54 JVM tests passed.
- Task 2: 3 Room history/deletion tests failed without the feature, then the full suite passed: 54 JVM / 86 device.
- Task 3: 6 UI tests failed with timeouts, then passed. Mutation checks made range inclusivity, all-time PRs, deletion guard, heaviest tie, and error states fail before restoring.
- Review: no critical findings. Fixed lint `ProduceStateDoesNotAssignValue`, chip selected semantics, skipped-first default exercise, delete-dialog date, and strengthened deletion/retry tests.
- Final: `lintDebug` passed; full E2E passed with 54 JVM / 94 device tests, 0 failed; INTERNET permission absent.
