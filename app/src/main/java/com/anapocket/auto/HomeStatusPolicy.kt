package com.anapocket.auto

/** Pure decisions for the home screen, kept out of the Activity so they can be unit tested. */
object HomeStatusPolicy {
    enum class Primary { STOP, SAFETY, DONE, CONTINUE, START }
    enum class Tone { MUTED, WARN, DONE, ACTIVE }

    /** The day is over and nothing is left: the main button reports that instead of offering another run. */
    fun resultsOnly(finished: Boolean, failed: Boolean, incomplete: Boolean, running: Boolean, safetyPending: Boolean) =
        finished && !failed && !incomplete && !running && !safetyPending

    fun primary(running: Boolean, safetyPending: Boolean, resultsOnly: Boolean, hasTodayWork: Boolean) = when {
        running -> Primary.STOP
        safetyPending -> Primary.SAFETY
        resultsOnly -> Primary.DONE
        hasTodayWork -> Primary.CONTINUE
        else -> Primary.START
    }

    /** A row that is not yet maxed is progress while a run is active and unfinished once it has stopped. */
    fun tile(enabled: Boolean, km: Int, covered: Boolean, unknown: Boolean, total: Int, done: Int, running: Boolean, scanLabel: String): Pair<String, Tone> = when {
        !enabled -> "未选中" to Tone.MUTED
        km == 0 -> "需设公里" to Tone.WARN
        covered && total == 0 -> "官方暂无" to Tone.MUTED
        covered -> "已完成 $done 项" to Tone.DONE
        unknown && running -> "进行中 $done/$total" to Tone.ACTIVE
        unknown -> "未完成 $done/$total" to Tone.WARN
        total > 0 -> "待扫描 $done/$total" to Tone.MUTED
        else -> scanLabel to Tone.MUTED
    }

    /** Fraction of the enabled travel scope that is confirmed; a fully covered scope is complete even with no rows. */
    fun travelProgress(cats: Int, covered: Int, total: Int, done: Int): Float = when {
        cats == 0 -> 0f
        covered == cats -> 1f
        total > 0 -> (done.toFloat() / total).coerceIn(0f, 1f)
        else -> 0f
    }
}
