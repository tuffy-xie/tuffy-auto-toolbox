# H14 code41：挑战升满确认弹窗闭环修复报告

状态：CODE_IMPLEMENTED；最终回归与签名验证结果见下文。候选未安装，DEVICE_PASS / NATURAL_RUN_PASS 均未取得，真机及自然运行验收为 NOT_TESTED。

## 1. 基线及保护范围

从实际 code38 快照 8e04816fc41c879db05b275f3a310647a84e933a 创建 fix/h14-max-boost-dialog-20261103。H14_source_8e04816f.zip SHA256 为 1b3b5a1ffb0fc873869384a706389930b8d524b97b4a0dba4c3c1c2d15371c8b；CRC及267个 app/src 文件逐字节核对通过。

已读取 H14_STABILITY_REPORT.md、H14_ACCEPTANCE_MATRIX.md、实际调查 REPORT.md / INVESTIGATION.json、最终 BIKE 节点与原始日志、既有正式导出及源码。未找到名为 REPORT(4).md 的副本，采用同一故障的原始 REPORT.md 与 INVESTIGATION.json，未据文件名猜测内容。正式诊断中的账本结论来自既有采集，本轮没有连接手机重新导出。

code39（bcbd11c）包含临时官方初次接取会话、导航和按钮改动；code40（45d5b9d）包含独立每日广告例外。已审查差异，本候选未合并它们，原分支与交付均保留，不能把它们当成真机通过。

H12 main / v22.0.12-R47 的目标仍为 5be68d6f25474708dcfb424deb9aeaea272a6516；包含 H13 有效保护的 code38 基线保留。本轮不推送、合 main 或打稳定 Tag。手机仍按最后核实结果为 code38，本轮无 ADB、安装、手机点击、广告观看、数据修改或人工手机辅助。

## 2. 已证明的根因与未知部分

手机业务日期2026-11-03，自然00:03运行 RT0b398a99-a78b-409a-ada5-b9069400fd90 / CHa35ab5fc-b76d-4eb5-b729-dd339416e84c：WALK7项和BIKE500m共8项、48条回执均已保存，本批新增UNKNOWN为0。00:36:25.918 BIKE500m升满，00:36:27最终200pt弹窗仍在，pageType返回OTHER，错误进入通用Back/重开导航；00:37:21.263停止于filtered_category_context_lost。

完整故障链有三个已证明缺陷：boost()仅查一次OK，忽略点击结果且固定等待1200ms，没有关闭后置验证；页面分类漏掉官方最大值完成文案；后续扫描把未关闭的确认弹窗送入通用导航，丢失原分类上下文。

保留节点显示 button_no 是可点击 ViewGroup，button_no_text 是不可点击 OK TextView。旧 tap 还会尝试Bounds手势，因此 clickable=false 不是已经证明的单次失败原因。实际点击是否被接受、是否取消、点击瞬间节点是否移位均缺少旧遥测，仍 UNKNOWN。本次没有证据支持新的LOW_MEMORY、断连或网络故障；不能据旧历史退出记录归因。

## 3. 文件、函数和核心差异

| 文件 | 修改范围 |
|---|---|
| MaxBoostCompletion.kt（新增） | 原生最大值语义/页面分类、已保存原身份校验、纯端口状态机、18秒关闭观察预算/3次跨进程操作预算、整轮阻塞策略 |
| MaxBoostCoordinator.kt（新增） | 新鲜同Window节点、原生祖先关系和Bounds检查、动态官方奖励核对、独立导航Journal、关闭与分类恢复适配、重启/旧弹窗保守恢复 |
| ChallengeBoost.kt | boost()保存原5/5后接入闭环；传入同一engine；run()先处理未完结确认；专用错误保留现场 |
| AutomationEngine.kt | pageType明确MAX_BOOST_DIALOG；通用导航拒绝处理该弹窗；prepareFilteredCategory允许禁止签到提交并复用现有类别/今日校验 |
| Ui.kt | 新增tapObserved，仅回报dispatch接受、onCompleted/onCancelled/3秒超时；旧通用点击行为不改 |
| DailyRoutine.kt | 确认恢复先于普通启动；确认失败即使旧扫描全部完成也阻塞整轮；不进入通用广告重开策略或下一阶段 |
| FaultStore.kt | 追加独立64KiB纯文本故障保留及Journal正式导出；原30目录/128MiB预算不变 |
| app/build.gradle | versionCode 38→41，同包名、versionName与原签名 |
| MaxBoostCompletionTest.kt（新增） | 21项针对性协议/页面/整轮回归；旧测试源文件不改 |

ReliableLedger及SQLite事务规则、原广告记录的保存与确认代码、两个历史UNKNOWN隔离策略、每日25次控制器、交通配置、定时器、Telegram配置、签到/牛排逻辑均未改。完整逐文件哈希证明见SOURCE_SCOPE_VERIFICATION.json。本轮不处理牛排误报奖励、MOV30未知或独立daily入口等其他问题。

## 4. 修复前后流程

修复前：最后广告回执保存 → Boost5/5保存 → 查一次OK并尝试点击 → 等1200ms → 直接扫描 → 最终弹窗被判OTHER → 通用Back/重开 → filtered_category_context_lost。

修复后：最后广告的原可靠确认保存 → 原5/5状态保存 → 从保存记录绑定日期/类别/精确标题/米数/原RUN/index/startedAt/当前官方奖励/完整ads摘要 → 独立Journal提交成功 → 读取当前ANA HomeActivity及有效Window → 精确官方最大值文案与原生控件校验 → 有界关闭 → 观察到稳定的详情/列表/Home后确认消失 → 同一天/安全条件复核 → 复用原导航恢复原类别、参加可能及今日，确认实际筛选 → 标记导航ROUTED → 原processed集合跳过已完成任务，继续下一项及下一类别。

原生点击优先针对当前可操作的button_no祖先，而非只点不可点击文字。必须验证该控件确实是当前OK的祖先、唯一、可见启用、有效且位于当前Window/屏幕范围。没有可操作原生控件，或前次无效后，才按新读取的按钮Bounds中心派发受限手势，无固定坐标。每次动作前再次捕获和校验；节点/布局变化则本次不点击，保留已消耗预算。dispatch成功或回调成功仅作遥测，不能代表弹窗消失。

稳定观察间隔250ms、动作前窗口/位置稳定至少300ms、动作间至少2500ms；关闭观察预算18秒、最多3次尝试，手势回调等待最多3秒。时间预算是协程观察/派发上限，不宣称能硬中断Android Binder或磁盘IO。分类导航另沿用原有各步骤有限重试，不无限Back。关闭失败保持现场，错误为MAX_BOOST_DIALOG_DISMISS_FAILED；弹窗已关但类别/今日验证失败为MAX_BOOST_CATEGORY_RESTORE_FAILED。两者均不继续广告/下一任务、不通用重启、不清最近任务、不回退已确认5/5。

已失败的确认Journal不会因重新进入就重置3次额度；前台已离开时也不会重开ANA掩盖已失败现场。仅真正中断且尚未失败的已绑定导航Journal，允许在恢复入口把现有ANA任务带回前台一次，再核对真实页面；这不是关闭失败后的泛化重开策略。

## 5. 持久化和进程中断边界

新增area=max_boost_completion_v1，使用既有ReliableLedger事务API，不改数据库schema、synchronous=FULL规则或原challenge/ad记录。唯一键为任务日+原挑战key；绑定最后已确认广告三元组和完整ads摘要。阶段PENDING / DISMISS_PENDING / DISMISSED / ROUTED / FAILED；动作先提交意图和累计次数，再派发点击，最后保存原生/手势结果。写入失败禁止后继动作。

确认导航Journal仅描述界面清理，不生成广告记录、不触发观看、不将匿名页面作为新奖励证明。重启后从原记录恢复；已看到可信详情/列表/Home时可直接验证并恢复分类，不再次点击OK或播放最后广告。多个未完结确认、日期改变、身份/奖励不符或无唯一OK均明确停止。

重要限制：code38故障发生前没有这个新Journal。如果升级时仍停在无任务名/日期的匿名最大值弹窗，不能凭200pt或最近一条记录给它补绑定；只有原生页面能唯一显示同一任务名、任务日及参加日时才导入绑定，否则binding_required_no_reward_amount_guess，安全BLOCKED。本候选保证后续受控流程的绑定，不承诺能无条件自动清掉既有匿名遗留弹窗。当前手机不会为验证此点被操作。

外部广告回执与本地确认之间原有不可消除的崩溃窗口仍存在；本次不改广告事务方案。若进程在最后确认保存之后、新导航Journal创建之前终止，原广告保持已确认，但匿名界面可能需人工只读核对；不能猜测绑定或重播。

## 6. 故障证据及资源开销

故障首先保存导航Journal中的明确失败状态/动作结果。独立max_boost_failures.log保留脱敏手机时间、Activity、Window id/active/focused、原生文案、控件class/clickable/Bounds、官方奖励及动作结果，每条≤8KiB、总量≤64KiB。它独立于原FaultStore预算，不采PNG、不删除历史；该小预算也满时不再追加，保留Journal及既有日志/Routine原因，不声称无限保存。

关闭模块没有截图、Bitmap解码或新后台服务，不长期保存AccessibilityNodeInfo或Activity；只保存字符串/Bounds等值快照。弹窗期间每250ms最多一次新鲜树遍历（沿用Ui最多2000节点），复杂度O(N)，仅本次关闭窗口内运行，不新增全程轮询。

首次成功关闭通常增加5次小事务（绑定、动作意图、动作结果、已关闭、已路由）；3次失败通常8次，原广告事务完全不变。每个Journal最多3条动作，随原历史保留，不清旧证据。每条挑战结束复用一次类别重新筛选，可能增加既有按需分类图标视觉处理及导航时间；这些必要视觉不保存PNG，并非本次新增常规截图。

本轮未操作手机，新增PSS/RSS/GC/耗时的真实测量为NOT_TESTED；没有可支持“内存降低若干MB”的前后数据，不能把静态资源上限当作实测峰值。

## 7. 测试与验收

最终完整回归：567 JVM / 75套件，失败0、错误0、跳过0。旧code38的546项、116个测试源文件逐字节保留，包含H13原517项安全断言。新增21项，不改删旧断言。JUnit、构建日志及旧文件SHA证明随候选交付。

| 用户要求 | 针对性覆盖 | 状态 |
|---|---|---|
| 正常最大值弹窗关闭 | 非固定200pt语义、原身份5/5、关闭后再验证分类顺序 | UNIT_PASS |
| OK clickable=false | 优先原生容器；无可操作原生时使用新Bounds手势 | UNIT_PASS（策略/桩）；Android节点操作NOT_TESTED |
| 失效节点/位置移动 | 暂时无效等待、新帧稳定、第一击无效后A→B重新取位 | UNIT_PASS |
| 多次无效有界停止 | 3次预算、回调成功不冒充关闭、取消手势不推进 | UNIT_PASS |
| 详情/Home/列表分流 | 实际纯分类器的原生字段输入；错误任务日/普通modal/外国Window拒绝 | UNIT_PASS |
| WALK末项→BIKE | 原类别恢复后既有CategoryRunSelection选择BIKE | UNIT_PASS；完整真实导航NOT_TESTED |
| BIKE500m→BIKE下一项 | 列表识别、恢复BIKE、已完成key跳过并选1km | UNIT_PASS；真机NOT_TESTED |
| 普通广告OK不误判 | 包名/SDK Activity/Window/官方文案联合负向检查 | UNIT_PASS |
| 已确认和UNKNOWN保全 | 端口不暴露观看/原账本写接口，确认及UNKNOWN夹具不变；源码范围哈希保护 | UNIT_PASS；未改手机数据 |
| 进程中断 | 已提交点击意图后重建；已关后未保存重建；跨重启次数不重置、不重播最终广告 | UNIT_PASS（模拟进程中断）；Android系统回收NOT_TESTED |
| 写入失败/跨日/类别失败 | 动作前写失败零点击、分类失败明确停止、分类后日期变化不推进 | UNIT_PASS |
| 整轮闭环 | 即使旧扫描已完成，确认失败仍BLOCKED且不启动每日广告 | UNIT_PASS |

关闭与原类别回到正确列表这一路径已CODE_IMPLEMENTED，原“最大值弹窗→OTHER→泛化Back→filtered_category_context_lost”的触发链被专用分类/关闭/阻塞替代。不能据模拟通过宣称所有filtered_category_context_lost原因都解决，也不能称真实手机本次故障已DEVICE_PASS。

## 8. 候选、签名和交付

版本：22.0.14-R47 / code41，包名com.anapocket.auto。同原正式证书SHA256 ab39397d23fca7803fa7cd3e33125874d010874d5641e6ffd044a2a3b55f484c。

功能提交：79ac34b7aec8320753ad29086683ab81c02b04e4。

APK：Tuffy_22.0.14-R47_code41_signed.apk，字节50272360，SHA256 d6de4f084d26daa9c1de4cafd71ab6a0047f5b3673b4f691d0381a12966dd834。

最终构建：build-20261103-030704.log。aapt包名/版本、apksigner验证、源码快照、JUnit摘要、保护范围哈希、交付文件SHA和NAS读回结果由交付目录JSON记录。code38/39/40和H12/H13原交付没有覆盖。

## 9. 尚待真机验收

本候选需要用户另行授权安装后，验证实际原生ACTION_CLICK、失败后的受限手势、真实窗口切换、详情/Home/列表恢复、WALK→BIKE及BIKE500m→下一项。在合法的新业务任务上观察，不为复现重播11月3日这48条已确认广告，不改变11月2日BIKE4/MOV30 UNKNOWN。

还需核实当前匿名遗留弹窗能否取得唯一原身份；证据不足则BLOCKED，不降低保护。跨日、拔线及长期无人值守仍NOT_TESTED，本轮没有新自然运行通过证据。

状态结论：CODE_IMPLEMENTED / UNIT_PASS（最终数字见上）；DEVICE_PASS未取得；NATURAL_RUN_PASS未取得；安装、真实广告及跨日验收均NOT_TESTED。仅交付候选，等待用户决定是否安装。

API依据：[Android AccessibilityService 官方文档](https://developer.android.com/reference/android/accessibilityservice/AccessibilityService)。ACTION_CLICK/dispatchGesture及其回调不能替代实际页面后置验证。
