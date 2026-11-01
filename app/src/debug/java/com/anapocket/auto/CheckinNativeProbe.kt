package com.anapocket.auto

import android.content.Context
import android.os.SystemClock
import kotlinx.coroutines.*
import org.json.JSONObject

/** Real alarm and UI owner preemption, deliberately no reward/controller business submissions. */
object CheckinNativeProbe {
    suspend fun run(ctx:Context){
        check(!AutoAccessibilityService.isRunning&&CheckinRuntime.activeApp==null)
        val svc=AutoAccessibilityService.instance?:error("a11y_required")
        val id="r46_isolation_"+System.currentTimeMillis()
        val before=Prefs.loadBlocking(ctx).toJson()
        val date=TokyoDate.of()
        val beforeCounts=DailyAdStore.counters(ctx,date).toString()
        CheckinRuntime.readOnlyProbe(ctx,CheckinApp.STEAK)
        withTimeout(20_000){while(CheckinRuntime.activeApp!=CheckinApp.STEAK)delay(200)}
        val old=CheckinRuntime.ownership.owner()?:error("probe_lease_missing")
        delay(3000)
        val scheduledAt=System.currentTimeMillis()+30_000
        Scheduler.scheduleReadinessTest(ctx,30_000)
        val proof=JSONObject().put("kind","REAL_ALARM_READ_ONLY_PREEMPTION_NOT_DAILY_REWARD").put("phoneTime",TokyoDate.time()).put("scheduledAt",scheduledAt).put("ordinaryRunId",old.run)
        var seenAna=false;var acquiredAt=0L
        val until=SystemClock.elapsedRealtime()+100_000
        while(SystemClock.elapsedRealtime()<until&&!seenAna){delay(100);if(CheckinRuntime.ownership.owner()?.app=="ANA"){seenAna=true;acquiredAt=System.currentTimeMillis()}}
        var staleActions=0
        val staleAccepted=CheckinRuntime.ownership.action(old,SystemClock.elapsedRealtime(),false){staleActions++}
        proof.put("anaAcquired",seenAna).put("acquiredAt",acquiredAt).put("observedDelayMillis",if(seenAna)acquiredAt-scheduledAt else JSONObject.NULL).put("staleActionAccepted",staleAccepted).put("staleActionCount",staleActions)
        withTimeoutOrNull(60_000){while(AutoAccessibilityService.isRunning)delay(250)}
        proof.put("anaSettingsUnchanged",before==Prefs.loadBlocking(ctx).toJson()).put("anaDailyCountersUnchanged",beforeCounts==DailyAdStore.counters(ctx,date).toString()).put("ordinaryPlansEnabled",CheckinApp.entries.any{CheckinStore(ctx,it).config().enabled}).put("anaSchedule",String.format("%02d:%02d",Prefs.loadBlocking(ctx).hour,Prefs.loadBlocking(ctx).minute))
        Evidence.write(ctx,id+".json",proof.toString(2))
        Logger.i("CHECKIN_TEST","native_probe anaAcquired="+seenAna+" staleActions="+staleActions+" settingsUnchanged="+proof.optBoolean("anaSettingsUnchanged"))
        CheckinRuntime.stop()
    }
}
