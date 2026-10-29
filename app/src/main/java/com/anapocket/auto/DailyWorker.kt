package com.anapocket.auto

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters

/** WorkManager 兜底触发（闹钟被系统压制时）。 */
class DailyWorker(ctx: Context, params: WorkerParameters) : Worker(ctx, params) {
    override fun doWork(): Result {
        Logger.init(applicationContext)
        Logger.i("WORKER", "backup trigger fired")
        Scheduler.dispatchDaily(applicationContext, "worker")
        return Result.success()
    }
}
