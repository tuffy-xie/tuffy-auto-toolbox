package com.anapocket.auto

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class AdInteractionTest {
    private class Port(val scenario: String, val before: Int = 0) : AdInteractionPort {
        var time = 0L
        var closes = 0
        var dismissed = false
        val trace = mutableListOf<String>()
        override fun now() = time
        override fun stopped() = scenario == "stop" && time >= 1600
        override suspend fun pause(ms: Long) { time += ms }
        override suspend fun state(state: AdState) { trace += "$time $state" }
        override suspend fun close(): Boolean { closes++; trace += "$time close_node_clicked"; return true }
        override suspend fun dismissReceipt() { dismissed = true; trace += "$time ANA_receipt_acknowledged" }
        override suspend fun frame(): AdFrame = when (scenario) {
            "success" -> when {
                time < 800 -> AdFrame(true, true, before)
                time < 3200 -> AdFrame(true, false, adSurface = true)
                closes == 0 -> AdFrame(true, false, closeAvailable = true, adSurface = true)
                !dismissed -> AdFrame(true, false, receipt = true)
                else -> AdFrame(true, true, before + 1)
            }
            "returned" -> if (time < 40000) AdFrame(true, false, adSurface = true) else AdFrame(true, true, before)
            "counter_only" -> if (time < 1600) AdFrame(true, false, adSurface = true) else AdFrame(true, true, before + 1)
            "foreign_receipt" -> if (time < 1600) AdFrame(false, false, receipt = true, adSurface = true) else AdFrame(true, true, before)
            "stale_receipt" -> AdFrame(true, true, before, receipt = true)
            "jump" -> if (time < 1600) AdFrame(true, false, adSurface = true) else AdFrame(true, true, before + 2)
            "error" -> AdFrame(true, true, before, error = true)
            "blocked" -> AdFrame(false, false, blocked = true)
            "limit" -> AdFrame(true, true, 25, limit = true)
            "close_only" -> AdFrame(true, false, closeAvailable = true, adSurface = true)
            else -> AdFrame(true, false, adSurface = true)
        }
    }
    private fun run(scenario: String): Pair<Port, AdReceipt> = runBlocking {
        val p = Port(scenario)
        p to AdInteraction(p).awaitReceipt(0)
    }
    @Test fun controlledInteractionHasSeparateCompletionAndRewardEvidence() {
        val (p, r) = run("success")
        assertEquals(AdState.REWARD_CONFIRMED, r.state)
        assertTrue(r.completed); assertTrue(r.reward); assertEquals(1, r.after)
        assertEquals(1, p.closes)
        assertTrue(p.trace.any { it.contains("AD_PLAYING") })
        assertTrue(p.trace.any { it.contains("AD_CLOSE_AVAILABLE") })
        assertTrue(p.trace.any { it.contains("AD_FINISHED") })
        println("CONTROLLED_FIXTURE_RUN_ID=JVM-AD-001; source=scripted_test_transport; real_points=NOT_TESTED")
        println(p.trace.joinToString("\n")); println(r)
    }
    @Test fun longPlaybackAndReturnDoNotConfirmCompletion() {
        val (_, r) = run("returned")
        assertFalse(r.completed); assertFalse(r.reward)
        assertEquals(AdState.UNCONFIRMED, r.state)
    }
    @Test fun officialCounterIsCompletionButNotReward() {
        val (_, r) = run("counter_only")
        assertTrue(r.completed); assertFalse(r.reward)
        assertEquals(AdState.UNCONFIRMED, r.state)
    }
    @Test fun receiptFromForeignPackageIsNotTrusted() {
        val (_, r) = run("foreign_receipt")
        assertFalse(r.completed); assertFalse(r.reward)
    }
    @Test fun staleReceiptBeforeDepartureIsNotTrusted() {
        val (_, r) = run("stale_receipt")
        assertFalse(r.completed); assertFalse(r.reward)
    }
    @Test fun counterJumpCannotBeAttributedToOneAd() {
        val (_, r) = run("jump")
        assertFalse(r.completed); assertEquals("counter_jump_ambiguous", r.reason)
    }
    @Test fun timerAndCloseButtonDoNotConfirmReward() {
        val (p, r) = run("close_only")
        assertEquals(3, p.closes); assertFalse(r.completed); assertFalse(r.reward)
        assertEquals(AdState.UNCONFIRMED, r.state)
        assertEquals("ad_timeout_95s", r.reason)
    }
    @Test fun officialErrorStopsImmediately() {
        val (p, r) = run("error")
        assertEquals(0L, p.time); assertEquals(AdState.FAILED, r.state)
    }
    @Test fun stopPreservesUnconfirmedAttempt() {
        assertEquals("user_stopped", run("stop").second.reason)
    }
    @Test fun emptyTreeAloneDoesNotProveAnAdPlayed() = runBlocking {
        val p = object : AdInteractionPort {
            var time = 0L
            override fun now() = time
            override fun stopped() = false
            override suspend fun pause(ms: Long) { time += ms }
            override suspend fun state(state: AdState) { assertNotEquals(AdState.AD_PLAYING, state) }
            override suspend fun frame() = if (time < 2000) AdFrame(true, false) else AdFrame(true, true, 1, receipt = true)
            override suspend fun close() = false
            override suspend fun dismissReceipt() { fail("unattributed receipt must not be acknowledged") }
        }
        val r = AdInteraction(p).awaitReceipt(0)
        assertFalse(r.completed); assertFalse(r.reward)
    }
    @Test fun permissionBlockIsExplicit() {
        assertEquals(AdState.BLOCKED, run("blocked").second.state)
    }
    @Test fun officialLimitEndsFlow() {
        assertEquals(AdState.LIMIT_REACHED, run("limit").second.state)
    }
    @Test fun countParserNeverConfusesRemainingWithWatched() {
        assertEquals(3, AdSignals.watched(listOf("今日の視聴済み回数", "3/25")))
        assertNull(AdSignals.watched(listOf("視聴可能回数 22/25")))
        assertNull(AdSignals.watched(listOf("今日の視聴済み回数 3/30")))
        assertFalse(AdSignals.receipt(listOf("11/14まで報酬を受け取りましょう！", "報酬をゲットしよう")))
    }
    @Test fun twentyFiveControlledFlowsStopAndNewJstDayIsSeparate() = runBlocking {
        var ledger = emptyList<AdAttempt>()
        var physicalStarts = 0
        repeat(30) {
            if (AdDayLogic.canStart(ledger)) {
                val a = AdAttempt("JVM-AD-25", "2026-10-27", ledger.size + 1, 1000L)
                ledger = ledger + a; physicalStarts++
                val receipt = AdInteraction(Port("success", physicalStarts - 1)).awaitReceipt(physicalStarts - 1)
                ledger = AdDayLogic.finish(ledger, a.runId, a.index, receipt.state, receipt.completed,
                    receipt.reward, receipt.reason, physicalStarts - 1, receipt.after, "fixture", 2000L)
            }
        }
        assertEquals(25, physicalStarts)
        val c = AdDayLogic.counters("2026-10-27", ledger)
        assertEquals(25, c.started); assertEquals(25, c.completed); assertEquals(25, c.rewardConfirmed)
        assertEquals(0, c.remaining)
        assertTrue(AdDayLogic.canStart(AdDayLogic.forDate("2026-10-28", ledger)))
        assertEquals(0, AdDayLogic.counters("2026-10-28", ledger).started)
    }
    @Test fun interruptedAndUnconfirmedRecordsForbidFurtherAutomaticStarts() {
        val a = AdAttempt("KILLED", "2026-10-27", 1, 1000L)
        assertFalse(AdDayLogic.canStart(listOf(a)))
        val recovered = AdDayLogic.reconcileInterrupted(listOf(a), 5000L)
        assertFalse(AdDayLogic.canStart(recovered))
        assertEquals(recovered, AdDayLogic.reconcileInterrupted(recovered, 6000L))
    }
    @Test fun scheduleUsesTokyoRegardlessOfHostZone() {
        val now = Instant.parse("2026-10-26T23:00:00Z").toEpochMilli()
        assertEquals(Instant.parse("2026-10-27T00:05:00Z").toEpochMilli(), DailySchedulePolicy.next(9, 5, now))
        assertFalse(DailySchedulePolicy.due(9, 5, now))
        val at = Instant.parse("2026-10-27T00:05:00Z").toEpochMilli()
        assertTrue(DailySchedulePolicy.due(9, 5, at))
        assertEquals(Instant.parse("2026-10-28T00:05:00Z").toEpochMilli(), DailySchedulePolicy.next(9, 5, at))
    }
}
