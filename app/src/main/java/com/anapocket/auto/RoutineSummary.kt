package com.anapocket.auto

import android.content.Context

object RoutineMessage {
 fun render(date:String,completed:List<String>,pending:List<String>,disabled:List<String>,unknown:List<String>,ads:String,login:String,reason:String,stopped:Boolean):String{
  val all=completed.isNotEmpty()&&pending.isEmpty()&&unknown.isEmpty()&&login=="已领取"
  val lines=mutableListOf("Tuffy｜ANA "+(if(unknown.isNotEmpty())"结果待核对" else if(stopped)"任务暂停" else if(all)"今日任务完成" else "本次任务结束")+"｜"+date)
  if(completed.isNotEmpty())lines.add("✅ 已完成："+completed.joinToString("、"))
  if(pending.isNotEmpty())lines.add("⏳ 待完成："+pending.joinToString("、"))
  if(unknown.isNotEmpty())lines.add("⚠ 待核对："+unknown.joinToString("、"))
  if(login!="已领取")lines.add("签到："+login)
  lines.add("🎬 每日广告："+ads)
  if(disabled.isNotEmpty())lines.add("➖ 未开启："+disabled.joinToString("、"))
  if(reason.isNotBlank())lines.add("原因："+reason.take(100))
  return lines.joinToString("\n")
 }
}
object RoutineSummary {
 fun build(c:Context,date:String,outcome:RoutineOutcome,runId:String):String{
  val s=Prefs.loadBlocking(c);val a=ChallengeStore.rows(c,date);val rows=(0 until a.length()).map{a.getJSONObject(it)}
  val done=mutableListOf<String>();val pending=mutableListOf<String>();val disabled=mutableListOf<String>();val unknown=mutableListOf<String>()
  for(cat in listOf(Cat.WALK,Cat.BIKE,Cat.CAR,Cat.MOV_ANY,Cat.SHINKANSEN,Cat.FLIGHT)){
   val rr=rows.filter{it.optString("cat")==cat.name};val unresolved=rr.any{it.optString("state") !in setOf("BOOST_MAX_CONFIRMED","BOOST_MAX_OBSERVED")}
   when{!s.catOn(cat)->disabled.add(cat.title);unresolved->unknown.add(cat.title);ChallengeScanStore.complete(c,date,cat,s.kmOf(cat))->done.add(cat.title+if(rr.isEmpty())"（范围内无可接任务）" else "（均5/5）");else->pending.add(cat.title)}
  }
  val safety=AccountSafetyStore.pending(c)
  if(safety!=null)unknown.add(safety.optString("title"))
  val ls=LoginBonusStore.read(c,date)?.optString("state");val login=when(ls){"REWARD_CONFIRMED"->"已领取";"NOT_PRESENT"->"本次未出现弹窗";"UNCONFIRMED","FAILED"->"领奖结果待核对";else->"未检查"}
  if(ls=="REWARD_CONFIRMED")done.add(0,"签到") else if(ls in setOf("UNCONFIRMED","FAILED"))unknown.add("签到")
  val counts=DailyAdStore.counters(c,date);val official=OfficialAdStore.counter(c,date);val own=DailyAdStore.allAttempts(c,date).filter{it.runId==runId||it.runId==RoutineStore.read(c,date)?.optString("adRunId")}
  val ads=if(!s.adEnabled){disabled.add("每日广告");"未开启"} else {
   if(official!=25)pending.add("每日广告")
   if(counts.unconfirmed>0)unknown.add("广告"+counts.unconfirmed+"次")
   (official?.let{"官方"+it+"/25"}?:"官方次数未核对")+"；本机确认"+counts.completed+"次"
  }
  return RoutineMessage.render(date,done,pending,disabled,unknown,ads,login,if(safety!=null)"请手动处理后回Tuffy复核" else if(outcome.state in setOf("BLOCKED","STOPPED"))TaskFailureText.short(outcome.detail) else "",outcome.state in setOf("BLOCKED","STOPPED"))
 }
}
