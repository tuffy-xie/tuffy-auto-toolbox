package com.anapocket.auto

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.io.FileOutputStream

/** Sanitized actual NODE_DETAILS; screen coordinates are evidence, never production tap constants. */
class GoogleCreativeStageRegressionTest {
    data class N(val cls:String,val id:String?,val visible:Boolean,val enabled:Boolean,val clickable:Boolean,val top:Int,val label:String)
    private val pkg="us.ana_pocket.ANAPocket"
    private val activity="com.google.android.gms.ads.AdActivity"
    private fun fixture():List<N> = javaClass.getResourceAsStream("/google_fake_system_notice_nodes.txt")!!.bufferedReader().useLines{lines->lines.map{l->
        N(l.substringBefore(" id="),l.substringAfter(" id=").substringBefore(' ').takeUnless{it=="null"},
            "visible=true" in l,"enabled=true" in l,"clickable=true" in l,
            Regex("bounds=Rect\\([0-9]+, ([0-9]+)").find(l)!!.groupValues[1].toInt(),l.substringAfter(" label="))
    }.toList()}
    private fun ids(ns:List<N>)=ns.filter{it.visible}.mapNotNull{it.id}.toSet()
    private fun advance(n:N,ns:List<N>)=n.visible&&n.enabled&&GoogleDismissPolicy.advanceControl(pkg,activity,ids(ns),n.cls,n.clickable,n.label)
    private fun choose(ns:List<N>)=GoogleDismissPolicy.stageControl(ns,
        dismiss={false},advance={advance(it,ns)},close={it.visible&&it.enabled&&AdSignals.closeLabel(it.label)&&
            !GoogleDismissPolicy.creativeClose(true,ids(ns),it.id,it.top,1650)})
    @Test fun realTreeSkipWinsOverEarlierFakeCloseAndInstallControls(){
        val ns=fixture();val fake=ns.single{it.id=="close-btn"};val skip=choose(ns)!!
        assertTrue(ns.indexOf(fake)<ns.indexOf(skip));assertEquals("Skip",skip.label);assertEquals(73,skip.top)
        assertTrue(GoogleDismissPolicy.creativeClose(true,ids(ns),fake.id,fake.top,1650))
    }
    @Test fun missingOrAmbiguousSkipDoesNotClickFakeCloseCancelOrUpdate(){
        val ns=fixture();assertNull(choose(ns.filter{it.label!="Skip"}))
        assertNull(choose(ns+ns.single{it.label=="Skip"}))
    }
    @Test fun finalCornerCloseRemainsAvailableAfterAdvance(){
        val ns=fixture().filter{it.label!="Skip"&&it.id!="close-btn"}
        val x=N("android.widget.Button","close-btn",true,true,true,73,"Close")
        assertEquals(x,choose(ns+x));assertFalse(GoogleDismissPolicy.creativeClose(true,ids(ns),x.id,x.top,1650))
    }
    @Test fun nativeGoogleAndOtherSdkCloseRulesRemainIndependent(){
        assertTrue(GoogleNativeClosePolicy.matches(activity,"android.widget.ImageButton","Interstitial close button",true,true,true))
        assertFalse(GoogleDismissPolicy.creativeClose(false,ids(fixture()),"close-btn",619,1650))
        assertFalse(GoogleDismissPolicy.creativeClose(true,setOf("app-interstitial-slot"),"close-btn",619,1650))
    }
    @Test fun sdkNoThanksStageStillPrecedesAdvance(){
        assertEquals("NO THANKS",GoogleDismissPolicy.stageControl(listOf("Close","Skip","NO THANKS"),
            dismiss={it=="NO THANKS"},advance={it=="Skip"},close={it=="Close"}))
    }
    @Test fun actualSelectorAndProductionAdLoopPreserveAttemptUntilNativeReceiptCommit()=runBlocking {
        val file=File.createTempFile("tuffy-stage-", ".journal").apply{deleteOnExit()}
        val identity="CH-original|1|1793718980474|DISPATCHED|"
        fun save(state:String){FileOutputStream(file).use{it.write((identity+state).toByteArray());it.fd.sync()}}
        save("UNKNOWN");val actions=mutableListOf<String>();var phase=0;var time=0L;var selected:N?=null
        val start=fixture();val end=start.filter{it.label!="Skip"&&it.id!="close-btn"}+N("android.widget.Button","close-btn",true,true,true,73,"Close")
        val port=object:AdInteractionPort {
            override fun now()=time
            override fun stopped()=false
            override suspend fun pause(ms:Long){time+=ms}
            override suspend fun state(state:AdState){}
            override suspend fun frame():AdFrame {
                if(phase>=2)return AdFrame(true,true,1,true)
                val ns=if(phase==0)start else end;selected=choose(ns)
                return AdFrame(true,false,adSurface=true,closeAvailable=selected!=null,advanceControl=selected?.let{advance(it,ns)}==true)
            }
            override suspend fun close():Boolean {
                assertEquals(identity+"UNKNOWN",file.readText());actions.add(selected!!.label);phase++;return true
            }
            override suspend fun receiptObserved(frame:AdFrame,before:Int){assertEquals(listOf("Skip","Close"),actions);save("CONFIRMED")}
            override suspend fun dismissReceipt(){assertEquals(identity+"CONFIRMED",file.readText());actions.add("dismiss")}
        }
        val result=AdInteraction(port).awaitReceipt(0)
        assertTrue(result.completed&&result.reward);assertEquals(listOf("Skip","Close","dismiss"),actions)
        assertEquals(identity+"CONFIRMED",file.readText());assertEquals(2,phase)
    }
}
