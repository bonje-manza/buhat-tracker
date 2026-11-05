# Phase 7 Visual Pass and Release Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans for native execution, or superpowers:subagent-driven-development if the user selects delegation. Track each step below.

**Goal:** Produce a signed 1.0.0 APK that matches the five designs and passes the roadmap's physical-device checklist.

**Architecture:** Keep the existing single-module Compose app, shared components, repositories, and test isolation. Correct the existing UI in place and configure Android Gradle Plugin's release signing, R8, and resource shrinking. Use the current E2E runner plus a small release-artifact check, with physical acceptance recorded separately.

**Tech Stack:** Kotlin 2.1.0, Compose/Material 3, Room, DataStore, Gradle 8.11.1, AGP 8.7.3, JDK 21, PowerShell, Android SDK tools.

**Spec:** `planning/buhat-tracker-roadmap.md`, Phase 7 and sections 2, 4.4, 4.6. Visual references: `docs/design/{home-profile,workouts,active-workout,progress,settings}.png`.

## Global Constraints

- Stay on `phase-7-visual-pass-release`. Keep the roadmap's entire Excluded list out of scope.
- Package `com.buhat.tracker`, minSdk 26, compile/target 35, versionCode 1, versionName 1.0.0.
- No INTERNET permission, `allowBackup="false"`, no new dependencies or chart library.
- Preserve the agreed departures from mockup copy: per-position targets, total workouts, backup controls, and Skip only during a running rest.
- All existing loading, empty, error, confirmation, accessibility, and persistence behavior remains required.
- New behavior gets a failing test before implementation. Every new check also gets a deliberate mutation failure before acceptance.
- Reuse captured `app/src/test/resources/phase6-backup.json` for backup regression. Use literal, hand-derived numeric expectations.
- Tests use the existing in-memory reset hook. Get confirmation that phone data is disposable or exported before the runner clears it or a signature change requires uninstalling debug.
- Keep the keystore and its properties outside the repository. Never log or commit passwords. Both key-backup locations and second-device acceptance need user confirmation.

## Review Focus

1. Missing, partial, or wrong signing credentials must fail release builds clearly without breaking debug builds or falling back to a debug signature. Task 2 exercises this through Gradle.
2. Shrinking must preserve Room, JSON serialization, and the timer receiver. Task 3 repeats their user flows on the actual optimized APK.
3. Long names and 1.3× font scaling must leave labels and actions usable, including editors and the set dialog. Task 1 records device comparisons and regressions for reproduced defects.
4. Light-theme accent/error text and failure badges must remain readable. Task 1 checks actual rendered foreground/background contrast.
5. Replacing a debug install differs from updating a release. Task 3 proves a same-key, higher-versionCode update without clearing data between installs.

## Inspection and environment evidence

- 2026-11-04: SM-S711W (`R5CW91PYFCH`) connected, `mDreamingLockscreen=false`; Temurin 21.0.12.1 and Gradle 8.11.1 available.
- Installed app is debuggable, versionCode 1 / versionName 1.0.0. Current build script has no release signing or shrink configuration.
- Manifest already disables backup and has no INTERNET permission. `.gitignore` already covers `keystore.properties`, `*.jks`, and `*.keystore`.
- Sampled design pixels: background `#151515`, card `#262626`, inset tile `#1C1C1C`, lime `#A6B53C`, orange `#EE7D4A`, stepper `#3A3A3A`.
- Source inspection shows an absent idle rest card, an unfilled chart without its endpoint marker, missing Inter roles, and several compact rows requiring phone checks. No baseline tests run during planning.

---

## Task 1: Visual alignment and accessibility

**Files:**
- Modify: `app/src/main/java/com/buhat/tracker/ui/theme/{Color,Theme,Type}.kt`.
- Modify: `app/src/main/java/com/buhat/tracker/ui/components/BuhatComponents.kt`.
- Modify: `app/src/main/java/com/buhat/tracker/ui/BuhatApp.kt`.
- Modify: `app/src/main/java/com/buhat/tracker/ui/screens/{HomeScreen,WorkoutsScreen,ActiveWorkoutScreen,ProgressScreen,SettingsScreen,TemplateEditorScreen}.kt`.
- Inspect on device; modify only for a reproduced defect: `ui/screens/{ProfileEditorScreen,ExerciseEditorScreen,ExerciseLibraryScreen}.kt` under the same main source root.
- Tests: existing `app/src/androidTest/java/com/buhat/tracker/ui/{ActiveWorkoutTest,ScreenTest,SettingsScreenTest}.kt` and `ui/components/BuhatComponentsTest.kt`.
- Create: `app/src/androidTest/java/com/buhat/tracker/ui/theme/ThemeContrastTest.kt`.
- Update: `.opencode/skills/e2e-testing/references/test-catalog.md` with tests actually added.

**Interfaces:** Keep the public screen and repository signatures. Extend `BuhatSegmentedControl` only with a defaulted selected-color parameter required by Active and Progress; existing Settings callers retain the neutral selection. Reuse Material color roles for inset tiles and controls.

- [ ] After phone-data confirmation, run the existing full E2E runner and retain its terminal output as the baseline. Resolve an environment failure before interpreting test failures.
- [ ] Write `idleRestCardExplainsAutomaticStartWithoutSkip` in `ActiveWorkoutTest`: start a seeded workout, assert visible `Ready for next set` and `Starts automatically after each set`, assert no `skip-rest`. Run the targeted class and record the missing-card failure.
- [ ] Write a component regression for the numeric input's actual 48dp minimum touch height and an accessible name in Active. Add the smallest semantics/size change after observing the assertion fail. Verify the corresponding edit-dialog inputs too.
- [ ] Write `textAndBadgeContrastMeetsFourPointFiveToOneInBothThemes` in `ThemeContrastTest`. Capture the actual Material color pairs, including primary text on background/surface, muted text on inset tiles, error text, and all set badges. Use AndroidX ColorUtils in the test, assert each ratio >= 4.5, and observe the present light-theme failures before correcting colors. Do not test chart pixels.
- [ ] Correct dark colors from the sampled reference and derive contrasting light accents. Use `onError` for the failure badge. Complete the Inter typography roles used by current screens and enable tabular numerals for numeric text.
- [ ] Align existing layouts with the reference: inset stat tiles, neutral pill secondary actions, orange primary actions, lime Active/Progress selections, filled line chart with orange final point, and the idle rest card. Keep chart text summaries and single-point/empty handling.
- [ ] At the phone's normal size, align profile header/stats, workout header/card actions, logging number/control order, Progress value/delta/history layout, and Settings section/stepper layout. Reuse current components, preserve test tags where labels stay the same, and update direct callers/tests when a visible label changes.
- [ ] Capture each screen and all three editors in dark and light at 1.0× and 1.3×. Include empty content, a 200-character exercise/template name, a 200-character profile name, the numeric extremes, and keyboard-open/save behavior. Record individual failures before changing a layout. Add targeted behavioral/geometry regressions only for reproduced defects, then fix with wrapping, weights, scrolling, or minimum sizes in the affected file. Template editor name/actions and four narrow numeric fields are specific inspection targets.
- [ ] Check 48dp touch bounds, focus/navigation, meaningful labels for symbol-only actions, dialog dismissal, and no clipped text. Preserve the design's deliberate ellipsis for workout exercise summaries, with full names available in the editor.
- [ ] Mutation-check new tests, restore mutations, then run the full E2E runner. Commit only this slice's files after the gate passes.

## Task 2: Fail-closed signed, optimized release build

**Files:**
- Modify: `app/build.gradle.kts`.
- Create: `scripts/verify-release.ps1`.
- Create: `docs/release.md` for the exact build/update procedure and physical acceptance evidence.
- Update: `.opencode/skills/e2e-testing/references/test-catalog.md` for the release check.

**Interfaces:** Build with `-PkeystoreProperties=<absolute external path>`. Properties are `storeFile`, `storePassword`, `keyAlias`, `keyPassword`; `storeFile` is an absolute external path. `scripts/verify-release.ps1 -SigningProperties <path>` runs real Gradle/SDK checks and exits nonzero on failure. Neither interface prints secrets. `assembleDebug` and debug tests need no release credentials.

- [ ] Confirm whether an existing release key must be reused. For a new key, agree its permanent external location and have the user retain its credentials and two backups. A disposable test key cannot qualify the distribution artifact.
- [ ] Write the negative signing check first: invoke `:app:assembleRelease` with a nonexistent external properties path, require a nonzero exit and the message `Release signing requires an external keystore.properties file`. Run it and retain the failure because the current build produces an unsigned APK instead.
- [ ] Add cases for a missing required property, nonexistent keystore, properties/key located inside the repo, and incorrect credentials. Use temporary external test files, clean them in `finally`, and assert failure without exposing their contents. Assert `assembleDebug` still works without the release properties.
- [ ] Add the minimal Gradle signing configuration and release-only validation dependency. Validate required fields and canonical paths, load the specified properties, and rely on AGP for cryptographic credential validation. Do not detect release builds by a brittle substring search of the command line.
- [ ] Set release `isMinifyEnabled = true`, `isShrinkResources = true`, and Android's default optimized ProGuard configuration. Add targeted keep rules only if a real optimized-build failure requires them.
- [ ] Extend the artifact check to build the signed APK, verify it with `apksigner verify --verbose --print-certs`, and match its certificate to the approved keystore. Check package/version/minSdk/targetSdk, `allowBackup=false`, non-debuggable, and no INTERNET permission using SDK tools. Require fresh R8 mapping/resource-shrinking task evidence from this build, not merely an old file.
- [ ] Run the negative tests and signed positive path. Mutation-check the signing guard and artifact assertions, restore them, and rerun. Record the final APK's path, SHA-256, signer fingerprint, and terminal output without credentials.
- [ ] Document the one build command, key backup/restore requirement, and same-key versionCode update steps in `docs/release.md`. Commit the passing build/check changes.

## Task 3: Release-device acceptance and final verification

**Files:** `docs/release.md`, this plan, `.opencode/skills/e2e-testing/references/test-catalog.md`, and the Phase 7 checklist in `planning/buhat-tracker-roadmap.md`. Mark only evidenced checks complete.

**Interfaces:** Use the exact signed, minified APK from Task 2 and real Android UI/SAF/alarm boundaries. The existing debug instrumentation suite remains the automated regression gate. Manual release acceptance uses disposable fixture data, not an in-memory hook or a debug substitute.

- [ ] Run full unit + Room + Compose regression through `powershell -NoProfile -ExecutionPolicy Bypass -File .opencode/skills/e2e-testing/scripts/run-e2e.ps1`. Capture actual counts, failures, permission check, and exit code. Also run `:app:lintDebug :app:lintRelease :app:test` using JDK 21 and release configuration as needed.
- [ ] Run `scripts/verify-release.ps1` again after the last code change. Install the signed APK on the test phone after preserving any needed debug data. Confirm a fresh offline launch has six templates with set counts 9, 16, 17, 13, 12, 16.
- [ ] Complete roadmap 4.6 on that APK: create/edit/reorder/duplicate/delete content, start and log all three set types, per-position raw prefill versus targets, edit/retype/delete active sets, finish/discard/resume, unit/theme changes, history/PR/range/deletion behavior, and backup through the real system picker. Compare rejected corrupted/newer backups against the pre-import data.
- [ ] Use a recorded 3×6–10 fixture at 80 kg with reps 10/9/8: next targets 82.5×6, 80×10, 80×9; volume 2160 kg; best e1RM 106.7 kg; three working sets; one PR. A warmup does not change these figures. Capture the resulting export and compare restored entities, profile, and settings.
- [ ] Prove restore after actual process termination, not only Activity recreation. Perform the physical 60-second locked-screen countdown, alert within ±2 seconds, audible sound/vibration, Skip/new-rest/finish cancellation, and notification-denied in-app path. Record user-observed sound/vibration and timing evidence.
- [ ] Repeat all five screens and editors in both themes with 1.3× font scale, long names, empty states, and airplane mode. Restore the phone's prior font/radio settings afterward.
- [ ] Prove update preservation: install code 1, create/export fixture data, temporarily build an otherwise identical code 2 with the same key, install with `adb install -r` without clearing data, and compare profile/settings/history/export. Restore committed versionCode 1 and retain the code-1 distribution APK separately. Record that the upgrade-test phone now has code 2.
- [ ] User confirms two recoverable key/credential backups and a friend's successful install/launch of the code-1 APK. These physical checks remain pending until confirmation exists.
- [ ] Run `verification-before-completion`, complexity review (`ponytail-review`), correctness review (`requesting-code-review`), and the signing/path/secrets security pass. Evaluate findings before edits, use TDD for accepted fixes, and rerun affected gates after changes.
- [ ] Confirm the staged/diff file list stays within scope and contains no secrets. Show real terminal output and clearly separate passing checks from any blocked physical acceptance. Use `finishing-a-development-branch` for the integration decision.

## Workflow ledger

- [x] Read the roadmap, designs, connected UI/build/tests, runner isolation, and environment.
- [x] Write and self-review a scoped implementation plan.
- [x] Re-arm ponytail and apply the cut gate. Reuse the current E2E runner and UI primitives; one artifact-check script, no build plugin, release-testing framework, or dependency additions.
- [x] User approves native execution.
- [x] User confirms phone data is disposable and authorizes a first release key under `C:\Users\Admin\.android\buhat-release`.
- [ ] Execute Tasks 1–3 with red/green and mutation evidence.
- [ ] Report verification and integration decision.
