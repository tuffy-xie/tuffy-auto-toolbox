# code44 最小恢复候选：源码审查与回归交付

状态：UNIT_PASS；签名及包名 VERIFIED；DEVICE_PASS / NATURAL_RUN_PASS 均 NOT_TESTED。手机保留 code43；安装、新广告、任务重跑、开关/配置修改均0。既有次日调度仍开启，当前43已知恢复身份风险仍存在，本候选未部署，不能说风险已经从手机消失。

## 真实基线及故障归因

已逐文件读取要求的四份验收/恢复报告、实际源码及MOV20原始节点，原最终清单83项SHA全部吻合。code43原功能提交b2c869bc；本次从cc971d7独立分支开发，未修改H12 main/tag5be68d6或code43分支。实装43包SHA f6f8ccf9…，原证书ab39397d…；本次签名验证使用同一正式证书。

原业务真实完成24接取、24项5/5、挑战144＋每日25＝169条确认、官方25/25、Routine DONE；签到仍UNKNOWN。Tuffy连续95分56秒，新增LOW_MEMORY0，PSS78.4–120.8MiB，视觉376次、PNG0，软件Bitmap累计1748736000字节约1.63GiB；累计分配不是实时占用或泄漏证据。

12:17:53 MOV20 index5已派发，原Boost3/100pt。12:19:30 awaitReceipt在95秒返回UNCONFIRMED时仍是playable/application-canvas，最后显示控件操作12:19:19仅距约11秒，不能认定广告已停止/失败。recoverOpaque随后移除ANA，12:19:38 SIGKILL与主动移除对应；12:19:48匿名续接、12:19:52按ordinal4/100pt判NO_GAIN，再发新index6并最终完成。业务恢复确实成功，但超时直接授权重开、无唯一身份却判结算，是可复现源码缺口；底层SDK/网络为何未返未知，不能归咎内存。

Home失灵时launchApp只恢复同一Android task，不能重建挂住的Window；保留强制重开有实际必要。原ensureListPage耗尽后没有安全HOME冷重开绑定，旧StalledAdRestart非MAX入口拒绝SDK而eligible却要求SDK，不能把该分支写成已证明的Home恢复成功。

## 实际调用链、所有权及持久化

原生Alarm/WorkManager → AutoAccessibilityService.startDailyRoutine同步单入口/CheckinRuntime ANA所有权 → RunForegroundService → DailyRoutine/RoutineSequence → StartupFlow → ChallengeTaskController/AutomationEngine → AdDispatchProtocol → AdInteraction.awaitReceipt/AdUiClose → ReliableLedger FULL SQLite原回执事务 → MaxBoostCoordinator → DailyAdTaskController/OfficialAdStore → Routine结果/通知 → TaskExit/ManagedAppCleanup/锁屏 → FGS.stop。外层RT持有UI令牌，子CH/AD不另建控制器。

Challenge/每日/签到账本是ReliableLedger FULL事务，原run/index/startedAt不改；Routine阶段是同步commit投影，不能代替广告确认。StalledAdRestart的同步SharedPreferences claim在系统动作前提交，每身份一次、每日最多3次。recoverOpaque原尝试、日期、派发阶段、未结算状态、恢复意图/次数先SQLite保存，再进入RECENTS；已有stage会阻止中断后重复移除。AnaProcessRecovery是Tuffy进程恢复，唯一WorkManager且有次数/日期/用户停止边界，与ANA广告重开不同。

必须保留停止/换日/账号风险/分类取消、UI独占、ARMED派发前事务、确认不可回退、UNKNOWN不重播、重开预算、MaxBoost绑定/实际弹窗消失及分类今日验证、每日官方精确+1和25上限、退出前无活跃广告检查。旧execute仍有调用，不能当死代码删。TaskExit、FGS、SQLite schema、定时器逻辑均未改。

## 最小修改

| 函数/范围 | 实际变化 |
|---|---|
| AdInteraction.awaitReceipt | 原95秒异常分支才调用可选进展探针；有进展继续原广告，绝对300秒上限不变。30秒稳定复核成功先写证据，再输出UNKNOWN供既有恢复入口判断。 |
| AdUiClose.stallProgressKey/beginFlow/releaseVisualCache | 复用当前Window/语义、既有按需inspectBitmap；异常每15秒16×16采样，只保留256整数，流程结束释放。正常路径不调用探针。 |
| ChallengeTaskController.watch/recoverOpaque | 原尝试stallReview先FULL保存；超时画布必须有当前run新鲜复核，明确官方加载失败分支保留；原精确任务移除/次数预算保留，增加UI令牌和账号检查。 |
| boundNativeIdentity/ChallengeDetailReceipt.identityMatches | 仅前台可见原生Window的唯一标题、任务日、参加日精确匹配，金额/次数/最近记录不能提供身份。 |
| resumeOfficialInterruptedBoost/awaitResumedBoost/reconcileBoundLateAdvance | 两条续接共用既有45秒观察、NO_GAIN至少15秒及加载守卫、稳定二次复核；原尝试成功/失败/未知分开，迟发回执也要求身份，禁止匿名误认。 |
| AutomationEngine.ensureListPage、StalledAdRestart.recover/reserve、HomeNavigationPolicy | 旧导航耗尽且纯Home/无SDK/无待决广告才复用原AppInfo结束流程；同步claim成功才系统操作；回来重新核验官方列表。3处repeat内伪break修为真正break，边界预算保留。 |
| DailyRoutineController、ChallengeTaskController.run | 删除两处从未acquire的唤醒锁对象/无效release；移除一个编译器证实未读的局部结果。其余实际持有的锁保留。 |

不增加第二套恢复控制器或状态机。两处强制重开实现仍为2处：recoverOpaque精确RECENTS移除、StalledAdRestart AppInfo强停；新增HOME只是既有入口的导航原因。新增业务状态enum0，新增持久异常证据字段stallReview1项；没有新数据库/线程/服务/后台轮询/唤醒锁。

## 恢复边界与风险

30秒不是广告失败判定，而是95秒后另做两次15秒有界观察，覆盖原故障最后交互距超时仅11秒的窗口；单次历史数据不能证明30秒为最优值。画面变化只是启发式进展：小运动可能漏检、持续动画可能延迟恢复；最终300秒限制仍在。无法取样不给强制重开许可。只对既有opaque画布分支增加复核，其他SDK/每日默认端口行为没有假装全部优化。

强制重开能力保留，不等于匿名弹窗可以安全自动续接。若重现原MOV20的匿名全局弹窗且完全没有标题/日期，候选会保持UNKNOWN并停止点击；这一情况确实仍可能BLOCKED，不能承诺它与旧冒险路径一样全部完成。干净官方页可沿现有SubmittedUnknownIsolation继续独立任务，仍有SDK/全局弹窗则不能隔离、不能双控制器抢画面。纯Home无未决操作不会因新增身份规则被永久拒绝，仍可走原有界重开。

## 回归与实际边界

最终完整JVM回归：589项/78套件，失败0、错误0、跳过0，UNIT_PASS；原118个测试源文件字节不变。新增16项只覆盖本次差异。构建与原正式签名验证通过。不是函数返回true即验收：新异常测试调用生产AdInteraction，核对receipt提交顺序、原任务/run/index/startedAt、DISPATCHED/UNKNOWN持久文件及中断重载；重开claim测试核对fsync/失败不操作/次数重载不能重复。

| 要求 | 可核实结果 | 真实设备边界 |
|---|---|---|
| Home卡死恢复 | UNIT_PASS：纯Home许可、SDK/待决/换日/重复/每日3次拒绝，claim提交失败不系统操作、重载预算保留 | Android AppInfo/列表往返 NOT_TESTED |
| 慢广告有进展 | UNIT_PASS：模拟180秒回执成功且不产生重开证据；300秒绝对上限 | 真实广告 NOT_TESTED |
| 无进展广告 | UNIT_PASS：95秒不立刻重开，至少125秒复核提交，原尝试仍UNKNOWN | 原生截图/任务移除 NOT_TESTED |
| 恢复前中断 | UNIT_PASS：持久文件重载身份、UNKNOWN、派发阶段/预算不丢失，写失败无系统操作 | Android SQLite/进程杀死 NOT_TESTED，未杀真实广告 |
| 官方成功/失败/未知、匿名续接 | UNIT_PASS：准确阶梯差异、NO_GAIN加载等待、无身份不得结算、stage预算 | 真实官方恢复 NOT_TESTED |
| ComponentDialog、连续5/5/下一项 | 原MaxBoostCompletion/NativeDialog/DetailState等现有回归 UNIT_PASS，生产Coordinator未改 | 候选连续两项 NOT_TESTED；code43历史DEVICE_PASS |
| 每日官方+1/25限制、UNKNOWN不重播 | 原OfficialAdQuota/DailyAd/H14Durability/SelectionDispatch等回归 UNIT_PASS，账本/调度生产逻辑未改 | 候选官方计数 NOT_TESTED；code43历史25/25 |
| 正常收尾/锁屏/次日调度 | 原TaskFinish/LockScreen/RoutineSequence等回归 UNIT_PASS，相关原生实现未改 | 候选自然运行/物理拔USB NOT_TESTED |

新测试有文件落盘故障注入，但不冒称Android SQLite/SharedPreferences真机进程杀死验收。当前环境无可用Android模拟器；没有为了补测试中断真广告或改装手机。

## 性能与复杂度对比

| 指标 | code43实际基线 | code44候选 |
|---|---|---|
| 截图/视觉请求 | 全轮376次；不是每条正常广告单独计数 | 实机NOT_TESTED；UNIT_PASS普通回执新增探针0；异常最多14次新增按需采样 |
| PSS峰值 | 约120.8MiB | NOT_TESTED |
| Bitmap累计分配 | 约1.63GiB（累计） | NOT_TESTED；只保留一个约1KiB像素样本，不缓存Bitmap |
| 正常PNG编码 | 0 | 无新增PNG代码；实际NOT_TESTED |
| LOW_MEMORY | 本轮新增0 | NOT_TESTED，未部署 |
| 强制ANA重开实现 | 2 | 2，复用其中1处供HOME失败调用 |
| 新增常驻服务/线程 | 原有FGS及既有协程 | 0 |
| 新增控制器/业务状态enum | 原有 | 0 / 0 |
| Home/广告停滞恢复回归 | 历史广告恢复成功/Home入口缺口 | UNIT_PASS；原生回归NOT_TESTED |
| 账本完整性 | code43原54/85/144/25核验 | 原生产SQLite未改、原测试字节不变、故障注入UNIT_PASS；Android实测NOT_TESTED |

没有证据宣称内存已优化。正常广告无新增截图/OCR/Bitmap及轮询；有少量局部判断/初始化，CPU实测NOT_TESTED。异常新增全屏Bitmap仍有短时分配成本，沿既有inspectBitmap及时回收，不把1KiB样本当成总分配成本。重复文本证据与原视觉管线是待观察风险；没有为删代码改动已实测的最大值闭环。

## 产物与下一步

独立code44候选，包com.anapocket.auto、versionName22.0.14-R47、versionCode44。APK SHA256 `b717cea2bd301aabd25f5025d3c0d0703e76817f11e219e1bd0a7598e5a2e47c`；正式证书SHA256 `ab39397d23fca7803fa7cd3e33125874d010874d5641e6ffd044a2a3b55f484c`。APK/source/最小patch/JUnit/报告/原始故障证据独立归档，Git提交及读回SHA以CODE44_VERIFICATION.json和CODE44_NAS_READBACK.json为准。

冻结本候选，先做受控真机验证再决定部署；不继续创建code45。当前手机code43和原自动开关、配置、UNKNOWN、次日调度保持；当前无人值守风险不能因候选UNIT_PASS而标已解决。候选DEVICE_PASS/NATURAL_RUN_PASS均NOT_TESTED。
