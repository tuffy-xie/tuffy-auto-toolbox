package com.anapocket.auto

/** 一次运行的汇总。语义严格：success 只统计有独立证据确认的任务。 */
data class RunOutcome(
    val runId: String,
    val success: Int,
    val failed: Int,
    val unconfirmed: Int,
    val already: Int,
    val skipped: Int,
    val stopped: Boolean,
    val detail: String
) {
    val partial: Boolean get() = failed > 0 || unconfirmed > 0

    /** 写入日志 / 报告的单行摘要，便于脚本用正则解析。 */
    fun line(): String =
        "success=$success failed=$failed unconfirmed=$unconfirmed already=$already skipped=$skipped stopped=$stopped"

    fun short(): String = "成功 $success · 失败 $failed · 未确认 $unconfirmed"
}
