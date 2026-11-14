package com.anapocket.auto

/** Recognition derives from the installed ANA2.3.5 layout and recorded native offer, never from a generic GET/claim label. */
object StartupPolicy {
    const val ANA = "us.ana_pocket.ANAPocket"
    fun loginContext(pkg:String?,labels:List<String>):Boolean = pkg==ANA&&
        labels.any{it.trim()=="ログインボーナス"}&&labels.any{it.contains("連続ログインでボーナスポイントUP")}
    fun loginVideoClaim(label:String):Boolean = Regex("^動画を見てさらに[1-9][0-9]{0,4}ptをGET$").matches(label.replace(Regex("\\s|　"),""))
    fun loginClaimId(pkg:String?,labels:List<String>,ids:Set<String>):String? {
        if(!loginContext(pkg,labels)||!labels.any{loginVideoClaim(it)}||"button_ok" !in ids)return null
        if("recycler" !in ids&&labels.filter{Regex("^[0-9]{1,2}日目$").matches(it.trim())}.distinct().size<2)return null
        return when {"button_get_point" in ids->"button_get_point";"bonus_point" in ids->"bonus_point";else->null}
    }
    fun loginOffer(pkg:String?,labels:List<String>,ids:Set<String>)=loginClaimId(pkg,labels,ids)!=null
    fun rewardReceipt(pkg: String?, labels: List<String>, ids: Set<String>, adSurface: Boolean): Boolean =
        pkg == ANA && !adSurface && AdSignals.receipt(labels) && "button_ok" in ids
    fun nativePopup(pkg: String?, ids: Set<String>, loginContext: Boolean, receipt: Boolean, adSurface: Boolean) =
        pkg == ANA && !loginContext && !receipt && !adSurface && "image_close" in ids && "content" in ids
    fun loginConfirmed(reserved: Boolean, observedAd: Boolean, officialFreshReceipt: Boolean, sameDate: Boolean) =
        reserved && observedAd && officialFreshReceipt && sameDate
    fun canReserveLogin(date: String, recordedDate: String?, recordedState: String?): Boolean =
        recordedDate != date || recordedState !in setOf("AD_OPENING", "AD_PLAYING", "UNCONFIRMED", "REWARD_CONFIRMED", "FAILED")
    /**
     * 11/12 02:40: a manual run after the night's runs found ANA closed (task exit cleanup) and stopped with
     * existing_login_task_unavailable_no_replay. A login attempt that already finished has no open ad to return to,
     * so ANA may simply be launched; a returned offer is then closed by [mayLeaveUnconfirmedOffer], never replayed.
     */
    fun mayFreshLaunchAfterFinishedLogin(date:String,recordedDate:String?,state:String?,finishedAt:Long)=
        recordedDate==date&&state in setOf("UNCONFIRMED","FAILED")&&finishedAt>0
    /** A terminal unknown reward stays unknown; only its recognized native offer may be dismissed. */
    fun mayLeaveUnconfirmedOffer(date:String,recordedDate:String?,state:String?,finishedAt:Long,knownLogin:Boolean,sdkActivity:Boolean)=
        recordedDate==date&&state in setOf("UNCONFIRMED","FAILED")&&finishedAt>0&&knownLogin&&!sdkActivity
    fun nativeBrowser(pkg:String?,activity:String,ids:Set<String>)=pkg==ANA&&activity==ANA+".views.common.WebViewActivity"&&
        ids.containsAll(setOf("toolbar","close","back","forward"))&&
        ("web_view" in ids||"webView_container" in ids)
}

object RoutinePolicy {
    /**
     * A stopped travel stage hands over to daily ads only when it left nothing open on the travel side:
     * no unconfirmed travel ad, no unisolated pending row and no Max Boost dialog. Otherwise ads wait.
     */
    fun adsAfterBlockedTravel(unresolvedTravelAd:Boolean, unisolatedPending:Boolean, maxBoostPending:Boolean, adsEnabled:Boolean):Boolean =
        adsEnabled && !unresolvedTravelAd && !unisolatedPending && !maxBoostPending
    /** With every travel category switched off by the user, travel yields; daily ads apply their own isolation gate. */
    fun travelYieldsToAds(anyCategoryOn:Boolean, maxBoostPending:Boolean):Boolean = !anyCategoryOn && !maxBoostPending
    fun reuseChallenges(sameDate:Boolean, currentDayOnly:Boolean,
        scopeVerified:Boolean, pending:Boolean):Boolean =
        sameDate && currentDayOnly && scopeVerified && !pending
    fun reuseAds(sameDate:Boolean, priorStage:String?, official:Int?, pending:Boolean):Boolean =
        sameDate && priorStage in setOf("DONE","LIMIT_REACHED","ALREADY_DONE") && official==DAILY_AD_LIMIT && !pending

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
