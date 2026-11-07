# Exercises signing failures, then verifies the actual optimized distribution APK.
[CmdletBinding()]
param([Parameter(Mandatory = $true)][string]$SigningProperties)

$ErrorActionPreference = 'Continue'
$repo = Split-Path $PSScriptRoot -Parent
$gradlew = Join-Path $repo 'gradlew.bat'
$sdk = Join-Path $env:LOCALAPPDATA 'Android\Sdk'
$tools = Join-Path $sdk 'build-tools\35.0.0'
function Use-Jdk21 {
    $current = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin\java.exe' } else { (Get-Command java -ErrorAction SilentlyContinue).Source }
    if ($current -and (Test-Path $current)) {
        $version = (& $current -version 2>&1 | Select-Object -First 1) | Out-String
        if ($version -match '"21\.') { return }
    }
    $candidates = @(Get-ChildItem 'C:\Program Files\Eclipse Adoptium' -Directory -Filter 'jdk-21*' -ErrorAction SilentlyContinue | Sort-Object Name -Descending)
    if ($candidates.Count -eq 0) { throw 'JDK 21 not found. Install Temurin 21, then rerun.' }
    $env:JAVA_HOME = $candidates[0].FullName
}
Use-Jdk21
$evidence = Join-Path $repo 'app\build\release-check'
New-Item -ItemType Directory -Force $evidence | Out-Null
$temporary = Join-Path $env:LOCALAPPDATA ("Temp\opencode\buhat-signing-" + [guid]::NewGuid())
New-Item -ItemType Directory $temporary | Out-Null

function Require([bool]$condition, [string]$message) {
    if (-not $condition) { throw $message }
}

function Reject-Signing([string]$name, [string]$path, [string]$expected, [string]$task = ':app:assembleRelease') {
    $output = & $gradlew $task "-PkeystoreProperties=$path" --console=plain 2>&1
    $code = $LASTEXITCODE
    $output | Out-File (Join-Path $evidence "$name.log")
    Require ($code -ne 0) "$name unexpectedly built a release APK"
    Require (($output | Out-String).Contains($expected)) "$name failed for the wrong reason. See app/build/release-check/$name.log"
    Write-Output "PASS: $name"
}

Push-Location $repo
try {
    Reject-Signing 'missing-properties' (Join-Path $temporary 'missing.properties') 'Release signing requires an external keystore.properties file'
    $testProperties = Join-Path $temporary 'keystore.properties'
    [IO.File]::WriteAllText($testProperties, 'keyAlias=test')
    Reject-Signing 'incomplete-properties' $testProperties 'Release signing requires storeFile, storePassword, keyAlias, keyPassword'
    $missingKey = (Join-Path $temporary 'missing.jks').Replace('\', '/')
    [IO.File]::WriteAllText($testProperties, "storeFile=$missingKey`nstorePassword=test`nkeyAlias=test`nkeyPassword=test`n")
    Reject-Signing 'missing-key' $testProperties 'Release keystore does not exist'

    $inside = Join-Path $evidence 'keystore.properties'
    [IO.File]::WriteAllText($inside, 'keyAlias=test')
    Reject-Signing 'properties-inside-repo' $inside 'Release signing files must be outside the repository'
    $insideKey = Join-Path $evidence 'test.jks'
    [IO.File]::WriteAllText($insideKey, 'not a key')
    [IO.File]::WriteAllText($testProperties, "storeFile=$($insideKey.Replace('\', '/'))`nstorePassword=test`nkeyAlias=test`nkeyPassword=test`n")
    Reject-Signing 'key-inside-repo' $testProperties 'Release signing files must be outside the repository'

    Reject-Signing 'aggregate-without-key' (Join-Path $temporary 'missing.properties') 'Release signing requires an external keystore.properties file' ':app:assemble'
    Reject-Signing 'bundle-without-key' (Join-Path $temporary 'missing.properties') 'Release signing requires an external keystore.properties file' ':app:bundleRelease'

    $testKey = Join-Path $temporary 'test.jks'
    & (Join-Path $env:JAVA_HOME 'bin\keytool.exe') -genkeypair -keystore $testKey -storetype PKCS12 -alias test -keyalg RSA -keysize 2048 -validity 1 -dname 'CN=Buhat signing test' -storepass test-password -keypass test-password *> (Join-Path $evidence 'test-key.log')
    Require ($LASTEXITCODE -eq 0) 'Could not create disposable signing-test key'
    [IO.File]::WriteAllText($testProperties, "storeFile=$($testKey.Replace('\', '/'))`nstorePassword=wrong-password`nkeyAlias=test`nkeyPassword=wrong-password`n")
    Reject-Signing 'wrong-credentials' $testProperties 'password was incorrect'

    & $gradlew :app:assembleDebug --console=plain *> (Join-Path $evidence 'debug-without-key.log')
    Require ($LASTEXITCODE -eq 0) 'Debug builds must work without release credentials'
    Write-Output 'PASS: debug without release credentials'

    # Pinned approved release certificate (see docs/release.md). A fixed value catches a
    # wrong or stale keystore; a value derived from the same properties file cannot.
    $approvedSigner = '0ae9f79bb9726babb4ef39c926483721cc2ae49eca633cabfe5f3574b9f51bac'

    & $gradlew :app:assembleRelease "-PkeystoreProperties=$SigningProperties" --rerun-tasks --console=plain *> (Join-Path $evidence 'signed-build.log')
    Require ($LASTEXITCODE -eq 0) 'Signed release build failed. See app/build/release-check/signed-build.log'
    $build = Get-Content (Join-Path $evidence 'signed-build.log') -Raw
    Require ($build -match '> Task :app:minifyReleaseWithR8\s') 'R8 did not run'
    Require ($build -match '> Task :app:(shrinkReleaseRes|optimizeReleaseResources)\s') 'Resource shrinking did not run'
    $apk = Join-Path $repo 'app\build\outputs\apk\release\app-release.apk'
    Require (Test-Path $apk) 'Signed release APK is missing'

    $signature = & (Join-Path $tools 'apksigner.bat') verify --verbose --print-certs $apk 2>&1
    Require ($LASTEXITCODE -eq 0) 'APK signature verification failed'
    Require (($signature | Out-String).ToLowerInvariant().Contains($approvedSigner)) 'APK signer does not match the approved release key'
    $signature | Write-Output

    $badging = & (Join-Path $tools 'aapt.exe') dump badging $apk
    Require ($LASTEXITCODE -eq 0) 'Could not inspect APK metadata'
    $metadata = $badging | Out-String
    Require ($metadata -match "package: name='com.buhat.tracker' versionCode='2' versionName='1.0.0'") 'Wrong package or release version'
    Require ($metadata -match "sdkVersion:'26'") 'Wrong minimum SDK'
    Require ($metadata -match "targetSdkVersion:'35'") 'Wrong target SDK'
    Require ($metadata -notmatch 'application-debuggable') 'Release is debuggable'
    $manifest = & (Join-Path $tools 'aapt.exe') dump xmltree $apk AndroidManifest.xml
    Require ($LASTEXITCODE -eq 0) 'Could not inspect packaged manifest'
    $xml = $manifest | Out-String
    Require ($xml -match 'android:allowBackup\([^\r\n]*\)=\(type 0x12\)0x0') 'Android backup is not disabled'
    Require ($xml -notmatch 'android.permission.INTERNET') 'Release has INTERNET permission'
    Write-Output 'PASS: R8, resource shrinking, package/version, backup disabled, non-debuggable, no INTERNET permission'
    Write-Output "APK: $apk"
    Write-Output "SHA-256: $((Get-FileHash $apk -Algorithm SHA256).Hash)"
    Write-Output 'RESULT: PASS'
} finally {
    Remove-Item $temporary -Recurse -Force -ErrorAction SilentlyContinue
    Remove-Item (Join-Path $evidence 'keystore.properties'), (Join-Path $evidence 'test.jks') -Force -ErrorAction SilentlyContinue
    Pop-Location
}
