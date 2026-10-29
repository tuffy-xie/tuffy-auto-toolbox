package com.anapocket.auto
object TaskFailureText {
 fun short(raw:String):String=when{
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
data class ChallengeRunItem(val name:String,val boost:Int,val joined:Boolean,val unknown:Boolean)
object ChallengeRunMessage {
 fun render(date:String,items:List<ChallengeRunItem>):String{
  val done=items.filter{!it.unknown&&it.joined&&it.boost==5}.map{it.name}
  val pending=items.filter{!it.unknown&&(!it.joined||it.boost<5)}.map{it.name+"（Boost "+it.boost+"/5）"}
  val unknown=items.filter{it.unknown}.map{it.name}
  val lines=mutableListOf("Tuffy｜ANA出行任务｜"+date)
  if(done.isNotEmpty())lines.add("✅ 已接取并升满："+done.joinToString("、"))
  if(pending.isNotEmpty())lines.add("⏳ 待完成："+pending.joinToString("、"))
  if(unknown.isNotEmpty())lines.add("⚠ 待核对："+unknown.joinToString("、"))
  if(items.isEmpty())lines.add("本次没有新增任务，详情见执行记录")
  return lines.joinToString("\n")
 }
}
