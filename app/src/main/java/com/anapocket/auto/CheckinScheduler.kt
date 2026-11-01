package com.anapocket.auto

import android.app.*
import android.content.*
import android.net.Uri
import android.os.Build
import androidx.work.*
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.*

/** Separate identities only. This class deliberately never invokes Scheduler.schedule(). */
object CheckinScheduler {
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO+CoroutineExceptionHandler{_,t->runCatching{Logger.w("CHECKIN_SCHEDULE","isolated scheduling failure: "+t.javaClass.simpleName)}})
    private fun alarm(ctx:Context,id:String)=PendingIntent.getBroadcast(ctx,0,Intent(ctx,CheckinAlarmReceiver::class.java).setAction("com.anapocket.auto.CHECKIN."+id).setData(Uri.parse("tuffy-checkin://schedule/"+id)).putExtra("app",id).putExtra("generation",CheckinApp.from(id)?.let{CheckinStore(ctx,it).config().generation}?:0L).putExtra("scheduledAt",ctx.getSharedPreferences("checkin_schedules",0).getLong(id,0)),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    private fun register(ctx:Context,id:String,at:Long){
        val am=ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val exact=Build.VERSION.SDK_INT<31||am.canScheduleExactAlarms()
        // Idle wakeup quota is per UID, not per PendingIntent. Reserve it for the unchanged ANA alarm.
        // P1 holds the screen awake while operating, so its protection alarm can still preempt on time.
        ctx.getSharedPreferences("checkin_schedules",0).edit().putLong(id,at).commit()
        if(exact)am.setExact(AlarmManager.RTC_WAKEUP,at,alarm(ctx,id)) else am.set(AlarmManager.RTC_WAKEUP,at,alarm(ctx,id))
        ctx.getSharedPreferences("checkin_schedules",0).edit().putLong(id,at).putString("mode:"+id,if(exact)"EXACT_NO_IDLE_QUOTA" else "INEXACT_NO_IDLE_QUOTA").apply()
    }
    fun schedule(ctx:Context,app:CheckinApp){
        val c=CheckinStore(ctx,app).config();val wm=WorkManager.getInstance(ctx);val name="checkin_backup_"+app.name
        if(!c.enabled){(ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(alarm(ctx,app.name));wm.cancelUniqueWork(name);CheckinStore(ctx,app).pending(null);ctx.getSharedPreferences("checkin_schedules",0).edit().remove(app.name).apply();return}
        val now=System.currentTimeMillis();val at=CheckinClock.next(now,c.hour,c.minute)
        register(ctx,app.name,at)
        wm.enqueueUniqueWork(name,ExistingWorkPolicy.REPLACE,OneTimeWorkRequestBuilder<CheckinBackupWorker>().setInputData(workDataOf("app" to app.name,"scheduledAt" to at,"generation" to c.generation)).setInitialDelay(at-now+60_000,TimeUnit.MILLISECONDS).build())
    }
    fun protection(ctx:Context){
        val c=Prefs.loadBlocking(ctx)
        if(!c.enabled){(ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(alarm(ctx,"ANA_PROTECT"));return}
        CheckinRuntime.updateAnaWindow(ctx)
        val now=System.currentTimeMillis();val due=CheckinRuntime.anaDue()
        val protect=due-300_000
        if(protect<=now){CheckinRuntime.protect(ctx);register(ctx,"ANA_PROTECT",CheckinClock.next(now+300_000,c.hour,c.minute)-300_000)} else register(ctx,"ANA_PROTECT",protect)
    }
    fun restore(ctx:Context)=scope.launch{
        CheckinApp.entries.forEach{app->runCatching{
            val store=CheckinStore(ctx,app);store.recover{CheckinRuntime.liveRun(app)}
            val old=ctx.getSharedPreferences("checkin_schedules",0).getLong(app.name,0)
            if(store.config().enabled&&old>0&&old<=System.currentTimeMillis()&&CheckinClock.date(old)==TokyoDate.of())dispatch(ctx,app,"reboot_catchup",old).join()
            schedule(ctx,app)
        }}
        runCatching{protection(ctx)}
    }
    fun dispatch(ctx:Context,app:CheckinApp,source:String,scheduledAt:Long=0,generation:Long=CheckinStore(ctx,app).config().generation)=scope.launch {
            val s=CheckinStore(ctx,app);val c=s.config();if(!CheckinDispatchPolicy.accepts(c.enabled,c.generation,generation,scheduledAt==0L||CheckinClock.date(scheduledAt)==TokyoDate.of())){s.log("dispatch_"+TokyoDate.of(),"REJECT stale_or_disabled generation="+generation);return@launch}
            val now=System.currentTimeMillis();val date=TokyoDate.of()
            s.log("dispatch_"+date,"source=$source scheduledAt=$scheduledAt actualTriggerAt=$now delayMs="+(if(scheduledAt>0)now-scheduledAt else 0))
            if(!s.acceptDispatch(date,scheduledAt,source,generation)){s.log("dispatch_"+date,"DEDUP alarm_or_backup_already_delivered");return@launch}
            CheckinRuntime.request(ctx,app,"scheduled",scheduledAt,generation)
    }
}
class CheckinAlarmReceiver:BroadcastReceiver(){
    override fun onReceive(ctx:Context,intent:Intent){val id=intent.getStringExtra("app")?:return
        if(id=="ANA_PROTECT"){CheckinRuntime.protect(ctx);CheckinScheduler.protection(ctx);return}
        val app=CheckinApp.from(id)?:return
        val at=intent.getLongExtra("scheduledAt",0)
        val pending=goAsync();CheckinScheduler.dispatch(ctx,app,"alarm",at,intent.getLongExtra("generation",0)).invokeOnCompletion{pending.finish()}
        // Keep today's backup alive: next registration happens in the backup worker.
    }
}
class CheckinBackupWorker(ctx:Context,params:WorkerParameters):CoroutineWorker(ctx,params){
    override suspend fun doWork():Result {
        val app=CheckinApp.from(inputData.getString("app"))?:return Result.failure()
        try{CheckinScheduler.dispatch(applicationContext,app,"backup",inputData.getLong("scheduledAt",0),inputData.getLong("generation",0)).join()}finally{CheckinScheduler.schedule(applicationContext,app)}
        return Result.success()
    }
}
class CheckinBootReceiver:BroadcastReceiver(){override fun onReceive(ctx:Context,intent:Intent){
    if(intent.action !in setOf(Intent.ACTION_BOOT_COMPLETED,Intent.ACTION_MY_PACKAGE_REPLACED,Intent.ACTION_TIME_CHANGED,Intent.ACTION_TIMEZONE_CHANGED,"android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED"))return
    val pending=goAsync();CheckinScheduler.restore(ctx).invokeOnCompletion{pending.finish()}
}}
