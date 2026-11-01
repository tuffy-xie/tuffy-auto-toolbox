# R47 H10专项测试与实机报告

版本22.0.10-R47/code32，同包名/正式签名保留数据更新。最终构建build-r47-hotfix10-device-final.log：BUILD SUCCESSFUL in6m58s。前两次候选分别8m56s、7m1s成功但未安装；新增真实11/1签到空Google黑屏处理后必须以第三轮最终结果为准。

最终JVM：474项/68套件，0失败、0错误、0跳过。H9原462项测试源文件逐字节未改。新增12项覆盖：通知OFF/旧代次/跨日、等待与抢占、真实受阻与服务断开、三种正常ANA页面只读核对、SDK/回执页面保护、计数不一致、UNKNOWN禁止预留、新鲜回执要求、已完成挑战进入下一阶段、取消、服务断开有限退出。既有Google精确原生按钮、95s/300s有界等待、多段广告、商店返回、同条回执、日上限、清理/锁屏原回归均保留。

实机00:17:13已正常Files安装，用户处理Files来源许可，未用shell授权/secure/appops。实际base.apk SHA256 e01c96b51b715967ff51ae33f137753dc52e1af6e21aab67d064f76afd8cbe22，与outputs正式APK一致。正式签名证书SHA256 ab39397d23fca7803fa7cd3e33125874d010874d5641e6ffd044a2a3b55f484c。通过HyperOS正常允许受限制设置、完整10秒确认倒计时，00:19:58仅Tuffy无障碍重新绑定，其他助手均OFF。

Android隔离与许可范围的原黑屏恢复结果在下文补充；在实际结果返回前不宣称通过。USB测试包安装被系统USER_RESTRICTED拒绝，转正常Files+PlayProtect扫描，不改变系统开发者安全设置。

10/31业务：最后官方实际仍5/25，启动7、完成5、回执5、失败1、未知1；第6 FAILED/第7 UNKNOWN及原身份保留。10/31已跨日，禁止为复测重放旧广告；Google原7真实关闭与回执恢复NOT_TESTED。11/1自然签到黑屏是另一条真实观看，待单独验证，不混用两日结果。钱包到账UNCONFIRMED。


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
