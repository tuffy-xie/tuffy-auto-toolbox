# ANA Pocket Auto R36 — 最新真实测试报告

日期：2026-10-27。业务日期/计划时间：Asia/Tokyo。正式包：11.0.0-R36 / versionCode 11。

## 结论与证据边界

每日固定25次管理模块、独立状态机、持久化、主界面、调度及通知已实现。58项JVM测试通过；真机隔离存储、进程中断恢复、受控原生界面成功/报错流程及闹钟通知已验证。**R36控制器进入真实ANA广告页面、真实广告SDK完整交互、真实奖励到账和当日25次真实循环，均未验证通过。**

本轮没有主动启动25次真实广告循环，没有执行挑战接取或Boost。模拟回执不是ANA真实积分。

## APK 与基线

- 正式APK：ANA-Pocket-Auto-R36-release.apk，3,492,059 bytes。
- SHA256：6dd47cc9217b014265eb919c9269dd5c9b9ef85ed83461c46c6fe1aba0b4a3ea。
- apksigner验证通过，APK Signature Scheme v2=true。
- 已通过标准流式安装更新真机，设备base.apk SHA256与上述值一致。
- 设备：Redmi 12C (2212ARNC4L)，Android 14；ANA Pocket 2.3.4 / code 605。
- R32 APK存在，SHA256=05181ec3171b061b57de0dcb07b49a69041af7de3ba84192e5263fc8450298db。没有独立R32源码快照；提供目录已含R33–R35源码，因此基于现有广告模块整理，未新建工程。
- 安装前设备实际为R35，SHA256=a8f4392124320dce2237682627646de9b5992da486f3f994ec0a728af7e09ab7。

## 分阶段状态

| 阶段 | 本轮真实结果 |
|---|---|
| P0 | PASS：源码/历史证据审查，R32文件及R35设备哈希、设备版本、权限核对 |
| P1 | PASS（单元+真机隔离存储）：固定25、分计数、持久化、幂等、写入失败、损坏阻止启动 |
| P2 | PARTIAL：真实页面只读导航基线已看到1/25；正式主界面及阻止重复启动链路通过；R36广告控制器导航探针被测试进程与无障碍连接生命周期阻塞，不能标PASS |
| P3 | PASS仅限受控原生模拟界面；真实广告SDK/真实回执未测试 |
| P4 | PASS：受控并发、进程被杀恢复、注入写入失败、模拟通信错误；真实SDK断网恢复未测试 |
| P5 | PASS仅限JST边界单元逻辑、隔离日桶、25条后拒绝第26条；真实跨夜和25次真实广告未测试 |
| P6 | PASS：Android系统闹钟送达、自检通知、断开服务通知证据、次日09:05 JST排程；物理拔USB及设备重启后完整任务尚未确认 |
| P7 | 37项原有筛选/卡片/距离/详情状态机单测通过；挑战引擎功能代码未改，执行入口暂停；本轮未领取挑战或Boost |

## JVM 单元测试

58 tests / 0 failures / 0 errors / 0 skipped。原始XML位于 evidence/R36/junit/。

| 测试类 | 数量 |
|---|---:|
| AdInteractionTest | 16 |
| DailyAdTaskTest | 5 |
| CardFilterTest | 10 |
| CardsTest | 10 |
| DetailStateMachineTest | 10 |
| DistTest | 7 |

受控JVM链路RUN_ID=JVM-AD-001：AD_PLAYING → AD_CLOSE_AVAILABLE → 模拟回执 → AD_RETURNED → 官方计数模拟0到1 → AD_FINISHED。原始输出：controlled_jvm_interaction.txt。其来源明确为scripted_test_transport，不能作为真实观看或到账证据。

## 真机受控原生界面

测试页面只存在于独立测试APK，界面标明CONTROLLED TEST FIXTURE / NO REAL POINTS；通过Android无障碍节点和实际点击交互，不请求真实广告，不产生积分。

- 成功链路：DEVICE-FIXTURE-1793064753283 / PID 4138。
- 实际状态记录：AD_PLAYING → AD_CLOSE_AVAILABLE → REWARD_CONFIRMED（模拟回执）→ AD_RETURNED → AD_FINISHED。
- 使用入口前0/25、返回后1/25的模拟官方计数，完成与奖励分别确认；截图/UI树在 evidence/R36/device/fixture_*。
- 错误链路：DEVICE-FIXTURE-1793064760255 / PID 4663；实际显示「通信エラー」，FAILED，started=1、completed=0、reward=0。
- 证据：instrument_ui.txt、instrument_ui-error.txt。两份记录明确写有official ANA reward NOT CONFIRMED。

## 真机持久化及异常恢复

最终存储套件：DEVICE-FIXTURE-1793064782934 / PID 4951，全部检查通过。

1. 隔离SharedPreferences写入25条完整记录，读取为25/25/25；第26次预留返回null。
2. 同一回调执行两次不重复计数。
3. 另一日期读到0，不删除原日期记录；这不是实际跨过午夜的证明。
4. 损坏JSON阻止启动，不回退为空计数。
5. 注入commit=false时，预留抛出失败且计数仍为0。
6. 两线程同时预留，只产生一个在途记录。

真实进程中断：PID 21985写入AD_PLAYING后执行killProcess，系统报告Process crashed（预期故障）。PID 22047重新运行后读取到同一条UNCONFIRMED，第二次预留被拒绝。证据：controlled_seed_before_kill.txt、instrument_seed-kill.txt、instrument_recover.txt。上述均使用r36_fixture_命名空间，不操作正式日记录。

## 正式主界面和真实任务管理链

升级保留两条旧R35启动记录，降级为UNCONFIRMED并保留原始失败原因：

- AD1793030469265：returned_too_early。
- AD1793059500103：ad_timeout_95s。

正式记录：started=2、completed=0、rewardConfirmed=0、failed=0、unconfirmed=2，剩余启动预算23。旧版失败结论不能充当新版本完成/奖励证据；没有清数据或修改计数绕过限制。

正式主界面点击「立即开始」的RUN_ID：ADf9c159fa-e2a3-49cf-9510-3a240931fc0d。结果day_halted_or_unconfirmed，启动次数仍为2，发送阻塞汇总通知，前台服务退出；没有新启动广告。原始证据：R36_final_device_log.txt、ad_result_ADf9c159fa-*.json。

## 本机调度和通知

- R36自检RUN_ID=DEVICE-FIXTURE-1793063860432，在10:17:55 JST由系统AlarmReceiver触发。
- 系统通知记录id=9001，标题「本机调度自检已触发」，正文明确本次未启动广告。
- 该自检验证系统派发/通知，不证明真实广告播放和奖励。
- 下次日计划保留为2026-10-28 09:05 JST，AlarmManager和WorkManager仍在Android本机运行。
- 原设备旧R35在本轮开发期间曾于09:05按原配置自然触发一次广告，09:07超时结束。该记录已归档，不能写作R36成功。
- 物理拔线测试尚无确认结果；不能把“代码没有ADB依赖”写成“物理拔线已测试”。

## 失败现场与修复

- 构建环境：Windows沙箱拒绝JDK逐层canonical路径枚举。以临时R:/S:映射已授权工作区/SDK，正常编译、签名、lint和测试流程通过；没有跳过测试或改业务断言。
- 首次测试包离线依赖缺core-runtime 2.1.0，测试变体与正式包已用的2.2.0对齐。
- 初次写入失败注入器错误返回原始Editor，导致失败模拟失效；修复链式返回，原断言不变，重跑通过。
- 初次测试进程无法取普通AccessibilityService内存实例，受控UI驱动改用instrumentation自身无障碍连接。
- HyperOS后台启动限制和独立测试Activity的Kotlin运行库隔离导致页面未就绪；改用平台Java测试Activity及标准前台启动，重跑通过。
- 真实ANA导航探针未通过；临时服务重绑试验不作为PASS，最终测试源移除该副作用。测试结束恢复原有无障碍和悬浮窗授权。
- 初次安装被USB安装策略拒绝。用户明确允许后，标准流式安装及对应测试应用确认成功；没有关闭系统安装校验。

## 尚未完成

1. R36控制器在真实ANA广告页的独立导航验证。
2. 真实广告SDK单次完整交互、可访问关闭按钮覆盖和真实完成/奖励回执核对。
3. 真实当日连续25次、实际跨夜、设备重启和拔USB后的完整广告任务回归。
4. 无标签关闭图标/多段SDK流程的可靠支持；当前未知情况有界等待后UNCONFIRMED停止。

下一步只继续每日广告：保留现有两条未确认记录，取得受控真实SDK单次证据后再扩展；不要清零强跑，也不要切回挑战领取。


## 最终交付测试包复核
- 最终测试包SHA256：e91103d46c5a01cf308a8cb92fcea1e4d0dd168143b21f87519fd957544b1513。
- 存储套件：DEVICE-FIXTURE-1793065535783 / PID15550，全部通过。
- 原生UI成功：DEVICE-FIXTURE-1793065537342 / PID15621，通过。
- 原生UI报错：DEVICE-FIXTURE-1793065544362 / PID15885，通过。
- 最终正式记录快照：DEVICE-FIXTURE-1793065567035，仍为2次启动、0完成、0奖励、2未确认。
- 测试应用已从手机移除；正式助手无障碍已绑定，通知/精确闹钟/悬浮窗状态已恢复。
- 正式APK未重建替换，设备端SHA256仍与交付文件相同。
