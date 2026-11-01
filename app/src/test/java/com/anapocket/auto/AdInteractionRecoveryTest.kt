package com.anapocket.auto
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
class AdInteractionRecoveryTest {
    private class Port(private val after:Int):AdInteractionPort {
        var time=0L;var frames=0;var dismissed=0
        override fun now()=time
        override fun stopped()=false
        override suspend fun frame()=if(frames++==0)AdFrame(true,false,receipt=true) else AdFrame(true,true,watched=after)
        override suspend fun close()=false
        override suspend fun dismissReceipt(){dismissed++}
        override suspend fun state(state:AdState){}
        override suspend fun pause(ms:Long){time+=ms}
    }
    @Test fun verifiedPriorDepartureCanConsumeLateReceiptWithoutOpeningAd()=runBlocking{val p=Port(24);val r=AdInteraction(p).awaitReceipt(23,true);assertTrue(r.completed&&r.reward);assertEquals(24,r.after);assertEquals(1,p.dismissed)}
    @Test fun defaultFlowStillRejectsAnUnboundPreexistingReceipt()=runBlocking{val p=Port(24);val r=AdInteraction(p).awaitReceipt(23);assertFalse(r.completed||r.reward);assertEquals(0,p.dismissed)}
    @Test fun recoveredCounterJumpIsStillUnconfirmed()=runBlocking{val r=AdInteraction(Port(25)).awaitReceipt(23,true);assertEquals(AdState.UNCONFIRMED,r.state);assertFalse(r.completed);assertEquals("counter_jump_ambiguous",r.reason)}
}
