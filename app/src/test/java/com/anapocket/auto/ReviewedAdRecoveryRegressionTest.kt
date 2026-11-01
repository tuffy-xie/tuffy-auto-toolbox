package com.anapocket.auto
import org.junit.Assert.*
import org.junit.Test

class ReviewedAdRecoveryRegressionTest {
    private val d="2026-10-31"
    private fun good(i:Int)=AdAttempt("r",d,i,i.toLong(),AdState.REWARD_CONFIRMED,true,true,100,officialBefore=i-1,officialAfter=i)
    private val failed=AdAttempt("r",d,6,6,AdState.FAILED,false,false,100,"official_error_dialog",5)
    private val pending=AdAttempt("r",d,7,7,AdState.UNCONFIRMED,false,false,100,"ad_timeout_95s",5)
    private val rows=(1..5).map{good(it)}+failed+pending
    private val keys=setOf(OfficialLoadFailurePolicy.key(failed))
    @Test fun reviewedPrefixAllowsOnlySameLastAttemptRecovery(){
        assertFalse(OfficialAdRecoveryPolicy.mayBind(rows,d,d,5,true))
        assertTrue(ReviewedAdRecoveryPolicy.mayBind(rows,d,d,5,true,keys))
        assertEquals(AdState.FAILED,failed.state);assertEquals(7,pending.index);assertEquals(7L,pending.startedAt)
    }
    @Test fun anotherUnknownAndUnreviewedFailureRemainBlocking(){
        assertFalse(ReviewedAdRecoveryPolicy.mayBind(rows,d,d,5,true,emptySet()))
        assertFalse(ReviewedAdRecoveryPolicy.mayBind(rows.dropLast(1)+pending.copy(index=7)+pending.copy(index=8),d,d,5,true,keys))
        assertFalse(ReviewedAdRecoveryPolicy.mayBind(rows,d,d,5,true,setOf("unknown-key")))
    }
    @Test fun dateCounterDepartureAndReasonAreStillChecked(){
        assertFalse(ReviewedAdRecoveryPolicy.mayBind(rows,d,"2026-11-01",5,true,keys))
        assertFalse(ReviewedAdRecoveryPolicy.mayBind(rows,d,d,6,true,keys))
        assertFalse(ReviewedAdRecoveryPolicy.mayBind(rows,d,d,5,false,keys))
        assertFalse(ReviewedAdRecoveryPolicy.mayBind(rows.dropLast(1)+pending.copy(failureReason="arbitrary_error"),d,d,5,true,keys))
    }
    @Test fun finalReviewedFailureCannotExposeAnEarlierUnknown(){
        val finalFailure=failed.copy(index=8,startedAt=8)
        assertFalse(ReviewedAdRecoveryPolicy.mayBind(rows+finalFailure,d,d,5,true,keys+OfficialLoadFailurePolicy.key(finalFailure)))
    }
}
