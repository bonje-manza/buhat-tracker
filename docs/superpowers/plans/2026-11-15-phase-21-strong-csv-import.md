# Phase 21 Strong CSV Import Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans. Steps use checkbox (`- [ ]`) syntax.

**Goal:** A Strong CSV export imports offline with preview, exercise mapping, duplicate skipping, notes, and RPE conversion.

**Architecture:** One pure CSV reader feeds one Strong parser that produces import-ready workouts. The repository resolves duplicates and exercise mappings against Room, then appends everything in one transaction. One Compose screen handles file pick, preview, mapping, and confirm.

**Tech Stack:** Kotlin, Compose, Room, JUnit, Compose UI tests. No new dependency.

**Spec:** `planning/buhat-tracker-roadmap.md` sections 4.4 "Strong CSV import (Phase 21)" and 4.8 "Strong CSV source evidence".

## Global Constraints

- No Room schema, migration, backup DTO, or backup format change.
- Imported sessions are `FINISHED`, `templateIdSnapshot = "strong-import"`, `templateNameSnapshot` = Strong workout name. They never create templates.
- Weights stay kilograms. Imported sets are unsided (`side = null`).
- Import identity is `strong|<raw exported date-time>|<workout name>`. `Workout #` is never a cross-export identity.
- Dates are device local time. `finishedAt = startedAt + duration seconds`.
- Any parse, mapping, range, or unsupported-field error blocks the whole import with no partial writes.
- Import is blocked while a workout is active with `Finish or discard the workout first.`
- Reuse the existing 20 MB text-file guard and the SAF `OpenDocument` picker.
- Do not add a CSV or parsing dependency.

## Review Focus

- A note containing a semicolon, an escaped quote, and a newline must survive exactly into history.
- Two source names mapped to one Buhat exercise stay separate history occurrences with their own sets.
- Failure sets count as working positions but keep the recorded reps; RPE never adds a rep.
- More than 10 working sets or reps above 30 must still export a backup the app can re-import (imported prescriptions are clamped, history sets are not).
- A zero-duration workout still orders every set inside `startedAt..finishedAt`.
- Re-importing after a timezone change still skips the same identity because identity uses raw date text.

---

### Task 1: Quoted CSV reader

**Files:**
- Create: `app/src/main/java/com/buhat/tracker/domain/Csv.kt`
- Test: `app/src/test/java/com/buhat/tracker/domain/CsvTest.kt`

**Interfaces:**
- Produces:
  - `data class CsvRow(val line: Int, val fields: List<String>)`
  - `class CsvException(message: String) : Exception(message)`
  - `fun parseCsv(text: String, delimiter: Char = ';'): List<CsvRow>`

- [ ] **Step 1: Write the failing tests**

`CsvTest`:
- `quotedDelimiterEscapedQuoteAndMultilineField`: input `a;"b;c";"say ""hi""";"line one\nline two"` returns one row with `["a", "b;c", "say \"hi\"", "line one\nline two"]` and `line == 1`.
- `bomAndCrLfAreAccepted`: `"\uFEFFa;b\r\nc;d\r\n"` returns two rows with fields `[a,b]` and `[c,d]`.
- `emptyFieldsAndBlankLines`: `"a;;b\r\n\r\nc;\"\";d"` returns two rows, `[a,"",b]` and `[c,"",d]`; the blank line is skipped.
- `unclosedQuoteReportsTheStartingLine`: `"a;b\r\nc;\"unclosed"` throws `CsvException` whose message contains `Row 2`.
- `textAfterClosingQuoteIsRejected`: `"\"a\"x;b"` throws `CsvException` whose message contains `Row 1`.

- [ ] **Step 2: Run tests and confirm failure**

Run: `.\gradlew.bat testDebugUnitTest --tests "com.buhat.tracker.domain.CsvTest" --console=plain`
Expected: FAIL on unresolved `CsvRow`/`parseCsv`.

- [ ] **Step 3: Implement `parseCsv`**

Behavior: strip one leading BOM; treat `"` only as a quote when it starts a field; inside quotes, `""` is one quote and delimiters/newlines are literal; accept `\r\n` and `\n`; a blank physical line emits no row; `line` is the physical line where the record starts; unclosed quote and text after a closing quote throw `CsvException("Row N: ...")`.

- [ ] **Step 4: Run tests and confirm pass**

Run the Step 2 command. Expected: PASS.

- [ ] **Step 5: Commit**

`git add app/src/main/java/com/buhat/tracker/domain/Csv.kt app/src/test/java/com/buhat/tracker/domain/CsvTest.kt`
`git commit -m "feat: read quoted semicolon csv"`

### Task 2: Strong parser, validator, and RPE conversion

**Files:**
- Create: `app/src/main/java/com/buhat/tracker/domain/StrongCsv.kt`
- Test: `app/src/test/java/com/buhat/tracker/domain/StrongCsvTest.kt`
- Create: `app/src/test/resources/strong-export-sanitized.csv`

**Interfaces:**
- Produces:
  - `const val STRONG_TEMPLATE_ID = "strong-import"`
  - `class StrongCsvException(messages: List<String>) : Exception(messages.joinToString("\n"))`
  - `data class StrongSet(val setType: SetType, val workingIndex: Int?, val weightKg: Double, val reps: Int, val rir: Int?, val completedAt: Long)`
  - `data class StrongExercise(val sourceName: String, val note: String?, val sets: List<StrongSet>)`
  - `data class StrongWorkout(val identity: String, val name: String, val startedAt: Long, val finishedAt: Long, val note: String?, val exercises: List<StrongExercise>)`
  - `data class StrongCsvImport(val workouts: List<StrongWorkout>, val skippedEmptyWorkouts: Int)`
  - `fun parseStrongCsv(text: String): StrongCsvImport`
  - `fun strongIdentity(rawDateTime: String, name: String): String`
  - `fun rirFromRpe(rpe: Double): Int`

**Fixture:** `strong-export-sanitized.csv` is a sanitized fixture, not a captured real file, because the original Strong export is unavailable. It uses the 13 documented columns, semicolon delimiter, quoted fields, and 33 workouts numbered 1..33 dated 2026-06-24 through 2026-08-30. Include warmups, working sets, one failure set, Rest Timer rows, Note rows (one multiline note containing `;`, `""`, and a newline), one blank RPE, blank weights, and two workout names. The real file still must be run before release (Phase 22 gate).

Expected 33-workout parse: `workouts.size == 33`, first identity `strong|2026-06-24 08:00:00|FBEOD`, last identity starts with `strong|2026-08-30`, every workout has `finishedAt - startedAt == 3_600_000`.

- [ ] **Step 1: Write the failing tests**

`StrongCsvTest`:
- `sanitizedExportParsesThirtyThreeWorkoutsAcrossItsDateRange`: read the resource; assert the four fixture facts above.
- `rpeTableConvertsHalfStepsHalfUp`: `rirFromRpe` for RPE `6, 6.5, 7, 7.5, 8, 8.5, 9, 9.5, 10` returns `4, 4, 3, 3, 2, 2, 1, 1, 0`; RPE `5.9` clamps to `4`; RPE `10.1` clamps to `0`.
- `restAndNoteRowsNeverBecomeSets`: a workout with `W`, `1`, `Rest Timer`, `Note`, `2`, `F` produces 4 sets; the note is attached to the occurrence and the rest row adds nothing.
- `failureKeepsItsRecordedRepsAndCountsAsWorking`: F row with reps 5 gives type `FAILURE`, reps 5, `workingIndex = 2` when a numeric working set precedes it.
- `blankWeightIsZeroAndBlankRpeIsNull`: working row with empty weight and empty RPE gives `weightKg == 0.0` and `rir == null`.
- `repeatedExerciseNameCreatesSeparateOccurrences`: A, B, A rows produce 3 occurrences with names `[A, B, A]`.
- `multipleNotesJoinWithBlankLinesAndABlankNoteStaysNull`: two Note rows produce `"first\n\nsecond"`; a workout whose only Note row is empty gives `null`.
- `workoutNotesUseTheFirstNonblankAndRejectConflicts`: two identical nonblank workout notes pass; two different nonblank notes throw.
- `workoutsWithoutWorkingSetsAreSkippedAndCounted`: a group with only warmups increments `skippedEmptyWorkouts` and is absent from `workouts`.
- `duplicateIdentityInsideOneFileIsRejected`: two groups with the same raw date and name throw.
- `unknownSetTagDistanceAndMissingRepsBlockWithRowNumbers`: separate inputs each throw with `Row` and the offending value.
- `dateNameAndDurationMustAgreeInsideOneGroup`: changing one repeated cell throws.
- `dateParsesAsDeviceLocalTime`: parse `2026-06-24 08:00:00` and convert `startedAt` back with `ZoneId.systemDefault()`; expect local date-time `2026-06-24T08:00`.
- `quotedSemicolonEscapedQuoteAndMultilineNoteSurvive`: parser-level input with BOM, CRLF, `"a;b"`, `""quoted""`, and a newline retains them in `sourceName`, workout note, and exercise note.
- `zeroDurationWorkoutKeepsSetsInsideStartAndFinish`: duration 0 gives `finishedAt == startedAt` and every `completedAt == startedAt`.

- [ ] **Step 2: Run tests and confirm failure**

Run: `.\gradlew.bat testDebugUnitTest --tests "com.buhat.tracker.domain.StrongCsvTest" --console=plain`
Expected: FAIL on unresolved `parseStrongCsv`/`rirFromRpe`.

- [ ] **Step 3: Implement the parser**

Rules:
- Map the 13 header names by name; missing or duplicate headers throw.
- Group rows by `Workout #` in first-seen order. Validate identical raw `Date`, `Workout Name`, `Duration (sec)`, and nonconflicting `Workout Notes` inside a group.
- `Set Order`: positive integer -> `WORKING`, `W` -> `WARMUP`, `F` -> `FAILURE`, `Rest Timer` -> ignore, `Note` -> metadata, anything else -> row error.
- `weightKg`: blank -> 0.0; else finite `0..1000`.
- `reps`: required `0..100`; failure adds nothing.
- `rir`: blank -> null; else finite RPE `0..10`, `floor((10 - rpe) + 0.5).toInt().coerceIn(0, 5)`.
- Nonblank `Distance (meters)` -> row error.
- `workingIndex` is 1..N over working/failure sets per occurrence, in row order; warmups null.
- Occurrence starts when `Exercise Name` changes from the previous set/note row; `Rest Timer` does not break it. Drop occurrences with no sets. Join note rows with `\n\n`, blank -> null, cap 2000.
- `completedAt = if (span == 0) startedAt else startedAt + span * (setIndex + 1) / (setCount + 1)` in workout row order.
- Identity uses the trimmed raw date and name. Duplicate identities in one file throw. Drop empty workouts after validation and count them.
- Collect row errors and throw one `StrongCsvException` with every message.

- [ ] **Step 4: Run tests and confirm pass**

Run the Step 2 command. Expected: PASS.

- [ ] **Step 5: Commit**

`git add app/src/main/java/com/buhat/tracker/domain/StrongCsv.kt app/src/test/java/com/buhat/tracker/domain/StrongCsvTest.kt app/src/test/resources/strong-export-sanitized.csv`
`git commit -m "feat: parse strong csv exports"`

### Task 3: Repository preview and transactional append

**Files:**
- Modify: `app/src/main/java/com/buhat/tracker/data/local/BuhatDao.kt`
- Modify: `app/src/main/java/com/buhat/tracker/data/BuhatRepository.kt`
- Test: `app/src/androidTest/java/com/buhat/tracker/data/StrongImportRepositoryTest.kt`

**Interfaces:**
- Consumes: `parseStrongCsv`, `StrongWorkout`, `StrongExercise`, `StrongSet`, `STRONG_TEMPLATE_ID`.
- Produces in `BuhatRepository.kt`:
  - `data class StrongSource(val name: String, val matchedExerciseId: String?)`
  - `data class StrongPreview(val newWorkouts: Int, val skippedDuplicates: Int, val skippedEmptyWorkouts: Int, val firstStartedAt: Long?, val lastStartedAt: Long?, val warmupSets: Int, val workingSets: Int, val failureSets: Int, val sources: List<StrongSource>, val exercises: List<Exercise>)`
  - `data class StrongMapping(val sourceName: String, val existingExerciseId: String? = null, val newExerciseName: String? = null, val muscleGroup: String? = null, val equipment: String? = null)`
  - `data class StrongImportResult(val workouts: Int, val skippedDuplicates: Int, val sets: Int)`
  - `suspend fun previewStrongImport(text: String): StrongPreview`
  - `suspend fun importStrong(text: String, mappings: List<StrongMapping>): StrongImportResult`
- Produces in `BuhatDao`:
  - `@Query("SELECT importKey FROM WorkoutSession WHERE importKey IS NOT NULL") suspend fun importKeys(): List<String>`

**Tests:**
- `previewCountsNewAndDuplicateWorkoutsAndPreselectsExactNames`: fixture with 2 workouts, one source exact match; first preview shows `newWorkouts = 2`, `skippedDuplicates = 0`, `source.matchedExerciseId` is the seeded name's id, set counts are literal.
- `importAppendsFinishedSessionsWithSentinelSnapshotNotesAndRir`: import; assert session status, sentinel id, workout name, raw local start, duration, import key, workout note, exercise snapshot/source note, `side == null`, working indices `1,2`, RIR from RPE.
- `reimportingTheSameTextImportsNothing`: second preview `newWorkouts = 0` and `skippedDuplicates = 2`; second import returns `0/2/0`; row counts unchanged.
- `overlappingExportAddsOnlyNewIdentities`: second text keeps one workout and adds one dated later; import adds one session.
- `mappingTwoSourceNamesToOneExerciseKeepsSeparateOccurrences`: map both unmatched names to `Bench Press`; assert two session exercises with different snapshots and the same `exerciseId`.
- `newCustomExerciseRequiresNameMuscleGroupAndEquipment`: three bad mappings throw before any write; a good mapping creates one custom row with `isCustom = true` and uses it.
- `unsupportedRowBlocksPreviewAndWritesNothing`: distance row throws with `Row`; session and exercise counts unchanged.
- `forcedWriteFailureRollsBackNewExercisesAndSessions`: create a `BEFORE INSERT ON WorkoutSession` trigger that aborts; import throws; exercise/session/set counts unchanged.
- `activeWorkoutBlocksPreviewAndImport`: start a session; both calls throw `Finish or discard the workout first.`
- `importedDataSurvivesABackupRoundTripAndAppearsInHistory`: import, export, `importBackup(export)`, then `history()` contains the workout name, exercise snapshot, notes, and unsided sets.
- `workoutsWithMoreThanTenSetsStillExportValidPrescriptions`: import an occurrence with 12 working sets and reps 35; `exportBackup()` decodes (clamped `workingSets`/`repMin`/`repMax`); logged reps stay 35.

- [ ] **Step 1: Write the failing tests**

Add `StrongImportRepositoryTest` using the in-memory Room + DataStore setup from `BackupRepositoryTest`. Seed the app database with `repo.seedIfNeeded()`. Use a small inline CSV fixture with one exact name (`Bench Press`) and one unmatched name (`Seated Row (Cable)`).

- [ ] **Step 2: Run tests and confirm failure**

Run: `.\gradlew.bat pixel6api35DebugAndroidTest --console=plain --rerun "-Pandroid.testInstrumentationRunnerArguments.class=com.buhat.tracker.data.StrongImportRepositoryTest"`
Expected: FAIL on unresolved `previewStrongImport`/`importStrong`/`importKeys`.

- [ ] **Step 3: Implement**

`previewStrongImport`: active guard, parse, load `importKeys()` and active exercises, compute new workouts, set counts, first/last start, distinct source names in first-seen order, and exact case-insensitive matches.

`importStrong`: inside `sessionWriteMutex` and `database.withTransaction`, re-check the active guard, parse, recompute new identities, validate exactly one mapping per distinct source from the new workouts, resolve existing ids, create missing customs (validate unique names, explicit muscle group/equipment from `SeedContent`), then insert sessions, session exercises, and sets. Use `exerciseNameSnapshot = sourceName`, `note = occurrence note`, `templateIdSnapshot = STRONG_TEMPLATE_ID`, `templateNameSnapshot = workout.name`, `importKey = identity`, `side = null`.

Imported prescriptions: `workingSets = workingSetCount.coerceIn(1, 10)`, `repMin = minReps.coerceIn(1, 30)`, `repMax = maxReps.coerceIn(repMin, 30)`, `targetRir = null`. Add `// shortcut: imported prescriptions are display-only, clamped so backups re-validate` above it. Any insert returning `-1` throws. Wrap write failures in `StrongCsvException` so the transaction rolls back.

- [ ] **Step 4: Run tests and confirm pass**

Run the Step 2 command. Expected: PASS.

- [ ] **Step 5: Commit**

`git add app/src/main/java/com/buhat/tracker/data/local/BuhatDao.kt app/src/main/java/com/buhat/tracker/data/BuhatRepository.kt app/src/androidTest/java/com/buhat/tracker/data/StrongImportRepositoryTest.kt`
`git commit -m "feat: preview and append strong imports"`

### Task 4: Settings entry and import screen

**Files:**
- Create: `app/src/main/java/com/buhat/tracker/ui/screens/StrongImportScreen.kt`
- Modify: `app/src/main/java/com/buhat/tracker/data/BackupFile.kt`
- Modify: `app/src/main/java/com/buhat/tracker/ui/BuhatApp.kt`
- Modify: `app/src/main/java/com/buhat/tracker/ui/screens/SettingsScreen.kt`
- Modify: `app/src/androidTest/java/com/buhat/tracker/ui/BackupScreenTest.kt` (SettingsScreen call gains `onImportStrong = {}`)
- Test: `app/src/androidTest/java/com/buhat/tracker/ui/StrongImportScreenTest.kt`
- Create: `app/src/androidTest/assets/strong-export-sanitized.csv` (copy of the unit fixture)

**Interfaces:**
- `SettingsScreen(..., onOpenProfile: () -> Unit, onImportStrong: () -> Unit)`
- Route `"strong-import"` -> `StrongImportScreen(repository, onBack)`
- `internal fun readStrongCsvText(resolver: ContentResolver, uri: Uri): String` in `BackupFile.kt`, sharing the 20 MB loop; message `This CSV file is larger than 20 MB.`

**UI:**
- Screen title `Import from Strong`, tag `screen-StrongImport`.
- `Choose CSV` button tag `strong-choose-file` launches `OpenDocument` for `text/csv`, `text/comma-separated-values`, `text/plain`, `application/csv`.
- Loading text while reading/parsing; error text tag `strong-error`.
- Preview card tag `strong-preview`: new workouts, skipped duplicates, skipped empty, date range, warmup/working/failure counts.
- One row per distinct source name with tag `strong-source-<name>`; text `Matched to <Buhat name>` or `Needs mapping`.
- Mapping dialog tags `strong-mapping-dialog`, `strong-picker-search`, `strong-existing-<name>`, `strong-create-new`, `strong-new-name`, `strong-muscle-<group>`, `strong-equipment-<type>`, `strong-use-new`, `strong-cancel`. Existing picker reuses the current exercise-picker list pattern. New custom requires name, muscle group, and equipment; defaults name to the source name but never defaults muscle/equipment.
- `Import N workouts` button tag `strong-confirm`, disabled until every source has a valid mapping. Hidden when `newWorkouts == 0`.
- When zero new workouts, show `No new workouts. All N workouts were already imported.`
- Result text tag `strong-result`: `Imported N workouts and M sets.` Additional text `Skipped X already imported and Y with no working sets.` when nonzero.
- Back is blocked while busy.

**Tests:**
- `previewMapAndImportAppendsWorkouts`: open Home > Settings > Import from Strong, choose the fixture via substituted registry, assert preview counts and `Needs mapping`, map `Seated Row (Cable)` to the seeded `Seated Cable Row`, confirm, assert result and repository history.
- `reimportReportsZeroNewAndHidesConfirm`: after a direct repository import, choose the same file; assert the zero-new message and no `strong-confirm`.
- `unsupportedRowReportsTheRowAndWritesNothing`: file with a populated distance; assert `strong-error` contains `Row` and sessions stay 0.
- `activeWorkoutDisablesTheSettingsEntry`: start a session, open Settings, assert `import-strong` is not enabled.

- [ ] **Step 1: Write the failing tests**

Add the screen test with the substituted `ActivityResultRegistry` pattern from `BackupScreenTest`. In the registry assertion, accept `ACTION_OPEN_DOCUMENT` and any of the four MIME types.

- [ ] **Step 2: Run tests and confirm failure**

Run: `.\gradlew.bat pixel6api35DebugAndroidTest --console=plain --rerun "-Pandroid.testInstrumentationRunnerArguments.class=com.buhat.tracker.ui.StrongImportScreenTest"`
Expected: FAIL because the Settings entry and screen do not exist.

- [ ] **Step 3: Implement the screen and wiring**

Keep preview state in the screen with `remember`; a rotation loses the preview and the user picks the file again. Use `rememberSaveable` only for the error/result strings if needed. Run parse/preview and import in `withContext(Dispatchers.IO)` or the repository's own IO context. Disable the confirm button while busy. On success clear the preview and show the result.

- [ ] **Step 4: Run tests and confirm pass**

Run the Step 2 command. Expected: PASS.

- [ ] **Step 5: Commit**

`git add app/src/main/java/com/buhat/tracker/ui/screens/StrongImportScreen.kt app/src/main/java/com/buhat/tracker/data/BackupFile.kt app/src/main/java/com/buhat/tracker/ui/BuhatApp.kt app/src/main/java/com/buhat/tracker/ui/screens/SettingsScreen.kt app/src/androidTest/java/com/buhat/tracker/ui/BackupScreenTest.kt app/src/androidTest/java/com/buhat/tracker/ui/StrongImportScreenTest.kt app/src/androidTest/assets/strong-export-sanitized.csv`
`git commit -m "feat: import strong csv from settings"`

### Task 5: Catalog, roadmap record, full suite

**Files:**
- Modify: `.opencode/skills/e2e-testing/references/test-catalog.md`
- Modify: `planning/buhat-tracker-roadmap.md`

- [ ] **Step 1: Add catalog rows**

Add unit rows for `domain/CsvTest` and `domain/StrongCsvTest`, a data row for `data/StrongImportRepositoryTest`, and a UI row for `ui/StrongImportScreenTest`. Name the sanitized-fixture limitation and the real-file release gate.

- [ ] **Step 2: Run the full suite**

Run: `powershell -NoProfile -ExecutionPolicy Bypass -File .opencode/skills/e2e-testing/scripts/run-e2e.ps1`
Expected: unit and device layers pass, `INTERNET` permission absent, no `NO-SOURCE` or zero-test result.

- [ ] **Step 3: Record the result**

Update Phase 21 checkboxes and add a `Result` line with the exact unit/device counts, the fixture note, and any deviation. Update the status paragraphs only if the phase is complete. Do not mark Phase 22 work.

- [ ] **Step 4: Commit**

`git add .opencode/skills/e2e-testing/references/test-catalog.md planning/buhat-tracker-roadmap.md`
`git commit -m "docs: record Phase 21 strong import"`

## Self-Review

- Spec coverage: parser, Strong mapping, identity, RPE, notes, duplicate skip, active guard, one transaction, schema-free storage, preview counts, mapping UI, Settings entry, and history visibility all have a task and a test.
- Unspecified fields: imported `workingSets`/`repMin`/`repMax` are clamped because backup validation would otherwise reject the app's own export. Logged reps and weights are never clamped.
- No new DAO beyond `importKeys`; no new dependency; no schema or backup change.
- The sanitized fixture is a deliberate exception to captured fixtures. Phase 22 must run the real export before release.
