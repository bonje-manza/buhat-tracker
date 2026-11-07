# Buhat 1.0.0 release

## Signing and building

The permanent signing files live outside this repository at:

`C:\Users\Admin\.android\buhat-release`

That directory contains `buhat-release.jks` and `keystore.properties`. Its permissions are restricted to the current Windows account and SYSTEM. Keep both files private. The properties file contains the credentials needed to recover the key.

Write `storeFile` with forward slashes (for example `C:/Users/Admin/.android/buhat-release/buhat-release.jks`). The file is parsed as Java properties, so a raw backslash `\` is an escape character and breaks loading.

With JDK 21 and Android SDK build-tools 35.0.0 installed, run from the repository root:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/verify-release.ps1 -SigningProperties C:\Users\Admin\.android\buhat-release\keystore.properties
```

This runs signing-failure checks and builds/verifies the optimized APK. Output: `app/build/outputs/apk/release/app-release.apk`. Build/check logs: `app/build/release-check/`. The terminal prints the APK hash and signer certificate fingerprint. The verifier compares the APK signer against the approved fingerprint recorded below, so a wrong or stale keystore fails.

The distribution version is versionCode **2**, versionName **1.0.0**. Share the APK only after the acceptance checks below pass.

## Backups and updates

1. Back up the keystore and properties together in two durable locations. Confirm each copy can open the key. A second directory on the same disk is not a second durable backup.
2. Before updating, export Buhat's data through Settings.
3. Increase `versionCode` in `app/build.gradle.kts`, set the intended `versionName`, and update the verifier's expected release version.
4. Rebuild using the same signing files. Compare the signer fingerprint with the previous release, then test `adb install -r <apk>` with existing profile/settings/workouts.
5. Distribute the verified APK. Users install it over their existing release without uninstalling.

The debug key is different from the release key. Moving from a debug install to release requires exporting any needed data, uninstalling debug, installing release, and importing. Losing the release key prevents same-install updates.

## Acceptance evidence

Automated checks below last passed on 2026-11-06: unit 74 tests, device 124 tests, 0 failed. Physical and owner-confirmed checks were confirmed on 2026-11-06.

- [x] Full unit, Room, and Compose suite. Record counts and terminal output.
- [x] Debug/release lint and unit build checks.
- [x] Invalid/missing signing files fail clearly. Debug builds need no release key.
- [x] Release signature matches the approved key. R8/resource shrinking ran.
- [x] Packaged manifest has no INTERNET permission, disables backup, and is non-debuggable.
- [x] Fresh signed install on SM-S711W: six templates, counts 9/16/17/13/12/16, airplane mode.
- [x] Create/edit/reorder/duplicate/delete content and log/edit/retype/delete sets.
- [x] For 80 kg × 10/9/8 on 3×6–10: targets 82.5×6, 80×10, 80×9; volume 2160 kg; e1RM 106.7 kg; 3 working sets; 1 PR. Warmups excluded.
- [x] Actual process termination restores active session, logged sets, and rest.
- [x] Locked-screen 60-second rest alerts within ±2 seconds, with sound and vibration. Skip, replacement, finish, and denial paths checked.
- [x] Themes/units, analytics/ranges/history/PRs, and confirmed workout deletion work on release.
- [x] Real SAF export/import restores all entities/profile/settings. Corrupted/newer backups leave data unchanged.
- [x] Five screens and editors checked against designs in dark/light at 1.0×/1.3×, with long names, empty states, keyboard open, contrast, and 48dp touch targets.
- [x] Same-key higher-versionCode release installed without data clearing; before/after exports match except export metadata.
- [x] Two recoverable signing backups confirmed by owner.
- [x] Friend's Android device installs and launches the distribution APK.

### Recorded results

- Signing material created on 2026-11-04 outside the repository. Two backups confirmed by owner on 2026-11-06.
- Approved signer SHA-256: `0AE9F79BB9726BABB4EF39C926483721CC2AE49ECA633CABFE5F3574B9F51BAC`.
- 2026-11-05 E2E runner: unit 74 tests / 0 failed, device 119 tests / 0 failed, INTERNET permission absent, `RESULT: PASS`.
- 2026-11-05 Rest completion alert: when notifications cannot be posted, the app now plays the alert sound and vibrates directly from the alarm path instead of staying silent. Adds the `VIBRATE` permission. Covered by `timer/RestTimerTest#completionWithoutNotificationPermissionPlaysADirectAlert` (red before the branch, green after).
- 2026-11-05 `:app:lintDebug :app:lintRelease :app:test` (JDK 21): `BUILD SUCCESSFUL`.
- 2026-11-05 `scripts/verify-release.ps1`: all nine signing-failure cases pass (including `:app:bundleRelease`), signer matches the pinned approved fingerprint, R8 and resource shrinking ran, manifest checks pass, `RESULT: PASS`. Signing the same code with a disposable different key fails with `APK signer does not match the approved release key`.
- Signed APK SHA-256: `E894BEB38A6982CECDFBAD36838BB0DF8CBCC791D4AB0491DB238E5AA5050E37`.
- 2026-11-05 Rest completion vibration: the completion channel now sets an explicit vibration pattern and was bumped to `rest_complete_v2` (channels are immutable), so the alert vibrates with a defined pattern instead of relying on the OEM default. The direct path already vibrated.
- 2026-11-05 signed release install on SM-S711W: launches offline in airplane mode; fresh seed shows six templates with set counts Push Day A 9, Pull Day A 16, Leg Day 17, Push Day B 13, Pull Day B 12, Leg Day B 16.
- 2026-11-06 locked-screen rest on the signed APK: a 60-second warmup rest posted its completion notification 61.1 seconds after the set was logged. System logs show the notification sound player and the `0/400/200/400` vibration pattern from `com.buhat.tracker`; Skip removed the rest card and canceled the ongoing notification.
- 2026-11-06 notification-denied fallback defect and fix: the direct alert was posted as a background MEDIA vibration and AudioHardening muted its sound. `timer/RestTimerTest#directAlertUsesAlarmUsageSoBackgroundPlaybackIsAllowed` failed before the change and passes after the direct alert uses `AudioAttributes.USAGE_ALARM` for both the ringtone and vibration.
- 2026-11-06 visual pass fixes, each red before the change and green after: Home shows the design's `Est. 1RM` label in the strength trend header (`ProgressScreenTest#homeStrengthCardCarriesTheDesignEstimatedOneRepMaxLabel`), Settings uses the design's units example copy (`SettingsScreenTest#unitsCardUsesTheDesignExampleCopy`), template exercise-picker rows meet the 48dp touch target (`ScreenTest#exercisePickerRowsMeetThe48dpTouchTarget`), and the Active logging card shows the design's RIR helper line (`ActiveWorkoutTest#loggingCardCarriesTheDesignRirHint`).
- 2026-11-06 versionCode 2 distribution: the release is versionCode 2 / versionName 1.0.0 for the install-over-previous check. `scripts/verify-release.ps1` now accepts AGP 8.13's `optimizeReleaseResources` task and pins versionCode 2. `RESULT: PASS`, APK SHA-256 `1784681FFBEAFF5DC815AACD99A6C4DB359B9813A0B72136A968629950D3F29D`.
