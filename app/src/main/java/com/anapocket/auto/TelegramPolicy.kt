package com.anapocket.auto

import java.security.MessageDigest

data class TelegramConfig(val enabled: Boolean = false, val botName: String = "", val token: String = "", val chatId: String = "") {
    override fun toString() = "TelegramConfig(enabled=$enabled, token=REDACTED)"
}
object TelegramPolicy {
    fun tokenValid(token: String) = Regex("^[0-9]{5,16}:[A-Za-z0-9_-]{20,}$").matches(token)
    fun chatValid(chat: String) = Regex("^-?[0-9]{1,20}$").matches(chat) || Regex("^@[A-Za-z][A-Za-z0-9_]{4,}$").matches(chat)
    fun normalizedName(name: String) = name.trim().removePrefix("@").lowercase(java.util.Locale.ROOT)
    fun matchesBot(configured: String, actual: String) = configured.isBlank() || normalizedName(configured) == normalizedName(actual)
    fun fingerprint(c: TelegramConfig): String = MessageDigest.getInstance("SHA-256").digest((c.token + "|" + c.chatId).toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    fun privateChat(starts: List<String>): String? = starts.distinct().singleOrNull()
    fun mayBeginDelivery(state: String) = state in setOf("", "retry")
    fun retry(attempt: Int, explicitlyRejected: Boolean, httpCode: Int) = attempt < 2 && explicitlyRejected && httpCode in setOf(429, 500, 502, 503, 504)
}
object TelegramText {
    fun officialDaily(date:String,official:Int?,runCompleted:Int,unknown:Int,reason:String):String {
        val lines=mutableListOf("Tuffy｜ANA每日广告｜"+date,"✅ 本次完成："+runCompleted+"次","🎬 官方次数："+(official?.let{it.toString()+"/25"}?:"未核对"))
        if(official!=null&&official<25)lines.add("⏳ 待完成："+(25-official)+"次")
        if(unknown>0)lines.add("⚠ 待核对："+unknown+"次")
        if(reason.isNotBlank())lines.add("原因："+reason)
        return lines.joinToString("\n")
    }
    fun daily(date: String, started: Int, completed: Int, reward: Int, failed: Int, unknown: Int,
        runCompleted: Int, runReward: Int, result: String): String =
        "ANA每日广告｜$date JST\n本次完成 $runCompleted，奖励回执 $runReward\n今日启动 $started/25 · 完成 $completed · 回执 $reward\n失败 $failed · 未确认 $unknown\n$result"
    fun challenge(date: String, walk: Int, bike: Int, car: Int, any: Int, unknown: Int, result: String): String =
        "ANA每日任务｜$date JST\n5/5：徒步$walk · 自行车$bike · 车$car · 不限$any\n$result" +
            (if (unknown > 0) "\n历史未确认 $unknown" else "") + "\nBoost额度不等于积分到账"
    fun reason(reason: String): String = when(reason) {
        "recorded_daily_receipt_counter_unconfirmed_no_replay" -> "原每日广告已播放，但当前次数无法唯一核对该次结果；UNKNOWN保留，已停止且未重播"
        "recorded_daily_receipt_page_unavailable" -> "原每日广告页面未能安全读取；记录保留，已停止且未重播"
        "official_limit_with_unconfirmed_attempt" -> "官方已满，本机个别观看结果待核对"
        "original_ad_surface_missing_counter_unchanged" -> "原广告界面已不在，官方次数核对未增加；该次观看仍待核对，未重看"
        "original_ad_surface_missing_receipt_unproven" -> "原广告界面已不在，次数有增加但缺少同条奖励回执；未重复观看"
        "original_ad_surface_missing_counter_unavailable" -> "原广告界面已不在，官方次数无法稳定核对；未重新观看"
        "original_ad_departure_proof_missing" -> "原观看身份缺少离开入口的证据，未重复观看"
        "original_ana_task_unavailable" -> "原ANA任务未能恢复，未重新打开广告"
        "original_ad_recovery_binding_or_budget_rejected" -> "原观看身份或恢复次数不满足安全条件，未新增观看"
        "existing_official_ad_recovery_not_confirmed" -> "原广告恢复未取得同条新鲜奖励回执，未新增观看"
        "accessibility_disconnected" -> "无障碍服务已断开，任务已保存；请正常开启服务后再核对"
        "new_official_attempt_requires_review" -> "上次广告结果未确认，已保存并暂停"
        "official_counter_regressed" -> "官方次数回退，请核对账号或页面"
        "official_attempt_safety_limit" -> "已到安全尝试上限，未继续观看"
        "official_reservation_blocked" -> "观看记录不能安全预留，已停止"
        "local_started_limit" -> "达到每日25次启动上限"
        "official_limit" -> "官方今日观看次数已满"
        "counter_jump_ambiguous" -> "官方次数跳变，已保存记录并停止"
        "day_halted_or_unconfirmed" -> "存在未确认记录，自动运行已停止"
        "official_error_dialog" -> "官方广告或网络报错，已停止"
        "user_stopped" -> "已按请求停止"
        "ad_timeout_95s", "ad_timeout_300s", "ad_part_timeout_95s" -> "广告超时，已保存记录"
        "reward_receipt_missing", "completion_not_confirmed" -> "回执未确认，已停止"
        "fresh_official_counter_unavailable", "official_baseline_changed_before_start" -> "官方次数尚未稳定，未继续启动"
        "accessibility_or_keyguard" -> "无障碍未连接或手机锁屏"
        "unfinished_challenge_blocks_daily_navigation" -> "挑战尚未结束，未启动每日广告"
        "" -> "执行结束"
        else -> "执行中止，请查看手机记录"
    }
}
