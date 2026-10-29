# ANA Pocket Auto R37 — 最新真实测试报告

日期：2026-10-27；业务日期及计划时间均使用 Asia/Tokyo。版本：12.0.0-R37 / versionCode 12。

## 最终结论

每日固定25次的独立管理、持久化、状态识别、主界面、调度和通知已实现。67项JVM单元测试通过；正式R37的真机隔离存储、模拟交互、失败恢复通过。真实Five SDK的一次广告由APK主界面触发并全自动完成：官方观看次数3/25→4/25，新奖励回执出现，独立刷新官方余额后3,234→3,284，实际到账50积分。

**这证明一条真实单次链路，不证明25次真实循环或全部SDK均可靠。** 本轮没有执行挑战接取或Boost，没有清除正式任务记录。

| 项目 | 真实结果 |
|---|---|
| 代码实现 | PASS：固定25、JST日桶、点击前持久化预留、分计数、独立控制器及原生调度 |
| JVM单元测试 | PASS：67 tests / 0 failures / 0 errors / 0 skipped；原有断言未削弱 |
| 真机受控测试 | PASS：25条隔离记录后拒绝第26条、幂等、并发、损坏、写入失败、进程kill恢复、模拟UI及通信错误 |
| 真机真实广告 | PASS仅限一次Five SDK完整自动交互，绑定下方RUN_ID及debug APK哈希 |
| 真实奖励 | PASS仅限该次：新ANA回执+官方观看计数恰好+1；官方余额独立核对实际+50 |
| 无持续ADB本机调度 | PASS：正式R37 16:45:22.221 JST闹钟通知在电脑ADB server关闭期间触发，无广告启动 |
| 正式R37设备重启 | PASS：实际重启后4条记录完整、4/1/1/3不变，无障碍自动连接，09:05 JST闹钟重建；boot_id=0847b640-6620-4cd5-b16b-1d7027ed0be7 |
| 真实连续多次/25次、实际跨午夜 | NOT TESTED；隔离计数和JST边界逻辑已通过，不能代替真实长测 |
| 物理拔USB后的完整广告任务 | NOT TESTED；本轮断开的是ADB服务，USB电缆仍连接 |
| 其他SDK无标签关闭图标 | 未完成覆盖；未知情况有界等待后UNCONFIRMED停止 |

## APK与可复现来源

- 正式APK：ANA-Pocket-Auto-R37-release.apk，3,492,059 bytes。
- SHA256：a52a6328d3ede56a462a52eea69b110ce59de7dc83d4fef30c9a9dcbb0148955。
- 受控测试APK：ANA-Pocket-Auto-R37-controlled-tests.apk，SHA256：6b65c0af6aba857fc6c09131dbcdb8dee9d6701bdb915e3e1c3d3a7a26328441。
- 真实单次测试debug APK：9f940d90da3f547c9e6769330a952e3e3cb55d87fe0789919e08acb4955996ce，保存在evidence/R37/R37-five-single-probe-debug.apk。正式与debug使用相同业务源码、相同签名；单次限制入口和外部诊断仅debug存在。
- 正式包签名v2验证通过；证书SHA256为ab39397d23fca7803fa7cd3e33125874d010874d5641e6ffd044a2a3b55f484c。正式DEX含AdCloseIcon及手动恢复保护，不含DebugReceiver。
- Redmi 12C / 2212ARNC4L / Android14 / HyperOS1.0；ANA Pocket 2.3.4(code605)，us.ana_pocket.ANAPocket。
- 在已有R32/R35/R36工程上审计修复，没有新建工程；R36历史证据保留在evidence/R36。

## 本轮真实自动广告

RUN_ID：ADbfb9c177-8181-4bb7-8f73-529473458806，第4条观看记录。来源是真实ANA Pocket及com.five_corp.ad.AdActivity，不是测试广告夹具。

- 16:31:09 JST：从APK主界面长按单次测试入口启动，保留前三条未确认记录。
- 启动前官方观看次数3/25；在点击观看前同步持久化第4条AD_OPENING记录。
- 16:31:16：AD_PLAYING。
- 16:31:32：在实际可点击ImageView中识别白色叉号，记录ADCLOSE visualCross=true，随后AD_CLOSE_AVAILABLE。位置取自当前节点，不猜测屏幕坐标。
- 16:31:35：捕获本次新出现的ANA「報酬をゲット！」回执，REWARD_CONFIRMED。
- 16:31:37：关闭回执后AD_RETURNED；官方观看计数从3变4，才产生AD_FINISHED。
- 16:31:39：本轮停止，detail=controlled_run_finished；没有启动第5次。
- 自动执行期间没有人工关闭、返回或点领奖。运行结束后独立刷新官方「現在の所有ポイント」：启动证据3,234，刷新后3,284，实际+50。程序当前以同次新回执确认奖励结果；逐次自动余额审计尚未实现，本次到账由真实界面另行核验。

原始截图、各状态UI树和结果JSON：evidence/R37/attempt4/；完整判定：attempt4_verdict.json；到账截图：attempt4_official_balance_refreshed.png。

## 当日真实账本与恢复策略

JST 2026-10-27：started=4、completed=1、rewardConfirmed=1、failed=0、unconfirmed=3，剩余预算21。started是点击前的保守持久化预留，写入失败不能点击。

1. AD1793030469265、AD1793059500103：旧版本缺少可靠证据，启动次数保留，仍为UNCONFIRMED。
2. AD706411f7-6900-4224-9937-a0b7b7c034cb：首次R37调试包dafe057b…ec51d9进入Five广告，但无标签叉号未识别，95秒超时后保存UNCONFIRMED并停止。失败现场先归档，再修复单一关闭识别根因。
3. 该超时广告在结束后由开发者人工清理，后来出现回执和积分；它不是自动PASS，正式记录没有回填成功。证据attempt3_result_note.json。
4. ADbfb9c177-8181-4bb7-8f73-529473458806：新RUN_ID的新一次观看，自动完成且奖励回执确认，旧三条记录不变。

自动调度遇到UNCONFIRMED仍停止，不自动重发。显式手动开始可以只新增一次：必须是全新RUN_ID、旧记录全部终结、官方入口及计数稳定、未达25；官方上限/明确报错、在途记录和同RUN_ID均不能绕过。没有重置日期或计数来取得PASS。

## 67项单元测试

AdCloseIconTest 5、AdInteractionTest 16、DailyAdTaskTest 9、CardFilterTest 10、CardsTest 10、DetailStateMachineTest 10、DistTest 7。原始XML：evidence/R37/junit/。

新增叉号测试使用真实屏幕中可访问节点范围的原始像素，保留PNG及取样来源哈希；加号、箭头、空白、无效像素均拒绝。初次JVM读取ImageIO不适配Android测试编译环境；改为同一图像的原始ARGB夹具，识别断言不变。首次编译失败记录已保留。

## 正式R37的真机隔离测试

所有夹具使用r36_fixture_命名空间，与正式日账本隔离；名称沿用历史代码，不代表本轮仍测试R36。

- 存储套件：DEVICE-FIXTURE-1793086688000 / PID29901。25/25/25后第26条被拒绝；重复回执幂等；另一日期桶为0；损坏记录阻止启动；注入commit=false不返回预留；双线程仅一个在途。
- 原生UI成功：DEVICE-FIXTURE-1793086694592 / PID30056；实际无障碍操作测试页面，播放→关闭→模拟回执→计数0→1。
- 原生UI通信错误：DEVICE-FIXTURE-1793086700808 / PID30604；FAILED，started1/completed0/reward0。
- 进程kill：seed-kill故意杀死测试进程，系统Process crashed为预期；新PID30765 / DEVICE-FIXTURE-1793086708022恢复同一UNCONFIRMED并拒绝第二次预留。
- 正式账本快照：DEVICE-FIXTURE-1793086709058 / PID30818，仍4/1/1/3，成功记录的官方计数3→4。

模拟回执没有真实ANA积分。以上受控测试不能代替上方真实广告证据。原始输出为instrument_suite/ui/ui-error/seed-kill/recover/snapshot.txt。

## 调度、通知与环境问题

- 保留用户每日09:05 JST配置；AlarmManager精确闹钟和WorkManager备份均在Android本机运行。
- R36普通APK主界面90秒自检已在电脑ADB关闭期间于13:31:25.711 JST触发，记录不变。
- 正式R37首次隔离自检未收到回执，保留诊断文件；系统后台启动管理中助手开关实际为false。
- 只对「ANA 自动助手」开启后台启动并确认checked=true；同一正式APK、同一隔离调度方法重测，于16:45:22.221 JST实际触发AlarmReceiver，通知9001标题「本机调度自检已触发」。期间PC adb server关闭，未启动广告或挑战。
- R36还发现shell临时悬浮窗授权在重启后被还原；改用系统Settings开关授予，第二次设备重启后仍allow。正式R37实际重启后悬浮窗仍allow、无障碍自动绑定，16:48:33 JST收到BOOT_COMPLETED并排程。重启后正式快照DEVICE-FIXTURE-1793087347125 / PID13089，4条记录逐条一致。
- instrumentation/uiautomator会改变普通无障碍服务生命周期，真实广告运行期间没有使用它们；普通APK自身诊断通道用于现场留证。
- USB安装提示会倒计时拒绝；在已观察的AdbInstallActivity上接受明确「继续安装」，没有关闭系统验证，没有卸载正式助手。

## 尚未完成

1. 其他广告SDK及不同叉号样式的真实覆盖；当前Five样本可验证，未知界面有界停止。
2. 真实连续多次、完整25次和实际跨午夜；25硬上限/日桶已经隔离测试和JST单元验证。
3. 物理拔线后的完整广告任务；本轮仅能证明无需持续ADB服务。
4. 自动逐条刷新并审计积分余额；当前新官方回执与完成计数分开记录，本次另有真实+50到账证据。

挑战接取、Boost、挑战详情诊断保持暂停。下一步只扩展每日广告验证，不清空未确认记录。


正式R37已安装在设备，重启后base.apk SHA256与交付文件完全一致。受控测试应用已移除，正式助手及记录保留。
