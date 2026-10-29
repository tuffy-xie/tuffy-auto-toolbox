package com.anapocket.auto

/** A manual restart can observe the same dispatched ad, never reserve a replacement. */
object ChallengeRecoveryPolicy {
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
