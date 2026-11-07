# Test catalog

The list of tests we keep. Update it in the same change that adds, renames, or deletes a test.
A row without a class name is planned work, not coverage — treat it as a gap.

The automated device layer runs on the Gradle-managed Pixel 6 / API 35 emulator (`pixel6api35DebugAndroidTest`).

## Automated

| Area | Class | Layer | Guards | Added in |
|---|---|---|---|---|
| Tab navigation | `TabNavigationTest` (2 tests) | UI | Tabs switch screens; selected tab survives `recreate()` | Phase 0 |
| Content validation | `domain/ContentRulesTest` (10 tests) | unit | Blank/duplicate names, 200-char text cap, zero exercises, duplicate exercise, sets 1..10, reps 1..30, min <= max, RIR 0..5 | Phase 2 |
| Content repository | `data/ContentRepositoryTest` (11 tests) | data | Custom exercise/template round trip, premade read-only, duplicate, reorder/remove, archive hides but keeps template rows, delete archives a referenced exercise, cards carry counts and names, profile stats | Phase 2 |
| Content screens | `ui/ScreenTest` (10 tests) | UI | Workouts filter/create, validation messages, premade duplicate only, custom exercise create/edit/archive, referenced delete offers archive, profile persists and Settings opens, bottom tab labels fit at 1.3x, exercise-picker rows meet 48dp | Phase 2/7 |
| Session rules | `domain/SessionRulesTest` (8 tests) | unit | Prefill from the matching position, current-session fallback, blank first time, RIR default 2, working-index renumbering, set field bounds, non-finite weight rejected, elapsed formatting | Phase 3 |
| Rest timer rules | `domain/RestTimerTest` (6 tests) | unit | Per-type seconds, zero disables, remaining clamped at zero, elapsed deadline ignores wall-clock changes, ceil countdown format, m:ss format | Phase 4 |
| Rest timer state | `data/RestTimerRepositoryTest` (9 tests) | data | Each type schedules its default, zero disabled, new set replaces, skip serializes with an in-flight set, finish/discard clear, end time survives repository recreate, finished sessions reject late sets | Phase 4 |
| Rest timer alerts | `timer/RestTimerTest` (11 tests) | device | Ongoing countdown and exact alarm, stale completion/start/cancel ignored, completion notification and state clear, channel sound/vibration, direct alarm-usage sound/vibration alert when notifications are off, a muted, silenced, or missing completion channel falls back to the direct alert | Phase 4/7 |
| Rest timer screen | `ui/RestTimerTest` (10 tests) | UI | Rest card after a set, Skip clears it, `recreate()` restores it, finish/discard/discard-and-start cancel it, leaving Active/Workouts during writes preserves cleanup, zero-rest replacement cancels the prior alarm, expired cleanup preserves a newer rest, Settings defaults persist across recreate | Phase 4 |
| Rest permission recovery | `ui/RestPermissionTest` (1 test) | UI | Restoring an unprompted saved timer requests notification permission once, including screen re-entry; only Android's permission boundary is substituted | Phase 4 |
| Profile/settings store | `data/ProfileSettingsStoreTest` (5 tests) | data | Profile/settings persistence, notification permission requested once, settings save does not clear a running rest | Phase 1/4 |
| Session repository | `data/SessionRepositoryTest` (12 tests) | data | Template snapshot copy, template edit after start is invisible, one active session, warmup/working/failure positions, set validation, finish, warmups-only finish refused, discard cascades, relaunch restore, finished-only history, bounded stays the latest overall plus the latest same-template session | Phase 3/5 |
| Set edits | `data/SetEditTest` (4 tests) | data | Retype working to warmup renumbers, delete a middle set renumbers, retype warmup to working appends, weight/reps/RIR edit keeps the position | Phase 3 |
| Active workout | `ui/ActiveWorkoutTest` (7 tests) | UI | Log a working set and see the next position target, `recreate()` restore, zero-working-set finish offers discard, Resume prompt for a second start, empty state, idle rest card explains automatic start, design RIR hint, named 48dp numeric inputs including the editor | Phase 3/7 |
| Progression targets | `domain/ProgressionTest` | unit | No history, below range, mid-range, top of range, per-position independence, missing position fallback, any-workout vs same-template, bodyweight | Phase 1 |
| e1RM and PRs | `domain/AnalyticsTest` (16 tests) | unit | Epley, strictly-greater PR chronology, ties are not PRs, window boundaries 28/30/90/365 days, warmups excluded, volume, best e1RM, heaviest-set tie by reps, session count, all-time seed stops a window-only PR, window plus seed equals the full-history stats, the Home clock follows a just-finished session but caps a far-future one at one day | Phase 1/5 |
| Units | `domain/UnitConversionTest` | unit | kg/lb round trip, 0.1 lb rounding, height stays cm | Phase 1 |
| Seeded content | `data/SeedTest` | data | 6 templates, total sets 9/16/17/13/12/16, exercise library present, seed idempotent | Phase 1 |
| Backup validation | `data/BackupTest` (21 tests) | unit | Captured phone backup round trip, export size guard accepts the limit and an injected limit, required nullable fields, strict field types, version/enums/ranges, UUIDs, references, duplicate names/IDs, timestamps, contiguous positions, deleted snapshots, oversized text | Phase 6 |
| Backup persistence | `data/BackupRepositoryTest` (8 tests) | data | All six tables plus profile/settings, file round trip into a fresh container including archived/custom rows, active guard, rejected files preserve data, actual SQLite failure rolls back, DataStore failure can retry, seed complete and rest cleared | Phase 6 |
| Settings controls | `ui/SettingsScreenTest` (6 tests) | UI | Live palette/system bars, kg/lb profile conversion without rounding drift, lb logging/editing preserves exact kilograms, previous source changes targets but not history, active session disables data actions, profile text over backup cap rejected, units card uses the design example copy | Phase 6/7 |
| Backup screen | `ui/BackupScreenTest` (7 tests) | UI | Real file export/import, preview/cancel/replace, live profile reload, timer alarm cleanup, malformed/newer files and picker cancellation, visible write errors, import failure/retry survives recreation, export and import timeouts report and unlock the screen, the export cap fails before the chosen file is opened. Only system picker substituted | Phase 6 |
| Theme contrast | `ui/theme/ThemeContrastTest` (4 tests) | UI | Light and dark body/card/muted/inset/link/button/badge text >= 4.5:1; failure badge readable in dark and light | Phase 7 |
| Finished history | `data/HistoryRepositoryTest` (3 tests) | data | Snapshot names/order, skipped exercises, warmups, active workouts excluded, reactive deletion cascade, recomputed PRs/profile totals, active-session deletion rejected with rest intact | Phase 5 |
| Bounded history query | `data/ExerciseHistoryQueryTest` (6 tests) | data | Latest overall plus latest same-template session, collapse when the newest is both, active sessions excluded, a newer session without sets never hides the older one, the template subquery also skips a same-template session without sets, `finishedAt` ties break by session id | Phase 3/5 |
| Home dashboard | `data/HomeDashboardTest` (6 tests) | data | 200 old sessions stay counted in SQL while only the 90-day window loads, the statement log shows no full-history query, the SQL seed matches `e1rm()` including warmup/bodyweight/tie shapes, the all-time seed stops a four-week tie and deleting it makes the tie a PR, the DAO window and seed reproduce the full-history four-week stats on both sides of the prior-window boundary, a far-future session does not shift the recent four-week stats | Phase 5 |
| PR recompute | `data/PrRecomputeTest` (3 tests) | data | Editing a later set above and below the earlier record, deleting the record promotes the next set, importing a backup replaces every previous PR set | Phase 5 |
| Analytics fixtures | `ui/ProgressScreenTest` (9 tests) | UI | Log via UI feeds Home 106.7 kg/2160 kg/3 sets/1 PR, Home strength card carries the design "Est. 1RM" label, range changes and all-time PR badges, empty/range-empty, max-four recent chips, skipped-first default, selection handoff, archived bodyweight history, units/recreation, load-failure retry recovery, confirmed deletion recalculates | Phase 5/7 |
| Menu and validation | `ui/ScreenTest` | UI | Blank/duplicate names rejected, bounds enforced, zero-working-set finish prompts discard | Phase 2 |

## Manual, release checklist

Run on the test phone (SM-S711W) against the signed release build. The automated suite runs on the emulator above; these checks stay physical because the payoff per tester is low and the setup cannot be reproduced in an emulator.

- [ ] Rest alert fires within ±2 s with the screen locked; sound and vibration; Skip cancels it
- [ ] `scripts/verify-release.ps1` passes: invalid signing files fail, debug needs no key, signer matches the approved key, R8/resource shrinking ran, no INTERNET, backup disabled, version 1/1.0.0
- [ ] Notification permission denied: in-app timer still runs, hint shown once
- [ ] Airplane mode: full app usable; `adb shell dumpsys package com.buhat.tracker | findstr INTERNET` shows nothing
- [ ] Signed release installed over the previous versionCode: data survives
- [ ] 1.3x font scale, long exercise/template names, empty states, 48dp touch targets
- [ ] Export on the phone, clear app data, import, counts and values match the hand-computed fixture

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
