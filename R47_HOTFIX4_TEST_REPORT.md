# R47 热修复4真实验收（进行中）

本版22.0.4-R47/code26是在原工程上的最小修复，保留原包名、签名、用户5/5/100/100km范围、ANA00:03和独立P1时间。当前仍安装code24，已编译通过并核对同一正式签名后，在原广告已关闭/控制器已停止的边界保数据升级。

01:03恢复Run RTeab944e8-566c-4ae7-b5c0-c899da8ea8b0因Google广告超时停止。前一任务徒歩500m已接取并5Boost完成；当前徒歩1km index5为第4Boost，原记录startedAt1793376080205、UNCONFIRMED/ad_timeout_95s。故障图与节点显示真实Close按钮，日志显示Google Skip已占用共享关闭额度，最终Close不再调度。修复只为实测Google Skip单独提供3次有界推进额度，真正Close仍3次、商店2次，阶段交替不刷新额度，原超时与官方回执断言不变。生产挑战、每日广告、启动广告三入口共用此识别。

开发者在控制器终止后仅关闭原广告一次，真实官方页面显示第5次Boost入口、300pt、4格完成。该迟到回执尚需由生产绑定核对逻辑更新原记录，不重播未知、不手工改账本。

|检查|当前状态|
|---|---|
|代码|IMPLEMENTED|
|单元|UNIT_PASS：429项，0失败/错误/跳过，61套件；旧425测试文件逐字节未改|
|正式候选安装|BLOCKED：系统INSTALL_FAILED_USER_RESTRICTED/Install canceled by user；实际仍code24|
|广告关闭修复真实复测|NOT_TESTED|
|原未知观看核对恢复|PENDING_OFFICIAL_BOUND_REVIEW|
|全日出行/25广告/退出/锁屏|INCOMPLETE|
|签到奖励|UNCONFIRMED_PRESERVED_NO_REPLAY|
|钱包独立入账|CREDIT_UNCONFIRMED|
|Android隔离|NOT_RERUN_FOR_HOTFIX，原110仅历史基线|

原始故障文件仅work/R47-midnight-watch/fault_google_walk1km_boost4及terminal；审查摘要outputs/evidence/R47_hotfix4/google_timeout_summary.json。不得把本次开发恢复宣称为自然00:03无干预整链PASS。

正式APK SHA256：18913b3f3e744ab47e7637df9621a63ee3f112ace8f91612230e528a84414d44。构建4m29成功，原证书ab39397d23fca7803fa7cd3e33125874d010874d5641e6ffd044a2a3b55f484c；正在安全边界执行-r保数据升级，尚待安装回执及恢复验证。

安装失败后保持code24及全部原数据。01:14从现有正式APK继续一次，优先核对同一条原Boost迟到回执，H4修复真实验证仍NOT_TESTED。必须由用户处理系统安装授权，获准后也只能在无活跃广告的安全边界再升级。

01:15:05现有code24生产reconcileBoundLateAdvance真实成功：原index5 startedAt1793376080205保留，reason=same_bound_attempt_stable_native_ordinal_and_reward_advance，previousFailure=ad_timeout_95s，boost3→4/300pt；没有为未知条重开广告。01:15:08仅启动此前未开始的index6（第5Boost）。新Routine RT06d0576d-b7c8-4c3b-9fe4-4d5b17b1e214，CH35c2aeea-b6be-4e7e-814c-0b3ccfbcd25d。H4安装仍BLOCKED，不能把旧版恢复当新关闭修复PASS。


## 最新真机状态（手机10/31 01:53后）
用户通过系统Files安装器在01:49:39完成22.0.4/code26升级，实际base.apk SHA256 18913b3f3e744ab47e7637df9621a63ee3f112ace8f91612230e528a84414d44与交付一致。01:52:25用户重新开启Tuffy无障碍（仅该服务）。01:53:17统一流程RT4e5c9c52-dd59-4309-800c-49ba217fd4d6恢复，生产绑定迟到回执核对原自転車1km index2（startedAt1793378519613）后boost0→1/100pt，旧记录保留；只启动下一未开始index3。故升级后数据/任务恢复为DEVICE_PASS_LIMITED，不等于全部任务完成。随后01:53:40及01:53:50系统ApplicationExitInfo再次LOW_MEMORY，H4未自动续跑；此环境/重连缺口未修复，H5正在编译。新Google关闭额度修复尚未完成单独真机回归，NOT_TESTED。
03:35后一次性兜底已安排：复用tuffy-r46改client04:36（按当前实测phone约03:35后），执行时先核手机真时/活动Run，不重复任务，不改原00:03。
