# Tuffy H9实际故障专项调查与H10最小修复

调查使用正式H9/code31真实日志、正式导出账本、系统通知记录、Activity/Window/无障碍/闹钟/退出原因，以及H9源码与冻结ZIP。手机业务日期为2026-10-31 JST；本机电脑日期不同，不据电脑时间改业务日。

## 真实时间线

完整时间线见 evidence/R47_hotfix10/investigation_timeline.json。00:03:00.096自然闹钟启动RT5c9b26ec-2e7f-401c-9f44-299700ad6081；00:08:15.579签到阶段startup_ad_timeout_300s受阻。签到UNKNOWN始终保留。04:40之后属于开发恢复，不能冒称整夜自然无干预通过。四类常规范围05:54:57已全部接取/5Boost。06:00第6条加载失败；06:20核5→5后只新开7；06:22第7条超时未知并停止。07:34安装H9；09:15楽天闹钟遇服务断开，23:28服务连接后补跑楽天，23:29更新受阻通知；23:31用户才点击ANA继续，挑战直接跳过，广告原条恢复受阻。

## A. 楽天受阻通知

正式RUN_ID为CI_RAKUTEN_9d7bd289-4500-48a4-9561-d3fe44df42c6，appId=jp.co.rakuten.pointclub.android。run.json记录triggerSource=alarm、scheduledAt09:15:00、actualTriggerAt09:15:00.389，首次等待原因accessibility_disconnected；服务23:28:15恢复后source=resume取得UI权。通知9300系统mCreationTimeMs对应09:15:00.546，mUpdateTimeMs对应23:29:00.067。

23:28:35与23:28:58两次LAUNCH_FOREGROUND_TIMEOUT，实际前台一直是ANA HomeActivity；随后points、garagara_daily、pickup、bonus、lottery1、lottery2均记录BLOCKED/app_launch_foreground_timeout_after_two_attempts，没有业务提交。最新真实设置楽天为ON（非交付初始OFF），当前日程09:15；牛排ON09:30。dispatch入口会检查enabled及generation；未发现被禁用旧回调获准执行的证据，未修改AlarmReceiver或ANA调度。后台启动为何未进入楽天仍UNKNOWN，不猜测网络或权限根因。

ANA当时只是前台App；真实ANA Routine从23:31:06.548才开始，晚于楽天故障。没有证据证明楽天抢占活跃ANA控制器。此通知对应真实启动超时，不能判为WAITING_FOR_ANA误报。H9确有通知缺陷：缺少来源/发生时间/RUN_ID，最终回调发送没有代次/日期检查，正常抢占后也经过无条件通知路径；Telegram排队缺少P1执行代次，迟到通知无法辨识。

H10只补通知与执行前围栏：ANA保护时先持久WAITING_FOR_ANA，不因等P0而发失败；PREEMPTED_BY_ANA不作为失败推送；新通知明确ACTUAL_BLOCKED/FAILED/NEEDS_USER_ACTION，附原始发生时间、来源、RUN_ID和精确启动超时原因。关闭模块、改变代次、跨日后旧回调不得发送；Telegram仅P1消息加入同样围栏，旧无代次P1排队消息抑制。没有重写P0调度或扩大P1权限。

## B. 每日广告官方5/25

正式账本startedCount=7，completedCount=5，rewardConfirmedCount=5，failedCount=1，unconfirmedCount=1。#1至5属于AD625846aa-3a1a-48b8-92f9-4ff69b9d891f，各officialBefore/After为0→1至4→5。#6 startedAt1793394006025，FAILED/official_error_dialog，officialBefore5、officialAfter=-1、completed=false、rewardConfirmed=false；06:20官方新鲜5→5仅证明该真实加载失败的有限替代条件，原FAILED未改成功。

#7属于AD3b0fa61e-7d82-4471-8d64-b0839bd7f198，startedAt1793395251195，UNCONFIRMED/ad_timeout_95s，officialBefore5、officialAfter=-1，完成及回执均false。06:22故障manifest/节点/截图共同证明前台Google AdActivity黑屏，准确原生android.widget.ImageButton，label=Interstitial close button，visible/enabled/clickable，bounds(580,49)-(720,189)。截图没有Google Play商店工作表遮挡该X的证据，也没有本条多段广告、外部商店或新奖励回执证据；不能套用#4的Pangle→Chrome情况。H8未识别该标签，H9已实现精确标签处理，但H9没有在原现场真实验证关闭及回执。

本次只导航官方广告页而未点观看，新鲜实见仍5/25。官方剩20，不代表可以消费未知条；没有后台增加次数的证据，延迟回执是否曾短暂出现仍UNKNOWN。后续未继续是APK在06:22主动安全停止；与后来的外部监听结束无因果证据。第7条UNKNOWN必须保留；不删除、覆盖、标成功，不用新观看替换未知。

## C. 继续后停在挑战页面

H9可重现条件：当天四类覆盖已完成、签到UNKNOWN、每日原7UNKNOWN；从Tuffy点继续。真实RTe50d582b-3790-4dbd-a2dc-1ed9947e5339日志23:31:06.742 STARTUP=DEFERRED，23:31:06.773 CHALLENGES=DONE，23:31:06.780 ADS=RUNNING。并未无限扫描挑战或重复5/5。子Run AD5cb9a57c-fad9-4f71-b6e8-270fab684313通过唯一正常Recent卡恢复ANA任务，回到HomeActivity/チャレンジ/参加可能。23:31:13.509受阻，正式结果原因existing_official_ad_recovery_not_confirmed。

根因是DailyAdTaskController.recoverExisting：先扣beginBoundRecovery次数，后检查广告现场。找到的是普通挑战页而非原Google SDK，直接false早退；未核新鲜官方次数，TelegramText又无对应文案，导致“执行中止/查看手机记录”与留在挑战页组合，被误认卡住。原7并没有被重看，后续安全停止本身正确。

H10将原SDK/回执存在性检查移到扣恢复次数之前，找不到原现场时只允许在真实ANA HomeActivity下核官方广告页（有界导航、重建页面、稳定次数），不启动签到或观看；计数只保存为独立观察，不改变原UNKNOWN及失败行。次数未变、有增但缺同条回执、跳变、回退、无法读取、原任务找不到、身份/恢复预算不符，各有独立阻塞原因及证据。挑战已覆盖时明确ALREADY_DONE并继续广告阶段。服务连接、取消、日期、UI owner均检查；Recent恢复也绑定原ANA租约。原OfficialAdQuota、同条fresh receipt+exact+1证明、25上限及两次有限恢复额度未放宽。

## 无障碍与外部监听

最新调查系统实际Enabled/Bound均为Tuffy，23:28:15.485日志service connected。历史09:43=null是当时时点状态，不能说当前仍关闭。07:34:45系统PACKAGE UPDATED确实终止旧进程；升级与受限设置关闭有时间相关，但缺少确切关闭事件，系统限制、用户操作等原因仍UNKNOWN。本次23:31 Resume有服务绑定，并非因权限关闭而卡住。H10在DeviceReady、阶段边界及广告观察增加明确accessibility_disconnected有限退出；未通过ADB写secure/appops。

外部collector最终endedAtHost=2026-10-31T01:02:27.5757690Z、elapsed28815.94、requestedStop=false，是其自己的8h计时退出；比用户固定00:39:15Z晚约23分钟，历史超时问题如实保留。随后stop.request及删除tuffy-00-03不控制APK。collector只有只读ADB，无取消闹钟/停止Routine/改计数/清账本路径。当前Android独立ANA00:03、23:58保护、P1 09:15/09:30仍存在，本轮不改定时或新建监听。

## 验证与交付状态

最终构建、测试、安装及许可范围真机结果由最终交付清单列出。单元与Android隔离通过不代表真实Google SDK成功，原7目前无可安全恢复的SDK现场与同条新鲜回执，官方25/25仍BLOCKED。本轮目标允许明确阻塞，不能以假成功换进度。


## 11月1日用户新增黑屏实证与月初偏好

既有00:03闹钟自然触发RTacf24d6e-9b90-4167-b68e-a69f7a4d42a3。00:03:52原生fault四件证明Google AdActivity内仅5个空容器、无WebView/视频/可操作按钮，截图黑屏；签到startedAt1793458986170、AD_PLAYING、completed/rewardConfirmed=false，不等于昨日7号同一种有X现场。仍以真实超时/回执为准。新增最小处理复用BlankGoogleAdPolicy：仅原签到同日真实departure+300s超时UNKNOWN、空容器+全黑+0控件、有效ANA租约且无其它未决daily时允许一次正常Back；先保存单次标记，不重新提交，不把Back当奖励。只有Back之后新官方签到回执才可确认，原身份与失败证据保留。

用户明确偏好：月初奖励在确认是官方Sparkling特典的里程/ポケマネー选择时选择ANAマイル；不对陌生兑换页猜点、不重复领取已选择项。官方FAQ https://www.anapocket.com/faqinsideapp 说明Sparkling特典的1～5日选择及后续积算；当前未保留该月初选择窗口节点，自动识别规则不凭猜测新增。本次未知页面需先核实，禁止以字面出现マイル就泛化点击。

ANA进程4490的logcat补充证据：11/1 00:03:07 Google Ads反复记录“#004 The webview is destroyed. Ignoring action.”，与空容器/无按钮现场一致；WebView为何被销毁仍UNKNOWN。00:03:09的“HTTP timeout:60000 milliseconds”仅为日志中的超时配置描述，不当作已发生网络超时的证明。H9原控制器00:08:08结束为BLOCKED/签到UNCONFIRMED，未手动重复启动。


## H10最终部署与真实复测（手机2026-11-01 00:31 JST）

正式22.0.10-R47/code32已同签名正常Files保数据更新，实机base.apk SHA256=e01c96b51b715967ff51ae33f137753dc52e1af6e21aab67d064f76afd8cbe22。最终构建6m58s成功；474 JVM/68套件，0失败/错误/跳过；H9原462测试源文件逐字节未改。Android隔离原70+40及新增13=123项全部通过，使用独立fixture，没有真实奖励提交。安装后只读比较10/31每日7条、挑战31条及签到与H9导出完全相同。

隔离instrumentation结束时Android在00:22:25/38/55停止目标进程，exit-info明确description=stop com.anapocket.auto due to finished inst；因此00:26服务Enabled但Bound为空、Crashed列表含Tuffy。这是本轮测试产生的断连，不能倒推为10/31根因。无服务时点击继续直接进入权限设置，没有启动Routine/UI循环。正常开关重连并完成10秒确认，00:27:59.083 service connected，Bound仅Tuffy/Crashed空；之后不再跑instrumentation，避免再次断连。

00:28:18手动RT1ca7dfcc-c4af-454f-b79a-2613f0a35e20进入STARTUP，00:28:26.959 BLOCKED/existing_login_task_unavailable_no_replay，约8.5秒终结、FGS停止。实际系统任务1563仍含原Google AdActivity，但ANA卡不在当前可见Recent区域，控制器没有盲扫不可见卡。开发者只做一次正常横向Recent滚动和点原ANA卡，未手动关闭广告；此人工定位不计自动恢复PASS。

再从Main点继续一次，RT2f0c54c7-75da-4893-9d7d-31bffaae43c0于00:29:27.686开始；生产AdTaskReturn找到原卡，保存恢复证据；H10识别空Google黑屏并自动正常Back一次，持久blankGoogleBackAt=1793460570902、blankGoogleBackRunId为本Run。返回的是ANA商品推广弹窗（ナッシュ／特典を交換する），不是签到回执。00:29:43.860明确BLOCKED/login_blank_or_original_scene_unconfirmed_no_replay，00:29:44.803 FGS停止。没有点兑换、没有新广告、没有后台伪造计数。

00:31正式故障包证明签到仍为原RTacf24d6e-9b90-4167-b68e-a69f7a4d42a3、startedAt1793458986170、原startup_ad_timeout_300s，UNCONFIRMED/completed=false/rewardConfirmed=false；仅新增一次返回标记。11/1 daily本机0、挑战0，官方今日次数尚未核对，不能把昨日5/25套到今日。六类开关/公里及ANA00:03、楽天ON09:15 generation7、牛排ON09:30 generation9均保留；Telegram加密配置未改。当前任务空闲明确BLOCKED，不宣称正常清理/锁屏完成。

| 项目 | 最终标记 | 实际边界 |
|---|---|---|
| A 通知代次/等待分类/来源与时间 | FIXED；UNIT_PASS_ONLY；Android隔离PASS | 历史楽天为真实两次启动超时；没有重演真实楽天失败，现场根因仍UNCONFIRMED |
| B 10/31每日广告 | BLOCKED；UNCONFIRMED | 官方最后实见5/25，启动7/确认5；6 FAILED，7 UNKNOWN；剩20但不可重播未知7 |
| B Google原7精确X与同条回执 | NOT_TESTED（真机）；单元及隔离PASS | 已跨日且原7现场不在，禁止重造观看取PASS |
| C 已完成挑战推进/广告只读核对 | FIXED；UNIT_PASS_ONLY；Android隔离PASS | 10/31挑战ALREADY_DONE→ADS场景跨日后未在真机重放；不能声称旧7已恢复 |
| C 11/1继续入口与有界阻塞 | REAL_DEVICE_PASS（明确阻塞、不重播） | 断连前置退出、原任务不可见约9秒退出、原现场恢复后约16秒退出；均非奖励成功 |
| 11/1签到空Google黑屏一次返回 | REAL_DEVICE_PASS（退出黑屏）；奖励UNCONFIRMED/BLOCKED | 曾人工定位原卡；自动Back真实发生，返回商品弹窗无回执 |
| 无障碍当前绑定 | REAL_DEVICE_PASS | 正常系统许可恢复，仅Tuffy；不以测试前Enabled当实际连接 |
| 月初里程偏好 | 已保存；APK窗口自动识别NOT_TESTED/未实现 | 仅确认官方特典时选ANAマイル；本次窗口及领取回执未捕获 |
| 整轮25/25/钱包/最终清理锁屏 | BLOCKED / UNCONFIRMED / NOT_TESTED | 不把单元、界面返回或摘要发送等同业务完成 |

测试A：单元核OFF/代次/跨日/抢占分类；新增Android隔离验证等待仅留pending、ANA释放后pending仍保留、关闭及重新开启拒绝旧结果，没有实机执行P1恢复。测试B：既有JVM测试覆盖Google按钮/多段/商店/回执/上限，新增Android隔离用6 FAILED+7 UNKNOWN结构核官方计数变化仍不能消除UNKNOWN或启动新条、原记录不变。测试C：JVM模拟port核已完成挑战推进、三种Home只读条件、服务断开/取消；Android只验证存储重建保留原身份，不能代替真实进程死亡后端到端UI恢复。D保留H9原462断言并复跑原110 Android断言。真实10/31挑战/首页/广告页面的完整继续流程、各页面进程重启后恢复均NOT_TESTED，不将政策函数或fixture当UI导航PASS。

最终证据：work/R47-H10-investigation/final-diagnostics.zip（私有原图/节点/日志）、real-resume/、post-instrument-exit.txt。公开outputs仅脱敏状态与隔离结果；源码及各报告以H10独立ZIP冻结，不覆盖H6～H9。
