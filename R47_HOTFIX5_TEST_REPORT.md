# R47热修复5验收（进行中）

正式22.0.5-R47/code27，APK SHA256 62b93f9b1b9b879c831a0bf095e2d13691e713a20bd743e78eb1318ed3f326dd，原证书保持。系统普通更新02:08:51已完成，实际base.apk SHA已核对一致；04:40恢复无障碍并开始生产续跑。

真实根因：ApplicationExitInfo在01:42:15、01:53:40/50记录助手LOW_MEMORY，重要性125，非已证实Java崩溃。原服务重连仅把Routine标为BLOCKED，没有自动通过旧观看恢复。H4曾已保留原BIKE1km index2成功核对并开下一index3，但再次被系统结束。

本版新增受限恢复：仅同一手机业务日、旧Routine为RUNNING且业务阶段、近2分钟确实LOW_MEMORY，计划仍启用且无风险/手停，重连后才启动原控制器核旧观看；每日最多3次、原run同步claim防重复。用户停止立即持久化；正常服务销毁不当作用户停止。不同日期、旧退出、升级、forceStop、已结束/阻塞状态不自动恢复。不修改旧奖励/计数、不伪造回执、不保证系统永不结束进程。

|检查|状态|
|---|---|
|代码|IMPLEMENTED|
|JVM|UNIT_PASS：435/0失败/0错误/0跳过，62套件，原429测试文件逐字节未改|
|第一次编译|FAILED：nullable安全调用，已修复；第二构建4m22成功|
|Android隔离|NOT_RERUN_FOR_HOTFIX，原110仅历史基线|
|安装|DEVICE_PASS：code27，实际base.apk校验一致，原签名升级保数据|
|低内存后自动恢复|NOT_TESTED_ON_DEVICE|
|官方中断Boost核对|DEVICE_PASS_LIMITED：生产bound_official_resume_confirms_boost_advance核原index3，旧startedAt与原序号保留，未重看|
|全日挑战/25广告/通知/退出锁屏|INCOMPLETE|
|签到奖励/钱包入账|UNCONFIRMED|

当前WALK≤5km coverage已完成，BIKE500m max、BIKE1km中断；汽车/移动不限各100km未完成。保留四ON两OFF/5/5/100/100、ANA00:03及保护23:58，P1独立09:15/09:30及用户现开关。用户全程授权操作并入睡；一次性兜底已改client04:36，届时必须读手机确认03:35后，再判断是否需要恢复。没有改系统时钟/卸载/清账本。原始日志图work私有，用户交付outputs。


## 真实恢复时间与当前结果
02:09至04:36手机停在系统已下载服务列表，Tuffy处于受限制/未启用；这段没有业务续跑，不能宣称03:35准点恢复或持续全自动完成。采集器一直只读保存样本。04:36恢复可操作会话后核真机现场，通过正常HyperOS应用信息底部“允许受限制的设置”，再启用Tuffy服务并完整等待系统风险确认倒计时；没有用adb secure/appops绕过权限。仅Tuffy启用，其他点击助手保持关闭。04:40:11服务真实重连。

04:40:46由APK主按钮恢复RUN_ID RTf3c9914b-5fa4-45a9-bb7c-772dfc067970，挑战RUN_ID CHb4de00d5-c4ed-4d5d-a1a0-c04528abc041。原BIKE1km index3经官方“再開する”恢复流程取得bound_official_resume_confirms_boost_advance，boost1→2；没有把之前基线100不变直接认noGain。随后仅观看未开始的index4/5/6，04:42已boost5，原index3未重开。挑战奖励仍为出行完成后的预期值，非钱包到账。

单次03:35后兜底已实际接续并删除tuffy-r46；tuffy-00-03持续监听保留。本日WALK范围已完成，BIKE继续处理其余范围，汽车/移动不限100km及每日25尚未完成；不能因一项升满结束采集。H5低内存后自动重连的真实触发仍NOT_TESTED，不人为杀正在播放的广告制造测试。

系统授权参考：[Android官方受限设置说明](https://support.google.com/android/answer/12623953)。本机具体入口以实际HyperOS页面为准，证据私有保存在work/R47-midnight-watch。
