package com.anapocket.auto

import org.junit.Assert.*
import org.junit.Test

/** Nov 9: a login ad and a travel ad each timed out and stayed open, and the guard refused the restart meant for them. */
class StalledRestartGuardTest {
    private fun refused(stage:String,sdk:Boolean=false,travel:Boolean=false,daily:Boolean=false)=StalledAdRestartPolicy.refused(stage,sdk,travel,daily)
    @Test fun aTimedOutLoginAdStillOpenPasses(){assertFalse(refused("STARTUP",sdk=true))}
    @Test fun aTimedOutTravelAdStillOpenPasses(){assertFalse(refused("CHALLENGES",sdk=true,travel=true))}
    @Test fun otherStagesWaitForTheAdWindow(){
        assertTrue(refused("ADS",sdk=true))
        assertTrue(refused("HOME",sdk=true))
        assertTrue(refused("MAX_BOOST",sdk=true))
    }
    @Test fun anUnresolvedTravelAdHoldsEveryOtherStage(){
        assertTrue(refused("STARTUP",travel=true))
        assertTrue(refused("ADS",travel=true))
    }
    @Test fun anUnresolvedDailyAdHoldsEveryStage(){
        for(s in listOf("STARTUP","CHALLENGES","ADS","HOME","MAX_BOOST"))assertTrue(s,refused(s,daily=true))
    }
    @Test fun nothingOpenIsNotRefused(){assertFalse(refused("ADS"))}
    @Test fun aFinishedUnconfirmedDailyAdNoLongerHoldsTravelRestarts(){
        assertFalse(StalledAdRestartPolicy.dailyHolds("CHALLENGES",false,true))
        assertTrue(StalledAdRestartPolicy.dailyHolds("CHALLENGES",true,false))
        for(s in listOf("STARTUP","ADS","HOME","MAX_BOOST"))assertTrue(s,StalledAdRestartPolicy.dailyHolds(s,false,true))
        assertFalse(StalledAdRestartPolicy.dailyHolds("ADS",false,false))
    }
    @Test fun theOpenAdLimitAllowsMoreThanThreeButStaysBounded(){
        val limit=StalledAdRestartPolicy.OPEN_AD_RESTART_LIMIT
        assertTrue(StalledAdRestartPolicy.reserve(false,3,limit){true})
        assertTrue(StalledAdRestartPolicy.reserve(false,limit-1,limit){true})
        assertFalse(StalledAdRestartPolicy.reserve(false,limit,limit){true})
        assertFalse("one restart per attempt",StalledAdRestartPolicy.reserve(true,0,limit){true})
    }
}
