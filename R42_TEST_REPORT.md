# R42 测试报告

## 交付变化

统一首页、记录、设置；固定首页开始/停止操作栏。一次开始依次检查启动遮挡/每日签到，接取并完成徒步、自行车、汽车、移动不限四类常规每日距离任务的5次Boost，执行独立每日25广告，最后发送本机及Telegram汇总。保存5/5/40/40km默认、原用户09:05JST日程、旧挑战/每日广告账本与Telegram加密配置。

## 新增签到与启动处理

根据已安装ANA Pocket2.3.4原生布局核对login_bonus/button_get_point/bonus_point/recycler/button_ok与宣传弹窗image_close+content。签到广告先同步预留独立日记录，再操作按钮；只在观察到SDK广告并返回新官方報酬をゲット！回执后确认。未知保留且当日不再次发起。未看到签到窗口显示NOT_PRESENT，不冒称已领取。启动广告关闭仅是恢复导航，不算完成或奖励。复用旧广告关闭/分段/商店返回/点亮关闭按钮机制，有绝对超时和有限次数。

## 验证边界

手机2026-10-28 JST今日原账本已达25启动、21完成、22奖励提示、4旧未知；本轮绝不补开第26条。已升满四类24项（徒步7、自行车6、汽车6、不限5）。R42验证今日幂等串行路径；R41连续21条及真实+1050积分为旧版本证据，不能算R42重新观看。

真正跨午夜签到领奖广告、新一天从零接取全部四类+25广告以及所有偶发SDK启动广告仍需真实后续窗口；受控回执不是实际账户积分。未改手机时间。自动界面操作需手机亮屏解锁；运行期间保持亮屏，不绕过密码锁。

## 最终构建与真实手机

版本17.0.0-R42/code17。Release SHA256：b3f442969fe339d52739bb49df2b2bc339188d33762a33b325cea0099efc87f6。Debug SHA256：51ca320ab33d8b89f44d54836bda5468265dffd886107baf81beb85365004e3d。手机最终base.apk逐字节SHA与Release一致，非DEBUGGABLE，未声明DebugReceiver。签名证书SHA256 ab39397d23fca7803fa7cd3e33125874d010874d5641e6ffd044a2a3b55f484c，与旧版一致。

最终构建BUILD SUCCESSFUL in 2m32s；248项JVM单元测试，0失败（保留旧211项，新增37项）。11项Android受控测试全部通过，隔离SharedPreferences及注入页面/时钟；对生产广告、挑战、Telegram加密偏好做前后字节核对，未变化。受控签到回执不是实际账户到账。

### 真实导航

19:23:11→19:23:53，NAV1793182991418：实际启动ANA、检查启动状态、使用既有识别进入今日徒步筛选；nativeFilteredNavigation=true。新每日广告0、新挑战广告0、新签到预留false、启动弹窗关闭0（当次未出现）。官方当前列表含10/15km，未误接超5km任务。此前24项已升满不在参加可能列表中，未重新接取。

只读生产导出2026-10-28_1793183049313：dailyPreferencesXml与challengePreferencesXml相对R42前完全一致。Login新增NOT_PRESENT、completed=false、rewardConfirmed=false。该导出在最终release安装前，不能冒称是release运行后原始SP审计。

### 最终正式版普通首页开始

RT执行ID：RTeae679d3-a22c-4adc-b5e3-e77ce15828e2。手机2026-10-28 19:25:09→19:25:27 JST。由首页普通按钮一次点击发起，无debug触发：

1. STARTUP→NOT_PRESENT：当次真实签到弹窗未出现，不认定已领取。
2. CHALLENGES→DONE：现有覆盖WALK5、BIKE5、CAR40、MOV_ANY40，四类24项已处理，直接跳过重复观看。
3. ADS→LIMIT_REACHED：AD4a15d68d-ed52-4d81-9925-f798e40a8e54。每日计数仍25启动、21完成、22回执、0失败、4旧未确认，local_started_limit；没有新观看。
4. RT→DONE，本机结果通知9001确实包含上述签到/四类/广告结果。
5. Telegram设置真实UI显示“推送成功 · 2026-10-28 19:25:28”，属于本次API成功；未向用户重复索取收件确认，不能写为用户已阅读。

重新打开正式版首页，今日流程已处理、上述计数、签到未出现、四类已处理、25上限及记录仍显示；设置默认5/5/40/40与09:05及Telegram启用保留。首页、记录、设置截图保存。原4条未知及自行车500m历史回执缺口保持原状态；本轮没有新增真实积分，旧21条+1050属于R41。

### 本机调度

从正式版设置页面19:26:16预约90秒自检，系统实际19:27:46.895触发AlarmReceiver，通知“本机调度自检已触发/系统闹钟已送达；本次未启动广告”。下一实际09:05闹钟仍2026-10-29 09:05 JST，WorkManager兜底保留，dispatchDaily已接到统一routine。自检证实系统闹钟与通知，不替代下次真实09:05整条任务触发。未做物理拔线、设备重启或息屏全量执行。

UIAutomator只在controller空闲时读取正式版状态，造成无障碍短暂断开并重连（19:26:45→47、19:27:08→10），没有在观看期间运行。此类测试工具重连日志及服务提示不冒称真实业务故障恢复测试。最终服务已连接。

## 仍需真实窗口验证

- 真正次日首次打开的登录奖励：点击原生领取、SDK播放/关闭、新官方回执及奖励；目前只有资源审查+JVM/Android受控验证。
- 偶发启动SDK/宣传弹窗：已实现有界识别/关闭，本次真实启动没出现，不写实际关闭PASS。
- 新真实日0条起的四类全部任务及25广告联跑；旧模块分别有R39–R41实际证据，本轮只验证已处理当天的整合幂等链。
- 所有第三方SDK、真实网络中断、物理拔线、重启、锁屏/息屏长跑。当前需先亮屏解锁，执行期间持有有时限亮屏锁。

## 证据索引

outputs/evidence/R42下：build_and_device_identity.json、build-r42-final.log、routine_controlled_final.json、native_category_navigation.json、production_before_r42.json、production_after_debug_checks.json、release_daily_phase_result.json、release_native_summary.txt、release_telegram_status.json、release_visible_counters.txt、release_scheduler_and_notifications.json、real_device_r42.log及真实截图。单元JUnit XML独立归档。源码不含签名私钥、真实Token、debug认证令牌或其他应用APK。
