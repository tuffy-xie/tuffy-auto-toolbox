# Tuffy 的自动百宝箱 R46

基于R45增量开发。正式21.0.0-R46/code21已同签名、保留数据安装；不是所有新增业务均已通过真机验收。详细事实见R46_TEST_REPORT.md及四份状态文档。

当前ANA每日00:03 JST，保护23:58；六类/公里/加密Telegram/账本/风险人工/本机唤醒锁屏保留。两新增模块有各自的开关、时间、账本、通知、独立AlarmManager与WorkManager身份，并由统一UI权限仲裁，ANA最高优先。楽天默认关闭保存09:15，牛排默认关闭保存09:30；需要使用每日定时可在各自入口单独开启。

10/30真实结果：ANA24项5Boost与官方25/25及登录已确认；牛排飞镖官方1日元结果但钱包入账未另证；乐天三张任务卡均達成、两次普通免费抽奖均未中奖。额外bonus一次Start+Stop后官方加载错误，保留UNCONFIRMED，不能重抽该未知机会。下一真实日期一次性开发跟进目标10/31 04:45 JST，先等待ANA结束，再验连续bonus回执。旧未知不会清除，日期用手机Asia/Tokyo，不改系统日期。

最终404项单元与70 Android隔离检查通过；真实独立闹钟/重启/优先抢占/串行恢复/原生90秒从息屏锁屏到ANA/正式升级保留数据/已完成任务跳过与Home锁屏有证据。物理拔线整夜、全部SDK及bonus完整结果仍未验收。

构建：JDK17、Android SDK34、Gradle8.9（原工程无新增模块）；bundled MLKit日语16.0.1位于单一:app中的独立:checkin_ocr进程。依赖未缓存时需正常访问Google/Maven下载。

- 单元：gradle testDebugUnitTest
- 本机开发包：gradle assembleDebug
- 正式：先配置本地keystore.properties或TUFFY_KEYSTORE_FILE/TUFFY_STORE_PASSWORD/TUFFY_KEY_ALIAS/TUFFY_KEY_PASSWORD，再gradle assembleRelease；缺私钥时正式构建拒绝静默退回调试签名。
- 原正式签名和用户设备数据需要保留；不得卸载来更新。源码不含私钥、真实Token、local.properties、Gradle缓存、用户账本或原始手机日志/会员截图。正式包不含DebugReceiver。

正式APK SHA256：490b4c3a76f04860dba0bae307a01fdf24e64b2ca75269a48b1a5eea4e1790f0。
正式证书SHA256：ab39397d23fca7803fa7cd3e33125874d010874d5641e6ffd044a2a3b55f484c。
源代码与正式包对应，签名私钥由原工程本地保留，不能公开上传。原始故障包仅私有保存，交付另含审查证据ZIP。
