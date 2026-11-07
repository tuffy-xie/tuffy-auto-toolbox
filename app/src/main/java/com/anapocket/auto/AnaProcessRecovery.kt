package com.anapocket.auto

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import android.os.Process
import android.provider.Settings
import androidx.work.*
import kotlinx.coroutines.CoroutineScope
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** The coroutine is no longer the job: SQLite intent and unique WorkManager work survive its loss. */
object AnaProcessRecovery {
    private const val AREA="anaRecovery"
    private fun sp(c:Context)=c.getSharedPreferences("ana_process_recovery_v1",Context.MODE_PRIVATE)
    private fun name(date:String)="ana-safe-recovery-"+date
    private fun boot(c:Context)=Settings.Global.getInt(c.contentResolver,Settings.Global.BOOT_COUNT,0)
    private fun version(c:Context)=c.packageManager.getPackageInfo(c.packageName,0).lastUpdateTime
    private fun load(c:Context,date:String)=ReliableLedger.read(c,AREA,date,"ANA"){null}?.let{JSONObject(it)}
    private fun progress(c:Context,date:String):String {
        val rows=ChallengeStore.rows(c,date);var count=0;var last=""
        for(i in 0 until rows.length()){val ads=rows.getJSONObject(i).getJSONArray("ads");for(j in 0 until ads.length()){
            val a=ads.getJSONObject(j);if(a.optBoolean("confirmed")){count++;last=a.optString("runId")+":"+a.optLong("startedAt")}
        }}
        return count.toString()+":"+last+":"+OfficialAdStore.attempts(c,date).count{it.rewardConfirmed}+":"+LoginBonusStore.read(c,date)?.optString("state").orEmpty()
    }
    fun markUserStop(c:Context,run:String){
        if(!run.startsWith("RT"))return
        check(sp(c).edit().putString("stoppedRun",run).commit())
        ReliableLedger.mutate(c,AREA,TokyoDate.of(),"ANA"){it?.apply{put("phase","CANCELLED");put("reason","user_stopped")}}
        WorkManager.getInstance(c.applicationContext).cancelUniqueWork(name(TokyoDate.of()))
    }
    fun schedule(svc:AutoAccessibilityService,scope:CoroutineScope,row:JSONObject?) {
        registerExit(svc,row);resumePending(svc)
    }
    private fun registerExit(c:Context,row:JSONObject?){
        if(row!=null&&Build.VERSION.SDK_INT>=30){
            val date=TokyoDate.of();val run=row.optString("runId");val now=System.currentTimeMillis()
            val previous=load(c,date)
            val ownedActive=previous?.optString("phase")=="CLAIMED"&&previous.optString("sourceRun")==run&&!row.has("finishedAt")
            val sameBoot=previous==null||previous.optInt("boot")==boot(c)
            val sameVersion=previous==null||previous.optLong("version")==version(c)
            val exit=runCatching{(c.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager)
                .getHistoricalProcessExitReasons(c.packageName,0,8).firstOrNull{it.processName==c.packageName}}.getOrNull()
            val valid=exit!=null&&PersistentRecoveryPolicy.canRegisterFromPhase(previous?.optString("phase"))&&
                PersistentRecoveryPolicy.mayRegister(date,row.optString("date"),PersistentRecoveryPolicy.activeSnapshotState(row.optString("state"),ownedActive),run,
                PersistentRecoveryPolicy.activeSnapshotStage(row.optString("stage"),ownedActive),row.optLong("updatedAt"),now,exit.timestamp,exit.reason==ApplicationExitInfo.REASON_LOW_MEMORY,
                Prefs.loadBlocking(c).enabled,sp(c).getString("stoppedRun",null)==run,AccountSafetyStore.pending(c)!=null,sameBoot,sameVersion)&&version(c)<exit.timestamp
            if(valid){
                val token=progress(c,date)
                ReliableLedger.mutate(c,AREA,date,"ANA"){old->
                    if(old?.optLong("exitAt")==exit!!.timestamp)return@mutate old
                    val first=old?.optLong("firstAt")?.takeIf{it>0}?:now
                    val plan=PersistentRecoveryPolicy.plan(now,first,old?.optInt("burst")?:0,old?.optInt("total")?:0,
                        old?.optBoolean("cooldown")?:false,old!=null&&old.optString("progress")!=token)
                    (old?:JSONObject()).put("date",date).put("sourceRun",run).put("firstAt",first).put("exitAt",exit.timestamp)
                        .put("sourceLowMemory",true).put("dueAt",plan.due).put("phase",if(plan.allowed)"PENDING" else "BLOCKED")
                        .put("burst",plan.burst).put("total",plan.total).put("cooldown",plan.cooldown).put("reason",plan.reason)
                        .put("boot",boot(c)).put("version",version(c)).put("progress",token).put("lease","").put("updatedAt",now)
                }
                Logger.w("PROCESS_RECOVERY","persisted original run="+run+"; no ad replay; bounded job")
            }
        }
    }
    fun deferUnstarted(c:Context,run:String){
        val date=TokyoDate.of();val now=System.currentTimeMillis()
        ReliableLedger.mutate(c,AREA,date,"ANA"){previous->
            val old=previous?.takeIf{it.optString("phase") in setOf("PENDING","CLAIMED")}
            (old?:JSONObject()).put("date",date).put("sourceRun",run).put("firstAt",old?.optLong("firstAt")?:now)
                .put("sourceLowMemory",false).put("unstartedIntent",true).put("dueAt",now+120_000)
                .put("phase","PENDING").put("reason","foreground_start_temporarily_disallowed")
                .put("boot",boot(c)).put("version",version(c)).put("burst",old?.optInt("burst")?:0)
                .put("total",old?.optInt("total")?:0).put("cooldown",old?.optBoolean("cooldown")?:false).put("lease","")
        }
        resumePending(c)
        Notifier.routineSummary(c,run,"系统暂不允许前台执行，尚未提交广告；手机已保存原启动意图并安排有界重试。")
    }
    private suspend fun watchdog(c:Context,date:String):ListenableWorker.Result {
        val work=OneTimeWorkRequestBuilder<AnaRecoveryWorker>().setInputData(workDataOf("date" to date))
            .setInitialDelay(120,TimeUnit.SECONDS).build()
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO){
            WorkManager.getInstance(c.applicationContext).enqueueUniqueWork(name(date),ExistingWorkPolicy.APPEND_OR_REPLACE,work).result.get()
        }
        return ListenableWorker.Result.success()
    }
    fun resumePending(c:Context){
        val date=TokyoDate.of();val row=load(c,date)?:return
        if(row.optString("phase") !in setOf("PENDING","CLAIMED"))return
        val delay=(row.optLong("dueAt")-System.currentTimeMillis()).coerceAtLeast(0)
        val work=OneTimeWorkRequestBuilder<AnaRecoveryWorker>().setInputData(workDataOf("date" to date))
            .setInitialDelay(delay,TimeUnit.MILLISECONDS).setBackoffCriteria(BackoffPolicy.LINEAR,30,TimeUnit.SECONDS).build()
        WorkManager.getInstance(c.applicationContext).enqueueUniqueWork(name(date),ExistingWorkPolicy.REPLACE,work)
    }
    fun describe(c:Context):String?=load(c,TokyoDate.of())?.takeIf{it.optString("phase") in setOf("PENDING","CLAIMED","BLOCKED")}
        ?.let{row->
            val text=when(row.optString("reason")){
                "memory_pressure_cooldown"->"连续系统回收，等待内存压力冷却后核对原任务"
                "foreground_start_temporarily_disallowed"->"系统暂不允许前台执行，已保存原任务等待重试"
                "recovery_daily_budget_exhausted","repeated_memory_exit_without_durable_progress"->"手机有界恢复已停止；原观看待核对，禁止重播"
                "recovery_window_expired"->"原任务恢复时间窗已结束，请核对保留的记录"
                else->if(row.optString("phase")=="BLOCKED")"原任务尚未安全完成，请核对记录中的权限、系统中断或未知回执原因" else "系统回收后等待手机核对原任务"
            }
            text+if(row.optString("phase")=="PENDING")"；最早 "+TokyoDate.time(row.optLong("dueAt")) else ""
        }
    suspend fun controllerStarting(c:Context,run:String,mode:RoutineRunMode){
        val date=TokyoDate.of();val now=System.currentTimeMillis();val prior=load(c,date)
        ReliableLedger.mutate(c,AREA,date,"ANA"){old->
            (old?:JSONObject()).put("date",date).put("sourceRun",run).put("firstAt",old?.optLong("firstAt")?.takeIf{it>0}?:now)
                .put("invocation",JSONObject().put("manual",mode.manual).put("challengesOnly",mode.challengesOnly))
                .put("phase","CLAIMED").put("pid",Process.myPid()).put("unstartedIntent",true).put("reason","foreground_start_temporarily_disallowed").put("dueAt",now+120_000)
                .put("boot",boot(c)).put("version",version(c)).put("updatedAt",now)
        }
        if(prior==null||prior.optString("phase") !in setOf("PENDING","CLAIMED"))watchdog(c,date)
    }
    fun routineProgress(c:Context,row:JSONObject?){
        if(row==null)return
        ReliableLedger.mutate(c,AREA,row.getString("date"),"ANA"){old->old?.apply{
            if(optString("sourceRun")==row.optString("runId"))put("routineSnapshot",JSONObject(row.toString())).put("unstartedIntent",false)
        }}
    }
    fun controllerFinished(c:Context,state:String){
        val date=TokyoDate.of();ReliableLedger.mutate(c,AREA,date,"ANA"){old->
            old?.apply{put("phase",if(state=="DONE")"DONE" else "BLOCKED");put("reason","controller_"+state);put("updatedAt",System.currentTimeMillis())}
        }
        WorkManager.getInstance(c.applicationContext).cancelUniqueWork(name(date))
    }
    fun defer(c:Context,reason:String){
        val date=TokyoDate.of();ReliableLedger.mutate(c,AREA,date,"ANA"){old->old?.apply{put("phase","PENDING");put("dueAt",System.currentTimeMillis()+120_000);put("reason",reason);put("lease","")}}
        resumePending(c)
    }
    suspend fun execute(c:Context,date:String,worker:String):ListenableWorker.Result {
        var old=load(c,date)?:return ListenableWorker.Result.success()
        if(old.optString("phase")=="CLAIMED"&&old.optInt("pid")!=Process.myPid()){
            registerExit(c,old.optJSONObject("routineSnapshot"))
            old=load(c,date)?:return ListenableWorker.Result.success()
        }
        if(old.optString("phase") !in setOf("PENDING","CLAIMED"))return ListenableWorker.Result.success()
        val svc=AutoAccessibilityService.instance
        val stopped=sp(c).getString("stoppedRun",null)==old.optString("sourceRun")
        if(AutoAccessibilityService.isRunning&&date==TokyoDate.of()&&Prefs.loadBlocking(c).enabled&&!stopped&&
            AccountSafetyStore.pending(c)==null&&boot(c)==old.optInt("boot")&&version(c)==old.optLong("version"))return watchdog(c,date)
        val allowed=PersistentRecoveryPolicy.mayDispatch(TokyoDate.of(),date,Prefs.loadBlocking(c).enabled,
            svc!=null&&AutoAccessibilityService.isConnected(),AccountSafetyStore.pending(c)!=null,stopped,
            boot(c)==old.optInt("boot"),version(c)==old.optLong("version"),System.currentTimeMillis(),old.optLong("firstAt"),old.optBoolean("sourceLowMemory"),old.optBoolean("unstartedIntent")&&old.optString("reason")=="foreground_start_temporarily_disallowed")
        if(!allowed){
            if(date==TokyoDate.of()&&Prefs.loadBlocking(c).enabled&&!stopped&&svc==null&&old.optInt("unavailable")<3){
                ReliableLedger.mutate(c,AREA,date,"ANA"){it?.put("unavailable",it.optInt("unavailable")+1)}
                Notifier.needService(c);return ListenableWorker.Result.retry()
            }
            ReliableLedger.mutate(c,AREA,date,"ANA"){it?.apply{put("phase","BLOCKED");put("reason","recovery_requires_permission_boot_version_or_risk_review")}}
            Notifier.routineSummary(c,old.optString("sourceRun"),"系统中断后的原任务需要核对；日期、权限、用户停止、重启或升级保护未通过，未重新观看广告。")
            return ListenableWorker.Result.success()
        }
        if(System.currentTimeMillis()<old.optLong("dueAt"))return ListenableWorker.Result.retry()
        if(AutoAccessibilityService.isRunning)return watchdog(c,date)
        if(old.optBoolean("sourceLowMemory")){
            val latest=if(Build.VERSION.SDK_INT>=30)(c.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager)
                .getHistoricalProcessExitReasons(c.packageName,0,8).firstOrNull{it.processName==c.packageName} else null
            if(latest==null||!PersistentRecoveryPolicy.exitStillMatches(old.optLong("exitAt"),latest.timestamp,latest.reason==ApplicationExitInfo.REASON_LOW_MEMORY)){
                ReliableLedger.mutate(c,AREA,date,"ANA"){it?.put("phase","BLOCKED")?.put("reason","latest_exit_not_bound_low_memory")}
                Notifier.routineSummary(c,old.optString("sourceRun"),"最新进程退出不再匹配已确认的低内存事件，原观看保留待核对，未自动重播。")
                return ListenableWorker.Result.success()
            }
        }
        if(old.optInt("burst")>=3||old.optInt("total")>=12){
            val plan=PersistentRecoveryPolicy.plan(System.currentTimeMillis(),old.optLong("firstAt"),old.optInt("burst"),old.optInt("total"),old.optBoolean("cooldown"),false)
            ReliableLedger.mutate(c,AREA,date,"ANA"){it?.apply{put("phase",if(plan.allowed)"PENDING" else "BLOCKED");put("burst",plan.burst);put("cooldown",plan.cooldown);put("dueAt",plan.due);put("reason",plan.reason)}}
            if(plan.allowed)return ListenableWorker.Result.retry()
            Notifier.routineSummary(c,old.optString("sourceRun"),"连续系统回收且没有可靠进度，手机有界恢复已停止；原广告保持待核对，未重复观看。")
            return ListenableWorker.Result.success()
        }
        if(old.optBoolean("unstartedIntent")&&Build.VERSION.SDK_INT>=30){
            val latest=(c.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getHistoricalProcessExitReasons(c.packageName,0,8).firstOrNull{it.processName==c.packageName}
            if(latest!=null&&latest.timestamp>=old.optLong("firstAt")&&latest.reason!=ApplicationExitInfo.REASON_LOW_MEMORY){
                ReliableLedger.mutate(c,AREA,date,"ANA"){it?.put("phase","BLOCKED")?.put("reason","unstarted_intent_interrupted_by_unclassified_exit")}
                Notifier.routineSummary(c,old.optString("sourceRun"),"原启动意图遇到原因未确认的退出，已保留任务，请从手机核对后继续。")
                return ListenableWorker.Result.success()
            }
        }
        val invocation=old.optJSONObject("invocation")
        if(invocation==null||!invocation.has("manual")||!invocation.has("challengesOnly")){
            ReliableLedger.mutate(c,AREA,date,"ANA"){it?.put("phase","BLOCKED")?.put("reason","original_invocation_scope_missing")}
            Notifier.routineSummary(c,old.optString("sourceRun"),"原任务执行范围缺少可靠记录，已保留进度，请从手机核对后继续，未自动扩大执行范围。")
            return ListenableWorker.Result.success()
        }
        val originalMode=RoutineRunMode(invocation.getBoolean("manual"),invocation.getBoolean("challengesOnly"))
        val claim=ReliableLedger.mutate(c,AREA,date,"ANA"){row->
            if(row==null)return@mutate null
            if(row.optString("phase")=="CLAIMED"&&row.optInt("pid")==Process.myPid()&&row.optLong("leaseUntil")>System.currentTimeMillis())return@mutate row
            row.put("phase","CLAIMED").put("lease",worker).put("pid",Process.myPid()).put("leaseUntil",System.currentTimeMillis()+60_000)
                .put("burst",row.optInt("burst")+1).put("total",row.optInt("total")+1)
        }?:return ListenableWorker.Result.success()
        if(claim.optString("lease")!=worker)return ListenableWorker.Result.retry()
        if(!AutoAccessibilityService.isRunning)svc!!.startDailyRoutine(if(old.optBoolean("sourceLowMemory"))"persistent_low_memory_recovery" else "persistent_unstarted_retry",originalMode)
        return watchdog(c,date)
    }
}
class AnaRecoveryWorker(c:Context,p:WorkerParameters):CoroutineWorker(c,p){
    override suspend fun doWork():Result=try{AnaProcessRecovery.execute(applicationContext,inputData.getString("date").orEmpty(),id.toString())}
        catch(e:kotlinx.coroutines.CancellationException){throw e}
        catch(e:Exception){Logger.e("PROCESS_RECOVERY","persistent worker could not acquire original task",e)
            if(runAttemptCount>=4){Notifier.routineSummary(applicationContext,"RECOVERY", "原任务持久化恢复暂不可用，未提交新广告，请查看手机记录。");Result.failure()} else Result.retry()}
}
