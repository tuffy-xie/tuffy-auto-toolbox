package com.anapocket.auto

import android.content.Context
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
        // Snapshot before this run's STARTUP progress replaces the previous run's steps.
        var previous=RoutineStore.read(svc,date)
        val port=object:RoutinePort{
            override fun connected()=AutoAccessibilityService.isConnected()
            override fun stopped()=AutoAccessibilityService.stopRequested
            override fun sameDate()=TokyoDate.of()==date
            override suspend fun progress(stage:String,result:RoutineStepResult){
                RunForegroundService.start(svc,result.detail)
                RoutineStore.update(svc,date,run,stage,if(result.state=="RUNNING")"RUNNING" else result.state,result.detail)
                AnaProcessRecovery.routineProgress(svc,RoutineStore.read(svc,date))
                Logger.i("ROUTINE","run=$run stage=$stage state="+result.state+" "+result.detail)
            }
            override suspend fun startup():RoutineStepResult{
                if(MaxBoostCoordinator.visible(svc)||MaxBoostCoordinator.hasPending(svc,date))return RoutineStepResult("DEFERRED","先完成已升满挑战的确认和分类恢复，不重新观看广告")
                val existingChallenge=TravelSelection.hasSelectedPending(svc,date)||TravelSelection.hasUnresolvedAd(svc,date)
                if(RoutineRecoveryNavigation.reviewChallengeFirst(challengesOnly,existingChallenge))return RoutineStepResult("DEFERRED","先核对原出行观看及官方续接入口，未启动新的签到广告")
                if(challengesOnly){val r=StartupFlow(svc).prepare(run,allowLoginSubmission=false);return RoutineStepResult(if(r.ok)"REVIEW_ONLY" else "BLOCKED",if(r.ok)"仅重新核对出行任务；签到记录保留" else r.reason,r.ok)}
                if(TravelSelection.hasSelectedPending(svc,date)||TravelSelection.hasUnresolvedAd(svc,date))return RoutineStepResult("DEFERRED","先恢复已记录的出行任务，未启动新的签到广告")
                if(OfficialAdStore.attempts(svc,date).any{!it.terminal||it.state==AdState.UNCONFIRMED})
                    return RoutineStepResult("DEFERRED","先核对已预留的每日广告，未重新打开观看")
                val result=StartupFlow(svc).prepare(run)
                return RoutineStepResult(if(result.ok)result.loginState else "BLOCKED",if(result.ok)LoginBonusStore.label(svc,date) else result.reason,result.ok)
            }
            override suspend fun challenges():RoutineStepResult{
                val latest=Prefs.loadBlocking(svc)
                val completionPending=MaxBoostCoordinator.hasPending(svc,date)||MaxBoostCoordinator.visible(svc)
                val configured=TravelSelectionPolicy.queue(TravelSelectionPolicy.categoryOrder,latest)
                val pending=completionPending||ChallengeStore.blocksIndependentNavigation(svc,date)
                val proofs=configured.associateWith{ChallengeScanStore.resumeState(svc,date,it,latest.kmOf(it))}
                val reused=configured.filter{cat->
                    RoutinePolicy.reuseChallenges(TokyoDate.of()==date,!challengesOnly&&latest.dayToday&&!latest.dayTomorrow,
                        proofs[cat]!=null,pending)
                }.toSet()
                val partialReuse=reused.any{proofs[it]=="PARTIALLY_COMPLETED"}
                val selected=configured.filterNot{it in reused}
                if(reused.isNotEmpty())Logger.i("ROUTINERESUME","run=$run same_day_reused_categories="+reused.joinToString(","){it.name+":"+proofs[it]}+" skip_reused_category_ui=true remaining="+selected.joinToString(","){it.name})
                Cat.values().filter{!latest.catOn(it)}.forEach{TravelSelection.audit(svc,date,run,it)}
                if(Cat.values().none{latest.catOn(it)}&&!TravelSelection.hasUnresolvedAd(svc,date)&&!completionPending)return RoutineStepResult("DISABLED_BY_USER","出行分类全部取消；签到与每日广告按原设置继续")
                if(selected.isEmpty()&&!pending){
                    if(partialReuse&&!ChallengeTaskController(svc).isolateForIndependentDaily(run+"_reuse_isolated",date))
                        return RoutineStepResult("BLOCKED","已隔离的出行观看仍需安全原生页面；未重播广告",false)
                    return RoutineStepResult(if(partialReuse)"PARTIALLY_COMPLETED" else "ALREADY_DONE",
                        if(partialReuse)"完整扫描已保存；隔离项仍UNKNOWN，未重复扫描或观看" else "已开启的交通任务范围已处理，未重复观看")
                }
                val child="CH"+java.util.UUID.randomUUID().toString()
                val result=ChallengeTaskController(svc).run(child,selected,allCards=true,reuseCompleted=!challengesOnly)
                if(!result.continueRun||ChallengeStore.blocksIndependentNavigation(svc,date)||MaxBoostDialogPolicy.blocksRoutine(result.detail,MaxBoostCoordinator.hasPending(svc,date)||MaxBoostCoordinator.visible(svc)))
                    return RoutineStepResult("BLOCKED",result.detail,false)
                return if(partialReuse&&result.state in setOf("DONE","ALREADY_DONE"))result.copy(state="PARTIALLY_COMPLETED") else result

            }
            override suspend fun ads():RoutineStepResult{
                if(challengesOnly)return RoutineStepResult("UNCHANGED","本次仅处理出行；每日广告记录保持，官方 "+(OfficialAdStore.counter(svc,date)?.toString()?:"未核对")+"/25")
                if(!settings.adEnabled)return RoutineStepResult("DISABLED","每日广告已在设置中关闭")
                val official=OfficialAdStore.observation(svc,date)
                if(RoutinePolicy.reuseAds(previous?.optString("date")==date&&official?.optString("date")==date,
                    previous?.optJSONObject("steps")?.optJSONObject("ADS")?.optString("state"),
                    OfficialAdStore.counter(svc,date),!OfficialAdStore.allResolved(svc,date)))
                    return RoutineStepResult("ALREADY_DONE","同日官方25/25已确认，未重新进入每日广告")
                if(OfficialAdStore.attempts(svc,date).none{!it.terminal||it.state==AdState.UNCONFIRMED}){
                    val gate=StartupFlow(svc).prepare(run+"_before_ads",launch=false)
                    if(!gate.ok)return RoutineStepResult("BLOCKED",gate.reason,false)
                }
                val child="AD"+java.util.UUID.randomUUID().toString()
                val r=DailyAdTaskController(svc).run(child,if(manual)"routine-manual" else "routine-schedule",manualNew=manual)
                DailyAdStore.saveResult(svc,r)
                val own=DailyAdStore.allAttempts(svc,date).filter{it.runId==child}
                val ok=OfficialLoadFailurePolicy.routineAllowed(r.limitReached,r.blockedReason,own,OfficialAdStore.reviewedKeys(svc,date))
                return RoutineStepResult(if(!ok)"BLOCKED" else if(r.limitReached)"LIMIT_REACHED" else "DONE" ,
                    "官方 "+(OfficialAdStore.counter(svc,date)?.toString()?:"未核对")+"/25 · 本机确认 "+r.completed+" · 待核对 "+r.unconfirmed+(if(ok)"" else "；"+TelegramText.reason(r.blockedReason.ifBlank{r.detail})),ok)
            }
        }
        val result:RoutineOutcome
        try{
            overlay.show()
            val safety=AccountSafetyStore.pending(svc)
            val readiness=if(safety==null)DeviceReady.ensure(svc,run) else DeviceReadyResult(false,"account_safety_human_required")
            if(!readiness.ready)result=RoutineOutcome("BLOCKED",if(safety!=null)AccountSafety.message(svc) else readiness.reason,emptyMap())
            else {
                /* No multi-hour wake hold: native readiness and active UI ownership remain bounded. */
                var outcome=RoutineSequence(port).run();var restarts=0
                while(outcome.state=="BLOCKED"&&!AutoAccessibilityService.stopRequested&&restarts<3){
                    val stage=if(outcome.detail.contains("MAX_BOOST_"))"MAX_BOOST" else outcome.steps.keys.lastOrNull()?:break
                    if(!StalledAdRestart.recover(svc,date,run,stage))break
                    restarts++
                    // Re-evaluate persisted tasks. Unknown rows and official resume proofs retain their original identity.
                    previous=RoutineStore.read(svc,date)
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
        }finally{overlay.hide()}
        val summary=RoutineSummary.build(svc,date,result,run);RoutineStore.finish(svc,date,run,result,summary)
        Evidence.write(svc,"routine_"+run+"_result.json",RoutineStore.read(svc,date).toString())
        return result to summary
    }
}
