# R47午夜热修复验收（进行中）

正式候选22.0.3-R47/code25，APK SHA256 3fc926abc3000c0eaa421f546485e38b5b4cda51f5b752a8bb6dc14c93564f07。本候选尚未安装：当前code24正在处理真实挑战广告，安装必须等待安全空闲边界。

|项目|状态|证据与限制|
|---|---|---|
|代码增量实现|IMPLEMENTED|签到回原offer及时保留未知；安全关闭已知未知弹窗不重播；分类仅采信前台窗口，语义点击，遮挡有界等待；原生网页精确toolbar及稳定container识别|
|JVM|UNIT_PASS|425/0失败/0错误/0跳过，60套件；原R47所有测试文件与原源码ZIP逐字节一致，新增10回归|
|Android隔离测试|NOT_TESTED_FOR_HOTFIX|原R47的110项为历史基线，本热修复未重跑，不混写|
|自然00:03触发/解锁|DEVICE_PASS_LIMITED|00:03:00.043闹钟、00:03:01.146解锁；此前Awake，不证明息屏或深度Doze|
|原签到广告|UNCONFIRMED|真实播放并关闭，未获新回执；原记录保留，不重播|
|未知签到弹窗恢复|DEVICE_PASS|H1真实native_offer_dismissed_no_replay，未改旧广告startedAt|
|前台分类导航|DEVICE_PASS_LIMITED|H2于00:53:44选中WALK，进入500m任务；尚未完成全分类回归|
|500m接取及Boost|REAL_TASK_PASS_PARTIAL|00:55:00接取确认、00:55:20 Boost1、00:56:08 Boost2；挑战奖励为实际出行完成后的预期值，非钱包入账|
|加载后原生网页自动关闭|NOT_TESTED|H3补稳定webView_container已通过单元，真实页面曾由开发者在停止边界手动关闭，不能算自动PASS|
|全日挑战/25广告/最终退出锁屏|IN_PROGRESS|当前恢复Run仍在执行，不能声明全部完成|

当前用户设置保留5/5/100/100公里、四ON两OFF，ANA00:03/23:58保护及各App独立09:15/09:30。没有更改手机时间、清除账本或卸载。自然首Run与人工恢复分开：当前Run RTeab944e8-566c-4ae7-b5c0-c899da8ea8b0。原始截图/节点仅work私有，审查后摘要在outputs。
