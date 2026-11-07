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

# H14 稳定性专项报告（code38候选；非长期无人值守验收）

此文件随实际证据更新。IMPLEMENTED 表示源码已实现；UNIT_PASS、DEVICE_PASS、NATURAL_RUN_PASS 分别需要对应测试证据。不能相互替代。当前未满足长期无人值守验收，禁止合入 main 或创建稳定 Tag。

## 1. 基线与已证明的故障

从 H13 最新实际 HEAD b3cfe0c185bd71a8e15e6b307387907c7c7045cf 创建 fix/h14-standalone-memory-recovery，包含功能提交 0b9cae6。H12 main 5be68d6 和 v22.0.12-R47 保留。H13 APK、源码 ZIP、JUnit 和原归档不覆盖。

H13 自然执行有明确 LOW_MEMORY 退出；04:19:50 的 SIGNALED/status9 原因仍 UNKNOWN。一次恢复成功不能泛化为连续恢复成功。旧恢复依赖连接回调且 3 次额度耗尽；确认识别后、完整保存前还穿插取证等工作。源码检查发现截图回调超时后 HardwareBuffer 未关闭的路径，H14 已补齐关闭；这不能证明它是全部 LOW_MEMORY 的根因。

最新升级前正常 Records 导出：phone 2026-11-02 14:31:04，ZIP 39,822,883 bytes，SHA256 a2a7882343611e4f62ecdbd56ae896deed6f94dbea475c3993b5a4aea358a61a，CRC 通过。最新日志另有 phone13:24 的 RTc632f032… 恢复阻塞，早于 H14 手机操作；不能继续只引用凌晨最终报告。最新导出仍为23条挑战/128条广告/126确认/2 UNKNOWN，固定每日广告本地0，官方X/25未观测。21项接取及各5 Boost确认，签到确认。设置四类5/5/40/40，长途OFF0，ANA00:03、楽天09:15gen7、牛排09:30gen9。

## 2. 截图路径与实际改变（IMPLEMENTED）

| 路径 | H14 正式默认行为 |
|---|---|
| Evidence：正常导航、Challenge、Boost、每日广告、签到、恢复步骤 | PNG捕获前检查开关；默认不申请截图、不写PNG；文本保留 |
| FaultStore：超时、未知页、运行异常 | 默认结构化文本/节点/Activity/Window，无自动图片；H14新故障目录最多30份/128MiB，满后停止新增，不删历史 |
| CheckinRuntime：last_ocr.png、逐步证据 | 持久图片默认关闭；必要OCR依旧按需执行 |
| AdUiClose：无标签X、Canvas控件、Pangle/Fyber/Five | 保留原视觉安全分类器，直接在临时Bitmap上计算；取消正常PNG编码→解码和长期PNG缓存；finally释放 |
| 分类图标（ANA未提供可用标签） | 必要按需Bitmap识别、原分类/遮挡/选中条件保留，直接分析后释放 |
| Challenge详情 | 取消每次开详情前的截图基线；原生节点先识别；不明确时最多两次轻量亮度探针 |
| 黑屏后原广告受限返回 | 原有按需视觉安全核对保留，不写PNG；不以黑截图直接认定广告故障 |
| CheckinVision独立OCR进程 | 必要临时PNG作为跨进程输入仍保留，finally删除该次临时文件；不属于运行取证图片。历史证据不删除 |
| 手工诊断ZIP | 默认不带图片；用户主动长按开启10分钟诊断，最多12张/32MiB；ZIP图片聚合也限32MiB |

用户明确开启的诊断模式有期限和数量/大小硬上限。删除/轮转旧ZIP的行为已取消。旧报告8839张、最新清单9481张历史证据没有清理；没有声称它们全部已备份。原手机3份诊断ZIP已复制并CRC/SHA校验，再将手机原exports目录同级改名保留，避免H13导出时自动删除旧ZIP；无文件删除。

## 3. 正式运行与电脑依赖（IMPLEMENTED；物理断开验收NOT_TESTED）

正式APK的AlarmManager/WorkManager、ForegroundService、无障碍控制、SQLite账本、Telegram HTTPS/持久发送队列全部在手机内。H14没有加入USB/ADB/H盘/NAS/开发会话/电脑转发依赖。电脑工具只用于本轮开发与可选诊断，H13外部采集和heartbeat未重启。

物理拔除USB后的调度、恢复、Telegram及多业务日观察必须另做实测。连接USB但暂不发ADB命令不等于拔线通过。

## 4. 内存实测与结论范围

H13 空闲 phone13:32:56：Tuffy PSS70,853KiB / RSS150,152KiB，Java Heap PSS7,612KiB，Native PSS12,680KiB，Graphics14,816KiB；ANA PSS350,524KiB/RSS510,020KiB；整机MemAvailable1,188,584KiB/总3,823,568KiB。

第二个H13空闲快照 host07:00:29UTC：Tuffy PSS69,511KiB/RSS136,796KiB；ANA PSS288,902KiB/RSS279,516KiB。

这些不是故障时的峰值，也不是相同工作负载的前后对照，不能证明Tuffy严重泄漏或优化百分比。H14新增手机自身每分钟一次、文件上限2MiB的结构化内存采样：Java/native/PSS/RSS/graphics、GC计数、图像请求/编码及已追踪软件截图分配、系统可用内存。计数仅覆盖所注明入口，不能冒充全进程总分配量。ANA跨进程内存仅能用可选开发诊断采样。H14 code36已有以下空闲实测，单位均为KiB：

| 情况 | Tuffy PSS | RSS | Java Heap PSS | Native Heap PSS | Graphics |
|---|---:|---:|---:|---:|---:|
| H13 phone13:32:56 Main空闲 | 70853 | 150152 | 7612 | 12680 | 14816 |
| H13 第二空闲样本 | 69511 | 136796 | 未单列 | 未单列 | 未单列 |
| H14 code36 phone16:29导出后/无障碍刚连接 | 74870 | 195728 | 6460 | 23568 | 12784 |
| H14 code36 phone20:59数小时空闲，同PID17423 | 60359 | 136916 | 4188 | 8352 | 7108 |

16:29对应ANA PSS345672/RSS335888，系统MemAvailable1060436/总3823568KiB。内建service_connected样本PSS26440KiB、Java实际分配5595112B、Native实际分配16911832B、GC0、视觉请求0/PNG编码0。此设备Android MemoryInfo的RSS键未提供值，RSS来自可选dumpsys实测。

code36安装后的16:15:17及16:25:37还出现importance400的系统LOW_MEMORY退出，均无活动业务；后者OS报PSS75MB。不能声称关闭截图后不会再被系统回收。没有相同活动负载的H13/H14峰值、识别延迟、图像总分配或GC对照，无法计算改善百分比，不能证明自身泄漏或归因全部上游原因。上述code36与code37的图像/账本实现相同，code37另修人工核对入口，但不能以code36数据冒充code37活动运行峰值。



code37 routine_active单样本PSS107986KiB，Java实际分配14123832B、Native41609608B、GC145次/6737ms。code38 service_connected PSS25736KiB；22:03:17 routine_active PSS92528KiB、Java Heap PSS12184、Native PSS30816、Graphics25820，Java实际分配15602368B、Native41265456B、GC172次/7129ms、系统可用985411584B、systemLowMemory=false；已追踪视觉请求/PNG编码/软件截图分配均0。结束BLOCKED后可选dumpsys：PSS90958/RSS209500、Java Heap PSS17068、Native29560、Graphics17272KiB。这些是已采到的时点，不是完整峰值，GC累计值也不属于同负载前后实验。

原因分类：A自身对象/Native增长没有足够长的活动同负载序列证明泄漏；B ANA/广告SDK峰值未捕获，已有ANA空闲占用较大；C整机样本可用约0.9–1.2GB，但不是回收瞬间；D导出、ADB诊断和隔离探针存在观测开销，未重建15秒外部采集；E系统确实回收过cached/importance400进程，不能直接推导为自身RAM耗尽。HardwareBuffer超时关闭缺口是已证明的源码缺陷，尚不能证明它就是H13连续回收根因。磁盘4.67GB历史证据不等同RAM。

## 5. 可靠账本（IMPLEMENTED，Android隔离事务DEVICE_PASS，正式业务连续回收另验）

使用Android内建SQLiteOpenHelper，私有 tuffy_ledger_v14.db，FULL synchronous、rollback journal，复合唯一键(area,scope,identity)。挑战按业务日/精确key保存整条任务及ads，事务提交完成后返回；每日官方广告和签到以原日身份非破坏性导入。旧SharedPreferences数据保留，不删除、不降级覆盖。挑战保存检查历史ads前缀身份及已确认不可回退。

H13核心广告SP本来已有同步commit，不能把事故简单归因于apply。H14同时将仍影响派发的pending-trigger异步保存改成检查commit结果。SQLite打开/事务失败会阻断新业务动作，不回退读取旧SP继续点击。UI显示存储阻塞，保留原记录。

不支持直接降级回旧H13后继续写账本：旧版本不理解H14新增权威SQLite。恢复旧版本需兼容迁移方案，不能把保留的旧SP误当最新权威账本。

## 6. 回执优先落盘（IMPLEMENTED）

点击之前先保存日期、类别/精确任务名称和key/公里数、原RUN/index/startedAt、官方奖励基线、RESERVED，再同步ARMED后派发，接受点击后保存DISPATCHED。点击失败/中途终止保持未知，不推断没有播放。

原始身份校验通过后，Challenge原广告确认及任务Boost/接取状态在同一事务提交，再做取证、通知、下一动作。每日广告先保存绑定回执观察，精确+1官方计数与原基线一致后提交最终确认，再更新界面/进入下一条；绑定的延迟回执恢复同样先保存，重复确认幂等。签到及各既有延迟官方回执路径也已将可选取证移到确认保存之后。

外部SDK回执出现→本地观察→可靠提交之间仍有不可消除的崩溃窗口；在提交前退出仍UNKNOWN。匿名挑战面板不能提供缺失的任务绑定。

## 7. 连续回收与回调丢失（IMPLEMENTED；真机连续验收NOT_TESTED）

每次Routine进入业务前在SQLite保存手机原任务意图，并确认原生WorkManager检查任务已入队；阶段更新另保存原RT快照。控制器之外的唯一Worker可重建，重新读账本和真实页面，不依赖旧协程闭包。ForegroundService必须实际建立后才进入业务；系统暂不允许时，保存尚未派发的独立启动意图、有界重试。

明确LOW_MEMORY按可靠进度实施10s/30s/120s退避；三次无进度后15分钟冷却，同一无进度段再耗尽则阻塞；最多12次自动控制器启动/业务日，8小时时窗。可靠新进度可重置短段，不重置每日硬上限。WorkManager实际时机受系统调度影响，不保证精确秒数。重启/升级/用户停止/跨日/权限失效/风控不会盲续；每次恢复核对最新退出，SIGNALED不能借用旧LOW_MEMORY原因。无多小时常驻唤醒锁、无限进程重启、循环开ANA或提高旧3次额度冒充修复。

## 8. UNKNOWN安全隔离（IMPLEMENTED；MOV30实际隔离BLOCKED）

保留H13的BIKE4精确初始预留隔离。新的DISPATCHED+departure UNKNOWN隔离要求：同日精确原身份/唯一未确认尝试、SDK及不可逆提交结束、干净原生页面持续15秒、只读官方今日参加中列表有唯一相同key/标题/公里数且没有未决每日广告。仅添加绑定隔离元数据，不改原广告成功/失败/派发/confirmed，不重播。

匿名Boost面板或现存SDK不满足干净页面条件。只有所有选中范围扫描完成且剩余项目全部严格隔离，才允许进入独立每日阶段，并另读当日官方X/25。多个不同任务分别验证，不能把一条不明观看整体放宽。

## 9. 两条原UNKNOWN保全

BIKE4：CHa5300b47-6b9e-4ba4-a4da-fab70bc42374/index1/start1793547617246；MOV30：CH6bef72d0-c323-4e2a-8259-3ac4eb2e991e/index1/start1793561396418，DISPATCHED/离开=true/confirmed=false。

升级前最新整条任务规范JSON SHA256分别 be752fbe09c1e31659df6dcb8ccf39642b67cb17407bf97b702c51b99651e95c 与 c168465060e920d9a2dd8cda818c3a24766b653d9caaef75f065097cc01745ab。三次正常升级及最终有限核对后的正式导出均与此整行SHA一致；后续允许的绑定隔离只增加元数据，原ads不得改写。

## 10. 测试

原115个测试源文件相对H13 b3cfe0c逐字节一致。最终code38：546项JVM/74套件、0失败/错误/跳过，包含原H13 517项与29项针对性回归，原115个测试源文件哈希逐字节一致。构建BUILD SUCCESSFUL in10m16s，76tasks，build-20261102-223935.log。早期候选/曾发现的JVM Android JSONObject测试环境错误原始证据保留，未删除旧断言或跳过失败以凑通过。

code37签名APK实际类的Android隔离验证：初始化及19个读取/写入/异常步骤通过，FULL synchronous=2、journalMode=persist；3轮提交前终止测试ART进程→回滚、提交确认后终止→保全，共6次测试进程自行SIGKILL。唯一合成任务、幂等保存、确认不可回退、另一条合成UNKNOWN及写失败锁存通过；真实Tuffy exit-info前后不变。此工具仅使用独立/data/local/tmp路径和2099年合成数据，不访问正式App私有数据、不杀真实Tuffy/ANA、不启动业务、不用instrumentation。早期两份候选也做过同类验证，原日志保留；code37候选的6次不能称自然LOW_MEMORY。

详见 H14_ACCEPTANCE_MATRIX.md。JUnit只证明测试环境中的顺序/边界策略；Android隔离事务验证与JVM分别取证；真实连续LOW_MEMORY、原SDK恢复及拔线多日执行仍不能由两者替代。

## 11. 真机操作与人工辅助

H13→H14 code36、code36→code37、code37→code38均仅在无活动广告时正常Google Files安装、PlayProtect扫描及小米安全扫描，同包名原签名；没有卸载/清数据。三次系统重新关闭无障碍，均正常允许受限制设置、完整10秒倒计时后恢复，仅Tuffy Enabled+Bound。所有安装/权限/正式导出/核对入口由开发辅助，不能称无人干预。

code36升级后phone16:22:28，settings/checkinConfig/全部23挑战及128ads/daily/login/officialObservation/scan与H13 phone14:31基线逐字段一致，ZIP0PNG。code37前phone21:08:43另作正式备份，SHA cc1123970e849f3761056c395dbc010c1e5f1fa3f0bca2495934ae39cdf2db8a。code37后phone21:26:46再次全部逐字段一致，ZIP1354636B、SHA d8b5b428f20edb2398dc96a289a453e5621fc999d505e41ca31e409450f18747、CRC通过、0PNG。两条UNKNOWN整行SHA始终与第9项一致。

code36原生90秒自检由系统在16:32:40.463触发，日志及本机通知9001证明送达；未启动广告。Telegram测试由手机直接HTTPS发送，20:59:18界面显示测试推送成功，无电脑转发。USB当时仍连接，不能称物理拔线通过。9481张手机历史PNG在该阶段新建0/缺失0；真实活动路径另按最终设备验证记录。

code36有限验收发现旧「重新扫描」一有未确认提交就提前拦截，尚进不到新隔离核对流程。code37新增保留扫描/账本的「核对未确认观看（仅出行）」入口，初始与确认后两次检查无活动/每日未决/SDK/账号风险，并复用原严格引擎；没有重置UNKNOWN，没有运行每日25阶段。21:29:17人工确认一次，RT34c2ab82-96d5-4f7d-9507-c86ac1668786，实际结果见最终DEVICE_VERIFICATION.json；不以入口可点或启动ANA算完成。

用户另报牛排立即执行几秒退出锁屏、重复推送1円。已留正常诊断证据：两次新manual RUN仅约6秒，没有新TASK/奖励回执，通知复用09:30原darts SUCCESS金额1、rewardConfirmed=false/REWARD_DISPLAYED/CREDIT_UNCONFIRMED。直接原因是done(task)跳过后按全日旧rows生成新RUN COMPLETED并清理；旧09:30识别正确性与到账仍UNKNOWN。用户指定登记待处理，BUG_BACKLOG已单列OPEN_NOT_FIXED；本次没有重抽或把旧奖励改成功。



最终code38观测：22:00:42正式导出在任何本轮新核对前，与phone21:41:56备份的settings/checkinConfig/challenges/daily/login/officialObservation/scan全部逐字段一致。22:03:17人工仅确认一次“保留记录并核对”，RT9d06cedc-d7fc-488a-a13c-31baa921bd77 / CHe419584b-08ea-4bd4-a3aa-6159a2ff4594。STARTUP立即DEFERRED，正确进入CHALLENGES原身份核对；22:03:22识别官方中断Boost“再開する”提示，但原MOV30为初次接取且回执缺失，明确BLOCKED，未点击再开/新广告，未采纳匿名面板。

22:05:29最终正常导出1633254B、SHA f508ed7a78bc0d7333a456b30bc688df6f2e6fc7520b9640289e688774a68352、CRC通过、0PNG；全部23挑战/128ads/126确认/2UNKNOWN及settings/checkinConfig/daily/login/officialObservation与升级前及核对前完全相等。BIKE4/MOV30整行SHA均不变。派生扫描仅追加本次BLOCKED原因。旧9481张PNG清单最终新增0/缺失0，异常取证只有结构化文本。当前签到确认，21项接取与各5Boost仍保留，MOV40未执行，daily本地0、官方X/25未观测。Routine BLOCKED，不是整日成功。

最终实际设置：四类5/5/40/40，两长途OFF0，ANA00:03；楽天09:15当前generation9、牛排09:30generation9。14:31时楽天generation7是历史，后续用户设置已有变化，最终按新导出保留，没有写回旧值。原生下一次ANA03日00:03及P1闹钟保留；22:08新鲜dumpsys仅Tuffy Enabled+Bound。省电“无限制”页面实核且未更改。因UNKNOWN及官方续接提示未解决，没有执行完成后的Recent清理或锁屏，未强停ANA。

## 12. 版本、源码、APK及交付

最终候选22.0.14-R47/code38；实机base与签名交付APK SHA256均为3380105b06648b0b353e7cd7c258a1a13b7eea2ddc0b47ece3dd8013dc255546，50255976B。证书ab39397d23fca7803fa7cd3e33125874d010874d5641e6ffd044a2a3b55f484c未变。code36/37中间候选及H13/H12全部保留。

构建及最终546项测试源码提交6ea2e3649ca4b565ed7fd356f7540f5a02edf405；在此前4d09831基础上修复原挑战恢复先于普通STARTUP的路由，并明确初次接取不能用匿名再开回执改账。核心账本/恢复见c56d90a、c5ad53d、5311cfa、9ff6711、678b141。最终报告/源ZIP快照commit另见DELIVERY_MANIFEST，两者app源码差异应为空。

完整Android隔离事务19步/6测试进程自终止是在code37签名APK上通过，code38的ReliableLedger与AdAttempt源文件哈希完全相同；code38最终APK又做实际类加载、Android JSON及合成导入烟测，FULL=2/persist，通过。没有为版本号重复更多真实进程故障注入，也不把这些合成验证称自然LOW_MEMORY。

本地fix/h14-standalone-memory-recovery保留提交，H12 main5be68d6及v22.0.12-R47未动，不合main、不打稳定Tag。Git CLI非交互认证失败，未推送GitHub；连接器能读取私库不等于本地提交已同步。完整Gitbundle认证加密归档。

交付H:/Tuffy_Live/H14_standalone/delivery/H14/code38；独立NAS X:/Elements/codex/Tuffy_2026-11-02_0003/H14/code38。公开源码ZIP只取最终Git快照，排除releases目录中的旧R44 APK；原文件留在Git与加密bundle，不删除。源码/已签名APK/JUnit/报告及证明逐SHA读回；私有原图/节点/正式诊断/故障日志与Gitbundle仅AES-GCM认证加密交付，key不进入NAS。归档执行结果见NAS_READBACK.json与PRIVATE_ARCHIVE_VERIFICATION.json；不覆盖H12/H13，不公开凭据。

## 13. 跨日待验

多个自然业务日、物理不插电脑的到点启动/控制器/Telegram/清理；自然连续LOW_MEMORY下原身份及无旧回调恢复；FGS暂被系统拒绝、真实原SDK恢复、重启/跨日/权限保护；同活动负载峰值、GC、识别耗时及系统压力；严格隔离后MOV40和独立daily的真实完成。不能杀真实广告制造测试样本。牛排旧结果重复通知/提前锁屏是另一个明确OPEN阻塞，不包含在已通过项目中。

## 14. 无人值守条件结论

主要修复IMPLEMENTED；546项UNIT_PASS；Android隔离事务、正常升级/迁移及部分本机调度/通知DEVICE_PASS。实际业务核对按最终设备证据逐项判定。连续LOW_MEMORY业务恢复、物理拔线和多日NATURAL_RUN_PASS均NOT_TESTED；BIKE4/MOV30仍UNKNOWN。H14尚未满足长期无人值守验收条件，也没有证据证明LOW_MEMORY根因已消除，禁止发布稳定结论。

正式APK使用手机自身AlarmManager/WorkManager/FGS/无障碍/SQLite/TelegramHTTPS，IMPLEMENTED层面没有电脑依赖；这与已完成脱离电脑自然验收是两个不同结论。

官方技术依据：[Android SQLiteDatabase](https://developer.android.com/reference/android/database/sqlite/SQLiteDatabase)、[SharedPreferences.Editor](https://developer.android.com/reference/android/content/SharedPreferences.Editor)、[WorkManager任务与重试](https://developer.android.com/develop/background-work/background-tasks/persistent/getting-started/define-work)。这些接口机制不保证外部奖励与本地数据库之间的分布式原子性。
