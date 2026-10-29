package com.anapocket.auto

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

enum class TaskKind { AD, CHALLENGE, BOOST, CLAIM }

/**
 * 任务状态。语义严格区分，避免把「界面返回」当成「奖励到账」：
 *  - SUCCESS       : 有独立证据确认（广告=积分/计数增长；挑战=出现预约成功弹窗）
 *  - UNCONFIRMED   : 动作完成但奖励未独立确认（例如广告播完、页面已返回）
 *  - ALREADY_DONE  : 检测到已参加/已预约，未重复操作
 *  - SKIPPED       : 用户未选择或已达目标
 *  - FAILED        : 明确失败，error 记录原因
 */
enum class TaskState { PENDING, RUNNING, SUCCESS, ALREADY_DONE, UNCONFIRMED, SKIPPED, FAILED }

data class AutoTask(
    val id: String,
    val kind: TaskKind,
    val dayOffset: Int,
    val date: String,
    val cat: Cat? = null,
    val km: Int? = null,
    /** v10：卡片级字段 */
    val title: String = "",
    val meters: Int = 0,
    val maxMeters: Int = 0,
    var boost: Int = 0,
    var boostMax: Int = 0,
    var state: TaskState = TaskState.PENDING,
    var startedAt: Long = 0L,
    var finishedAt: Long = 0L,
    var error: String = "",
    var note: String = ""
) {
    fun toJson(): JSONObject {
        val o = JSONObject()
        o.put("id", id)
        o.put("kind", kind.name)
        o.put("dayOffset", dayOffset)
        o.put("date", date)
        o.put("cat", cat?.name ?: JSONObject.NULL)
        o.put("km", km ?: JSONObject.NULL)
        o.put("title", title)
        o.put("meters", meters)
        o.put("maxMeters", maxMeters)
        o.put("boost", boost)
        o.put("boostMax", boostMax)
        o.put("state", state.name)
        o.put("startedAt", startedAt)
        o.put("finishedAt", finishedAt)
        o.put("error", error)
        o.put("note", note)
        return o
    }

    companion object {
        fun fromJson(o: JSONObject): AutoTask = AutoTask(
            id = o.optString("id"),
            kind = runCatching { TaskKind.valueOf(o.optString("kind")) }.getOrDefault(TaskKind.CHALLENGE),
            dayOffset = o.optInt("dayOffset", 0),
            date = o.optString("date"),
            cat = if (o.isNull("cat")) null else Cat.ofName(o.optString("cat")),
            km = if (o.isNull("km")) null else o.optInt("km"),
            title = o.optString("title"),
            meters = o.optInt("meters"),
            maxMeters = o.optInt("maxMeters"),
            boost = o.optInt("boost"),
            boostMax = o.optInt("boostMax"),
            state = runCatching { TaskState.valueOf(o.optString("state")) }.getOrDefault(TaskState.PENDING),
            startedAt = o.optLong("startedAt"),
            finishedAt = o.optLong("finishedAt"),
            error = o.optString("error"),
            note = o.optString("note")
        )

        fun listToJson(list: List<AutoTask>): String {
            val a = JSONArray()
            for (t in list) a.put(t.toJson())
            return a.toString()
        }

        fun listFromJson(s: String?): List<AutoTask> {
            if (s.isNullOrBlank()) return emptyList()
            return try {
                val a = JSONArray(s)
                val out = ArrayList<AutoTask>(a.length())
                for (i in 0 until a.length()) out.add(fromJson(a.getJSONObject(i)))
                out
            } catch (t: Throwable) {
                emptyList()
            }
        }
    }
}

/**
 * 把「用户选择」翻译成独立任务队列。
 * 广告任务数量 = 目标次数 − 当日已完成次数（本地记录），而不是固定 25。
 */
object QueueBuilder {

    fun build(ctx: Context, s: AppSettings): List<AutoTask> {
        val today = Dates.today()
        val list = ArrayList<AutoTask>()

        // 广告任务由 DailyAdTaskController 独立管理（固定 25 次），
        // 不再进入挑战队列，避免双计数与重复启动。

        if (s.dayToday) {
            for (c in Cat.values()) {
                if (!s.catOn(c)) continue
                list.add(
                    AutoTask(
                        id = "CH-$today-${c.name}",
                        kind = TaskKind.CHALLENGE,
                        dayOffset = 0,
                        date = today,
                        cat = c,
                        km = s.kmOf(c),
                        maxMeters = s.kmOf(c) * 1000
                    )
                )
            }
        }

        if (s.dayTomorrow) {
            val tm = Dates.tomorrow()
            for (c in Cat.values()) {
                if (!s.catOn(c)) continue
                list.add(
                    AutoTask(
                        id = "CH-$tm-${c.name}",
                        kind = TaskKind.CHALLENGE,
                        dayOffset = 1,
                        date = tm,
                        cat = c,
                        km = s.kmOf(c),
                        maxMeters = s.kmOf(c) * 1000
                    )
                )
            }
        }

        return list
    }
}
