<#
.SYNOPSIS
    验证「系统闹钟到点真实执行」(T12) 与「脱离 ADB 独立运行」(T13)。

.DESCRIPTION
    做法：
      1. 在 App 界面点「调度自检」，由 App 用 AlarmManager 排一个 90 秒后的精确闹钟；
      2. 立刻 `adb kill-server`，此后主机上没有任何进程能与设备通信；
      3. 等待闹钟到点 + 观察窗口；
      4. `adb start-server` 重新连接，只读取设备端日志，判断闹钟是否真的触发了运行。

    诚实说明：USB 线仍插着，但主机侧 adb 服务已终止，期间没有任何主机指令下发。
    这是在不物理拔线的前提下能做到的最强证明；报告中会明确标注该限制。

.EXAMPLE
    powershell -ExecutionPolicy Bypass -File .\scripts\verify_schedule.ps1
#>
[CmdletBinding()]
param(
    [string]$ProjectRoot = (Split-Path -Parent $PSScriptRoot),
    [string]$Adb,
    [int]$WaitSeconds = 240,
    [string]$OutDir
)

$ErrorActionPreference = 'Continue'
$Pkg = 'com.anapocket.auto'
$LogPath = '/sdcard/Android/data/com.anapocket.auto/files/logs/ana_auto.log'

function Find-Adb {
    param([string]$Hint)
    $c = @()
    if ($Hint) { $c += $Hint }
    $c += @(
        (Join-Path $env:USERPROFILE 'android-tools\platform-tools\adb.exe'),
        (Join-Path $env:USERPROFILE 'android-build\sdk\platform-tools\adb.exe'),
        'adb.exe'
    )
    foreach ($x in $c) {
        if ($x -eq 'adb.exe') {
            $cmd = Get-Command adb.exe -ErrorAction SilentlyContinue
            if ($cmd) { return $cmd.Source }
        } elseif (Test-Path $x) { return $x }
    }
    return $null
}

$script:adb = Find-Adb -Hint $Adb
if (-not $script:adb) { Write-Host 'ERROR: adb not found' -ForegroundColor Red; exit 1 }

function Sh {
    param([string]$Cmd)
    $out = & $script:adb shell $Cmd 2>&1
    return (($out | ForEach-Object { "$_" }) -join "`n").Trim()
}

$stamp = 'SCHED-' + (Get-Date -Format 'yyyyMMdd-HHmmss')
if (-not $OutDir) { $OutDir = Join-Path $ProjectRoot "evidence\$stamp" }
New-Item -ItemType Directory -Force -Path $OutDir | Out-Null

$lines = New-Object System.Collections.Generic.List[string]
function Add-Line([string]$s) { Write-Host $s; $lines.Add($s) }

function Get-Ui {
    & $script:adb shell uiautomator dump /sdcard/ana_ui.xml 2>&1 | Out-Null
    $p = Join-Path $OutDir 'ui_dump.xml'
    & $script:adb pull /sdcard/ana_ui.xml $p 2>&1 | Out-Null
    return $p
}

function Find-Node([string]$xmlPath, [string]$text) {
    if (-not (Test-Path $xmlPath)) { return $null }
    try { [xml]$x = Get-Content $xmlPath -Encoding UTF8 } catch { return $null }
    foreach ($n in $x.SelectNodes('//node')) {
        if ("$($n.text)" -eq $text -or "$($n.'content-desc')" -eq $text) {
            if ("$($n.bounds)" -match '\[(\d+),(\d+)\]\[(\d+),(\d+)\]') {
                return [pscustomobject]@{
                    X = [int]((([int]$Matches[1]) + ([int]$Matches[3])) / 2)
                    Y = [int]((([int]$Matches[2]) + ([int]$Matches[4])) / 2)
                }
            }
        }
    }
    return $null
}

function Tap-Text([string]$text, [int]$maxScroll = 6) {
    for ($i = 0; $i -le $maxScroll; $i++) {
        $ui = Get-Ui
        $n = Find-Node $ui $text
        if ($n) { & $script:adb shell input tap $n.X $n.Y 2>&1 | Out-Null; return $true }
        & $script:adb shell input swipe 360 1300 360 700 280 2>&1 | Out-Null
        Start-Sleep -Milliseconds 700
    }
    return $false
}

Add-Line '# 调度真实性 / 独立运行验证'
Add-Line ''
Add-Line "| 项 | 值 |"
Add-Line "|---|---|"
Add-Line "| 验证 ID | ``$stamp`` |"
Add-Line "| 执行时间 | $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss zzz') |"

$serial = ((& $script:adb devices) | Where-Object { $_ -match '\tdevice$' } | Select-Object -First 1)
if (-not $serial) {
    Add-Line ''
    Add-Line '> FAIL — 无设备连接'
    $lines | Set-Content (Join-Path $OutDir 'REPORT.md') -Encoding UTF8
    exit 2
}

$baseLines = 0
$wc = Sh "wc -l < $LogPath"
if ($wc -match '^\s*(\d+)') { $baseLines = [int]$Matches[1] }
Add-Line "| 日志基线行数 | $baseLines |"

# 确保前台在 App
Sh "am start -n $Pkg/.MainActivity" | Out-Null
Start-Sleep -Seconds 2

$tapped = Tap-Text '调度自检（90 秒后由系统闹钟执行）'
if (-not $tapped) { $tapped = Tap-Text '调度自检' }
Add-Line "| 点击调度自检 | $tapped |"

$logNow = (& $script:adb shell "cat $LogPath" 2>&1) -join "`n"
$schedAt = ''
if ($logNow -match 'selftest alarm at (\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2})') { $schedAt = $Matches[1] }
Add-Line "| 闹钟排程时间 | $schedAt |"

if (-not $tapped -or -not $schedAt) {
    Add-Line ''
    Add-Line '> FAIL — 未能排程自检闹钟（按钮未点击成功或日志无排程记录）'
    $lines | Set-Content (Join-Path $OutDir 'REPORT.md') -Encoding UTF8
    exit 3
}

Add-Line ''
Add-Line '## 操作：终止主机 adb 服务'
& $script:adb kill-server 2>&1 | Out-Null
$killAt = Get-Date
Add-Line "- 时间：$($killAt.ToString('yyyy-MM-dd HH:mm:ss'))"
Add-Line '- 此后无任何主机进程与设备通信。'

# 等待闹钟 + 观察窗口
$sleep = $WaitSeconds
Add-Line "- 等待 $sleep 秒..."
Start-Sleep -Seconds $sleep

# 重新连接
& $script:adb start-server 2>&1 | Out-Null
Start-Sleep -Seconds 4
$dev = (& $script:adb devices) | Where-Object { $_ -match '\tdevice$' } | Select-Object -First 1
Add-Line ''
Add-Line '## 重新连接设备'
Add-Line "- adb devices：$(if ($dev) { 'OK' } else { '未连接' })"

if (-not $dev) {
    Add-Line ''
    Add-Line '> FAIL — 重新连接失败，无法读取日志'
    $lines | Set-Content (Join-Path $OutDir 'REPORT.md') -Encoding UTF8
    exit 4
}

& $script:adb shell screencap -p /sdcard/sched_shot.png 2>&1 | Out-Null
& $script:adb pull /sdcard/sched_shot.png (Join-Path $OutDir 'after_alarm.png') 2>&1 | Out-Null

$all = (& $script:adb shell "cat $LogPath" 2>&1) -join "`n"
$all | Set-Content (Join-Path $OutDir 'ana_auto.log') -Encoding UTF8
$arr = @($all -split "`n" | ForEach-Object { $_.TrimEnd("`r") })
$new = @($arr | Select-Object -Skip $baseLines)
$txt = ($new -join "`n")

$fired = $txt -match 'alarm-selftest fired'
$mRun = [regex]::Match($txt, '=== (R\d+) START reason=alarm-selftest ===')
$runId = ''
if ($mRun.Success) { $runId = $mRun.Groups[1].Value }
$mEnd = $null
if ($runId) { $mEnd = [regex]::Match($txt, "=== $runId END: (.+?) ===") }

Add-Line ''
Add-Line '## 判定'
Add-Line ''
Add-Line '| ID | 检查项 | 判定 | 说明 |'
Add-Line '|---|---|---|---|'

$t12 = 'FAIL'
$t12note = '未发现 alarm-selftest 触发记录'
if ($fired -and $runId) {
    $t12 = 'PASS'
    $t12note = "闹钟到点触发运行 $runId"
} elseif ($fired -and -not $runId) {
    $t12 = 'FAIL'
    $t12note = '闹钟已触发，但未能启动运行（服务未连接）'
}
Add-Line "| T12 | 闹钟到点真实执行 | **$t12** | $t12note |"

$t13 = 'FAIL'
$t13note = '未在无 ADB 窗口内产生由闹钟驱动的运行'
if ($runId -and $mEnd -and $mEnd.Success) {
    $t13 = 'PASS'
    $t13note = "无主机连接期间完成整轮运行并结束：$($mEnd.Groups[1].Value)"
} elseif ($runId) {
    $t13 = 'PASS'
    $t13note = "无主机连接期间由闹钟启动运行 $runId（尚未结束，已证明可自主启动）"
}
Add-Line "| T13 | 脱离 ADB 独立运行 | **$t13** | $t13note |"

Add-Line ''
Add-Line '## 限制说明'
Add-Line ''
Add-Line '- USB 线仍连接，但主机 adb 服务在窗口期内已终止，未下发任何指令。'
Add-Line '- 未通过任何广播 / 手动点击触发；唯一触发源是系统 AlarmManager。'
Add-Line '- 若要更强的证明，可物理拔线后等待每日定时，再插线读取日志。'

Add-Line ''
Add-Line '## 关键日志'
Add-Line ''
Add-Line '```'
foreach ($l in @($new | Where-Object { $_ -match 'SCHED|ALARM|RUN' } | Select-Object -Last 40)) { Add-Line $l }
Add-Line '```'

$lines | Set-Content (Join-Path $OutDir 'REPORT.md') -Encoding UTF8
Write-Host ''
Write-Host "T12 = $t12 ; T13 = $t13" -ForegroundColor $(if ($t12 -eq 'PASS' -and $t13 -eq 'PASS') { 'Green' } else { 'Yellow' })
Write-Host "OUT: $OutDir"
