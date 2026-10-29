package com.anapocket.auto
import org.junit.Assert.*
import org.junit.Test

class JoinedChallengeResumePolicyTest {
    @Test fun onlyConfirmedOrOfficialNoGainFailureIsResolved() { assertTrue(JoinedChallengeResumePolicy.resolved(true,false,"REWARD_CONFIRMED"));assertTrue(JoinedChallengeResumePolicy.resolved(false,true,"FAILED"));assertFalse(JoinedChallengeResumePolicy.resolved(false,false,"FAILED"));assertFalse(JoinedChallengeResumePolicy.resolved(false,true,"UNCONFIRMED")) }
    private val steps=listOf(50,50,50,100,100)
    private val labels=listOf("3回目のブーストをしますか？","現在のチャレンジ達成後の報酬：200pt")+steps.map{"+$it"}
    @Test fun confirmedPriorAdResumesOnlyTheNextUnstartedBoost() { assertTrue(JoinedChallengeResumePolicy.matches(true,3,3,2,200,steps,labels)) }
    @Test fun unknownPriorAdCannotBeResubmitted() { assertFalse(JoinedChallengeResumePolicy.matches(false,3,3,2,200,steps,labels)) }
    @Test fun changedOrdinalRewardOrTableCannotBeAdopted() { assertFalse(JoinedChallengeResumePolicy.matches(true,3,3,1,200,steps,labels));assertFalse(JoinedChallengeResumePolicy.matches(true,3,3,2,250,steps,labels));assertFalse(JoinedChallengeResumePolicy.matches(true,3,3,2,200,listOf(50,50,100,100,100),labels)) }
    @Test fun olderAttemptOrAlreadyMaxTaskCannotResume() { assertFalse(JoinedChallengeResumePolicy.matches(true,2,3,2,200,steps,labels));assertFalse(JoinedChallengeResumePolicy.matches(true,3,3,5,200,steps,labels)) }
}
