package com.anapocket.auto

import org.junit.Assert.*
import org.junit.Test

/** 2026-11-15 engineering review R1-R6: stop/cancel endings, queued retries, stall proof, risk reads, Boost prompt. */
class RuntimeSafetyTest {
    private fun delay(state:String="BLOCKED",watchdog:Boolean=false,userStopped:Boolean=false)=
        RoutineRetryPolicy.nextDelay(state,"startup_ad_timeout_300s",watchdog,0,0,8,false,true,userStopped)

    // R1: the retry decision reads how the run actually ended.
    @Test fun cancellationIsAStopNotABusinessBlock(){
        assertEquals("STOPPED",RoutineRetryPolicy.terminalState("BLOCKED","service_destroyed",cancelled=true))
        assertEquals("STOPPED",RoutineRetryPolicy.terminalState("BLOCKED","",cancelled=true))
        assertEquals("BLOCKED",RoutineRetryPolicy.terminalState("BLOCKED","",cancelled=false))
        assertEquals("DONE",RoutineRetryPolicy.terminalState("DONE","",cancelled=false))
        // A stopped run's step may report BLOCKED when its next tap was refused; the user stop decides.
        assertEquals("STOPPED",RoutineRetryPolicy.terminalState("BLOCKED","user",cancelled=false))
        assertNull(delay(state=RoutineRetryPolicy.terminalState("BLOCKED","service_destroyed",true)))
        assertNull(delay(state=RoutineRetryPolicy.terminalState("BLOCKED","user",false)))
    }
    @Test fun watchdogCancellationKeepsItsBoundedRetry(){
        val state=RoutineRetryPolicy.terminalState("BLOCKED","watchdog",cancelled=true)
        assertEquals("STOPPED",state);assertEquals(10,delay(state=state,watchdog=true))
    }
    @Test fun userStoppedRunIsNeverRetriedWhateverItReported(){
        assertEquals(10,delay())
        assertNull(delay(userStopped=true))
        assertNull(delay(state="STOPPED",watchdog=true,userStopped=true))
    }

    // R2: a queued retry is authorized again when it fires.
    private fun drop(pending:String?="RT1",on:Boolean=true,stopped:String?=null,finished:Boolean=false,minute:Int=600)=
        RoutineRetryPolicy.dropReason(pending,on,stopped,finished,minute)
    @Test fun queuedRetryFiresOnlyWhileStillAuthorized(){
        assertNull(drop())
        assertEquals("no_pending_retry_for_today",drop(pending=null))
        assertEquals("schedule_disabled",drop(on=false))
        assertEquals("source_run_stopped_by_user",drop(stopped="RT1"))
        assertNull(drop(stopped="RT0"))   // an older, different stop does not cancel this retry
        assertEquals("day_finished",drop(finished=true))
        assertEquals("too_late",drop(minute=23*60+1))
        assertNull(drop(minute=23*60))
    }
    @Test fun disabledScheduleWinsOverEverythingElse(){
        assertEquals("schedule_disabled",drop(on=false,stopped="RT1",finished=true,minute=23*60+30))
    }

    // R1: one action boundary for every tap/click/swipe/Back.
    @Test fun stopGateOnlyBlocksWhileARunIsActive(){
        assertTrue(UiStopGate.refuses(running=true,stopRequested=true))
        assertFalse(UiStopGate.refuses(running=true,stopRequested=false))
        // A latch-time stop flag left without a run must not block a later check-in.
        assertFalse(UiStopGate.refuses(running=false,stopRequested=true))
    }

    // R6: "could not read" is not "no risk".
    @Test fun riskReadSeparatesFailureFromNoRisk(){
        val none=AccountRiskRead.bounded(3){null as String?}
        assertTrue(none.isSuccess);assertNull(none.getOrNull())
        var calls=0
        val recovered=AccountRiskRead.bounded(3){calls++;if(calls<3)throw IllegalStateException("stale node");"risk"}
        assertEquals("risk",recovered.getOrNull());assertEquals(3,calls)
        calls=0
        val failed=AccountRiskRead.bounded(3){calls++;throw IllegalStateException("stale node")}
        assertTrue(failed.isFailure);assertEquals(3,calls)
    }
    @Test(expected=kotlinx.coroutines.CancellationException::class) fun riskReadNeverSwallowsCancellation(){
        AccountRiskRead.bounded(3){throw kotlinx.coroutines.CancellationException("stop")}
    }

    // R3: a restart needs the same proven freeze; progress or no evidence refuses it.
    @Test fun stallReviewVerifiesOnlyAnUnchangedKeyForTheQuietWindow(){
        val t=StallQuietTracker(30_000)
        assertFalse(t.observe(0,"a"));assertFalse(t.observe(29_999,"a"));assertTrue(t.observe(30_000,"a"))
    }
    @Test fun slowAdThatKeepsChangingIsProgressNotAStall(){
        val t=StallQuietTracker(30_000)
        var now=0L;var verified=false
        repeat(30){verified=verified||t.observe(now,"frame"+(now/10_000));now+=1500}   // key changes every 10 s
        assertFalse(verified);assertEquals("progress_observed",t.outcome())
    }
    @Test fun unreadableScreenNeverCountsAsQuiet(){
        val t=StallQuietTracker(30_000)
        repeat(40){assertFalse(t.observe(it*1500L,null))}
        assertEquals("no_evidence",t.outcome())
        // Evidence that drops out restarts the quiet clock instead of bridging the gap.
        val g=StallQuietTracker(30_000)
        g.observe(0,"a");g.observe(20_000,null);assertFalse(g.observe(31_000,"a"));assertTrue(g.observe(61_000,"a"))
    }
    @Test fun onlySegmentTimeoutsNeedTheReview(){
        assertTrue(StalledAdRestartPolicy.needsStallReview("ad_timeout_95s"))
        assertTrue(StalledAdRestartPolicy.needsStallReview("ad_part_timeout_95s"))
        for(r in listOf("ad_timeout_300s","startup_ad_timeout_300s","completion_not_confirmed"))assertFalse(StalledAdRestartPolicy.needsStallReview(r))
        assertTrue(StalledAdRestartPolicy.REVIEW_WINDOW_MS>OpaqueRecoveryPolicy.STALL_QUIET_MS)
    }
    @Test fun treeDigestIsStableShortAndLabelFree(){
        val a=OpaqueRecoveryPolicy.treeDigest("Activity\nView|id|残り 12 秒|0,0,10,10")
        assertEquals(a,OpaqueRecoveryPolicy.treeDigest("Activity\nView|id|残り 12 秒|0,0,10,10"))
        assertNotEquals(a,OpaqueRecoveryPolicy.treeDigest("Activity\nView|id|残り 11 秒|0,0,10,10"))
        assertEquals(24,a.length);assertFalse(a.contains("残り"))
    }

    // R5: generic navigation names its refusal instead of declining a Boost.
    @Test fun boostPromptRefusalIsExplicit(){
        assertEquals("boost_dialog_requires_challenge_owner_no_skip",AutomationEngine.BOOST_DIALOG_REFUSED)
    }
}
