<!-- H14_CODE42_NIGHT_CURRENT -->
## 最新夜间结果（优先于下方历史code41未安装描述）

code41已正常同签名安装，手机仍code41。真机累计9项接取且5/5、54条已确认；最大值400pt弹窗的Accessibility类ComponentDialog被错误排除，OK尝试0而BLOCKED。人工安全关闭重开后为匿名再開する，无法唯一绑定，未点击、未扩大执行。历史UNKNOWN和54条保全。code42候选已提交、572 JVM/76套件全通过；未安装，连续两项闭环及无人值守仍NOT_TESTED。完整范围、SHA、失败证据与人工辅助见[H14_CODE42_NIGHT_REPORT.md](H14_CODE42_NIGHT_REPORT.md)。H12 main/Tag及原始交付未改、不推送。模型额度≠App额度，没有App两小时重试。

<!-- H14_CODE42_NIGHT_CURRENT_END -->

<!-- H14_MAX_BOOST_CODE41_START -->
## 当前专项：H14 code41 最大值确认弹窗候选（未安装）

从code38快照8e04816f创建独立分支fix/h14-max-boost-dialog-20261103；功能提交79ac34b7aec8320753ad29086683ab81c02b04e4。code39 bcbd11c、code40 45d5b9d保留且未混入。H12 main/正式Tag仍5be68d6，无合并/推送/新稳定Tag。

本次Nov3故障是最终最大值弹窗未验证关闭并落入OTHER通用导航，未发现本轮新LOW_MEMORY。已实现原5/5可靠保存后的任务绑定、当前Window语义识别、原生祖先点击/新Bounds手势、18秒观察/跨进程最多3次关闭、实际页面验证、原类别及今日筛选恢复后继续扫描；失败专用BLOCKED，不重播或通用重开。

最终567 JVM /75套件，新增21，0失败/错误/跳过；原546项全部实际执行、116测试源字节不变。CODE_IMPLEMENTED / UNIT_PASS。DEVICE_PASS、NATURAL_RUN_PASS未取得，Android真实操作和自然运行验收NOT_TESTED。本轮无ADB/手机点击/安装/真实广告，最后实核手机版本仍code38。

候选22.0.14-R47/code41，APK SHA256 d6de4f084d26daa9c1de4cafd71ab6a0047f5b3673b4f691d0381a12966dd834。原正式签名保持；详细范围、失败链、模拟验收和资源开销见[H14_MAX_BOOST_DIALOG_REPORT.md](H14_MAX_BOOST_DIALOG_REPORT.md)。原SQLite事务/广告/UNKNOWN/配置/日程规则未改。code38冻结交付及11月2日两UNKNOWN、11月3日48回执未由本轮操作修改。已有匿名弹窗如无Journal且缺唯一任务名/日期仍BLOCKED，不按200pt猜绑定。

下一步：等待用户明确决定是否安装；未授权前不安装、不触发真实广告、不修改手机数据。牛排误报、MOV30未知及每日25次入口不属于本专项，仍按旧记录待处理。以下code38等内容为历史证据，不代表code41真机通过。
<!-- H14_MAX_BOOST_CODE41_END -->

# H14 验收矩阵（状态不能互相替代）

最终代码候选code38 / 6ea2e3649ca4b565ed7fd356f7540f5a02edf405。546 JVM/74套件零失败、错误、跳过；原517项及115源文件保全。code38正常同签名安装、SHA核实、原账本保全及正确路由保守阻塞已实核。最后核对结果以BUILD_VERIFICATION/DEVICE_VERIFICATION为准。

| 项目 | 已验证范围 | 状态与限制 |
|---|---|---|
| A 点击前退出 | 保存失败不触发手势，原预留保留 | UNIT_PASS；真实广告故障注入未做 |
| B 意图保存、实际点击前退出 | ARMED保留，不推断未播放、不重复点击 | UNIT_PASS；原记录恢复保护保留 |
| C 派发后无回执退出 | DISPATCHED仍UNKNOWN，原RUN/index/start不变 | UNIT_PASS；原SDK新H14自然恢复NOT_TESTED |
| D 回执观察后、提交前退出 | 写失败阻断后继动作；Android未提交事务进程退出后回滚 | UNIT_PASS + DEVICE_PASS隔离事务；外部SDK→提交窗口仍可能UNKNOWN |
| E 提交后退出 | 确认幂等/不可回退；Android提交后终止测试ART仍保全 | UNIT_PASS + DEVICE_PASS隔离数据，非真实广告 |
| F 连续LOW_MEMORY | 进度、短段退避、冷却、12/日与8h边界 | UNIT_PASS；自然连续业务恢复NOT_TESTED；空闲LM不算 |
| G 旧回调丢失 | SQLite原意图+唯一WorkManager+FGS实际建立确认；保留原调用范围 | IMPLEMENTED/UNIT_PASS；无旧回调的实际系统回收重建NOT_TESTED |
| H 原SDK/官方再开 | 原身份继续，不新预留；已确认接取后的Boost可用官方再开；恢复先于普通启动 | 原H13及H14策略UNIT_PASS；当前MOV30初次接取缺唯一回执，BLOCKED/UNKNOWN |
| I 多项UNKNOWN | 每项唯一绑定；同一任务多未决不合并 | UNIT_PASS；实机BIKE4/MOV30保全DEVICE_PASS |
| J 隔离后独立阶段 | 同日唯一官方列表+干净原生15秒+无SDK/每日未决+全选中范围扫描 | IMPLEMENTED/UNIT_PASS；当前仍有官方中断Boost提示，不满足隔离，MOV40/daily推进BLOCKED |
| K 默认无常规PNG | 捕获前关闭，显式10min/12张/32MiB；必要视觉按需释放 | UNIT_PASS；code36/37/38有限真机导出/启动/故障取证0PNG、9481旧PNG无新增缺失；完整广告识别链NOT_TESTED |
| L 不接电脑 | 手机原生Alarm/Worker/FGS/A11y/SQLite/Telegram HTTPS | IMPLEMENTED；90s本机闹钟和手机TG DEVICE_PASS；物理拔线/跨多自然业务日NOT_TESTED |

Android完整事务故障注入使用code37实际签名APK的ReliableLedger/AdAttempt，初始化与19个步骤通过，6个测试ART进程自终止；正式Tuffy exit-info不变，不读正式私有数据、不用instrumentation。code38这两个源文件未变，证明哈希另存；不可冒称code38整条自然业务连续LOW_MEMORY通过。更早候选实验原日志保留，不叠加成更多功能测试数。

人工辅助包括Files安装/PlayProtect扫描、正常HyperOS权限和倒计时、导出、两次有限核对入口。原广告没有人为杀进程或重播。用户新报牛排manual约6秒退出/旧1円重复COMPLETED通知已留证，OPEN_NOT_FIXED；旧rewardConfirmed=false，不属于实际新获奖通过。

最终有限核对：code38/RT9d06cedc…已进入原CHALLENGES并精确识别官方再开；原MOV30初次接取缺唯一回执，BLOCKED。没有点击该再开按钮，没有新增观看；全部原23行/128ads与核对前完全相等，2 UNKNOWN整行SHA不变；最终导出0 PNG，旧PNG9481新增0/缺失0。此项是DEVICE_PASS（保全与负向安全路径），不能称MOV40/25次完成或正向SDK恢复通过。
