# Full E2E run for Buhat: JDK 21 + device gate, isolated data, unit + data + UI suite.
# Run from anywhere:  powershell -NoProfile -ExecutionPolicy Bypass -File .opencode/skills/e2e-testing/scripts/run-e2e.ps1
[CmdletBinding()]
param(
    # 'all' is the only mode that counts as a run. 'unit'/'device' are for chasing a failure.
    [ValidateSet('all', 'unit', 'device')]
    [string]$Layer = 'all',

    # Device test class or class#method, e.g. com.buhat.tracker.TabNavigationTest
    [string]$Feature = '',

    # Skip adb pm clear. Only for fast iteration; never for the final run.
    [switch]$KeepData
)

# 'Continue' on purpose: Gradle writes failures to stderr, and PS 5.1 turns native stderr
# into a terminating error under 'Stop'. Exit codes are checked explicitly instead.
$ErrorActionPreference = 'Continue'
$AppId = 'com.buhat.tracker'
$repo = (Resolve-Path $PSScriptRoot).Path
while ($repo -and -not (Test-Path (Join-Path $repo 'gradlew.bat'))) {
    $parent = Split-Path $repo -Parent
    if (-not $parent -or $parent -eq $repo) { throw "Could not find gradlew.bat above $PSScriptRoot" }
    $repo = $parent
}
$gradlew = Join-Path $repo 'gradlew.bat'

# --- JDK 21 gate. JDK 24 is the machine default and AGP 8.7.3 rejects it. ---
function Use-Jdk21 {
    $current = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin\java.exe' } else { (Get-Command java -ErrorAction SilentlyContinue).Source }
    if ($current -and (Test-Path $current)) {
        $version = (& $current -version 2>&1 | Select-Object -First 1) | Out-String
        if ($version -match '"21\.') { return }
    }
    $candidates = @(Get-ChildItem 'C:\Program Files\Eclipse Adoptium' -Directory -Filter 'jdk-21*' -ErrorAction SilentlyContinue | Sort-Object Name -Descending)
    if ($candidates.Count -eq 0) { throw "JDK 21 not found. Install Temurin 21, then rerun." }
    $env:JAVA_HOME = $candidates[0].FullName
    Write-Host "JAVA_HOME=$env:JAVA_HOME"
}
Use-Jdk21

$adb = Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe'
if (-not (Test-Path $adb)) { $adb = (Get-Command adb -ErrorAction SilentlyContinue).Source }
if (-not $adb) { throw "adb not found. Install Android platform-tools." }

# --- Device gate. A locked phone fails every Compose test with 'No compose hierarchies found'. ---
if ($Layer -ne 'unit') {
    $deviceLine = (& $adb devices) | Where-Object { $_ -match '\sdevice\s*$' } | Select-Object -First 1
    if (-not $deviceLine) { throw "No device ready. Plug in the test phone, accept the debugging prompt, rerun. adb output:`n$(& $adb devices)" }
    $serial = ($deviceLine -split '\s+')[0]

    & $adb -s $serial shell input keyevent KEYCODE_WAKEUP 2>&1 | Out-Null
    & $adb -s $serial shell wm dismiss-keyguard 2>&1 | Out-Null
    # Test phone is 1080x2340; swipe up to clear a simple keyguard.
    & $adb -s $serial shell input swipe 540 2000 540 500 200 2>&1 | Out-Null
    Start-Sleep -Milliseconds 800
    $window = (& $adb -s $serial shell dumpsys window) | Out-String
    if ($window -match 'mDreamingLockscreen=true') {
        throw "Phone $serial is locked. Unlock it by hand, then rerun."
    }

    if ($KeepData) {
        Write-Host "Keeping app data on $serial (-KeepData)."
    }
    else {
        Write-Host "Clearing $AppId data on $serial. Any real training log on this phone is lost."
        & $adb -s $serial shell pm clear $AppId 2>&1 | Out-Null
    }
}

# --- Stale result XML would pollute the counts. ---
foreach ($dir in @(
        (Join-Path $repo 'app\build\test-results\testDebugUnitTest'),
        (Join-Path $repo 'app\build\outputs\androidTest-results\connected\debug'))) {
    if (Test-Path $dir) { Remove-Item $dir -Recurse -Force -ErrorAction SilentlyContinue }
}

$failedLayers = @()
$ranLayers = @()

if ($Layer -ne 'device') {
    Write-Host "`n== unit tests ==" -ForegroundColor Cyan
    & $gradlew testDebugUnitTest --console=plain
    if ($LASTEXITCODE -ne 0) { $failedLayers += 'unit' }
    $ranLayers += 'unit'
}

if ($Layer -ne 'unit') {
    Write-Host "`n== device tests (Room + Compose) ==" -ForegroundColor Cyan
    $gradleArgs = @('connectedDebugAndroidTest', '--console=plain')
    if ($Feature) { $gradleArgs += "-Pandroid.testInstrumentationRunnerArguments.class=$Feature" }
    & $gradlew @gradleArgs
    if ($LASTEXITCODE -ne 0) { $failedLayers += 'device' }
    $ranLayers += 'device'
}

# --- Counts. Gradle returns success on NO-SOURCE, which is not a pass. ---
function Get-ResultCounts([string[]]$Paths) {
    $tests = 0
    $bad = 0
    foreach ($path in $Paths) {
        foreach ($file in (Get-ChildItem -Path $path -ErrorAction SilentlyContinue)) {
            try { $xml = [xml](Get-Content $file.FullName -Raw) } catch { continue }
            $suites = if ($xml.testsuite) { @($xml.testsuite) } elseif ($xml.testsuites) { @($xml.testsuites.testsuite) } else { @() }
            foreach ($suite in $suites) {
                $tests += [int]$suite.tests
                $bad += [int]$suite.failures + [int]$suite.errors
            }
        }
    }
    [pscustomobject]@{ Tests = $tests; Failed = $bad }
}

$unit = Get-ResultCounts @(Join-Path $repo 'app\build\test-results\testDebugUnitTest\TEST-*.xml')
$device = Get-ResultCounts @(Join-Path $repo 'app\build\outputs\androidTest-results\connected\debug\TEST-*.xml')

# --- Security check: the app must not gain network access. ---
$permission = 'not checked'
if ($Layer -ne 'unit') {
    $apk = Join-Path $repo 'app\build\outputs\apk\debug\app-debug.apk'
    $buildTools = Get-ChildItem (Join-Path $env:LOCALAPPDATA 'Android\Sdk\build-tools') -Directory -ErrorAction SilentlyContinue | Sort-Object Name -Descending | Select-Object -First 1
    if ((Test-Path $apk) -and $buildTools) {
        $aapt = Join-Path $buildTools.FullName 'aapt.exe'
        if (Test-Path $aapt) {
            $dump = (& $aapt dump permissions $apk) | Out-String
            $permission = if ($dump -match 'android\.permission\.INTERNET') { 'PRESENT (must be removed)' } else { 'absent' }
            if ($permission -ne 'absent') { $failedLayers += 'permission' }
        }
    }
}

# --- Summary ---
$noTests = @()
$skipped = @()
$unitSources = @(Get-ChildItem (Join-Path $repo 'app\src\test') -Recurse -Include '*.kt', '*.java' -ErrorAction SilentlyContinue).Count
$deviceSources = @(Get-ChildItem (Join-Path $repo 'app\src\androidTest') -Recurse -Include '*.kt', '*.java' -ErrorAction SilentlyContinue).Count
if (('unit' -in $ranLayers) -and $unit.Tests -eq 0) { if ($unitSources -eq 0) { $skipped += 'unit' } else { $noTests += 'unit' } }
if (('device' -in $ranLayers) -and $device.Tests -eq 0) { if ($deviceSources -eq 0) { $skipped += 'device' } else { $noTests += 'device' } }

Write-Host "`n== E2E summary ==" -ForegroundColor Cyan
Write-Host ("unit    : {0} tests, {1} failed{2}" -f $unit.Tests, $unit.Failed, $(if ('unit' -in $skipped) { ' (no test sources yet)' } else { '' }))
Write-Host ("device  : {0} tests, {1} failed{2}" -f $device.Tests, $device.Failed, $(if ('device' -in $skipped) { ' (no test sources yet)' } else { '' }))
Write-Host ("INTERNET permission: {0}" -f $permission)

if ($noTests.Count -gt 0) {
    Write-Host ("NO TESTS RAN in: {0}. Not a pass." -f ($noTests -join ', ')) -ForegroundColor Yellow
    $failedLayers += 'empty'
}
if ($Feature -and $device.Tests -eq 0) { $failedLayers += 'filter' }

if ($failedLayers.Count -gt 0) {
    Write-Host ("RESULT: FAIL ({0})" -f (($failedLayers | Select-Object -Unique) -join ', ')) -ForegroundColor Red
    exit 1
}
Write-Host "RESULT: PASS" -ForegroundColor Green
exit 0
