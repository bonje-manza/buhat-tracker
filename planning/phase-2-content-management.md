# Phase 2 - Content management

Roadmap: `planning/buhat-tracker-roadmap.md` section "Phase 2".
Outcome: the user can manage exercises, templates, and the profile without touching the database.

## Slices

Each slice: failing test first, then the smallest code that passes it.

### 1. Content rules and repository

- `domain/ContentRules.kt`: draft type and validation for exercise and template names,
  exercise count, duplicate exercises, sets 1..10, reps 1..30, min <= max, RIR 0..5.
- `data/local/BuhatDao.kt`: template card rows, single-row lookups, name-exclusion queries,
  update/delete, finished-session stats.
- `data/BuhatRepository.kt`: create/edit/archive/delete exercise, create/edit/duplicate/delete
  template, template cards flow, profile stats flow, premade guards.
- Tests: `domain/ContentRulesTest` (unit), `data/ContentRepositoryTest` (in-memory Room).

### 2. Workouts screen

- Header count, All/Premade/My templates filter, template cards with exercise/set counts and
  exercise names, empty state, Start workout placeholder, New.
- Custom cards: Edit, Delete. Premade cards: Duplicate only.
- Test: `ui/ScreenTest#workoutsFiltersAndCreatesTemplate`, `#premadeIsReadOnlyAndDuplicates`.

### 3. Template editor

- Name, exercise rows (sets, rep min/max, optional RIR), add from library, remove, reorder,
  Save, validation messages.
- Test: `ui/ScreenTest#templateValidationBlocksBadInput`.

### 4. Exercise library and editor

- Search, muscle-group filter, Custom badge, create/edit custom, archive, delete with the
  referenced-exercise rule.
- Test: `ui/ScreenTest#customExerciseCreateEditArchiveDelete`.

### 5. Home profile card, profile editor, Settings entry

- Profile card: initial, name, training since, total workouts, gender/weight/height,
  Settings button. Profile editor: name, gender, age, weight, height.
- Settings entry opens a Settings screen with a Profile link.
- Test: `ui/ScreenTest#profileEditsPersistAndSettingsOpens`.

## Files

New:
- `app/src/main/java/com/buhat/tracker/domain/ContentRules.kt`
- `app/src/main/java/com/buhat/tracker/ui/screens/WorkoutsScreen.kt`
- `app/src/main/java/com/buhat/tracker/ui/screens/TemplateEditorScreen.kt`
- `app/src/main/java/com/buhat/tracker/ui/screens/ExerciseLibraryScreen.kt`
- `app/src/main/java/com/buhat/tracker/ui/screens/ExerciseEditorScreen.kt`
- `app/src/main/java/com/buhat/tracker/ui/screens/HomeScreen.kt`
- `app/src/main/java/com/buhat/tracker/ui/screens/ProfileEditorScreen.kt`
- `app/src/main/java/com/buhat/tracker/ui/screens/SettingsScreen.kt`
- `app/src/test/java/com/buhat/tracker/domain/ContentRulesTest.kt`
- `app/src/androidTest/java/com/buhat/tracker/data/ContentRepositoryTest.kt`
- `app/src/androidTest/java/com/buhat/tracker/ui/ScreenTest.kt`

Changed:
- `app/src/main/java/com/buhat/tracker/ui/BuhatApp.kt` (Navigation Compose host, tabs, bottom bar)
- `app/src/main/java/com/buhat/tracker/ui/components/BuhatComponents.kt` (full-screen header)
- `app/src/main/java/com/buhat/tracker/data/local/BuhatDao.kt`
- `app/src/main/java/com/buhat/tracker/data/BuhatRepository.kt`
- `app/src/main/java/com/buhat/tracker/MainActivity.kt` (pass the container in)
- `app/build.gradle.kts` (Navigation Compose, roadmap 4.4)
- `.opencode/skills/e2e-testing/references/test-catalog.md`
- `planning/buhat-tracker-roadmap.md` (Phase 2 checkboxes, only after the suite passes)

## Rules to prove

| Rule | Expected |
|---|---|
| Blank template name | "Name is required." |
| Duplicate template name, case-insensitive | "That name is already used." |
| No exercises | "Add at least one exercise." |
| Same exercise twice | "Each exercise can appear once." |
| Sets 11 | "Sets must be 1 to 10." |
| Rep min 31 | "Reps must be 1 to 30." |
| Rep min 12, max 8 | "Minimum reps cannot exceed maximum reps." |
| RIR 6 | "RIR must be 0 to 5." |
| Edit/delete a premade template | "Premade templates are read-only." |
| Delete a custom exercise used by a template | archived, not deleted |
| Delete an unreferenced custom exercise | deleted |
| Profile stats | count and earliest `finishedAt` of FINISHED sessions only |

## Out of scope

Start workout behavior (Phase 3), timer (Phase 4), Home dashboard (Phase 5), Settings
controls and export/import (Phase 6), anything in the roadmap "Excluded" list.
