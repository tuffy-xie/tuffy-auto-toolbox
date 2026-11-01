# R47 H8 每日广告加载失败核对测试报告

22.0.8-R47/code30，构建已成功、已同签安装。APK SHA256 c55573ca9d013216e497d9039670952edfec785106ecf241c8dd6db9a7e80de7。保留H1-H7。H7候选29未安装，不混算真实自动浏览器返回。

真实问题：四类出行范围已完成；每日广告#1-5官方回执确认，第6条06:00:08收到原生AlertDialog“広告表示に失敗しました／アプリの再起動をお試しください”，停止在官方5/25。第6条FAILED、startedAt1793394006025保留，非未知观看。另第4条Pangle转Chrome由开发者正常Back一次返回，05:59:36同条官方回执确认，不冒称自动返回通过。

最小修改：独立load_reviews证明账，不修改原FAILED或回收序号。仅相同run/index/date的故障manifest、原生alertTitle/message精确加载失败节点、原FAILED official_error_dialog及30秒冷却后重建官方广告页面稳定次数仍等于原officialBefore，才核对无收益并有限补齐；最多2次替代，绝对最多27条启动，官方25/25仍目标和上限。未知、泛网络错误、计数变化、跨日、证据不全、第三次不放宽。新证明后的失败不冒充奖励，在Routine完成守卫仅作为已核对失败排除，任何未知仍阻塞；旧OfficialAdQuota与RoutineAdCompletion断言不改。真实中途加载失败可在原run有界核对后继续，所有延迟可取消。

|检查|状态|
|---|---|
|实现|IMPLEMENTED|
|构建/455单元|UNIT_PASS：455项/65套件0失败错误跳过，旧447测试未改，构建3m47成功|
|Android隔离|NOT_RERUN_FOR_HOTFIX，历史110不混入|
|正式安装|DEVICE_PASS：06:17:29，实机base.apk SHA相同，原签名包名和数据保留|
|旧第6条核对及补齐|DEVICE_PASS_LIMITED：06:20:33原生证明+新鲜5→5核对，原FAILED保留；仅新增#7，最终补齐回执继续观察|
|自动Chrome返回|NOT_TESTED（仅人工同条恢复已证实）|
|四类出行|REAL_TASK_PASS_LIMITED：官方接取/5Boost/范围覆盖完成，非实际移动达成或钱包入账|
|每日25/通知清理锁屏|INCOMPLETE：官方5/25|
|真实钱包|UNCONFIRMED|

配置保留四ON5/5/100/100、长途OFF0、ANA00:03/保护23:58，P1独立09:15/09:30及用户现开关。原自然签到UNKNOWN不重播。低内存自动恢复未发生，NOT_TESTED。单次监听仍ACTIVE、原8小时截止不变，原始图日志work私有，用户产物outputs。

06:19:46正常系统设置恢复仅Tuffy；06:20:03通过统一继续按钮启动RT5877cc13-0d2b-40f6-bbef-0673dccd0c17，四类出行直接DONE且未重复观看，签到UNKNOWN保留。每日新子Run AD3b0fa61e-7d82-4471-8d64-b0839bd7f198，06:20:33新鲜官方5→5，ADLOAD_REVIEW核原run的index6并保留FAILED和startedAt；06:20:52仅新增index7播放。原00:03闹钟/23:58保护、09:15/09:30独立闹钟从系统注册再次核实未改。

06:22新事实：补齐新index7确实启动，但遇到另一真实Google黑屏，native ImageButton标签Interstitial close button不在既有识别，95秒后UNCONFIRMED；该条不是加载失败，不可据H8证明补看。原load-failure核对已真实通过，但整日未完成，待单独H9 SDK标签/保留失败前缀后的同条恢复修复。
