package com.anapocket.auto

import android.content.Context
import android.os.PowerManager
import org.json.JSONArray
import org.json.JSONObject

object RoutineStore {
    private fun sp(c:Context)=c.getSharedPreferences("ana_daily_routine_v1",Context.MODE_PRIVATE)
    @Synchronized fun read(c:Context,date:String=TokyoDate.of()):JSONObject?=sp(c).getString("day_"+date,null)?.let{JSONObject(it)}
    @Synchronized fun update(c:Context,date:String,runId:String,stage:String,state:String,detail:String){
        val row=read(c,date)?.takeIf{it.optString("runId")==runId}?:JSONObject().put("date",date).put("runId",runId).put("startedAt",System.currentTimeMillis()).put("steps",JSONObject())
        row.put("stage",stage).put("state",state).put("detail",detail).put("updatedAt",System.currentTimeMillis())
        row.getJSONObject("steps").put(stage,JSONObject().put("state",state).put("detail",detail))
        check(sp(c).edit().putString("day_"+date,row.toString()).commit()){ "routine_state_write_failed" }
    }
    @Synchronized fun finish(c:Context,date:String,runId:String,outcome:RoutineOutcome,summary:String){
        update(c,date,runId,outcome.state,outcome.state,outcome.detail)
        val row=read(c,date)!!.put("summary",summary).put("finishedAt",System.currentTimeMillis())
        val a=JSONArray(sp(c).getString("history","[]"));a.put(row)
        val keep=JSONArray();for(i in (a.length()-20).coerceAtLeast(0) until a.length())keep.put(a.getJSONObject(i))
        check(sp(c).edit().putString("day_"+date,row.toString()).putString("history",keep.toString()).commit())
    }
    fun history(c:Context)=JSONArray(sp(c).getString("history","[]"))
    fun reconcile(c:Context){val d=TokyoDate.of();val row=read(c,d)
        if(row?.optString("state")=="RUNNING")update(c,d,row.getString("runId"),"BLOCKED","BLOCKED","上次执行已中断，原观看记录保留")
        val login=LoginBonusStore.read(c,d)
        if(login?.optString("state") in setOf("AD_OPENING","AD_PLAYING")){
            login!!.put("state","UNCONFIRMED").put("reason","process_interrupted").put("finishedAt",System.currentTimeMillis());LoginBonusStore.save(c,d,login)
        }
    }
}

class DailyRoutineController(private val svc:AutoAccessibilityService){
    suspend fun run(run:String,manual:Boolean,challengesOnly:Boolean=false):Pair<RoutineOutcome,String>{
        val date=TokyoDate.of();val settings=Prefs.loadBlocking(svc)
        val overlay=OverlayKeeper(svc)
        @Suppress("DEPRECATION") val wake=(svc.getSystemService(Context.POWER_SERVICE) as PowerManager).newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK,"ana:daily_routine")
        var ad:DailyAdResult?=null
        val port=object:RoutinePort{
            override fun connected()=AutoAccessibilityService.isConnected()
            override fun stopped()=AutoAccessibilityService.stopRequested
            override fun sameDate()=TokyoDate.of()==date
            override suspend fun progress(stage:String,result:RoutineStepResult){
                RunForegroundService.start(svc,result.detail)
                RoutineStore.update(svc,date,run,stage,if(result.state=="RUNNING")"RUNNING" else result.state,result.detail)
                Logger.i("ROUTINE","run=$run stage=$stage state="+result.state+" "+result.detail)
            }
            override suspend fun startup():RoutineStepResult{
                if(challengesOnly){val r=StartupFlow(svc).prepare(run,allowLoginSubmission=false);return RoutineStepResult(if(r.ok)"REVIEW_ONLY" else "BLOCKED",if(r.ok)"仅重新核对出行任务；签到记录保留" else r.reason,r.ok)}
                if(TravelSelection.hasSelectedPending(svc,date)||TravelSelection.hasUnresolvedAd(svc,date))return RoutineStepResult("DEFERRED","先恢复已记录的出行任务，未启动新的签到广告")
                if(OfficialAdStore.attempts(svc,date).any{!it.terminal||it.state==AdState.UNCONFIRMED})
                    return RoutineStepResult("DEFERRED","先核对已预留的每日广告，未重新打开观看")
                val result=StartupFlow(svc).prepare(run)
                return RoutineStepResult(if(result.ok)result.loginState else "BLOCKED",if(result.ok)LoginBonusStore.label(svc,date) else result.reason,result.ok)
            }
            override suspend fun challenges():RoutineStepResult{
                val latest=Prefs.loadBlocking(svc)
                val rows=ChallengeStore.rows(svc,date)
                val pending=(0 until rows.length()).map{rows.getJSONObject(it)}.any{it.optString("state") !in setOf("BOOST_MAX_CONFIRMED","BOOST_MAX_OBSERVED")}
                val selected=TravelSelectionPolicy.queue(TravelSelectionPolicy.categoryOrder,latest).filter{pending||!ChallengeScanStore.complete(svc,date,it,latest.kmOf(it))}
                Cat.values().filter{!latest.catOn(it)}.forEach{TravelSelection.audit(svc,date,run,it)}
                if(Cat.values().none{latest.catOn(it)}&&!TravelSelection.hasUnresolvedAd(svc,date))return RoutineStepResult("DISABLED_BY_USER","出行分类全部取消；签到与每日广告按原设置继续")
                if(selected.isEmpty()&&!TravelSelection.hasUnresolvedAd(svc,date))return RoutineStepResult("ALREADY_DONE","已开启的交通任务范围已处理，未重复观看")
                val child="CH"+java.util.UUID.randomUUID().toString()
                val result=ChallengeTaskController(svc).run(child,selected,allCards=true)
                val complete=TravelSelectionPolicy.queue(TravelSelectionPolicy.categoryOrder,Prefs.loadBlocking(svc)).all{ChallengeScanStore.complete(svc,date,it,Prefs.loadBlocking(svc).kmOf(it))}&&!TravelSelection.hasSelectedPending(svc,date)
                return RoutineStepResult(if(complete)"DONE" else "BLOCKED",if(complete)"已核对所选分类官方状态，接取与Boost分别保存" else result.lineSequence().firstOrNull().orEmpty(),complete)
            }
            override suspend fun ads():RoutineStepResult{
                if(challengesOnly)return RoutineStepResult("UNCHANGED","本次仅处理出行；每日广告记录保持，官方 "+(OfficialAdStore.counter(svc,date)?.toString()?:"未核对")+"/25")
                if(!settings.adEnabled)return RoutineStepResult("DISABLED","每日广告已在设置中关闭")
                if(OfficialAdStore.attempts(svc,date).none{!it.terminal||it.state==AdState.UNCONFIRMED}){
                    val gate=StartupFlow(svc).prepare(run+"_before_ads",launch=false)
                    if(!gate.ok)return RoutineStepResult("BLOCKED",gate.reason,false)
                }
                val child="AD"+java.util.UUID.randomUUID().toString()
                val r=DailyAdTaskController(svc).run(child,if(manual)"routine-manual" else "routine-schedule",manualNew=manual)
                ad=r;DailyAdStore.saveResult(svc,r)
                val own=DailyAdStore.allAttempts(svc,date).filter{it.runId==child}
                val ok=OfficialLoadFailurePolicy.routineAllowed(r.limitReached,r.blockedReason,own,OfficialAdStore.reviewedKeys(svc,date))
                return RoutineStepResult(if(!ok)"BLOCKED" else if(r.limitReached)"LIMIT_REACHED" else "DONE" ,
                    "官方 "+(OfficialAdStore.counter(svc,date)?.toString()?:"未核对")+"/25 · 本机确认 "+r.completed+" · 待核对 "+r.unconfirmed+(if(ok)"" else "；"+TelegramText.reason(r.blockedReason.ifBlank{r.detail})),ok)
            }
        }
        val result:RoutineOutcome
        try{
            val km=svc.getSystemService(Context.KEYGUARD_SERVICE) as android.app.KeyguardManager
            val pm=svc.getSystemService(Context.POWER_SERVICE) as PowerManager
            overlay.show()
            val safety=AccountSafetyStore.pending(svc)
            val readiness=if(safety==null)DeviceReady.ensure(svc,run) else DeviceReadyResult(false,"account_safety_human_required")
            if(!readiness.ready)result=RoutineOutcome("BLOCKED",if(safety!=null)AccountSafety.message(svc) else readiness.reason,emptyMap())
            else {
                wake.acquire(8*60*60*1000L)
                var outcome=RoutineSequence(port).run();var restarts=0
                while(outcome.state=="BLOCKED"&&!AutoAccessibilityService.stopRequested&&restarts<3){
                    val stage=outcome.steps.keys.lastOrNull()?:break
                    if(!StalledAdRestart.recover(svc,date,run,stage))break
                    restarts++
                    // Re-evaluate persisted tasks. Unknown rows and official resume proofs retain their original identity.
                    outcome=RoutineSequence(port).run()
                }
                result=outcome
            }
        }catch(e:Exception){
            val failed=RoutineOutcome(if(AutoAccessibilityService.stopRequested)"STOPPED" else "BLOCKED","执行中止："+(e.message?:e.javaClass.simpleName).take(140),emptyMap())
            val summary=RoutineSummary.build(svc,date,failed,run)
            RoutineStore.finish(svc,date,run,failed,summary)
            Evidence.write(svc,"routine_"+run+"_result.json",RoutineStore.read(svc,date).toString())
            return failed to summary
        }finally{overlay.hide();if(wake.isHeld)wake.release()}
        val summary=RoutineSummary.build(svc,date,result,run);RoutineStore.finish(svc,date,run,result,summary)
        Evidence.write(svc,"routine_"+run+"_result.json",RoutineStore.read(svc,date).toString())
        return result to summary
    }
}
