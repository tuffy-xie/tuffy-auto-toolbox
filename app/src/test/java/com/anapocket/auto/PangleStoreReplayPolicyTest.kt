package com.anapocket.auto
import org.junit.Assert.*
import org.junit.Test

class PangleStoreReplayPolicyTest {
    private fun ids(name: String) = javaClass.classLoader!!.getResourceAsStream(name)!!.bufferedReader().readLines()
    private val first get() = ids("pangle_store_replay_first.txt")
    private val repeated get() = ids("pangle_store_replay_repeated.txt")
    private val steps = listOf(50,50,50,100,100)
    private val labels = listOf("1回目のブーストをしますか？","現在のチャレンジ達成後の報酬：50pt")+steps.map{"+$it"}
    @Test fun actualRepeatedDocumentMatchesDespiteVolatileClContainer() { assertNotNull(PangleStoreReplayPolicy.signature(first)); assertEquals(PangleStoreReplayPolicy.signature(first),PangleStoreReplayPolicy.signature(repeated)) }
    @Test fun genericSdkRootIsInsufficient() { assertNull(PangleStoreReplayPolicy.signature(listOf("__root_view__","android:id/content","cl-2"))) }
    @Test fun firstStoreVisitKeepsExistingReturnBehavior() { val p=PangleStoreReplayPolicy();p.observeDocument(first);p.observeSheet(true);assertFalse(p.shouldReturn(true)) }
    @Test fun sameDocumentSecondStoreVisitAllowsOneLauncherReturn() { val p=PangleStoreReplayPolicy();p.observeDocument(first);p.observeSheet(true);p.observeSheet(false);p.observeDocument(repeated);p.observeSheet(true);assertTrue(p.shouldReturn(true));p.markReturned();assertFalse(p.shouldReturn(true)) }
    @Test fun differentDocumentDoesNotTriggerReplayRecovery() { val p=PangleStoreReplayPolicy();p.observeDocument(first);p.observeSheet(true);p.observeSheet(false);p.observeDocument(first.map{"different_"+it});p.observeSheet(true);assertFalse(p.shouldReturn(true)) }
    @Test fun launcherReturnRequiresObservedPlayHome() { val p=PangleStoreReplayPolicy();p.observeDocument(first);p.observeSheet(true);p.observeSheet(false);p.observeDocument(repeated);p.observeSheet(true);assertFalse(p.shouldReturn(false)) }
    @Test fun exactUnchangedOriginalPanelIsOnlyAReviewCandidate() { assertTrue(PangleStoreReplayPolicy.unchangedBaselineCandidate(true,true,false,true,0,0,50,100,steps,labels));assertFalse(AdSignals.receipt(labels)) }
    @Test fun unboundInitialRepeatedOrChangedBaselineCandidatesAreRejected() { assertFalse(PangleStoreReplayPolicy.unchangedBaselineCandidate(false,true,false,true,0,0,50,100,steps,labels));assertFalse(PangleStoreReplayPolicy.unchangedBaselineCandidate(true,false,false,true,0,0,50,100,steps,labels));assertFalse(PangleStoreReplayPolicy.unchangedBaselineCandidate(true,true,true,true,0,0,50,100,steps,labels));assertFalse(PangleStoreReplayPolicy.unchangedBaselineCandidate(true,true,false,true,1,0,50,100,steps,labels));assertFalse(PangleStoreReplayPolicy.unchangedBaselineCandidate(true,true,false,true,0,0,50,100,steps,labels.map{if(it=="現在のチャレンジ達成後の報酬：50pt")"現在のチャレンジ達成後の報酬：75pt" else it})) }
    @Test fun unchangedPanelNeverAuthorizesAutomaticResubmission() { assertFalse(PangleStoreReplayPolicy.mayRetryFromUnchangedPanel());assertFalse(PangleStoreReplayPolicy.confirmedLateAdvance(true,true,false,true,0,50,100,steps,labels)) }
    @Test fun realLateNextTwoAndOneHundredConfirmsOriginalAttempt() { val l=listOf("2回目のブーストをしますか？","現在のチャレンジ達成後の報酬：100pt")+steps.map{"+$it"};assertTrue(PangleStoreReplayPolicy.confirmedLateAdvance(true,true,false,true,0,50,100,steps,l)) }
    @Test fun lateReceiptMustBeBoundAndAdvanceExactlyOne() { val l=listOf("3回目のブーストをしますか？","現在のチャレンジ達成後の報酬：150pt")+steps.map{"+$it"};assertFalse(PangleStoreReplayPolicy.confirmedLateAdvance(true,true,false,true,0,50,100,steps,l));assertFalse(PangleStoreReplayPolicy.confirmedLateAdvance(true,false,false,true,0,50,100,steps,labels)) }
    @Test fun finalLateAdvanceNeedsExactFinalReward() { val l=listOf("現在のチャレンジ達成後の報酬：400pt")+steps.map{"+$it"};assertTrue(PangleStoreReplayPolicy.confirmedLateAdvance(true,true,false,true,4,300,400,steps,l));assertFalse(PangleStoreReplayPolicy.confirmedLateAdvance(true,true,false,true,4,300,450,steps,l)) }
}
