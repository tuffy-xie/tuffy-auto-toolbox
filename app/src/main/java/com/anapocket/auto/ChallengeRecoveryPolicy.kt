package com.anapocket.auto

/** A manual restart can observe the same dispatched ad, never reserve a replacement. */
object ChallengeRecoveryPolicy {
    fun officialNoGainReady(elapsedMs:Long,loading:Boolean)=elapsedMs>=15_000&&!loading
    fun blockingOfficialLoading(id:String?,className:String,containsScreenCenter:Boolean)=
        className=="android.widget.ProgressBar"&&id?.endsWith("/boost_bar")!=true&&containsScreenCenter
    /** The native official resume prompt resumes one bound attempt; it is not a new ad entry. */
    fun officialPromptAllowed(taskState:String,adState:String,reason:String,confirmed:Boolean,initial:Boolean,
        index:Int,attempts:Int,pendingCount:Int,sameDate:Boolean,boost:Int,before:Int,expected:Int,
        steps:List<Int>,requests:Int,officialPrompt:Boolean):Boolean =
        officialPrompt&&sameDate&&pendingCount==1&&!initial&&requests in 0..1&&boost in 0..4&&
            steps.size==5&&steps.all{it>0}&&before>0&&expected==before+steps[boost]&&
            canResume(taskState,adState,reason,confirmed,index,attempts,true)
    /** Only the authenticated debug operator-review path uses this; it never grants a new ad start. */
    fun reviewableFailure(reason: String, initial: Boolean): Boolean = if (initial) reason == "ad_timeout_95s" else
        reason in setOf("ad_timeout_95s", "ad_part_timeout_95s", "ad_timeout_300s", "completion_not_confirmed")
    fun canResume(taskState: String, adState: String, reason: String, confirmed: Boolean,
        index: Int, attempts: Int, observedAd: Boolean): Boolean =
        observedAd && !confirmed && index == attempts && attempts in 1..7 &&
            taskState in setOf("AD_OPENING", "UNCONFIRMED") &&
            (adState in setOf("AD_OPENING", "AD_PLAYING", "AD_CLOSE_AVAILABLE") ||
                adState == "UNCONFIRMED" && reason in setOf("ad_timeout_95s", "ad_part_timeout_95s", "ad_timeout_300s", "process_or_run_interrupted", "user_stopped"))
}
