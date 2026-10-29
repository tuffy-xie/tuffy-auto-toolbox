package com.anapocket.auto

/** Resume the next unstarted Boost after an official advance or no-gain proof; never adopt an unknown ad. */
object JoinedChallengeResumePolicy {
    fun resolved(confirmed: Boolean, officialNoGain: Boolean, state: String): Boolean =
        confirmed || officialNoGain && state == "FAILED"
    fun matches(lastResolved: Boolean, index: Int, attempts: Int, boost: Int, offered: Int,
        steps: List<Int>, labels: List<String>): Boolean = lastResolved && index == attempts && attempts in 1..7 &&
            boost in 0..4 && offered > 0 && steps.size == 5 && ChallengeSignals.increments(labels) == steps &&
            ChallengeSignals.nextBoost(labels) == boost + 1 && ChallengeSignals.offeredReward(labels) == offered
}
