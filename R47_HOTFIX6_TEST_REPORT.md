# R47 H6 真实测试报告

版本22.0.6-R47/code28。APK SHA256 cf35ea5dd2d3b0d3f94c22a32fa5a08af12ed7e755617a77b0761fe1bd04dd03。根因证据为BIKE5km内两条不同广告实际出现官方“広告表示に失敗しました／アプリの再起動をお試しください”。原index4已通过官方恢复证明NO_GAIN，30秒冷却后补偿index5成功、index6成功，index7又出现同一加载错误，04:51:58原控制器BLOCKED。第二条原记录已于05:04:10生产官方恢复确认NO_GAIN，未重播原条。

仅增加已有一次官方NO_GAIN且来源为加载错误时对第二条绑定加载错误的有限核对；必须再次获得官方原生NO_GAIN、保留失败和原startedAt、冷却后才允许第二次替代。普通6次、既有一次替代7次保持；两个独立加载失败都证明NO_GAIN才最多8次，第三次/未知/opaque/初次接取不放宽。每任务仍5Boost，daily25及配置不变。第8条若随后中断，仍需真实官方结果核对，通用旧恢复上限未扩大；不能据点击推断收益。

|检查|结果|
|---|---|
|实现|IMPLEMENTED|
|JVM|UNIT_PASS：440项、63套件、0失败/错误/跳过；旧435测试文件逐字节未修改|
|正式构建|PASS：5m26s，正式证书保持|
|Android隔离|NOT_RERUN_FOR_HOTFIX（历史110不算本版）|
|安装|DEVICE_PASS：05:01:45系统普通更新，实机base.apk SHA与交付相同，原签名和数据保留|
|第二条官方NO_GAIN核对及恢复|DEVICE_PASS：05:04:10核原index7 NO_GAIN并冷却30秒，05:04:59 index8官方回执确认，任务BOOST_MAX_CONFIRMED/5Boost|
|全日挑战/每日25/通知退出锁屏|INCOMPLETE：自行车5km已完成，继续后续分类|
|钱包实际到账|UNCONFIRMED|

私有原始现场保存在work/R47-midnight-watch/fault_official_load_failure_0450及fault_second_load_error_bike5km_0452；审查摘要见outputs/evidence/R47_hotfix6/two_distinct_official_load_errors.json。原R47自然签到UNKNOWN保留，恢复开发过程不等同自然无人干预全链通过。H5真实自动LOW_MEMORY恢复仍未发生，不人为杀广告制造PASS。

05:05真机结果：原index4/7保留FAILED及原startedAt，两条均officialNoGain；新增index8正常关闭并取得官方回执。合计8条启动/6条确认（1接取+5Boost）/2条官方证明无收益，未添加第9条。预期挑战奖励450，不是钱包到账。新Run RTe18ebe8c-ddfb-4dda-b070-420f178d0167，CH44e5eae3-dbec-4930-967a-eb66273fdbd8。
