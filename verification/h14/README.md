# H14 隔离 Android 持久化验证

此目录是可选开发验证工具，不编译进正式 APK，也不是正式运行前提。

`H14LedgerProbe.java` 使用 Android ART 加载已签名候选 APK 中的实际 `ReliableLedger` 和 `AdAttempt` 类。只使用公开 Android API和候选应用类反射；不用 instrumentation、run-as、root、secure settings 或 appops。

安全边界：临时 Context 的数据库路径固定在 `/data/local/tmp/tuffy_h14_probe_20261102_<唯一标识>/`；SharedPreferences 仅返回内存中的合成 2099-01-01 记录，禁止编辑。不会取得正式 Tuffy Context、读取私有配置、启动业务、触碰 ANA 或发送通知。崩溃注入仅调用测试进程自己的 `Process.myPid()`，不得修改为其他 PID。

验证内容：Android JSON 往返及旧可选字段兼容；非破坏性迁移；任务唯一性和重复保存；确认不可回退；SQLite FULL synchronous；在生产事务内部写入后、提交前终止测试进程的回滚；提交返回后终止测试进程的保全；写入异常使后续写入被拒绝；多进程重开后原身份及另一条 UNKNOWN 保持。

宿主先用 Android SDK android-34/android.jar 编译 Java8，再用 D8 min-api30 生成独立 DEX JAR。每轮创建全新、可核验的独立临时目录，候选 APK 和探针 JAR 设为只读，使用 `CLASSPATH=<探针JAR> app_process /system/bin H14LedgerProbe <候选APK> <隔离目录> <模式> <值>` 执行。输入 APK 必须先验证 SHA256、版本和签名；每个输出与返回码都保留。

模式：`init`、`write`、`read`、`dirty_kill`、`commit_kill`、`abort`。`dirty_kill` 必须在本工具专用目录内已有合成数据库时使用。先写入 BASE，再在未提交事务中写入 UNCOMMITTED 并终止测试进程，重新读取必须仍为 BASE；COMMITTED 提交返回后终止测试进程，重新读取必须保留 COMMITTED。实际终止属于测试 SIGKILL，绝不能标作自然 LOW_MEMORY。

本轮候选的实际原始输出和设备证明保存在 H14 私有证据目录，交付时加密。正式 App 连续 LOW_MEMORY、WorkManager 调度/服务恢复、物理拔线和多自然业务日必须分别实测；本工具通过不代表这些项目通过。