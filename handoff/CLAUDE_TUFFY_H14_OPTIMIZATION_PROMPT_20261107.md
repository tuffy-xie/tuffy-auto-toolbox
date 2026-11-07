# 给Claude的执行提示词

你是Tuffy H14的Android/Kotlin可靠性工程师。请基于现有真实证据继续最小优化，目标是实际无人值守业务恢复，不是重写项目、增加测试数量或放宽账本保护。

我同时提供《H14_CURRENT_ISSUES_AND_CLAUDE_HANDOFF_20261107.md》和《H14_PENDING_INVENTORY_20261107.json》。请先完整阅读，区分：仍有实际故障、修复已安装待真机验证、历史问题、已通过能力、仅待调查的假设。

如果你是能访问本机的Claude Code，执行必要代码审查、最小修复、测试、原证书构建、满足安全门槛后的部署及生产入口验证；不要只写计划。若是Claude网页或没有ADB/项目权限，则据附件审查并提供准确的最小补丁/验收步骤，不得声称操作过手机、安装成功或真机通过。不要索取签名私钥、Bot Token或完整隐私数据库。

## 当前基线：先核对，不能回退到旧报告

- 工作区：C:/Users/xiejiatao/Documents/Codex/2026-11-03/tuffy-h14-sol-high-tuffy-auto
- 实际源码：H:/Tuffy_Archive/Tuffy_H12_2026-11-01T11-49-30-908Z/development
- 当前实装code44，commit a3d6137e24b15770a677c1e8e1e4333585c65fdd。
- 分支fix/h14-resume-category-scope-20261107；保留H12 main、d519与92b24c8回滚，不创建code45。
- APK SHA256：6d13d4f2f5670901a58bb383e28d3e0ce3eb54b44bd49aa260e128c3ab2152c7。
- 原证书SHA256：ab39397d23fca7803fa7cd3e33125874d010874d5641e6ffd044a2a3b55f484c。
- 截至手机Nov7 16:40:31 JST：23项正式接取并5/5，138挑战确认回执；另CAR40官方已参加0/5、原广告UNKNOWN。每日22次派发、21条确认，官方21/25，第22条UNKNOWN。Nov6的24项/144条不能当Nov7成果。
- 最近60项针对性、652项全回归通过；实际继续34秒直接ADS，挑战UI调用0，保留PARTIALLY_COMPLETED，明确BLOCKED并停止FGS。
- 登录基础20pt已到账；额外100pt视频入口曾被误关，已安装修复但还没有真实领取验收。

这只是快照。任何手机操作前先重新读手机JST、业务日期、安装SHA、官方页面、SDK、A11y/FGS与账本。Windows历史快约7214秒，不是业务日期依据。

## 最高优先级1：解决每日ADS结算，而不是删除保护

Nov7第22条原始身份：
`AD7b7b03e6-44a1-4cb1-b365-bbb7c20b1914 / index22 / startedAt1793978707341 / before21 / DISPATCHED / UNCONFIRMED`。

原生奖励弹窗和durable dailyReceipt确实存在，但官方多次新鲜读取仍21/25。当日广告积分1100pt是分类日汇总，没有逐次交易ID，不能1100÷50推断该条GAIN。Nov6也曾出现同类问题：ADf710addb-9006-4126-927a-2f07cdef3fac/index2/1793895841423、before1，奖励/钱包增加但当时计数1/25；后来用户手动观看导致4/25，不能倒推原第2条结果。

审查真实调用链：DailyAdTaskController.run → AdInteraction.awaitReceipt → OfficialAdStore.reconcileRecordedReceipt / OfficialAdRecoveryPolicy → OfficialAdQuota.canReserve / allResolved → ReliableLedger。

请回答：
1. 第一处没有满足的证据条件是什么？观察时序、刷新来源、稳定性判断、迟到结果归属是否有可复现的软件缺陷？
2. 能否在官方允许且不绕过安全限制的前提下取得权威GAIN/NO_GAIN或逐次会话身份？
3. 若不能结算原条，是否有可靠证据证明下一观看是独立业务会话且不混淆迟到结果？有证据才放行，不能猜测按钮会创建新会话。

若具备安全条件，使用既有生产接口和FULL事务幂等恢复，直接继续ADS，不重做23项完成挑战。若缺证据，明确外部待确认阻塞，保存断点后转向其他可做事项，不反复冷启动/刷新同一页面，不凭原生奖励UI、积分或计数未增长伪造GAIN/NO_GAIN。不要先给allResolved加一个随意skip分支。

## 优先级2：定位CAR40异常探针失效，保留有效恢复能力

Nov7 CAR40原attempt：CHe1c48440-518d-4b54-9f14-ea2f2bdc97bc/index1/1793985097984。02:11进入AD_PLAYING，95秒后日志`probe_unavailable=unsupported_or_shape`，未取得有效stallReview。

后来看见官方mission3623/user_mission1779173544、02:26参加、0/5，不能把后见参与ID倒填派发前或确认原广告奖励。此前关于截图间隔碰撞只是其他旧证据中的猜测，不能套作此条根因。

沿现有AdInteraction、AdSignals/AdSdkWindows、AdUiClose、StalledAdRestart和ChallengeBoost异常分支追踪第一个失败前置条件。仅在异常分支补最小诊断/恢复缺口；保留95–300秒有进展等待、Home卡死恢复、有界ANA任务移除/重开及官方再開する。

已有Nov7自行车1km真实案例：有效stallReview→一次恢复→一次官方续接→原身份官方NO_GAIN→新attempt继续到5/5。不要为消除风险取消这项能力。匿名续接导航与奖励记账分开；缺身份、多历史UNKNOWN时不能选“最近一条”。

## 优先级3：验收已安装的登录奖励修复与自然运行

读取00:03:04误关原始节点和当前StartupPolicy/StartupFlow：基础20pt已自动到账，漏的是额外100pt视频，不是所有登录奖励都没领。现已同一快照识别、刷新原生领取节点、已知登录上下文禁止通用X，控件不完整沿既有35秒预算保留现场。下一真实弹窗出现时验证领取、广告关闭、官方结果、持久记录和后续Routine；没有真实机会则NOT_TESTED，不重构已装修复来制造新代码。

Nov7原生AlarmReceiver确于00:03触发，触发时出行类别为DISABLED_BY_USER；后来的23项来自manual运行。不得拼成完整NATURAL_RUN_PASS，也不得无证据改变用户配置。下一计划为Nov7 23:58 JST保护、Nov8 00:03 JST Routine，以手机实际为准。保留唯一Tuffy控制器运行，工程heartbeat当前暂停；没有真实配置不要承诺后台工程续跑。

## 冻结已有效的架构

- JOIN：交通方式Tab决定类别，仅按公里数≤配置筛选；重新启动重读可接取列表，不追查已消失普通任务，不无条件全扫已接取列表。局部失败跳过，不伪造接取成功。Boost必要查询保留。
- 同日同配置完整范围可复用，隔离UNKNOWN必须保留PARTIALLY_COMPLETED；新日/范围改变/证据不完整不能误跳过。若审查同日新任务缓存失效，只做有证据的有界修正。
- Google真Skip→X、Pangle、正常AppLovin关闭保持。旧所谓AppLovin“5秒Skip”实际进入Google Play，经受控返回后出现X，未证明通用空白唤醒Skip；禁止无X就点中央/左角或把商店CTA当Skip。
- 不新增第二套状态机、数据库表、广告控制器、常驻服务/线程、持续截图/OCR、正常路径Bitmap缓存或无界重试。删除代码前证明调用关系和副作用。

## 系统与性能事项：按证据，不抢占P0

- 下午安装新增LOW_MEMORY1，随后A11y重连；业务继续阶段无新增退出。PSS/RSS0是无有效样本，不是零分配；空闲PSS约91MiB不是峰值，累计Bitmap分配不是泄漏证明。
- 审查BootReceiver/重连重复工作前先找实际证据；新候选完整CPU/PSS峰值/Bitmap仍NOT_TESTED。
- DUMP+PACKAGE_USAGE_STATS只获授权用于ANA身份诊断；granted不替代App自身UID实测，不能扩到其他App或依赖电脑shell长期派发。
- 红色设备提示与ADS失败没有已证明因果，不绕过官方限制。
- 旧退出采集脚本CRLF曾误计新增，比较完整ApplicationExitInfo时间/块，不累计历史退出当本轮新增。

## 不可突破的边界

不得重播UNKNOWN、直接手改SQLite、改index/清pending、补造回执、跨日混记、卸载/清数据/root、强停活跃未结算广告、放弃当前CAR40资格或扩用旧有限放弃授权、修改账号/筛选/原生调度、修改H12 main、发送邮件或擅自联系客服。用户人工操作造成的官方变化必须单独记来源。

同签名安装须有必要缺陷、相关回归与release通过、正式导出CRC/SHA/逻辑SQLite integrity通过、原APK及回滚点、无活跃SDK和不可中断权益。保留原证书、配置、所有历史账本/UNKNOWN/NAS，不能用旧备份覆盖新成果。Telegram只在既有授权渠道发送新的真实终态一次，不重复旧结果。

## 读取入口与交付要求

优先：outputs/H14_NOV7_SCOPE_LOGIN_FINAL_REPORT.md与FINAL_VERIFICATION.json；work/nov7-afternoon-review/；outputs/H14_NOV7_NATURAL_FINAL_REPORT.md；outputs/H14_ADS_SETTLEMENT_FINAL_REPORT.md；outputs/APPLOVIN_SKIP_FINAL_REPORT.md；work/daily-natural-20261107/LATEST_ADS_SETTLEMENT_CHECKPOINT.md。

先给一份精简的“问题→已知证据→第一个失败函数→最小差异→验收条件”，随即执行能安全完成的工作；普通编译、测试或页面错误自行修正，不每阶段重新问用户，也不为测试制造真实未结算广告。硬阻塞则停止危险操作并保存断点，继续独立可完成工作。

最终报告：改动函数/最小diff/commit、UNIT_PASS、限定范围DEVICE_PASS、真实新派发与确认数、官方最终ADS次数、UNKNOWN与重播数、登录额外奖励是否真正领取、原生自然源头及完整运行结果、LOW_MEMORY/收尾/A11y/FGS/Bot、原签名SHA、备份和NAS读回。严格区分UNIT_PASS / DEVICE_PASS / ROUTINE_RESUMED / ROUTINE_DONE / NATURAL_RUN_PASS / BLOCKED / NOT_TESTED。

没有代码缺陷就冻结版本；不以测试数量或越来越复杂的保护分支作为交付。目标是减少真实故障和无效等待，并让能够被证明安全的后续任务真正继续。
