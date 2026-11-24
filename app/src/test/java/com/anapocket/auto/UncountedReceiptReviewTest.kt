package com.anapocket.auto
import org.junit.Assert.*
import org.junit.Test

/** 11/24 #9 (8/25 for 17 hours) and 11/25 #8 (7/25): receipt seen, official counter unchanged; user-approved 11/25. */
class UncountedReceiptReviewTest {
    private val date="2026-11-25"
    private val P=OfficialLoadFailurePolicy
    private fun good(i:Int,before:Int)=AdAttempt("r",date,i,i.toLong(),AdState.REWARD_CONFIRMED,true,true,100,officialBefore=before,officialAfter=before+1)
    private fun uncounted(i:Int,before:Int)=AdAttempt("r",date,i,i.toLong(),AdState.UNCONFIRMED,false,true,100,"completion_not_confirmed",before,
        dispatchPhase="DISPATCHED",adDepartureObserved=true)
    private val day=(1..7).map{good(it,it-1)}+uncounted(8,7)
    private val old=P.UNCOUNTED_MIN_AGE_MS

    @Test fun onlyAReceiptTheCounterNeverTookIsThisKind() {
        assertTrue(P.receiptUncounted(uncounted(8,7)))
        assertFalse(P.receiptUncounted(uncounted(8,7).copy(rewardConfirmed=false)))           // no receipt: the 30-min quiet return
        assertFalse(P.receiptUncounted(uncounted(8,7).copy(failureReason="ad_timeout_95s")))
        assertFalse(P.receiptUncounted(uncounted(8,7).copy(adDepartureObserved=false)))
        assertFalse(P.receiptUncounted(good(8,7)))
        assertFalse(P.quietReturn(uncounted(8,7)))                                              // the two kinds never overlap
        assertTrue(P.reviewable(uncounted(8,7)))
    }
    @Test fun twoUnchangedReadsAfterTheRestartLetTheNextSlotOpen() {
        assertTrue(P.mayStartUncounted(day,date,date,true,old,emptySet(),0))
        assertTrue(P.mayReviewUncounted(day,date,date,true,true,true,7,7,old,emptySet(),0))
        assertFalse(P.mayReviewUncounted(day,date,date,true,false,true,7,7,old,emptySet(),0))   // ANA not restarted
        assertFalse(P.mayReviewUncounted(day,date,date,true,true,false,7,7,old,emptySet(),0))   // an ad window is still up
        assertFalse(P.mayReviewUncounted(day,date,date,true,true,true,7,8,old,emptySet(),0))    // counted after all
        assertFalse(P.mayReviewUncounted(day,date,date,true,true,true,null,7,old,emptySet(),0))
        assertFalse(P.mayReviewUncounted(day,date,date,false,true,true,7,7,old,emptySet(),0))   // no durable receipt
        assertFalse(P.mayStartUncounted(day,date,date,true,old-1,emptySet(),0))                 // too early
        assertFalse(P.mayStartUncounted(day,date,"2026-11-26",true,old,emptySet(),0))           // another day
    }
    @Test fun atMostTwoADayAndNeverPastTheReviewBudget() {
        assertFalse(P.mayStartUncounted(day,date,date,true,old,emptySet(),P.MAX_UNCOUNTED))
        val four=(1..P.MAX_REVIEWS).map{"k$it"}.toSet()
        assertFalse(P.mayStartUncounted(day,date,date,true,old,four,0))
        assertFalse(P.mayStartUncounted(day,date,date,true,old,setOf(P.key(uncounted(8,7))),0)) // already reviewed
        // An earlier unresolved row still stops it.
        assertFalse(P.mayStartUncounted(listOf(uncounted(1,0).copy(rewardConfirmed=false))+day.drop(1),date,date,true,old,emptySet(),0))
    }
    @Test fun aReviewedRowFreesExactlyOneNewStartAndStaysUnconfirmed() {
        val reviewed=setOf(P.key(uncounted(8,7)))
        assertTrue(day.all{P.resolved(it,reviewed)})
        assertTrue(P.reviewSourceMatches(P.SOURCE_RECEIPT_UNCOUNTED,uncounted(8,7)))
        assertFalse(P.reviewSourceMatches(P.SOURCE_RECEIPT_UNCOUNTED,uncounted(8,7).copy(rewardConfirmed=false)))
        assertTrue(P.canReserve(7,7,true,false,day,reviewed))
        assertEquals(26,P.startCap(reviewed.size))
        assertEquals(AdState.UNCONFIRMED,day.last().state)   // never turned into a GAIN
        assertFalse(P.canReserve(7,7,true,false,day,emptySet()))
    }
}
