<#
.SYNOPSIS
    通过 ADB 安装 APK 到已连接的真机。

.EXAMPLE
    powershell -ExecutionPolicy Bypass -File .\scripts\install.ps1
    powershell -ExecutionPolicy Bypass -File .\scripts\install.ps1 -Apk .\dist\ANA-Pocket-Auto-debug.apk
#>
[CmdletBinding()]
param(
    [string]$ProjectRoot = (Split-Path -Parent $PSScriptRoot),
    [string]$Apk,
    [string]$Adb
)

$ErrorActionPreference = 'Stop'

function Fail([string]$m) {
    Write-Host "ERROR: $m" -ForegroundColor Red
    exit 1
}

function Find-Adb {
    param([string]$Hint)
    $candidates = @()
    if ($Hint) { $candidates += $Hint }
    $candidates += @(
        (Join-Path $env:USERPROFILE 'android-tools\platform-tools\adb.exe'),
        (Join-Path $env:USERPROFILE 'android-build\sdk\platform-tools\adb.exe'),
        'adb.exe'
    )
    foreach ($c in $candidates) {
        if ($c -eq 'adb.exe') {
            $cmd = Get-Command adb.exe -ErrorAction SilentlyContinue
            if ($cmd) { return $cmd.Source }
        }
        elseif (Test-Path $c) { return $c }
    }
    return $null
}

$adb = Find-Adb -Hint $Adb
if (-not $adb) { Fail '未找到 adb.exe。请安装 Android platform-tools，或用 -Adb 指定路径。' }

if (-not $Apk) {
    $Apk = Join-Path $ProjectRoot 'dist\ANA-Pocket-Auto-debug.apk'
}
if (-not (Test-Path $Apk)) { Fail "APK 不存在：$Apk。请先运行 scripts\build.ps1" }

Write-Host "ADB: $adb"
$devices = (& $adb devices) | Where-Object { $_ -match '\tdevice$' }
if (-not $devices) { Fail '未检测到已授权设备。请连接手机并允许 USB 调试。' }
Write-Host "设备: $($devices -join ', ')"

Write-Host "安装: $Apk"
& $adb install -r $Apk
if ($LASTEXITCODE -ne 0) { Fail "安装失败，退出码 $LASTEXITCODE" }

$pkgPath = (& $adb shell pm path com.anapocket.auto) -join ''
Write-Host ''
Write-Host '========== INSTALL OK ==========' -ForegroundColor Green
Write-Host $pkgPath
Write-Host '================================' -ForegroundColor Green
