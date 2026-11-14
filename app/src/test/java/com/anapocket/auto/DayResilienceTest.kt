package com.anapocket.auto

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/** One fault must not end the day: retries, a bounded check-in wait, and travel no longer holding ads. */
class DayResilienceTest {
    private fun at(s:String)=SimpleDateFormat("yyyy-MM-dd HH:mm:ss",Locale.US).apply{timeZone=TimeZone.getTimeZone("Asia/Tokyo")}.parse(s)!!.time
    private fun delay(state:String="BLOCKED",detail:String="startup_ad_timeout_300s",watchdog:Boolean=false,attempts:Int=0,stalls:Int=0,minute:Int=8,safety:Boolean=false,on:Boolean=true)=
        RoutineRetryPolicy.nextDelay(state,detail,watchdog,attempts,stalls,minute,safety,on)

    @Test fun oneOffFaultsAreRetriedWithGrowingGaps(){
        // Oct 31, Nov 1, Nov 3: login ad timed out at 00:08 and nothing else ran that day.
        assertEquals(10,delay())
        assertEquals(listOf(10,20,30,45,60,90,120,120),(0..7).map{delay(attempts=it,minute=60)})
        assertNull(delay(attempts=8))
    }
    @Test fun retryStopsWhenNothingMovesOrAPersonIsNeeded(){
        assertEquals(30,delay(attempts=2,stalls=1))
        assertNull(delay(attempts=2,stalls=2))
        assertNull(delay(safety=true))
        assertNull(delay(detail="secure_lock_requires_owner"))
        assertNull(delay(detail="账号保护已暂停任务，请手动处理后复核"))
        assertNull(delay(on=false))
    }
    @Test fun onlyStoppedRunsAreRetried(){
        assertNull(delay(state="DONE"));assertNull(delay(state="PARTIALLY_COMPLETED"))
        assertNull(delay(state="STOPPED"))                      // the user pressed stop
        assertEquals(10,delay(state="STOPPED",watchdog=true))   // the run watchdog stopped a hung run
        assertNull(delay(state="DEFERRED"))
    }
    @Test fun noRetryStartsLateAtNight(){
        assertEquals(10,delay(minute=22*60+50))
        assertNull(delay(minute=22*60+55))
        assertEquals(60,delay(attempts=4,minute=22*60))
        assertNull(delay(attempts=4,minute=22*60+1))
    }
    @Test fun checkinsWaitForAStartedAnaRunButNotForOneThatNeverStarted(){
        val due=at("2026-11-09 00:03:00")
        assertTrue(CheckinClock.registeredProtection(at("2026-11-08 23:58:00"),due,0,true))
        assertTrue(CheckinClock.registeredProtection(at("2026-11-09 00:17:59"),due,0,true))
        // ANA never started (service off, restart): 07:00 Rakuten is no longer held.
        assertFalse(CheckinClock.registeredProtection(at("2026-11-09 00:18:00"),due,0,true))
        assertFalse(CheckinClock.registeredProtection(at("2026-11-09 07:00:00"),due,0,true))
        assertFalse(CheckinClock.registeredProtection(at("2026-11-09 00:05:00"),due,due,true))
    }
    @Test fun blockedTravelHandsOverToAdsOnlyWithNothingOpen(){
        // Nov 4 / Nov 8: travel stopped on navigation with no ad open; the 25 daily ads never started.
        assertTrue(RoutinePolicy.adsAfterBlockedTravel(false,false,false,true))
        assertFalse(RoutinePolicy.adsAfterBlockedTravel(true,false,false,true))
        assertFalse(RoutinePolicy.adsAfterBlockedTravel(false,true,false,true))
        assertFalse(RoutinePolicy.adsAfterBlockedTravel(false,false,true,true))
        assertFalse(RoutinePolicy.adsAfterBlockedTravel(false,false,false,false))
        assertTrue(RoutinePolicy.travelYieldsToAds(anyCategoryOn=false,maxBoostPending=false))
        assertFalse(RoutinePolicy.travelYieldsToAds(anyCategoryOn=true,maxBoostPending=false))
        assertFalse(RoutinePolicy.travelYieldsToAds(anyCategoryOn=false,maxBoostPending=true))
    }
    private class Port(val c:RoutineStepResult):RoutinePort{
        val calls=mutableListOf<String>()
        override fun stopped()=false
        override fun sameDate()=true
        override suspend fun progress(stage:String,result:RoutineStepResult){}
        override suspend fun startup()=RoutineStepResult("REWARD_CONFIRMED","ok").also{calls.add("startup")}
        override suspend fun challenges()=c.also{calls.add("challenges")}
        override suspend fun ads()=RoutineStepResult("LIMIT_REACHED","官方 25/25").also{calls.add("ads")}
    }
    @Test fun adsRunAfterAnIndependentTravelStopButTheDayStaysUnfinished()=runBlocking{
        val p=Port(RoutineStepResult("BLOCKED","挑战执行中止：native_category_strip_not_observed",true))
        val r=RoutineSequence(p).run()
        assertEquals(listOf("startup","challenges","ads"),p.calls)
        assertEquals("BLOCKED",r.state);assertTrue(r.detail.contains("native_category_strip"))
        val held=Port(RoutineStepResult("BLOCKED","unknown",false))
        assertEquals("BLOCKED",RoutineSequence(held).run().state);assertEquals(listOf("startup","challenges"),held.calls)
    }
}
