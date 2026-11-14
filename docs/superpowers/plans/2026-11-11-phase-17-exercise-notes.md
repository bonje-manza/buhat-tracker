# Phase 17 Exercise Notes Implementation Plan

> **For agentic workers:** Use `executing-plans` with `test-driven-development`. Track the single slice below.

**Goal:** One persistent note per exercise, editable in Library and Active Workout and visible on the active exercise card.

**Architecture:** Extend the existing preference update and shared settings dialog. Read the current Exercise note through the existing active-screen flow. Schema 4 and backup 3 already carry notes.

**Tech Stack:** Kotlin, Room, Jetpack Compose, JUnit, managed Pixel 6/API 35.

**Spec:** `planning/buhat-tracker-roadmap.md`, Phase 17.

## Global Constraints

- Accept 2,000 characters, reject 2,001 before any write. Blank stores null. Preserve nonblank multiline text.
- Premade content remains read-only. Notes are not session snapshots and do not dirty the session.
- Extend the preference API with note and unilateral fields as Phase 17 specifies. The unilateral UI belongs to Phase 19.
- Reuse existing storage, constants, components, and tests. No new dependencies or won't-build features.
- Work on the user's existing `phase-17-persistent-exercise-notes` branch. Preserve pre-existing changes.

## Review Focus

- Saving a note must preserve unit/rest and the existing unilateral value.
- Failed or overlong saves must retain both the draft and the previous stored values.
- Clearing whitespace removes the card text, without inheriting a historical note.
- Changing exercises must show only that exercise's note.
- Saved notes must survive recreation, the next workout, and backup replacement.

### Task 1: Persistent exercise notes, end to end

**Files:**
- Modify `app/src/main/java/com/buhat/tracker/data/BuhatRepository.kt` and `data/local/BuhatDao.kt`.
- Modify `app/src/main/java/com/buhat/tracker/ui/screens/ExerciseLibraryScreen.kt` and `ActiveWorkoutScreen.kt`.
- Extend `app/src/androidTest/java/com/buhat/tracker/data/ContentRepositoryTest.kt`, `BackupRepositoryTest.kt`, `ui/ScreenTest.kt`, and `ui/ActiveWorkoutTest.kt`.
- Update preference-call arguments in `data/RestTimerRepositoryTest.kt` and `ui/ProgressScreenTest.kt`.
- Update `.opencode/skills/e2e-testing/references/test-catalog.md` with feature coverage.

**Interfaces:** `saveExercisePreferences(exerciseId: String, unitOverride: String?, restWorkingSecondsOverride: Int?, note: String?, isUnilateral: Boolean): Unit`; DAO writes these four preferences only. UI tags: `pref-note`, `exercise-note`, existing `save-exercise-prefs`.

- [x] Baseline: run `powershell -NoProfile -ExecutionPolicy Bypass -File .opencode/skills/e2e-testing/scripts/run-e2e.ps1`. Expected: nonzero test counts, zero failures.
- [x] RED: add UI scenarios for Library premade/custom notes, active edit/cancel/clear/recreate/next workout, note bounds, and failed-write retry. Run the new methods using runner `-Feature`. Expected: failure because note input is absent.
- [x] Add preference API parameters without behavior, update existing callers, and write repository tests: note saved on premade/custom rows, `" \n\t"` becomes null, 2,000 accepted, 2,001 rejected without modifying any preferences, missing exercise rejected, no dirty flag/session snapshot. Extend backup test through the real preference API. Run targeted classes. Expected: stored note assertions fail.
- [x] GREEN: add length validation and blank normalization in the repository, bound DAO update, multiline field with count and validation, save busy/error/retry handling, and nonblank active-card display.
- [x] Run targeted feature tests. Expected: all pass. Mutate validation and clear behavior if their assertions have not yet been observed failing, then restore.
- [x] Update catalog, run the full runner. Expected: unit and device suites pass, no INTERNET permission.
- [x] Review complexity, correctness, and user-input validation. Address substantive findings with red/green verification.
- [x] Report terminal output and leave the feature on the requested branch for the user.

## Execution record

- Baseline commit: `3d57ed1` (Phase 16 foundation).
- Scope review: production changes limited to four existing files. Required signature updates reach four existing test classes plus the two feature UI classes. No config, migration, or export format changes needed.
- Baseline: 123 unit, 207 device, zero failures. UI RED: 4/4 fail at missing `pref-note`. Data RED: 4 failures in 16 tests, notes are not written or cleared.
- Targeted GREEN: 123 unit and 20 device tests pass. Mutation check: removing note limits/blank normalization and overwriting unilateral makes all four selected tests fail at the intended assertions. Restored all mutations before the final suite.
- 2026-11-14 resume: the earlier full run has no completion summary. Restarted full verification. Independent review identified draft restoration across exercise IDs and a stale observed exercise row during selection changes. Added regression scenarios before fixing either.
- Review regressions: both failed on the reported wrong-exercise text. Keying the saved draft by exercise ID and ignoring observed rows with a different ID made both pass. No security findings or deferred minor findings. Review exclusions match the user's scope: Phase 19 controls and pre-existing unrelated changes.
- Final verification, 2026-11-14: full runner passes 123 unit tests and 215 device tests, zero failed/skipped, INTERNET permission absent. Terminal log: `C:\Users\Admin\AppData\Local\Temp\opencode\phase17-verified.log`.
- Task 1 complete. Changes remain uncommitted on `phase-17-persistent-exercise-notes`. Physical-device process-kill and release verification were not run for this feature.
