# Test catalog

The list of tests we keep. Update it in the same change that adds, renames, or deletes a test.
A row without a class name is planned work, not coverage — treat it as a gap.

## Automated

| Area | Class | Layer | Guards | Added in |
|---|---|---|---|---|
| Tab navigation | `TabNavigationTest` (2 tests) | UI | Tabs switch screens; selected tab survives `recreate()` | Phase 0 |
| Content validation | `domain/ContentRulesTest` (10 tests) | unit | Blank/duplicate names, 200-char text cap, zero exercises, duplicate exercise, sets 1..10, reps 1..30, min <= max, RIR 0..5 | Phase 2 |
| Content repository | `data/ContentRepositoryTest` (11 tests) | data | Custom exercise/template round trip, premade read-only, duplicate, reorder/remove, archive hides but keeps template rows, delete archives a referenced exercise, cards carry counts and names, profile stats | Phase 2 |
| Content screens | `ui/ScreenTest` (7 tests) | UI | Workouts filter/create, validation messages, premade duplicate only, custom exercise create/edit/archive, referenced delete offers archive, profile persists and Settings opens, bottom tab labels fit at 1.3x | Phase 2/7 |
| Session rules | `domain/SessionRulesTest` (7 tests) | unit | Prefill from the matching position, current-session fallback, blank first time, RIR default 2, working-index renumbering, set field bounds, elapsed formatting | Phase 3 |
| Rest timer rules | `domain/RestTimerTest` (6 tests) | unit | Per-type seconds, zero disables, remaining clamped at zero, elapsed deadline ignores wall-clock changes, ceil countdown format, m:ss format | Phase 4 |
| Rest timer state | `data/RestTimerRepositoryTest` (9 tests) | data | Each type schedules its default, zero disabled, new set replaces, skip serializes with an in-flight set, finish/discard clear, end time survives repository recreate, finished sessions reject late sets | Phase 4 |
| Rest timer alerts | `timer/RestTimerTest` (8 tests) | device | Ongoing countdown and exact alarm, stale alarm/completion/start/cancel ignored, completion notification and state clear, channel sound/vibration, direct sound/vibration alert when notifications are off | Phase 4/7 |
| Rest timer screen | `ui/RestTimerTest` (10 tests) | UI | Rest card after a set, Skip clears it, `recreate()` restores it, finish/discard/discard-and-start cancel it, leaving Active/Workouts during writes preserves cleanup, zero-rest replacement cancels the prior alarm, expired cleanup preserves a newer rest, Settings defaults persist across recreate | Phase 4 |
| Rest permission recovery | `ui/RestPermissionTest` (1 test) | UI | Restoring an unprompted saved timer requests notification permission once, including screen re-entry; only Android's permission boundary is substituted | Phase 4 |
| Profile/settings store | `data/ProfileSettingsStoreTest` (5 tests) | data | Profile/settings persistence, notification permission requested once, settings save does not clear a running rest | Phase 1/4 |
| Session repository | `data/SessionRepositoryTest` (10 tests) | data | Template snapshot copy, template edit after start is invisible, one active session, warmup/working/failure positions, set validation, finish, warmups-only finish refused, discard cascades, relaunch restore, finished-only history | Phase 3 |
| Set edits | `data/SetEditTest` (4 tests) | data | Retype working to warmup renumbers, delete a middle set renumbers, retype warmup to working appends, weight/reps/RIR edit keeps the position | Phase 3 |
| Active workout | `ui/ActiveWorkoutTest` (6 tests) | UI | Log a working set and see the next position target, `recreate()` restore, zero-working-set finish offers discard, Resume prompt for a second start, empty state, idle rest card explains automatic start, named 48dp numeric inputs including the editor | Phase 3/7 |
| Progression targets | `domain/ProgressionTest` | unit | No history, below range, mid-range, top of range, per-position independence, missing position fallback, any-workout vs same-template, bodyweight | Phase 1 |
| e1RM and PRs | `domain/AnalyticsTest` | unit | Epley, strictly-greater PR chronology, ties are not PRs, window boundaries 28/30/90/365 days, warmups excluded, volume, best e1RM, heaviest-set tie by reps, session count | Phase 1/5 |
| Units | `domain/UnitConversionTest` | unit | kg/lb round trip, 0.1 lb rounding, height stays cm | Phase 1 |
| Seeded content | `data/SeedTest` | data | 6 templates, total sets 9/16/17/13/12/16, exercise library present, seed idempotent | Phase 1 |
| Backup validation | `data/BackupTest` (19 tests) | unit | Captured phone backup round trip, required nullable fields, strict field types, version/enums/ranges, UUIDs, references, duplicate names/IDs, timestamps, contiguous positions, deleted snapshots, oversized text | Phase 6 |
| Backup persistence | `data/BackupRepositoryTest` (8 tests) | data | All six tables plus profile/settings, file round trip into a fresh container including archived/custom rows, active guard, rejected files preserve data, actual SQLite failure rolls back, DataStore failure can retry, seed complete and rest cleared | Phase 6 |
| Settings controls | `ui/SettingsScreenTest` (5 tests) | UI | Live palette/system bars, kg/lb profile conversion without rounding drift, lb logging/editing preserves exact kilograms, previous source changes targets but not history, active session disables data actions, profile text over backup cap rejected | Phase 6 |
| Backup screen | `ui/BackupScreenTest` (4 tests) | UI | Real file export/import, preview/cancel/replace, live profile reload, timer alarm cleanup, malformed/newer files and picker cancellation, visible write errors, import failure/retry survives recreation. Only system picker substituted | Phase 6 |
| Theme contrast | `ui/theme/ThemeContrastTest` (4 tests) | UI | Light and dark body/card/muted/inset/link/button/badge text >= 4.5:1; failure badge readable in dark and light | Phase 7 |
| Finished history | `data/HistoryRepositoryTest` (3 tests) | data | Snapshot names/order, skipped exercises, warmups, active workouts excluded, reactive deletion cascade, recomputed PRs/profile totals, active-session deletion rejected with rest intact | Phase 5 |
| Analytics fixtures | `ui/ProgressScreenTest` (8 tests) | UI | Log via UI feeds Home 106.7 kg/2160 kg/3 sets/1 PR, range changes and all-time PR badges, empty/range-empty, max-four recent chips, skipped-first default, selection handoff, archived bodyweight history, units/recreation, load-failure retry recovery, confirmed deletion recalculates | Phase 5 |
| Menu and validation | `ui/ScreenTest` | UI | Blank/duplicate names rejected, bounds enforced, zero-working-set finish prompts discard | Phase 2 |

## Manual, release checklist

Run on the test phone (SM-S711W) against the signed release build. These are not automated because the payoff per tester is low and the setup is physical.

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
