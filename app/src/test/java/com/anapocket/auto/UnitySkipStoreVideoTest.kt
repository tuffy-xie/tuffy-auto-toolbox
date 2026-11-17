package com.anapocket.auto
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

/** 11/17 00:22 WALK 4km, 00:45 BIKE 500m, 02:31 CAR: Unity 「Skip 打开商店」 video still playing at 95 s (real node dumps). */
class UnitySkipStoreVideoTest {
    private fun labels(name:String):List<String> = javaClass.getResourceAsStream("/unity1117/$name")!!
        .bufferedReader(Charsets.UTF_8).useLines{it.map{l->l.substringAfter(" label=","")}.toList()}

    @Test fun realTimedOutScreenIsRecognisedOnlyInsideUnityActivity() {
        val ls=labels("skip_store_video_nodes.txt")
        assertTrue(ls.contains("Skip 打开商店"))
        assertTrue(UnityClosePolicy.skipStoreVideo(UnityClosePolicy.ACTIVITY,ls))
        assertFalse(UnityClosePolicy.skipStoreVideo("com.google.android.gms.ads.AdActivity",ls))
        assertFalse(UnityClosePolicy.skipStoreVideo(null,ls))
    }
    @Test fun skipControlIsNeverATapTarget() {
        // Same bounds as the dump: Rect(498, 83 - 686, 139) on a 720x1650 screen.
        assertFalse(UnityClosePolicy.storeSkip(UnityClosePolicy.ACTIVITY,"android.widget.Button","Skip 打开商店",true,
            498,83,188,56,720,1650,0))
        assertFalse(AdSignals.closeLabel("Skip 打开商店"))
    }
    @Test fun playSheetEndCardAndPlayableAreNotTheVideo() {
        val sheet=labels("play_sheet_after_timeout_nodes.txt")
        assertTrue(AdSignals.playSheet("com.android.vending",sheet))
        assertFalse(UnityClosePolicy.skipStoreVideo(UnityClosePolicy.ACTIVITY,sheet))
        assertFalse(UnityClosePolicy.skipStoreVideo(UnityClosePolicy.ACTIVITY,listOf("Close","Privacy")))
        assertFalse(UnityClosePolicy.skipStoreVideo(UnityClosePolicy.ACTIVITY,listOf("打开商店","Privacy")))
        assertFalse(UnityClosePolicy.skipStoreVideo(UnityClosePolicy.ACTIVITY,listOf("Skip 打开商店")))
    }

    /** Video ends by itself at [endAt]; the Play sheet then needs one close, the receipt follows. */
    private class Port(val unity:Boolean,val endAt:Long,val stopAt:Long=Long.MAX_VALUE,val blocked:Boolean=false):AdInteractionPort {
        var time=0L;var closes=0;var budget=0L;var source=""
        override fun now()=time
        override fun stopped()=time>=stopAt
        override suspend fun pause(ms:Long){time+=ms}
        override suspend fun state(state:AdState){}
        override suspend fun observationBudgetChanged(ms:Long){budget=ms}
        override suspend fun extendedObservationSource(source:String){this.source=source}
        override suspend fun frame()=when{
            closes>0->AdFrame(true,true,1,receipt=true)
            time>=endAt->AdFrame(true,false,closeAvailable=true,adSurface=true,storeOverlay=true)
            else->AdFrame(true,false,adSurface=true,unitySkipStoreVideo=unity,blocked=blocked)
        }
        override suspend fun close():Boolean{closes++;return true}
        override suspend fun dismissReceipt(){}
    }
    @Test fun longUnityVideoIsWatchedPastNinetyFiveSecondsAndItsSheetClosed()=runBlocking {
        val p=Port(true,endAt=130_000);val r=AdInteraction(p).awaitReceipt(0)
        assertTrue(r.reward);assertEquals(AdState.REWARD_CONFIRMED,r.state);assertEquals(1,p.closes)
        assertEquals(300_000L,p.budget);assertEquals("unity_skip_store_video",p.source);assertTrue(p.time>=130_000)
    }
    @Test fun unrecognisedVideoKeepsNinetyFiveSecondTimeout()=runBlocking {
        val p=Port(false,endAt=130_000);val r=AdInteraction(p).awaitReceipt(0)
        assertEquals("ad_timeout_95s",r.reason);assertFalse(r.reward);assertEquals(0,p.closes);assertEquals(0L,p.budget)
        assertTrue(p.time in 95_000..100_000)
    }
    @Test fun unityVideoStillHasAbsoluteThreeHundredSecondCap()=runBlocking {
        val p=Port(true,endAt=Long.MAX_VALUE);val r=AdInteraction(p).awaitReceipt(0)
        assertEquals("ad_timeout_300s",r.reason);assertFalse(r.reward);assertEquals(0,p.closes);assertTrue(p.time in 300_000..302_000)
    }
    @Test fun blockedScreenCannotGrantExtendedBudget()=runBlocking {
        val p=Port(true,endAt=Long.MAX_VALUE,blocked=true);val r=AdInteraction(p).awaitReceipt(0)
        assertEquals("accessibility_or_keyguard",r.reason);assertEquals(0L,p.budget)
    }
    @Test fun stoppingDuringExtendedWaitCreatesNoReceipt()=runBlocking {
        val p=Port(true,endAt=250_000,stopAt=130_000);val r=AdInteraction(p).awaitReceipt(0)
        assertEquals("user_stopped",r.reason);assertFalse(r.reward);assertEquals(0,p.closes)
    }
}
