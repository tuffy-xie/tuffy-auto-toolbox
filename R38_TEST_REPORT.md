# ANA Pocket Auto R38 — 完整真实批次测试报告

日期：2026-10-27（Asia/Tokyo）。版本13.0.0-R38 / versionCode13。

## 用户目标与真实结论

目标：普通点击APK主界面「开始」后，自动处理当天全部剩余广告，到达25次上限后自动停止并通知。

**本轮已达到该目标。** 正式R38一次普通点击，从官方5/25连续执行到25/25；新增20次观看全部完成确认及奖励回执确认，原有记录未被清除或回填。积分由3,334增至4,334，实际增加1,000积分。20:26:27 JST自动停止并发送汇总通知。

| 核验项 | 结果与证据 |
|---|---|
| 普通主界面开始 | PASS，19:59:16 JST一次普通点击，非debug单次入口 |
| 全部剩余额度 | PASS，新增第6至25条共20条，同一RUN_ID连续完成 |
| 关闭处理 | PASS，实际观察60个广告分段、60次商店小窗关闭、40次SDK关闭操作 |
| 有效完成 | PASS，每条官方观看计数均恰好+1，最终25/25 |
| 真实奖励 | PASS，20条新回执确认；官方余额3334→4334，+1000 |
| 自动终止与通知 | PASS，official_limit，limitReached=true，通知9001真实存在 |
| 持久化与旧记录 | PASS，测试读取真实日账本25条；新20条全部成功；旧5条逐字段不变 |
| 达上限再次开始 | PASS，启动数仍25，没有第26条，没有进入广告 |
| JVM单元测试 | PASS，76 tests / 0 failures / 0 errors / 0 skipped，原67项断言保留 |

## APK

正式文件：ANA-Pocket-Auto-R38-release.apk。

SHA256：bf9bed49c52e70e69c5b7cd212d0e23cb7878916a280d5b9737da704a50b87b8。

已安装在Redmi 12C，设备base.apk哈希与交付包一致。APK签名v2验证通过，沿用原release证书ab39397d23fca7803fa7cd3e33125874d010874d5641e6ffd044a2a3b55f484c。正式包没有DebugReceiver。

设备：2212ARNC4L / Android14 / HyperOS1.0；ANA Pocket 2.3.4(code605)，包名us.ana_pocket.ANAPocket。

## 原故障的实际原因

用户在R37点击开始：AD8bffaebc-48e2-4963-8ddd-1f26e64864ad，第5条记录，19:38:12启动、19:39:53超时。无障碍当时连接正常。

失败现场前台为Google Play的HsdpAlias商店小窗。它的关闭节点content-desc是中文「关闭」，而R37只匹配日文/英文关闭词和叉号。该小窗挡住广告，控制器没有识别到可关闭节点，最终95秒超时。

继续检查还看到同一次观看包含Ad 1 of 3、Ad 2 of 3、Ad 3 of 3，每段都可能进入商店小窗。R37整次95秒及共享3次关闭预算不适合这一流程；R37遇到旧未确认记录时又把显式手动任务限制为仅新增1次，无法满足一次点击处理全部剩余广告。

故障日志、截图、节点XML均保存在evidence/R38。没有把「点击成功」当成「观看完成」。

## R38修复

1. 商店小窗关闭限定为前台com.android.vending，且同时出现Google Play品牌与关闭工作表标记。只选择明确的关闭节点，按该节点实际范围点击，不触碰安装按钮，不猜测固定坐标。
2. 沿用既有ANA/Five识别及交互状态机，新增显式Ad n of m分段信息。仅确认分段前进才更新95秒分段预算；整次观看仍最多300秒，同一分段反复出现或倒退不会无限续时。
3. 每段SDK关闭最多3次、商店小窗关闭最多2次，分别计数；分段不会增加startedCount。
4. 普通手动开始可处理全部剩余额度，旧未知记录保持原样。同一运行只在上一条已确认完成及奖励后继续；本运行出现新未知或在途状态不能继续预留。每天25启动硬上限及官方报错终止规则保留。
5. 新增7项多段流程/计时/商店范围测试与2项完整手动批次测试，保留已有67项断言。没有重建工程，没有执行挑战或Boost。

## 完整真实批次

RUN_ID：AD3edb135d-0db2-404a-ae97-50d37bb84b15。

- APK来源：正式release，哈希与上文相同。
- 触发：19:59:16.019 JST普通点击APK主界面开始。
- 基线：官方5/25，积分3334；正式账本5启动、1完成、1奖励、4未知。
- 过程：第6至25条均出现新广告播放、关闭、ANA新奖励回执和官方计数恰好+1。同一RUN_ID连续接续下一条，没有重新按开始。
- 分段：共60条分段进展；60次Google Play小窗关闭、40次SDK Close操作。真实单条从持久化开始到终结为72.079至89.703秒；超过95秒的三段长流程另由控制测试验证，不能把虚拟时长写成真实观看时长。
- 20:26:24：第25条捕获新ANA奖励回执。
- 20:26:26：官方24→25，AD_FINISHED。
- 20:26:27.492：整批结束，detail=official_limit、limitReached=true、stopped=false、blockedReason为空。
- 20:26:27.539：系统通知9001已发布，标题「今日广告任务已停止：达到观看上限」；前台运行服务随后退出。
- 整批约27分11秒。运行期间本助手仅只读日志和截图，没有用ADB代点广告、没有使用会抑制无障碍的uiautomator/instrumentation、没有安装/重启/切权限干预。
- 结果画面：官方25/25、当前所有积分4334。真实余额增量1000与20次50pt回执一致。

核心证据：full_batch_started.json、full_batch_runtime_log.txt、full_batch_terminal_and_notification.txt、official_25_of_25.png、verification_scope.json。391份逐条状态/截图/结果位于evidence/R38/batch/。

## 计数与历史记录

最终JST日账本：started25 / completed21 / rewardConfirmed21 / failed0 / unconfirmed4，剩余启动预算0。

官方25/25与程序确认数21不同，原因是历史4条仍无足够自动链路证据，必须保留未知：第1、2条是旧版记录，第3条是此前Five超时，第5条是本次用户报告的R37商店遮挡超时。第4条为R37已确认成功。

第5条故障现场为诊断人工关闭过前两段商店窗，R38的只关闭探针清理了第三段并返回遗留回执；导航关闭该遗留回执后才建立本轮5/25、3334pt基线。该清理不属于本轮20次自动PASS；其旧记录保持UNCONFIRMED。没有根据总数25反推或回填历史成功。

结束后使用已签名测试读取器导出正式持久化账本，DEVICE-FIXTURE-1793100788790。25条记录真实存在；当前RUN_ID正好20条，全部completed/rewardConfirmed=true；每条officialBefore=index-1、officialAfter=index。旧5条与运行前逐字段相同。原始文件actual_full_day_ledger.json及persistent_ledger_verdict.json。

导出结束后移除测试应用并恢复正常无障碍绑定；再次点击普通开始验证上限，RUN_ID=AD06298637-854c-416e-9c79-6eea9c940cf1，立即返回local_started_limit，启动数仍25、没有广告页面或第26条。通知重新确认达到上限。证据post_cap_start_verdict.json。

## 单元与受控测试

本次构建testDebugUnitTest、assembleDebug、assembleRelease、assembleReleaseAndroidTest成功，76 tests / 0 failures / 0 errors / 0 skipped。

AdInteraction16、AdMultiPart7、DailyAdTask11、AdCloseIcon5、CardFilter10、Cards10、DetailStateMachine10、Dist7。XML在evidence/R38/junit/。

三段控制测试每段虚拟播放60秒并分别关闭商店和SDK结束控件，最终只确认一次官方计数变化；重复/倒退分段不能续时，整次300秒仍终止。手动批次测试保留旧5条记录，连续新增至25后拒绝继续；本运行新未知/在途和官方错误阻止继续。

本轮R38测试APK只用于读取真实持久化结果，没有把模拟回执作为真实积分。本轮76单测与真实20次批次分别列出。R37已有的损坏存储、写入失败、并发、进程kill、模拟通信错误、系统闹钟/重启证据仍按其原版本归档，没有冒充R38本轮重跑。

## 调度、范围及剩余验证

保留每日09:05 JST，最终系统中仍有2026-10-28 09:05的精确闹钟。HyperOS后台启动/悬浮窗、无障碍、通知已恢复，测试应用已移除。R38整个广告循环在Android服务内执行，PC只是只读采集日志。

本轮没有新增一次R38物理拔USB、实际跨午夜或设备重启全流程测试；R37已有无持续ADB闹钟及真实重启验证，不能改写成R38新增实测。其他未出现的SDK样式仍需按真实样本验证。自动逐条余额刷新审计尚未增加，本轮总到账1000由官方界面独立核验。

挑战接取、Boost和挑战详情诊断继续暂停。
