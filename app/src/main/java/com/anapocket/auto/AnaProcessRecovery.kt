package com.anapocket.auto

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject

/** No ledger edits or ad dispatch here: the normal controller first reviews its saved attempt. */
object AnaProcessRecovery {
    private fun sp(c:Context)=c.getSharedPreferences("ana_process_recovery_v1",Context.MODE_PRIVATE)
    fun markUserStop(c:Context,run:String){if(run.startsWith("RT"))check(sp(c).edit().putString("stoppedRun",run).commit())}
    fun schedule(svc:AutoAccessibilityService,scope:CoroutineScope,row:JSONObject?) {
        if(row==null||Build.VERSION.SDK_INT<30)return
        val run=row.optString("runId");val date=TokyoDate.of();val prefs=sp(svc)
        val used=if(prefs.getString("date",null)==date)prefs.getInt("used",0) else 0
        val exit=runCatching{(svc.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager)
            .getHistoricalProcessExitReasons(svc.packageName,0,8).firstOrNull{it.processName==svc.packageName}}.getOrNull()?:return
        val eligible=RoutineRecoveryPolicy.eligible(date,row.optString("date"),row.optString("state"),run,
            row.optString("stage"),row.optLong("updatedAt"),System.currentTimeMillis(),exit.timestamp,
            exit.reason==ApplicationExitInfo.REASON_LOW_MEMORY,Prefs.loadBlocking(svc).enabled,
            prefs.getString("stoppedRun",null)==run,AccountSafetyStore.pending(svc)!=null,used)
        if(!eligible||prefs.getString("claimedRun",null)==run)return
        // Claim synchronously before scheduling; reconnect callbacks cannot start duplicate controllers.
        check(prefs.edit().putString("date",date).putInt("used",used+1).putString("claimedRun",run).commit())
        Logger.w("PROCESS_RECOVERY","queued oldRun=$run reason=LOW_MEMORY recovery="+(used+1)+"/3; saved_attempt_review_required")
        scope.launch {
            delay(1500)
            val current=RoutineStore.read(svc,date)
            if(AutoAccessibilityService.instance!==svc||AutoAccessibilityService.isRunning||
                AutoAccessibilityService.stopRequested||TokyoDate.of()!=date||!Prefs.loadBlocking(svc).enabled||
                prefs.getString("stoppedRun",null)==run||AccountSafetyStore.pending(svc)!=null||
                current?.optString("runId")!=run||current?.optString("state")!="BLOCKED")return@launch
            Logger.w("PROCESS_RECOVERY","resume oldRun=$run; existing official receipt/ad binding only; no replay requested")
            svc.startDailyRoutine("low_memory_reconnect")
        }
    }
}
