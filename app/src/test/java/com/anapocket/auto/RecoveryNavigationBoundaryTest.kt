package com.anapocket.auto

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.io.FileOutputStream

class RecoveryNavigationBoundaryTest {
    private fun allowed(same:Boolean=true,date:Boolean=true,owner:Boolean=true,pending:Int=1,
        stage:String="REMOVE_ONLY_OBSERVED_ANA_TASK",age:Long=1000,requests:Int=0)=
        ChallengeRecoveryPolicy.liveRestartNavigation(same,date,owner,pending,stage,age,requests)
    @Test fun navigationReservationSurvivesKillWithoutReceiptOrSecondClick(){
        val f=File.createTempFile("tuffy-original-navigation-",".journal").apply{deleteOnExit()}
        val original="CH-original|6|1793834260580|DISPATCHED|UNKNOWN"
        assertTrue(allowed())
        FileOutputStream(f).use{it.write((original+"|resume_requests=1").toByteArray());it.fd.sync()}
        // Reload after a process interruption at the pre-click fence.
        assertEquals(original+"|resume_requests=1",f.readText());assertFalse(allowed(requests=1))
        assertFalse(f.readText().contains("GAIN"));assertFalse(f.readText().contains("CONFIRMED"))
    }
    @Test fun historicalLatestUnknownDoesNotSupplyALiveOriginalRestartContext(){
        assertFalse(allowed(same=false));assertFalse(allowed(date=false));assertFalse(allowed(owner=false))
        assertFalse(allowed(pending=2));assertFalse(allowed(stage=""));assertFalse(allowed(age=-1));assertFalse(allowed(age=60001))
        assertFalse(allowed(stage="OFFICIAL_RESUME_OBSERVED"));assertTrue(allowed(age=60000))
    }
    @Test fun closeRecognitionThenStallProbeHonoursApiGapAndPersistsOnlyUnknown()=runBlocking{
        var time=0L;var lastImage=0L;var proof="";var probes=0;var tooSoon=0;var receipt=false
        val port=object:AdInteractionPort{
            override fun now()=time
            override fun stopped()=false
            override suspend fun pause(ms:Long){time+=ms}
            override suspend fun frame():AdFrame{lastImage=time;return AdFrame(true,false,adSurface=true)}
            override suspend fun close()=false
            override suspend fun dismissReceipt(){fail("unknown is not a receipt")}
            override suspend fun state(state:AdState){}
            override suspend fun receiptObserved(frame:AdFrame,before:Int){receipt=true}
            override suspend fun stallProgressKey():String?{
                probes++;time+=OpaqueRecoveryPolicy.stallCaptureDelay(lastImage,time)
                if(time-lastImage<=333){tooSoon++;return null};return "same-window:static"
            }
            override suspend fun stallReviewed(quietMs:Long,key:String){assertTrue(quietMs>=30000);proof="CH-original|6|1793834260580|DISPATCHED|UNKNOWN"}
        }
        val r=AdInteraction(port).awaitReceipt(5)
        assertTrue(probes>0);assertEquals(0,tooSoon);assertFalse(receipt||r.reward||r.completed)
        assertEquals(AdState.UNCONFIRMED,r.state);assertTrue(time>=125000&&time<130000)
        assertEquals("CH-original|6|1793834260580|DISPATCHED|UNKNOWN",proof)
        assertEquals(0L,OpaqueRecoveryPolicy.stallCaptureDelay(0,95000))
        assertEquals(0L,OpaqueRecoveryPolicy.stallCaptureDelay(94000,95000))
    }
}
