<#
.SYNOPSIS
    v9 真机验收脚本（严格三态：PASS / FAIL / NOT TESTED）。

.DESCRIPTION
    设计原则（针对 v8 验收意见 2/3/4/5/6）：
      * 每个断言独立判定，安装成功 / 日志出现 END 只作为独立条目，绝不等于功能通过；
      * 报告绑定 RUN_ID、APK SHA256（本地 + 设备已安装包双算）、执行时间、设备版本；
      * 只解析「本次运行 ID」对应的日志行，日志基线按行号截断，避免旧日志误判；
      * 广告「界面返回」与「奖励确认」分开判定；
      * 挑战 claimed=0 直接判 FAIL，不用人工结果冒充自动化结果。

.EXAMPLE
    powershell -ExecutionPolicy Bypass -File .\scripts\test.ps1
    powershell -ExecutionPolicy Bypass -File .\scripts\test.ps1 -SkipInstall -WaitMinutes 12
#>
[CmdletBinding()]
param(
    [string]$ProjectRoot = (Split-Path -Parent $PSScriptRoot),
    [string]$Apk,
    [string]$Adb,
    [int]$WaitMinutes = 12,
    [string]$OutDir,
    [switch]$SkipInstall
)

$ErrorActionPreference = 'Continue'

$Pkg = 'com.anapocket.auto'
$Target = 'us.ana_pocket.ANAPocket'
$Svc = 'com.anapocket.auto/com.anapocket.auto.AutoAccessibilityService'
$LogPath = '/sdcard/Android/data/com.anapocket.auto/files/logs/ana_auto.log'
$EvPath = '/sdcard/Android/data/com.anapocket.auto/files/evidence'

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

function ShNoTrim {
    param([string]$Cmd)
    $out = & $script:adb shell $Cmd 2>&1
    return (($out | ForEach-Object { "$_" }) -join "`n")
}

if (-not $Apk) { $Apk = Join-Path $ProjectRoot 'dist\ANA-Pocket-Auto-v9-release.apk' }
if (-not (Test-Path $Apk)) { Write-Host "ERROR: APK not found: $Apk" -ForegroundColor Red; exit 1 }

$runId = 'V9-' + (Get-Date -Format 'yyyyMMdd-HHmmss')
if (-not $OutDir) { $OutDir = Join-Path $ProjectRoot "evidence\$runId" }
New-Item -ItemType Directory -Force -Path $OutDir | Out-Null
$script:out = $OutDir

$lines = New-Object System.Collections.Generic.List[string]
$results = New-Object System.Collections.Generic.List[object]

function Add-Line([string]$s) {
    Write-Host $s
    $lines.Add($s)
}

function Add-Result([string]$id, [string]$name, [string]$status, [string]$evidence, [string]$note) {
    $results.Add([pscustomobject]@{ Id = $id; Name = $name; Status = $status; Evidence = $evidence; Note = $note })
    $color = 'Yellow'
    if ($status -eq 'PASS') { $color = 'Green' }
    if ($status -eq 'FAIL') { $color = 'Red' }
    Write-Host ("  [{0}] {1} {2} - {3}" -f $status, $id, $name, $note) -ForegroundColor $color
}

function Save-Shot([string]$name) {
    $remote = '/sdcard/ana_shot.png'
    & $script:adb shell screencap -p $remote | Out-Null
    & $script:adb pull $remote (Join-Path $script:out $name) 2>&1 | Out-Null
}

function Get-Ui {
    & $script:adb shell uiautomator dump /sdcard/ana_ui.xml 2>&1 | Out-Null
    $p = Join-Path $script:out 'ui_dump.xml'
    & $script:adb pull /sdcard/ana_ui.xml $p 2>&1 | Out-Null
    return $p
}

function Find-Node([string]$xmlPath, [string]$text) {
    if (-not (Test-Path $xmlPath)) { return $null }
    try { [xml]$x = Get-Content $xmlPath -Encoding UTF8 } catch { return $null }
    foreach ($n in $x.SelectNodes('//node')) {
        $t = "$($n.text)"
        $d = "$($n.'content-desc')"
        if ($t -eq $text -or $d -eq $text) {
            if ("$($n.bounds)" -match '\[(\d+),(\d+)\]\[(\d+),(\d+)\]') {
                return [pscustomobject]@{
                    X       = [int]((([int]$Matches[1]) + ([int]$Matches[3])) / 2)
                    Y       = [int]((([int]$Matches[2]) + ([int]$Matches[4])) / 2)
                    Bounds  = "$($n.bounds)"
                    Checked = "$($n.checked)"
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
        if ($n) {
            Start-Sleep -Milliseconds 700
            $ui2 = Get-Ui
            $n2 = Find-Node $ui2 $text
            if ($n2) { $n = $n2 }
            & $script:adb shell input tap $n.X $n.Y 2>&1 | Out-Null
            return $true
        }
        & $script:adb shell input swipe 700 1300 700 700 600 2>&1 | Out-Null
        Start-Sleep -Milliseconds 900
    }
    return $false
}

# ---------------------------------------------------------------- 0. header
Add-Line '# ANA Pocket Auto v9 — 真机技术验收报告'
Add-Line ''
Add-Line '| 项 | 值 |'
Add-Line '|---|---|'
Add-Line "| RUN_ID | ``$runId`` |"
Add-Line "| 执行时间 | $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss zzz') |"

# ---------------------------------------------------------------- 1. device
$devices = (& $script:adb devices) | Where-Object { $_ -match '\tdevice$' }
if (-not $devices) {
    Add-Line '| 设备 | **未检测到已授权设备** |'
    Add-Line ''
    Add-Line '> 结论：FAIL — 无法连接设备，所有功能项 NOT TESTED。'
    $lines | Set-Content -Path (Join-Path $OutDir 'REPORT.md') -Encoding UTF8
    exit 2
}
$serial = ($devices[0] -split '\t')[0]
$model = Sh 'getprop ro.product.model'
$device = Sh 'getprop ro.product.device'
$rel = Sh 'getprop ro.build.version.release'
$sdk = Sh 'getprop ro.build.version.sdk'
$hyper = Sh 'getprop ro.mi.os.version.name'
$finger = Sh 'getprop ro.build.fingerprint'
$wm = Sh 'wm size'
$anaVer = (Sh "dumpsys package $Target | grep -E 'versionName|versionCode'") -replace '\s+', ' '

Add-Line "| 设备序列号 | $serial |"
Add-Line "| 型号 / 代号 | $model / $device |"
Add-Line "| Android | $rel (API $sdk) |"
Add-Line "| HyperOS | $hyper |"
Add-Line "| 指纹 | ``$finger`` |"
Add-Line "| 分辨率 | $wm |"
Add-Line "| ANA Pocket | $anaVer |"

# ---------------------------------------------------------------- 2. apk
$apkHash = (Get-FileHash -Path $Apk -Algorithm SHA256).Hash.ToLower()
$apkSize = (Get-Item $Apk).Length
$apkName = Split-Path $Apk -Leaf
Add-Line "| APK 文件 | ``$apkName`` |"
Add-Line "| APK 大小 | $apkSize bytes |"
Add-Line "| APK SHA256（本地） | ``$apkHash`` |"

# ---------------------------------------------------------------- 3. install
Add-Line ''
Add-Line '## 1. 安装'
Add-Line ''
$installMode = 'skipped'
if (-not $SkipInstall) {
    $installOut = (& $script:adb install -r $Apk) -join "`n"
    if ($installOut -match 'Success') {
        $installMode = 'adb install -r'
    } else {
        Write-Host 'adb install 被系统拒绝，改用系统安装器 UI 路径...' -ForegroundColor Yellow
        $remoteName = "ana_v9_$([int](Get-Date -UFormat %s)).apk"
        $remote = "/sdcard/Download/$remoteName"
        & $script:adb push $Apk $remote 2>&1 | Out-Null
        Start-Sleep -Seconds 1
        $q = Sh "content query --uri content://media/external/file --projection _id:_display_name --where ""_display_name='$remoteName'"""
        $mid = $null
        if ($q -match '_id=(\d+)') { $mid = $Matches[1] }
        if ($mid) {
            Sh "am start -a android.intent.action.VIEW -d content://media/external/file/$mid -t application/vnd.android.package-archive --grant-read-uri-permission" | Out-Null
            Start-Sleep -Seconds 3
            for ($i = 0; $i -lt 12; $i++) {
                $ui = Get-Ui
                $tapped2 = $false
                foreach ($label in @('更新', '安装', '继续安装', '扫描应用', '仍然安装', '允许')) {
                    $n = Find-Node $ui $label
                    if ($n) {
                        & $script:adb shell input tap $n.X $n.Y 2>&1 | Out-Null
                        Start-Sleep -Seconds 4
                        $tapped2 = $true
                        break
                    }
                }
                if (-not $tapped2) {
                    $focus = Sh 'dumpsys window | grep mCurrentFocus'
                    if ($focus -notmatch 'packageinstaller|permissioncontroller') { break }
                    Start-Sleep -Seconds 2
                }
            }
            $installMode = 'system installer UI'
        }
    }
}
$pkgPath = Sh "pm path $Pkg"
$installed = $pkgPath -match 'package:'
$lastUpd = Sh "dumpsys package $Pkg | grep lastUpdateTime"
Add-Line "- 安装方式：$installMode"
Add-Line "- pm path：``$pkgPath``"
Add-Line "- ``$lastUpd``"

$devHash = ''
if ($installed) {
    $base = ($pkgPath -split "`n")[0] -replace '^package:', ''
    $base = $base.Trim()
    $devHash = Sh "sha256sum '$base'"
    if ($devHash -match '([0-9a-fA-F]{64})') { $devHash = $Matches[1].ToLower() } else { $devHash = '' }
}
Add-Line "- 设备端已安装包 SHA256：``$devHash``"

if (-not $installed) {
    Add-Result 'T1' 'APK 安装' 'FAIL' 'pm path' 'pm path 为空，未安装成功'
} elseif ($devHash -and $devHash -ne $apkHash) {
    Add-Result 'T1' 'APK 与设备已安装包一致' 'FAIL' "local=$apkHash device=$devHash" '哈希不一致，证据与交付物不匹配'
} elseif ($devHash) {
    Add-Result 'T1' 'APK 与设备已安装包一致' 'PASS' "sha256=$apkHash" '本地与设备端 SHA256 完全一致'
} else {
    Add-Result 'T1' 'APK 与设备已安装包一致' 'NOT TESTED' 'sha256sum 不可用' '设备端无法计算哈希'
}

$verName = Sh "dumpsys package $Pkg | grep versionName"
if ($verName -match '9\.0\.0') {
    Add-Result 'T2' '应用版本 v9.0.0 已安装' 'PASS' 'dumpsys package' "$verName"
} else {
    Add-Result 'T2' '应用版本 v9.0.0 已安装' 'FAIL' 'dumpsys package' "$verName"
}

# ---------------------------------------------------------------- 4. permissions
Add-Line ''
Add-Line '## 2. 权限'
Add-Line ''
$curA11y = Sh 'settings get secure enabled_accessibility_services'
if ($curA11y -notmatch [regex]::Escape($Pkg)) {
    Sh "settings put secure enabled_accessibility_services $Svc" | Out-Null
    Sh 'settings put secure accessibility_enabled 1' | Out-Null
    Start-Sleep -Seconds 1
    $curA11y = Sh 'settings get secure enabled_accessibility_services'
}
if ($curA11y -notmatch [regex]::Escape($Pkg)) {
    # Android 13+ 侧载应用的「受限制的设置」，等价于用户在应用信息页手动允许
    Sh "appops set $Pkg ACCESS_RESTRICTED_SETTINGS allow" | Out-Null
    Sh "settings put secure enabled_accessibility_services $Svc" | Out-Null
    Sh 'settings put secure accessibility_enabled 1' | Out-Null
    Start-Sleep -Seconds 1
    $curA11y = Sh 'settings get secure enabled_accessibility_services'
}
Sh "appops set $Pkg SYSTEM_ALERT_WINDOW allow" | Out-Null
Sh "appops set $Pkg SCHEDULE_EXACT_ALARM allow" | Out-Null
$curOverlay = Sh "appops get $Pkg SYSTEM_ALERT_WINDOW"
Add-Line "- enabled_accessibility_services = ``$curA11y``"
Add-Line "- SYSTEM_ALERT_WINDOW = ``$curOverlay``"

# ---------------------------------------------------------------- 5. launch app
Add-Line ''
Add-Line '## 3. 启动与触发'
Add-Line ''
Sh "am start -n $Pkg/.MainActivity" | Out-Null
Start-Sleep -Seconds 3
Save-Shot '01_app_main.png'
Add-Line '- 已保存 01_app_main.png（主界面）'

& $script:adb pull "$EvPath/settings_snapshot.json" (Join-Path $OutDir 'settings_snapshot.json') 2>&1 | Out-Null
$stPath = Join-Path $OutDir 'settings_snapshot.json'
$settings = $null
if (Test-Path $stPath) {
    try { $settings = Get-Content $stPath -Raw -Encoding UTF8 | ConvertFrom-Json } catch { $settings = $null }
}
if ($settings) {
    Add-Line "- 设置快照：adEnabled=$($settings.adEnabled) adTarget=$($settings.adTarget) dayToday=$($settings.dayToday) dayTomorrow=$($settings.dayTomorrow)"
    Add-Line "- catEnabled=$($settings.catEnabled | ConvertTo-Json -Compress)"
    Add-Line "- catKm=$($settings.catKm | ConvertTo-Json -Compress)"
} else {
    Add-Line '- 设置快照：未取到'
}

$baseLines = 0
$wc = Sh "wc -l < $LogPath"
if ($wc -match '^\s*(\d+)') { $baseLines = [int]$Matches[1] }
Add-Line "- 日志基线行数：$baseLines"

$tapped = Tap-Text '立即执行已选任务'
Add-Line "- 点击「立即执行已选任务」：$tapped"
if (-not $tapped) { Save-Shot '02_trigger_failed.png' }

# ---------------------------------------------------------------- 6. observe
Add-Line ''
Add-Line '## 4. 执行过程'
Add-Line ''
$deadline = (Get-Date).AddMinutes($WaitMinutes)
$shot = 2
$runStarted = ''
$finished = $false
while ((Get-Date) -lt $deadline) {
    Start-Sleep -Seconds 15
    $all = ShNoTrim "cat $LogPath"
    $arr = @($all -split "`n" | ForEach-Object { $_.TrimEnd("`r") })
    $new = @($arr | Select-Object -Skip $baseLines)
    $txt = ($new -join "`n")
    if (-not $runStarted -and $txt -match '=== (R\d+) START reason=(\S+) ===') {
        $runStarted = $Matches[1]
        Add-Line "- $(Get-Date -Format 'HH:mm:ss') 检测到本次运行：$runStarted"
    }
    if ($runStarted -and $txt -match "=== $runStarted END:") {
        $finished = $true
        Add-Line "- $(Get-Date -Format 'HH:mm:ss') 检测到本次运行结束标记"
        break
    }
    if (($shot % 2) -eq 0) { Save-Shot ("{0:D2}_run.png" -f $shot) }
    $shot++
}

Save-Shot '90_final.png'

# ---------------------------------------------------------------- 7. collect
Add-Line ''
Add-Line '## 5. 证据采集'
Add-Line ''
ShNoTrim "cat $LogPath" | Set-Content -Path (Join-Path $OutDir 'ana_auto.log') -Encoding UTF8
& $script:adb logcat -d -s AnaAuto:V AndroidRuntime:E 2>&1 | Set-Content -Path (Join-Path $OutDir 'logcat.txt') -Encoding UTF8
ShNoTrim 'dumpsys notification --noredact' | Set-Content -Path (Join-Path $OutDir 'notifications.txt') -Encoding UTF8
ShNoTrim 'dumpsys alarm' | Set-Content -Path (Join-Path $OutDir 'alarms.txt') -Encoding UTF8
ShNoTrim "dumpsys package $Pkg" | Set-Content -Path (Join-Path $OutDir 'package.txt') -Encoding UTF8
New-Item -ItemType Directory -Force -Path (Join-Path $OutDir 'evidence_json') | Out-Null
& $script:adb pull "$EvPath/." (Join-Path $OutDir 'evidence_json') 2>&1 | Out-Null
Add-Line '- ana_auto.log / logcat.txt / notifications.txt / alarms.txt / package.txt / evidence_json/'

$logAll = Get-Content (Join-Path $OutDir 'ana_auto.log') -Encoding UTF8
$logNew = @($logAll | Select-Object -Skip $baseLines)
$logTxt = ($logNew -join "`n")

# ---------------------------------------------------------------- 8. asserts
Add-Line ''
Add-Line '## 6. 判定'

$svcOk = ($curA11y -match [regex]::Escape($Pkg))
Add-Result 'T3' '无障碍服务已启用' $(if ($svcOk) { 'PASS' } else { 'FAIL' }) 'settings get secure enabled_accessibility_services' "$curA11y"

$mStart = [regex]::Match($logTxt, '=== (R\d+) START reason=(\S+) ===')
$triggerReason = ''
if (-not $mStart.Success) {
    Add-Result 'T4' '由界面按钮触发本次运行' 'FAIL' 'ana_auto.log' '未发现本次 START（点击未生效或服务未运行）'
} else {
    $triggerReason = $mStart.Groups[2].Value
    $runStarted = $mStart.Groups[1].Value
    Add-Result 'T4' '由界面按钮触发本次运行' $(if ($triggerReason -eq 'manual') { 'PASS' } else { 'FAIL' }) 'ana_auto.log' "reason=$triggerReason runId=$runStarted"
}

$mEnd = $null
if ($runStarted) { $mEnd = [regex]::Match($logTxt, "=== $runStarted END: (.+?) ===") }
if ($mEnd -and $mEnd.Success) {
    Add-Result 'T5' '运行正常结束（仅代表流程结束）' 'PASS' 'ana_auto.log' $mEnd.Groups[1].Value
} else {
    Add-Result 'T5' '运行正常结束（仅代表流程结束）' 'FAIL' 'ana_auto.log' '超时未出现本次运行的 END 标记'
}

$mQ = [regex]::Match($logTxt, 'queue size=(\d+) :: (.+)')
$queueOk = 'NOT TESTED'
$queueNote = '未取到队列日志'
$queueIds = @()
if ($mQ.Success) {
    $queueSize = [int]$mQ.Groups[1].Value
    $queueIds = @($mQ.Groups[2].Value.Trim() -split ',')
    $queueNote = "size=$queueSize"
    if ($settings) {
        $enabledCats = @()
        foreach ($c in @('MOV_ANY', 'WALK', 'BIKE', 'CAR')) {
            if ($settings.catEnabled.$c) { $enabledCats += $c }
        }
        $expectDays = 0
        if ($settings.dayToday) { $expectDays++ }
        if ($settings.dayTomorrow) { $expectDays++ }

        $chIds = @($queueIds | Where-Object { $_ -like 'CH-*' })
        $byDate = @{}
        foreach ($id in $chIds) {
            if ($id -match '^CH-(\d{4}-\d{2}-\d{2})-(\w+)$') {
                $d = $Matches[1]; $c = $Matches[2]
                if (-not $byDate.ContainsKey($d)) { $byDate[$d] = @() }
                $byDate[$d] += $c
            }
        }
        $daysOk = ($byDate.Keys.Count -eq $expectDays)
        $catsOk = $true
        foreach ($k in $byDate.Keys) {
            $got = @($byDate[$k] | Sort-Object)
            $exp = @($enabledCats | Sort-Object)
            if (($got -join ',') -ne ($exp -join ',')) { $catsOk = $false }
        }
        $adIds = @($queueIds | Where-Object { $_ -like 'AD-*' })
        if ($daysOk -and $catsOk) {
            $queueOk = 'PASS'
            $queueNote = "size=$queueSize ad=$($adIds.Count) ch=$($chIds.Count) days=$($byDate.Keys.Count) cats=$($enabledCats -join '/')"
        } else {
            $queueOk = 'FAIL'
            $queueNote = "队列与选择不一致：days=$($byDate.Keys.Count)/expect$expectDays catsOk=$catsOk"
        }
    }
}
Add-Result 'T6' '按选择生成执行队列' $queueOk 'ana_auto.log queue size=...' $queueNote

$queueAfter = $null
if ($runStarted) {
    $queueAfter = Get-ChildItem (Join-Path $OutDir 'evidence_json') -Filter "queue_${runStarted}_after.json" -ErrorAction SilentlyContinue | Select-Object -First 1
}
$adTotal = 0; $adOk = 0; $adUnc = 0; $adFail = 0; $adSkip = 0
$chTotal = 0; $chOk = 0; $chFail = 0; $chAlready = 0
if ($queueAfter) {
    $qa = Get-Content $queueAfter.FullName -Raw -Encoding UTF8 | ConvertFrom-Json
    foreach ($t in $qa) {
        if ($t.id -like 'AD-*') {
            $adTotal++
            if ($t.state -eq 'SUCCESS') { $adOk++ }
            elseif ($t.state -eq 'UNCONFIRMED') { $adUnc++ }
            elseif ($t.state -eq 'FAILED') { $adFail++ }
            elseif ($t.state -eq 'SKIPPED' -or $t.state -eq 'PENDING') { $adSkip++ }
        } elseif ($t.id -like 'CH-*') {
            $chTotal++
            if ($t.state -eq 'SUCCESS') { $chOk++ }
            elseif ($t.state -eq 'FAILED') { $chFail++ }
            elseif ($t.state -eq 'ALREADY_DONE') { $chAlready++ }
        }
    }
}
if ($adTotal -eq 0) {
    Add-Result 'T7' '广告任务执行统计' 'NOT TESTED' 'queue_*_after.json' '本次队列无广告任务'
    Add-Result 'T8' '广告奖励独立确认' 'NOT TESTED' 'queue_*_after.json' '本次无广告任务'
} elseif ($adOk -gt 0) {
    Add-Result 'T7' '广告任务执行统计' 'PASS' 'queue_*_after.json' "total=$adTotal confirmed=$adOk unconfirmed=$adUnc failed=$adFail skipped=$adSkip"
    Add-Result 'T8' '广告奖励独立确认' 'PASS' 'queue_*_after.json' "确认到账 $adOk 次"
} else {
    Add-Result 'T7' '广告任务执行统计' 'FAIL' 'queue_*_after.json' "total=$adTotal confirmed=0 unconfirmed=$adUnc failed=$adFail skipped=$adSkip"
    Add-Result 'T8' '广告奖励独立确认' 'FAIL' 'queue_*_after.json' "未确认到账 $adUnc 次（仅界面返回，不计为发放）"
}

if ($chTotal -eq 0) {
    Add-Result 'T9' 'チャレンジ 自动领取' 'NOT TESTED' 'queue_*_after.json' '本次队列无挑战任务'
} elseif ($chOk -gt 0) {
    Add-Result 'T9' 'チャレンジ 自动领取' 'PASS' 'queue_*_after.json' "total=$chTotal claimed=$chOk failed=$chFail already=$chAlready"
} else {
    Add-Result 'T9' 'チャレンジ 自动领取' 'FAIL' 'queue_*_after.json' "total=$chTotal claimed=0 failed=$chFail already=$chAlready"
}

$notifTxt = ''
$nf = Join-Path $OutDir 'notifications.txt'
if (Test-Path $nf) { $notifTxt = Get-Content $nf -Raw -Encoding UTF8 }
$hasNotif = $notifTxt -match [regex]::Escape($Pkg)
Add-Result 'T10' '完成通知已发出' $(if ($hasNotif) { 'PASS' } else { 'FAIL' }) 'notifications.txt' $(if ($hasNotif) { '通知记录中包含本应用' } else { '未发现本应用通知' })

$alarmTxt = ''
$af = Join-Path $OutDir 'alarms.txt'
if (Test-Path $af) { $alarmTxt = Get-Content $af -Raw -Encoding UTF8 }
$hasAlarm = $alarmTxt -match [regex]::Escape($Pkg)
Add-Result 'T11' '每日闹钟已排程' $(if ($hasAlarm) { 'PASS' } else { 'FAIL' }) 'alarms.txt' $(if ($hasAlarm) { 'dumpsys alarm 中存在本应用闹钟' } else { '未发现本应用闹钟' })

Add-Result 'T12' '闹钟到点真实执行' 'NOT TESTED' 'scripts\verify_schedule.ps1' '由独立脚本验证，见其报告'
Add-Result 'T13' '脱离 ADB/USB 独立运行' 'NOT TESTED' 'scripts\verify_standalone.ps1' '由独立脚本验证，见其报告'

# ---------------------------------------------------------------- 9. verdict
$failCount = @($results | Where-Object { $_.Status -eq 'FAIL' }).Count
$ntCount = @($results | Where-Object { $_.Status -eq 'NOT TESTED' }).Count
$passCount = @($results | Where-Object { $_.Status -eq 'PASS' }).Count
$overall = 'PASS'
if ($failCount -gt 0) { $overall = 'FAIL' }
elseif ($ntCount -gt 0) { $overall = 'NOT TESTED' }

Add-Line ''
Add-Line '| ID | 检查项 | 判定 | 说明 | 证据 |'
Add-Line '|---|---|---|---|---|'
foreach ($r in $results) {
    Add-Line ("| {0} | {1} | **{2}** | {3} | {4} |" -f $r.Id, $r.Name, $r.Status, ($r.Note -replace '\|', '/'), $r.Evidence)
}
Add-Line ''
Add-Line "**总体判定：$overall**（PASS $passCount / FAIL $failCount / NOT TESTED $ntCount）"
Add-Line ''
Add-Line '> 说明：T2（安装成功）与 T5（日志出现 END）只证明「装上了、跑完了」，不代表任何功能验收通过。'
Add-Line '> 功能结论只看 T6–T9；调度真实性看 T12；独立性看 T13。'

Add-Line ''
Add-Line '## 7. 本次运行日志（尾部 60 行）'
Add-Line ''
Add-Line '```'
foreach ($l in @($logNew | Select-Object -Last 60)) { Add-Line $l }
Add-Line '```'

$lines | Set-Content -Path (Join-Path $OutDir 'REPORT.md') -Encoding UTF8
$results | ConvertTo-Json -Depth 4 | Set-Content -Path (Join-Path $OutDir 'results.json') -Encoding UTF8

Write-Host ''
$vc = 'Yellow'
if ($overall -eq 'PASS') { $vc = 'Green' }
if ($overall -eq 'FAIL') { $vc = 'Red' }
Write-Host "========== RESULT: $overall ==========" -ForegroundColor $vc
Write-Host "RUN_ID : $runId"
Write-Host "APK    : $apkHash"
Write-Host "OUT    : $OutDir"
Write-Host '======================================'
