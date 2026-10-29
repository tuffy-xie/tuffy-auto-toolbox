package com.anapocket.auto
import org.junit.Assert.*
import org.junit.Test
class ChallengeRecoveryPolicyTest {
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
