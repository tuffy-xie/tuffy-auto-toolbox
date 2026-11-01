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
    @Test fun sameFailureAndThirdReplacementNeverQualify(){
        val a=fail(1,0);val b=fail(2,0);val c=fail(3,0)
        val k=OfficialLoadFailurePolicy.key(a)
        assertFalse(OfficialLoadFailurePolicy.mayReview(listOf(a),date,date,0,true,30000,setOf(k)))
        assertFalse(OfficialLoadFailurePolicy.mayReview(listOf(a,b,c),date,date,0,true,30000,setOf(k,OfficialLoadFailurePolicy.key(b))))
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
        assertEquals(25,OfficialLoadFailurePolicy.startCap(3))
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
