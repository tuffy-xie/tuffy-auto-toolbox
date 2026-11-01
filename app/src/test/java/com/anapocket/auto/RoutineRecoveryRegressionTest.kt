package com.anapocket.auto
import org.junit.Assert.*
import org.junit.Test

class RoutineRecoveryRegressionTest {
    private fun eligible(state:String="RUNNING",date:String="2026-10-31",stage:String="CHALLENGES",
        lowMemory:Boolean=true,stopped:Boolean=false,risk:Boolean=false,enabled:Boolean=true,used:Int=0,
        exit:Long=9500L,now:Long=10000L)=RoutineRecoveryPolicy.eligible("2026-10-31",date,state,"RT-original",stage,
        9000L,now,exit,lowMemory,enabled,stopped,risk,used)
    @Test fun recentLowMemoryInterruptedRoutineCanReviewSavedAttempt(){assertTrue(eligible())}
    @Test fun stoppedCompletedOrBlockedRoutineNeverRestarts(){for(s in listOf("STOPPED","DONE","BLOCKED"))assertFalse(eligible(state=s))}
    @Test fun previousDateOrExpiredExitDoesNotTriggerRecovery(){assertFalse(eligible(date="2026-10-30"));assertFalse(eligible(now=200000L))}
    @Test fun forceStopUpgradeOrCrashDoesNotMasqueradeAsLowMemory(){assertFalse(eligible(lowMemory=false));assertFalse(eligible(exit=8000L))}
    @Test fun userStopRiskAndDisabledScheduleAreRespected(){assertFalse(eligible(stopped=true));assertFalse(eligible(risk=true));assertFalse(eligible(enabled=false))}
    @Test fun recoveryBudgetIsFiniteAndOnlyBusinessStagesRecover(){assertTrue(eligible(used=2));assertFalse(eligible(used=3));assertFalse(eligible(stage="DONE"))}
}
