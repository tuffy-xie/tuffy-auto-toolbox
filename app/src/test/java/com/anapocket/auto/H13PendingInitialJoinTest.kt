package com.anapocket.auto

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class H13PendingInitialJoinTest {
    private val date="2026-11-02"
    private val a=PendingInitialJoin(date,"BIKE|自転車で4km移動しよう！","CH-original",1793547617246,1,1,
        true,false,0,"AD_OPENING","AD_OPENING",false,false,"","")
    private val proof=InitialJoinIsolationProof(date,a.key,a.runId,a.startedAt,1,a.startedAt+20_000,PendingInitialJoinPolicy.ISOLATION_KIND)
    @Test fun legacyReservationMayOnlyBeIsolatedAfterStableNativeUnjoinedObservation(){
        assertTrue(PendingInitialJoinPolicy.mayIsolate(a,date,1,true,15_000))
        assertFalse(PendingInitialJoinPolicy.mayIsolate(a,date,1,true,14_999))
        assertFalse(PendingInitialJoinPolicy.mayIsolate(a,date,1,false,90_000))
        assertFalse(PendingInitialJoinPolicy.mayIsolate(a,date,2,true,90_000))
    }
    @Test fun legacyMissingDispatchMarkerNeverGrantsAnotherClick(){
        assertFalse(PendingInitialJoinPolicy.mayDispatchReserved(a,date,true))
        assertTrue(PendingInitialJoinPolicy.mayDispatchReserved(a.copy(dispatchPhase="RESERVED"),date,true))
    }
    @Test fun persistedArmedOrDispatchedGestureIsNeverTreatedAsUnsubmitted(){
        for(phase in listOf("ARMED","DISPATCHED","future_unknown")){
            val b=a.copy(dispatchPhase=phase)
            assertFalse(PendingInitialJoinPolicy.mayDispatchReserved(b,date,true))
            assertFalse(PendingInitialJoinPolicy.mayIsolate(b,date,1,true,30_000))
        }
    }
    @Test fun newReservationContinuesOnlyOnItsSameNativePageAndBusinessDay(){
        val b=a.copy(dispatchPhase="RESERVED")
        assertFalse(PendingInitialJoinPolicy.mayDispatchReserved(b,date,false))
        assertFalse(PendingInitialJoinPolicy.mayDispatchReserved(b,"2026-11-03",true))
        assertFalse(PendingInitialJoinPolicy.mayIsolate(b,date,1,true,20_000))
    }
    @Test fun submittedOrConfirmedInitialAndAnyBoostRemainBlocked(){
        for(b in listOf(a.copy(departed=true),a.copy(confirmed=true),a.copy(initial=false),a.copy(joined=true),a.copy(boost=1),
            a.copy(adState="AD_PLAYING"),a.copy(adState="UNCONFIRMED"),a.copy(taskState="JOINED"),a.copy(reason="counter_jump_ambiguous"))){
            assertFalse(PendingInitialJoinPolicy.mayIsolate(b,date,1,true,30_000))
            assertFalse(PendingInitialJoinPolicy.isolated(b,proof,date))
        }
    }
    @Test fun isolationRequiresOriginalRunIndexAndStartedAtNotJustCategory(){
        assertTrue(PendingInitialJoinPolicy.isolated(a,proof,date))
        for(p in listOf(proof.copy(runId="CH-other"),proof.copy(startedAt=a.startedAt+1),proof.copy(index=2),
            proof.copy(key="BIKE|自転車で5km移動しよう！"),proof.copy(date="2026-11-01"),proof.copy(kind="unknown"),proof.copy(observedAt=1))){
            assertFalse(PendingInitialJoinPolicy.isolated(a,p,date))
        }
        assertFalse(PendingInitialJoinPolicy.isolated(a,null,date))
        assertFalse(PendingInitialJoinPolicy.isolated(a,proof,"2026-11-03"))
    }
    @Test fun earlierUnknownCannotBindToALaterAttempt(){
        assertFalse(PendingInitialJoinPolicy.isolated(a.copy(index=2,attempts=2),proof,date))
        assertFalse(PendingInitialJoinPolicy.mayIsolate(a.copy(index=1,attempts=2),date,1,true,20_000))
    }
    @Test fun isolationDoesNotMutateThePendingWatchingOrGrantReward(){
        val original=a.copy()
        assertTrue(PendingInitialJoinPolicy.isolated(a,proof,date))
        assertEquals(original,a);assertFalse(a.confirmed);assertEquals("AD_OPENING",a.adState)
    }
    @Test fun bicycleUnknownDoesNotMakeCarOrWalkingScanIncomplete(){
        assertTrue(PendingInitialJoinPolicy.scopedPending(listOf("BIKE"),"BIKE"))
        assertFalse(PendingInitialJoinPolicy.scopedPending(listOf("BIKE"),"CAR"))
        assertFalse(PendingInitialJoinPolicy.scopedPending(listOf("BIKE"),"WALK"))
    }
    @Test fun productionDailyNavigationStillBlocksOrdinaryPendingButAllowsCompletedScanIsolation(){
        assertTrue(PendingInitialJoinPolicy.blocksDailyNavigation(true,false))
        assertFalse(PendingInitialJoinPolicy.blocksDailyNavigation(true,true))
        assertFalse(PendingInitialJoinPolicy.blocksDailyNavigation(false,false))
    }
    private class P(val isolated:Boolean=true):RoutinePort {
        val calls=mutableListOf<String>();var stopped=false;var day=true;var connected=true
        override fun connected()=connected
        override fun stopped()=stopped
        override fun sameDate()=day
        override suspend fun progress(stage:String,result:RoutineStepResult){}
        override suspend fun startup()=RoutineStepResult("DEFERRED","saved status").also{calls.add("startup")}
        override suspend fun challenges()=RoutineStepResult(if(isolated)"PARTIALLY_COMPLETED" else "BLOCKED","old pending",isolated).also{calls.add("challenges")}
        override suspend fun ads()=RoutineStepResult("LIMIT_REACHED","fresh official25/25").also{calls.add("ads")}
    }
    @Test fun safelyIsolatedChallengeAllowsIndependentDailyStageButNeverWholeDayDone()=runBlocking {
        val p=P();val r=RoutineSequence(p).run()
        assertEquals(listOf("startup","challenges","ads"),p.calls)
        assertEquals("PARTIALLY_COMPLETED",r.state);assertEquals("PARTIALLY_COMPLETED",r.steps["CHALLENGES"]!!.state)
        assertEquals("LIMIT_REACHED",r.steps["ADS"]!!.state)
    }
    @Test fun ordinaryUnknownStillStopsBeforeDailyAdvertising()=runBlocking {
        val p=P(false);assertEquals("BLOCKED",RoutineSequence(p).run().state)
        assertEquals(listOf("startup","challenges"),p.calls)
    }
    @Test fun stopDateAndAccessibilityGuardsStillPrecedeIndependentContinuation()=runBlocking {
        val stopped=P().also{it.stopped=true};assertEquals("STOPPED",RoutineSequence(stopped).run().state);assertTrue(stopped.calls.isEmpty())
        val day=P().also{it.day=false};assertEquals("BLOCKED",RoutineSequence(day).run().state);assertTrue(day.calls.isEmpty())
        val disconnected=P().also{it.connected=false};assertEquals("BLOCKED",RoutineSequence(disconnected).run().state);assertTrue(disconnected.calls.isEmpty())
    }
}
