package com.anapocket.auto

/** A freshly reopened official page must settle before reserving another viewing. */
class DailyAdBaseline {
    private var value: Int? = null
    private var since = 0L
    private var samples = 0
    fun observe(now: Long, counter: Int?, entryReady: Boolean): Int? {
        if (!entryReady || counter == null || counter !in 0..25) { value = null; samples = 0; return null }
        if (counter != value) { value = counter; since = now; samples = 1 } else samples++
        return counter.takeIf { samples >= 4 && now - since >= 3000L }
    }
}

/** Only a deliberate NEW run, after a verified fresh official page; never resolve old ambiguous ads. */
object DailyAdCounterRefreshPolicy {
    fun candidate(rows: List<AdAttempt>, runId: String, halt: String): Boolean =
        halt == "counter_jump_ambiguous" && rows.size < DAILY_AD_LIMIT && rows.isNotEmpty() &&
        rows.all { it.terminal && it.runId != runId && it.state in setOf(AdState.UNCONFIRMED, AdState.AD_FINISHED, AdState.REWARD_CONFIRMED) } &&
        rows.last().let { it.state == AdState.UNCONFIRMED && it.failureReason == "counter_jump_ambiguous" &&
            it.officialBefore in 0..23 && it.officialAfter in (it.officialBefore + 2)..25 }

    fun canContinue(rows: List<AdAttempt>, runId: String, halt: String, refreshed: Int?): Boolean {
        val old = rows.filter { it.runId != runId }
        val own = rows.filter { it.runId == runId }
        return refreshed != null && refreshed in 0..24 && candidate(old, runId, halt) &&
            refreshed >= old.last().officialAfter && rows.size < DAILY_AD_LIMIT &&
            own.all { it.terminal && it.state == AdState.REWARD_CONFIRMED && it.completed && it.rewardConfirmed }
    }
}
