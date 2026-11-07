package com.anapocket.auto

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * 每日调度：AlarmManager 精确闹钟为主，WorkManager 周期任务兜底。
 * 另提供「调度自检」一次性闹钟，用于验证系统到点真的会执行（不依赖手动触发）。
 */
object Scheduler {
    private const val REQ_DAILY = 1001
    private const val REQ_SELFTEST = 1002
    private const val REQ_RETRY = 1003
    private const val BACKUP_WORK = "ana_backup_daily"

    private fun am(ctx: Context) = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun schedule(ctx: Context) {
        runCatching{AnaProcessRecovery.resumePending(ctx)}
        val st = Prefs.loadBlocking(ctx)
        schedule(ctx, st.hour, st.minute)
    }

    fun schedule(ctx: Context, hour: Int, minute: Int) {
        val alm = am(ctx)
        val next = nextTrigger(hour, minute)
        try {
            val pi = pending(ctx, REQ_DAILY, Intent(ctx, AlarmReceiver::class.java))
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && alm.canScheduleExactAlarms()) {
                alm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pi)
                Logger.i("SCHED", "exact daily alarm -> ${fmt(next)}")
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pi)
                Logger.i("SCHED", "inexact daily alarm -> ${fmt(next)}")
            } else {
                alm.set(AlarmManager.RTC_WAKEUP, next, pi)
                Logger.i("SCHED", "legacy daily alarm -> ${fmt(next)}")
            }
        } catch (t: Throwable) {
            Logger.e("SCHED", "daily alarm failed: ${t.message}", t)
        }

        try {
            val req = PeriodicWorkRequestBuilder<DailyWorker>(24, TimeUnit.HOURS)
                .setInitialDelay(12, TimeUnit.HOURS)
                .build()
            WorkManager.getInstance(ctx)
                .enqueueUniquePeriodicWork(BACKUP_WORK, ExistingPeriodicWorkPolicy.UPDATE, req)
            Logger.i("SCHED", "backup worker enqueued")
        } catch (t: Throwable) {
            Logger.e("SCHED", "worker enqueue failed: ${t.message}", t)
        }
    }

    /** 一次性自检闹钟：delayMs 后由系统触发，用于验证「到点真的执行」。 */
    fun scheduleSelfTest(ctx: Context, delayMs: Long = 90_000L): Long {
        val at = System.currentTimeMillis() + delayMs
        try {
            val i = Intent(ctx, AlarmReceiver::class.java).putExtra("selftest", true)
            val pi = pending(ctx, REQ_SELFTEST, i)
            val alm = am(ctx)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && alm.canScheduleExactAlarms()) {
                alm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            } else {
                alm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            }
            Logger.i("SCHED", "selftest alarm at ${fmt(at)} (in ${delayMs / 1000}s)")
        } catch (t: Throwable) {
            Logger.e("SCHED", "selftest alarm failed: ${t.message}", t)
        }
        return at
    }

    fun scheduleReadinessTest(ctx:Context,delayMs:Long=90_000L){
        val at=System.currentTimeMillis()+delayMs
        val intent=Intent(ctx,AlarmReceiver::class.java).putExtra("readinessSelftest",true)
        val pi=pending(ctx,1004,intent);val alarm=am(ctx)
        if(Build.VERSION.SDK_INT>=31&&alarm.canScheduleExactAlarms())alarm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,at,pi)
        else alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,at,pi)
        Logger.i("READY_TEST","scheduled at "+TokyoDate.time(at)+";native alarm;no ad dispatch")
    }

    /** 服务未连接时的重试闹钟。 */
    fun scheduleRetry(ctx: Context, delayMs: Long = 120_000L) {
        val at = System.currentTimeMillis() + delayMs
        try {
            val pi = pending(ctx, REQ_RETRY, Intent(ctx, AlarmReceiver::class.java))
            val alm = am(ctx)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && alm.canScheduleExactAlarms()) {
                alm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            } else {
                alm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            }
            Logger.i("SCHED", "retry alarm at ${fmt(at)}")
        } catch (t: Throwable) {
            Logger.w("SCHED", "retry alarm failed: ${t.message}")
        }
    }

    fun nextTrigger(hour: Int, minute: Int): Long =
        DailySchedulePolicy.next(hour, minute, System.currentTimeMillis())

    /** Shared gate for AlarmManager, WorkManager and accessibility reconnect. */
    @Synchronized
    fun dispatchDaily(ctx: Context, reason: String) {
        val st = Prefs.loadBlocking(ctx)
        if (!st.enabled) { RunStore.clearPendingTrigger(ctx); return }
        val now = System.currentTimeMillis()
        if (!DailySchedulePolicy.due(st.hour, st.minute, now)) return
        val date = TokyoDate.of(now)
        val sp = ctx.getSharedPreferences("ana_ad_schedule", Context.MODE_PRIVATE)
        if (sp.getString("dispatched_date", "") == date || AutoAccessibilityService.isRunning) return
        if (!AutoAccessibilityService.isConnected()) {
            RunStore.setPendingTrigger(ctx, reason)
            Notifier.needService(ctx)
            val retry = sp.getInt("retry_" + date, 0)
            if (retry < 3) {
                check(sp.edit().putInt("retry_" + date, retry + 1).commit())
                scheduleRetry(ctx)
            }
            return
        }
        check(sp.edit().putString("dispatched_date", date).commit()) { "schedule_write_failed" }
        RunStore.clearPendingTrigger(ctx)
        AutoAccessibilityService.triggerRoutine(reason)
    }

    fun describeNext(hour: Int, minute: Int): String = fmt(nextTrigger(hour, minute))

    private fun fmt(ts: Long): String =
        TokyoDate.time(ts) + " JST"

    private fun pending(ctx: Context, req: Int, i: Intent): PendingIntent {
        var flags = PendingIntent.FLAG_UPDATE_CURRENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) flags = flags or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getBroadcast(ctx, req, i, flags)
    }
}
