# Tuffy H14：现存问题、验收缺口与 Claude 优化交接报告

整理日期：2026-11-07。**业务状态依据手机截至2026-11-07 16:40:31 JST的最近正式证据，不是本次重新操作手机后的结果。**北京时间为15:40:31。本文只整理交接材料，没有新增源码修改、安装、广告或通知。旧报告按其业务日期解释，不能把旧版本的BLOCKED或NOT_TESTED覆盖最新结果。

## 1. 当前可复现的结论

当前最大问题是：**广告已经播放并出现原生奖励提示，但官方每日次数没有增加；缺少逐次官方结算身份，无法可靠确认原attempt，也无法证明继续新观看不会混淆迟到结算。**另一个实际缺口是部分广告超时复核拿不到有效探针。正常挑战和同日继续路径已经取得真机成果，不需要重新开发JOIN或Boost。

| 项目 | 最新真实状态 |
|---|---|
| 实装 | code44 / a3d6137e24b15770a677c1e8e1e4333585c65fdd |
| 分支 | fix/h14-resume-category-scope-20261107；H12 main未改 |
| APK SHA256 | 6d13d4f2f5670901a58bb383e28d3e0ce3eb54b44bd49aa260e128c3ab2152c7 |
| 原证书SHA256 | ab39397d23fca7803fa7cd3e33125874d010874d5641e6ffd044a2a3b55f484c |
| Nov7挑战 | 本机正式接取23项，23项5/5，138条确认广告回执 |
| 另1项CAR40 | 官方已参加、0/5；原接取广告仍UNCONFIRMED，不能算本机回执确认成功 |
| Nov7每日广告 | 22次派发，21条正式确认，官方21/25；第22次UNCONFIRMED |
| 当日积分历史 | 基础登录20pt；移动336pt；广告观看1100pt；日合计1456pt。分类汇总不是逐次交易回执 |
| 登录额外视频 | 100pt入口被误关的证据已找到；修复已安装，真实领取NOT_TESTED |
| 最近继续 | 34秒直接到ADS，挑战扫描/Boost调用0；CHALLENGES保留PARTIALLY_COMPLETED，ADS明确BLOCKED，FGS停止 |
| 最近回归 | 针对性60项、完整652项通过；不能替代ADS或登录视频真机验收 |
| 最新系统 | A11y Enabled+Bound，FGS停止，桌面锁屏；保留ANA任务和原UNKNOWN |
| 内存 | 下午安装阶段新增LOW_MEMORY 1；后续继续验收无新增退出；内存根因未知 |
| 下一原生计划 | Nov7 23:58 JST保护、Nov8 00:03 JST Routine，已见闹钟记录；未来执行NOT_TESTED |

Nov6的24项5/5、144回执是另一业务日，不能用来补齐Nov7。工程heartbeat已暂停，手机原生闹钟保持；没有承诺电脑长期后台监控。

## 2. 现存问题清单

### P0-01 每日ADS结算与官方次数不一致，实际25/25未完成

**已证实的现象：**

- Nov6第2次：before1，已派发，关闭后出现原生奖励，钱包17772→17822；当时每日次数仍1/25，日广告积分100pt。真正结束主进程再启动仍1/25。后来用户手动看过其他广告，次数到4/25，不能把后来的增长唯一归属原第2条。
- Nov7第22次：before21，00:25左右关闭广告、出现原生奖励，官方仍21/25；下午新鲜核对仍21/25。日广告积分1100pt不能用1100÷50结算该attempt。
- 原生奖励观察已经持久化，本地run/index/startedAt未丢失；不是目前已证明的Accessibility点击失效，也没有证据证明是错误套用challenge mission_id。

**第一个未满足的生产条件：**`AdInteraction.awaitReceipt`没有得到对应次数增长；`OfficialAdStore.reconcileRecordedReceipt`/`OfficialAdRecoveryPolicy.recordedReceipt`没有原持久回执加同日新鲜精确增量；`DailyAdTaskController.run`的`allResolved`与`OfficialAdQuota.canReserve`阻止新派发。后两个是防重播门槛，**不能直接当成应删除的BUG**。

**仍未知：**积分分类与每日计数的准确业务对应关系、服务端/持久缓存是否异常、计数观察是否遗漏真实更新、官方是否提供可用于逐次归属的合法证据。现有证据不足以断言ANA服务端故障或Tuffy判断过严。

**优化目标：**沿原ADS调用链检查观察时序和结果证据；如能取得可唯一归属的权威结果，用原FULL事务幂等恢复；如能证明新会话结算独立，才允许继续。找不到新证据时停止重复冷启动/刷新，保留外部待确认状态。不得以强行放行作为“修复”。

证据：`H14_ADS_RESCUE_FINAL_REPORT.md`、`H14_ADS_SETTLEMENT_FINAL_REPORT.md`、`H14_NOV7_NATURAL_FINAL_REPORT.md`、最新验证JSON及`work/nov7-afternoon-review/daily22-timeline.log`。

### P0-02 CAR40超时复核无法生成有效探针，恢复覆盖不完整

Nov7 CAR40原attempt：`CHe1c48440-518d-4b54-9f14-ea2f2bdc97bc / index1 / startedAt1793985097984`。预派发记录mission_id=3623，首次接取前participation ID为空。02:11:38进入AD_PLAYING；02:13:14记录`ADSTALL probe_unavailable=unsupported_or_shape`，没有有效stallReview用于下一步恢复。

当前只读官方详情：2026/11/7、02:26参加、mission_id=3623、user_mission_id=1779173544、Boost0回。这证明当前官方参加实例存在，**不能证明原02:11广告获得了哪种奖励**，更不能倒填派发前身份。该普通详情没有可安全开始Boost的现成入口。

**待定位：**探针内部究竟是SDK/Window分类不支持、页面形状不匹配、节点缺失还是其他前置条件；此记录发生在有效截图采样之前，不能把早期“截图间隔碰撞”猜测当成该次根因。没有足够原SDK/节点快照，不能随意扩大强重启适用范围。

**优化目标：**在既有异常分支保留最小失败原因和窗口形状证据，明确“拿不到探针”后的有界退出/恢复选择；正常广告路径不增加截图。恢复必须绑定原attempt，且不能重复派发或放弃当前资格。旧CAR40/CAR20一次退出授权不适用于本实例。

### P1-01 登录额外100pt误关已修，但尚未真实领取验收

00:03:04原始节点同时出现`ログインボーナス`、连续日历、`動画を見てさらに100ptをGET`和`閉じる`，日志随后为`observed_native_image_close`。官方基础登录20pt已经到账，漏的是额外视频机会。

已安装修复：同一快照取标签与ID、优先原生领取CTA、点击前刷新节点、已知登录上下文禁止通用X；控件暂不完整沿用既有35秒有界等待并保留现场。官方APK仍有领取相关ID，不能说已证明是ANA删除ID造成。

**验收缺口：**下一次真实弹窗→领取→广告关闭→官方奖励确认→持久化→继续Routine；还有缺控件、已有登录UNKNOWN和跨日情形。当前没有真实弹窗，不能为了测试再造广告或声称DEVICE_PASS。

证据：`work/nov7-afternoon-review/midnight-reward-before-close.txt`、`official-login-layout.txt`、`points/today-history-proof.json`。修改函数集中在`StartupPolicy`、`StartupFlow`，不应再写一套签到控制器。

### P1-02 原生闹钟触发通过，但全天无人值守业务未通过

Nov7 00:03:00.029 AlarmReceiver→00:03:00.227 Routine reason=alarm已证实，旧日UNKNOWN未阻止新日21次ADS确认。触发时六个出行类别全部记为DISABLED_BY_USER；没有证据说明是谁、何时改了开关，不应归罪用户或擅自改配置。

后来23项挑战来自多个reason=manual的运行，不能与最初alarm拼成“自然全天成功”。最新a3d6137还没有完整自然运行记录；闹钟存在、能锁屏、能启动并不等于24项与25/25闭环。

**待验收：**从自然触发时配置快照开始关联单次run及合法恢复，分开统计alarm与manual成果，确认锁屏/断USB后的权限、身份诊断、FGS、清理和Bot闭环。没有真实记录则NOT_TESTED，不由电脑手动启动冒充。

### P1-03 有界重启与官方续接已有成功案例，但不是所有异常都能恢复

Nov7 01:43自行车1km的AppLovin：有效stallReview，quiet约30.5秒；任务恢复一次、官方续接一次，绑定原身份后官方NO_GAIN，然后新attempt正常继续到5/5。这是实际有效能力，应保留。它属于manual源头，不是自然全天成功。

匿名`再開する`导航与奖励结算必须分开。已保存的原身份和恢复意图可支持一些场景；多个历史UNKNOWN、缺身份或缺官方结果仍然不能用“最近一条”推断。需要审查恢复意图/预算/Journal在进程死亡、日期变化后是否一致，而非重新增加控制器或删除强制重启。

### P1-04 AppLovin短暂图标的业务含义容易被误判

旧Nov4调查已实际证明：所谓约5秒“Skip”左侧图标最终进入Google Play，而非直接Skip。受控返回后才出现真正X，随后返回ANA。该实例验证的是“商店跳转→返回→X”，**没有验证普适的空白唤醒→Skip→X**。

这是识别边界，不应按用户最初假设加入“无X点中央”“点击左上图标”“任意AppLovin空白唤醒”。未来若要支持短暂安全控件，应基于真实SDK、语义/节点/阶段证据和有界预算，实时核对最新Bounds，不复用旧坐标，也不引入正常路径频繁截图。

证据：`outputs/APPLOVIN_SKIP_FINAL_REPORT.md`。Google真正Skip→X与Pangle既有安全返回应保留，不能把Google规则直接套给AppLovin素材。

### P1-05 历史不确定记录、手动操作与恢复语义仍需统一解释

最新正式逻辑SQLite快照有**11条明确state=UNCONFIRMED的广告/登录记录，另有2条Nov2旧阶段未确认挑战广告**。因此仅按UNCONFIRMED字符串计数会漏掉历史保护对象。清单见`H14_PENDING_INVENTORY_20261107.json`。这是导出范围内的分类，不声称已穷尽全部其他插件业务。

`rewardConfirmed=true`可表示观察到原生奖励UI，而`completed=false/state=UNCONFIRMED`仍表示正式结算未通过。Nov6与Nov7每日pending均有此组合；日志或报表若只看rewardConfirmed容易夸大完成。旧10/28的index26也是历史原记录，不得归一化改号或作为当前可超限的授权。

**优化方向：**优先澄清现有字段在UI/日志/导出中的说明和统计口径；保留原字段与事务，不为改名增加schema。用户手动操作后应重读官方现状，但手动看广告造成的计数增长不能事后强归属原pending。

### P1-06 安装后LOW_MEMORY已发生一次，根因和完整性能尚未证明

16:27:32旧进程退出是PACKAGE_UPDATED；16:27:36短暂新BootReceiver进程pid19926被记录LOW_MEMORY；16:27:37无障碍连接恢复。两种退出原因不能混算。退出记录PSS/RSS0表示没有有效样本，不是零分配；没有退出前系统内存与Bitmap对象证据。

最新空闲PSS93412KiB约91MiB，不是峰值。code43历史成功约95分56秒、PSS约78–121MiB、376次视觉请求、约1.63GiB累计Bitmap分配、正常PNG0、业务LOW_MEMORY0，只能作为那次历史基线；累计分配不是实时占用或内存泄漏证据。

**优化方向：**先审查安装/BootReceiver/服务重连重复工作是否有实际证据，再决定是否需改；下一真实业务用现有计数测峰值/视觉次数/累计分配，避免新增常驻采样。不能承诺“内存已优化”。

## 3. 次要问题和工程不完善处

| 编号 | 问题/缺口 | 已知边界与建议 |
|---|---|---|
| P2-01 | 红色“设备缺少必要功能”提示仍有历史证据 | 尚未定位本机具体功能，未证明导致ADS次数异常；不推断封禁、不伪造传感器、不修改官方App。按需只读核实 |
| P2-02 | Windows时间曾快约7214秒 | 手机与当时外部时间源一致；Windows服务NoSync、普通修复被权限拒绝，未完成校时。最新偏差未再量；主机文件时间不能当业务时间，保留原时间戳与来源 |
| P2-03 | 身份诊断依赖DUMP与使用情况权限 | 用户仅授权ANA诊断；两权限已granted但usage AppOp记录为default。须验证App自身UID、锁屏/重启后的真实读取，不能拿ADB shell能力当手机权限证明；禁止扩到其他App历史 |
| P2-04 | 同versionCode44多候选易混淆 | APK/commit/SHA/证书必须一一对应；不要凭versionCode挑包。旧92、d519和a3d成果分开；无需仅为管理方便新增code45 |
| P2-05 | 同日完成证据复用的适用范围 | 已验证同日同配置完整范围；新日/配置变化/未完整扫描不跳过。若官方同日新增任务，缓存如何失效仍需审查，当前未复现为BUG；必要时有界复查，不能退回每次全扫已接取列表 |
| P2-06 | 已修复路径仍有整体验收空白 | “重置普通扫描状态”、失败项跳过、人工接取后继续等用例不能仅因652项通过全部宣称真机通过；按证据逐项确认，不为造失败而制造真实UNKNOWN |
| P2-07 | 退出原因采集器曾误统计 | CRLF/空行导致旧记录被误计为新增；最终证明已改为完整ApplicationExitInfo块比较。旧`work/nov7-afternoon-review/collect_system.py`仍可能误计，复用前需修正或运行verify_exit_timeline校核；原始证据不改 |
| P2-08 | Bot/无人值守说明不够精细 | 最新Bot16:30:50为API成功，不证明已读；不重复发送旧结果。工程heartbeat暂停≠手机原生闹钟关闭，不能承诺未配置的后台工程续跑 |
| P2-09 | 源码维护与冗余 | 已删onlyIsolatedRemaining（只有声明无调用），已搬移复用回执证明。其余重复恢复/导航逻辑是否可删须先证明调用关系，不能以状态数量多就整体重写 |
| P2-10 | 其他插件历史问题 | 牛排旧奖励重复通知等仅为历史待核实项，本轮没有新验证；与ANA广告结算分开，不扩展本轮修改范围 |

## 4. 已修复或已通过的能力：不要当作未解决问题重新开发

- JOIN按交通方式Tab决定类别，仅按公里数与用户配置筛选；不每次全扫已接取列表。中断后重读可接取列表，不追查普通已消失任务；消失不等于伪造接取成功。Boost阶段必要已接取查询保留。
- Google假Close与真实Skip/X阶段、Pangle既有关闭/返回有真机历史成果；不能为AppLovin实验删掉。
- 挑战派发前官方身份绑定与原FULL事务已取得真机成果；当前CAR40不能事后补齐派发前不存在的参与ID。
- 同日已完成类别直接复用范围证据；隔离项保留PARTIALLY_COMPLETED。最新继续路径实际挑战UI调用0，直接ADS，明确阻塞并停止FGS。
- 历史UNKNOWN不会无条件跨日全局阻塞：Nov7真实完成了21次新日ADS；不能据旧Nov6报告断言跨日隔离仍完全未测。
- 有界ANA任务恢复、官方续接和NO_GAIN后新attempt继续存在真实案例，不能为了降低风险直接取消。
- 正式导出CRC、SHA及逻辑SQLite integrity已验证；当前配置、原回执与UNKNOWN前后保持，NAS已认证解密读回。

## 5. 建议Claude的工作次序与验收

1. 核对新鲜手机JST、安装SHA、Git和当前SDK/模态；本报告快照不能代替现场。先有界检查ADS是否有新的权威证据，不重做已完成挑战。
2. 对ADS逐次结算与后续会话独立性给出证据结论。内部观测缺陷才最小修复；如果是证据不可得/待官方确认，明确外部阻塞，不无限研究、不降低防重播门槛。
3. 定位`probe_unavailable=unsupported_or_shape`的真实前置条件，异常分支保存最小可诊断证据；保留95–300秒有进展等待和原重启预算。
4. 遇到下一真实登录弹窗，优先验收现有修复；不要为了产生新diff而再次重构。若没有机会则NOT_TESTED。
5. 使用原生下一次自然触发核对全链路：触发源、当时配置、完成数量、ADS官方计数、异常恢复、FGS停止、锁屏与Bot。运行中只允许唯一手机控制器，不由电脑抢操作。
6. 只有可复现缺陷才做独立最小提交。相关测试→必要全回归→原签名构建→安全备份和安装→真实生产入口验收。无活跃SDK与不可中断权益才安装，不能为部署牺牲资格。

验收必须分别给出`UNIT_PASS / DEVICE_PASS / ROUTINE_RESUMED / ROUTINE_DONE / NATURAL_RUN_PASS / BLOCKED / NOT_TESTED`。每个DEVICE_PASS注明覆盖范围；不得用测试数量、按钮返回true、App启动成功、闹钟存在或Bot成功替代真实业务完成。

## 6. 项目位置、证据与交付

工作区：`C:/Users/xiejiatao/Documents/Codex/2026-11-03/tuffy-h14-sol-high-tuffy-auto`。

实际源码：`H:/Tuffy_Archive/Tuffy_H12_2026-11-01T11-49-30-908Z/development`。ADB：`C:/Users/xiejiatao/android-tools/platform-tools/adb.exe`，设备serial=`pbscmzqwbiv8nfeu`。这些是本机路径，不保证Claude网页环境能访问；不能访问时只做证据审查/补丁建议，明示NOT_TESTED。

优先读取：

1. `outputs/H14_NOV7_SCOPE_LOGIN_FINAL_REPORT.md`及同名FINAL_VERIFICATION.json：最新安装、数据比对和验收。
2. `work/nov7-afternoon-review/`：原始节点、挑战/ADS时间线、登录误关、最小diff、bundle、测试、实装与回滚证明、系统退出原因。
3. `outputs/H14_NOV7_NATURAL_FINAL_REPORT.md`：自然触发与manual来源分开统计。
4. `outputs/H14_ADS_SETTLEMENT_FINAL_REPORT.md`、`H14_ADS_RESCUE_FINAL_REPORT.md`：Nov6结算调查；其中当时NOT_TESTED被后来Nov7证据更新，不能机械沿用旧状态。
5. `outputs/APPLOVIN_SKIP_FINAL_REPORT.md`：短暂图标实际进入商店，纠正假Skip假设。
6. `work/daily-natural-20261107/LATEST_ADS_SETTLEMENT_CHECKPOINT.md`：当前安全断点。
7. `work/acquisition-simplify/afternoon-final.zip`：正式逻辑快照，SHA256=`e7aeaf7b8f599cc07555d4096dc82fd50ecfd0b1c0ad51710cedb827d867dcab`；只读，不以旧备份覆盖当前数据。

最近NAS独立归档：`X:/Elements/codex/Tuffy_2026-11-02_0003/H14/nov7-scope-login-a3d6137-20261107`；AES-GCM认证与还原SHA通过。读取归档密钥、Bot Token、签名私钥没有必要时不要访问，绝不能加入交接或发到模型/外部服务。

本交接报告不包含完整私有SQLite、令牌、密钥或账号标识；pending清单仅含排障所需的本地业务身份。**不发邮件，不代用户联系官方，不自动上传原始归档。**
