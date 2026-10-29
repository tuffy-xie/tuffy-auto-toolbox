<#
.SYNOPSIS
    检查并配置 Android 构建环境（JDK 17 + Android SDK 34 + Gradle 8.9）。

.DESCRIPTION
    - 默认安装到 %USERPROFILE%\android-build，可用 -Root 覆盖。
    - 不删除、不移动任何已有目录；已存在的组件会直接复用。
    - 完成后写入 toolchain.json，供 build.ps1 使用。

.EXAMPLE
    powershell -ExecutionPolicy Bypass -File .\scripts\setup.ps1
#>
[CmdletBinding()]
param(
    [string]$Root = (Join-Path $env:USERPROFILE 'android-build')
)

$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'

function Log([string]$m) {
    $t = Get-Date -Format 'HH:mm:ss'
    Write-Host "[$t] $m"
}

function Find-First([string]$base, [string]$filter) {
    if (-not (Test-Path $base)) { return $null }
    $hit = Get-ChildItem $base -Recurse -Filter $filter -ErrorAction SilentlyContinue |
        Select-Object -First 1
    if ($hit) { return $hit.FullName }
    return $null
}

New-Item -ItemType Directory -Force -Path $Root | Out-Null
$jdkDir = Join-Path $Root 'jdk'
$sdkDir = Join-Path $Root 'sdk'
New-Item -ItemType Directory -Force -Path $jdkDir, $sdkDir | Out-Null

# ---------------- JDK 17 ----------------
$javaExe = Join-Path $jdkDir 'bin\java.exe'
if (-not (Test-Path $javaExe)) {
    Log '下载 Temurin JDK 17 ...'
    $zip = Join-Path $Root 'jdk.zip'
    if (-not (Test-Path $zip)) {
        Invoke-WebRequest -Uri 'https://api.adoptium.net/v3/binary/latest/17/ga/windows/x64/jdk/hotspot/normal/eclipse' -OutFile $zip -UseBasicParsing
    }
    Log '解压 JDK ...'
    Expand-Archive -Path $zip -DestinationPath $jdkDir -Force
    $inner = Get-ChildItem $jdkDir -Directory | Select-Object -First 1
    if ($inner -and -not (Test-Path $javaExe)) {
        Get-ChildItem $inner.FullName -Force | Move-Item -Destination $jdkDir -Force
    }
}
if (-not (Test-Path $javaExe)) { throw "JDK 安装失败：$javaExe 不存在" }
Log "JDK OK: $((cmd /c "`"$javaExe`" -version 2>&1" | Select-Object -First 1))"

# ---------------- Gradle 8.9 ----------------
$gradleBat = Find-First (Join-Path $Root 'gradle_8_9') 'gradle.bat'
if (-not $gradleBat) {
    Log '下载 Gradle 8.9 ...'
    $gzip = Join-Path $Root 'gradle-8.9-bin.zip'
    if (-not (Test-Path $gzip)) {
        Invoke-WebRequest -Uri 'https://services.gradle.org/distributions/gradle-8.9-bin.zip' -OutFile $gzip -UseBasicParsing
    }
    Log '解压 Gradle ...'
    $gdir = Join-Path $Root 'gradle_8_9'
    New-Item -ItemType Directory -Force -Path $gdir | Out-Null
    Expand-Archive -Path $gzip -DestinationPath $gdir -Force
    $gradleBat = Find-First $gdir 'gradle.bat'
}
if (-not $gradleBat) { throw 'Gradle 安装失败：找不到 gradle.bat' }
Log "Gradle OK: $gradleBat"

# ---------------- Android cmdline-tools ----------------
$sdkManager = Find-First (Join-Path $Root 'cmdtools_11076708') 'sdkmanager.bat'
if (-not $sdkManager) {
    Log '下载 Android cmdline-tools ...'
    $czip = Join-Path $Root 'commandlinetools-win.zip'
    if (-not (Test-Path $czip)) {
        Invoke-WebRequest -Uri 'https://dl.google.com/android/repository/commandlinetools-win-11076708_latest.zip' -OutFile $czip -UseBasicParsing
    }
    Log '解压 cmdline-tools ...'
    $cdir = Join-Path $Root 'cmdtools_11076708'
    New-Item -ItemType Directory -Force -Path $cdir | Out-Null
    Expand-Archive -Path $czip -DestinationPath $cdir -Force
    $sdkManager = Find-First $cdir 'sdkmanager.bat'
}
if (-not $sdkManager) { throw 'cmdline-tools 安装失败：找不到 sdkmanager.bat' }
Log "sdkmanager OK: $sdkManager"

# ---------------- SDK packages ----------------
$env:JAVA_HOME = $jdkDir
$env:PATH = "$jdkDir\bin;$env:PATH"
$env:ANDROID_HOME = $sdkDir
$env:ANDROID_SDK_ROOT = $sdkDir

Log '接受 SDK 许可（批量 y）...'
1..30 | ForEach-Object { 'y' } | & $sdkManager --sdk_root=$sdkDir --licenses | Out-Null

Log '安装 platform-tools / platforms;android-34 / build-tools;34.0.0 ...'
& $sdkManager --sdk_root=$sdkDir 'platform-tools' 'platforms;android-34' 'build-tools;34.0.0'
if ($LASTEXITCODE -ne 0) { throw "sdkmanager 安装失败，退出码 $LASTEXITCODE" }

$okPlatform = Test-Path (Join-Path $sdkDir 'platforms\android-34\android.jar')
$okBuildTools = Test-Path (Join-Path $sdkDir 'build-tools\34.0.0')
if (-not $okPlatform) { throw 'platforms;android-34 未安装成功' }
if (-not $okBuildTools) { throw 'build-tools;34.0.0 未安装成功' }

$toolchain = [ordered]@{
    jdk        = $jdkDir
    sdk        = $sdkDir
    gradle     = $gradleBat
    sdkManager = $sdkManager
    createdAt  = (Get-Date).ToString('s:')
}
$toolchain | ConvertTo-Json | Set-Content -Path (Join-Path $Root 'toolchain.json') -Encoding UTF8

Log '=========================================='
Log '环境配置完成'
Log "JDK   : $jdkDir"
Log "SDK   : $sdkDir"
Log "Gradle: $gradleBat"
Log "配置  : $(Join-Path $Root 'toolchain.json')"
Log '=========================================='
