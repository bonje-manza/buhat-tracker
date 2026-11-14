# Test catalog

The list of tests we keep. Update it in the same change that adds, renames, or deletes a test.
A row without a class name is planned work, not coverage — treat it as a gap.

The automated device layer runs on the Gradle-managed Pixel 6 / API 35 emulator (`pixel6api35DebugAndroidTest`).

## Automated

| Area | Class | Layer | Guards | Added in |
|---|---|---|---|---|
| Tab navigation | `TabNavigationTest` (2 tests) | UI | Tabs switch screens; selected tab survives `recreate()` | Phase 0 |
| Content validation | `domain/ContentRulesTest` (10 tests) | unit | Blank/duplicate names, 200-char text cap, zero exercises, duplicate exercise, sets 1..10, reps 1..30, min <= max, RIR 0..5 | Phase 2 |
| Content repository | `data/ContentRepositoryTest` (15 tests) | data | Custom exercise/template round trip, premade read-only, duplicate, reorder/remove, archive hides but keeps template rows, delete archives a referenced exercise, cards carry counts and names, profile stats, preferences including notes/unilateral save for premade and custom rows through repository recreation, invalid preferences and missing exercise rejected, 2,000-character notes accepted and 2,001 rejected before any write, blank notes clear without snapshots or dirtying the session | Phase 2/11/17 |
| Content screens | `ui/ScreenTest` (14 tests) | UI | Workouts filter/create, validation messages, premade duplicate only, custom exercise create/edit/archive, referenced delete offers archive, profile persists and Settings opens, bottom tab labels fit at 1.3x, exercise-picker rows meet 48dp, premade exercise settings keep content read-only, custom menu keeps its actions and adds settings, Library notes follow premade/custom exercises into workouts across Activity recreation, multiline Unicode and exercise selection stay distinct, recreation cannot restore another exercise's note draft | Phase 2/7/12/17 |
| Session rules | `domain/SessionRulesTest` (8 tests) | unit | Prefill from the matching position, current-session fallback, blank first time, RIR default 2, working-index renumbering, set field bounds, non-finite weight rejected, elapsed formatting | Phase 3 |
| Rest timer rules | `domain/RestTimerTest` (8 tests) | unit | Per-type seconds, zero disables, remaining clamped at zero, elapsed deadline ignores wall-clock changes, ceil countdown format, m:ss format, per-exercise working override wins while warmup/failure stay global, null falls back and zero is kept | Phase 4/12 |
| Rest timer state | `data/RestTimerRepositoryTest` (12 tests) | data | Each type schedules its default, zero disabled, new set replaces, skip serializes with an in-flight set, finish/discard clear, end time survives repository recreate, finished sessions reject late sets, working override applies only to working sets, zero override starts no rest, archived rows stay in allExercises/observeExercise | Phase 4/12 |
| Rest timer alerts | `timer/RestTimerTest` (11 tests) | device | Ongoing countdown and exact alarm, stale completion/start/cancel ignored, completion notification and state clear, channel sound/vibration, direct alarm-usage sound/vibration alert when notifications are off, a muted, silenced, or missing completion channel falls back to the direct alert | Phase 4/7 |
| Rest timer screen | `ui/RestTimerTest` (10 tests) | UI | Rest card after a set, Skip clears it, `recreate()` restores it, finish/discard/discard-and-start cancel it, leaving Active/Workouts during writes preserves cleanup, zero-rest replacement cancels the prior alarm, expired cleanup preserves a newer rest, Settings defaults persist across recreate | Phase 4 |
| Rest permission recovery | `ui/RestPermissionTest` (1 test) | UI | Restoring an unprompted saved timer requests notification permission once, including screen re-entry; only Android's permission boundary is substituted | Phase 4 |
| Profile/settings store | `data/ProfileSettingsStoreTest` (5 tests) | data | Profile/settings persistence, notification permission requested once, settings save does not clear a running rest | Phase 1/4 |
| Session repository | `data/SessionRepositoryTest` (33 tests) | data | Template snapshot copy, template edit after start is invisible, one active session, warmup/working/failure positions, set validation, finish, warmups-only finish refused, discard cascades, repository recreation, finished-only history, bounded stays the latest overall plus the latest same-template session, reorder survives repository recreation and finish with sets/rest/template intact, invalid IDs and inactive sessions rejected, failed reorder rolls back all positions, finish waits for reorder, add/edit/remove/replace renumber positions and preserve logged sets, duplicates and inactive sessions rejected, every structure change and reorder sets the dirty flag, this-workout-only leaves the template alone, update rewrites a custom template in session order, copies a premade as `(copy)`/`(copy 2)`, recreates a deleted custom template or falls back to `(copy)` if the name was reused, excludes replaced rows, clears a dangling replacement link, treats a repeated finish as a no-op, and rolls back the whole finish when the template write fails | Phase 3/5/13/18 |
| Set edits | `data/SetEditTest` (4 tests) | data | Retype working to warmup renumbers, delete a middle set renumbers, retype warmup to working appends, weight/reps/RIR edit keeps the position | Phase 3 |
| Active workout | `ui/ActiveWorkoutTest` (31 tests) | UI | Log a working set and see the next position target, `recreate()` restore, zero-working-set finish offers discard, Resume prompt for a second start, empty state, idle rest card explains automatic start, design RIR hint, named 48dp numeric inputs including the editor, lb logging/targets/steps, mixed kg/lb exercises in one workout, target-card settings change unit mid-session while the logged kg and running rest stay put, live reorder preserves selection/sets/rest through Activity recreation and finish, Home shows finished order, template unchanged, single-row and in-flight arrows disabled, write failure is visible and retryable, a second move in the same frame is rejected instead of overwriting the first, notes save/cancel/recreate/follow into the next workout/clear while preserving unilateral, note cap blocks save without truncation, failed note save retains the draft for retry, switching exercises hides the previous note/settings while the new Room read is pending, add/edit/replace/remove mid-workout through the picker and prescription dialog, replace selects the new row and keeps both set groups in history, logged rows cannot be removed, finish offers the template prompt only when dirty, this-workout-only leaves the premade alone, update creates the premade copy, a failed save-back shows its error with the workout still active, and a finish failure without a structure change also shows its error | Phase 3/7/12/13/17/18 |
| Progression targets | `domain/ProgressionTest` (17 tests) | unit | No history, below range, mid-range, top of range, per-position independence, missing position fallback, any-workout vs same-template, bodyweight, lb top-of-range adds 5 lb, lb messages use pounds, lb bodyweight never adds weight | Phase 1/12 |
| e1RM and PRs | `domain/AnalyticsTest` (16 tests) | unit | Epley, strictly-greater PR chronology, ties are not PRs, window boundaries 28/30/90/365 days, warmups excluded, volume, best e1RM, heaviest-set tie by reps, session count, all-time seed stops a window-only PR, window plus seed equals the full-history stats, the Home clock follows a just-finished session but caps a far-future one at one day | Phase 1/5 |
| Units | `domain/UnitsTest` (9 tests) | unit | kg/lb round trip, 0.1 lb rounding, kg caps converted values at two decimals, pound round trip stays whole, height stays cm, effective unit uses override then global, weight increment is 2.5 kg or 5 lb | Phase 1/12/15 |
| Seeded content | `data/SeedTest` | data | 6 templates, total sets 9/16/17/13/12/16, exercise library present, seed idempotent | Phase 1 |
| Backup validation | `data/BackupTest` (34 tests) | unit | Captured phone backup round trip, export size guard accepts the limit and an injected limit, required nullable fields, strict field types, version/enums/ranges, UUIDs, references, duplicate names/IDs, timestamps, contiguous positions, deleted snapshots, oversized text, format-1 import defaults to null overrides and format-3 fields, format-2 defaults every format-3 field, format-2 override round trip, required override keys, invalid override values, format-3 round trip of notes/import key/replacement link/side, required format-3 keys, 2,000-char note cap, invalid side, duplicate import key, nonblank import keys with a 200-character cap, dangling replacement link | Phase 6/11/16 |
| Backup persistence | `data/BackupRepositoryTest` (10 tests) | data | All six tables plus profile/settings, file round trip into a fresh container including archived/custom rows, active guard, rejected files preserve data, actual SQLite failure rolls back, DataStore failure can retry, seed complete and rest cleared, exercise preferences saved through the repository including note/unilateral survive export/import, notes/unilateral/import key/replacement link/side survive export/import and reach full, per-exercise, latest, and dashboard history | Phase 6/11/16/17 |
| Room migrations | `data/MigrationTest` (4 tests) | data | 1->2 keeps rows and adds history indexes, 2->3 keeps rows and adds null override columns, 3->4 keeps every row and adds notes/unilateral/import/side defaults plus the unique import-key index, 1->3 runs the whole chain | Phase 6/11/16 |
| Settings controls | `ui/SettingsScreenTest` (7 tests) | UI | Live palette/system bars, kg/lb profile conversion without rounding drift, lb logging/editing preserves exact kilograms, previous source changes targets but not history, active session disables data actions, profile text over backup cap rejected, units card names both weight steps and the per-exercise settings location | Phase 6/7/12 |
| Backup screen | `ui/BackupScreenTest` (7 tests) | UI | Real file export/import, preview/cancel/replace, live profile reload, timer alarm cleanup, malformed/newer files and picker cancellation, visible write errors, import failure/retry survives recreation, export and import timeouts report and unlock the screen, the export cap fails before the chosen file is opened. Only system picker substituted | Phase 6 |
| Theme contrast | `ui/theme/ThemeContrastTest` (4 tests) | UI | Light and dark body/card/muted/inset/link/button/badge text >= 4.5:1; failure badge readable in dark and light | Phase 7 |
| Finished history | `data/HistoryRepositoryTest` (3 tests) | data | Snapshot names/order, skipped exercises, warmups, active workouts excluded, reactive deletion cascade, recomputed PRs/profile totals, active-session deletion rejected with rest intact | Phase 5 |
| Bounded history query | `data/ExerciseHistoryQueryTest` (6 tests) | data | Latest overall plus latest same-template session, collapse when the newest is both, active sessions excluded, a newer session without sets never hides the older one, the template subquery also skips a same-template session without sets, `finishedAt` ties break by session id | Phase 3/5 |
| Home dashboard | `data/HomeDashboardTest` (6 tests) | data | 200 old sessions stay counted in SQL while only the 90-day window loads, the statement log shows no full-history query, the SQL seed matches `e1rm()` including warmup/bodyweight/tie shapes, the all-time seed stops a four-week tie and deleting it makes the tie a PR, the DAO window and seed reproduce the full-history four-week stats on both sides of the prior-window boundary, a far-future session does not shift the recent four-week stats | Phase 5 |
| PR recompute | `data/PrRecomputeTest` (3 tests) | data | Editing a later set above and below the earlier record, deleting the record promotes the next set, importing a backup replaces every previous PR set | Phase 5 |
| Analytics fixtures | `ui/ProgressScreenTest` (12 tests) | UI | Log via UI feeds Home 106.7 kg/2160 kg/3 sets/1 PR, Home strength card carries the design "Est. 1RM" label, range changes and all-time PR badges, empty/range-empty, max-four recent chips, skipped-first default, selection handoff, archived bodyweight history, units/recreation, load-failure retry recovery, confirmed deletion recalculates, per-exercise lb in Progress/Home while volume stays global, archived exercise keeps its unit | Phase 5/7/12 |
| Menu and validation | `ui/ScreenTest` | UI | Blank/duplicate names rejected, bounds enforced, zero-working-set finish prompts discard | Phase 2 |

## Manual, release checklist

Run on the test phone (SM-S711W) against the signed release build. The automated suite runs on the emulator above; these checks stay physical because the payoff per tester is low and the setup cannot be reproduced in an emulator.

- [ ] Rest alert fires within ±2 s with the screen locked; sound and vibration; Skip cancels it
- [ ] `scripts/verify-release.ps1` passes: invalid signing files fail, debug needs no key, signer matches the approved key, R8/resource shrinking ran, no INTERNET, backup disabled, version 4/1.1.0
- [ ] Notification permission denied: in-app timer still runs, hint shown once
- [ ] Airplane mode: full app usable; `adb shell dumpsys package com.buhat.tracker | findstr INTERNET` shows nothing
- [ ] Signed release installed over the previous versionCode: data survives
- [ ] 1.3x font scale, long exercise/template names, empty states, 48dp touch targets
- [ ] Bench Press in lb and Leg Extension in kg: input, target, logged rows, past workouts, and that exercise's Progress chart use the chosen unit while volume stays global
- [ ] One exercise with a working-rest override: warmup and failure use the global rest, working uses the override
- [ ] Reorder or change the exercises in an active workout, kill its process, relaunch, and verify order plus the finish prompt. Automated tests cover Room rows, repository recreation, and Activity recreation, not an actual process kill.
- [ ] Export on the phone, clear app data, import, counts and values match the hand-computed fixture
- [ ] Import a format-1 backup: both exercise overrides and every format-3 field default to null or false

## Deliberately not tested

Rare, mostly unrecoverable in v1, and expensive to automate for a user base of a few friends on a handful of phones:

- Reboot or clock change during a rest timer
- Storage permission revoked mid export/import
- The 20 MB import size cap
- OEM battery tuning beyond the test phone
- Localization (English only in v1)
- Chart pixel output (assert the text summary instead)

## Keep or delete

Delete when:

- The behavior no longer exists in the app.
- Another test asserts the same values against the same inputs.
- It only checks a framework (Compose renders text, Room stores a row).

Never delete:

- Import/export data loss
- Progression targets
- Analytics math
- Permission and network checks

## When to run

| Trigger | Run |
|---|---|
| Feature complete | New feature test first, then the full suite |
| Bug fix | Regression test that failed before the fix, then the full suite |
| Before a release APK | Full suite, then the manual checklist above |
| AGP / Kotlin / Compose / Room upgrade | Full suite |
| Change to shared domain or data code | Full suite |
| Debugging one failure | `-Feature <class>` or `-Layer <layer>`, then the full suite before claiming done |

## Updating the runner

`scripts/run-e2e.ps1` changes only when a layer is added (for example a web client, out of scope for v1). For a new test, add a row here instead. If you feel like editing the script per feature, use `-Feature` instead.
