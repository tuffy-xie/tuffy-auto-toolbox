package com.anapocket.auto

/** Finite recovery of one locally bound Boost ad; an unknown outcome never permits a new ad. */
object OpaqueRecoveryPolicy {
    fun recoveryAlreadyUsed(stage: String, attempts: Int): Boolean = stage.isNotBlank() || attempts >= 2
    fun mayRecoverLoadFailure(initial: Boolean, observedFailure: Boolean, bound: Boolean, alreadyTried: Boolean,
        verifiedFailures: Int, boost: Int, steps: List<Int>): Boolean =
        !initial && observedFailure && bound && !alreadyTried && verifiedFailures == 0 &&
            boost in 0..4 && steps.size == 5 && steps.all { it > 0 }
    fun mayRecover(initial: Boolean, timedOut: Boolean, departed: Boolean, sameRun: Boolean,
        opaque: Boolean, alreadyTried: Boolean, verifiedFailures: Int, boost: Int, steps: List<Int>): Boolean =
        !initial && timedOut && departed && sameRun && opaque && !alreadyTried && verifiedFailures == 0 &&
            boost in 0..4 && steps.size == 5 && steps.all { it > 0 }
    fun unlockedAnaCard(description: String): Boolean = description.trim() in setOf(
        "ANA Pocket,未加锁", "ANA Pocket,Unlocked", "ANA Pocket,ロック解除")
    fun outcome(boost: Int, before: Int, expected: Int, originalSteps: List<Int>, labels: List<String>): String? {
        if (boost !in 0..4 || originalSteps.size != 5 || ChallengeSignals.increments(labels) != originalSteps ||
            before + originalSteps[boost] != expected) return null
        val next = ChallengeSignals.nextBoost(labels)
        val offered = ChallengeSignals.offeredReward(labels)
        return when {
            next == boost + 1 && offered == before -> "NO_GAIN"
            offered == expected && (next == boost + 2 || boost == 4 && next == null) -> "ADVANCED"
            else -> null
        }
    }
}
