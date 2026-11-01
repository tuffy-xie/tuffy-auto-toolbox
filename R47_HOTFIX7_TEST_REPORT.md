# R47 H7 浏览器返回修复测试报告

22.0.7-R47/code29，候选未安装。APK SHA256 d5a2146358be5e568ea479bcdb0ec71cde8e31f9270562a81215f43063067cc3。实际daily广告#4在05:56:31观察到Pangle后打开Chrome推广页并停留，旧驱动只覆盖商店返回。私有原图daily4_chrome_0558.png、系统activity栈和fault日志已保。05:59开发者仅普通Back一次回原Pangle，原控制器05:59:36核同条官方回执，未重新观看。该人工辅助不是本版自动返回PASS。

本版仅新增同条已观察Pangle→5秒内ChromeTabbedActivity→稳定2秒后一次Back；必须原ANA UI所有权/run/date仍有效且无停止/风险，旧回调/其他App/陈旧链/未知浏览器/第二次返回不授权。既有广告预算、计数和原生奖励核对不变。

|检查|状态|
|---|---|
|代码|IMPLEMENTED|
|构建/447单元|UNIT_PASS：447/64套件0失败错误跳过，旧440测试未改，构建5m8s成功|
|Android隔离|NOT_RERUN_FOR_HOTFIX|
|安装|NOT_INSTALLED|
|真实自动浏览器返回|NOT_TESTED|
|原#4人工返回后官方回执|DEVICE_PASS_LIMITED_DEVELOPER_ASSISTED|
|全日流程|INCOMPLETE：出行范围完成，daily官方5/25，第6条官方加载失败BLOCKED|
|钱包到账|UNCONFIRMED|

旧H6 code28仍安装。daily第6条另一独立故障需后续基于确切native加载失败和新鲜官方次数的有限补齐，当前不能直接重看或把失败改成功。H7修复先留独立APK/源码/test记录，避免不同版本源包错配。
