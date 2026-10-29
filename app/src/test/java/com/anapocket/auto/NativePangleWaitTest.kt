package com.anapocket.auto
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class NativePangleWaitTest {
    private class Port(val native: Boolean, val foreign: Boolean=false, val closeAt: Long=110_000, val stopAt: Long=Long.MAX_VALUE): AdInteractionPort {
        var time=0L;var closes=0;var budget=0L
        override fun now()=time
        override fun stopped()=time>=stopAt
        override suspend fun pause(ms:Long){time+=ms}
        override suspend fun state(state:AdState){}
        override suspend fun observationBudgetChanged(ms:Long){budget=ms}
        override suspend fun frame()=if(closes==0) AdFrame(!foreign,false,closeAvailable=time>=closeAt,adSurface=true,nativePangleRender=native)
            else AdFrame(true,true,1,receipt=true)
        override suspend fun close():Boolean{closes++;return true}
        override suspend fun dismissReceipt(){}
    }
    @Test fun observedNativeFormatCanWaitForRealCloseAfterNinetyFiveSeconds()=runBlocking {
        val p=Port(true);val r=AdInteraction(p).awaitReceipt(0)
        assertTrue(r.completed&&r.reward);assertEquals(1,p.closes);assertTrue(p.time>=110_000);assertEquals(300_000L,p.budget)
    }
    @Test fun otherFormatsKeepOriginalNinetyFiveSecondBudget()=runBlocking {
        val p=Port(false);val r=AdInteraction(p).awaitReceipt(0)
        assertFalse(r.completed);assertFalse(r.reward);assertEquals(0,p.closes);assertTrue(p.time in 95_000..100_000);assertEquals(0L,p.budget)
    }
    @Test fun nativeFormatStillHasAbsoluteThreeHundredSecondCap()=runBlocking {
        val p=Port(true,closeAt=Long.MAX_VALUE);val r=AdInteraction(p).awaitReceipt(0)
        assertFalse(r.completed);assertFalse(r.reward);assertEquals(0,p.closes);assertTrue(p.time in 300_000..302_000);assertEquals("ad_timeout_300s",r.reason)
    }
    @Test fun foreignWindowCannotGrantNativeWaitingBudget()=runBlocking {
        val p=Port(true,foreign=true);val r=AdInteraction(p).awaitReceipt(0)
        assertFalse(r.completed);assertEquals(0L,p.budget);assertTrue(p.time<100_000)
    }
    @Test fun stoppingDuringExtendedWaitDoesNotCreateReceiptOrRetry()=runBlocking {
        val p=Port(true,closeAt=250_000,stopAt=130_000);val r=AdInteraction(p).awaitReceipt(0)
        assertEquals("user_stopped",r.reason);assertFalse(r.completed);assertFalse(r.reward);assertEquals(0,p.closes)
    }
}
