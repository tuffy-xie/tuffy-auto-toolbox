# Tuffy H14 AppLovin P0：空白交互假设核查

**BLOCKED。本轮没有安全点击空白、没有出现真实X、没有恢复新一轮Routine。** 手机仍code44。今日19项已接取、18项5/5、111条挑战回执（92次Boost确认）；每日本机0，官方次数NOT_TESTED。当前CAR40第3次Boost广告仍UNKNOWN；另有历史3条UNKNOWN保留。不能将11月3日24项/144条合并为今天结果。

## 新鲜真机证据与一次点击预算

- 08:55:25、09:07:03 JST重新采集真实屏幕、Activity/Window、任务、退出原因、运行日志和调度。当前仍为ANA包内AppLovinFullscreenActivity，ActivityRecord8501d7f、task1687、PID7147，与原广告相同；当前和业务日均11月4日，没有跨日。
- 一次性只读节点采集取得42个当前ANA节点，窗口6180、范围(0,49)-(720,1650)。`end`节点可见、启用、可点击，范围覆盖整个广告窗；其后代`endBtn`为可点击PLAY NOW。绿色空白仍在该全屏交互层内，无法排除推广跳转。**不能据此断言点击必定跳商店，也不能证明点击只会唤醒X。**
- 当前可访问的正常Skip/Continue/Close/X数量0；当前画面也没有X。1/3是素材中的游戏标签，不作为可信广告分段/倒计时。当前SDK额外原生容器GONE且0×0，其用途没有被证实，不能称其为已识别的隐藏X并强点。
- 探索性点击0次（预算最多1次，安全条件未满足）；广告/安装/下载/外跳按钮点击0次；未点击系统返回、强停、移除ANA任务或重装以制造进展。假设状态NOT_TESTED，不伪称“点空白失败”。

## 采集方式

未使用默认会抑制其他服务的uiautomator dump。临时只读采集器按Android平台shell采集方式，以`FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES=1`连接，读取一次ANA节点并在finally断开。没有输入/点击/启动业务/账本API，不安装APK，不常驻。采集前后Tuffy PID均1900，Enabled+Bound保持；原生业务日志逐字节相同，没有服务重连或新任务。参考[Android UiAutomation官方说明](https://developer.android.com/reference/android/app/UiAutomation#FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)及[AOSP shell wrapper](https://android.googlesource.com/platform/frameworks/testing/+/89f6117/uiautomator/library/src/com/android/uiautomator/core/UiAutomationShellWrapper.java)。

## 实际调用链与停止原因

`ChallengeBoost.watch`已持久派发原attempt → `AdUiClose.isAdSurface`通过AdSdkWindows识别AppLovin → `identifyClose`没有观察到安全控件 → `AdInteraction.awaitReceipt`默认95秒分段到期。`stallProgressKey`严格要求原已验证的playable/application-canvas结构，本广告无application-canvas，因此没有可信复核key，返回UNCONFIRMED/ad_timeout_95s。`recoverOpaque`不满足已验证画布条件，DailyRoutine的StalledAdRestart又因活跃SDK＋未决广告拒绝重开，最终03:17:32 Routine BLOCKED、FGS停止。

首个可证实的失败返回在`AdInteraction.awaitReceipt`；当前结构未获得可用关闭/恢复证据是覆盖缺口，不能从95秒超时推出广告失败，也不能从SDK进程仍响应推出奖励已完成。为什么SDK/素材没有给出可用关闭入口，根因尚未证实。当前没有可见X漏检证据，不能直接套Google Skip规则或把PLAY NOW当继续。

保留既有Home、有界画布恢复和Google/Pangle能力。原正常画布允许的一次受控唤醒不自动授权本AppLovin结束卡。本轮产品代码0改动，没有新控制器/状态机/服务/常驻线程、没有正常路径新增截图。没有足够证据支持安全的新AppLovin输入策略，因此未创建新候选或把等待时间调整伪称修复。

## 原attempt与实际业务结果

CAR|車、電車、モノレールで40km移動しよう！，2026-11-04；run CH37ba687e-fb6e-4f71-bb5a-8cf9ddd7feb9 / index4 / startedAt1793729755524 / DISPATCHED。之前2/5、150pt，目标下次应为3/5、200pt，均为任务达成后的奖励说明，不是钱包到账。当前没有官方面板，不按数字猜成功。

上轮02:23原生恢复确实完成14项新5/5，今日合计18；原徒歩3km接取通过审计证据＋生产事务只补记一次。原24条已确认对象逐字段保持。当前共有112个唯一attempt、111确认和1UNKNOWN，无新重放；原11/2 BIKE4/MOV30和11/3签到历史UNKNOWN保留。**本轮空白假设核查没有新业务派发、回执写入或Routine启动。**

WALK7项和BIKE6项完整扫描结束；CAR已有5项5/5，40km未结算；MOV_ANY与每日尚未执行。当前SDK独占全屏且不能安全退出，无法将该UNKNOWN局部隔离后导航去独立任务。阻塞依据是全局活跃界面，非仅因历史账本有UNKNOWN。

当前正式终态SQLite逻辑导出NOT_TESTED：没有为导出而切走活跃SDK。保留运行前正式导出及CRC/SHA、原生SQLite FULL提交后的整行日志和841份实际节点/Journal文本；111计数来自这些持久生产记录，非仅Telegram。私有原始SQLite没有root/run-as绕过。

## 回归、版本与性能边界

- 完整JVM回归本轮实际重跑：610项、81套件、失败/错误/跳过均0，159.5秒，UNIT_PASS；JUnit CRC及SHA独立保存。涵盖Google阶段、Pangle、慢广告、Home/画布恢复、迟到回执、UNKNOWN、最大值弹窗、每日上限、换日/调度/收尾/通知既有保护。**没有新AppLovin关闭DEVICE_PASS。**
- 功能提交：15808a006de5a06e93989c1fd8a251627e7c734d；本轮产品diff0。实装code44，22.0.14-R47，com.anapocket.auto。
- APK SHA256：ccf0b824b93e055be30de78187b07cb3dbe53db0251b05674ee54f01c2fa7a17；原证书SHA256：ab39397d23fca7803fa7cd3e33125874d010874d5641e6ffd044a2a3b55f484c。设备安装包哈希复核相同，本轮安装0次。
- 上轮54分18秒原生续跑新增Tuffy LOW_MEMORY0；两个覆盖安装后后台重建进程的LOW_MEMORY另列在OVERNIGHT_RECOVERY_FINAL_VERIFICATION.json，不隐藏，也不归因于截图泄漏。本轮当前只读核查新增退出0，同PID保持。完整PSS峰值/累计Bitmap/CPU优化NOT_TESTED；没有据单元测试宣称内存改善。

## 次日准备、通知和恢复断点

无障碍Enabled+Bound，精确闹钟和后台权限、WorkManager、电池白名单仍在。原自动开关保留；AlarmManager中11/4 23:58保护与11/5 00:03 ANA保留。活跃未决AppLovin尚未解除，次日可靠无人值守准备BLOCKED；下一次自然触发及物理拔USB均NOT_TESTED。未为清理强关ANA、返回桌面或锁屏；FGS无异常残留。

此前03:17手机Bot已主动推送8214，是上轮原生BLOCKED摘要。本轮没有为了制造通知而重启业务。最新调查通知的实际渠道/消息ID单独保存在APPLOVIN_P0_TELEGRAM_DELIVERY.json，不把网页发送冒充手机Bot。

下一安全入口：重新读取手机。如出现可证明属于当前SDK的真实关闭/正常阶段控件，先重新确认位置/预算再用已有安全流程；官方返回后核对原run/index/start和任务日，以生产恢复接口观察原attempt，禁止重放。若仍是全屏CTA结束卡，无安全空白，不点击。若跨日，原UNKNOWN留原业务日，不并入新日。若需进一步修复，应先获得可验证的SDK正常阶段/退出证据，再复用现有一次预算，不创建平行控制器；当前未解决问题须人工或官方安全退出入口介入，不能标稳定版。

H12 main 5be68d6f25474708dcfb424deb9aeaea272a6516及code43、2201631、a650163、892ccaaa原分支/APK/历史备份保持。独立NAS目录：X:/Elements/codex/Tuffy_2026-11-02_0003/H14/applovin-blank-review-20261104-0907。最终归档SHA/CRC、AES-GCM认证读回及Git交付提交见独立manifest；不覆盖旧产物。
