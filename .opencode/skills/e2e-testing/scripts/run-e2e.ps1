# Full E2E run for Buhat: JDK 21 + managed emulator, isolated data, unit + data + UI suite.
# Run from anywhere:  powershell -NoProfile -ExecutionPolicy Bypass -File .opencode/skills/e2e-testing/scripts/run-e2e.ps1
[CmdletBinding()]
param(
    # 'all' is the only mode that counts as a run. 'unit'/'device' are for chasing a failure.
    [ValidateSet('all', 'unit', 'device')]
    [string]$Layer = 'all',

    # Device test class or class#method, e.g. com.buhat.tracker.TabNavigationTest
    [string]$Feature = ''
)

# 'Continue' on purpose: Gradle writes failures to stderr, and PS 5.1 turns native stderr
# into a terminating error under 'Stop'. Exit codes are checked explicitly instead.
$ErrorActionPreference = 'Continue'
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

# --- Managed-device gate. Gradle provisions the emulator; no phone or adb targeting. ---
if ($Layer -ne 'unit') {
    $emulator = Join-Path $env:LOCALAPPDATA 'Android\Sdk\emulator\emulator.exe'
    if (-not (Test-Path $emulator)) {
        throw "Android Emulator not installed. Android Studio > SDK Manager > SDK Tools > Android Emulator, then rerun."
    }
    Write-Host 'Device layer: Gradle-managed Pixel 6 / API 35 (clean snapshot per run).'
}

# --- Stale result XML would pollute the counts. ---
foreach ($dir in @(
        (Join-Path $repo 'app\build\test-results\testDebugUnitTest'),
        (Join-Path $repo 'app\build\outputs\androidTest-results\managedDevice\debug'),
        (Join-Path $repo 'app\build\reports\androidTests\managedDevice'))) {
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
    Write-Host "`n== device tests (Room + Compose on the managed emulator) ==" -ForegroundColor Cyan
    # --rerun is required: managed-device results are cached, and the counts must reflect this run.
    $gradleArgs = @('pixel6api35DebugAndroidTest', '--console=plain', '--rerun')
    if ($Feature) { $gradleArgs += "-Pandroid.testInstrumentationRunnerArguments.class=$Feature" }
    & $gradlew @gradleArgs
    if ($LASTEXITCODE -ne 0) { $failedLayers += 'device' }
    $ranLayers += 'device'
}

# --- Counts. Gradle returns success on NO-SOURCE, which is not a pass. ---
function Get-ResultCounts([string[]]$Dirs) {
    $tests = 0
    $bad = 0
    $skipped = 0
    $parseErrors = 0
    foreach ($dir in $Dirs) {
        foreach ($file in (Get-ChildItem -Path $dir -Recurse -Filter 'TEST-*.xml' -ErrorAction SilentlyContinue)) {
            try { $xml = [xml](Get-Content $file.FullName -Raw) } catch { $parseErrors++; continue }
            $suites = if ($xml.testsuite) { @($xml.testsuite) } elseif ($xml.testsuites) { @($xml.testsuites.testsuite) } else { @() }
            foreach ($suite in $suites) {
                $tests += [int]$suite.tests
                $bad += [int]$suite.failures + [int]$suite.errors
                $skipped += [int]$suite.skipped
            }
        }
    }
    [pscustomobject]@{ Tests = $tests; Failed = $bad; Skipped = $skipped; ParseErrors = $parseErrors }
}

$unit = Get-ResultCounts @(Join-Path $repo 'app\build\test-results\testDebugUnitTest')
$device = Get-ResultCounts @(Join-Path $repo 'app\build\outputs\androidTest-results\managedDevice\debug')

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
$noSources = @()
$unitSources = @(Get-ChildItem (Join-Path $repo 'app\src\test') -Recurse -Include '*.kt', '*.java' -ErrorAction SilentlyContinue).Count
$deviceSources = @(Get-ChildItem (Join-Path $repo 'app\src\androidTest') -Recurse -Include '*.kt', '*.java' -ErrorAction SilentlyContinue).Count
if (('unit' -in $ranLayers) -and $unit.Tests -eq 0) { if ($unitSources -eq 0) { $noSources += 'unit' } else { $noTests += 'unit' } }
if (('device' -in $ranLayers) -and $device.Tests -eq 0) { if ($deviceSources -eq 0) { $noSources += 'device' } else { $noTests += 'device' } }
$skippedTests = $unit.Skipped + $device.Skipped
$parseErrors = $unit.ParseErrors + $device.ParseErrors

Write-Host "`n== E2E summary ==" -ForegroundColor Cyan
Write-Host ("unit    : {0} tests, {1} failed, {2} skipped{3}" -f $unit.Tests, $unit.Failed, $unit.Skipped, $(if ('unit' -in $noSources) { ' (no test sources yet)' } else { '' }))
Write-Host ("device  : {0} tests, {1} failed, {2} skipped{3}" -f $device.Tests, $device.Failed, $device.Skipped, $(if ('device' -in $noSources) { ' (no test sources yet)' } else { '' }))
Write-Host ("INTERNET permission: {0}" -f $permission)

if ($noTests.Count -gt 0) {
    Write-Host ("NO TESTS RAN in: {0}. Not a pass." -f ($noTests -join ', ')) -ForegroundColor Yellow
    $failedLayers += 'empty'
}
if ($Feature -and $device.Tests -eq 0) { $failedLayers += 'filter' }
if ($skippedTests -gt 0) {
    Write-Host ("SKIPPED TESTS: {0}. Not a pass." -f $skippedTests) -ForegroundColor Yellow
    $failedLayers += 'skipped'
}
if ($parseErrors -gt 0) {
    Write-Host ("UNREADABLE RESULT XML: {0} file(s). Not a pass." -f $parseErrors) -ForegroundColor Yellow
    $failedLayers += 'parse'
}

if ($failedLayers.Count -gt 0) {
    Write-Host ("RESULT: FAIL ({0})" -f (($failedLayers | Select-Object -Unique) -join ', ')) -ForegroundColor Red
    exit 1
}
Write-Host "RESULT: PASS" -ForegroundColor Green
exit 0
