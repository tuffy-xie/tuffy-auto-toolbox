package com.anapocket.auto

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.io.FileOutputStream

/** Lifecycle/commit-fence regression; file-backed port is not an Android SQLite/device test. */
class AdStallReviewTest {
    private class Killed:RuntimeException()
    private class Port(val receiptAt:Long=Long.MAX_VALUE,val moving:Boolean=false,val failProof:Boolean=false,
        val dieAfterProof:Boolean=false,val stopAt:Long=Long.MAX_VALUE):AdInteractionPort {
        var time=0L;var probes=0;var committed=false;var proofWrites=0
        val events=mutableListOf<String>();val file=File.createTempFile("tuffy-stall-", ".journal").apply{deleteOnExit()}
        override fun now()=time
        override fun stopped()=time>=stopAt
        override suspend fun frame()=if(time>=receiptAt)AdFrame(true,true,1,true) else AdFrame(true,false,adSurface=true)
        override suspend fun pause(ms:Long){time+=ms}
        override suspend fun close()=false
        override suspend fun dismissReceipt(){assertTrue(committed);events.add("dismiss")}
        override suspend fun state(state:AdState){events.add(state.name)}
        override suspend fun receiptObserved(frame:AdFrame,before:Int){committed=true;events.add("receipt_committed")}
        override suspend fun stallProgressKey():String {probes++;return if(moving)"window:${time/8_000}" else "window:static"}
        override suspend fun stallReviewed(quietMs:Long,key:String){
            if(failProof)throw Killed()
            FileOutputStream(file).use{it.write("original-task|2026-11-03|CH-original|5|1793675873846|DISPATCHED|UNKNOWN|$quietMs|$key".toByteArray());it.fd.sync()}
            proofWrites++;events.add("stall_committed");if(dieAfterProof)throw Killed()
        }
    }
    @Test fun ordinaryReceiptDoesNotCallExceptionProbeOrWriteStallJournal()=runBlocking {
        val p=Port(receiptAt=8_000);val r=AdInteraction(p).awaitReceipt(0)
        assertTrue(r.completed&&r.reward);assertEquals(0,p.probes);assertEquals(0,p.proofWrites)
        assertTrue(p.events.indexOf("receipt_committed")<p.events.indexOf("dismiss"))
    }
    @Test fun quietCanvasNeedsAnotherThirtySecondsAndDurableProofRemainsUnknown()=runBlocking {
        val p=Port();val r=AdInteraction(p).awaitReceipt(0)
        assertEquals(AdState.UNCONFIRMED,r.state);assertFalse(r.reward||r.completed)
        assertTrue(p.time>=125_000&&p.time<130_000);assertEquals(1,p.proofWrites)
        assertTrue(p.file.readText().startsWith("original-task|2026-11-03|CH-original|5|1793675873846|DISPATCHED|UNKNOWN|"))
        assertFalse(p.committed);assertFalse("AD_FINISHED" in p.events)
    }
    @Test fun movingAdvertisementCanFinishAfterOriginalTimeoutWithoutRecovery()=runBlocking {
        val p=Port(receiptAt=180_000,moving=true);val r=AdInteraction(p).awaitReceipt(0)
        assertTrue(p.probes>0);assertTrue(r.reward&&r.completed);assertEquals(0,p.proofWrites)
        assertFalse("stall_committed" in p.events)
    }
    @Test fun visibleProgressCannotExtendAbsoluteObservationForever()=runBlocking {
        val p=Port(moving=true);val r=AdInteraction(p).awaitReceipt(0)
        assertEquals("ad_timeout_300s",r.reason);assertTrue(p.time>=300_000&&p.time<301_000)
        assertEquals(0,p.proofWrites);assertFalse(r.reward||r.completed)
    }
    @Test fun failedProofWriteStopsBeforeReturningRestartEligibility()=runBlocking {
        val p=Port(failProof=true)
        try{AdInteraction(p).awaitReceipt(0);fail("write failure must propagate")}catch(_:Killed){}
        assertEquals(0,p.proofWrites);assertEquals("",p.file.readText());assertFalse(p.committed)
    }
    @Test fun interruptionAfterProofRetainsOriginalAttemptAndUnknownOnReload()=runBlocking {
        val p=Port(dieAfterProof=true)
        try{AdInteraction(p).awaitReceipt(0);fail("simulated process interruption")}catch(_:Killed){}
        val reloaded=p.file.readText().split('|')
        assertEquals(listOf("original-task","2026-11-03","CH-original","5","1793675873846","DISPATCHED","UNKNOWN"),reloaded.take(7))
        assertTrue(reloaded[7].toLong()>=30_000);assertEquals(1,p.proofWrites);assertFalse(p.committed)
    }
    @Test fun stopDuringReviewCannotProduceStallPermissionOrReceipt()=runBlocking {
        val p=Port(stopAt=110_000);val r=AdInteraction(p).awaitReceipt(0)
        assertEquals("user_stopped",r.reason);assertEquals(0,p.proofWrites);assertFalse(r.reward)
    }
}
