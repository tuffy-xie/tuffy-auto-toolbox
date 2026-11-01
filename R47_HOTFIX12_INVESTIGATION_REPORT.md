# H12 实际故障调查与真机续跑报告

正式22.0.12-R47/code34，APK SHA256 bc37d89472f2aa154e394401c769ce400a8ec017175d46647ec000e158da663a。11:31:29正常Files/PlayProtect更新，实机base一致。2026-11-01 JST：WALK7、BIKE6、CAR6、MOV_ANY5，共24项接取及各5/5 Boost均已确认。挑战147次启动、144次官方回执、3次FAILED、0未确认；失败原RUN/index/start及NO_GAIN证据保留。每日固定广告独立25启动/25完成/25回执，12:38:37官方重新打开入口稳定4样本/3秒复核25→25，没有新每日观看。原签到RTacf24d6e-9b90-4167-b68e-a69f7a4d42a3/start1793458986170仍UNCONFIRMED，不能声称整日全部成功。

## 已证实根因与修复

旧DailyCardDeadline只接受补零2026/11/01，而01:39:16 CH705b84d8-c020-4cbb-aa9a-a890f7af2b88真实卡片是2026/11/1まで，导致今天卡片全部过滤；空扫描coverage又被Main/DailyRoutine当作完成，02:22 RT019bedf7-49b1-4fa2-b2fd-17913eaf9e34跳过挑战并锁死入口。02:40官方仍有符合条件WALK项目，排除“官方确实没任务”。修复后真实发现并完成24项，非伪造非空结果。

另外实际发现：列表底部分类栏折叠，点已选参加可能未展开；先有限回顶再定位分类。DailyRoutine计算剩余分类却传全部，改传selected。BIKE500m04:14已获得官方接取回执，但旧Fyber Activity标签把完整原生回执误识别为广告，04:15误超时；新增原身份和原始官方节点绑定核对，11:06原观看确认且未重播。中断routine没有finishedAt时首页误显示开始，按已有当日记录显示继续。

| 源码（相对于原工程） | 实际修改 |
|---|---|
| app/src/main/java/com/anapocket/auto/DailyCardDeadline.kt / ChallengeDetailReceipt.kt | 以数字年月日精确核对今天；兼容2026/11/1，不能用截止日期≤今天。 |
| app/src/main/java/com/anapocket/auto/ChallengeScanPolicy.kt / ChallengeScanStore.kt | 独立8态、双列表完成证据、范围校验、扫描审计；旧coverage或空结果不能授权完成。重置仅派生投影。 |
| app/src/main/java/com/anapocket/auto/AutomationEngine.kt | 参加中仅進行中今日移动任务；排除昨日PROCESSING和历史待领奖。有限回顶再找分类，原生切页及完整图标核对。 |
| app/src/main/java/com/anapocket/auto/DailyRoutine.kt / AutoAccessibilityService.kt | 仅向挑战模块传入真正剩余分类；手动重扫为挑战独立流程，不新增签到或每日广告。 |
| app/src/main/java/com/anapocket/auto/MainActivity.kt / activity_main.xml | 首页始终有继续／重新扫描／查看结果；记录页安全重置。已有中断记录即显示继续，不依赖finishedAt。 |
| app/src/main/java/com/anapocket/auto/NativeChallengeReceiptPolicy.kt / AdUiClose.kt / ChallengeBoost.kt | 完整前台官方Boost回执优先于过期SDK Activity事件；真实SDK节点仍阻塞。迟到初次接取须原身份、原观看REWARD_CONFIRMED节点、稳定当前回执、唯一pending共同成立，不能认领匿名面板。 |
| app/src/main/java/com/anapocket/auto/RoutineSummary.kt / FaultStore.kt | 导出真实扫描状态和审计，不把未扫描、失败、阻塞写成完成。 |

## 完整调查时间线

| JST | 实际事件与证据 |
|---|---|
| 02:32 | H11正式账本：daily25启动/25完成/25回执/0FAILED/0UNKNOWN；挑战空；原签到RTacf24d6e-9b90-4167-b68e-a69f7a4d42a3/start1793458986170为UNCONFIRMED。 |
| 02:55 | H12首候选a5b5...安装；494 JVM，10 Android隔离。测试结束造成Tuffy停止，与业务故障区分。 |
| 03:00–03:03 | 首真实重扫RTd1afb9ea/CH39ed9ff8原手势切页失败，记录SCAN_FAILED，零新广告；保留恢复入口。 |
| 03:07:52 | 实机安全重置扫描投影，四类NOT_SCANNED；25/25和所有账本保持，未启动业务。 |
| 03:16–03:19 | 第二候选4d23...安装、496 JVM/71套件/10隔离；正常恢复仅Tuffy无障碍；新重扫RT25604710-0bb2-409e-a6cb-d4d6fa5d1f11/CH1229759c-7845-4aa6-a119-5994cb831093。 |
| 03:19–03:21 | 原生参加中→进行中、参加可能；真实发现当天500m并接取，官方接取回执确认。 |
| 03:32 | 元気5km原index3/start1793471451075实际Fyber黑屏、无可操作节点，95s UNKNOWN。不是Google。自动AppInfo结束流程未通过保护，未强停，根因UNKNOWN，不冒称自动恢复PASS。 |
| 03:36–03:37 | 开发者按既有授权正常系统结束ANA/重开一次，生产原任务再開核到NO_GAIN后原index3保留FAILED+officialNoGain+原RUN/start/previousFailure，才新index4；此项7启动/6确认/1FAILED、Boost5/5。必须明示人工辅助。 |
| 03:58 | WALK7项均接取+5Boost确认；分类折叠造成BIKE切换SCAN_FAILED。 |
| 04:10–04:13 | 第三候选72504a80...安装，496 JVM/10隔离，正常无障碍恢复。RT108e02f4-fa81-47c3-ae0e-da6d3e115c79/CH303a7772-1758-47a6-867c-4deabd9f14c6跳过WALK，真实进入BIKE。 |
| 04:14–04:15 | 原BIKE500m已出现官方接取回执，过期SDK标记造成误超时；账本安全UNCONFIRMED，自动重开未成功。无重复观看。 |
| 04:54 | 当前会话只读段live-02按3600秒边界结束；业务04:15已BLOCKED。不声称后续持续观察，也不创建旧8小时任务。 |
| 09:15/09:30 | 楽天和牛排按自身配置自然运行，彼时ANA早已BLOCKED。独立RUN，未改ANA账本。楽天bonus、牛排darts仍UNCONFIRMED，未重播。 |
| 10:42–10:45 | 用户继续；新当前会话有界只读段live-03。正常冷开ANA出现官方再開する；开发者点击一次恢复原接取面板（无新广告）。旧Continue复现unconfirmed_challenge_requires_evidence_review，未重播。 |
| 10:48 | 完整正式导出before-native-receipt-upgrade.zip保存8行挑战、25 daily、原signupUNKNOWN及独立P1账本。 |

| 11:03–11:06 | c6原生回执版正常同签名更新；504 JVM/72套件、10隔离；六组升级前后完全相同。原BIKE500m精确身份和原始官方回执核对成功，不重复初次观看。 |
| 11:08:55 | BIKE500m各5Boost确认。 |
| 11:12:31–11:13:04 | 真实LOW_MEMORY后A11y自动重连，同BIKE1km原index3/start1793499146347恢复，官方100→150；一次自然自动恢复真实通过。 |
| 11:16 / 11:21 | 连续LOW_MEMORY再次杀死排队回调，claim保留，3/3保护未放宽；仍未修。 |
| 11:22–11:24 | ANA原生ANR；开发者正常确定关闭并Launcher重开一次，恢复响应。内部原因UNKNOWN，不能断言SDK泄漏。 |
| 11:31:29–11:33 | 最终bc37首页修复正式安装，504/72零失败、最终10项隔离；正常恢复仅Tuffy无障碍。原BLOCKED无finishedAt现场正确显示继续／重扫／结果，DEVICE_PASS。 |
| 11:33:32 | RTe0556439-b053-4440-bb42-dbbcad787623 / CH8685c570-eb42-4337-83a3-772fefb86b7c继续，自动跳过WALK，完成余下BIKE、CAR，再入MOV_ANY。 |
| 12:15:17–12:16:53 | MOV_ANY10km原index5/start1793502916863实际Unity FullScreenWebViewDisplay，只有playable/Privacy/打开商店，无真实关闭控件；95s超时UNCONFIRMED，未点击商店作关闭。 |
| 12:16:55–12:17:08 | 自动AppInfo重启第三次claim未通过保护、未执行结束；自动重启仍NOT_PASSED，不能冒称修复。 |
| 12:19–12:20 | 开发者正常系统结束ANA并Launcher重开一次；官方中断Boost提示保留。真实点击重扫被UNCONFIRMED保护拒绝，不清账、不新RUN。 |
| 12:21:16–12:22:13 | RTe346a9c7-d32d-412b-8c6e-cf1890e4d9f0 / CHd0ae3aa3-9fec-4e2d-996e-01ea1808c819生产自动再開。官方NO_GAIN原index5保留FAILED+原身份/previousFailure，随后新index6/7确认，MOV10达到5/5。 |
| 12:27:51 | 官方参加可能今日MOV20/30/40km真实可接，有限完整图标定位成功，开始剩余任务；非从缓存制造任务。 |
| 12:33:50–12:34:17 | MOV40原index2/start1793503932610不透明画布超时。生产自动进入系统最近任务、精确定位未锁ANA卡、移除该任务后正常重开，原官方再開提示核NO_GAIN，原条保留FAILED，才启动新index3；本次无人工代找卡。Recent路线REAL_TASK_PASS一次，不能等同AppInfo强停修复。 |
| 12:38:08–12:38:45 | 四类双列表核完，24项各5Boost；daily官方25→25；结果通知已保存并posted，自动Home及锁屏确认。原签到仍待核对。 |
| 13:24 | live-05只读段按3600秒边界结束，217样本，androidStateChanged=false。业务已于12:38结束；没有旧8小时监听或新长期自动任务。 |
| 18:02 | 用户要求WorkBuddy交接；业务空闲时正常Records正式导出核24行，升级前9行逐字节保留，settings/checkinConfig/daily/login/P1均相同。A11y仅Tuffy Enabled+Bound、Crashed空。 |

## 实际终态及边界

2026-11-01 JST：WALK7、BIKE6、CAR6、MOV_ANY5，共24项接取及各5/5 Boost均已确认。挑战147次启动、144次官方回执、3次FAILED、0未确认；失败原RUN/index/start及NO_GAIN证据保留。每日固定广告独立25启动/25完成/25回执，12:38:37官方重新打开入口稳定4样本/3秒复核25→25，没有新每日观看。原签到RTacf24d6e-9b90-4167-b68e-a69f7a4d42a3/start1793458986170仍UNCONFIRMED，不能声称整日全部成功。

配置以手机为准：徒歩ON5km、自転車ON5km、汽车ON40km、移动不限ON40km，新干线/飞机OFF0；ANA00:03、楽天ON09:15 gen7、牛排ON09:30 gen9。不是旧50/100。参加中只查進行中属于今天任务日的移动任务，排除昨日PROCESSING及历史待领奖，不用≤今天判断。

09:15/09:30 P1自然运行发生于ANA已BLOCKED之后；楽天bonus、牛排darts仍UNCONFIRMED，未重复消费。原10/31的每日6FAILED/7UNKNOWN不跨日重播。24项接取与Boost不是实际移动达成或钱包到账。

人工辅助明确披露：正常安装/系统许可/点击恢复入口；03:36、12:19正常系统结束ANA并重开；10:45点击官方再開する；11:20普通入口续跑；11:23正常关闭ANR并重开。不能宣称自然无人干预整链PASS。MOV40的Recent自动恢复和11:12自然LOW_MEMORY同条恢复分别真实通过一次；AppInfo自动结束仍NOT_PASSED，11:16/11:21连续低内存使排队回调丢失仍未修，内部ANR原因UNKNOWN。未放宽3次保护。

原始故障包、节点、截图、exit-info和只读样本保存在work/R47-H12-challenge-rescan；交付outputs仅必要脱敏证据，不含token、签名私钥。完整测试见R47_HOTFIX12_TEST_REPORT.md，真机汇总见R47_HOTFIX12_REAL_DEVICE_REPORT.md。
