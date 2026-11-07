package com.anapocket.auto

import org.junit.Assert.*
import org.junit.Test

class RecordedDailyReceiptTest {
    private val previous=AdAttempt("AD-previous","2026-11-06",1,100,state=AdState.REWARD_CONFIRMED,completed=true,rewardConfirmed=true,finishedAt=200,officialBefore=0,officialAfter=1)
    private val original=AdAttempt("AD-original","2026-11-06",2,300,state=AdState.UNCONFIRMED,completed=false,rewardConfirmed=true,finishedAt=400,
        failureReason="completion_not_confirmed",officialBefore=1,dispatchPhase="DISPATCHED",dispatchCompletedAt=310,adDepartureObserved=true,officialBinding="bound")
    private fun allowed(a:AdAttempt=original,fresh:Int?=2,proof:Boolean=true,today:String="2026-11-06",before:AdAttempt=previous)=
        OfficialAdRecoveryPolicy.recordedReceipt(listOf(before,a),"2026-11-06",today,fresh,proof)
    @Test fun savedNativeReceiptAndFreshExactCounterResolveOriginalNotReplacement(){
        assertTrue(allowed());assertEquals("AD-original",original.runId);assertEquals(300L,original.startedAt)
        assertTrue(allowed(original.copy(state=AdState.AD_RETURNED,finishedAt=0,rewardConfirmed=false)))
        val committed=original.copy(state=AdState.REWARD_CONFIRMED,completed=true,officialAfter=2)
        assertFalse(allowed(committed)) // Store's idempotent path does not apply the transition a second time.
    }
    @Test fun cachedCounterWalletOrMissingOriginalReceiptCannotSupplySuccess(){
        assertFalse(allowed(fresh=1));assertFalse(allowed(fresh=3));assertFalse(allowed(fresh=4));assertFalse(allowed(fresh=null));assertFalse(allowed(proof=false))
        assertFalse(allowed(original.copy(officialBinding="")));assertFalse(allowed(original.copy(adDepartureObserved=false)))
        assertFalse(allowed(original.copy(dispatchPhase="ARMED")));assertFalse(allowed(original.copy(failureReason="ad_timeout_95s")))
    }
    @Test fun otherUnsettledAttemptAndCrossDayRemainBlocked(){
        assertFalse(allowed(today="2026-11-07"));assertFalse(allowed(before=previous.copy(completed=false)))
        assertFalse(allowed(original.copy(date="2026-11-05")))
    }
}
