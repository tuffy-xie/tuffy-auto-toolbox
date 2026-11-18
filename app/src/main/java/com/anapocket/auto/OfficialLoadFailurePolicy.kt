package com.anapocket.auto

/** Fresh native load-failure proof grants a bounded replacement; original rows remain FAILED. */
object OfficialLoadFailurePolicy {
    fun key(a:AdAttempt)=a.runId+":"+a.index+":"+a.startedAt
    fun failedLoad(a:AdAttempt)=a.terminal&&a.state==AdState.FAILED&&!a.completed&&!a.rewardConfirmed&&
        a.failureReason=="official_error_dialog"&&a.officialBefore in 0..24
    /**
     * A timed-out daily ad with no reward observed. The original row stays UNCONFIRMED; review only frees a new attempt.
     * 11/19 (Gemini review E): a multi-part ad stuck in a later part, or an ad that hit the 300 s absolute cap, is the same
     * kind of timeout; before this only the 95 s whole-ad reason was reviewable and the other two kept ADS blocked all day.
     */
    val BLANK_TIMEOUTS=setOf("ad_timeout_95s","ad_part_timeout_95s","ad_timeout_300s")
    fun blankStall(a:AdAttempt)=a.terminal&&a.state==AdState.UNCONFIRMED&&!a.completed&&!a.rewardConfirmed&&
        a.failureReason in BLANK_TIMEOUTS&&a.dispatchPhase=="DISPATCHED"&&a.adDepartureObserved&&a.officialBefore in 0..24
    /** The 300 s cap is absolute (StalledAdRestartPolicy.needsStallReview); segment timeouts still need the frozen proof. */
    fun stallProofSatisfied(a:AdAttempt,stallVerified:Boolean)=stallVerified||a.failureReason=="ad_timeout_300s"
    /**
     * 11/16 #15 (user-approved): the ad went out, came back to ANA by itself, and no reward was seen. Never replayed and the
     * row stays UNCONFIRMED; only after [QUIET_RETURN_MS] with a closed ad window and an unchanged counter is a new attempt freed.
     */
    fun quietReturn(a:AdAttempt)=a.terminal&&a.state==AdState.UNCONFIRMED&&!a.completed&&!a.rewardConfirmed&&
        a.failureReason=="completion_not_confirmed"&&a.dispatchPhase=="DISPATCHED"&&a.adDepartureObserved&&a.officialBefore in 0..24
    const val QUIET_RETURN_MS=30*60_000L
    fun reviewable(a:AdAttempt)=failedLoad(a)||blankStall(a)||quietReturn(a)
    fun resolved(a:AdAttempt,reviewed:Set<String>)=
        a.terminal&&a.state==AdState.REWARD_CONFIRMED&&a.completed&&a.rewardConfirmed||reviewable(a)&&key(a) in reviewed
    /**
     * [stallVerified]: the original ad was proven frozen (same evidence unchanged for 30 s) before its window went away.
     * A window that closed without that proof (a person watched or closed it, the SDK ended on its own) and an unchanged
     * counter cannot show that no reward is still coming, so it never frees a new attempt; the day's ADS stay BLOCKED.
     */
    fun mayReviewBlank(rows:List<AdAttempt>,date:String,today:String,counter:Int?,adClosed:Boolean,receipt:Boolean,elapsed:Long,reviewed:Set<String>,stallVerified:Boolean):Boolean {
        val a=rows.lastOrNull()?:return false
        return date==today&&a.date==date&&adClosed&&!receipt&&reviewed.size<2&&key(a) !in reviewed&&
            (blankStall(a)&&stallProofSatisfied(a,stallVerified)&&elapsed>=60_000||quietReturn(a)&&elapsed>=QUIET_RETURN_MS)&&
            counter==a.officialBefore&&rows.dropLast(1).all{it.date==date&&resolved(it,reviewed)}
    }
    fun mayReview(rows:List<AdAttempt>,date:String,today:String,counter:Int?,nativeProof:Boolean,elapsed:Long,reviewed:Set<String>):Boolean {
        val a=rows.lastOrNull()?:return false
        return date==today&&a.date==date&&nativeProof&&elapsed>=30_000&&reviewed.size<2&&key(a) !in reviewed&&
            failedLoad(a)&&counter==a.officialBefore&&rows.dropLast(1).all{it.date==date&&resolved(it,reviewed)}
    }
    fun runCap(base:Int,initial:Int,current:Int)=base+(current-initial).coerceIn(0,2)
    fun nativeAlert(activity:String,lines:List<String>)=activity.endsWith("AlertDialog")&&
        lines.any{it.contains("id=us.ana_pocket.ANAPocket:id/alertTitle ")&&it.endsWith("label=広告表示に失敗しました。")}&&
        lines.any{it.contains("id=android:id/message ")&&it.contains("アプリの再起動をお試しください")}
    fun startCap(reviewed:Int)=25+if(reviewed in 0..2)reviewed else 0
    fun canReserve(counter:Int?,previous:Int?,sameDate:Boolean,stopped:Boolean,rows:List<AdAttempt>,reviewed:Set<String>)=
        sameDate&&!stopped&&counter!=null&&counter in 0..24&&(previous==null||counter>=previous)&&
        reviewed.size in 1..2&&reviewed.all{k->rows.any{key(it)==k&&reviewable(it)}}&&
        rows.size<startCap(reviewed.size)&&rows.all{resolved(it,reviewed)}
    fun routineAllowed(limit:Boolean,blocked:String,own:List<AdAttempt>,reviewed:Set<String>)=
        reviewed.size<=2&&RoutineAdCompletion.allowed(limit,blocked,own.filterNot{reviewable(it)&&key(it) in reviewed}.map{it.completed to it.rewardConfirmed})
}
