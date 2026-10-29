package com.anapocket.auto

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * 运行状态存储（沿用 SharedPreferences，避免引入 Room 的额外构建风险）。
 * 保存：最近一次运行、执行历史（最近 20 条）、当日广告已完成次数、最近一次队列快照。
 */
object RunStore {
    private const val PREF = "ana_runs"
    private const val MAX_HISTORY = 20

    private fun sp(ctx: Context) = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    fun record(ctx: Context, runId: String, summary: String) {
        sp(ctx).edit().putString("last", "${System.currentTimeMillis()}|$runId|$summary").apply()
    }

    fun last(ctx: Context): String? = sp(ctx).getString("last", null)

    fun appendHistory(ctx: Context, runId: String, summary: String, ts: Long = System.currentTimeMillis()) {
        val s = sp(ctx)
        val arr = try {
            JSONArray(s.getString("history", "[]") ?: "[]")
        } catch (t: Throwable) {
            JSONArray()
        }
        val o = JSONObject()
        o.put("ts", ts)
        o.put("runId", runId)
        o.put("summary", summary)
        val out = JSONArray()
        out.put(o)
        var i = 0
        while (i < arr.length() && out.length() < MAX_HISTORY) {
            out.put(arr.getJSONObject(i))
            i++
        }
        s.edit().putString("history", out.toString()).apply()
    }

    fun history(ctx: Context): List<String> {
        val raw = sp(ctx).getString("history", "[]") ?: "[]"
        val out = ArrayList<String>()
        try {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                out.add("${o.optLong("ts")}|${o.optString("runId")}|${o.optString("summary")}")
            }
        } catch (_: Throwable) {
        }
        return out
    }

    /** 当日已完成广告次数（本地计数，仅在广告被 App 记账后递增）。 */
    fun adDone(ctx: Context, date: String): Int =
        sp(ctx).getInt("ad_done_$date", 0)

    fun setAdDone(ctx: Context, date: String, v: Int) {
        sp(ctx).edit().putInt("ad_done_$date", v.coerceAtLeast(0)).apply()
    }

    fun bumpAdDone(ctx: Context, date: String): Int {
        val v = adDone(ctx, date) + 1
        setAdDone(ctx, date, v)
        return v
    }

    fun saveQueue(ctx: Context, runId: String, tasks: List<AutoTask>) {
        sp(ctx).edit()
            .putString("queue_$runId", AutoTask.listToJson(tasks))
            .putString("queue_last_id", runId)
            .apply()
    }

    fun loadQueue(ctx: Context, runId: String): List<AutoTask> =
        AutoTask.listFromJson(sp(ctx).getString("queue_$runId", null))

    fun lastQueueId(ctx: Context): String? = sp(ctx).getString("queue_last_id", null)

    fun lastRunAt(ctx: Context): Long = sp(ctx).getLong("last_run_at", 0L)

    fun setLastRunAt(ctx: Context, v: Long) {
        sp(ctx).edit().putLong("last_run_at", v).apply()
    }

    /** 闹钟触发时无障碍服务可能尚未连接：先挂起，等服务连上再执行。 */
    fun setPendingTrigger(ctx: Context, reason: String) {
        sp(ctx).edit().putString("pending_trigger", reason).apply()
    }

    fun pendingTrigger(ctx: Context): String? = sp(ctx).getString("pending_trigger", null)

    fun clearPendingTrigger(ctx: Context) {
        sp(ctx).edit().remove("pending_trigger").apply()
    }
}
