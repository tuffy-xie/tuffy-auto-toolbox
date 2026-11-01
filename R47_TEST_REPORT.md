# Tuffy 的自动百宝箱 R47 真实测试报告

基线：R46/code21；本轮：22.0.0-R47/code22。业务日期、运行时间以 Redmi 12C 真实时间及 Asia/Tokyo 为准。报告日期2026-10-30。未修改系统时间，未卸载Tuffy，未清除设置、Telegram密文或历史账本。

## 当前结论

- IMPLEMENTED：六类首页卡片直接勾选/取消；原DataStore持久化；执行队列和首页共用选择；新广告提交前的互斥检查；已预留广告只收尾；两个App首页独立勾选与代次防护；正常完成后单App最近任务清理。
- UNIT_PASS：415项JVM，0失败、0错误、0跳过。R46原404测试源文件字节未变；新增11项。最终build-r47-final-candidate.log成功，包含testDebugUnitTest、assembleDebug、assembleRelease。
- DEVICE_PASS：原70项Android隔离断言保留并重跑通过；新40项Android DataStore/SharedPreferences/代次/并发切换隔离检查全部通过。六卡实际点击、队列组合、ON与OFF两次真实设备重启、旧回调拒绝、受控P0抢占、三App最近任务清理均有证据。
- REAL_TASK_PASS：本轮只复核10/30已完成任务和原未确认记录，不重玩飞镖、不重抽Bonus、不重新消费ANA24项挑战及25次广告。R46历史真实业务成功与本轮管理/退出回归分开记录。
- UNCONFIRMED：楽天10/30额外Bonus结果；各钱包独立入账核对。中奖或完成画面不提升为钱包确认。
- NOT_TESTED：本轮新版本自然跨日、整夜Doze、自然00:03全业务；正在观看的真实新广告中取消分类。后者只完成隔离/互斥边界检查，不能冒称真机中断真实广告验收。

## 设备和保留配置

Redmi12C，Android14/HyperOS，无密码滑动锁屏；无障碍仅Tuffy。ANA Pocket 2.3.4/code605；楽天PointClub6.8.1/code416；いきなり！ステーキ2.2.0/code67。两新App已有真实登录后界面，未伪造登录。

|项目|真实基线及当前最终值|
|---|---|
|ANA独立计划|开启，00:03 JST；保护23:58，跨午夜动态计算|
|徒歩|选中，≤5km|
|自転車|选中，≤5km|
|車・電車・モノレール|选中，≤40km|
|移動手段問わず|选中，≤40km|
|新幹線|未选中，原公里0/未设置，不自动赋值|
|飛行機|未选中，原公里0/未设置，不自动赋值|
|楽天|OFF，保存09:15 JST|
|牛排|OFF，保存09:30 JST|

原ANA AlarmManager身份和ana_backup_daily周期WorkManager不变。原WorkManager为24小时兜底，初始延迟12小时，不能将其下一运行窗口误写成00:03精确闹钟；真正00:03由独立AlarmReceiver闹钟负责。未新增其他签到App。

## 六卡与安全边界

MainActivity沿用原小猫/紫金主题与布局：单击选中、再次单击取消；选中边框、未选中淡化；节点同时提供checkable/checked/selected、状态描述。公里保留原值，长途选中但公里0时显示“需设公里”，不会扫描或启动广告。

Prefs.toggleCategory在原DataStore上读取最新值并原子翻转，避免快速双击丢一次。TravelSelectionPolicy为真实控制器和调试队列共同入口；每分类、每卡、每Boost及新广告预留前读取最新值。卡片保存与新广告预留共享互斥边界；若已经预留，允许同一广告安全收尾，此后不再提交该类新广告。取消只写独立DISABLED_BY_USER审计，不删除已完成或未确认历史。原被取消的JOINED记录不再把独立每日签到误判为必须恢复；实际已提交广告仍受恢复保护。

|测试|证据与范围|
|---|---|
|六类各自ON/OFF|P05：真实首页12次切换，节点状态描述和实际存储一致，公里未变|
|全部取消/部分/全部选择|P06：真实队列分别为空、WALK/CAR、四类有距离的分类；新干线/飞机公里0仍不加入|
|重启保留|P11/P13：ON与OFF两个真实系统重启，四类选择、长途取消、公里和ANA时间保持|
|快速切换|P23：真实Android隔离DataStore并发20次翻转不丢失，再翻转一次持久化改变|
|执行中取消|P23：同一预留互斥门禁，取消前只接受一条、取消后拒绝下一条；既有提交不重复。真实新广告取消NOT_TESTED，当天奖励已经完成|
|当天重新选择|P05/P06及前后账本：选择不清历史、不产生新广告；既有确认记录继续保留|
|全取消的独立功能|代码与隔离检查保留每日签到/25广告配置与流程，DISABLED_BY_USER只用于出行步骤；没有重消费当日完成项进行假验收|

## 两App独立开关、调度和恢复

首页原生CheckBox与设置页面使用同一CheckinStore配置。勾选仅注册该App未来计划，绝不立即执行。OFF保留时间、历史，取消自己的Alarm和唯一WorkManager，清该代pending。配置改变递增generation；Alarm/Worker、排队请求、恢复日志和每次UI动作均核对代次。关闭后再开启，旧回调仍失效。配置变更先撤销旧UI操作权，最终动作入口再检查当前配置。

P07：两App同时ON，09:15/09:30各自注册，未因勾选启动领取。P11：ON真实重启恢复两独立闹钟；P12：双OFF后真实internal Receiver受控投递旧gen1，被REJECT；P13：OFF真实重启后两P1无活动闹钟、时间和历史保持。P17/P21读取本机WorkManager唯一工作真实状态，OFF为CANCELLED，ON为ENQUEUED。这些旧回调是受控投递，不冒充取消后OS自然送达的旧Alarm。

独立身份为CHECKIN.RAKUTEN、CHECKIN.STEAK和checkin_backup_RAKUTEN、checkin_backup_STEAK；不改ANA身份。P1不使用allow-while-idle配额，保留同UID原ANA闹钟配额。各App异常/账本/通知独立；共享的只是可抢占UI操作权。

## ANA优先级

原R46仲裁、9分钟普通任务超时与独立看门狗保留。P0直接撤销P1令牌，不等待普通控制器join；清理动作也经过同一操作权门禁。跨午夜保护窗的模型断言保留。P25为最终源码本机30秒readiness闹钟抢占等待中的普通只读控制器：ANA实际获得权=true；观测延迟87ms；旧动作接受=false、动作数0；ANA配置和每日广告计数未变。随后真实ANA原生页面确认成功并清最近任务(P26)。这是测试闹钟和只读控制器，不是真实永久ANR、自然00:03全业务或三闹钟同毫秒测试。

|要求|本轮状态|
|---|---|
|普通完成不改ANA|DEVICE_PASS，前后00:03及账本保留|
|普通等待/卡住时P0抢占|UNIT_PASS + DEVICE_PASS受控等待；未制造正式App永久ANR|
|同刻优先与ANA期间P1等待|UNIT_PASS/原70隔离；本轮自然同刻调度NOT_TESTED|
|旧异步动作|DEVICE_PASS，P25拒绝且0动作|
|普通异常与恢复隔离|UNIT_PASS/原70+新40；配置代次、旧取消不能覆盖新运行|
|重启恢复|DEVICE_PASS，两次真实系统重启，ANA与独立P1开关一致|
|自然00:03、整夜Doze|NOT_TESTED，不承诺100%准点|

## 清理、通知与锁屏

本轮先真实复核牛排已完成任务：正常通知、Home、锁屏成功，但最近任务清理FALSE。P09保存节点后确定单一原因：HyperOS实际卡片是“いきなりステーキ,未加锁”，旧规则仅接受“未锁定”。最小修复增加实际“未加锁”并拒绝“已加锁”，保持精确App名称，不使用清除全部、不清Tuffy。

修复后P14牛排、P18楽天、P26 ANA均实际recentTaskRemoved=true/returnedHome=true。牛排与楽天lockConfirmed=true，稳定窗口2500ms；ANAreadiness测试明确lockAfter=false，不能把它当锁屏验收。牛排移除后开发只读未发现主PID；楽天移除后仍观察到进程。因此生产日志统一PROCESS_NOT_VERIFIED、forceStopped=false，不虚报FORCE_STOP_SUCCESS。主PID不存在也不等于系统强制停止。

Android14普通App的killBackgroundProcesses仅能终止自身；AppTask API也限调用方任务。本轮使用无障碍有界7秒、仅目标App的最近任务移除，再核验Home；P1清理仍可被ANA撤销，清理失败不无限等待。不依赖root、ADB常驻或越权权限。官方参考：[ActivityManager](https://developer.android.com/reference/android/app/ActivityManager#killBackgroundProcesses(java.lang.String))、[AppTask](https://developer.android.com/reference/android/app/ActivityManager.AppTask#finishAndRemoveTask())。验证码/风险或未完成提交现场按原策略保留，不走伪成功清理。

Android通知和Telegram入队发生在清理之前。原Telegram加密配置保留；实际发送结果以本机发送日志为准，不把已提交或API发送成功写成用户已阅读。锁屏失败仍记录失败和通知。

## 业务和奖励

|任务|当前实际业务证据|独立入账|
|---|---|---|
|ANA|R46同日24项升满、官方25/25；本轮不重看，账本回归检查|官方任务/奖励回执有证据，钱包独立余额核对UNCONFIRMED|
|楽天三日课|真实もらう：1ポイントミッション/ガラガラポン日课/PICKUP已达成；本轮只复核|TASK_COMPLETED；SPS预计点数/次日付与保留CREDIT_PENDING或UNCONFIRMED|
|楽天普通くじ|10/30两次已完成，均ハズレ/0点，本轮不重抽|无中奖积分，未伪造到账|
|楽天额外Bonus|10/30原Start/Stop保留，本轮复核UNCONFIRMED/no_replay|无最终结果；等待下一真实日期新鲜页面验证|
|牛排每日飞镖|R46实际1円结果，本轮SUCCESS跳过不重玩|REWARD_DISPLAYED，rewardConfirmed=false，CREDIT_UNCONFIRMED|

CheckinStore读视图标注rewardStage/creditState；旧SUCCESS与原submittedAt不删除、不伪改。新完成/中奖画面不会直接设置钱包confirmed。不同App资产不混算；不做付费、消费、到店、验证码破解或系统改日。

## 物理拔线验收

DEVICE_PASS（短时拔线、正式release、当日完成项复核）：用户拔线后ADB列表为空，接回前用户确认“两项都通知并锁屏”；重连读到屏幕Asleep。正式本机诊断导出P40与P42系统通知记录证明两App各自触发、串行复核、清理及最后锁屏。

|App|保存的验收时间|实际触发/来源|取得UI权|结果|
|---|---|---|---|---|
|楽天|21:05 JST|21:06:39.028 / backup|21:06:39.067，较计划99.067秒|跳过已完成任务，Bonus只复核不重抽，最近任务移除/Home；牛排已等待，按策略不在此时锁屏|
|牛排|21:06 JST|21:07:07.672 / backup|21:07:18.364，较计划78.364秒|先等待楽天释放，已完成飞镖跳过，最近任务移除/Home，21:07:30.975稳定锁屏确认|

这次实际由各自WorkManager兜底触发，不虚报两条精确闹钟准点。未证明原P1闹钟为何未先获得交付；不把原因臆定为网络或系统Doze。日志21:01:57还出现TIME_SET系统广播，本轮没有发送修改手机时间命令，广播来源未证明；计划恢复后ANA仍为00:03。

P41与正式R46生产快照逐字段核对：ANA24挑战、25观看、签到、每日流程记录完全相同；两新App所有原submittedAt/stopSubmittedAt保持，不产生新抽奖或飞镖。P36/P37实机已恢复楽天OFF09:15、牛排OFF09:30；P43活动闹钟只剩ANA00:03及保护23:58，AndroidX系统Job保留原ANA兜底。短时拔线通过不替代自然跨日、整夜Doze或次日新奖励全流程。

## 正式交付和后续

APK：Tuffy_Auto_Toolbox_R47.apk；SHA256 07370f4d780711336b8ab170122f86987e53284071f7de89dabdee4e1285d215，50,157,400字节。原包名com.anapocket.auto，正式证书ab39397d23fca7803fa7cd3e33125874d010874d5641e6ffd044a2a3b55f484c；无DEBUGGABLE/DebugReceiver。升级使用-r保留数据，未卸载。手机实际base.apk已读取，SHA256与交付APK一致（P39），正式包身份核对通过。

完整源码、415项JUnit XML、110项Android隔离JSON、审查证据、报告/四份MD与交付清单放outputs；不含私钥、真实Token、调试口令、会员截图原始包。源代码同步原工程，保留本机签名/SDK配置。R44既有私有GitHub备份不变，本轮未另行推送。

既有一次性后续开发跟进已更新到R47，保留原开发机本地03:45计划，启动后先核对手机真实日期和ANA状态。开发机与手机时钟存在差异，不把开发机计划时刻冒称手机实际触发时间；手机ANA00:03计划不变。Bonus等条件未发生不提前宣称通过；设备不可用时保留NOT_TESTED/BLOCKED。

## 交付时十项审查

1. 六类选择：四常规ON5/5/40/40，两长途OFF0；正式首页已核对。
2. 两App：已实机恢复原OFF09:15/OFF09:30，P36/P37；临时21:05/21:06已取消。
3. ANA：00:03 JST，保护23:58，保持。
4. 独立闹钟：ON/OFF重启及正式临时计划有证据；恢复OFF后无P1活动闹钟，ANA00:03/保护23:58保留（P43）。
5. ANA抢占：最终受控原生闹钟通过，87ms观测延迟，旧动作0；自然午夜整链未验。
6. Bonus：10/30 UNCONFIRMED，未重抽；下一真实日期最终结果NOT_TESTED。
7. 奖励钱包：独立入账UNCONFIRMED，结果展示/预计点数不混同到账。
8. 退出：三App最近任务移除+Home实际通过；进程停止PROCESS_NOT_VERIFIED，未force-stop。
9. 锁屏：修复候选及正式包拔线均有真实稳定锁屏证据；本轮最后牛排lockConfirmed=true。
10. 物理独立运行：短时正式包拔线/两独立兜底/串行复核/本机通知/退出/最终锁屏DEVICE_PASS；整夜Doze、自然跨日与新鲜奖励整链仍NOT_TESTED。
