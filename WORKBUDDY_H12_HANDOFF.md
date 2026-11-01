# WorkBuddy 本地工程交接 — Tuffy H12

## 1. 直接打开的原工程（唯一开发基准）

C:\Users\xiejiatao\WorkBuddy AI\2026-10-25-19-33-44\ANA_Pocket_Auto

不是新建项目。Kotlin源码在app/src/main/java/com/anapocket/auto；界面在app/src/main/res；JVM测试在app/src/test；隔离测试在app/src/androidTest。构建文件是Groovy app/build.gradle，不是build.gradle.kts；当前无gradlew.bat，使用下述已安装Gradle。

本轮提交方式是直接修改上述原工程文件，再同步工作副本、编译、正常同签名更新。已核实原工程及父目录不是Git仓库，没有branch、commit ID、remote或push记录；不能把本地文件保存称为Git提交。无需另建或覆盖原工程，WorkBuddy直接打开该文件夹继续。若要上传到代码托管服务，应先另行核实目标仓库和忽略规则；本轮未执行Git初始化或远程上传。

## 2. Codex工作区、同步副本、交付与证据

工作区：C:/Users/xiejiatao/Documents/Codex/2026-10-27/ana-pocket-auto-25-ana-pocket

同步副本：C:/Users/xiejiatao/Documents/Codex/2026-10-27/ana-pocket-auto-25-ana-pocket/work/ANA_Pocket_Auto（副本，不要在两个目录同时改不同版本）。原工程257个app/src文件与最终编译快照、此副本已逐项哈希相同。

正式交付：C:/Users/xiejiatao/Documents/Codex/2026-10-27/ana-pocket-auto-25-ana-pocket/outputs

本轮原始证据：C:/Users/xiejiatao/Documents/Codex/2026-10-27/ana-pocket-auto-25-ana-pocket/work/R47-H12-challenge-rescan

最新正式账本：C:/Users/xiejiatao/Documents/Codex/2026-10-27/ana-pocket-auto-25-ana-pocket/work/R47-H12-challenge-rescan/final-formal-diagnostics.zip，解析结果final-formal-state.json，保留校验final-home-upgrade-preservation.json。原始证据只在本机work，不要把整个work或原工程直接公开上传。

首先完整阅读原工程四份DEVELOPMENT_STATE.md / TEST_RESULTS.md / BUG_BACKLOG.md / NEXT_ACTION.md的H12最新块，再读outputs/R47_HOTFIX12_INVESTIGATION_REPORT.md、R47_HOTFIX12_TEST_REPORT.md、R47_HOTFIX12_REAL_DEVICE_REPORT.md及R47_hotfix12_manifest.json。旧H10/H11报告仅历史，不据旧配置覆盖手机。完整进度work/R47_PROGRESS.md。

## 3. 已安装正式版本与文件

包名com.anapocket.auto；22.0.12-R47 / versionCode34。11:31:29 JST正常Files/PlayProtect更新，18:02再次pull实机base校验一致。

APK：outputs/Tuffy_Auto_Toolbox_R47_hotfix12.apk

SHA256：bc37d89472f2aa154e394401c769ce400a8ec017175d46647ec000e158da663a

正式证书SHA256：ab39397d23fca7803fa7cd3e33125874d010874d5641e6ffd044a2a3b55f484c

源码：outputs/Tuffy_Auto_Toolbox_R47_hotfix12_source.zip（285个精选文件，含本版本对应报告及四份状态文档，不含密钥或构建缓存）。清单R47_hotfix12_source_manifest.json；总校验R47_hotfix12_SHA256SUMS.txt。JVM结果R47_hotfix12_JUnit_Results.zip；隔离结果R47_hotfix12_Android_Isolation.zip。不要用后续代码覆盖H8/H10/H11冻结归档。

## 4. 本机工具和构建命令

JDK17：C:\Users\xiejiatao\android-build\jdk
Android SDK：C:\Users\xiejiatao\android-build\sdk
Gradle8.9：C:\Users\xiejiatao\android-build\gradle_8_9\gradle-8.9\bin\gradle.bat
ADB：C:\Users\xiejiatao\android-tools\platform-tools\adb.exe
项目AGP8.5.2、Kotlin1.9.24，compile/targetSDK34、minSDK24。

PowerShell（在原工程）：

```powershell
Set-Location -LiteralPath 'C:\Users\xiejiatao\WorkBuddy AI\2026-10-25-19-33-44\ANA_Pocket_Auto'
$env:JAVA_HOME = 'C:\Users\xiejiatao\android-build\jdk'
$env:ANDROID_HOME = 'C:\Users\xiejiatao\android-build\sdk'
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
& 'C:\Users\xiejiatao\android-build\gradle_8_9\gradle-8.9\bin\gradle.bat' --no-daemon --offline :app:testDebugUnitTest :app:assembleRelease :app:assembleReleaseAndroidTest
```

APK构建结果app/build/outputs/apk/release/app-release.apk；JVM XML在app/build/test-results/testDebugUnitTest。最终H12真实504测试/72套件，失败错误跳过均0；10个独立Android隔离通过，历史110未全量重跑，不能算成本次通过。完整最终日志work/build-r47-hotfix12-home-recovery-final.log（若需定位，用rg --files搜索同名）；最终home-final-build-summary.json保留真实7m5s结果。

正式签名由原工程本机keystore.properties或TUFFY_*环境变量加载；保留当前本机签名材料，禁止换debug签名。不要把keystore/、keystore.properties、local.properties、token或密码提交/上传；交付ZIP不包含这些。新机器没有原签名则应明确无法同签名构建，不能新造证书替代。

## 5. 真机安装和测试约束

先adb devices、shell date，手机JST为业务依据；不改钟。USB -r安装历史返回USER_RESTRICTED，正常路径是push正式APK到Download后用Files打开，正常更新和PlayProtect扫描；不要卸载、清数据。更新后按正常HyperOS受限设置/无障碍完整倒计时恢复，仅Tuffy Enabled+Bound；禁止secure settings、appops临时授权。

Android隔离仅在正式业务已停止且无活跃未确认广告时运行：

```powershell
& 'C:\Users\xiejiatao\android-tools\platform-tools\adb.exe' shell am instrument -w -e phase h12 com.anapocket.auto.test/com.anapocket.auto.AdTestInstrumentation
```

测试包需正常安装匹配版本。instrumentation结束会停止Tuffy，必须正常恢复无障碍；不要把这种断连冒充自然业务故障。活跃业务禁止instrumentation/UIAutomator/run-as/debug控制。

## 6. 已核实业务结果与尚未解决事项

2026-11-01 JST：WALK7、BIKE6、CAR6、MOV_ANY5，共24项接取及各5/5 Boost均已确认。挑战147次启动、144次官方回执、3次FAILED、0未确认；失败原RUN/index/start及NO_GAIN证据保留。每日固定广告独立25启动/25完成/25回执，12:38:37官方重新打开入口稳定4样本/3秒复核25→25，没有新每日观看。原签到RTacf24d6e-9b90-4167-b68e-a69f7a4d42a3/start1793458986170仍UNCONFIRMED，不能声称整日全部成功。

当前真实设置：徒歩ON5km、自転車ON5km、汽车ON40km、移动不限ON40km，新干线/飞机OFF0；ANA00:03，楽天ON09:15 generation7、牛排ON09:30 generation9，用户后续修改以手机为准。参加中只看進行中属于今天任务日的移动任务，昨天因24h判定仍显示的PROCESSING要排除，不看全部历史，也不是截止日期≤今天。

最后RUN RTe346a9c7-d32d-412b-8c6e-cf1890e4d9f0，子CHd0ae3aa3-9fec-4e2d-996e-01ea1808c819；12:38:37结果通知已posted，12:38:42自动Home，12:38:45锁屏确认。Routine DONE只是本次流程处理结束，summary和首页仍明确签到待核对，不等于整日全部官方成功。当前无障碍仅Tuffy Enabled+Bound、Crashed空。

楽天bonus、牛排darts仍UNCONFIRMED；旧10/31的daily6FAILED、7UNKNOWN不跨日重播。所有未知提交、RUN、startedAt、奖励和用户配置必须保留。

已修：不补零日期导致遗漏/空coverage误完成、首页恢复入口、独立扫描8态及安全重置、分类栏有限回顶、DailyRoutine只传剩余分类、完整官方原生回执优先于过期SDK事件、finishedAt缺失时继续按钮显示。

未解决：自动AppInfo结束ANA路线仍NOT_PASSED；连续LOW_MEMORY打断恢复排队回调仍NOT_FIXED；ANA ANR内部原因UNKNOWN。原签到不能重复新广告换成功。MOV40最近任务精确找ANA卡并恢复原观看官方NO_GAIN真实自动通过一次，不能概括全部重启路线通过。

人工辅助：安装/正常权限/恢复入口；03:36与12:19正常系统结束ANA并重开、10:45官方再開、11:20继续、11:23关闭ANR并重开。不能报告无人干预完整PASS。

## 7. 监控与后续边界

本会话有界只读采样live-01至05；04:54–10:42不声称连续观察。最后live-05在13:24按3600s结束（217样本、androidStateChanged=false），Android业务12:38已结束。不创建/恢复旧8小时或新的长期监听，不取消APK独立闹钟。

本轮没有大规模重构、未改ANA P0或P1日程、未清账、未改计数；只在官方NO_GAIN明确后才允许下一观看。用户已授权正常安装更新和自主修复，无需反复询问。真正无法安全判断的窗口/账号风控/未知广告应保留并报告。官方可选免费月初奖励偏好ANAマイル，但必须确认身份/资格/未领，不点未知兑换、购买。
