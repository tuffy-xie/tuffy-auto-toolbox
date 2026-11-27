package com.anapocket.auto

import org.junit.Assert.*
import org.junit.Test

/** Nov 27: 217 reward ads tapped 03:40-07:34, 181 never started (no fill). User-approved pause after 8 in a row. */
class RewardNoFillPauseTest {
    private fun run(outcomes:List<Pair<String,Long>>):Int=outcomes.fold(0){s,(o,d)->RewardClaimPolicy.noFillStreak(s,o,d)}

    @Test fun eightNoFillsInARowPause(){
        val seven=List(7){"AD_NO_FILL" to 5L}
        assertFalse(RewardClaimPolicy.pauseForNoFill(run(seven)))
        assertTrue(RewardClaimPolicy.pauseForNoFill(run(seven+("AD_NO_FILL" to 5L))))
    }
    @Test fun anyAdThatPlayedOrAClaimStartsTheCountAgain(){
        for(o in listOf("CLAIMED_AD","CLAIMED_PLAIN","ALREADY","AD_FAILED"))
            assertEquals(o,0,run(List(7){"AD_NO_FILL" to 5L}+(o to 5L)))
    }
    @Test fun aCardExpiringTodayNeverCountsSoItsPlainFallbackIsReached(){
        assertEquals(3,run(List(3){"AD_NO_FILL" to 5L}+List(4){"AD_NO_FILL" to 0L}))
    }
    @Test fun otherOutcomesNeitherCountNorReset(){
        assertEquals(2,run(listOf("AD_NO_FILL" to 5L,"detail_not_opened" to 5L,"AD_NO_FILL" to 5L)))
    }
    @Test fun nov27SequenceWouldHavePausedAt0440(){
        // Real order after the 04:40 claim: 17 no-fills before the reward stage was stopped at 04:58.
        assertTrue(RewardClaimPolicy.pauseForNoFill(run(List(17){"AD_NO_FILL" to 8L})))
    }
    @Test fun pausedRewardStepGetsThreeLaterRetriesOfItsOwn(){
        val r=RewardClaimPolicy.NO_FILL_REASON
        assertEquals(45,RoutineRetryPolicy.rewardNoFillDelay("PARTIALLY_COMPLETED","PARTIAL",r,0,4*60+40,false,true,false))
        assertEquals(60,RoutineRetryPolicy.rewardNoFillDelay("PARTIALLY_COMPLETED","PARTIAL",r,1,5*60+30,false,true,false))
        assertEquals(90,RoutineRetryPolicy.rewardNoFillDelay("PARTIALLY_COMPLETED","PARTIAL",r,2,6*60+40,false,true,false))
        assertNull(RoutineRetryPolicy.rewardNoFillDelay("PARTIALLY_COMPLETED","PARTIAL",r,3,8*60,false,true,false))
    }
    @Test fun onlyThatPauseAndNothingAPersonMustHandle(){
        val r=RewardClaimPolicy.NO_FILL_REASON;val t=4*60
        assertNull(RoutineRetryPolicy.rewardNoFillDelay("PARTIALLY_COMPLETED","PARTIAL","time_budget",0,t,false,true,false))
        assertNull(RoutineRetryPolicy.rewardNoFillDelay("PARTIALLY_COMPLETED","DONE",r,0,t,false,true,false))
        assertNull(RoutineRetryPolicy.rewardNoFillDelay("PARTIALLY_COMPLETED",null,null,0,t,false,true,false))
        assertNull(RoutineRetryPolicy.rewardNoFillDelay("DONE","PARTIAL",r,0,t,false,true,false))
        assertNull(RoutineRetryPolicy.rewardNoFillDelay("PARTIALLY_COMPLETED","PARTIAL",r,0,t,true,true,false))
        assertNull(RoutineRetryPolicy.rewardNoFillDelay("PARTIALLY_COMPLETED","PARTIAL",r,0,t,false,false,false))
        assertNull(RoutineRetryPolicy.rewardNoFillDelay("PARTIALLY_COMPLETED","PARTIAL",r,0,t,false,true,true))
        assertNull(RoutineRetryPolicy.rewardNoFillDelay("PARTIALLY_COMPLETED","PARTIAL",r,0,22*60+30,false,true,false))
    }
    @Test fun aFinishedDayKeepsOnlyItsNoFillRetry(){
        assertTrue(RoutineRetryPolicy.dayFinished("PARTIALLY_COMPLETED",false))
        assertTrue(RoutineRetryPolicy.dayFinished("DONE",false))
        assertFalse(RoutineRetryPolicy.dayFinished("PARTIALLY_COMPLETED",true))
        assertFalse(RoutineRetryPolicy.dayFinished("BLOCKED",false))
        assertFalse(RoutineRetryPolicy.dayFinished(null,false))
    }
    @Test fun theSummarySaysTheRewardsComeLater(){
        assertEquals("报酬广告暂时没有可播放的，稍后自动再领",TaskFailureText.short(RewardClaimPolicy.NO_FILL_REASON))
    }
}
