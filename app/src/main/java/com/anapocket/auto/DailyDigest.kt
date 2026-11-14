package com.anapocket.auto

import android.content.Context
import androidx.work.*
import java.util.Calendar
import java.util.TimeZone
import java.util.concurrent.TimeUnit

data class DigestAna(val done:Boolean,val challengesDone:Int,val challengesTotal:Int,val official:Int?,val adsOn:Boolean,val login:String,val travelUnknown:Int,val adUnknown:Int,val reason:String,val medal:String?=null)
data class DigestApp(val title:String,val finished:Boolean,val done:Int,val total:Int,val open:List<String>,val reward:String,val hold:Boolean)

/**
 * One Telegram report per day instead of one per run. Automatic retries and resumed runs stay on the phone;
 * Telegram gets the day's result once, one follow-up if ANA finishes after it, and alerts that need a person.
 */
object DigestPolicy {
    const val DEADLINE_HOUR=10
    fun ready(ana:DigestAna?,apps:List<DigestApp>)=(ana!=null||apps.isNotEmpty())&&(ana==null||ana.done)&&apps.all{it.finished}
    /** 10:00, or an hour after the latest enabled check-in when that is later; never after 23:00. */
    fun deadlineMinutes(checkinMinutes:List<Int>)=maxOf(DEADLINE_HOUR*60,(checkinMinutes.maxOrNull()?:0)+60).coerceAtMost(23*60)
    fun loginLabel(state:String)=when(state){"REWARD_CONFIRMED"->"签到已领";"NOT_PRESENT"->"签到无弹窗";"UNCONFIRMED","FAILED"->"签到待核对";else->"签到未检查"}
    fun anaLine(a:DigestAna):String {
        val parts=mutableListOf<String>()
        if(a.challengesTotal>0)parts.add("挑战 ${a.challengesDone}/${a.challengesTotal}")
        if(a.adsOn)parts.add("广告 "+(a.official?.let{"$it/25"}?:"未核对"))
        parts.add(a.login)
        a.medal?.let{parts.add(it)}
        if(a.travelUnknown>0)parts.add("出行 ${a.travelUnknown} 次结果未确认")
        // Once the official count is 25/25 an unconfirmed ad changes nothing; it stays in the phone's records.
        if(a.adsOn&&a.adUnknown>0&&a.official!=DAILY_AD_LIMIT)parts.add("广告 ${a.adUnknown} 次结果未确认")
        return (if(a.done)"✅" else "⚠️")+" ANA："+parts.joinToString(" · ")+(if(!a.done&&a.reason.isNotBlank())"\n　"+a.reason else "")
    }
    fun appLine(p:DigestApp):String = when {
        !p.finished -> "➖ ${p.title}：今天还没执行"
        p.open.isEmpty() -> "✅ ${p.title}：${p.done}/${p.total} 完成"+(if(p.reward.isNotBlank())" · ${p.reward}" else "")
        else -> "⚠️ ${p.title}：${p.done}/${p.total} 完成 · "+p.open.joinToString("、")
    }
    private fun day(date:String)=date.substring(5).replace('-','/')
    fun render(date:String,ana:DigestAna?,apps:List<DigestApp>,safety:String?):String {
        val lines=mutableListOf("Tuffy｜"+day(date)+" 日报")
        ana?.let{lines.add(anaLine(it))}
        apps.forEach{lines.add(appLine(it))}
        val todo=mutableListOf<String>()
        safety?.let{todo.add("ANA账号保护（$it）")}
        if(ana!=null&&!ana.done&&safety==null)todo.add("ANA未完成，打开Tuffy查看")
        apps.filter{it.hold}.forEach{todo.add(it.title+"需要人工验证")}
        lines.add(if(todo.isEmpty())"无需处理" else "需要你处理："+todo.joinToString("；"))
        return lines.joinToString("\n")
    }
    fun followUp(date:String,ana:DigestAna)="Tuffy｜"+day(date)+" 补充\n"+anaLine(ana)
}

object DailyDigest {
    private fun sp(c:Context)=c.getSharedPreferences("tuffy_daily_digest_v1",Context.MODE_PRIVATE)
    private val travel=listOf(Cat.WALK,Cat.BIKE,Cat.CAR,Cat.MOV_ANY,Cat.SHINKANSEN,Cat.FLIGHT)
    private val maxed=setOf("BOOST_MAX_CONFIRMED","BOOST_MAX_OBSERVED")

    fun ana(c:Context,date:String):DigestAna? {
        val s=Prefs.loadBlocking(c);if(!s.enabled)return null
        val cats=travel.filter{s.catOn(it)}
        val all=ChallengeStore.rows(c,date);val rows=(0 until all.length()).map{all.getJSONObject(it)}.filter{r->cats.any{it.name==r.optString("cat")}}
        val official=OfficialAdStore.counter(c,date)
        val login=LoginBonusStore.read(c,date)?.optString("state").orEmpty()
        val safety=AccountSafetyStore.pending(c)
        val travelDone=cats.all{ChallengeScanStore.complete(c,date,it,s.kmOf(it))}&&rows.all{it.optString("state") in maxed}
        // An unconfirmed login ad is never replayed and nobody can act on it, so it shows in the line but does not hold the day open.
        val done=!AutoAccessibilityService.isRunning&&safety==null&&travelDone&&(!s.adEnabled||official==25)&&login.isNotEmpty()
        val routine=RoutineStore.read(c,date)
        val reason=when{AutoAccessibilityService.isRunning||routine?.optString("state")=="RUNNING"->"仍在执行";routine==null->"今天还没运行";else->TaskFailureText.short(routine.optString("detail"))}
        return DigestAna(done,rows.count{it.optString("state") in maxed},rows.size,official,s.adEnabled,DigestPolicy.loginLabel(login),
            rows.count{ChallengeStore.unconfirmedAd(it)},DailyAdStore.counters(c,date).unconfirmed,reason,MedalVideoStore.label(c,date))
    }
    fun app(c:Context,date:String,app:CheckinApp):DigestApp? {
        val st=CheckinStore(c,app);if(!st.config().enabled)return null
        val rows=st.all(date)
        val finished=rows.isNotEmpty()&&rows.none{it.optString("state")=="RUNNING"}&&CheckinRuntime.activeApp!=app&&st.pendingDate()!=date
        val open=st.tasks().mapNotNull{t->st.read(t,date)?.optString("state").takeIf{it !in setOf("SUCCESS","ALREADY_DONE")}?.let{CheckinStore.taskLabel(t)+CheckinStore.stateLabel(it)}}
        val reward=rows.filter{!it.isNull("rewardAmount")&&it.optString("rewardAmount") !in setOf("","0","null")}.joinToString("、"){it.optString("rewardAmount")+" "+it.optString("rewardType")+(if(it.optBoolean("rewardConfirmed"))"（已到账）" else "（次日入账）")}
        return DigestApp(app.title,finished,st.tasks().size-open.size,st.tasks().size,open,reward,st.hold().isNotBlank())
    }
    private fun nowMinutes()=Calendar.getInstance(TimeZone.getTimeZone("Asia/Tokyo")).let{it.get(Calendar.HOUR_OF_DAY)*60+it.get(Calendar.MINUTE)}

    /** Called after every ANA or check-in run and hourly; sends the day's report at most once. */
    @Synchronized fun evaluate(c:Context) {
        try {
            val date=TokyoDate.of();val ana=ana(c,date);val apps=CheckinApp.entries.mapNotNull{app(c,date,it)}
            if(ana==null&&apps.isEmpty())return
            val p=sp(c);val sentKey="sent_$date"
            if(!p.contains(sentKey)){
                val deadline=DigestPolicy.deadlineMinutes(CheckinApp.entries.map{CheckinStore(c,it).config()}.filter{it.enabled}.map{it.hour*60+it.minute})
                if(!DigestPolicy.ready(ana,apps)&&nowMinutes()<deadline)return
                check(p.edit().putBoolean(sentKey,ana==null||ana.done).commit())
                TelegramNotifier.enqueue(c,"DIGEST_$date",DigestPolicy.render(date,ana,apps,AccountSafetyStore.pending(c)?.optString("title")))
                Logger.i("DIGEST","sent date=$date anaDone="+(ana?.done)+" ready="+DigestPolicy.ready(ana,apps))
            }else if(!p.getBoolean(sentKey,true)&&ana?.done==true&&!p.contains("followup_$date")){
                check(p.edit().putBoolean("followup_$date",true).commit())
                TelegramNotifier.enqueue(c,"DIGEST_FOLLOWUP_$date",DigestPolicy.followUp(date,ana))
                Logger.i("DIGEST","follow-up date=$date ana done after report")
            }
        }catch(e:Exception){Logger.w("DIGEST","check failed: "+e.javaClass.simpleName)}
    }
    /** Something only a person can fix; the delivery ledger keeps it to one message per kind per day. */
    fun alert(c:Context,kind:String,text:String)=TelegramNotifier.enqueue(c,"ALERT_"+kind+"_"+TokyoDate.of(),text)
    fun schedule(c:Context) {
        runCatching{WorkManager.getInstance(c).enqueueUniquePeriodicWork("tuffy_daily_digest",ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<DigestWorker>(1,TimeUnit.HOURS).build())}
    }
}

class DigestWorker(ctx:Context,params:WorkerParameters):Worker(ctx,params){
    override fun doWork():Result{DailyDigest.evaluate(applicationContext);return Result.success()}
}
