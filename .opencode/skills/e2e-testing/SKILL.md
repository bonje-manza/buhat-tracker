---
name: E2E Testing
description: Use when testing any Buhat feature, fixing a bug, or building a release APK - runs the unit, data-layer, and Compose UI suite on the isolated test setup, proves the new feature against hand-computed expected values, and keeps the regression suite in sync.
---

# E2E Testing (Buhat)

Buhat is a single-module Android app: Kotlin, Compose, Room, DataStore. Offline, no server, no web client.
So "backend" means the Room/DataStore/repository/domain layers and "frontend" means Compose. Playwright does not apply here and must not be added for this app.

## Core rules

1. A run has two jobs: prove the new feature, then prove nothing else broke. Run the full suite every time, not just the new test.
2. Expected values are literals you computed by hand. Never compute the expected value with the same formula the code uses. A test that re-implements Epley to check Epley proves nothing.
3. Tests never touch real data. No file database, no developer install state, no phone data left over from a previous run.
4. A green run with `NO-SOURCE` or 0 tests is not a pass. The script flags it.

## 1. Environment gate

Run these checks before any test work. The locked-phone and JDK failures have already happened in this repo.

| Need | Check | Fix |
|---|---|---|
| JDK 21 | `java -version` says 21 | `$env:JAVA_HOME='C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot'`. JDK 24 is the machine default; the build is only verified on 21. |
| Device | `adb devices` lists `SM-S711W` as `device` | Reseat USB, accept the debugging prompt |
| Unlocked screen | `adb shell dumpsys window` shows `mDreamingLockscreen=false` | Wake + swipe up. A locked phone fails every Compose test with `No compose hierarchies found in the app.` |
| Build tools | `app/build` exists, `gradlew.bat` runs | `.\gradlew.bat --version` with the JDK 21 `JAVA_HOME` |

The runner does all four. Prefer it over typing commands by hand.

## 2. Isolation

| Layer | Where it runs | Database | Seeded by | Reset |
|---|---|---|---|---|
| Unit | JVM, `app/src/test` | none | inline fixtures | none needed |
| Data | device, `app/src/androidTest` | `Room.inMemoryDatabaseBuilder` | fixture seeders | new in-memory DB per test class |
| UI | device, `app/src/androidTest` | in-memory via the app container test hook, or wiped app data | fixture seeders | `pm clear` before the run + `@Before` reset |

Hard rules:

- No test opens the on-device file database. In-memory only.
- The runner wipes `com.buhat.tracker` (`adb shell pm clear`) before device runs. That is the isolation boundary. It also destroys real training data on the phone, so export first when the phone holds a real log. `-KeepData` skips the wipe for fast iteration.
- There is no server and no API key, so "test keys only" reduces to: no network code, no key in a fixture, and no `INTERNET` permission in the merged manifest. The runner checks the permission on the debug APK.
- One test-only hook is allowed in main code: `BuhatApplication.container.resetForTest()` (Phase 1+). It swaps in an in-memory database so a UI test starts from a known state. Nothing else test-only ships in `main`.

## 3. The three layers

The domain and data layers do not exist yet (repo is at Phase 0: tabs and empty screens). The examples below fix the shape; use the real class and function names when those layers land.

### Unit — domain rules

`app/src/test/java/com/buhat/tracker/domain/`. Pure Kotlin, no Android. This is where progression, e1RM, PR chronology, window boundaries, and unit conversion are proven. Add `testImplementation("junit:junit:4.13.2")` to `app/build.gradle.kts` with the first test.

```kotlin
class ProgressionTest {
    // Last session on a 3x6-10 exercise at 80 kg: 10 / 9 / 8 reps.
    private val previous = listOf(
        set(workingIndex = 1, weightKg = 80.0, reps = 10),
        set(workingIndex = 2, weightKg = 80.0, reps = 9),
        set(workingIndex = 3, weightKg = 80.0, reps = 8),
    )

    @Test
    fun `each position advances on its own`() {
        // P1 hit the 10-rep top: add 2.5 kg, restart at repMin 6.
        // P2 is one short: 80 kg, 9 + 1 = 10.
        // P3 is two short: 80 kg, 8 + 1 = 9.
        val targets = nextTargets(repMin = 6, repMax = 10, previous = previous)

        assertEquals(Target(82.5, 6), targets[1])
        assertEquals(Target(80.0, 10), targets[2])
        assertEquals(Target(80.0, 9), targets[3])
    }
}
```

### Data — what a user actually does, through the repositories

`app/src/androidTest/java/com/buhat/tracker/data/`. No HTTP, so this is the backend analog: call the repository in the same order a user taps, then assert the rows that landed. Assert stored values, not values recomputed by another function.

```kotlin
@RunWith(AndroidJUnit4::class)
class SessionRepositoryTest {
    private lateinit var db: BuhatDatabase

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(), BuhatDatabase::class.java,
        ).build()
        seedFakeLibrary(db.exerciseDao())
    }

    @After fun tearDown() = db.close()

    @Test
    fun `logging a warmup and two working sets stores them in order`() = runTest {
        val repo = SessionRepository(db.sessionDao(), db.setDao())
        val session = repo.start(templateName = "Push Day A")

        // A user taps: warmup 40x10, working 60x8 RIR 2, working 62.5x7 RIR 1.
        repo.logSet(session.id, type = WARMUP, weightKg = 40.0, reps = 10, rir = null)
        repo.logSet(session.id, type = WORKING, weightKg = 60.0, reps = 8, rir = 2)
        repo.logSet(session.id, type = WORKING, weightKg = 62.5, reps = 7, rir = 1)
        repo.finish(session.id)

        val sets = repo.finishedSets(session.id)
        assertEquals(listOf(40.0, 60.0, 62.5), sets.map { it.weightKg })
        assertEquals(listOf(null, 1, 2), sets.map { it.workingIndex })  // warmup has no position
    }
}
```

### UI — Compose, driven like a finger

`app/src/androidTest/java/com/buhat/tracker/`. Use `createAndroidComposeRule<MainActivity>()`, tap and type through `testTag` nodes, assert on visible text you wrote out yourself.

```kotlin
@Test
fun userLogsAWorkingSet() {
    compose.onNodeWithTag("tab-Active").performClick()
    compose.onNodeWithTag("type-Working").performClick()
    compose.onNodeWithTag("weight-input").performTextReplacement("60")
    compose.onNodeWithTag("reps-input").performTextReplacement("8")
    compose.onNodeWithTag("log-set").performClick()

    compose.onNodeWithTag("set-row-1").assertTextContains("60 kg × 8", substring = true)
}
```

Every screen keeps a `testTag` on each tab and screen (`tab-*`, `screen-*`, already in `BuhatApp.kt`). New interactive elements get a tag when they are built, not when a test needs them.

## 4. Hand-computed expectations

Write the arithmetic in a comment, then the literal. If the expected value is awkward, run the code once, verify the output with a calculator or by hand, then hardcode it. Do not call the production function in the expected-value slot.

Worked example, the roadmap's Phase 3 fixture (3 × 6-10 at 80 kg, last reps 10/9/8):

- Position 1: `10 >= repMax 10` → weight `80 + 2.5 = 82.5`, reps `repMin = 6` → `82.5 × 6`
- Position 2: `9 < 10` → weight stays `80`, reps `min(9 + 1, 10) = 10` → `80 × 10`
- Position 3: `8 < 10` → weight stays `80`, reps `min(8 + 1, 10) = 9` → `80 × 9`
- e1RM of `80 × 10`: `80 × (1 + 10/30) = 106.666…` → `106.7`
- Volume of `80×10 + 80×9`: `800 + 720 = 1520`

Applies to every layer: the data test asserts `82.5`, the UI test asserts the literal text `82.5 × 6`, the unit test asserts `106.7`.

## 5. Edge cases we cover, and the ones we skip

The audience is the developer and a few friends on a handful of phones. Edge cases earn a test when a normal user can hit them or when the failure loses data. Rare-and-recoverable gets a manual check or nothing.

Automate:

| Case | Why it is worth a test |
|---|---|
| Warmup excluded from volume, working sets, e1RM, PRs | Warmups are logged every session; one leak corrupts every number |
| Per-position independence (position 2 never reads position 1) | This is the product's core rule |
| Bodyweight set (weight 0) gives no e1RM point, never adds weight | Common exercise type and a zero-weight path that skips e1RM |
| Finish with zero working sets prompts discard | Normal path at end of a session |
| Blank and duplicate names rejected (case-insensitive) | Typed on every create |
| Validation bounds: sets 1..10, reps 1..30, min <= max, RIR 0..5, weight 0..1000, rest 0..600 | Cheap, saves a crash |
| Retype a set renumbers working positions | Editing a mis-tapped type is normal |
| Delete a middle set renumbers the rest contiguously | Otherwise positions go stale |
| Only one active session; starting another prompts Resume/Discard | Process death and re-entry |
| Template edit after start does not change the active session | Snapshots are easy to break in a refactor |
| Invalid import writes nothing | Data-loss boundary, always tested |
| kg/lb round trip and display rounding | Toggled in Settings, seen on every screen |
| Process-death restore of session and logged sets | `activityRule.scenario.recreate()` covers it |

Manual on the phone only, in the release checklist (do not automate; the value is low for one tester and the setup cost is high):

- Rest alert within ±2 s with the screen locked; sound and vibration; Skip cancels it
- Notification permission denied, in-app timer still works
- Airplane mode: app fully usable, no network permission
- Signed release installed over the previous version, data survives
- 1.3x font scale, long names, empty states, 48dp targets

Skip, with the reason recorded in the catalog:

- Reboot during rest, clock changes, storage revoked mid-export, 20 MB import cap, localization, OEM battery tuning beyond the test phone. All are rare, mostly unrecoverable in v1, and none is worth a permanent test.

## 6. Run it

```powershell
# Full run: unit + data + UI, fresh data, new feature and full suite. This is the default and the only count that matters.
powershell -NoProfile -ExecutionPolicy Bypass -File .opencode/skills/e2e-testing/scripts/run-e2e.ps1

# One test class while iterating on a failure (not a complete run)
powershell -NoProfile -ExecutionPolicy Bypass -File .opencode/skills/e2e-testing/scripts/run-e2e.ps1 -Feature com.buhat.tracker.TabNavigationTest

# One test method
powershell -NoProfile -ExecutionPolicy Bypass -File .opencode/skills/e2e-testing/scripts/run-e2e.ps1 -Feature 'com.buhat.tracker.TabNavigationTest#tabsSwitchScreens'
```

The script prints each layer's test/failure counts, checks the `INTERNET` permission, and exits non-zero on any failure. `-Layer unit` and `-Layer device` exist to re-run one layer after a failure, never for a normal run.

## 7. Adding, keeping, and deleting tests

- New feature: write the feature test first, watch it fail, implement, then run the full suite.
- Bug fix: add a regression test that fails with the old code and carries the hand-computed value. A fix with no test did not happen.
- Delete a test when the behavior it guards is gone, or when another test asserts the same thing against the same values. Overlap is noise, not safety.
- Never delete: import/export data loss, progression targets, analytics math, permission checks.
- Update `references/test-catalog.md` in the same change. It is the list of what we keep.
- The runner script changes only when a layer is added (for example a web client, which is out of scope for v1). Needing to edit it per feature means you want `-Feature`, not a script change.

When to run: when a feature is complete, after a bug fix, before building a release APK, after an AGP/Kotlin/Compose/Room upgrade, and after touching shared domain or data code.

## 8. When it fails

| Symptom | Cause | Action |
|---|---|---|
| `No compose hierarchies found in the app` | Phone locked or dozing | Wake, swipe up, rerun. Already the first thing the script checks |
| `checksum` / `No matching variant` after a dependency edit | Stale Gradle cache | `.\gradlew.bat --stop` then rerun |
| `NO-SOURCE` or `0 tests ran` | Test file is in the wrong source set (`src/test` vs `src/androidTest`) | Move it, rerun |
| Old data shows up in a UI test | The `@Before` reset hook is missing | Add it; do not rely on the runner wipe for per-test state |
| Test passes but the feature is wrong | Expected value was copied from the code | Recompute by hand, fix the literal |
| Device test fails only on a second run | Data not cleared | Check `pm clear` output; do not use `-KeepData` for the final run |

## Common mistakes

- Re-implementing the formula in the test to produce the expected value. That is a tautology.
- Asserting on a color or a chart pixel. Assert on the text summary the chart renders.
- Adding a test for a case a user cannot reach (import during an active session is blocked by the UI, so test the guard, not the unreachable path).
- Adding `Playwright`, `MockWebServer`, or a network stack. There is no server.
- Treating a locked phone as a flaky test. It is an environment failure.
- Leaving a test that only checks framework behavior (Compose renders text). Delete it.
