# H14 夜间验收与 ComponentDialog 修复结果

结论：PARTIAL / BLOCKED。手机实际仍是 code41；本轮新增 BIKE1km 接取和5次Boost共6条官方回执，总计9项接取且5/5、54条确认，挑战新增UNKNOWN为0。但最大值关闭闭环失败，连续两项完整闭环未通过，未扩大观看范围。code42仅为已提交、已回归、同签名的未安装候选，不能称真机故障已修复或无人值守通过。

## 真实故障与停止理由

手机11/3 02:52:09通过现有继续按钮开始；02:56:15.590保存BIKE1km最后回执及400pt。02:56:18报MAX_BOOST_DIALOG_DISMISS_FAILED:unknown_post_page_no_back_or_relaunch。实际Activity是ANA HomeActivity，Accessibility事件类却是androidx.activity.ComponentDialog。code41仅认HomeActivity，错误排除这个原生弹窗，分类OTHER，Journal的OK尝试为0。故本次不是已经点击OK而关闭失败；证据不能证明code38以前单次OK失效的具体原因。

02:58按用户明确指令，在BLOCKED且没有活跃SDK后，由开发者辅助关闭并重新打开ANA一次。官方出现「ブーストが中断されているチャレンジが存在します。ブーストを再開しますか？」及「再開する」。04:35再次通过原Recent卡只读核对，它仍然不显示唯一任务名、任务日和原广告身份。54条已确认与一个本地完成Journal不能证明这个匿名提示一定属于BIKE1km，尤其存在历史UNKNOWN。没有点击再開する，没有凭400pt或最近任务猜测归属，没有重新播放广告。

这是全局页面的身份阻塞，不能安全开始第二项或独立daily。保留现场并结束本次外部监听，不以反复重启或再点继续来制造进展。每日广告官方X/25未观测，本地新提交0；不能写0/25或25/25。Routine仍BLOCKED，未做正常完成后的清理或锁屏。

## 最小代码改动（code42，未安装）

功能提交：c28ce6cc100c0f26977cd9c4f095692ef391b6f6；前置提交8462015。基线1704b4ea（包含H13保护与code41）。独立分支fix/h14-dialog-host-resume-20261103；H12 main/Tag仍5be68d6，不合并、不推送。

| 文件/函数 | 改动与安全条件 |
|---|---|
| MaxBoostCompletion.kt / nativeHost、nativeShape、classify | 在ANA包、有效前台Window、精确官方文案及原生资源结构约束下接受ComponentDialog/明确原生Dialog宿主；不按金额猜测，不接受广告Activity |
| MaxBoostCoordinator.kt / capture、visible、recover | 宿主识别一致；恢复现有ANA系统任务；原5/5记录与广告摘要必须仍一致 |
| MaxBoostCompletion.kt / resumeShape、resumeIdentity、Flow | 精确中断文案和原生按钮只是候选；必须可见唯一标题、任务日、参加日匹配。匿名提示阻塞；一次续接意图先落盘，进程重启后不再派发；续接后必须回到对应详情才能恢复类别 |
| StalledAdRestart.kt / recover | 原生最大值卡死且本地5/5已可靠确认、无活跃SDK、无未决挑战/每日广告时，沿既有HyperOS应用信息结束运行/核实停止/正常打开；每身份一次、全日最多3次，不提高额度。活跃SDK或未决提交直接阻塞，超时本身不是广告结束证明 |
| DailyRoutine.kt | 最大值专用失败可进入上述有界恢复，然后重新读取持久状态；不增加按钮，不通用盲Back |
| NativeDialogResumeTest.kt | 5项针对性测试；原567项与原测试源字节保持 |
| app/build.gradle | 候选versionCode42，versionName仍22.0.14-R47 |

正常闭环：保存原5/5 → 识别ComponentDialog → 新节点/Window校验 → 原生祖先或最新Bounds受限手势 → 检查实际消失 → 核对详情/列表/Home → 现有类别与今日过滤重新验证 → 扫描下一项。状态保存与最多3次OK尝试的原code41协议保留。

安全重启闭环：仅已结束且绑定的最大值流程可申请原生结束运行 → 确认系统停止 → 正常打开 → 读取原记录和当前官方窗口 → 若有再開する，先唯一绑定再允许一次续接 → 后置对应详情验证 → 原类别/今日扫描。任一步证据不足即BLOCKED。现有匿名实机提示不满足条件；代码不包含绕过。

新开销为少量宿主字符串判定、标志和既有导航Journal字段；未增加Bitmap、截图、常驻服务、唤醒锁或后台两小时重试。没有实机code42内存对比，不能据此量化优化收益。

## 账本与配置保全

最终正式导出CRC通过，0张PNG。原8项整行与升级前一致；全部54条与首次故障正式导出逐字段相等。配置、签到UNKNOWN、每日记录均不变。Nov2正式备份中23项/128记录/126确认/BIKE4和MOV30两UNKNOWN已保留；Nov3导出不包含Nov2私有账本，不能把它说成重新读取了完整私有SQLite镜像。code42未安装，也未操作手机私有账本。

配置按手机实读：四类5/5/40/40，长途OFF，ANA00:03；楽天与牛排当前OFF/gen10，未按旧报告恢复开关。今日签到start1793631793832仍UNKNOWN，不重播。54条挑战确认只是接取/Boost回执，不等于实际移动达成或钱包到账。

## 测试与产物

572 JVM / 76套件，失败0、错误0、跳过0；原567项全部实际执行，117个原测试源字节不变。新5项覆盖真实ComponentDialog、精确再开文案、匿名/错误身份/跨日/UNKNOWN/重复阻断、重启边界，以及续接后详情验证和进程中断后仍强制验证。没有靠杀真实广告做故障注入，也未运行会中断A11y的instrumentation。

最终构建日志build-20261103-053751.log，对应一致功能源码；之前构建过程中源码修订导致接口/测试快照不一致的失败日志保留，不用作最终产物证明。

- APK：Tuffy_22.0.14-R47_code42_signed.apk，50288744字节；SHA256 `092ace51e6794b1366ee1739f420be784efc687af1eb68c040f7238b0c689413`。
- 原正式签名验证通过，同包名com.anapocket.auto；候选未安装。
- 源ZIP：H14_code42_source_c28ce6cc.zip；SHA256 `a90094cc560fe457e67f3c96229e16e8a5090681c1ce92abf96c2bc3c72f5a96`。
- code38/code41原始交付、H12主线和Tag未覆盖。

| 验收项 | 状态 |
|---|---|
| code41正常同签名保数据安装、A11y Enabled+Bound | VERIFIED / DEVICE_PASS，仅安装迁移范围 |
| BIKE1km新增接取及5Boost，原始6回执 | VERIFIED，开发者从现有继续入口启动 |
| code41最大值关闭和接下一项 | BLOCKED，实际失败 |
| code42 ComponentDialog/有界安全续接代码 | CODE_IMPLEMENTED / UNIT_PASS |
| code42原生结束运行、再开、最大值关闭 | NOT_TESTED（未安装） |
| 真机连续两项完整闭环 | NOT_TESTED，未扩大范围 |
| 完整自然无人干预/物理拔USB/跨多自然日 | NOT_TESTED；NATURAL_RUN_PASS未取得 |

## LOW_MEMORY 与内存观察

本夜安装和权限恢复期间确实有两次Tuffy后台LOW_MEMORY：02:44:01.628、02:45:26.317；不是安装PACKAGE_UPDATED退出。不能声称整夜没有LOW_MEMORY。02:48服务重连后至最后核对Tuffy PID5070未再变，02:56弹窗故障没有新LOW_MEMORY证据。02:58 ANA退出是人工授权关闭，不能当自然恢复通过。

只读采样区间2026-11-03 02:55:28 JST至2026-11-03 04:45:28 JST，共45次内存采样；绝大部分时间为BLOCKED空闲。Tuffy PSS采样范围60672–101768KiB，RSS 115060–181028KiB；ANA PSS 297006–689283KiB；系统MemAvailable最低764552KiB。详见MEMORY_OBSERVATION.json。它们不是连续峰值、泄漏证明或截图开关前后的受控比较；code42无真机内存数据。

## 人工辅助、通知与模型额度

人工辅助包括正常Files+PlayProtect安装、受限设置/完整A11y倒计时、关闭code38遗留弹窗一次并核对BIKE500m详情、02:52点击现有继续一次，以及02:58按用户要求关闭并重开ANA一次。不能称无人干预执行。

手机Telegram设置页已实核「推送成功 · 2026-11-03 02:56:20」，对应本次执行的BLOCKED汇总。最终含源码/SHA的补充长汇总另存TELEGRAM_FINAL_SUMMARY.txt；现有App仅有固定测试推送按钮，无任意文本发送入口，当前也无已登录Telegram客户端/连接器，故没有声称该补充消息已发送；未提取硬件加密Token或改通知设置。

用户所说额度是Codex模型使用额度。误加的App两小时重试源码已完全撤销，未安装；App恢复额度和ANA广告上限未改。源码已提交，真机/下一步状态已保存。确实建立过本聊天heartbeat，后因本次全局安全阻塞而删除；不能保证模型无额度时执行，也没有假装安排了额度恢复后的必然启动。本轮结束不是模型额度耗尽。

下一步必须先取得可唯一绑定的官方任务身份或官方可验证的安全解除方式，再决定实机安装候选和两项连续闭环；不得为了这个门槛重复54条或历史UNKNOWN。未满足无人值守条件。
