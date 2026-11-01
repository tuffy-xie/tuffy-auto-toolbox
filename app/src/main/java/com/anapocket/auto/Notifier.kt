package com.anapocket.auto

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

/** 完成通知：广告 / 今日挑战 / 明日预约 / 全部完成 / 部分失败需人工处理。 */
object Notifier {
    private const val CHANNEL = "ana_run"
    private const val ID_SUMMARY = 9001
    private const val ID_SERVICE = 9002

    fun ensureChannel(ctx: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL) != null) return
        val ch = NotificationChannel(CHANNEL, "执行结果", NotificationManager.IMPORTANCE_DEFAULT)
        ch.description = "自动化任务完成与失败提醒"
        nm.createNotificationChannel(ch)
    }

    private fun canNotify(ctx: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ctx.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) !=
                android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                Logger.w("NOTIFY", "POST_NOTIFICATIONS not granted")
                return false
            }
        }
        if (!NotificationManagerCompat.from(ctx).areNotificationsEnabled()) {
            Logger.w("NOTIFY", "notifications disabled")
            return false
        }
        return true
    }

    private fun base(ctx: Context, title: String, text: String): NotificationCompat.Builder {
        val i = Intent(ctx, MainActivity::class.java)
        var flags = PendingIntent.FLAG_UPDATE_CURRENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) flags = flags or PendingIntent.FLAG_IMMUTABLE
        val pi = PendingIntent.getActivity(ctx, 0, i, flags)
        return NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_ana)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setContentIntent(pi)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
    }

    fun readinessResult(ctx:Context,run:String,ok:Boolean,reason:String){
        val text=if(ok)"手机已解锁并打开ANA；未启动观看，原任务记录未改变。" else "未启动任务："+TaskFailureText.short(reason)
        ensureChannel(ctx)
        if(canNotify(ctx))runCatching{NotificationManagerCompat.from(ctx).notify(9004,base(ctx,if(ok)"锁屏启动自检通过" else "锁屏启动需要处理",text).build())}
        TelegramNotifier.enqueue(ctx,"READINESS_"+run,(if(ok)"🔓 锁屏启动自检通过\n" else "⚠ 锁屏启动自检未通过\n")+text)
    }

    fun finishIssue(ctx:Context,run:String){
        val text="ANA任务结果已保存，已退出前台；自动锁屏未确认，请检查手机。"
        ensureChannel(ctx)
        if(canNotify(ctx))runCatching{NotificationManagerCompat.from(ctx).notify(9005,base(ctx,"任务收尾需要检查",text).build())}
        TelegramNotifier.enqueue(ctx,"EXIT_"+run,text)
    }

    fun safetyPause(ctx:Context,row:org.json.JSONObject){
        val text="⚠ ANA任务已暂停\n"+row.optString("title")+"\n请手动处理后回Tuffy复核；已保留执行记录"
        TelegramNotifier.enqueue(ctx,"SAFETY_"+row.optString("id"),text)
        ensureChannel(ctx)
        if(canNotify(ctx))runCatching{NotificationManagerCompat.from(ctx).notify(9003,base(ctx,"需要人工处理",text).build())}
    }

    fun summary(ctx: Context, o: RunOutcome) {
        ensureChannel(ctx)
        if (!canNotify(ctx)) return
        val title = when {
            o.success == 0 && o.failed == 0 && o.unconfirmed == 0 -> "本次无可执行任务"
            o.partial -> "任务部分完成，需要人工确认"
            else -> "所选任务已全部完成"
        }
        val text = "${o.short()}\n已参加/已预约 ${o.already} · 跳过 ${o.skipped}" +
            (if (o.stopped) "\n（已按请求停止）" else "") +
            "\n运行 ID: ${o.runId}"
        try {
            NotificationManagerCompat.from(ctx).notify(ID_SUMMARY, base(ctx, title, text).build())
            Logger.i("NOTIFY", "summary posted: $title | ${o.short()}")
        } catch (t: Throwable) {
            Logger.w("NOTIFY", "post failed: ${t.message}")
        }
    }

    fun dailyAdSummary(ctx: Context, r: DailyAdResult) {
        runCatching { TelegramNotifier.daily(ctx, r) }
        ensureChannel(ctx)
        if (!canNotify(ctx)) return
        val title = when {
            r.blockedReason.isNotEmpty() -> "广告任务被阻塞"
            r.limitReached -> "今日广告任务已停止：达到观看上限"
            r.failed > 0 || r.unconfirmed > 0 -> "广告任务部分完成，需要人工确认"
            r.stopped -> "广告任务已停止"
            else -> "本次广告任务已结束"
        }
        val text=TelegramText.officialDaily(r.date,OfficialAdStore.counter(ctx,r.date),DailyAdStore.allAttempts(ctx,r.date).count{it.runId==r.runId&&it.completed},r.unconfirmed,
            if(r.blockedReason.isNotBlank()||r.stopped)TelegramText.reason(if(r.stopped)"user_stopped" else r.blockedReason) else "")
        try {
            NotificationManagerCompat.from(ctx).notify(ID_SUMMARY, base(ctx, title, text).build())
            Logger.i("NOTIFY", "ad summary posted: $title | ${r.line()}")
        } catch (t: Throwable) {
            Logger.w("NOTIFY", "ad summary failed: ${t.message}")
        }
    }

    fun routineSummary(ctx:Context,runId:String,text:String){
        TelegramNotifier.enqueue(ctx,runId,text)
        ensureChannel(ctx)
        if(canNotify(ctx))NotificationManagerCompat.from(ctx).notify(ID_SUMMARY,base(ctx,"每日流程执行结果",text).build())
        Logger.i("NOTIFY","routine summary posted run="+runId)
    }

    fun challengeSummary(ctx: Context, runId: String, result: String) {
        runCatching { TelegramNotifier.challenge(ctx, runId, result) }
        ensureChannel(ctx)
        if (!canNotify(ctx)) return
        NotificationManagerCompat.from(ctx).notify(ID_SUMMARY,
            base(ctx, "挑战接取与 Boost 执行结果", result + "\n任务奖励额度变更不代表积分已到账。\n" + runId).build())
        Logger.i("NOTIFY", "challenge summary posted: " + runId + " " + result)
    }

    fun storageBlocked(ctx: Context) {
        ensureChannel(ctx)
        if (!canNotify(ctx)) return
        NotificationManagerCompat.from(ctx).notify(ID_SUMMARY,
            base(ctx, "广告任务已阻止启动", "计数记录无法可靠读取或写入，请查看执行日志后处理。").build())
    }

    fun scheduleVerified(ctx: Context) {
        ensureChannel(ctx)
        if (!canNotify(ctx)) return
        NotificationManagerCompat.from(ctx).notify(ID_SUMMARY,
            base(ctx, "本机调度自检已触发", "系统闹钟已送达；本次未启动广告。" + TokyoDate.time() + " JST").build())
    }

    fun serviceRestored(ctx:Context){runCatching{NotificationManagerCompat.from(ctx).cancel(ID_SERVICE)}}

    fun needService(ctx: Context) {
        ensureChannel(ctx)
        if (!canNotify(ctx)) return
        val text = "定时任务已触发，但无障碍服务未连接，无法操作界面。请打开应用并确认无障碍服务处于开启状态。"
        try {
            NotificationManagerCompat.from(ctx)
                .notify(ID_SERVICE, base(ctx, "需要开启无障碍服务", text).build())
            Logger.w("NOTIFY", "service-needed notification posted")
        } catch (_: Throwable) {
        }
    }
}
