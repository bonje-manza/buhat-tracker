# Phase 6 - Settings, export, import

Spec: `planning/buhat-tracker-roadmap.md` sections 4.2, 4.4 (Export/import), 4.5, 4.6.

## Slice 1 - Backup format and validation (unit, pure Kotlin)

Files: `data/Backup.kt`, `app/src/test/java/com/buhat/tracker/data/BackupTest.kt`.

- `@Serializable Backup` DTOs, `FORMAT_VERSION = 1`, root fields per roadmap.
- `encodeBackup`, `decodeBackup` (throws `BackupException` with not-a-backup, newer-version, corrupt messages).
- Validate version, required fields, enums, ranges (sets 1..10, reps 1..30, min <= max, RIR 0..5, weight 0..1000, reps 0..100, rest 0..600), unique IDs/names, references, timestamps, FINISHED-only sessions, contiguous working indices.

## Slice 2 - Repository export/import (device)

Files: `data/local/BuhatDao.kt`, `data/BuhatRepository.kt`, `data/Backup.kt`, `AppContainer.kt`, `app/src/androidTest/java/com/buhat/tracker/data/BackupRepositoryTest.kt`.

- DAO: read-all queries and delete-all queries.
- `exportBackup()`: block active session, snapshot all tables + profile + settings (no rest state).
- `previewBackup(text)`: block active session, decode and return counts + export date.
- `importBackup(text)`: block active session, decode, replace all Room rows in one transaction, then profile/settings, mark seeded, clear rest.

## Slice 3 - Settings UI and theme wiring (UI)

Files: `ui/screens/SettingsScreen.kt`, `MainActivity.kt`, `ui/BuhatApp.kt`, `app/src/androidTest/java/com/buhat/tracker/ui/SettingsScreenTest.kt`.

- Appearance, units, previous-set source segmented controls.
- Keep rest section.
- Data card: export (CreateDocument), import (OpenDocument), size cap, import preview dialog with counts/date/unencrypted warning, replace confirmation, error text, active-session hint and disabled buttons.
- MainActivity reads stored theme so theme changes apply live.

## Slice 4 - Verification

- Unit, data, UI suites through `.opencode/skills/e2e-testing/scripts/run-e2e.ps1`.
- Mutation check: break codec validation, import replace, and theme wiring; confirm red.
- Update `references/test-catalog.md`.
- Review passes (complexity, correctness, security) per feature-workflow.

## Resume ledger

- [x] Initial codec and repository implemented. Unit codec 10/10, repository 6/6 previously passed.
- [x] Diagnose remaining Settings selection assertion, finish live theme/units coverage.
- [x] Strengthen backup validation and transactional failure coverage with failing tests first.
- [x] Exercise file export/import and confirmation through real screen, repository, Room and DataStore.
- [x] Mutation checks, full regression run, review, terminal evidence.
- Ruling: continue on the existing Phase 6 branch and preserve its uncommitted work rather than relocate it mid-feature.
- Ruling: repository/UI tests were not actually run red before their initial implementation. Do not claim otherwise. Prove these with explicit mutations before completion.
- Evidence: full E2E runner passed 73 unit + 110 device tests, zero failures, no INTERNET permission.
- Mutation evidence: empty decoder caused 18/18 codec tests to fail. No-op import failed data replacement, malformed/newer/active guards, rollback and preference retry tests. Conditional no-op import after valid preview failed the UI file round-trip. Mutations restored.
- Removed redundant archived-import test which could pass without importing. Captured repository-to-file-to-fresh-container round-trip checks the archived value and every other field.
- Follow-up review found the import-only 200-character cap could reject app-created rows. Red test `namesAreCappedAtTwoHundredCharacters`, then shared `MAX_TEXT_LENGTH` enforced by `validateExerciseName`/`validateTemplate` and the profile save action.
- Evidence after cap fix: 74 unit and 9 Backup/Settings device tests pass. Full E2E gate is the final regression run.
