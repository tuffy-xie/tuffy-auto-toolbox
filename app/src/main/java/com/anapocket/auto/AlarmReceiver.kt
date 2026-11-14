package com.anapocket.auto

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.launch

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val pending = goAsync()
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default).launchAlarm {
            try {
                Logger.init(context)
                if(intent?.getBooleanExtra("routineRetry",false)==true){
                    Logger.i("ALARM","routine retry fired")
                    RoutineRetry.fire(context)
                }else if(intent?.getBooleanExtra("readinessSelftest",false)==true){
                    Logger.i("ALARM","readiness selftest fired")
                    val svc=AutoAccessibilityService.instance
                    if(svc!=null)svc.startReadinessCheck() else Notifier.needService(context)
                }else if (intent?.getBooleanExtra("selftest", false) == true) {
                    Logger.i("ALARM", "selftest fired: dispatch-only, no ad or challenge started")
                    Evidence.write(context, "schedule_selftest_" + System.currentTimeMillis() + ".txt", TokyoDate.time() + " JST alarm delivered; no ad started")
                    Notifier.scheduleVerified(context)
                } else {
                    Logger.i("ALARM", "daily alarm fired")
                    Scheduler.dispatchDaily(context, "alarm")
                    Scheduler.schedule(context)
                }
            } finally { pending.finish() }
        }
    }
}

private fun kotlinx.coroutines.CoroutineScope.launchAlarm(block: suspend () -> Unit) = launch(block = { block() })
