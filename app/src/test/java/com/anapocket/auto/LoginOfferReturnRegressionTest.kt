package com.anapocket.auto

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class LoginOfferReturnRegressionTest {
    private class Port(private val frames:List<StartupAdFrame>):StartupAdPort {
        var time=0L;var index=0;var closes=0
        override fun now()=time
        override fun stopped()=false
        override fun sameDate()=true
        override suspend fun frame()=frames[index.coerceAtMost(frames.lastIndex)].also{index++}
        override suspend fun close(){closes++}
        override suspend fun observedAd(){}
        override suspend fun pause(ms:Long){time+=ms}
    }
    @Test fun realAdThenUnchangedLoginOfferDoesNotWaitFiveMinutesOrClaimReward()=runBlocking {
        val port=Port(listOf(StartupAdFrame(ad=true,close=true),StartupAdFrame(login=true)))
        val result=StartupAdSequence(port).run(true)
        assertEquals("UNCONFIRMED",result.state)
        assertEquals("login_reward_not_confirmed_after_offer_return",result.reason)
        assertTrue(port.time>=12_000L&&port.time<20_000L)
        assertEquals(0,port.closes) // the ad left by itself; its early plain close is never tapped
    }
    @Test fun freshReceiptWhileWaitingOnReturnedOfferStillConfirms()=runBlocking {
        val port=Port(listOf(StartupAdFrame(ad=true,close=true))+List(5){StartupAdFrame(login=true)}+StartupAdFrame(receipt=true))
        assertEquals("REWARD_CONFIRMED",StartupAdSequence(port).run(true).state)
    }
    @Test fun onlyTerminalSameDayNativeOfferCanBeLeftWithoutReplay(){
        assertTrue(StartupPolicy.mayLeaveUnconfirmedOffer("2026-10-31","2026-10-31","UNCONFIRMED",100,true,false))
        assertTrue(StartupPolicy.mayLeaveUnconfirmedOffer("2026-10-31","2026-10-31","FAILED",100,true,false))
        for(state in listOf("AD_OPENING","AD_PLAYING","REWARD_CONFIRMED"))assertFalse(StartupPolicy.mayLeaveUnconfirmedOffer("2026-10-31","2026-10-31",state,100,true,false))
        assertFalse(StartupPolicy.mayLeaveUnconfirmedOffer("2026-10-31","2026-10-30","UNCONFIRMED",100,true,false))
        assertFalse(StartupPolicy.mayLeaveUnconfirmedOffer("2026-10-31","2026-10-31","UNCONFIRMED",0,true,false))
        assertFalse(StartupPolicy.mayLeaveUnconfirmedOffer("2026-10-31","2026-10-31","UNCONFIRMED",100,false,false))
        assertFalse(StartupPolicy.mayLeaveUnconfirmedOffer("2026-10-31","2026-10-31","UNCONFIRMED",100,true,true))
    }
}
