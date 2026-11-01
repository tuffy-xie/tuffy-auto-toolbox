package com.anapocket.auto
import org.junit.Assert.*
import org.junit.Test
class ChallengeRecoveryPolicyTest {
    @Test fun officialBoostLevelProgressIsNotANetworkLoadingSpinner(){assertFalse(ChallengeRecoveryPolicy.blockingOfficialLoading("us.ana_pocket.ANAPocket:id/boost_bar","android.widget.ProgressBar",true));assertFalse(ChallengeRecoveryPolicy.blockingOfficialLoading(null,"android.widget.ProgressBar",false));assertTrue(ChallengeRecoveryPolicy.blockingOfficialLoading(null,"android.widget.ProgressBar",true))}
    @Test fun loadingAndEarlyBaselineCannotBeMisreportedAsNoGain(){assertFalse(ChallengeRecoveryPolicy.officialNoGainReady(1500,false));assertFalse(ChallengeRecoveryPolicy.officialNoGainReady(20000,true));assertTrue(ChallengeRecoveryPolicy.officialNoGainReady(15000,false))}
    private fun prompt(initial:Boolean=false,confirmed:Boolean=false,index:Int=4,attempts:Int=4,pending:Int=1,sameDate:Boolean=true,
        boost:Int=2,before:Int=150,expected:Int=200,steps:List<Int> =listOf(50,50,50,100,100),requests:Int=0,official:Boolean=true,reason:String="ad_timeout_95s")=
        ChallengeRecoveryPolicy.officialPromptAllowed("UNCONFIRMED","UNCONFIRMED",reason,confirmed,initial,index,attempts,pending,sameDate,boost,before,expected,steps,requests,official)
    @Test fun nativePromptResumesActualInterruptedThirdBoostWithoutAReplacementReservation(){assertTrue(prompt())}
    @Test fun promptNeedsExactOfficialContextSingleBoundAttemptAndDate(){assertFalse(prompt(official=false));assertFalse(prompt(pending=2));assertFalse(prompt(sameDate=false));assertFalse(prompt(index=3));assertFalse(prompt(initial=true));assertFalse(prompt(confirmed=true))}
    @Test fun promptNeverRelaxesSavedRewardBaselineOrUnknownReasons(){assertFalse(prompt(expected=201));assertFalse(prompt(steps=listOf(50,50)));assertFalse(prompt(before=0));assertFalse(prompt(reason="counter_jump_ambiguous"));assertFalse(prompt(requests=2))}
    @org.junit.Test fun explicitOperatorReviewCanInspectClosedButUnconfirmedAdWithoutBroadeningInitialOrArbitraryFailures() {
        org.junit.Assert.assertTrue(ChallengeRecoveryPolicy.reviewableFailure("completion_not_confirmed",false))
        org.junit.Assert.assertTrue(ChallengeRecoveryPolicy.reviewableFailure("ad_timeout_95s",true))
        org.junit.Assert.assertFalse(ChallengeRecoveryPolicy.reviewableFailure("completion_not_confirmed",true))
        org.junit.Assert.assertFalse(ChallengeRecoveryPolicy.reviewableFailure("arbitrary_error",false))
    }
    @Test fun timeoutResumesOnlyTheSameObservedAttempt() {
        assertTrue(ChallengeRecoveryPolicy.canResume("UNCONFIRMED", "UNCONFIRMED", "ad_timeout_95s", false, 4, 4, true))
        assertFalse(ChallengeRecoveryPolicy.canResume("UNCONFIRMED", "UNCONFIRMED", "ad_timeout_95s", false, 3, 4, true))
    }
    @Test fun officialPanelCannotBeAdoptedAsAnExistingAd() {
        assertFalse(ChallengeRecoveryPolicy.canResume("UNCONFIRMED", "UNCONFIRMED", "ad_timeout_95s", false, 4, 4, false))
    }
    @Test fun killedProcessKeepsDispatchedAdEligibleWithoutNewReservation() {
        assertTrue(ChallengeRecoveryPolicy.canResume("AD_OPENING", "AD_PLAYING", "", false, 1, 1, true))
    }
    @Test fun confirmedFailedAndAmbiguousReceiptsCannotBeReplayed() {
        assertFalse(ChallengeRecoveryPolicy.canResume("UNCONFIRMED", "REWARD_CONFIRMED", "", true, 4, 4, true))
        assertFalse(ChallengeRecoveryPolicy.canResume("UNCONFIRMED", "UNCONFIRMED", "counter_jump_ambiguous", false, 4, 4, true))
        assertFalse(ChallengeRecoveryPolicy.canResume("UNCONFIRMED", "FAILED", "official_error_dialog", false, 4, 4, true))
    }
}
