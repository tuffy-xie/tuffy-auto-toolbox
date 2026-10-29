package com.anapocket.auto
import org.junit.Assert.*
import org.junit.Test
class OpaqueRecoveryPolicyTest {
 @Test fun preRemovalSystemTransitionCanResumeButNeverRepeatTaskRemoval() { assertFalse(OpaqueRecoveryPolicy.recoveryAlreadyUsed("",1));assertTrue(OpaqueRecoveryPolicy.recoveryAlreadyUsed("",2));assertTrue(OpaqueRecoveryPolicy.recoveryAlreadyUsed("REMOVE_ONLY_OBSERVED_ANA_TASK",1)) }
 @Test fun officialDisplayFailureIsNotARewardOrAnUnknownTimeout() { val l=listOf("広告表示に失敗しました。", "恐れ入りますが、アプリの再起動をお試しください。");assertTrue(AdSignals.loadFailure(l));assertTrue(AdSignals.error(l));assertFalse(AdSignals.receipt(l)) }
 @Test fun boundOfficialLoadFailureMayRebuildButNeverUnboundInitialOrRepeated() { val s=listOf(50,50,50,100,100);assertTrue(OpaqueRecoveryPolicy.mayRecoverLoadFailure(false,true,true,false,0,0,s));assertFalse(OpaqueRecoveryPolicy.mayRecoverLoadFailure(true,true,true,false,0,0,s));assertFalse(OpaqueRecoveryPolicy.mayRecoverLoadFailure(false,true,false,false,0,0,s));assertFalse(OpaqueRecoveryPolicy.mayRecoverLoadFailure(false,true,true,true,0,0,s));assertFalse(OpaqueRecoveryPolicy.mayRecoverLoadFailure(false,true,true,false,1,0,s)) }
 @Test fun genericNetworkOrAdvertisingTextDoesNotAuthorizeSessionRemoval() { assertFalse(AdSignals.loadFailure(listOf("通信エラー")));assertFalse(AdSignals.loadFailure(listOf("広告表示に関するご案内"))) }
 private val steps = listOf(50,50,50,100,100)
 private fun labels(next: Int?, points: Int) = listOfNotNull(next?.let { "${it}回目のブーストをしますか？" }, "現在のチャレンジ達成後の報酬：${points}pt") + steps.map { "+${it}" }
 @Test fun triangleAndGooglePlayHintCannotAuthorizeTap() { assertFalse(CanvasControlPolicy.canTap("SKIP")); assertFalse(CanvasControlPolicy.canTap("Google Play")); assertTrue(CanvasControlPolicy.canTap("CLOSE")) }
 @Test fun onlyUnambiguousUnlockedAnaTaskIsEligible() { assertTrue(OpaqueRecoveryPolicy.unlockedAnaCard("ANA Pocket,未加锁")); for (x in listOf("ANA Pocket,已加锁", "ANA 自动助手,未加锁", "Chrome,未加锁", "ANA Pocket")) assertFalse(OpaqueRecoveryPolicy.unlockedAnaCard(x)) }
 @Test fun boundDepartedBoostCanRecoverOnce() { assertTrue(OpaqueRecoveryPolicy.mayRecover(false,true,true,true,true,false,0,3,steps)) }
 @Test fun initialAndUndepartedCannotRecover() { assertFalse(OpaqueRecoveryPolicy.mayRecover(true,true,true,true,true,false,0,3,steps));assertFalse(OpaqueRecoveryPolicy.mayRecover(false,true,false,true,true,false,0,3,steps)) }
 @Test fun timeoutAndOpaqueEvidenceRequired() { assertFalse(OpaqueRecoveryPolicy.mayRecover(false,false,true,true,true,false,0,3,steps));assertFalse(OpaqueRecoveryPolicy.mayRecover(false,true,true,true,false,false,0,3,steps)) }
 @Test fun unrelatedRunAndRepeatAreRejected() { assertFalse(OpaqueRecoveryPolicy.mayRecover(false,true,true,false,true,false,0,3,steps));assertFalse(OpaqueRecoveryPolicy.mayRecover(false,true,true,true,true,true,0,3,steps));assertFalse(OpaqueRecoveryPolicy.mayRecover(false,true,true,true,true,false,1,3,steps)) }
 @Test fun noGainNeedsOriginalOrdinalAndReward() { assertEquals("NO_GAIN",OpaqueRecoveryPolicy.outcome(3,250,350,steps,labels(4,250))) }
 @Test fun advanceNeedsExactOrdinalAndReward() { assertEquals("ADVANCED",OpaqueRecoveryPolicy.outcome(3,250,350,steps,labels(5,350))) }
 @Test fun finalAdvanceNeedsExactRewardAndNoNext() { assertEquals("ADVANCED",OpaqueRecoveryPolicy.outcome(4,350,450,steps,labels(null,450))) }
 @Test fun unrelatedOrPartialPanelStaysUnknown() { assertNull(OpaqueRecoveryPolicy.outcome(3,250,350,steps,labels(4,350)));assertNull(OpaqueRecoveryPolicy.outcome(3,250,350,steps,labels(3,250)));assertNull(OpaqueRecoveryPolicy.outcome(3,250,350,steps,labels(4,250).dropLast(1))) }
 @Test fun changedIncrementTableStaysUnknown() { assertNull(OpaqueRecoveryPolicy.outcome(3,250,350,listOf(50,50,50,50,100),labels(4,250))) }
 @Test fun invalidBaselineAndCompletedTaskCannotRecover() { assertNull(OpaqueRecoveryPolicy.outcome(3,250,300,steps,labels(4,250)));assertFalse(OpaqueRecoveryPolicy.mayRecover(false,true,true,true,true,false,0,5,steps)) }
}
