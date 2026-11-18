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
}
