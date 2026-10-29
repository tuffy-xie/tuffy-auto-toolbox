<#
.SYNOPSIS
    收集真机证据：设备信息、logcat、应用日志、UI 树、截图。

.EXAMPLE
    powershell -ExecutionPolicy Bypass -File .\scripts\collect-logs.ps1
    powershell -ExecutionPolicy Bypass -File .\scripts\collect-logs.ps1 -Shots 3
#>
[CmdletBinding()]
param(
    [string]$ProjectRoot = (Split-Path -Parent $PSScriptRoot),
    [string]$Adb,
    [string]$OutDir,
    [int]$Shots = 2
)

$ErrorActionPreference = 'Continue'

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
if (-not $adb) { Write-Host 'ERROR: 未找到 adb.exe' -ForegroundColor Red; exit 1 }

if (-not $OutDir) {
    $stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
    $OutDir = Join-Path $ProjectRoot "evidence\collect-$stamp"
}
New-Item -ItemType Directory -Force -Path $OutDir | Out-Null

$device = (& $adb devices) | Where-Object { $_ -match '\tdevice$' }
if (-not $device) { Write-Host 'ERROR: 未检测到已授权设备' -ForegroundColor Red; exit 1 }

Write-Host "ADB: $adb"
Write-Host "输出: $OutDir"

$info = @()
$info += "time=$(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')"
$info += "serial=$(($device[0] -split '\t')[0])"
$info += "model=$((& $adb shell getprop ro.product.model) -join '')"
$info += "device=$((& $adb shell getprop ro.product.device) -join '')"
$info += "android=$((& $adb shell getprop ro.build.version.release) -join '')"
$info += "api=$((& $adb shell getprop ro.build.version.sdk) -join '')"
$info += "hyperos=$((& $adb shell getprop ro.mi.os.version.name) -join '')"
$info += "size=$((& $adb shell wm size) -join '')"
$info += "ana_pocket=$((& $adb shell pm path us.ana_pocket.ANAPocket) -join '')"
$info += "auto_pkg=$((& $adb shell pm path com.anapocket.auto) -join '')"
$info += "a11y_services=$(((& $adb shell settings get secure enabled_accessibility_services) -join '').Trim())"
$info += "a11y_enabled=$(((& $adb shell settings get secure accessibility_enabled) -join '').Trim())"
$info | Set-Content -Path (Join-Path $OutDir 'device-info.txt') -Encoding UTF8

& $adb logcat -d -s AnaAuto:V AndroidRuntime:E > (Join-Path $OutDir 'logcat_AnaAuto.txt')
& $adb logcat -d > (Join-Path $OutDir 'logcat_full.txt')

& $adb shell "cat /sdcard/Android/data/com.anapocket.auto/files/logs/ana_auto.log" > (Join-Path $OutDir 'ana_auto.log')

& $adb shell "uiautomator dump /sdcard/ana_ui.xml" | Out-Null
& $adb pull /sdcard/ana_ui.xml (Join-Path $OutDir 'ui.xml') | Out-Null

for ($i = 1; $i -le $Shots; $i++) {
    $remote = '/sdcard/ana_collect_shot.png'
    & $adb shell screencap -p $remote | Out-Null
    & $adb pull $remote (Join-Path $OutDir ("shot_$i.png")) | Out-Null
    Start-Sleep -Seconds 1
}

Write-Host ''
Write-Host '========== COLLECTED ==========' -ForegroundColor Green
Get-ChildItem $OutDir | Select-Object Name, Length | Format-Table -AutoSize
Write-Host '===============================' -ForegroundColor Green
