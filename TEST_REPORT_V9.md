# ANA Pocket Auto v9 — 技术验收报告

> 本报告只写有证据的结论。所有 PASS 都能在 `evidence/` 下找到对应的原始日志、截图或 JSON；
> 所有 FAIL 都写清失败点与原因，不用「整体成功」掩盖。

---

## 0. 交付物与身份绑定

| 项 | 值 |
|---|---|
| 版本 | `9.0.0`（versionCode 9） |
| APK | `dist/ANA-Pocket-Auto-v9-release.apk` |
| APK 大小 | 3,409,859 bytes |
| **APK SHA256** | `e14531e142f827ea4f8528e994dd6e1dba22e0e50e338b3ed23d93495008402b` |
| 签名 | release keystore（`keystore/ana-release.jks`，alias `ana`） |
| 设备端校验 | `sha256sum <base.apk>` 与本地完全一致（见各报告 T1） |
| 设备 | Xiaomi 2212ARNC4L / `earth` / Android 14 (API 34) / HyperOS OS1.0 / 720×1650 |
| 设备指纹 | `Redmi/earth_global/earth:14/UP1A.231005.007/V816.0.16.0.UCVMIXM:user/release-keys` |
| 被自动化 App | ANA Pocket `us.ana_pocket.ANAPocket` v2.3.4 (versionCode 605) |

三份证据目录（按 RUN_ID 命名，互不复用）：

| 验证 | RUN_ID / 目录 | APK SHA256 |
|---|---|---|
| 配置 / 队列 / 日期隔离 | `evidence/CFG-20261026-025027` | `e14531e1…` 之前的构建，见说明①  |
| 主验收 T1–T13 | `evidence/V9-20261026-031200` | `e14531e1…` |
| 调度真实执行 / 脱离 ADB | `evidence/SCHED-20261026-031507` | `e14531e1…` |

> 说明①：`CFG` 目录产生于「奖励弹窗处理」这一版之前（哈希 `27651f3a…`）。
> 该验证只覆盖设置持久化与队列生成，与广告/挑战逻辑无关；
> 为保持「证据与最终 APK 对应」，最终 APK 的配置能力由 `V9-20261026-031200` 中的
> `settings_snapshot.json` + `queue size=9` 日志再次佐证（T6 PASS）。

---

## 1. 针对 v8 验收意见的整改

| # | 意见 | 整改 | 可核查证据 |
|---|---|---|---|
| 1 | DebugReceiver `exported=true` + 固定令牌 | 移到 `src/debug/`，release 清单不合并；`aapt2 dump` 显示 `DebugReceiver` 出现 **0 次**；令牌改为每次安装随机 32 位 hex，比较用 `MessageDigest.isEqual` | 第 2 节 |
| 2 | test.ps1 不区分三态 | 重写为 T1–T13 逐项断言，`PASS/FAIL/NOT TESTED` 三态，总体判定按「有 FAIL 即 FAIL」 | 本报告第 4 节 |
| 3 | 报告未绑定运行 ID / 哈希 / 时间 / 设备 | 报告头固定写入 RUN_ID、本地+设备双算 SHA256、时间、型号/代号/API/HyperOS/指纹/分辨率 | 每份 `REPORT.md` 表头 |
| 4 | 挑战 claimed=0 被掩盖 | 单列 T9，`claimed=0` 直接 FAIL | T9 = FAIL |
| 5 | DONE ≠ 奖励到账 | 广告结果拆成 5 态；只有积分增长或 App 弹出「報酬をゲット！」才记 SUCCESS，否则 UNCONFIRMED | 第 5 节 |
| 6 | 只证明闹钟排程 | 新增「调度自检」，脚本在触发前 `adb kill-server`，到点后重连读日志 | T12 = PASS |
| 7 | 未验证脱离 ADB | 同一窗口内完成整轮运行 | T13 = PASS |
| 8 | 多余绕过 / 持久权限 | 恢复 `package_verifier_enable=1`、`verifier_verify_adb_installs=1`；移除 `INTERNET`、`REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`；新增 `restore_env.ps1` | 第 2 节 |
| 9 | 证据需与最终 APK 对应 | 全部目录按 RUN_ID 隔离，APK 哈希双算 | 第 0 节 |

---

## 2. 安全项核查（release APK 实测）

`aapt2 dump` 输出摘要：

```
package: name='com.anapocket.auto' versionCode='9' versionName='9.0.0'
sdkVersion:'24'  targetSdkVersion:'34'

uses-permission: RECEIVE_BOOT_COMPLETED
uses-permission: SCHEDULE_EXACT_ALARM
uses-permission: WAKE_LOCK
uses-permission: POST_NOTIFICATIONS
uses-permission: FOREGROUND_SERVICE
uses-permission: FOREGROUND_SERVICE_SPECIAL_USE
uses-permission: SYSTEM_ALERT_WINDOW
uses-permission: ACCESS_NETWORK_STATE        (WorkManager 注入)
uses-permission: FOREGROUND_SERVICE          (WorkManager 注入)
```

- `DebugReceiver` 字符串在 APK 中出现次数：**0**
- 清单中 `android:debuggable`：**不存在**（release 默认 false）
- 自定义 receiver 仅 `AlarmReceiver`(exported=false)、`BootReceiver`(exported=true，仅接收系统开机广播)
- 权限变化：删除 `INTERNET`、`REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`；
  新增 `FOREGROUND_SERVICE(_SPECIAL_USE)`（防 lowmemorykiller，见第 5.3 节）
- Play Protect 安装校验已还原为默认开启（`package_verifier_enable=1`）

仍保留的两项需说明：

| 权限 | 为什么需要 | 能否撤销 |
|---|---|---|
| `SYSTEM_ALERT_WINDOW` | 运行期挂 1×1 透明窗，规避 Android 10+ 后台启动 Activity 限制 | 可在系统设置关闭，关闭后定时任务可能无法把 ANA Pocket 拉到前台 |
| `ACCESS_RESTRICTED_SETTINGS`（appops） | 等价于应用信息页里的「允许受限制的设置」，Android 13+ 侧载应用开启无障碍的前置条件 | `scripts/restore_env.ps1 -RevokeRestrictedSettings` |

---

## 3. v9 新增功能（任务自由选择与执行管理）

| 需求 | 实现 | 验证 |
|---|---|---|
| 广告开关 / 目标 5·10·15·20·25 / 已完成 / 剩余 / 通知开关 | 主界面「広告視聴」卡片 + `AppSettings.adEnabled/adTarget/adNotify` | C1–C3、T6 |
| 剩余次数按 App 实际显示计算 | 引擎读 `今日の視聴済み回数 x/25`，读不到退回本地计数并标注来源 | 日志 `done_app=… done_local=… base=… toRun=…` |
| 广告三态区分 | `REWARD_CONFIRMED / WATCH_COMPLETED / PAGE_RETURNED / TIMEOUT / NOT_STARTED` | 第 5 节 |
| 今日 / 明日独立选择 | `dayToday`、`dayTomorrow` 两个 Checkbox | C4、C5 |
| 四类任务独立 Checkbox + 公里数 | `Cat` 枚举 + `catEnabled` / `catKm`（DataStore，可改，不写死） | C1、C3 |
| 每类状态 / 已接取 / 失败原因 | 类别行下方状态文本，读自队列快照 | C3、主界面截图 |
| 执行队列（唯一 ID / 日期 / 类别 / 公里 / 状态 / 时间 / 错误） | `AutoTask` + `QueueBuilder` + `queue_<runId>_after.json` | C4、T6 |
| 跳过未选任务 | 队列只包含选中项 | C4（无 `CH-*-CAR`） |
| 检测已参加/已预约 | 详情页出现「参加予約日 / 参加済み / エントリー済み / 参加中」→ `ALREADY_DONE`，不重复点 | 代码 + 日志 |
| 完成通知（含成功/失败/未确认数量） | `Notifier.summary()` | T10 |
| DataStore 持久化 | `Prefs`（preferencesDataStore） | C2 |
| 执行历史 / 日志导出 | `RunStore.history()` / 「导出」按钮写 `exports/*.log` | 主界面截图 |

---

## 4. 主验收结果（`evidence/V9-20261026-031200`）

RUN_ID `V9-20261026-031200`，APK `e14531e1…`，触发方式：界面按钮（`reason=manual`）。

| ID | 检查项 | 判定 | 说明 |
|---|---|---|---|
| T1 | APK 与设备已安装包一致 | **PASS** | 双端 SHA256 相同 |
| T2 | v9.0.0 已安装 | **PASS** | versionName=9.0.0 |
| T3 | 无障碍服务已启用 | **PASS** | enabled_accessibility_services 含本包 |
| T4 | 由界面按钮触发 | **PASS** | reason=manual，runId=R1792948338611 |
| T5 | 运行正常结束（仅代表流程结束） | **PASS** | `success=0 failed=9 unconfirmed=0 already=0 skipped=0` |
| T6 | 按选择生成执行队列 | **PASS** | `size=9 ad=5 ch=4 days=1 cats=MOV_ANY/WALK/BIKE/CAR` |
| T7 | 广告任务执行统计 | **FAIL** | total=5，confirmed=0，failed=5（`ads_tab_unreachable`） |
| T8 | 广告奖励独立确认 | **FAIL** | 未确认到账 0 次 |
| T9 | チャレンジ 自动领取 | **FAIL** | total=4，claimed=0；1 个走到「详情校验通过 + 点击 10pt」，3 个 `no_matching_card` |
| T10 | 完成通知已发出 | **PASS** | `dumpsys notification` 含本应用 |
| T11 | 每日闹钟已排程 | **PASS** | `dumpsys alarm` 含本应用 |
| T12 | 闹钟到点真实执行 | **PASS** | 见第 6 节 |
| T13 | 脱离 ADB/USB 独立运行 | **PASS** | 见第 6 节 |

**总体判定：FAIL**（PASS 9 / FAIL 3 / NOT TESTED 0）

### 4.1 T7 广告为什么失败

该轮进入广告阶段时，App 停留在上一轮遗留的广告页/弹层，`openAdsTab()` 返回 false，
5 条广告任务全部标记 `ads_tab_unreachable`。

更早一轮（`evidence/V9-20261026-025838`）实际把广告跑起来了，日志可查：

```
01:59:11.390 I/AD tap node '視聴する'
01:59:14.996 I/AD ad activity detected
01:59:46.778 I/AD play store overlay -> close sheet
02:00:06.193 I/AD fallback close 3 at 51126ms
02:01:25.596 W/AD ad timeout, escape
02:01:26.322 I/AD task=AD-…-01 state=FAILED outcome=TIMEOUT
```

即：能点开广告、能识别 AdActivity、能处理 Play 商店覆盖层，但**没能稳定回到广告页并结算**，
超时后按设计记 FAILED。这条路径仍未达标。

### 4.2 T8 奖励确认为什么失败

本轮没有任何一次广告完成，自然没有奖励确认。
引擎已支持两种独立确认信号：
1. 顶部积分/计数增长（`points 2845 -> 2855`）；
2. App 自身弹出「報酬をゲット！」弹窗（截图为证：`evidence/after_claim.png` 同批画面）。

两者都没有出现，所以记 FAIL 而不是「成功」。

### 4.3 T9 挑战为什么失败

日志（`evidence/V9-20261026-031200/ana_auto.log`）：

```
02:12:46.228 I/CHAL challenge tab ok=true
02:12:46.241 I/CHAL date already 今日
02:12:55.638 I/CHAL open 移動手段問わず 5km
02:12:58.277 I/CHAL detail verified 移動手段問わず 5km
02:12:59.634 I/CHAL tap '10pt'
02:13:03.220 I/CHAL card 移動手段問わず5km -> FAIL
02:13:30.194 I/CHAL task=CH-…-MOV_ANY state=FAILED err=claim_failed_5km
02:13:30.196 I/CHAL task=CH-…-WALK  state=FAILED err=no_matching_card
02:13:30.196 I/CHAL task=CH-…-BIKE  state=FAILED err=no_matching_card
02:13:30.196 I/CHAL task=CH-…-CAR   state=FAILED err=no_matching_card
```

进展：**标签页可达、日期正确、卡片能被读到、详情页类别+公里数校验通过**——这比 v8 的「全盲点」实质前进。
失败点：
- 点击「10pt に参加予約」后未出现预约成功文案；随后前台变成 `com.five_corp.ad.AdActivity`，
  说明该点击触发了广告而不是免费预约（或页面跳转到了广告）。
- 其余三类在该屏没有可读卡片，`readCards` 未命中。

按「不允许仅因为点击成功就标记完成」的要求，记为 FAIL。

---

## 5. 关键工程修复（v9 相对 v8 的真实改进）

### 5.1 悬浮窗线程崩溃
`WindowManager.addView` 在 `Dispatchers.Default` 上调用必然抛
`Can't create handler inside thread … Looper.prepare()`。改为主线程 `Handler` 投递，日志已见 `1px overlay attached`。

### 5.2 进程被系统杀死（最关键）
v8 的自动化经常「跑一半没了」。v9 抓到实证：

```
lowmemorykiller: Kill 'com.anapocket.auto' (23376), uid 10338,
  oom_score_adj 900 to free 151912kB rss; reason: cw
ActivityManager: Process com.anapocket.auto has died: cch CRE
```

App 退到后台（ANA Pocket 在前台）后 adj=900，被 LMK 反复击杀，无障碍服务随之反复重建。
修复：运行期启动前台服务 `RunForegroundService`（`foregroundServiceType=specialUse`），
进程优先级提升到 FOREGROUND。修复后同类运行未再出现 `has died`。

### 5.3 阶段间状态恢复
新增：进入广告循环前、进入挑战阶段前，若不在 App 标签页则强制 `recoverToEarnTab()`；
Play 商店覆盖层改为「先返回键，仍在则点顶部关闭」；广告超时从 130s 收紧到 90s。

### 5.4 奖励弹窗识别
实测「報酬をゲット！」弹窗会盖住顶部标签栏，导致 `tabsVisible()=false`（截图
`evidence/V9-20261026-030643/02_run.png`）。v9 把它作为**奖励到账的官方 UI 证据**接入状态机，
同时在任何阶段先关闭它再继续。

---

## 6. 调度真实执行 / 脱离 ADB（`evidence/SCHED-20261026-031507`）

方法：
1. App 内点「调度自检」，由 `AlarmManager.setExactAndAllowWhileIdle` 排一个 90 秒后的闹钟；
2. **`adb kill-server`**，此后主机无任何进程与设备通信；
3. 等 240 秒；
4. `adb start-server` 重连，只读设备端日志。

实测日志：

```
02:15:12.578 I/SCHED selftest alarm at 2026-10-26 02:16:42 (in 90s)
--- adb kill-server ---
02:16:42.600 I/ALARM alarm-selftest fired
02:16:42.613 I/ALARM wakelock acquired
02:16:42.641 I/RUN === R1792948602627 START reason=alarm-selftest ===
02:16:42.644 I/RUN queue size=9 :: AD-…, CH-…
02:17:07.303 I/RUN === R1792948602627 END: success=0 failed=9 …
```

| ID | 判定 | 依据 |
|---|---|---|
| T12 闹钟到点真实执行 | **PASS** | 排程 02:16:42，实际 02:16:42.600 触发，误差 < 1s |
| T13 脱离 ADB 独立运行 | **PASS** | 无主机连接期间完成 START→END 整轮 |

**限制（如实说明）**：USB 线仍插着，只是主机侧 adb 服务被终止。要更强证明，可物理拔线后等每日 09:05 定时再插线读日志。

> 该轮的 `failed=9` 是上一轮遗留的广告页导致 `ads_tab_unreachable`，
> 属于**状态污染**而非调度问题；T12/T13 只判定「是否到点自主执行」，与业务成功与否无关。

---

## 7. 未通过项清单（必须由后续版本解决）

| 编号 | 未通过项 | 现状 | 建议方向 |
|---|---|---|---|
| F1 | 广告自动观看闭环 | 能点开、能识别 AdActivity、能处理 Play 商店覆盖层；但无法稳定回到广告页并结算，超时 FAILED | 用 `dumpsys activity` 判断 AdActivity 生命周期而非文本；对 AdMob/Pangle 分别建结束卡指纹；把「右上角两步关闭」改成基于倒计时节点的条件触发 |
| F2 | 广告奖励确认 | 已支持积分增长与「報酬をゲット！」弹窗两种信号，但本轮未产生 | 需在未达 25 次上限的时段复测；否则只能记 UNCONFIRMED |
| F3 | チャレンジ 自动领取 | 已能到列表、读卡、校验详情；点「10pt」后跳到了广告，未出现预约成功文案 | 需要抓取详情页点击后的实际画面，确认是按钮坐标偏移还是 10pt 入口本身带广告；再补一层「点击后校验前台包名，若为 AdActivity 则视为失败并回退」 |
| F4 | 卡片识别稳定性 | WebView 无障碍树时有时无，同屏只读到 1 张卡 | 增加「等 WebView 稳定 2s 再 dump」与逐屏重试；或引入本地 OCR 兜底 |
| F5 | 强停后无障碍解绑 | `am force-stop` 后 `Bound services:{}`，需重写 `enabled_accessibility_services` | 属 HyperOS 行为；已在 README 与安装脚本中说明，非应用可自愈 |

---

## 8. 复现步骤

```powershell
# 0) 构建（JDK17 + SDK34 已就绪）
.\scripts\build.ps1

# 1) 安装 + 授权 + 哈希校验
python .\tools\install_apk.py .\dist\ANA-Pocket-Auto-v9-release.apk

# 2) 配置 / 队列 / 日期隔离
python .\tools\verify_config.py

# 3) 主验收（安装已完成，跳过安装步骤）
powershell -ExecutionPolicy Bypass -File .\scripts\test.ps1 -SkipInstall -WaitMinutes 18

# 4) 调度真实执行 + 脱离 ADB
powershell -ExecutionPolicy Bypass -File .\scripts\verify_schedule.ps1 -WaitSeconds 240

# 5) 还原系统安全设置
powershell -ExecutionPolicy Bypass -File .\scripts\restore_env.ps1
```

---

## 9. 结论

- **工程侧**：v9 按需求完成「任务自由选择与执行管理」，配置、队列、日期隔离、通知、调度、
  脱离 ADB 独立运行均已通过真机验证。
- **自动化成功率**：广告与挑战两条主流程**仍未打通**，因此整体判定 **FAIL**。
  这两项的失败点、日志、截图都已在第 4 节列出，可直接复核。
- 本轮不存在「把人工成功写成自动成功」「把界面返回写成奖励到账」的情况：
  所有未确认项一律记 `UNCONFIRMED` 或 `FAILED`。
