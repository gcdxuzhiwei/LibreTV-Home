$ErrorActionPreference = 'Stop'
$tvProject = Split-Path $PSScriptRoot -Parent
Push-Location $tvProject
try {
    $tvBundledJdk = Get-ChildItem '.tools/jdk' -Directory -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($tvBundledJdk) { $env:JAVA_HOME = $tvBundledJdk.FullName }
    if (Test-Path '.tools/android-sdk') {
        $env:ANDROID_HOME = (Resolve-Path '.tools/android-sdk').Path
        Set-Content local.properties ('sdk.dir=' + $env:ANDROID_HOME.Replace('\', '/'))
    }
    $env:GRADLE_USER_HOME = Join-Path $tvProject '.tools/gradle-cache'
    if (Test-Path '.tools/gradle-8.9/bin/gradle.bat') {
        & '.tools/gradle-8.9/bin/gradle.bat' --no-daemon assembleDebug lintDebug
    } else {
        & './gradlew.bat' --no-daemon assembleDebug lintDebug
    }
    if ($LASTEXITCODE -ne 0) { throw 'Android 构建或 Lint 失败' }
    $tvApkDirectory = Join-Path $tvProject 'app/build/outputs/apk/debug'
    $tvMetadata = Get-Content (Join-Path $tvApkDirectory 'output-metadata.json') -Raw | ConvertFrom-Json
    $tvOutputs = @($tvMetadata.elements)
    if ($tvOutputs.Count -ne 1 -or -not $tvOutputs[0].versionName -or -not $tvOutputs[0].outputFile) {
        throw '无法确定唯一的 APK 输出和版本号'
    }
    $tvVersion = $tvOutputs[0].versionName
    $tvApkName = "LibreTV-Home-$tvVersion.apk"
    $tvApkPath = Join-Path $tvProject "dist/$tvApkName"
    New-Item -ItemType Directory -Force dist | Out-Null
    Copy-Item (Join-Path $tvApkDirectory $tvOutputs[0].outputFile) $tvApkPath -Force
    $tvHash = Get-FileHash $tvApkPath -Algorithm SHA256
    "$($tvHash.Hash.ToLowerInvariant())  $tvApkName" | Set-Content "dist/SHA256-$tvVersion.txt" -Encoding ascii
    $tvHash
} finally { Pop-Location }
