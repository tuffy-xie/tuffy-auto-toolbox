# R39：每日广告25次与四类挑战接取/Boost

业务日期和计划时间以手机系统时间为来源，按 Asia/Tokyo 归日；不使用 Windows 时钟校正手机。

- 每日广告任务：固定25次启动上限，独立持久化账本及本机09:05计划保留。
- 用户新授权的四类挑战入口：今日徒步、自行车、移动手段不问、车各选1个；通常每类接取广告1个 + 5次Boost广告。只核对官方接取/Boost回执，不自动完成真实移动或领取积分。
- 与旧版执行器分开：R30/R32仅复用卡片解析、列表导航、目标详情校验；旧的挑战接取/Boost执行路径仍停用。
- 观看前同步保存记录。超时保留UNCONFIRMED；普通开始可继续当前仍可观察到的同一次广告，不新增序号。无可观察在途广告时停止核对，不能猜成功或自动重发。
- 无关闭控件的实际广告画布有界单击唤出，随后重新识别真实关闭节点；点击、返回、等待均不作为奖励回执。
- 官方所示“达成后的奖励”是完成移动后可获得的额度，不能当作已到账积分。

最新真实验证和未完成项见 TEST_RESULTS.md、R39_TEST_REPORT.md；源代码实现、单元测试与真机结果分别记录。

---
以下为历史记录；冲突处以上述R39说明及最新用户指令为准。

# R36：每日固定25次广告任务

当前仅开发每日广告主线，挑战接取/Boost执行暂停。正式APK固定25次上限，没有次数选择器。

- 当前设计与基线：[DEVELOPMENT_STATE.md](DEVELOPMENT_STATE.md)
- 广告模块复用审查：[AD_MODULE_AUDIT.md](AD_MODULE_AUDIT.md)
- 最新验证与未完成项：[TEST_RESULTS.md](TEST_RESULTS.md)
- 后续顺序：[NEXT_ACTION.md](NEXT_ACTION.md)

正式记录保留启动预留、完成确认和奖励回执三个独立事实。进程中断、未知回执或损坏记录均阻止自动重新提交。旧版记录保留启动次数，不能清数据来绕过当日计数。

受控测试Activity只在androidTest测试APK中；它没有真实广告或积分，不能作为ANA真实奖励证据。

---
以下是原工程README，仅供历史参考；与本轮优先级冲突处以以上说明为准。

# ANA Pocket Auto v9

Android 无障碍自动化工具：在 ANA Pocket（`us.ana_pocket.ANAPocket`）上按用户选择执行
「広告視聴」与「チャレンジ」每日任务，并把每条任务的结果落成可核查的证据。

- 目标环境：Xiaomi 2212ARNC4L / Android 14 (API 34) / HyperOS 1.0 / 720×1650
- 版本：`9.0.0`（versionCode 9），release 签名
- 语言/工具链：Kotlin 1.9.24 / AGP 8.5.2 / Gradle 8.9 / JDK 17 / compileSdk 34 / minSdk 24

---

## 1. 与 v8 的差异（针对验收意见逐条）

| 验收意见 | v9 处理 |
|---|---|
| 1. DebugReceiver `exported=true` + 固定令牌 | 移出主清单，只存在于 `src/debug/`；release APK 反编译检查 `DebugReceiver` 出现次数 = 0；令牌改为每次安装随机 32 位十六进制，比较用 `MessageDigest.isEqual` |
| 2. `test.ps1` 不区分 PASS/FAIL/NOT TESTED | 重写为逐项三态断言（T1–T13），安装成功/日志 END 只是独立条目 |
| 3. 报告未绑定运行 ID / 哈希 / 时间 / 设备 | 报告头固定写入 RUN_ID、本地 + 设备端双算 SHA256、时间、型号/代号/API/HyperOS/指纹/分辨率 |
| 4. 挑战 claimed=0 被掩盖 | 挑战结果单列 T9，`claimed=0` 直接 FAIL；不再用人工结果冒充 |
| 5. DONE ≠ 奖励到账 | 广告结果拆成 `REWARD_CONFIRMED / WATCH_COMPLETED / PAGE_RETURNED / TIMEOUT / NOT_STARTED`；只有积分或计数增长才记 `SUCCESS`，否则 `UNCONFIRMED` |
| 6. 只证明闹钟排程 | 新增「调度自检」：90 秒后由系统闹钟触发，脚本在触发前 `adb kill-server`，到点后重连读日志判定 T12 |
| 7. 未验证脱离 ADB | 同上窗口内完成，判定 T13（限制：USB 线未拔，但主机侧无进程通信，报告中如实标注） |
| 8. 移除不必要的绕过/持久权限 | 恢复 Play Protect 校验（`package_verifier_enable=1`）；移除 `INTERNET`、`REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`；新增 `restore_env.ps1` 一键还原 |
| 9. 源码/日志/截图需与最终 APK 对应 | 所有证据目录以 RUN_ID 命名，APK 哈希在设备端二次计算并比对 |

## 2. v9 新功能

### 2.1 广告任务
- 开关、当日累计目标（5/10/15/20/25）、当前已完成次数、剩余次数、完成通知开关。
- 剩余次数按 **App 显示的 `今日の視聴済み回数 x/25`** 计算；读不到时退回本地计数，并在日志标注来源，
  不会把「固定 25 次」当成每次都要跑 25 次。
- 状态区分：页面返回 / 观看完成 / 奖励确认（见上表第 5 条）。

### 2.2 チャレンジ 自由选择
- 日期：今日 / 明日（参加予約），可只选其一或同时选。
- 四类：移動手段問わず / 徒歩 / 自転車 / 車，各自 Checkbox + 最大公里数（默认 40/5/5/40，可改，不写死在代码里）。
- 每类显示：当前状态、已接取数量、失败原因。
- 只处理「当日实际存在 + 已选类别 + ≤ 设定公里数」的卡片。

### 2.3 执行队列
- `AutoTask`：唯一 ID、日期、类别、公里数、状态、执行时间、错误信息。
- 广告任务数 = 目标 − 当日已完成；挑战任务 = 选中日期 × 选中类别。
- 已参加/已预约的卡片在详情页被识别后记 `ALREADY_DONE`，不重复点击。

### 2.4 通知
广告完成 / 今日挑战完成 / 明日预约完成 / 全部完成 / 部分失败需人工处理，内容含成功、失败、未确认数量。

### 2.5 持久化
- 设置：DataStore（Preferences），JSON 结构，带默认值兜底。
- 运行状态：`RunStore`（SharedPreferences）保存最近运行、执行历史（20 条）、当日广告计数、队列快照。

## 3. 目录

```
ANA_Pocket_Auto/
├─ app/src/main/java/com/anapocket/auto/
│  ├─ Settings.kt            设置模型 + DataStore（Cat 枚举含默认公里数）
│  ├─ TaskQueue.kt           AutoTask / TaskState / QueueBuilder
│  ├─ AutomationEngine.kt    引擎：队列消费、广告状态机、挑战强校验领取
│  ├─ AutoAccessibilityService.kt  常驻无障碍服务 + 运行入口
│  ├─ RunForegroundService.kt      运行期前台服务（防 lowmemorykiller）
│  ├─ OverlayKeeper.kt       1px 透明窗（后台启动 Activity 豁免）
│  ├─ Scheduler.kt           每日闹钟 / 自检闹钟 / 重试闹钟 + WorkManager 兜底
│  ├─ AlarmReceiver.kt       闹钟入口（唤醒锁 + 挂起 + 重试）
│  ├─ Notifier.kt            通知渠道与汇总通知
│  ├─ RunStore.kt            运行历史 / 广告计数 / 队列快照
│  ├─ Evidence.kt            设置/队列/结果 JSON 落盘
│  ├─ Logger.kt / Ui.kt / Sel.kt / Outcome.kt / MainActivity.kt
│  └─ (src/debug) DebugReceiver.kt  仅 debug 变体，随机令牌
├─ scripts/
│  ├─ build.ps1 / install.ps1 / setup.ps1 / collect-logs.ps1
│  ├─ test.ps1               主验收（T1–T13，三态）
│  ├─ verify_schedule.ps1    调度真实执行 + 脱离 ADB（T12/T13）
│  └─ restore_env.ps1        还原 Play Protect 等系统设置
├─ tools/
│  ├─ install_apk.py         HyperOS 安装 + 授权 + 哈希校验
│  └─ verify_config.py       配置保存/恢复/队列/日期隔离（C1–C7）
├─ dist/ANA-Pocket-Auto-v9-release.apk
├─ evidence/                 每次运行的原始证据（按 RUN_ID 分目录）
├─ TEST_REPORT_V9.md         技术验收报告
└─ README.md
```

## 4. 构建

```powershell
$env:JAVA_HOME='C:\Users\xiejiatao\android-build\jdk'
$env:ANDROID_HOME='C:\Users\xiejiatao\android-build\sdk'
.\gradlew assembleRelease    # 或 scripts\build.ps1
```

签名：`keystore/ana-release.jks`（alias `ana`，口令在 `app/build.gradle`，仅个人使用）。

## 5. 安装与授权（HyperOS）

`adb install` 在本机返回 `INSTALL_FAILED_USER_RESTRICTED`，两条路径都可以：

```powershell
# A. 直接安装（需要已打开「USB 安装」与「USB 调试(安全设置)」）
adb install -r dist\ANA-Pocket-Auto-v9-release.apk

# B. 自动走系统安装器（推荐，tools/install_apk.py 已封装 push + content URI + 自动点按）
python tools\install_apk.py dist\ANA-Pocket-Auto-v9-release.apk
```

`tools/install_apk.py` 会顺带完成：`ACCESS_RESTRICTED_SETTINGS`、`SYSTEM_ALERT_WINDOW`、
`SCHEDULE_EXACT_ALARM`、`POST_NOTIFICATIONS`、写入 `enabled_accessibility_services`，
并比对设备端 `sha256sum` 与本地哈希。

手动路径：设置 → 无障碍 → 已下载的服务 → ANA 自动助手 → 开启；
设置 → 应用管理 → ANA 自动助手 → 显示悬浮窗 → 允许。

## 6. 验收

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\test.ps1 -SkipInstall -WaitMinutes 20
powershell -ExecutionPolicy Bypass -File .\scripts\verify_schedule.ps1
python tools\verify_config.py
```

报告落在 `evidence/<RUN_ID>/REPORT.md`，同时有 `results.json`。

## 7. 已知限制（如实记录）

1. **HyperOS 会在 `am force-stop` 后解绑无障碍服务**（`dumpsys accessibility` → `Bound services:{}`），
   需重新写入 `enabled_accessibility_services` 才能恢复。日常使用不会触发，但强停/清理后台后需要重新开启。
2. **チャレンジ 列表是 WebView**，无障碍树时有时无。引擎只在读到卡片标题时才领取；
   读不到就记 `no_matching_card` 并 FAIL，不做盲点。可选的「位置猜测 + 详情页强校验」默认关闭。
3. **广告奖励无法保证确认**：达到当日 25 次上限时，广告仍可能播放但积分不再增长，
   此时任务记为 `UNCONFIRMED` 而不是 `SUCCESS`。
4. **USB 独立性验证的边界**：验证窗口内主机 adb 服务已终止，但 USB 线仍连接。
5. 本工具不修改 ANA Pocket 数据，只模拟点击；请自行确认符合服务条款。
