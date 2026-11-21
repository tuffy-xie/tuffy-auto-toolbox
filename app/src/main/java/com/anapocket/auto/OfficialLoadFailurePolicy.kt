package com.anapocket.auto

/** Fresh native load-failure proof grants a bounded replacement; original rows remain FAILED. */
object OfficialLoadFailurePolicy {
    /**
     * Reviews per day, each freeing one replacement attempt (so at most 25+[MAX_REVIEWS] starts). 11/19: three daily ads
     * stalled (#3, #11, #17); the third was proven frozen and the counter was unchanged, but the old limit of 2 kept the
     * remaining 11 ads blocked all day. Every review still needs the unchanged official counter; nothing is replayed.
     */
    const val MAX_REVIEWS=4
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
    /**
     * 11/19 09:57: attempt #28 was reserved and the run then failed before the ARMED fence was written, so no tap was ever
     * sent (the tap only follows a durable ARMED). On restart it is marked process_interrupted and nothing could free it.
     * Same review as the others: closed window, no receipt, and the fresh counter unchanged from its baseline.
     */
    fun undispatched(a:AdAttempt)=a.terminal&&a.state==AdState.UNCONFIRMED&&!a.completed&&!a.rewardConfirmed&&
        a.failureReason=="process_interrupted"&&a.dispatchPhase=="RESERVED"&&a.dispatchArmedAt==0L&&a.dispatchCompletedAt==0L&&
        !a.adDepartureObserved&&a.officialBefore in 0..24
    /**
     * 11/20 03:39 and 04:39: the timed-out #16 ad's task was already removed and the launcher was in front, but launcher and
     * Recents events carry no Activity name, so the SDK Activity stayed recorded and the blank review never started.
     * With the launcher in front no ad window is; the review still reopens ANA and re-checks the window before deciding.
     */
    fun adWindowInFront(sdkActivityRecorded:Boolean,currentPkg:String?,homePkg:String?)=
        sdkActivityRecorded&&!(currentPkg!=null&&currentPkg==homePkg)
    fun reviewable(a:AdAttempt)=failedLoad(a)||blankStall(a)||quietReturn(a)||undispatched(a)
    fun resolved(a:AdAttempt,reviewed:Set<String>)=
        a.terminal&&a.state==AdState.REWARD_CONFIRMED&&a.completed&&a.rewardConfirmed||reviewable(a)&&key(a) in reviewed
    /**
     * [stallVerified]: the original ad was proven frozen (same evidence unchanged for 30 s) before its window went away.
     * A window that closed without that proof (a person watched or closed it, the SDK ended on its own) and an unchanged
     * counter cannot show at 60 s that no reward is still coming, so it waits [QUIET_RETURN_MS] like a quiet return.
     * 11/21 #19 (user-approved): a Mintegral "Confirm to close?" dialog paused the ad, so no frozen proof was recorded; its
     * window was gone and the counter stayed 17 for four hours while the remaining 8 ads were blocked all day.
     * The original row stays UNCONFIRMED and is never replayed; only one new attempt is freed, within [MAX_REVIEWS].
     */
    fun mayReviewBlank(rows:List<AdAttempt>,date:String,today:String,counter:Int?,adClosed:Boolean,receipt:Boolean,elapsed:Long,reviewed:Set<String>,stallVerified:Boolean):Boolean {
        val a=rows.lastOrNull()?:return false
        return date==today&&a.date==date&&adClosed&&!receipt&&reviewed.size<MAX_REVIEWS&&key(a) !in reviewed&&
            (blankStall(a)&&(stallProofSatisfied(a,stallVerified)&&elapsed>=60_000||elapsed>=QUIET_RETURN_MS)||quietReturn(a)&&elapsed>=QUIET_RETURN_MS||
                undispatched(a)&&elapsed>=60_000)&&
            counter==a.officialBefore&&rows.dropLast(1).all{it.date==date&&resolved(it,reviewed)}
    }
    fun mayReview(rows:List<AdAttempt>,date:String,today:String,counter:Int?,nativeProof:Boolean,elapsed:Long,reviewed:Set<String>):Boolean {
        val a=rows.lastOrNull()?:return false
        return date==today&&a.date==date&&nativeProof&&elapsed>=30_000&&reviewed.size<MAX_REVIEWS&&key(a) !in reviewed&&
            failedLoad(a)&&counter==a.officialBefore&&rows.dropLast(1).all{it.date==date&&resolved(it,reviewed)}
    }
    fun runCap(base:Int,initial:Int,current:Int)=base+(current-initial).coerceIn(0,MAX_REVIEWS)
    fun nativeAlert(activity:String,lines:List<String>)=activity.endsWith("AlertDialog")&&
        lines.any{it.contains("id=us.ana_pocket.ANAPocket:id/alertTitle ")&&it.endsWith("label=広告表示に失敗しました。")}&&
        lines.any{it.contains("id=android:id/message ")&&it.contains("アプリの再起動をお試しください")}
    fun startCap(reviewed:Int)=25+if(reviewed in 0..MAX_REVIEWS)reviewed else 0
    fun canReserve(counter:Int?,previous:Int?,sameDate:Boolean,stopped:Boolean,rows:List<AdAttempt>,reviewed:Set<String>)=
        sameDate&&!stopped&&counter!=null&&counter in 0..24&&(previous==null||counter>=previous)&&
        reviewed.size in 1..MAX_REVIEWS&&reviewed.all{k->rows.any{key(it)==k&&reviewable(it)}}&&
        rows.size<startCap(reviewed.size)&&rows.all{resolved(it,reviewed)}
    fun routineAllowed(limit:Boolean,blocked:String,own:List<AdAttempt>,reviewed:Set<String>)=
        reviewed.size<=MAX_REVIEWS&&RoutineAdCompletion.allowed(limit,blocked,own.filterNot{reviewable(it)&&key(it) in reviewed}.map{it.completed to it.rewardConfirmed})
}
