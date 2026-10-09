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
    $tvBuiltApk = Join-Path $tvApkDirectory $tvOutputs[0].outputFile
    $tvSdk = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { $env:ANDROID_SDK_ROOT }
    if (-not $tvSdk -and (Test-Path 'local.properties')) {
        $tvSdkProperty = Get-Content 'local.properties' | Select-String '^sdk\.dir\s*=\s*(.+)$' | Select-Object -First 1
        if ($tvSdkProperty) { $tvSdk = $tvSdkProperty.Matches[0].Groups[1].Value.Replace('\:', ':').Replace('\\', '\') }
    }
    if (-not $tvSdk) { throw '无法定位 Android SDK，请设置 ANDROID_HOME 或 local.properties' }
    $tvApkSigner = Join-Path $tvSdk 'build-tools/34.0.0/apksigner.bat'
    $tvCertificateOutput = & $tvApkSigner verify --print-certs $tvBuiltApk
    if ($LASTEXITCODE -ne 0) { throw 'APK 签名校验失败' }
    $tvCertificate = @($tvCertificateOutput | Select-String '^Signer #1 certificate SHA-256 digest: (.+)$')
    $tvExpectedCertificate = (Get-Content 'signing-certificate.sha256' -Raw).Trim()
    if ($tvCertificate.Count -ne 1 -or $tvCertificate[0].Matches[0].Groups[1].Value -cne $tvExpectedCertificate) {
        throw 'APK 签名与固定证书不一致，停止复制发布产物'
    }
    $tvApkName = "LibreTV-Home-$tvVersion.apk"
    $tvApkPath = Join-Path $tvProject "dist/$tvApkName"
    New-Item -ItemType Directory -Force dist | Out-Null
    Copy-Item $tvBuiltApk $tvApkPath -Force
    $tvHash = Get-FileHash $tvApkPath -Algorithm SHA256
    "$($tvHash.Hash.ToLowerInvariant())  $tvApkName" | Set-Content "dist/SHA256-$tvVersion.txt" -Encoding ascii
    $tvHash
} finally { Pop-Location }
