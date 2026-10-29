<#
.SYNOPSIS
    一键编译 APK。

.DESCRIPTION
    优先读取 %USERPROFILE%\android-build\toolchain.json；
    找不到时自动在该目录下探测 JDK / SDK / Gradle。
    成功后把 APK 复制到 dist\ANA-Pocket-Auto-debug.apk。

.EXAMPLE
    powershell -ExecutionPolicy Bypass -File .\scripts\build.ps1
    powershell -ExecutionPolicy Bypass -File .\scripts\build.ps1 -Release
#>
[CmdletBinding()]
param(
    [string]$ProjectRoot = (Split-Path -Parent $PSScriptRoot),
    [string]$ToolchainRoot = (Join-Path $env:USERPROFILE 'android-build'),
    [switch]$Release
)

$ErrorActionPreference = 'Stop'

function Fail([string]$m) {
    Write-Host "ERROR: $m" -ForegroundColor Red
    exit 1
}

function Find-First([string]$base, [string]$filter) {
    if (-not (Test-Path $base)) { return $null }
    $hit = Get-ChildItem $base -Recurse -Filter $filter -ErrorAction SilentlyContinue |
        Select-Object -First 1
    if ($hit) { return $hit.FullName }
    return $null
}

$jdk = $null; $sdk = $null; $gradleBat = $null
$cfgPath = Join-Path $ToolchainRoot 'toolchain.json'
if (Test-Path $cfgPath) {
    $cfg = Get-Content $cfgPath -Raw | ConvertFrom-Json
    $jdk = $cfg.jdk
    $sdk = $cfg.sdk
    $gradleBat = $cfg.gradle
}
if (-not $jdk -or -not (Test-Path (Join-Path $jdk 'bin\java.exe'))) {
    $jdk = Join-Path $ToolchainRoot 'jdk'
}
if (-not $sdk -or -not (Test-Path $sdk)) {
    $sdk = Join-Path $ToolchainRoot 'sdk'
}
if (-not $gradleBat -or -not (Test-Path $gradleBat)) {
    $gradleBat = Find-First $ToolchainRoot 'gradle.bat'
}

if (-not (Test-Path (Join-Path $jdk 'bin\java.exe'))) { Fail '未找到 JDK，请先运行 scripts\setup.ps1' }
if (-not (Test-Path (Join-Path $sdk 'platforms\android-34\android.jar'))) { Fail '未找到 Android SDK 34，请先运行 scripts\setup.ps1' }
if (-not $gradleBat) { Fail '未找到 Gradle，请先运行 scripts\setup.ps1' }

$env:JAVA_HOME = $jdk
$env:ANDROID_HOME = $sdk
$env:ANDROID_SDK_ROOT = $sdk
$env:PATH = "$jdk\bin;$env:PATH"

Push-Location $ProjectRoot
try {
    $task = if ($Release) { 'assembleRelease' } else { 'assembleDebug' }
    Write-Host "Running: $gradleBat $task --no-daemon" -ForegroundColor Cyan
    & $gradleBat $task --no-daemon
    if ($LASTEXITCODE -ne 0) { Fail "Gradle 构建失败，退出码 $LASTEXITCODE" }
}
finally {
    Pop-Location
}

$apk = Get-ChildItem (Join-Path $ProjectRoot 'app\build\outputs\apk') -Recurse -Filter '*.apk' -ErrorAction SilentlyContinue |
    Sort-Object LastWriteTime -Descending | Select-Object -First 1
if (-not $apk) { Fail '构建结束但未找到 APK' }

$dist = Join-Path $ProjectRoot 'dist'
New-Item -ItemType Directory -Force -Path $dist | Out-Null
$suffix = if ($Release) { 'release' } else { 'debug' }
$out = Join-Path $dist "ANA-Pocket-Auto-$suffix.apk"
Copy-Item $apk.FullName $out -Force

Write-Host ''
Write-Host '========== BUILD OK ==========' -ForegroundColor Green
Write-Host "APK : $out"
Write-Host ("Size: {0:N0} bytes" -f (Get-Item $out).Length)
Write-Host "SHA256: $((Get-FileHash $out -Algorithm SHA256).Hash)"
Write-Host '==============================' -ForegroundColor Green
