package com.anapocket.auto

import org.junit.Assert.*
import org.junit.Test

/** Nov 9: BIKE 3km, a first-join ad that timed out and was missing from ANA's participating list afterwards. */
class AbsentInitialIsolationTest {
    private val today="2026-11-09"
    private val a=SubmittedUnknown(today,"BIKE|自転車で3km移動しよう！","CH665f24cc",1794158717841,1,1,false,true,"DISPATCHED",1)
    private fun absent(unknown:SubmittedUnknown=a,initial:Boolean=true,reason:String="ad_timeout_95s",clean:Boolean=true,stable:Long=15_000,daily:Boolean=false)=
        SubmittedUnknownIsolationPolicy.mayIsolateAbsent(unknown,today,initial,reason,clean,stable,daily)

    @Test fun setsAsideTheObservedAttempt(){assertTrue(absent())}
    @Test fun onlyAFirstJoinThatTimedOut(){
        assertFalse(absent(initial=false))
        assertFalse(absent(reason="process_or_run_interrupted"))
        assertFalse(absent(reason=""))
    }
    @Test fun needsACleanStablePageAndNoDailyAdInFlight(){
        assertFalse(absent(clean=false));assertFalse(absent(stable=14_999));assertFalse(absent(daily=true))
    }
    @Test fun needsASubmittedAttemptOfToday(){
        assertFalse(absent(a.copy(phase="ARMED")))
        assertFalse(absent(a.copy(departed=false)))
        assertFalse(absent(a.copy(date="2026-11-08")))
        assertFalse(absent(a.copy(confirmed=true)))
        assertFalse(absent(a.copy(pendingPerTask=2)))
    }
    @Test fun bothProofKindsBindTheSameAttempt(){
        for(kind in listOf(SubmittedUnknownIsolationPolicy.KIND,SubmittedUnknownIsolationPolicy.KIND_ABSENT))
            assertTrue(kind,SubmittedUnknownIsolationPolicy.bound(a,today,a.key,a.run,a.startedAt,1,1,kind))
        assertFalse(SubmittedUnknownIsolationPolicy.bound(a,today,a.key,a.run,a.startedAt,1,1,"other"))
    }
}
