package com.anapocket.auto

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class H14DurabilityAndRecoveryTest {
    private class Killed:RuntimeException()
    @Test fun reservationSurvivesWhenArmingCannotCommit()=runBlocking {
        var phase="RESERVED";var clicks=0
        try{AdDispatchProtocol.dispatch({throw Killed()},{clicks++;true});fail()}catch(_:Killed){}
        assertEquals("RESERVED",phase);assertEquals(0,clicks)
    }
    @Test fun crashAfterIntentBeforeGestureCannotBeCalledUnsubmitted()=runBlocking {
        var phase="RESERVED";var clicks=0
        try{AdDispatchProtocol.dispatch({phase=it},{throw Killed()});fail()}catch(_:Killed){}
        assertEquals("ARMED",phase);assertEquals(0,clicks)
    }
    @Test fun dispatchedWithoutReceiptRemainsUnknownAndDoesNotClickAgain()=runBlocking {
        val writes=mutableListOf<String>();var clicks=0
        assertTrue(AdDispatchProtocol.dispatch({writes.add(it)},{clicks++;true}))
        assertEquals(listOf("ARMED","DISPATCHED"),writes);assertEquals(1,clicks)
        val p=Port(receipt=false);val r=AdInteraction(p).awaitReceipt(0)
        assertEquals(AdState.UNCONFIRMED,r.state);assertFalse(r.reward);assertFalse(p.committed)
    }
    private class Port(val receipt:Boolean=true,val failCommit:Boolean=false,val crashAfterCommit:Boolean=false):AdInteractionPort {
        var t=0L;var committed=false;val events=mutableListOf<String>()
        override fun now()=t
        override fun stopped()=false
        override suspend fun pause(ms:Long){t+=ms}
        override suspend fun frame()=if(t<800||!receipt)AdFrame(true,false,adSurface=true) else AdFrame(true,true,1,true)
        override suspend fun close()=false
        override suspend fun dismissReceipt(){events.add("dismiss");assertTrue(committed)}
        override suspend fun receiptObserved(frame:AdFrame,before:Int){events.add("commit");if(failCommit)throw Killed();committed=true}
        override suspend fun completionObserved(frame:AdFrame,before:Int,reward:Boolean){assertTrue(committed)}
        override suspend fun state(state:AdState){events.add(state.name);if(state==AdState.REWARD_CONFIRMED){assertTrue(committed);if(crashAfterCommit)throw Killed()}}
    }
    @Test fun receiptWriteFailureStopsBeforeUiDismissalOrNextAction()=runBlocking {
        val p=Port(failCommit=true)
        try{AdInteraction(p).awaitReceipt(0);fail()}catch(_:Killed){}
        assertFalse(p.committed);assertFalse("dismiss" in p.events);assertFalse("AD_FINISHED" in p.events)
    }
    @Test fun crashAfterDurableReceiptKeepsConfirmation()=runBlocking {
        val p=Port(crashAfterCommit=true)
        try{AdInteraction(p).awaitReceipt(0);fail()}catch(_:Killed){}
        assertTrue(p.committed);assertEquals(listOf("AD_PLAYING","commit","REWARD_CONFIRMED"),p.events)
    }
    @Test fun receiptCommitPrecedesAllResultSideEffects()=runBlocking {
        val p=Port();val r=AdInteraction(p).awaitReceipt(0)
        assertTrue(r.reward&&r.completed);assertTrue(p.events.indexOf("commit")<p.events.indexOf("dismiss"))
        assertTrue(p.events.indexOf("commit")<p.events.indexOf("AD_FINISHED"))
    }
    @Test fun dailyCounterJumpCannotCommitCompletion()=runBlocking {
        var writes=0
        val p=object:AdInteractionPort {
            var t=0L
            override fun now()=t
            override fun stopped()=false
            override suspend fun pause(ms:Long){t+=ms}
            override suspend fun frame()=if(t==0L)AdFrame(true,false,adSurface=true) else AdFrame(true,true,3,false)
            override suspend fun close()=false
            override suspend fun dismissReceipt(){}
            override suspend fun state(state:AdState){}
            override suspend fun completionObserved(frame:AdFrame,before:Int,reward:Boolean){writes++}
        }
        val r=AdInteraction(p).awaitReceipt(0);assertEquals("counter_jump_ambiguous",r.reason);assertEquals(0,writes)
    }
    @Test fun repeatedMemoryExitsBackOffAndReachFiniteCooldown(){
        val first=100_000L
        val a=PersistentRecoveryPolicy.plan(first,first,0,0,false,false)
        val b=PersistentRecoveryPolicy.plan(first,first,1,1,false,false)
        val c=PersistentRecoveryPolicy.plan(first,first,2,2,false,false)
        val d=PersistentRecoveryPolicy.plan(first,first,3,3,false,false)
        assertEquals(first+10_000,a.due);assertEquals(first+30_000,b.due);assertEquals(first+120_000,c.due)
        assertTrue(d.cooldown);assertEquals(first+900_000,d.due)
        assertFalse(PersistentRecoveryPolicy.plan(first,first,3,6,true,false).allowed)
    }
    @Test fun reliableProgressStartsNewBurstButDoesNotEraseDailyBudget(){
        val p=PersistentRecoveryPolicy.plan(100_000,100_000,3,5,true,true)
        assertTrue(p.allowed);assertEquals(0,p.burst);assertEquals(5,p.total)
        assertFalse(PersistentRecoveryPolicy.plan(100_000,100_000,0,12,false,true).allowed)
    }
    @Test fun callbackLossUsesPersistedWindowAndCannotExtendIt(){
        assertTrue(PersistentRecoveryPolicy.plan(200_000,100_000,1,1,false,false).allowed)
        assertFalse(PersistentRecoveryPolicy.plan(100_000+PersistentRecoveryPolicy.WINDOW+1,100_000,0,0,false,false).allowed)
        assertFalse(PersistentRecoveryPolicy.plan(99_999,100_000,0,0,false,false).allowed)
    }
    private fun allowed(date:String="2026-11-02",enabled:Boolean=true,service:Boolean=true,risk:Boolean=false,stopped:Boolean=false,
        boot:Boolean=true,version:Boolean=true,lowMemory:Boolean=true)=PersistentRecoveryPolicy.mayDispatch("2026-11-02",date,enabled,service,risk,stopped,boot,version,200_000,100_000,lowMemory)
    @Test fun rebootUpgradePermissionUserStopAndRiskNeverSilentlyResume(){
        assertTrue(allowed());assertFalse(allowed(boot=false));assertFalse(allowed(version=false));assertFalse(allowed(service=false))
        assertFalse(allowed(stopped=true));assertFalse(allowed(risk=true));assertFalse(allowed(enabled=false));assertFalse(allowed(date="2026-11-01"))
    }
    @Test fun signalKillIsNotAutomaticallyLowMemory(){assertFalse(allowed(lowMemory=false))}
    private val unknown=SubmittedUnknown("2026-11-02","MOV_ANY|30km","CH-original",1234,1,1,false,true,"DISPATCHED",1)
    @Test fun submittedUnknownNeedsUniqueCurrentNativeProofAndNoDailySubmission(){
        fun valid(unique:Boolean=true,clean:Boolean=true,stable:Long=15_000,daily:Boolean=false)=SubmittedUnknownIsolationPolicy.mayIsolate(unknown,"2026-11-02",unique,clean,stable,daily)
        assertTrue(valid());assertFalse(valid(unique=false));assertFalse(valid(clean=false));assertFalse(valid(stable=14_999));assertFalse(valid(daily=true))
    }
    @Test fun sdkOrAnonymousReceiptCannotBeIsolatedToAdvance(){
        assertFalse(SubmittedUnknownIsolationPolicy.mayIsolate(unknown,"2026-11-02",false,false,90_000,false))
        assertFalse(SubmittedUnknownIsolationPolicy.mayIsolate(unknown.copy(phase="ARMED"),"2026-11-02",true,true,90_000,false))
        assertFalse(SubmittedUnknownIsolationPolicy.mayIsolate(unknown.copy(phase="RESERVED",departed=false),"2026-11-02",true,true,90_000,false))
    }
    @Test fun severalUnknownsWithinOneTaskCannotBeCollapsed(){
        assertFalse(SubmittedUnknownIsolationPolicy.mayIsolate(unknown.copy(pendingPerTask=2),"2026-11-02",true,true,90_000,false))
        val other=unknown.copy(key="MOV_ANY|40km",run="CH-other",startedAt=2345)
        assertTrue(SubmittedUnknownIsolationPolicy.mayIsolate(other,"2026-11-02",true,true,90_000,false))
        assertFalse(SubmittedUnknownIsolationPolicy.bound(other,"2026-11-02",unknown.key,unknown.run,unknown.startedAt,1,1,SubmittedUnknownIsolationPolicy.KIND))
    }
    @Test fun isolationRemainsUnknownAndNeverGrantsAReceipt(){
        val before=unknown.copy()
        assertTrue(SubmittedUnknownIsolationPolicy.bound(unknown,"2026-11-02",unknown.key,unknown.run,unknown.startedAt,1,1,SubmittedUnknownIsolationPolicy.KIND))
        assertEquals(before,unknown);assertFalse(unknown.confirmed)
    }
    @Test fun defaultAndExpiredScreenshotModeNeverAllocateEvidence(){
        assertFalse(ScreenshotBudget.allowed(100,0,0,0));assertFalse(ScreenshotBudget.allowed(100,100,0,0))
        assertFalse(ScreenshotBudget.allowed(100,100+ScreenshotBudget.DURATION+1,0,0))
    }
    @Test fun explicitDiagnosticsHaveHardImageAndByteBudgets(){
        assertTrue(ScreenshotBudget.allowed(100,200,11,ScreenshotBudget.MAX_BYTES-1))
        assertFalse(ScreenshotBudget.allowed(100,200,12,0));assertFalse(ScreenshotBudget.allowed(100,200,0,ScreenshotBudget.MAX_BYTES))
    }

    @Test fun freshReceiptIsCommittedBeforeSegmentTimeout()=runBlocking {
        var now=0L;var committed=false
        val p=object:AdInteractionPort{
            override fun now()=now
            override fun stopped()=false
            override suspend fun frame()=if(now==0L)AdFrame(true,false,adSurface=true) else AdFrame(true,true,1,receipt=true)
            override suspend fun pause(ms:Long){now=95_000}
            override suspend fun close()=false
            override suspend fun receiptObserved(frame:AdFrame,before:Int){committed=true}
            override suspend fun dismissReceipt(){assertTrue(committed)}
            override suspend fun state(state:AdState){}
        }
        val result=AdInteraction(p).awaitReceipt(0)
        assertTrue(committed);assertTrue(result.completed);assertEquals(AdState.REWARD_CONFIRMED,result.state)
    }
    @Test fun onlyExplicitUnstartedIntentCanRetryNonMemoryStartDenial(){
        assertTrue(PersistentRecoveryPolicy.mayDispatch("d","d",true,true,false,false,true,true,10,0,false,true))
        assertFalse(PersistentRecoveryPolicy.mayDispatch("d","d",true,true,false,false,true,true,10,0,false,false))
        assertFalse(PersistentRecoveryPolicy.mayDispatch("d","d",true,true,false,true,true,true,10,0,false,true))
    }

    @Test fun laterSignalledExitCannotBorrowAnEarlierLowMemoryCause(){
        assertTrue(PersistentRecoveryPolicy.exitStillMatches(100,100,true))
        assertFalse(PersistentRecoveryPolicy.exitStillMatches(100,200,false))
        assertFalse(PersistentRecoveryPolicy.exitStillMatches(100,200,true))
        assertFalse(PersistentRecoveryPolicy.exitStillMatches(0,0,true))
    }

    @Test fun nativeWatchdogCanClassifyARecentExitWithoutOldProcessCallbacks(){
        assertTrue(PersistentRecoveryPolicy.mayRegister("d","d","RUNNING","RT-original","CHALLENGES",100,300_000,200,true,true,false,false))
        assertFalse(PersistentRecoveryPolicy.mayRegister("d","d","RUNNING","RT-original","CHALLENGES",100,300_000,200,false,true,false,false))
        assertFalse(PersistentRecoveryPolicy.mayRegister("d","d","STOPPED","RT-original","CHALLENGES",100,300_000,200,true,true,false,false))
        assertFalse(PersistentRecoveryPolicy.mayRegister("d","d","RUNNING","RT-original","CHALLENGES",100,PersistentRecoveryPolicy.WINDOW+101,200,true,true,false,false))
    }

    @Test fun registeringANewLowMemoryEventCannotEraseBootOrUpgradeProtection(){
        assertFalse(PersistentRecoveryPolicy.mayRegister("d","d","RUNNING","RT-original","CHALLENGES",100,300,200,true,true,false,false,false,true))
        assertFalse(PersistentRecoveryPolicy.mayRegister("d","d","RUNNING","RT-original","CHALLENGES",100,300,200,true,true,false,false,true,false))
    }

    @Test fun completedStepBetweenStagesStillBelongsToTheActiveDurableController(){
        assertEquals("RUNNING",PersistentRecoveryPolicy.activeSnapshotState("REWARD_CONFIRMED",true))
        assertEquals("RUNNING",PersistentRecoveryPolicy.activeSnapshotState("DONE",true))
        assertEquals("ADS",PersistentRecoveryPolicy.activeSnapshotStage("DONE",true))
        assertEquals("DONE",PersistentRecoveryPolicy.activeSnapshotState("DONE",false))
        assertEquals("BLOCKED",PersistentRecoveryPolicy.activeSnapshotState("BLOCKED",true))
        assertEquals("STOPPED",PersistentRecoveryPolicy.activeSnapshotState("STOPPED",true))
    }
    @Test fun durableStopOrTerminalControllerCannotBeReopenedByAStaleRunningSnapshot(){
        assertTrue(PersistentRecoveryPolicy.canRegisterFromPhase(null))
        assertTrue(PersistentRecoveryPolicy.canRegisterFromPhase("CLAIMED"))
        assertTrue(PersistentRecoveryPolicy.canRegisterFromPhase("PENDING"))
        for(phase in listOf("CANCELLED","DONE","BLOCKED"))assertFalse(PersistentRecoveryPolicy.canRegisterFromPhase(phase))
    }

    @Test fun dailyDepartureSurvivesProcessReconcileWithoutAnOptionalTextEvidenceFile(){
        val legacy=AdAttempt("AD-original","2026-11-02",1,1234,officialBefore=3)
        assertFalse(legacy.adDepartureObserved) // JSON serialization is an Android device probe, not a mocked-JVM assertion.
        val played=legacy.copy(state=AdState.AD_PLAYING,dispatchPhase="DISPATCHED",adDepartureObserved=true)
        val restarted=AdDayLogic.reconcileInterrupted(listOf(played),2000).single()
        assertEquals(legacy.runId,restarted.runId);assertEquals(legacy.startedAt,restarted.startedAt)
        assertTrue(restarted.adDepartureObserved);assertEquals("DISPATCHED",restarted.dispatchPhase)
        assertFalse(restarted.rewardConfirmed);assertFalse(restarted.completed)
    }

    @Test fun restartingAChallengeOnlyControllerCannotSilentlyEnableTheDailyAdStage(){
        val challenges=RoutineRunModes.choose("manual-challenge-rescan")
        assertTrue(challenges.challengesOnly);assertTrue(challenges.manual)
        assertEquals(challenges,RoutineRunModes.choose("persistent_low_memory_recovery",challenges))
        val scheduled=RoutineRunModes.choose("alarm")
        assertFalse(scheduled.manual);assertFalse(scheduled.challengesOnly)
        assertEquals(scheduled,RoutineRunModes.choose("persistent_unstarted_retry",scheduled))
        val manualFull=RoutineRunModes.choose("manual")
        assertTrue(manualFull.manual);assertFalse(manualFull.challengesOnly)
        assertEquals(manualFull,RoutineRunModes.choose("persistent_low_memory_recovery",manualFull))
    }
    @Test fun pendingChallengeReviewDoesNotRequireOrPermitClearingPendingSubmissions(){
        assertFalse(ChallengeScanPolicy.mayReset(false,true,false))
        assertTrue(ChallengeScanEntryPolicy.preservingReview(true,false,false,false,false))
        assertFalse(ChallengeScanEntryPolicy.preservingReview(false,false,false,false,false))
        assertFalse(ChallengeScanEntryPolicy.preservingReview(true,true,false,false,false))
        assertFalse(ChallengeScanEntryPolicy.preservingReview(true,false,true,false,false))
        assertFalse(ChallengeScanEntryPolicy.preservingReview(true,false,false,true,false))
        assertFalse(ChallengeScanEntryPolicy.preservingReview(true,false,false,false,true))
    }

    @Test fun originalTravelRecoveryPromptIsRoutedBeforeGenericStartupInEitherInvocationScope(){
        assertTrue(RoutineRecoveryNavigation.reviewChallengeFirst(true,true))
        assertTrue(RoutineRecoveryNavigation.reviewChallengeFirst(false,true))
        assertFalse(RoutineRecoveryNavigation.reviewChallengeFirst(true,false))
        assertFalse(RoutineRecoveryNavigation.reviewChallengeFirst(false,false))
        assertTrue(RoutineRunModes.choose("manual-challenge-rescan").challengesOnly)
    }

}
