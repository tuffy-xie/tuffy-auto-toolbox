package com.anapocket.auto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class DailyAdTaskTest {

    private fun attempt(
        index: Int,
        state: AdState = AdState.REWARD_CONFIRMED,
        completed: Boolean = true,
        reward: Boolean = true
    ) = AdAttempt(
        runId = "R1",
        date = "2026-10-27",
        index = index,
        startedAt = 1_000L + index,
        state = state,
        completed = completed,
        rewardConfirmed = reward,
        finishedAt = 2_000L + index
    )

    @Test
    fun tokyoDateRollsAtJstMidnightNotUtc() {
        assertEquals("2026-10-26", TokyoDate.of(Instant.parse("2026-10-26T14:59:59Z").toEpochMilli()))
        assertEquals("2026-10-27", TokyoDate.of(Instant.parse("2026-10-26T15:00:00Z").toEpochMilli()))
    }

    @Test
    fun limitIsFixedAt25Starts() {
        val attempts = (1..25).map { attempt(it) }
        assertFalse(AdDayLogic.canStart(attempts))
        assertEquals(0, AdDayLogic.counters("2026-10-27", attempts).remaining)
    }

    @Test
    fun countersKeepStartedCompletedRewardFailedAndUnconfirmedSeparate() {
        val list = listOf(
            attempt(1),
            attempt(2, AdState.UNCONFIRMED, completed = true, reward = false),
            attempt(3, AdState.FAILED, completed = false, reward = false),
            attempt(4, AdState.REWARD_CONFIRMED, completed = true, reward = true)
        )
        val c = AdDayLogic.counters("2026-10-27", list)
        assertEquals(4, c.started)
        assertEquals(3, c.completed)
        assertEquals(2, c.rewardConfirmed)
        assertEquals(1, c.failed)
        assertEquals(1, c.unconfirmed)
        assertEquals(21, c.remaining)
    }

    @Test
    fun interruptedAttemptBecomesUnconfirmedAndIsNotCompleted() {
        val inflight = AdAttempt(
            runId = "R1", date = "2026-10-27", index = 1,
            startedAt = 1000L, state = AdState.AD_PLAYING
        )
        val out = AdDayLogic.reconcileInterrupted(listOf(inflight), now = 5000L)
        assertEquals(AdState.UNCONFIRMED, out[0].state)
        assertEquals("process_interrupted", out[0].failureReason)
        assertFalse(out[0].completed)
        assertEquals(0, AdDayLogic.counters("2026-10-27", out).completed)
    }

    @Test
    fun finishingSameAttemptTwiceDoesNotDoubleCount() {
        val base = listOf(AdAttempt("R1", "2026-10-27", 1, 1000L, AdState.AD_PLAYING))
        val once = AdDayLogic.finish(
            base, "R1", 1, AdState.REWARD_CONFIRMED, true, true,
            "", 1, 2, "e1", now = 5000L
        )
        val twice = AdDayLogic.finish(
            once, "R1", 1, AdState.REWARD_CONFIRMED, true, true,
            "", 1, 2, "e2", now = 6000L
        )
        assertEquals(1, AdDayLogic.counters("2026-10-27", twice).completed)
        assertEquals(1, AdDayLogic.counters("2026-10-27", twice).rewardConfirmed)
        assertEquals("e1", twice[0].evidence)
    }

    @Test fun unknownStillBlocksAutomaticStart() {
        val old = listOf(attempt(1, AdState.UNCONFIRMED, false, false))
        assertFalse(AdDayLogic.canStart(old))
        assertTrue(AdDayLogic.canStartFreshManual(old, "NEW", ""))
        assertEquals(0, AdDayLogic.counters("2026-10-27", old).completed)
    }
    @Test fun manualRecoveryCannotReuseRunOrInflightAttempt() {
        val old = listOf(attempt(1, AdState.UNCONFIRMED, false, false))
        assertFalse(AdDayLogic.canStartFreshManual(old, "R1", ""))
        assertFalse(AdDayLogic.canStartFreshManual(old + attempt(2, AdState.AD_PLAYING, false, false).copy(finishedAt = 0), "NEW", ""))
    }
    @Test fun manualRecoveryCannotBypassLimitOrOfficialFailure() {
        val old = (1..25).map { attempt(it, AdState.UNCONFIRMED, false, false) }
        assertFalse(AdDayLogic.canStartFreshManual(old, "NEW", ""))
        for (reason in listOf("official_limit", "official_error_dialog", "counter_jump_ambiguous", "unknown_halt")) {
            assertFalse(reason, AdDayLogic.canStartFreshManual(old.take(1), "NEW", reason))
        }
    }
    @Test fun finishingNewAttemptPreservesUnknownAndDoesNotReopenAutomaticRun() {
        val unknown = attempt(1, AdState.UNCONFIRMED, false, false)
        val fresh = attempt(2, AdState.AD_OPENING, false, false).copy(runId = "NEW", finishedAt = 0)
        val out = AdDayLogic.finish(listOf(unknown, fresh), "NEW", 2, AdState.REWARD_CONFIRMED, true, true, "", 2, 3, "real", now = 6000)
        assertEquals(unknown, out[0])
        assertEquals(2, AdDayLogic.counters("2026-10-27", out).started)
        assertEquals(1, AdDayLogic.counters("2026-10-27", out).unconfirmed)
        assertEquals(1, AdDayLogic.counters("2026-10-27", out).rewardConfirmed)
        assertFalse(AdDayLogic.canStart(out))
    }

    @Test fun explicitManualBatchAdvancesNewAttemptsWhileKeepingOldUnknownRecords() {
        val old = listOf(attempt(1), attempt(2, AdState.UNCONFIRMED, false, false),
            attempt(3, AdState.UNCONFIRMED, false, false), attempt(4, AdState.UNCONFIRMED, false, false),
            attempt(5, AdState.UNCONFIRMED, false, false))
        var rows = old
        assertTrue(AdDayLogic.canStartFreshManual(rows, "BATCH", "ad_timeout_95s"))
        repeat(30) {
            if (AdDayLogic.canContinueManualBatch(rows, "BATCH", "ad_timeout_95s")) {
                val a = AdAttempt("BATCH", "2026-10-27", rows.size + 1, 1000)
                rows = AdDayLogic.finish(rows + a, "BATCH", a.index, AdState.REWARD_CONFIRMED, true, true,
                    "", a.index - 1, a.index, "proof", 2000)
            }
        }
        assertEquals(old, rows.take(5))
        assertEquals(25, rows.size)
        val c = AdDayLogic.counters("2026-10-27", rows)
        assertEquals(21, c.completed); assertEquals(21, c.rewardConfirmed); assertEquals(4, c.unconfirmed)
        assertFalse(AdDayLogic.canContinueManualBatch(rows, "BATCH", "ad_timeout_95s"))
        assertFalse(AdDayLogic.canStart(rows))
    }
    @Test fun manualBatchCannotContinuePastItsOwnUnconfirmedOrInflightAttempt() {
        val old = listOf(attempt(1, AdState.UNCONFIRMED, false, false))
        val ownUnknown = attempt(2, AdState.UNCONFIRMED, false, false).copy(runId = "BATCH")
        assertFalse(AdDayLogic.canContinueManualBatch(old + ownUnknown, "BATCH", "ad_timeout_95s"))
        assertFalse(AdDayLogic.canContinueManualBatch(old + ownUnknown.copy(finishedAt = 0), "BATCH", ""))
        assertFalse(AdDayLogic.canContinueManualBatch(old, "BATCH", "official_error_dialog"))
        assertFalse(AdDayLogic.canContinueManualBatch(old, "BATCH", "official_limit"))
    }
}
