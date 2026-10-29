package com.anapocket.auto

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        Logger.init(context)
        Logger.i("BOOT", "action=${intent?.action}")
        Scheduler.schedule(context)
    }
}
