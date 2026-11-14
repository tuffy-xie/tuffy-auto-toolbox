package com.anapocket.auto

import org.junit.Assert.*
import org.junit.Test

/** Nov 9: the timed-out WALK Boost, with ANA's panel back on 1回目 at 100pt and the ad gone. */
class OfficialPanelSettleTest {
    private fun settle(task:String="UNCONFIRMED",ad:String="UNCONFIRMED",reason:String="ad_timeout_95s",confirmed:Boolean=false,initial:Boolean=false,
        index:Int=2,attempts:Int=2,pending:Int=1,sameDate:Boolean=true,bound:Boolean=true,phase:String="DISPATCHED",adSurface:Boolean=false,priorNoGain:Boolean=false,outcome:String?="NO_GAIN")=
        ChallengeRecoveryPolicy.panelMaySettle(task,ad,reason,confirmed,initial,index,attempts,pending,sameDate,bound,phase,adSurface,priorNoGain,outcome)

    @Test fun readsTheObservedAttempt(){assertTrue(settle());assertTrue(settle(outcome="ADVANCED"))}
    @Test fun waitsWhileTheAdIsStillOpen(){assertFalse(settle(adSurface=true))}
    @Test fun needsAPanelThatMatchesTheAttempt(){assertFalse(settle(outcome=null))}
    @Test fun neverForTheInitialJoinOrAnUnboundOrUnsentAttempt(){
        assertFalse(settle(initial=true));assertFalse(settle(bound=false));assertFalse(settle(phase="ARMED"))
    }
    @Test fun oneAttemptOneTaskSameDay(){
        assertFalse(settle(pending=2));assertFalse(settle(sameDate=false));assertFalse(settle(index=1,attempts=2))
    }
    @Test fun atMostOneNoGainPerTask(){assertFalse(settle(priorNoGain=true))}
    /** Nov 13 CAR 30km index 4: store sheet, return, panel still 3回目 at 150pt. */
    @Test fun completionNotConfirmedIsReadFromThePanelOnly(){
        assertTrue(settle(reason="completion_not_confirmed",index=4,attempts=4))
        assertFalse(settle(reason="completion_not_confirmed",index=4,attempts=4,adSurface=true))
        assertFalse(settle(reason="completion_not_confirmed",index=4,attempts=4,priorNoGain=true))
        assertFalse(settle(reason="completion_not_confirmed",index=4,attempts=4,outcome=null))
        assertFalse(ChallengeRecoveryPolicy.canResume("UNCONFIRMED","UNCONFIRMED","completion_not_confirmed",false,4,4,true))
    }
    @Test fun onlyAKnownTimeout(){assertFalse(settle(reason="login_required"));assertFalse(settle(confirmed=true))}
}
