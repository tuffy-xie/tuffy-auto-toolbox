# Tuffy 的自动百宝箱 — R44 归档

私人 Android 自动助手工程。当前提交归档正式版本 **19.0.0-R44 / versionCode 19**，应用包名 **com.anapocket.auto**。

R44包括ANA Pocket出行挑战接取、Boost、每日广告、原生调度、无密码锁屏启动、账号风险与验证码人工接管、Telegram和紫金小猫首页。详细历史验证与限制见 [R44 测试报告](R44_TEST_REPORT.md)。手机已安装的R45及其数据保持原状。

## 正式 APK

[下载已签名 R44 APK](releases/R44/Tuffy_Auto_Toolbox_R44.apk) · [SHA256](releases/R44/Tuffy_Auto_Toolbox_R44.apk.sha256)

SHA256：ae1f49e2a408f8e57de754a09adf1dd4f6abda06ce25a4bdce817759428811ad

此文件为原正式成品，签名证书SHA256：ab39397d23fca7803fa7cd3e33125874d010874d5641e6ffd044a2a3b55f484c。

## 构建

JDK17、Android SDK34、Gradle8.9：

~~~sh
gradle testDebugUnitTest assembleDebug
~~~

未提供正式签名时，debug使用Android默认开发签名，release输出未签名包。正式覆盖升级需在本机配置同一正式密钥；签名私钥和口令不存入仓库。可以用忽略的keystore.properties填写storeFile、storePassword、keyAlias、keyPassword，或提供TUFFY_KEYSTORE_FILE、TUFFY_STORE_PASSWORD、TUFFY_KEY_ALIAS、TUFFY_KEY_PASSWORD环境变量。

## 归档范围

app/src内的R44业务与测试文件保持原快照字节一致。发布准备仅分离构建签名凭据、扩充.gitignore，以及添加此说明、归档清单和原正式APK。没有加入新App模块或R45业务变更。

不上传原始手机日志/截图、Telegram配置、调试口令、签名私钥、应用私有数据及构建缓存。既有测试报告是历史真实验证，新的CI结果单独显示，不能代替真机奖励证明。

本次发布快照本机重新验证：311项单元，0失败/错误/跳过；debug构建成功。182个app/src文件与原R44归档逐字节一致，完整校验见ARCHIVE_MANIFEST.json。
