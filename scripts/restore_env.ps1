<#
.SYNOPSIS
    还原测试期间修改过的系统安全配置，并列出仍保留的授权项。

.DESCRIPTION
    本项目在 v8 阶段曾关闭 Play Protect 的安装校验（package_verifier_enable=0 /
    verifier_verify_adb_installs=0）。v9 已不再需要，默认还原为 1。

    仍会保留的两项授权，均为「用户可在系统设置中自行开关」的普通权限：
      * SYSTEM_ALERT_WINDOW   —— 运行期间挂 1px 透明窗，用于绕过后台启动限制
      * ACCESS_RESTRICTED_SETTINGS —— 等价于应用信息页里的「允许受限制的设置」
    如不再使用本应用，可用 -RevokeRestrictedSettings 一并撤销。

.EXAMPLE
    powershell -ExecutionPolicy Bypass -File .\scripts\restore_env.ps1
    powershell -ExecutionPolicy Bypass -File .\scripts\restore_env.ps1 -RevokeRestrictedSettings
#>
[CmdletBinding()]
param(
    [string]$Adb,
    [switch]$RevokeRestrictedSettings
)

$ErrorActionPreference = 'Continue'
$Pkg = 'com.anapocket.auto'

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

$adb = Find-Adb -Hint $Adb
if (-not $adb) { Write-Host 'ERROR: adb not found' -ForegroundColor Red; exit 1 }

function Sh {
    param([string]$Cmd)
    $out = & $adb shell $Cmd 2>&1
    return (($out | ForEach-Object { "$_" }) -join "`n").Trim()
}

Write-Host '== 还原 Play Protect 安装校验 ==' -ForegroundColor Cyan
Sh 'settings put global package_verifier_enable 1' | Out-Null
Sh 'settings put global verifier_verify_adb_installs 1' | Out-Null
Write-Host ("package_verifier_enable      = " + (Sh 'settings get global package_verifier_enable'))
Write-Host ("verifier_verify_adb_installs = " + (Sh 'settings get global verifier_verify_adb_installs'))

Write-Host ''
Write-Host '== 当前保留的授权 ==' -ForegroundColor Cyan
Write-Host ("enabled_accessibility_services = " + (Sh 'settings get secure enabled_accessibility_services'))
Write-Host ("SYSTEM_ALERT_WINDOW            = " + (Sh "appops get $Pkg SYSTEM_ALERT_WINDOW"))
Write-Host ("ACCESS_RESTRICTED_SETTINGS     = " + (Sh "appops get $Pkg ACCESS_RESTRICTED_SETTINGS"))

if ($RevokeRestrictedSettings) {
    Write-Host ''
    Write-Host '== 撤销 ACCESS_RESTRICTED_SETTINGS（撤销后无障碍可能被系统关闭）==' -ForegroundColor Yellow
    Sh "appops set $Pkg ACCESS_RESTRICTED_SETTINGS default" | Out-Null
    Write-Host ("ACCESS_RESTRICTED_SETTINGS = " + (Sh "appops get $Pkg ACCESS_RESTRICTED_SETTINGS"))
}

Write-Host ''
Write-Host '完成。' -ForegroundColor Green
