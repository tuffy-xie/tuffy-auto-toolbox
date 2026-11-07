package com.anapocket.auto

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

/** Visible execution notification raises importance; Android may still reclaim the process. */
class RunForegroundService : Service() {

    companion object {
        private const val CH = "ana_running"
        private const val ID = 9100
        @Volatile private var established=false
        suspend fun ensure(ctx:Context,text:String):Boolean {
            if(!start(ctx,text))return false
            return kotlinx.coroutines.withTimeoutOrNull(3000L){while(!established)kotlinx.coroutines.delay(50);true}?:false
        }

        fun start(ctx: Context, text: String):Boolean {
            return try {
                val i = Intent(ctx, RunForegroundService::class.java).putExtra("text", text)
                ContextCompat.startForegroundService(ctx, i)
                Logger.i("FGS", "foreground service start requested")
                true
            } catch (t: Throwable) {
                Logger.w("FGS", "start failed: ${t.message}")
                false
            }
        }

        fun stop(ctx: Context) {
            try {
                ctx.stopService(Intent(ctx, RunForegroundService::class.java))
                Logger.i("FGS", "foreground service stopped")
            } catch (t: Throwable) {
                Logger.w("FGS", "stop failed: ${t.message}")
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if(intent?.action=="com.anapocket.auto.STOP"){AutoAccessibilityService.instance?.requestStop();return START_NOT_STICKY}
        val text = intent?.getStringExtra("text") ?: "正在执行任务"
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                if (nm.getNotificationChannel(CH) == null) {
                    val ch = NotificationChannel(CH, "运行状态", NotificationManager.IMPORTANCE_LOW)
                    ch.description = "自动化执行期间的常驻通知"
                    ch.setShowBadge(false)
                    nm.createNotificationChannel(ch)
                }
            }
            val n = NotificationCompat.Builder(this, CH)
                .setSmallIcon(R.drawable.ic_stat_ana)
                .setContentTitle("Tuffy的自动百宝箱")
                .addAction(0,"停止任务",PendingIntent.getService(this,9102,Intent(this,RunForegroundService::class.java).setAction("com.anapocket.auto.STOP"),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build()
            startForeground(ID, n)
            established=true
        } catch (t: Throwable) {
            established=false
            Logger.w("FGS", "startForeground failed: ${t.message}")
            stopSelf(startId)
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        established=false
        try {
            stopForeground(true)
        } catch (_: Throwable) {
        }
        super.onDestroy()
    }
}
