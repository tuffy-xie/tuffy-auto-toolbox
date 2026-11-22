package com.anapocket.auto

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

/** 11/19 Batch 1: Tier 4 single Back (user-approved) and the widened in-card restart gate. JVM only, no device. */
class StallSingleBackTest {
    /** after: what the screen shows once the Back was dispatched ("ignored", "receipt", "native_no_receipt"). */
    private class Port(val after:String="ignored",val dispatch:Boolean=true,val moving:Boolean=false):AdInteractionPort {
        var time=0L;var backs=0;var backAt=-1L;var proofWrites=0;var dismissed=false
        val events=mutableListOf<String>()
        override fun now()=time
        override fun stopped()=false
        override suspend fun pause(ms:Long){time+=ms}
        override suspend fun close()=false
        override suspend fun dismissReceipt(){dismissed=true}
        override suspend fun state(state:AdState){events.add(state.name)}
        override suspend fun frame():AdFrame {
            if(backAt<0||after=="ignored"||time<backAt+1_600)return AdFrame(true,false,adSurface=true)
            return when(after){
                "receipt"->if(!dismissed)AdFrame(true,false,receipt=true) else AdFrame(true,true,1)
                else->AdFrame(true,true,0)
            }
        }
        override suspend fun stallProgressKey():String=if(moving)"tree:${time/8_000}" else "tree:static"
        override suspend fun stallReviewed(quietMs:Long,key:String){proofWrites++;events.add("stall_committed")}
        override suspend fun stallBack():Boolean{backs++;if(dispatch)backAt=time;return dispatch}
    }

    @Test fun ignoredBackFallsBackToTheRestartProofOnce()=runBlocking {
        val p=Port("ignored");val r=AdInteraction(p).awaitReceipt(0)
        assertEquals("ad_timeout_95s",r.reason);assertEquals(AdState.UNCONFIRMED,r.state)
        assertEquals(1,p.backs);assertEquals(1,p.proofWrites)
        // 95 s budget + 30 s quiet, then at most the 10 s grace before the restart path takes over.
        assertTrue(p.time in 125_000..137_000)
        assertFalse(r.reward||r.completed)
    }
    @Test fun backThatLeadsToTheOfficialReceiptConfirmsWithoutRestart()=runBlocking {
        val p=Port("receipt");val r=AdInteraction(p).awaitReceipt(0)
        assertEquals(AdState.REWARD_CONFIRMED,r.state);assertTrue(r.reward&&r.completed);assertEquals(1,r.after)
        assertEquals(1,p.backs);assertEquals(0,p.proofWrites)
    }
    @Test fun backThatReturnsWithoutReceiptStaysUnknownAndIsNeverAStall()=runBlocking {
        val p=Port("native_no_receipt");val r=AdInteraction(p).awaitReceipt(0)
        assertEquals(AdState.UNCONFIRMED,r.state);assertEquals("completion_not_confirmed",r.reason)
        assertFalse(r.reward||r.completed);assertEquals(1,p.backs);assertEquals(0,p.proofWrites)
    }
    @Test fun refusedBackKeepsThePreviousBehaviour()=runBlocking {
        val p=Port(dispatch=false);val r=AdInteraction(p).awaitReceipt(0)
        assertEquals("ad_timeout_95s",r.reason);assertEquals(1,p.backs);assertEquals(1,p.proofWrites)
        assertTrue(p.time in 125_000..130_000)
    }
    @Test fun movingAdvertisementIsNeverSentBack()=runBlocking {
        val p=Port(moving=true);val r=AdInteraction(p).awaitReceipt(0)
        assertEquals("ad_timeout_300s",r.reason);assertEquals(0,p.backs);assertEquals(0,p.proofWrites)
    }
    @Test fun backPolicyNeedsPlayTimeQuietAdWindowAndNoEarlierBack() {
        assertTrue(OpaqueRecoveryPolicy.mayStallBack(95_000,30_000,true,false))
        assertFalse(OpaqueRecoveryPolicy.mayStallBack(64_999,30_000,true,false))
        assertFalse(OpaqueRecoveryPolicy.mayStallBack(95_000,29_999,true,false))
        assertFalse(OpaqueRecoveryPolicy.mayStallBack(95_000,30_000,false,false))
        assertFalse(OpaqueRecoveryPolicy.mayStallBack(95_000,30_000,true,true))
    }
    @Test fun inCardRestartAcceptsAnyTimeoutWithFrozenProofOrTheAbsoluteCap() {
        for(r in listOf("ad_timeout_95s","ad_part_timeout_95s","ad_timeout_300s"))assertTrue(OpaqueRecoveryPolicy.inCardTimeout(r))
        for(r in listOf("completion_not_confirmed","reward_receipt_missing","counter_jump_ambiguous","user_stopped",""))
            assertFalse(OpaqueRecoveryPolicy.inCardTimeout(r))
        // 11/19 01:04: AppLovin playable, no canvas shape, node tree frozen -> proof exists.
        assertTrue(OpaqueRecoveryPolicy.stallEvidence(false,true,"ad_timeout_95s",true))
        assertFalse(OpaqueRecoveryPolicy.stallEvidence(false,false,"ad_timeout_95s",true))
        assertTrue(OpaqueRecoveryPolicy.stallEvidence(false,false,"ad_timeout_300s",true))
        assertFalse(OpaqueRecoveryPolicy.stallEvidence(false,false,"ad_timeout_300s",false))
        assertTrue(OpaqueRecoveryPolicy.reviewRequired("ad_timeout_95s"));assertTrue(OpaqueRecoveryPolicy.reviewRequired("ad_part_timeout_95s"))
        assertFalse(OpaqueRecoveryPolicy.reviewRequired("ad_timeout_300s"))
        // The initial join and every other existing gate stay closed.
        assertFalse(OpaqueRecoveryPolicy.mayRecover(true,true,true,true,true,false,0,2,listOf(50,50,50,100,100)))
    }
    @Test fun openWindowPastTheAbsoluteCapNeedsNoFrozenProof() {
        val start=1_000_000L
        assertTrue(StalledAdRestartPolicy.pastAbsoluteCap(start,start+330_000,true))
        assertFalse(StalledAdRestartPolicy.pastAbsoluteCap(start,start+329_999,true))
        assertFalse(StalledAdRestartPolicy.pastAbsoluteCap(start,start+3_600_000,false))
        assertFalse(StalledAdRestartPolicy.pastAbsoluteCap(0,3_600_000,true))
        assertFalse(StalledAdRestartPolicy.pastAbsoluteCap(start,start-1,true))
    }
    @Test fun dailyBlankReviewCoversPartAndAbsoluteTimeoutsButKeepsTheFrozenProofForSegments() {
        val date="2026-11-19"
        val a=AdAttempt(date=date,runId="AD1",index=3,startedAt=1L,finishedAt=2L,officialBefore=5,state=AdState.UNCONFIRMED,
            dispatchPhase="DISPATCHED",adDepartureObserved=true,failureReason="ad_part_timeout_95s")
        assertTrue(OfficialLoadFailurePolicy.blankStall(a))
        assertFalse(OfficialLoadFailurePolicy.mayReviewBlank(listOf(a),date,date,5,true,false,60_000,emptySet(),false))
        assertTrue(OfficialLoadFailurePolicy.mayReviewBlank(listOf(a),date,date,5,true,false,60_000,emptySet(),true))
        val cap=a.copy(failureReason="ad_timeout_300s")
        assertTrue(OfficialLoadFailurePolicy.blankStall(cap))
        assertTrue(OfficialLoadFailurePolicy.mayReviewBlank(listOf(cap),date,date,5,true,false,60_000,emptySet(),false))
        assertFalse(OfficialLoadFailurePolicy.mayReviewBlank(listOf(cap),date,date,6,true,false,60_000,emptySet(),false))
        assertFalse(OfficialLoadFailurePolicy.blankStall(a.copy(failureReason="completion_not_confirmed")))
    }
    @Test fun nov21UnprovenTimeoutIsReviewedOnlyAfterThirtyQuietMinutes() {
        val date="2026-11-21"
        // Real rows: #13 timed out at 12 and was reviewed; #19 timed out at 17 behind a Mintegral dialog, no frozen proof.
        val r13=AdAttempt(date=date,runId="ADb215930c",index=13,startedAt=1795195104356L,finishedAt=1795195199785L,officialBefore=12,
            state=AdState.UNCONFIRMED,dispatchPhase="DISPATCHED",adDepartureObserved=true,failureReason="ad_timeout_95s")
        val r19=r13.copy(runId="AD970f78de",index=19,startedAt=1795195743552L,finishedAt=1795195839153L,officialBefore=17)
        val rows=listOf(r13,r19);val reviewed=setOf(OfficialLoadFailurePolicy.key(r13))
        val m30=OfficialLoadFailurePolicy.QUIET_RETURN_MS
        // 04:12 review: 102 minutes later, window gone, counter still 17.
        assertTrue(OfficialLoadFailurePolicy.mayReviewBlank(rows,date,date,17,true,false,1795201926452L-r19.finishedAt,reviewed,false))
        assertTrue(OfficialLoadFailurePolicy.mayReviewBlank(rows,date,date,17,true,false,m30,reviewed,false))
        assertFalse(OfficialLoadFailurePolicy.mayReviewBlank(rows,date,date,17,true,false,m30-1,reviewed,false))
        assertFalse(OfficialLoadFailurePolicy.mayReviewBlank(rows,date,date,17,false,false,m30,reviewed,false))  // ad window still there
        assertFalse(OfficialLoadFailurePolicy.mayReviewBlank(rows,date,date,18,true,false,m30,reviewed,false))   // counter moved
        assertFalse(OfficialLoadFailurePolicy.mayReviewBlank(rows,date,date,17,true,true,m30,reviewed,false))    // a receipt exists
        assertFalse(OfficialLoadFailurePolicy.mayReviewBlank(rows,date,date,17,true,false,m30,emptySet(),false)) // #13 unresolved
        assertFalse(OfficialLoadFailurePolicy.mayReviewBlank(rows,date,"2026-11-22",17,true,false,m30,reviewed,false))
        val four=(1..4).map{"x$it"}.toSet()
        assertFalse(OfficialLoadFailurePolicy.mayReviewBlank(rows,date,date,17,true,false,m30,reviewed+four,false))
        // After the review #19 counts as resolved, while its own row stays UNCONFIRMED.
        assertTrue(OfficialLoadFailurePolicy.resolved(r19,reviewed+OfficialLoadFailurePolicy.key(r19)))
        assertEquals(AdState.UNCONFIRMED,r19.state)
    }
    @Test fun nov22EveryWrittenReviewSourceIsAcceptedWhenReadBack() {
        val date="2026-11-22"
        // Real row: daily #2 timed out at 03:30:48 with no frozen proof and was reviewed at 09:21:41.
        val d2=AdAttempt(date=date,runId="AD2ecadacb-33b6-4601-836e-8aacdaf14db3",index=2,startedAt=1795285752452L,finishedAt=1795285848079L,
            officialBefore=1,state=AdState.UNCONFIRMED,dispatchPhase="DISPATCHED",adDepartureObserved=true,failureReason="ad_timeout_95s")
        val unproven=OfficialLoadFailurePolicy.blankReviewSource(d2,false)
        assertEquals(OfficialLoadFailurePolicy.SOURCE_BLANK_UNPROVEN,unproven)
        assertTrue(OfficialLoadFailurePolicy.reviewSourceMatches(unproven,d2))
        assertTrue(OfficialLoadFailurePolicy.reviewSourceMatches(OfficialLoadFailurePolicy.blankReviewSource(d2,true),d2))
        val quiet=d2.copy(failureReason="completion_not_confirmed")
        assertTrue(OfficialLoadFailurePolicy.reviewSourceMatches(OfficialLoadFailurePolicy.blankReviewSource(quiet,false),quiet))
        val reserved=d2.copy(failureReason="process_interrupted",dispatchPhase="RESERVED",adDepartureObserved=false)
        assertTrue(OfficialLoadFailurePolicy.reviewSourceMatches(OfficialLoadFailurePolicy.blankReviewSource(reserved,false),reserved))
        // A source never matches a row of another kind, and an unknown source is never accepted.
        assertFalse(OfficialLoadFailurePolicy.reviewSourceMatches(unproven,quiet))
        assertFalse(OfficialLoadFailurePolicy.reviewSourceMatches(OfficialLoadFailurePolicy.SOURCE_LOAD_FAILURE,d2))
        assertFalse(OfficialLoadFailurePolicy.reviewSourceMatches("something_else",d2))
    }
    @Test fun nov22FinishedTravelUnknownsNoLongerHoldTheTimedOutDailyAdWindow() {
        // Real: the only unresolved travel ad was MOV_ANY 5km #5 (03:02:48-03:03:00, stopped by the watchdog);
        // daily #2 started 03:29:12 and its AppLovin end card stayed open until the day was given up.
        val daily=1795285752452L
        val car15=1795284168739L to 1795284180412L
        assertFalse(StalledAdRestartPolicy.travelHoldsDailyRestart(daily,listOf(car15)))
        assertFalse(StalledAdRestartPolicy.travelHoldsDailyRestart(daily,emptyList()))
        // A travel ad that has not ended, or that ran after the daily ad started, may own the window in front.
        assertTrue(StalledAdRestartPolicy.travelHoldsDailyRestart(daily,listOf(car15,1795276600000L to 0L)))
        assertTrue(StalledAdRestartPolicy.travelHoldsDailyRestart(daily,listOf(daily+1 to daily+90_000)))
        assertTrue(StalledAdRestartPolicy.travelHoldsDailyRestart(daily,listOf(daily-60_000 to daily+5_000)))
        assertTrue(StalledAdRestartPolicy.travelHoldsDailyRestart(daily,listOf(0L to 0L)))
        assertTrue(StalledAdRestartPolicy.travelHoldsDailyRestart(0L,emptyList()))
    }
}
