# H13：11月2日自行车待核对调查与安全续跑（进行中）

## 已核实结论

| 问题 | 结论与状态 |
|---|---|
| Telegram为何推送 | 00:40:47.391，RTeed85dd7-4147-4769-b64d-3142f2129bab的RoutineSummary经Notifier.routineSummary进入Telegram队列，并生成本地通知。挑战恢复返回unconfirmed_challenge_requires_evidence_review。服务端实际送达秒数UNKNOWN，用户确认收到。 |
| 真停还是监控停止 | 当时业务真实BLOCKED，00:40:48.599 FGS停止；外部监控仍在运行。后续外部采集另于host18:12停止，原因和业务不同。 |
| 卡在哪条 | BIKE自転車で4km移動しよう！，初始接取广告index1，原CHa5300b47-6b9e-4ba4-a4da-fab70bc42374，startedAt1793547617246。 |
| 原广告成功了吗 | UNCONFIRMED。预留expectedJoinReward100，before/after无确认值，步进50/50/50/100/100；无departure标记、原生成功回执或官方NO_GAIN，不改成功或失败。 |
| 系统退出 | DEVICE_PASS证据：exit-info 00:40:17.856 LOW_MEMORY，pid18757，importance125/PSS79MB/RSS89MB；无本次ANR证据。具体内存压力根因UNKNOWN。 |
| 无障碍 | 回收后00:40:19.502自动重连；升级会再次关闭权限，01:28按正常系统流程恢复仅Tuffy Enabled+Bound，无secure/appops写入。 |
| 是否恢复原业务 | DEVICE_PASS_LIMITED：现有继续按钮01:30点击一次，新恢复控制器RT292d6895-b6fd-406a-823c-d36a21e1e28c / CH3298d77f-b0bb-48b3-ae26-b8f1192c0186复用11/2账本，原观看身份未替换。不是创建干净的新业务日。 |
| 真实进度 | 本日签到00:04:48确认；WALK7各5Boost；BIKE500m/1/2/3km原已各5Boost，5km于01:34:32接取及01:36:04最后Boost回执确认；4km仍隔离未知。CAR正在执行；MOV40和daily官方X/25尚未验收。 |
| 修复状态 | IMPLEMENTED / UNIT_PASS：517JVM、73套件、0失败/错误/跳过，原114测试源文件逐字节保持。DEVICE_PASS_LIMITED：旧记录严格隔离、同一ads数组未改、后续BIKE5km真实接取和5Boost。新RESERVED边界再次遇LOW_MEMORY自动恢复NOT_TESTED，未人为杀广告。 |
| 用户介入 | 目前不需新人工操作；开发者正常安装、权限和继续入口辅助均披露。原BIKE4仍需最终官方核对，不得承诺整日全成功。 |

## 时间线及根因

00:03:00.034自然Alarm，RT42acd713-7711-42db-9bcf-d535ddf09c4e启动；00:04:48.595签到REWARD_CONFIRMED后进入挑战。原挑战逐项官方确认，未使用11/1进度。

00:40:17.246原自行车4km初始观看被同步预留，00:40:17.336开始证据TXT落盘；00:40:17.856系统回收。H12源码在手势前采集节点/截图，但没有持久化“仅预留”和“已进入手势派发”的边界，因此不能凭缺少PNG/日志断言未点击。自动恢复后官方仍为精确同日未参加详情，未观察到SDK、成功回执或官方恢复提示，旧保护正确拒绝重复。

本次低内存恢复回调确实排队并启动，不能归因于历史“连续LOW_MEMORY丢回调”。通知是这次安全阻塞的真实摘要，不是楽天抢占、外部停止、未知旧通知或无障碍持续断开。

## 最小修复

- ChallengeBoost.watch增加RESERVED→ARMED→DISPATCHED持久化；ARMED在手势之前同步提交，ARMED及DISPATCHED均不能被当成未提交。只有精确原生页面匹配的明示RESERVED初始预留可以原身份继续。
- PendingInitialJoinPolicy/Store：旧无marker的唯一同日初始AD_OPENING、无departure、未参加、零Boost、绑定原run/index/start，仅在精确HomeActivity原生详情且无SDK、原奖励/步进一致、15秒连续稳定时记录隔离。旧row只新增isolation字段，原ads数组不变。隔离只允许跳过这个key，不能授予奖励、FAILED、NO_GAIN或另一次观看。
- 普通UNKNOWN、Boost未知、已离开、跨日、多个未决项及账号保护仍阻塞；恢复时真实SDK继续使用原有关闭及回执规则。未因黑屏无限Back或重启ANA。
- 各分类扫描只受本分类pending影响。只有其余扫描均完成且剩余都为有效绑定隔离项时，Routine和DailyAd两个入口才允许进入独立每日广告。最终仍返回BLOCKED/待核对，不误报整日DONE。

源功能提交0b9cae6d30f3d14065da154f2888dc7eb73add1d，分支fix/h13-bike-initial-reservation-20261102。main5be68d6和v22.0.12-R47保留不变；未自动合并H13。

## 构建、签名与数据

正式签名Release完整BUILD SUCCESSFUL in20m41s；517JVM/73套件0失败/错误/跳过（原504+新13）；旧114测试源文件与main逐字节相同。第一候选构建主动中止以补齐发布前审查发现的DailyAd门禁；第一候选未安装，不冒称首候选最终通过。

APK22.0.13-R47/code35：50223212字节，SHA256 64bb977a4288a2b7346b7be806b3f8f4e2055faf6529b14ef95c94d52dfbd163；正式证书SHA256 ab39397d23fca7803fa7cd3e33125874d010874d5641e6ffd044a2a3b55f484c。01:23:43正常Files/PlayProtect更新；实机base拉取同SHA。没有卸载、清数据或写权限数据库。

升级前正常Records导出279条目ZIP并CRC验证；其中12挑战、签到REWARD_CONFIRMED、本地daily启动0。01:30:46隔离后去掉新增isolation字段，原自行车4km整行与ads数组均与升级前完全一致。已确认WALK未重新观看；BIKE5km为此前未开始的项目。全历史/配置的结束后正式导出比对仍待安全空闲，不能以当前局部比对充全量完成。

自启动正常系统UI确认原ON；省电策略由智能限制改为无限制并确认生效。该变化可能减少后台限制，但本次LOW_MEMORY与省电策略的因果关系UNKNOWN。未修改用户四类5/5/40/40、长途OFF0、ANA00:03、楽天ON09:15gen7、牛排ON09:30gen9或Telegram配置；P1尚未到期，未提前执行。

## 监控边界

现有采集controller因Windows读取latest.json的PermissionError于host18:12退出，持锁进程UNKNOWN。最后成功18:11:00UTC/手机01:10:47；恢复首次成功18:33:19UTC/手机01:33:06。这段有开发者安装/权限等截图证据，但不能声称每15秒连续监控。

已保全原停止记录，补读锁容错/有界状态写重试；同一目录从242续号，旧样本未覆盖。现controller13904/collector4716只读运行；外部绝对截止2026-11-02T05:30:00Z未延长，没有新增业务调度，不取消APK闹钟。外部停止未改APK账本/计数，业务隔离真实成立。

手机用户数据的大头为8839张运行PNG及节点证据，普通Evidence没有轮转；本轮仅统计，用户已要求先修复续跑，没有清理。

## 尚待完成

持续观察CAR/MOV及11/2每日官方X/25；原BIKE4保持待核对。活跃广告期间不切Main或导出、不安装、不跑instrumentation；只有安全空闲才核最终账本、通知、清理和锁屏。单元通过和局部真机通过均不等于全天全部任务通过。


## 21:10 UTC补充：MOV5原广告在商店停留后保身份恢复

截至host UTC2026-11-01 21:10（手机11/2约04:10）：CAR6接取及各5Boost已确认；MOV5km接取01:55:55确认，原Boost index2/start1793552159231于01:57:39超时BLOCKED，Google Play TikTok详情不能作为回执。外部采集仍13904/4716持续写新样本，业务停止期间日志不再前进，不能把采集健康当业务健康。04:06:53人工仅一次Back回原Pangle窗口，04:07:42正常继续入口一次，RT6dd84777-2d37-4daf-94f8-bc3702bd11af / CH1e0ea4bc-f539-4123-bfd2-8c9f13a570f0。生产代码恢复同一广告，04:08:13新鲜官方回执确认Boost1，原RUN/index/start及previousFailure=ad_timeout_95s保留，核对时ads仍2条，未为未知重复预留。随后自动继续新Boost，当前仍活跃。不是无人干预恢复PASS；没有新Kotlin修改或APK安装。其余MOV10/20/30/40与daily官方25继续待核实，BIKE4旧隔离项仍UNKNOWN。

证据：H:/Tuffy_Live/2026-11-02-0003/investigation-store-mov5/recovery-verification.json。截图与原日志仅私有加密交付。H13 APK/sourceZIP/JUnit冻结不改；NAS原公开12文件逐文件读回SHA一致，两份私有AES-GCM归档读回认证及明文SHA复核通过。最终业务尚未完成，正式Records导出及旧账本最终核对仍待安全空闲。


## 21:24UTC：一次自动LOW_MEMORY恢复、一次SIGKILL后辅助恢复

截至host21:24UTC/手机04:24，MOV10已接取+5Boost。MOV20原index3/start1793560738685在04:19:02.069遭Android LOW_MEMORY，04:19:05自动RT8598744f-32fc-4869-b2a4-d71235bf086f恢复同一广告，04:19:22回执确认Boost2，原身份及3条ads保留。04:19:50.600再次退出为OS reason2 SIGNALED/status9，根因UNKNOWN，不能冒称LOW_MEMORY/ANR；源码自动恢复仅授权明确LOW_MEMORY，故04:19:55服务重连后无自动调度。保全现场后04:23:22正常继续按钮一次，RTc3ea3ff1-e1ad-445e-b5d4-bfe37472fa9b / CH6bef72d0-c323-4e2a-8259-3ac4eb2e991e，从原Google AdActivity恢复MOV20 index5/start1793560787742，04:23:38官方回执确认Boost4，原身份、DISPATCHED及5条ads保留，无重复预留；随后原index6及后续扫描由生产继续。两次均为Boost恢复，不是新RESERVED初始路径真机通过；无源码/安装/系统权限变更。BIKE4仍UNKNOWN，余项/daily待核，未整次完成。

私有证据：investigation-low-memory-mov20/recovery-verification.json、tuffy-exit-info.txt、原日志和屏幕。


# H13 手机11/2 00:03专项执行最终报告

结果：**BLOCKED / INCOMPLETE，整日业务未完成**。按用户指定的未知广告无法安全恢复终止条件提前结束外部监控，未等到上限05:30UTC；未取消Android独立日程。


手机2026-11-02自然00:03任务；最终正式诊断phone04:38:23，最终RTdc89e6d1-134e-4ba3-86ea-d8d428d4284a于04:33:57 BLOCKED。签到已确认；WALK7、BIKE5（500m/1/2/3/5km）、CAR6、MOV5/10/20共21项接取及各5Boost确认。挑战128条广告尝试记录、126回执、0FAILED、2UNKNOWN。BIKE4原index1/start1793547617246无departure隔离保留；MOV30原index1/start1793561396418为DISPATCHED且有departure，仍未确认。MOV40及剩余范围未完成扫描/接取核对。daily本机0次，官方X/25未观测，不能称0/25或25/25；整日未完成，不是PASS，也不代表实际移动达成或钱包到账。

H13/22.0.13-R47/code35正式同签名安装，最终再次读实机base SHA256=64bb977a4288a2b7346b7be806b3f8f4e2055faf6529b14ef95c94d52dfbd163，与交付一致；原证书ab39397d23fca7803fa7cd3e33125874d010874d5641e6ffd044a2a3b55f484c保持。功能提交0b9cae6d30f3d14065da154f2888dc7eb73add1d，fix/h13-bike-initial-reservation-20261102；main5be68d6/H12标签未动，H13未推送合并。517JVM/73套件0失败错误跳过，原114测试源字节不变，未跑干扰A11y的instrumentation；最终仅文档更新，没有H14、没有额外APK。

H13真实隔离旧BIKE4通过且原row减去唯一隔离字段及ads均与升级前相等；新RESERVED初始恢复分支仍NOT_TESTED。MOV5商店返回后原index2回执确认，人工一次Back及一次继续披露。04:19 MOV20自然LOW_MEMORY既有Boost自动恢复回执确认；04:19:50 SIGNALED/status9原因UNKNOWN，一次人工继续后原index5回执确认。04:29:59和04:30:21再次LOW_MEMORY，3/3自动恢复已用；原MOV30广告关闭后出现匿名“已开始挑战/第1次Boost”面板，但没有任务名/日期且25pt表与多项相同，原REWARD_CONFIRMED证据不存在。04:33正常继续入口一次仅保全并BLOCKED，未提交新观看。不能通过匿名回执领养未知、手工改账、清UNKNOWN或放宽Boost/跨日限制。RAM压力上游根因及SIGKILL原因仍UNKNOWN。

04:38正常Records正式导出35,052,849字节、ZIP CRC通过、SHA9828a4d5e7d18e98a1e10a2f15d5667ba99b5a2ab126e8859d61f0564fe07300。原12条挑战去掉唯一隔离字段后全部逐字段相等；settings/checkinConfig/login/dailyAttempts均与升级前一致。四类5/5/40/40、长途OFF0、ANA00:03、楽天ON09:15gen7、牛排ON09:30gen9保留；历史11/1签到UNKNOWN和10/31旧FAILED/UNKNOWN不删不重播，未声称本次全量重导所有旧业务日私有SP。最终仅Tuffy Enabled+Bound/Crashed空。通知04:33:57 posted；未因结果未明执行Recent清理或锁屏，未主动关闭原ANA回执。

外部原18:11–18:33UTC采集缺口保留，18:58–21:06样本持续但无即时故障处理亦披露。最终host21:40:14UTC按硬阻塞停止外部controller13904/collector4716，exit0、样本止874（恢复段242–874共633）、androidStateChanged=false；heartbeat tuffy-11-2-00-03已删除。原绝对上限05:30UTC未延长，Android独立闹钟/P1保留。手机4GB证据清理明确延后，未手动删数据。最终报告H13_FINAL_REAL_DEVICE_REPORT.md；私有原图/节点/日志及正式诊断在H私有目录，NAS仅认证加密备份；公开交付不含凭据。H13 APK/sourceZIP/JUnit冻结，后续文档与最终Git包另存。


## 原观看与剩余

| 类别 | 确认接取+5Boost | 剩余 |
|---|---:|---|
| WALK≤5km | 7 | 本次范围已确认 |
| BIKE≤5km | 5 | 4km原初始接取UNKNOWN，隔离不重播 |
| CAR≤40km | 6 | 本次范围已确认 |
| MOV≤40km | 3（5/10/20km） | 30km原初始接取UNKNOWN；40km及剩余范围尚未完成核对 |
| 每日广告 | 本机0次 | 官方X/25未读取，未启动 |

MOV30原身份：CH6bef72d0-c323-4e2a-8259-3ac4eb2e991e/index1/start1793561396418。恢复run CHdd27bd6b-e7c8-4f05-9e2b-2cfe612e8ccb只恢复原SDK并点击真实Ogury close-btn，未新预留。第二次LOW_MEMORY发生在关闭后、确认落盘前；最终ad状态AD_CLOSE_AVAILABLE/confirmed=false，原DISPATCHED和departure保留。原生面板虽然明确“挑战已开始”，却缺少当前任务名和日期，不能把它等同于严格绑定此原广告的正式回执。现有NativeChallengeReceiptPolicy要求原始开始和原始回执证据；原REWARD_CONFIRMED文件两次精确查询均不存在，不能制造该文件。H13未把这种已提交初始广告隔离，daily也未放行。

## 保全与验证边界

正式导出已验证23条当前日任务记录、128条广告尝试记录；升级前12条完整保留，除了BIKE4新增隔离元数据。旧业务日记录没有删除或重播，但导出器仅直接导出当前日业务账本，故不声称已全量重导10/31、11/1的SharedPreferences。此前正式诊断、故障快照及Records历史结果继续保留。

本轮517JVM为已完成H13构建结果；最终文档阶段没有重跑或增加无意义测试，未跑instrumentation。上游持续低内存、SIGKILL的具体触发原因未证实；不以无限制省电、单次恢复或单元测试替代真机整链成功。所有人工入口/正常权限/安装及商店Back均在时间线披露。

## 交付定位

公开交付：H:/Tuffy_Live/2026-11-02-0003/delivery/H13；NAS镜像X:/Elements/codex/Tuffy_2026-11-02_0003/H13。APK/sourceZIP/JUnit沿用冻结H13，sourceZIP快照067c02bf，功能源码0b9cae6d；最终文档不是重建APK。私有采集run目录、故障原图/节点、正式诊断与最新Git历史包认证加密后备份，密钥不在公开交付/源码/Git内。
