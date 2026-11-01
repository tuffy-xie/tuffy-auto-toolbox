package com.anapocket.auto

/** Recognition derives from the installed ANA2.3.4 layouts, never from a generic GET/claim label. */
object StartupPolicy {
    const val ANA = "us.ana_pocket.ANAPocket"
    fun loginOffer(pkg: String?, labels: List<String>, ids: Set<String>): Boolean = pkg == ANA &&
        labels.any { it.trim() == "ログインボーナス" } && labels.any { it.contains("連続ログインでボーナスポイントUP") } &&
        "button_get_point" in ids && "bonus_point" in ids && "recycler" in ids
    fun rewardReceipt(pkg: String?, labels: List<String>, ids: Set<String>, adSurface: Boolean): Boolean =
        pkg == ANA && !adSurface && AdSignals.receipt(labels) && "button_ok" in ids
    fun nativePopup(pkg: String?, ids: Set<String>, login: Boolean, receipt: Boolean, adSurface: Boolean) =
        pkg == ANA && !login && !receipt && !adSurface && "image_close" in ids && "content" in ids
    fun loginConfirmed(reserved: Boolean, observedAd: Boolean, officialFreshReceipt: Boolean, sameDate: Boolean) =
        reserved && observedAd && officialFreshReceipt && sameDate
    fun canReserveLogin(date: String, recordedDate: String?, recordedState: String?): Boolean =
        recordedDate != date || recordedState !in setOf("AD_OPENING", "AD_PLAYING", "UNCONFIRMED", "REWARD_CONFIRMED", "FAILED")
    /** A terminal unknown reward stays unknown; only its recognized native offer may be dismissed. */
    fun mayLeaveUnconfirmedOffer(date:String,recordedDate:String?,state:String?,finishedAt:Long,knownLogin:Boolean,sdkActivity:Boolean)=
        recordedDate==date&&state in setOf("UNCONFIRMED","FAILED")&&finishedAt>0&&knownLogin&&!sdkActivity
    fun nativeBrowser(pkg:String?,activity:String,ids:Set<String>)=pkg==ANA&&activity==ANA+".views.common.WebViewActivity"&&
        ids.containsAll(setOf("toolbar","close","back","forward"))&&
        ("web_view" in ids||"webView_container" in ids)
}

object RoutinePolicy {
    fun mayContinue(stopped: Boolean, sameDate: Boolean, phaseBlocked: Boolean) = !stopped && sameDate && !phaseBlocked
    fun stageLabel(stage: String) = when(stage) {
        "STARTUP" -> "打开ANA Pocket与签到"
        "CHALLENGES" -> "接取出行任务并升满Boost"
        "ADS" -> "观看每日广告"
        "NOTIFY" -> "发送结果通知"
        "DONE" -> "本次执行已结束"
        "STOPPED" -> "任务已停止"
        "BLOCKED" -> "需要核对后继续"
        else -> "准备就绪"
    }
}
