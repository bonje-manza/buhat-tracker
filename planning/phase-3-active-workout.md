# Phase 3 - Active workout and per-set progression

Roadmap: `planning/buhat-tracker-roadmap.md` section "Phase 3".
Outcome: the core logging loop works end to end with per-set double progression and survives
process death.

## Slices

Each slice: failing test first, then the smallest code that passes it.

### 1. Session rules (unit)

- `domain/SessionRules.kt`: prefill for the position being logged, working-index renumbering,
  set field validation, elapsed formatting.
- Tests: `domain/SessionRulesTest`.

Rules to prove:

| Rule | Expected |
|---|---|
| Prefill, source set at position exists | raw source weight/reps/RIR (e.g. 80, 10, 2) |
| Prefill, no source at position, current session has sets | last current-session set's raw values |
| Prefill, nothing anywhere | weight and reps blank, RIR 2 |
| Prefill, source RIR null | RIR 2 |
| Working/failure sets numbered 1..N by `completedAt`, warmups null | indices `[null, 1, 2]` |
| Retype a set | positions stay contiguous, 1..N |
| Weight 1000.5 / reps 101 / RIR 6 | rejected with the roadmap message |
| Elapsed 0 / 65 s / 1 h 2 m 5 s | `0:00` / `1:05` / `1:02:05` |

### 2. Session repository (data)

- `data/local/BuhatDao.kt`: observe active session, session exercises, logged sets per exercise,
  finished history rows for one exercise, update session, delete session, clear working indices.
- `data/BuhatRepository.kt`: start (snapshot copy, one active session), auto working index,
  log/edit/delete set with renumbering, finish, discard, prefill source lookup.
- Tests: `data/SessionRepositoryTest`, `data/SetEditTest`.

Rules to prove:

| Rule | Expected |
|---|---|
| Start | `SessionExercise` rows copied from the template, positions 1..N, target fields equal |
| Template edit after start | active session rows unchanged |
| Start while one is active | `ActiveSessionException` carries the active id, nothing written |
| Log warmup, working, failure | `workingIndex` null, 1, 2 in tap order |
| Finish | status FINISHED, `finishedAt` set, no active session |
| Discard | session, its exercises and sets are gone |
| Reopen the database | active session and its logged sets still there |
| Retype working -> warmup | remaining working positions contiguous, next target recalculated |
| Delete a middle working set | later positions shift down |
| Retype warmup -> working | appended at the next position |
| History | FINISHED sessions only, sets grouped per session, ordered by `finishedAt` |

### 3. Active screen (UI)

- `ui/screens/ActiveWorkoutScreen.kt`: header (template name, elapsed pill, overflow
  Finish/Discard), exercise chips, target card with target RIR and "Use target", logged-set
  rows with type badge/weight × reps/type/RIR, tap-to-edit dialog, logging card
  (type segmented control, weight and reps stepper + numeric entry, RIR 0..5, Log set).
- `ui/screens/WorkoutsScreen.kt`: enable Start workout; Resume/Discard dialog when a session
  is already active.
- `ui/BuhatApp.kt`: render the Active tab, open it from Workouts.
- Test: `ui/ActiveWorkoutTest`.

Rules to prove:

| Rule | Expected |
|---|---|
| Log a working set at 80 kg x 8 RIR 2 | row shows `80 kg × 8`, `Working`, `RIR 2` |
| Next target after 8 reps on a 6-8 exercise | `82.5 kg × 6`, top-of-range message |
| `recreate()` | active session, selected exercise, and logged set still shown |
| Finish with no working sets | discard prompt, not a finished session |
| Discard | Active tab returns to its empty state |
| No active session | empty state pointing at Workouts |

## Files

New:
- `app/src/main/java/com/buhat/tracker/domain/SessionRules.kt`
- `app/src/main/java/com/buhat/tracker/ui/screens/ActiveWorkoutScreen.kt`
- `app/src/test/java/com/buhat/tracker/domain/SessionRulesTest.kt`
- `app/src/androidTest/java/com/buhat/tracker/data/SessionRepositoryTest.kt`
- `app/src/androidTest/java/com/buhat/tracker/data/SetEditTest.kt`
- `app/src/androidTest/java/com/buhat/tracker/ui/ActiveWorkoutTest.kt`
- `planning/phase-3-active-workout.md`

Changed:
- `app/src/main/java/com/buhat/tracker/data/local/BuhatDao.kt`
- `app/src/main/java/com/buhat/tracker/data/BuhatRepository.kt`
- `app/src/main/java/com/buhat/tracker/domain/Units.kt` (bare weight value for numeric entry)
- `app/src/main/java/com/buhat/tracker/ui/screens/WorkoutsScreen.kt`
- `app/src/main/java/com/buhat/tracker/ui/BuhatApp.kt`
- `.opencode/skills/e2e-testing/references/test-catalog.md`
- `planning/buhat-tracker-roadmap.md` (Phase 3 checkboxes, only after the suite passes)

No schema change: every entity and index Phase 3 needs already exists.

## Out of scope (roadmap will-not-build)

Rest timer, notifications, and the rest card (Phase 4). Home and Progress (Phase 5). Settings
controls and export/import (Phase 6). Visual pass and release signing (Phase 7). Anything in
the roadmap "Excluded" list: editing a finished workout, accounts, sync, localization.
