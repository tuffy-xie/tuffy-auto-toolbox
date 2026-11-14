# Tuffy 的自动百宝箱

> 原名 **ANA Pocket Auto**。个人自用的 Android 自动化工具，用无障碍服务驱动三个日本 App 的每日任务。
> 当前正式版本：**22.0.12-R47 / versionCode 34**（包名 `com.anapocket.auto`）。

这个仓库只服务于一台手机、一个使用者，不做通用发布。文档里出现的 `R37`…`R47`、`H9`…`H12` 是真实的迭代编号，不是版本营销号；每一轮都对应一份带日期的测试/调查报告。

---

## 1. 它做什么

三个 App、三条独立流水线，共用一套无障碍执行引擎：

| App | 包名 | 自动化的内容 | 优先级 |
|---|---|---|---|
| **ANA Pocket** | `us.ana_pocket.ANAPocket` | 每日签到、出行挑战接取、挑战 Boost、每日 25 次广告 | **P0** |
| **楽天 PointClub** | `jp.co.rakuten.pointclub.android` | 每日积分任务、PICKUP、普通抽奖 | P1 |
| **いきなり！ステーキ** | `jp.co.pepperfs.android` | 每日飞镖等已验证的免费任务 | P1 |

三个 App 各有独立的开关、定时、账本和通知。**楽天和牛排不得影响 ANA**：ANA 一旦进入执行窗口，P1 的普通任务会被让出（见第 9 节）。

### 最近一次真实结果（2026-11-01，JST）

| 项 | 结果 |
|---|---|
| 挑战 | WALK 7 + BIKE 6 + CAR 6 + MOV_ANY 5 = **24 项接取，各 5/5 Boost** |
| 挑战总账 | 147 次启动 / 144 次官方回执 / 3 次 `FAILED` / 0 次 `UNKNOWN` |
| 每日广告 | 25/25（18:02 复核仍为 25，未新增观看） |
| 签到 | `UNCONFIRMED` —— 整日不能称全部成功 |
| 收尾 | 12:38:37 通知 → 12:38:42 回 Home → 12:38:45 锁屏确认 |

「24 项完成」指**接取成功且 Boost 满 5 次**，不是真实出行达成，也不是积分入账。

---

## 2. 运行环境

| 项 | 值 |
|---|---|
| 目标设备 | Redmi 12C |
| 系统 | Android 14 / HyperOS |
| 业务时区 | Asia/Tokyo（JST），业务日以手机本地日期为准 |
| minSdk / targetSdk / compileSdk | 24 / 34 / 34 |
| 构建 | JDK 17、Gradle 8.9、AGP 8.5.2、Kotlin 1.9.24 |

用户有两台手机。**这台 Redmi 只负责接取挑战、看广告和签到，不承担记录真实出行距离的职责**——所以「挑战已接取、Boost 已 5/5」不等于「真的走了 5km」，更不等于积分到账。

必要权限：无障碍服务、通知、精确闹钟、前台服务、悬浮窗、电池优化白名单。HyperOS 还需要允许「后台弹出界面」和「锁屏显示」，否则息屏唤醒会失败。

---

## 3. 工程结构

```
app/src/main/java/com/anapocket/auto/   主源码（97 个 Kotlin 文件）
  ├─ AutoAccessibilityService.kt        无障碍服务入口、页面识别
  ├─ AutomationEngine.kt                执行引擎（最大单体，约 109KB）
  ├─ DailyRoutine.kt                    当日任务编排：签到 → 挑战 → 广告
  ├─ Scheduler.kt / AlarmReceiver.kt    精确闹钟、唤醒、保护窗口
  ├─ Settings.kt / TravelSelection.kt   六类开关、公里数、选择审计
  ├─ ChallengeScanStore.kt              8 态扫描状态与审计
  ├─ ChallengeBoost.kt / NativeChallengeReceiptPolicy.kt   Boost 与原生回执核对
  ├─ DailyAdTask.kt                     每日 25 次广告
  ├─ CheckinController.kt               楽天 / 牛排独立签到
  ├─ AppTaskPolicy.kt                   P0/P1 所有权仲裁（UiOwnership）
  ├─ TelegramNotify.kt                  Telegram 推送（Token 存 AndroidKeyStore）
  └─ Notifier.kt / FaultStore.kt        本地通知与故障留证
app/src/test/                           71 个单元测试文件（504 项 / 72 套件）
app/src/androidTest/                    设备隔离检查（fixture，不碰真实账本）
app/src/debug/                          仅 debug 变体：DebugReceiver、自检探针
scripts/                                build / install / test / setup / verify_schedule
tools/                                  install_apk.py、verify_config.py
.github/workflows/android-build.yml     CI：仅编译与单元测试，不含签名私钥
```

`dist/`、`app/build/`、`.gradle/` 均为产物，不入库。

---

## 4. 首页与六种出行类别

首页只有三个动作，没有隐藏菜单：

- **开始今日任务** — 当天还没有任何记录时显示
- **继续未完成任务** — 当天已有 routine/挑战/广告/签到记录但没跑完时显示（即使 `finishedAt` 缺失也显示，这是 H12 修的）
- **重新扫描 / 查看结果** — 已结束或已 BLOCKED 时显示

六种出行类别（`Cat` 枚举，按首页顺序）：

| 枚举 | 日文原文 | 中文 | 默认公里 |
|---|---|---|---|
| `WALK` | 徒歩 | 徒步 | 5 |
| `BIKE` | 自転車 | 自行车 | 5 |
| `CAR` | 車 | 汽车 | 40 |
| `MOV_ANY` | 移動手段問わず | 移动不限 | 40 |
| `SHINKANSEN` | 新幹線 | 新干线 | 0（关） |
| `FLIGHT` | 飛行機 | 飞机 | 0（关） |

**选择方式**：点击类别即选中，**再次点击取消**。没选中的类别一律不执行，日志写 `DISABLED_BY_USER`，不发起任何新广告。这个「点一下选中、再点一下取消」是 R47 改的，之前是单选式。

执行顺序固定为 `WALK → BIKE → CAR → MOV_ANY → SHINKANSEN → FLIGHT`，一个类别跑完（全部接取 + Boost 满）才切下一个。类别栏在列表底部会折叠，所以切换前会先做**有限回顶**再找分类栏；这一步失败会明确记 `SCAN_FAILED`，不会假装切过去了。

---

## 5. 公里数设置与执行规则

- 普通四类范围 1–200 km；长途两类（新干线/飞机）范围 0–20000 km，**填 0 即视为关闭**。
- 一个类别「算启用」的条件是：**开关打开 且 公里数 > 0**（`TravelSelection.enabled`）。只开开关不填公里等于没开。
- 公里数只作为**筛选条件**：只接取官方列表里距离不超过该值的任务。它不会改变 App 里已存在的记录。
- 改公里数会让当天的旧扫描投影失效，需要重新扫描；已接取、已 Boost、已 UNKNOWN 的历史记录**不因改设置而清除**。
- 长途类默认关闭，避免误接高价任务。

当前手机上的真实配置是 `徒歩 ON 5 / 自転車 ON 5 / 車 ON 40 / 移動手段問わず ON 40 / 新幹線 OFF 0 / 飛行機 OFF 0`。文档里出现的 50/100 km 是 10 月 31 日及更早的历史值，**不要拿旧值覆盖手机**。

---

## 6. 每日挑战、Boost、25 次广告：三个独立状态

这是最容易误读的地方，三者互不代偿：

| 状态 | 判定依据 | 完成不等于 |
|---|---|---|
| **签到** | 官方签到回执 | 挑战可跳过 |
| **挑战接取** | 官方「チャレンジをはじめました！」原生回执 | 出行真实完成 |
| **Boost 5/5** | 每次 Boost 的独立回执，逐条累计 | 积分已入钱包 |
| **每日广告 25/25** | 官方计数页读到 25，且本机 25 次启动/25 次回执 | **挑战也全部完成** |

- `DAILY_AD_LIMIT = 25`，计数从持久化记录派生，避免双写漂移。
- **广告 25/25 只代表广告额度用完**。挑战是另一条流水线，广告满了照样可以、也必须继续接挑战。
- **Boost 5/5 是「本次观看额度用满」**，不是积分到账。官方中奖/获得画面只证明该次交互成功，钱包是否增加需要另外核对。
- 每次挑战接取和每次 Boost 都是独立记录，带原始 `runId / challengeId / index / startedAt`，不允许用新记录覆盖旧记录。

---

## 7. 今日任务：开始、继续、重新扫描

- **开始今日任务**：当天无记录时从签到开始，按「签到 → 挑战 → 每日广告」推进。
- **继续未完成任务**：读取当天已有账本，跳过已完成项，从断点续跑。续跑必须能对上原始身份（原始 `startedAt`、原始 `index`），对不上就不动。
- **重新扫描**：只重读官方的「参加中 → 進行中」列表，找回遗漏的挑战，**不重看已完成广告、不重新签到**。
- **重置扫描状态**：只清当天的扫描投影（派生缓存），保留提交记录、奖励记录、UNKNOWN、配置和其他 App 的账本。重置和重扫是两个不同动作，界面上分开。

扫描状态有 8 个独立取值（`ChallengeScanStore`），包括 `SCAN_FAILED`、`NO_ELIGIBLE_TASK`、`NOT_SCANNED` 等。**空结果不等于没有任务**：只有官方双列表都读完且确实为空，才允许写 `NO_ELIGIBLE_TASK`；切页失败一律 `SCAN_FAILED`，首页保留可重试入口。

只核对「参加中 → 進行中」里**任务日期等于今天**的移动任务。昨天判定中的任务（`PROCESSING`）要排除，历史待领奖也排除——这是用户明确澄清过的口径。

---

## 8. 楽天与牛排：独立开关、独立定时

- 各自有启用开关、时/分、独立账本、独立通知、独立 `AlarmManager` 与 `WorkManager` 身份。
- 默认都是**关闭**：楽天 09:15，牛排 09:30。要用就进各自入口单独打开。
- 每天到点由各自闹钟触发，与 ANA 的 00:03 完全无关。
- 两个模块的失败不会写进 ANA 的账本，反之亦然。
- 已验证的真实结果是「楽天三张任务卡達成、两次普通抽奖未中奖」「牛排飞镖官方 1 日元结果」。**钱包是否入账未另证**；楽天的 bonus 与牛排的 darts 目前仍是 `UNCONFIRMED`，**不重播**。

---

## 9. ANA 的 P0 优先级与抢占恢复

`UiOwnership` 是一把带租约的互斥锁，规则很硬：

- ANA 持有锁时，P1 **不能**获取。
- P1 持有锁、但 ANA 需要执行时，P1 的普通任务被**撤销并让出**（`revokeOrdinary`），然后 ANA 接管。
- 带「保护窗口」的操作（例如 ANA 23:58 的预唤醒）对非 ANA 一律拒绝。
- 租约到期自动失效，避免进程被杀后锁永远不释放。
- 每次获取/校验/释放都写日志，便于事后核对到底是谁抢了谁。

时间线：ANA 每日 **00:03** 执行，**23:58** 起进入保护窗口。楽天 09:15、牛排 09:30 与 ANA 不重叠；若用户改了时间导致重叠，ANA 优先。

---

## 10. 异常处理

| 异常 | 现在的行为 | 证据等级 |
|---|---|---|
| **Google / Fyber / Unity 广告黑屏或卡死** | 有界等待后超时，写 `UNCONFIRMED`，保留原身份，不重播；有限次数尝试正常系统「应用信息 → 结束运行」再重开 | 超时与留证：已实测；**自动结束/重开：未通过**（见第 15 节） |
| **LOW_MEMORY 进程回收** | 无障碍自动重连，同一条广告身份恢复并核对官方回执 | 单次自然回收恢复：已实测通过；**连续回收**：见第 15 节 |
| **ANA ANR（系统无响应）** | 留证（屏幕/窗口/logcat/meminfo），等系统或人工关闭，之后从继续入口恢复 | 留证已做；**内部根因 UNKNOWN** |
| **无障碍服务断开** | 服务重连后重新绑定，业务从断点继续；HyperOS 需要完整倒计时确认 | 已实测恢复 |
| **账号风险提示 / 验证码** | 立即暂停，保留记录，标记需人工处理；重启不会自动解除 | 策略已实现 |
| **官方「继续 / 再開する」弹窗** | 只在存在匹配的待恢复记录时才点；陌生 Continue 不点 | 已实测 |
| **最近任务卡片恢复** | 从最近任务精确找到 ANA 卡 → 官方再開 → 官方 NO_GAIN 后才开下一次 | `REAL_TASK_PASS_ONCE`（MOV 40km，无人工查卡） |

原则：**能安全停就停，不猜、不硬点、不清账**。停下来的代价是一天没跑完，乱点的代价是账本被污染且无法复原。

---

## 11. 安全规则：UNKNOWN / UNCONFIRMED 不重复广告

这是整个项目最不能被绕开的一条规则。

1. 一次观看没有拿到可信回执，就记为 `UNKNOWN` / `UNCONFIRMED`，**保留原始 `startedAt` 和 `index`**。
2. **绝不重播**这条未知观看。剩余次数 ≠ 允许重看。
3. 只有官方明确给出「无增益 / NO_GAIN」结论，才允许把该条转成 `FAILED`，然后才允许开下一次。
4. 迟到的接取回执要认领，必须同时满足：原始 `runId / index / startedAt / 标题 / 日期` 全部对上 + 原观看期间存在 `REWARD_CONFIRMED` 原生证据 + 当前面板稳定且唯一 pending。**匿名面板或缺失历史证据一律不认领。**
5. 每日广告、签到、挑战各有独立未知守卫；一个未知不会把整条流水线无条件卡死，但也不会被「跳过」掉。

这套规则在 H9–H12 里反复被真实故障检验过，包括「官方接取回执已经出现、但旧 SDK Activity 还挂在前面」这种被误判成广告超时的情况。

---

## 12. 通知与锁屏

- **Telegram**：每次每日广告或四类任务结束，推送本次结果、今日计数和停止原因。Token 用 AndroidKeyStore 加密保存在手机上，**不进日志、不进仓库、不进证据包**。配置方法见 `TELEGRAM_SETUP.md`。
- **本地通知**：完成、BLOCKED、需人工处理各有独立通知；锁屏自检结果单独一条。
- **锁屏**：任务结束并退出前台后，通过无障碍 `GLOBAL_ACTION_LOCK_SCREEN` 主动锁屏。**锁屏未被确认时会推送「自动锁屏未确认，请检查手机」**，不会假装成功。
- 手机设了密码锁屏时，到点能唤醒但不能解锁，需要本人操作——这是系统限制，不是 bug。

---

## 13. 构建、测试与升级

### 构建

```powershell
# 本机已装好 JDK17 + SDK34 + Gradle 8.9 时
powershell -ExecutionPolicy Bypass -File .\scripts\build.ps1            # debug
powershell -ExecutionPolicy Bypass -File .\scripts\build.ps1 -Release   # release
```

`scripts/build.ps1` 会读取 `%USERPROFILE%\android-build\toolchain.json`，找不到就在该目录下探测 JDK / SDK / Gradle。

手工构建：

```bash
export JAVA_HOME=<jdk17>
export ANDROID_HOME=<sdk with platforms/android-34>
gradle testDebugUnitTest        # 单元测试
gradle assembleDebug            # 开发包
gradle assembleRelease          # 正式包
```

### 签名

签名材料只放在本地，通过 `keystore.properties` 或环境变量提供：

```
TUFFY_KEYSTORE_FILE / TUFFY_STORE_PASSWORD / TUFFY_KEY_ALIAS / TUFFY_KEY_PASSWORD
```

`keystore.properties`、`keystore/`、`local.properties` 均在 `.gitignore` 里。**缺少私钥时 release 构建会直接失败，不会静默退回 debug 签名。**

正式证书 SHA256：`ab39397d23fca7803fa7cd3e33125874d010874d5641e6ffd044a2a3b55f484c`

### 升级

必须**同签名覆盖安装**，不能卸载：

1. 手机 `Files` 打开新 APK → 安装 → Play Protect 扫描放行；
2. 安装后核对 `base.apk` 的 SHA256 与交付一致；
3. 重新确认无障碍服务（HyperOS 需要走完整的 10 秒倒计时确认）；
4. 核对账本：`settings / checkinConfig / daily / challenges / login / P1` 六组升级前后应逐字节一致。

卸载会丢数据，且新装无法继承原有账本，**不要卸载**。

### 测试

- 单元测试：`gradle testDebugUnitTest` —— 当前 **504 项 / 72 套件**。
- 设备隔离检查：`app/src/androidTest` 的 fixture 用例，跑在**独立 preferences** 上，不读写真实账本。当前 10 个独立用例。
- `JVM_PASS` 只代表可注入的规则和状态机正确，**不代表真实 SDK 行为或奖励到账**。

---

## 14. 证据等级

文档里的每个结论都标了等级，请按字面理解：

| 标记 | 含义 |
|---|---|
| `UNIT_PASS` | 单元测试通过，规则层正确 |
| `DEVICE_PASS` | 真机界面/流程实测通过 |
| `REAL_TASK_PASS` | 真实业务任务完成并有官方回执 |
| `REAL_DEVICE_PASS_ONCE` | 真实环境发生过一次，未重复验证 |
| `NOT_TESTED` | 未测 |
| `NOT_PASSED` / `BLOCKED` | 测了没过 / 被阻塞 |
| `UNCONFIRMED` | 有动作但无官方回执，按未知处理 |

---

## 15. 已知限制与未完成的验收

诚实清单，别把下面这些当已完成：

1. **自动结束卡死的 ANA 并重开：未通过。** `AppInfo → 结束运行` 的自动流程在多次真实卡死中都没能走通，根因仍 `UNKNOWN`。真机上成功的几次都是**开发者手动**点的系统界面。不要把「有限自动重试」描述成「无人值守自动恢复」。
2. **连续 LOW_MEMORY：有未修缺陷。** 单次自然回收能恢复；但 11:16 与 11:21 连续两次回收会让排队中的恢复回调丢失（claim 已占位，2/3、3/3 额度被吃掉），原 3/3 保护没有放宽。这是已知限制。
3. **ANA ANR 的根因未定位。** 只证明了现象（`Application Not Responding`、可用内存约 420MB），OEM `lastanr` 没给内部栈，**不能断言是某个 SDK 内存泄漏**。
4. **11 月 1 日：所选挑战已全部完成，但整日不算全部成功。** WALK 7 + BIKE 6 + CAR 6 + MOV_ANY 5 = **24 项接取、各 5/5 Boost**；挑战累计 147 次启动 / 144 次官方回执 / 3 次 `FAILED` / 0 次 `UNKNOWN`。每日广告 25/25，18:02 重新打开官方入口复核仍是 25→25，未新增观看。**但原签到仍 `UNCONFIRMED`**，所以只能称「所选挑战完成」，不能称整日全部官方成功。
5. **签到仍 `UNCONFIRMED`。** 原始 `RTacf24d6…/1793458986170` 从未拿到回执，按规则不重播。
6. **通知 / 清理 / 锁屏有一次完整终态证据，但只有一次。** 12:38:37 结果通知 posted → 12:38:42 自动回 Home → 12:38:45 锁屏确认。未跨日重复验证。
7. **物理拔线整夜、全部广告 SDK 组合、楽天 bonus 与牛排 darts 的最终结果**均未验收。
8. **钱包积分到账从未被证明。** 所有「获得」「当選」画面只能证明交互成功。

---

## 16. 文档索引

| 文件 | 作用 |
|---|---|
| `CHANGELOG.md` | 按轮次记录改了什么、验证到哪一步 |
| `DEVELOPMENT_STATE.md` | 当前开发状态（最新在最上） |
| `NEXT_ACTION.md` | 下一步该做什么、不要做什么 |
| `TEST_RESULTS.md` | 测试结果与证据等级 |
| `BUG_BACKLOG.md` | 缺陷清单：症状 / 根因 / 修改 / 验证 / 剩余风险 |
| `R47_HOTFIX12_INVESTIGATION_REPORT.md` | H12 故障调查与真机时间线 |
| `R47_HOTFIX12_TEST_REPORT.md` | H12 测试报告与验收矩阵 |
| `R36…R47_*_REPORT.md` | 各历史轮次原始报告，按日期保留，不修改 |
| `USER_PREFERENCES.md` | 用户长期偏好与授权边界 |
| `TELEGRAM_SETUP.md` | Telegram 配置步骤 |

---

## 17. 隐私

本仓库**不包含**：签名私钥与密码、Telegram Token、真实账号信息、手机原始诊断日志、用户账本数据、私有截图、`local.properties`、Gradle 缓存。

真机证据（屏幕、窗口树、logcat、账本导出）单独存放在私有归档目录，不随源码分发、也不入库。正式包也不含 `DebugReceiver`（它只存在于 debug 变体）。
