# 恢复链集成候选：code43共同基线 → code44恢复优化＋Google阶段关闭＋迟到接取核对

结论：原UNKNOWN尚不能在手机安全补记，Routine仍BLOCKED；已审查集成，无文本冲突，完整回归UNIT_PASS；独立同签名code44 APK已生成但不满足当前安装门槛，未安装。候选DEVICE_PASS/NATURAL_RUN_PASS均NOT_TESTED。

## P0：当前原业务，未绕过

实装仍43。只读保存当前官方成功/首次Boost/100pt面板、Window/HomeActivity/task1687、原派发start节点、日志及正式包权限。没有点击、切走/关闭Boost面板、观看广告、调用恢复、强停/重开或安装。原attempt为徒歩3km/2026-11-04、CH197ed324-dd0d-467e-ae75-227f62264dea、index1、startedAt1793718980474，原DISPATCHED/departure已在生产保存后输出的Journal中可追溯。

正式包run-as只读访问被明确拒绝：package not debuggable。未借Root/UiAutomation、修改权限或伪造外部文件绕过。现有正式导出需打开Tuffy界面；本轮保留当前Boost现场，没有将旧日志冒充本次SQLite直接读回或安装前完整备份。当前官方Fragment的任务、参加实例、JST日上下文与广告关闭后片段相同，但旧attempt未保存这些server ID，也没有实装外部生产补记入口；因此不能仅凭同task/100pt/首次Boost把原账本改为成功。

reconcileRecordedInitialReceipt原逻辑只查找startedAt..finishedAt内REWARD_CONFIRMED文件。本次00:17:56超时，官方成功在之后人工Skip→X才显现，条件必不满足。正式43不能使用本次新写候选函数。原UNKNOWN/历史记录、当前面板及自动配置保持；官方接取成功是已观察事实，钱包到账和原attempt确认不能混同。今日原持久24条/4项5/5不加计此条。当前不能安全自动续跑，也不点击「ブーストしない」牺牲Boost资格。

## P1：审查后集成，不作盲目合并

共同基线cc971d7/code43；2201631恢复优化和a650163 Google补丁原分支/产物保留。实际生产交叉文件仅AdUiClose：前者修改beginFlow/release及95秒异常画布采样，后者修改identifyClose选择优先级，区域不重叠。已先保存PRE_IMPLEMENTATION_REVIEW，再建立独立fix/h14-integrated-recovery-20261104，从2201631仅应用审查后的Google四个app源码/测试文件，git apply --check通过。

语义检查：native receipt仍优先拒绝广告关闭；Google native X、NO_THANKS、唯一Skip、可信Close次序保留；observedAdvanceControl每帧重置，Skip与Close各3次原预算不混用。Skip不提交回执。Google此HTML模板不是application-canvas，不冒称它具备全套opaque采样；95秒异常探针与300秒绝对上限保持原候选。initialJoin仍不能走recoverOpaque强制重开；Home重开仍不能越过未结算广告。UI所有权、匿名再开保护、恢复意图/次数先持久化均保留。

14个关键生产文件与2201631逐字节相同（AdInteraction、Home/强停/恢复预算、MaxBoost、DailyRoutine、每日25次、派发协议、退出锁屏、FGS、Tuffy进程恢复等）；AdUiClose.identifyClose与a650163逐字相同。Manifest未改。没有新平行控制器、常驻线程、服务、正常路径截图/OCR/Bitmap或数据库schema。

## 最小新增差异：迟到回执仍走原生产入口

相对2201631只改5个生产文件：AdUiClose、GoogleDismissPolicy为原Google补丁；ChallengeBoost、NativeChallengeReceiptPolicy、ReliableLedger为迟到接取核对。没有增加独立恢复入口。

reconcileRecordedInitialReceipt保留旧的原始已记录回执路径；没有历史回执时，必须同时具备：原start官方标题/任务日证据、当前前台唯一标题/任务日/参加日、原DISPATCHED及离开广告入口、同run/index/startedAt与派发阶段、唯一待决原attempt、稳定1.5秒的官方首次成功和完整预期奖励阶梯。不能拿金额、次数、最近任务或ADB Fragment ID代替这些条件；当前页面若无法给出完整可见身份，仍返回未确认，不能保证本次面板会满足候选门槛。

保存前在ReliableLedger同一同步锁内重新读取SQLite原行、重新计算待决数和核对原身份，再复用既有saveChallenge FULL事务/不可回退检查；不插入新attempt、保留ordinal与历史。只修改原初次广告确认及JOINED/boost0，确认成功后才返回后续Boost逻辑。已确认同一原attempt重复核对不再写入、不再计数；写失败不改变调用者行副本、不进行下一次UI操作。AccountSafety、JST换日、停止及外层ANA所有权令牌在复核/保存前重验。

这是候选内部生产恢复接口的扩展，不是远程写库口令或外部可调用广播；实装43没有新增接口，所以本轮没有“隔空补记”或把测试结果灌入真实账本。

## P2：受控回归结果及边界

603项/80套件，失败0、错误0、跳过0，UNIT_PASS。原44的589项＋Google6项＋迟到回执8项一起运行，无测试源删减。

| 覆盖项 | 可核实结果 |
|---|---|
| Google假Close/Skip→X | 实际脱敏节点回放＋生产AdInteraction状态流UNIT_PASS；Skip不确认，最终回执提交先于dismiss |
| 迟到官方接取可靠入账 | 生产commitInitialOnce在精确身份条件下确认原attempt；fsync重载检查run/index/start/dispatch/单条确认，UNIT_PASS |
| 缺失/错任务身份 | 无历史回执且无精确身份拒绝；错key/run/start/index/date/派发阶段、多个待决、非原生面板、金额不符均拒绝，UNIT_PASS |
| Home/无进展广告恢复 | 原Home预算、停滞30秒复核、慢广告180秒与300秒上限、保存失败不系统操作，UNIT_PASS；原生AppInfo/重开NOT_TESTED |
| 中断前后不重放 | 派发ARMED/DISPATCHED保护、恢复预算重载、提交前失败保留UNKNOWN、提交后中断重载不再次确认，UNIT_PASS |
| 5/5、每日25次、UNKNOWN、退出/锁屏/调度 | 原MaxBoost/NativeDialog/DailyAd/OfficialQuota/Routine/TaskExit/LockScreen回归UNIT_PASS，关键生产文件未改 |

持久化故障注入使用受控文件端口；Android SQLite真实进程杀死与候选真机事务读回NOT_TESTED，没有杀真实广告。原FULL实现及同步更新审查不等于实机验证。当前屏幕不会因这些测试自行恢复；候选DEVICE_PASS/NATURAL_RUN_PASS不成立。CPU、PSS和Bitmap实际性能NOT_TESTED，仅能核实没有新增正常路径截图调用。

## 候选、安装门槛及保留项

包com.anapocket.auto，22.0.14-R47/versionCode44；沿用尚未部署的44候选号，不创建45，不覆盖原2201631的APK。新集成APK SHA256 `d400515b298ee33ced5e449e9e4d3c79d544d215e316c69c7b869ad47216d384`；证书SHA256 `ab39397d23fca7803fa7cd3e33125874d010874d5641e6ffd044a2a3b55f484c`，与原正式证书一致，apksigner及aapt核验VERIFIED。

当前未决接取尚未完成生产确认、Boost面板需保留，手机完整升级备份尚未取得，因此安装门槛BLOCKED。APK存在且签名可核验不代表允许现在安装；未改自动开关/权限/定时器、未安装/卸载/清数据或重启任务。先保留当前现场；下一安全阶段须通过正式只读导出取得原SQLite/配置/UNKNOWN备份并证明页面与原身份，再决定受控设备验收，不能为绕过这一阶段直接覆盖。

最小patch（相对220与相对code43）、源码、JUnit、完整Git refs、P0证据及NAS认证读回分别独立归档。H12 main/tag、43实装APK、2201631和a650163原始交付不改。Git提交与全部SHA见INTEGRATED_RECOVERY_VERIFICATION.json，NAS读回见INTEGRATED_RECOVERY_NAS_READBACK.json。
