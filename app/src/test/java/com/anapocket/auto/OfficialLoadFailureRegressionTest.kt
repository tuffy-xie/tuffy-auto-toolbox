package com.anapocket.auto
import org.junit.Assert.*
import org.junit.Test

class OfficialLoadFailureRegressionTest {
    private val date="2026-10-31"
    private fun good(i:Int)=AdAttempt("r",date,i,i.toLong(),AdState.REWARD_CONFIRMED,true,true,100,officialBefore=i-1,officialAfter=i)
    private fun fail(i:Int,before:Int)=AdAttempt("r",date,i,i.toLong(),AdState.FAILED,false,false,100,"official_error_dialog",before)
    @Test fun explicitNativeFailureAndFreshSameCounterCanBeReviewed(){
        assertTrue(OfficialLoadFailurePolicy.mayReview(listOf(good(1),fail(2,1)),date,date,1,true,30000,emptySet()))
    }
    @Test fun unknownGenericErrorAndMissingProofNeverQualify(){
        val a=fail(1,0)
        for(b in listOf(a.copy(state=AdState.UNCONFIRMED),a.copy(failureReason="network_error"),a.copy(finishedAt=0)))
            assertFalse(OfficialLoadFailurePolicy.mayReview(listOf(b),date,date,0,true,30000,emptySet()))
        assertFalse(OfficialLoadFailurePolicy.mayReview(listOf(a),date,date,0,false,30000,emptySet()))
    }
    @Test fun otherDateChangedCounterAndCooldownReject(){
        val r=listOf(fail(1,0))
        assertFalse(OfficialLoadFailurePolicy.mayReview(r,date,"2026-11-01",0,true,30000,emptySet()))
        assertFalse(OfficialLoadFailurePolicy.mayReview(r,date,date,1,true,30000,emptySet()))
        assertFalse(OfficialLoadFailurePolicy.mayReview(r,date,date,0,true,29999,emptySet()))
    }
    @Test fun priorUnknownRemainsBlocking(){
        assertFalse(OfficialLoadFailurePolicy.mayReview(listOf(fail(1,0).copy(state=AdState.UNCONFIRMED),fail(2,0)),date,date,0,true,30000,emptySet()))
    }
    @Test fun sameFailureAndReplacementPastTheDailyLimitNeverQualify(){
        val r=(1..5).map{fail(it,0)};val keys=r.map{OfficialLoadFailurePolicy.key(it)}
        assertFalse(OfficialLoadFailurePolicy.mayReview(listOf(r[0]),date,date,0,true,30000,setOf(keys[0])))
        // 11/19: the third review is now allowed (limit 4), the fifth still is not.
        assertTrue(OfficialLoadFailurePolicy.mayReview(r.take(3),date,date,0,true,30000,keys.take(2).toSet()))
        assertTrue(OfficialLoadFailurePolicy.mayReview(r.take(4),date,date,0,true,30000,keys.take(3).toSet()))
        assertFalse(OfficialLoadFailurePolicy.mayReview(r,date,date,0,true,30000,keys.take(4).toSet()))
    }
    @Test fun nov19ReservationThatWasNeverArmedIsReviewedLikeAStall(){
        val rows=(1..24).map{good(it).copy(officialBefore=it-1,officialAfter=it)}
        val r=AdAttempt("r",date,25,25L,AdState.UNCONFIRMED,false,false,100,"process_interrupted",24,dispatchPhase="RESERVED")
        assertTrue(OfficialLoadFailurePolicy.undispatched(r));assertTrue(OfficialLoadFailurePolicy.reviewable(r))
        assertFalse(OfficialLoadFailurePolicy.blankStall(r));assertFalse(OfficialLoadFailurePolicy.quietReturn(r))
        assertTrue(OfficialLoadFailurePolicy.mayReviewBlank(rows+r,date,date,24,true,false,60_000,emptySet(),false))
        assertFalse(OfficialLoadFailurePolicy.mayReviewBlank(rows+r,date,date,24,true,false,59_999,emptySet(),false))
        assertFalse(OfficialLoadFailurePolicy.mayReviewBlank(rows+r,date,date,25,true,false,60_000,emptySet(),false))
        assertFalse(OfficialLoadFailurePolicy.mayReviewBlank(rows+r,date,date,24,false,false,60_000,emptySet(),false))
        // Anything that may have reached the tap is never this case.
        for(x in listOf(r.copy(dispatchPhase="ARMED",dispatchArmedAt=5),r.copy(dispatchPhase="DISPATCHED"),r.copy(dispatchArmedAt=5),
            r.copy(adDepartureObserved=true),r.copy(failureReason="completion_not_confirmed"),r.copy(finishedAt=0)))
            assertFalse(OfficialLoadFailurePolicy.undispatched(x))
    }
    @Test fun nov19ThirdFrozenDailyStallIsReviewedButAFifthIsNot(){
        fun stall(i:Int,before:Int)=AdAttempt("r",date,i,i.toLong(),AdState.UNCONFIRMED,false,false,100,"ad_timeout_95s",before,
            dispatchPhase="DISPATCHED",adDepartureObserved=true)
        val s3=stall(3,2);val s11=stall(11,9);val s17=stall(17,14)
        val rows=listOf(good(1),good(2),s3)+(4..10).map{good(it).copy(officialBefore=it-2,officialAfter=it-1)}+s11+
            (12..16).map{good(it).copy(officialBefore=it-3,officialAfter=it-2)}+s17
        val two=setOf(OfficialLoadFailurePolicy.key(s3),OfficialLoadFailurePolicy.key(s11))
        assertTrue(OfficialLoadFailurePolicy.mayReviewBlank(rows,date,date,14,true,false,60_000,two,true))
        assertFalse(OfficialLoadFailurePolicy.mayReviewBlank(rows,date,date,15,true,false,60_000,two,true))
        assertFalse(OfficialLoadFailurePolicy.mayReviewBlank(rows,date,date,14,true,false,60_000,two,false))
        val four=two+setOf("x:1:1","x:2:2")
        assertFalse(OfficialLoadFailurePolicy.mayReviewBlank(rows,date,date,14,true,false,60_000,four,true))
    }
    @Test fun onlyVerifiedFailuresPermit26Or27Starts(){
        val a=fail(1,0);val key=OfficialLoadFailurePolicy.key(a)
        val rows=listOf(a)+(2..25).map{good(it)}
        assertTrue(OfficialLoadFailurePolicy.canReserve(24,24,true,false,rows,setOf(key)))
        assertFalse(OfficialAdQuota.canReserve(24,24,true,false,rows))
        assertFalse(OfficialLoadFailurePolicy.canReserve(25,25,true,false,rows,setOf(key)))
        assertFalse(OfficialLoadFailurePolicy.canReserve(24,24,true,true,rows,setOf(key)))
        assertFalse(OfficialLoadFailurePolicy.canReserve(24,24,true,false,rows+good(26),setOf(key)))
        assertEquals(27,OfficialLoadFailurePolicy.startCap(2))
        assertEquals(29,OfficialLoadFailurePolicy.startCap(4))
        assertEquals(25,OfficialLoadFailurePolicy.startCap(5))
    }
    @Test fun reviewedFailedRowIsPreservedAndUnknownStillBlocksRoutine(){
        val a=fail(1,0);val keys=setOf(OfficialLoadFailurePolicy.key(a))
        assertTrue(OfficialLoadFailurePolicy.routineAllowed(true,"",listOf(a,good(2)),keys))
        assertEquals(AdState.FAILED,a.state);assertFalse(a.rewardConfirmed)
        assertFalse(OfficialLoadFailurePolicy.routineAllowed(true,"",listOf(a,good(2).copy(completed=false,rewardConfirmed=false,state=AdState.UNCONFIRMED)),keys))
    }
    @Test fun onlyExactNativeAlertAndBoundedNewReviewsGrantBudget(){
        val nodes=listOf("android.widget.TextView id=us.ana_pocket.ANAPocket:id/alertTitle enabled=true label=広告表示に失敗しました。",
            "android.widget.TextView id=android:id/message enabled=true label=恐れ入りますが、アプリの再起動をお試しください。")
        assertTrue(OfficialLoadFailurePolicy.nativeAlert("androidx.appcompat.app.AlertDialog",nodes))
        assertFalse(OfficialLoadFailurePolicy.nativeAlert("ChromeTabbedActivity",nodes))
        assertFalse(OfficialLoadFailurePolicy.nativeAlert("AlertDialog",nodes.map{it.replace("us.ana_pocket.ANAPocket","other.pkg")}))
        assertFalse(OfficialLoadFailurePolicy.nativeAlert("AlertDialog",nodes.dropLast(1)))
        assertEquals(25,OfficialLoadFailurePolicy.runCap(25,1,1))
        assertEquals(26,OfficialLoadFailurePolicy.runCap(25,1,2))
        assertEquals(27,OfficialLoadFailurePolicy.runCap(25,0,2))
    }
}
