package com.anapocket.auto
object TaskFailureText {
 fun short(raw:String):String=when{
  raw.contains("recorded_daily_receipt_")||raw.contains("原每日广告") -> "每日广告受阻：原观看的官方结果仍待确认；UNKNOWN保留，已停止且未重播"
  raw.contains("login_blank_or_original_scene_unconfirmed_no_replay")||raw.contains("startup_ad_timeout_300s") -> "签到广告黑屏或原现场未确认，记录已保留，未重复观看"
  raw.contains("existing_login_task_unavailable_no_replay") -> "原签到任务未能恢复，记录已保留，未重复观看"
  raw.contains("原广告界面") -> raw.substringAfterLast("；").let{if(it.contains("原广告界面"))it else raw}.take(150)
  raw.contains("无障碍服务已断开")||raw.contains("accessibility_disconnected") -> "无障碍服务已断开，进度已保存，请正常开启服务后核对"
  raw.contains("original_ad_") -> TelegramText.reason(raw)
  raw.contains("account_safety")||raw.contains("人工")->"账号保护已暂停任务，请手动处理后复核"
  raw.contains("secure_lock_requires_owner")->"手机设置了安全锁屏，请由本人解锁"
  raw.contains("nonsecure_unlock_timeout")->"请允许HyperOS锁屏显示、后台弹出界面后重试"
  raw.contains("counter_jump")||raw.contains("次数跳变")->"广告次数发生变化，结果已保留待核对"
  raw.contains("网络")||raw.contains("network",true)->"网络不可用，进度已保存"
  raw.contains("锁屏")||raw.contains("keyguard")->"手机未解锁或无障碍未连接"
  raw.contains("pending_category_not_selected")->"有未结束的出行任务，请检查该分类设置"
  raw.contains("category_")->"出行分类页未能打开，进度已保存"
  raw.contains("广告加载")||raw.contains("load_failure")->"广告暂时无法加载，进度已保存"
  raw.contains("unconfirmed")||raw.contains("未确认")||raw.contains("待核对")->"观看结果需要核对，请查看执行记录"
  raw.contains("停止")||raw.contains("user_stopped")->"已按请求停止，进度已保存"
  else->"任务已暂停，原因详见执行记录"
 }
}
