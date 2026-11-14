package com.anapocket.auto
import org.junit.Assert.*
import org.junit.Test

class Nov8RecoveryRegressionTest {
    private val date="2026-11-08"
    private fun good(i:Int,before:Int)=AdAttempt("r",date,i,i.toLong(),AdState.REWARD_CONFIRMED,true,true,100,officialBefore=before,officialAfter=before+1)
    private fun blank(i:Int,before:Int)=AdAttempt("r",date,i,i.toLong(),AdState.UNCONFIRMED,false,false,100,"ad_timeout_95s",before,dispatchPhase="DISPATCHED",adDepartureObserved=true)

    @Test fun bothObservedTutorialsAreRecognizedByTheirOwnIds(){
        assertTrue(CategoryOverlayPolicy.tutorial(setOf("day_filter_tutorial","day_filter_tutorial_balloon","ok_button","page_text_view")))
        assertTrue(CategoryOverlayPolicy.tutorial(setOf("type_filter_tutorial","type_filter_tutorial_balloon","ok_button")))
        assertFalse(CategoryOverlayPolicy.tutorial(setOf("ok_button")))
        assertFalse(CategoryOverlayPolicy.tutorial(setOf("day_filter_tutorial_balloon")))
        assertFalse(CategoryOverlayPolicy.tutorial(setOf("button_ok","title_join")))
    }
    @Test fun completedSheetNeedsItsCloseAndReceiveControls(){
        assertTrue(CategoryOverlayPolicy.completedSheet(setOf("design_bottom_sheet","close","button_receive")))
        assertFalse(CategoryOverlayPolicy.completedSheet(setOf("design_bottom_sheet","close")))
        assertFalse(CategoryOverlayPolicy.completedSheet(setOf("close","button_receive")))
    }
    @Test fun cornerCloseMatchesOnlyTheObservedPromoCardShape(){
        assertTrue(CategoryOverlayPolicy.cornerClose(42f,42f,true,1f,.97f,2))
        assertFalse(CategoryOverlayPolicy.cornerClose(42f,42f,false,1f,.97f,2))
        assertFalse(CategoryOverlayPolicy.cornerClose(42f,42f,true,.5f,.97f,2))
        assertFalse(CategoryOverlayPolicy.cornerClose(42f,42f,true,1f,.5f,2))
        assertFalse(CategoryOverlayPolicy.cornerClose(42f,42f,true,1f,.97f,3))
        assertFalse(CategoryOverlayPolicy.cornerClose(276f,276f,true,1f,.97f,2))
        assertFalse(CategoryOverlayPolicy.cornerClose(10f,10f,true,1f,.97f,2))
    }
    private class ReceiptPort(val tapsNeeded:Int):AdInteractionPort{
        var t=0L;var dismisses=0;var seen=false
        override fun now()=t
        override fun stopped()=false
        override suspend fun frame():AdFrame{
            if(!seen){seen=true;return AdFrame(anaForeground=true,entry=false,adSurface=true)}
            return if(dismisses<tapsNeeded)AdFrame(anaForeground=true,entry=false,receipt=true)
            else AdFrame(anaForeground=true,entry=true,watched=19)
        }
        override suspend fun close()=false
        override suspend fun dismissReceipt(){dismisses++}
        override suspend fun pause(ms:Long){t+=ms}
        override suspend fun state(state:AdState){}
    }
    @Test fun missedReceiptTapIsRetriedUntilTheCounterIsVisible()=kotlinx.coroutines.runBlocking{
        val port=ReceiptPort(3)
        val r=AdInteraction(port).awaitReceipt(18)
        assertEquals(AdState.REWARD_CONFIRMED,r.state);assertTrue(r.completed);assertEquals(19,r.after);assertEquals(3,port.dismisses)
    }
    @Test fun receiptRetriesAreBoundedAndStillTimeOut()=kotlinx.coroutines.runBlocking{
        val port=ReceiptPort(Int.MAX_VALUE)
        val r=AdInteraction(port).awaitReceipt(18)
        assertEquals(AdState.UNCONFIRMED,r.state);assertTrue(r.reward);assertFalse(r.completed);assertEquals(6,port.dismisses)
    }
    @Test fun timedOutRewardWithDurableReceiptSettlesOnlyOnExactPlusOne(){
        fun a(reward:Boolean,reason:String)=AdAttempt("r",date,2,2L,AdState.UNCONFIRMED,false,reward,100,reason,18,dispatchPhase="DISPATCHED",adDepartureObserved=true,officialBinding="{}")
        val ok=listOf(good(1,17),a(true,"ad_part_timeout_95s"))
        assertTrue(OfficialAdRecoveryPolicy.recordedReceipt(ok,date,date,19,true))
        assertFalse(OfficialAdRecoveryPolicy.recordedReceipt(ok,date,date,18,true))
        assertFalse(OfficialAdRecoveryPolicy.recordedReceipt(ok,date,date,20,true))
        assertFalse(OfficialAdRecoveryPolicy.recordedReceipt(ok,date,date,19,false))
        assertFalse(OfficialAdRecoveryPolicy.recordedReceipt(listOf(good(1,17),a(false,"ad_part_timeout_95s")),date,date,19,true))
        assertFalse(OfficialAdRecoveryPolicy.recordedReceipt(listOf(good(1,17),a(true,"user_stopped")),date,date,19,true))
    }
    @Test fun lateBoostAcceptsADispatchedBoundAttemptWithoutDepartureMark(){
        assertTrue(BoundLateBoostReceiptPolicy.departed(true,false,""))
        assertTrue(BoundLateBoostReceiptPolicy.departed(false,true,"DISPATCHED"))
        assertFalse(BoundLateBoostReceiptPolicy.departed(false,true,"ARMED"))
        assertFalse(BoundLateBoostReceiptPolicy.departed(false,false,"DISPATCHED"))
        val steps=listOf(25,25,25,50,50)
        val labels=listOf("3回目のブーストをしますか？","現在のチャレンジ達成後の報酬：75pt")+steps.map{"+$it"}
        assertTrue(BoundLateBoostReceiptPolicy.matches("UNCONFIRMED","UNCONFIRMED","ad_timeout_95s",false,false,3,3,1,true,true,1,50,75,steps,true,labels))
        assertFalse(BoundLateBoostReceiptPolicy.matches("UNCONFIRMED","UNCONFIRMED","ad_timeout_95s",false,false,3,3,1,true,false,1,50,75,steps,true,labels))
        assertFalse(BoundLateBoostReceiptPolicy.matches("UNCONFIRMED","UNCONFIRMED","ad_timeout_95s",false,false,3,3,1,true,true,1,50,100,steps,true,labels))
    }
    @Test fun googleSkipIsKeptWhenTheTrackedWindowIsAnaDialog(){
        val ids=setOf("app-interstitial-slot","mys-creative")
        assertTrue(GoogleDismissPolicy.advanceControl(StartupPolicy.ANA,"androidx.activity.ComponentDialog",ids,"android.widget.Button",true,"Skip"))
        assertTrue(GoogleDismissPolicy.advanceControl(StartupPolicy.ANA,"com.google.android.gms.ads.AdActivity",ids,"android.widget.Button",true,"Skip"))
        assertFalse(GoogleDismissPolicy.advanceControl(StartupPolicy.ANA,"androidx.activity.ComponentDialog",setOf("mys-creative"),"android.widget.Button",true,"Skip"))
        assertFalse(GoogleDismissPolicy.advanceControl(StartupPolicy.ANA,"x.Other",ids,"android.widget.Button",true,"Skip"))
    }
    @Test fun molocoSkipNeedsItsActivityButtonLabelAndCorner(){
        val a="com.moloco.sdk.xenoss.sdkdevkit.android.adrenderer.internal.templates.renderer.fullscreen.FullscreenWebviewActivity"
        val b="android.widget.Button"
        assertTrue(MolocoClosePolicy.skip(a,b,"Skip",true,638,40,720,1650))
        assertFalse(MolocoClosePolicy.skip("com.applovin.adview.AppLovinFullscreenActivity",b,"Skip",true,638,40,720,1650))
        assertFalse(MolocoClosePolicy.skip(a,"android.view.View","Skip",true,638,40,720,1650))
        assertFalse(MolocoClosePolicy.skip(a,b,"Get it on Google Play",true,638,40,720,1650))
        assertFalse(MolocoClosePolicy.skip(a,b,"Skip",false,638,40,720,1650))
        assertFalse(MolocoClosePolicy.skip(a,b,"Skip",true,140,1518,720,1650))
    }
    @Test fun faultBudgetIsPerDayAndTotalSizeStaysBounded(){
        val old=List(FaultBudgetPolicy.MAX_DIRS_PER_DAY){"2026-11-03"}
        assertTrue(FaultBudgetPolicy.hasRoom(old,date,18L*1024*1024))
        assertFalse(FaultBudgetPolicy.hasRoom(List(FaultBudgetPolicy.MAX_DIRS_PER_DAY){date},date,0))
        assertFalse(FaultBudgetPolicy.hasRoom(emptyList(),date,FaultBudgetPolicy.MAX_TOTAL_BYTES))
        assertEquals(256L*1024*1024,FaultBudgetPolicy.MAX_TOTAL_BYTES)
    }
    @Test fun fullFaultBudgetEvictsOldestFoldersFirst(){
        val mb=1024L*1024
        val olds=List(200){FaultDir("old$it","2026-11-0"+(1+it/50),1000L+it,mb+mb/4)}
        val todays=List(10){FaultDir("today$it",date,9_000_000L+it,mb)}
        val ev=FaultBudgetPolicy.evict(todays+olds,date)
        assertTrue(ev.isNotEmpty())
        assertTrue(ev.none{it.date==date})
        assertEquals(olds.take(ev.size),ev) // oldest first
        val left=(todays+olds).sumOf{it.bytes}-ev.sumOf{it.bytes}
        assertTrue(left<=FaultBudgetPolicy.MAX_TOTAL_BYTES-FaultBudgetPolicy.CAPTURE_RESERVE_BYTES)
        assertTrue(FaultBudgetPolicy.hasRoom((todays+olds-ev.toSet()).map{it.date},date,left))
        // Under budget: nothing deleted.
        assertTrue(FaultBudgetPolicy.evict(olds.take(20),date).isEmpty())
    }
    // 11/11: the daily cap was used up at 02:56 and the rest of the night left no evidence.
    @Test fun dailyCapIsARingThatKeepsTheNewestAndLeavesOlderDaysAlone(){
        val kb=1024L;val cap=FaultBudgetPolicy.MAX_DIRS_PER_DAY
        val olds=List(30){FaultDir("old$it","2026-11-07",1000L+it,300*kb)}
        val today=List(cap){FaultDir("t$it",date,5_000_000L+it,300*kb)}
        val ev=FaultBudgetPolicy.evict(today+olds,date)
        assertEquals(listOf(today.first()),ev) // exactly today's oldest, nothing from earlier days
        val rest=today+olds-ev.toSet()
        assertTrue(FaultBudgetPolicy.hasRoom(rest.map{it.date},date,rest.sumOf{it.bytes}))
        // Three over the cap (e.g. after the cap was lowered): the three oldest of today go.
        val over=today+List(3){FaultDir("x$it",date,9_000_000L+it,300*kb)}
        assertEquals(today.take(4),FaultBudgetPolicy.evict(over,date))
        // One below the cap: nothing is deleted.
        assertTrue(FaultBudgetPolicy.evict(today.drop(1)+olds,date).isEmpty())
        assertTrue(cap>=200&&FaultBudgetPolicy.CAPTURE_LOG_BYTES<=256*kb)
    }
    @Test fun googleScrollToClosePromptIsScrolledNeverTapped(){
        val g="com.google.android.gms.ads.AdActivity"
        val labels=listOf("Scroll to bottom of ad to close","カーライフグループ大阪","人気商品","¥750,000") // real 11/12 00:21 screen
        assertTrue(GoogleNativeClosePolicy.scrollToClose(g,labels))
        assertTrue(GoogleNativeClosePolicy.scrollPrompt(labels[0]))
        assertFalse(GoogleNativeClosePolicy.scrollToClose("x.Other",labels))
        assertFalse(GoogleNativeClosePolicy.scrollToClose(g,listOf("Close")))
        assertFalse(GoogleNativeClosePolicy.scrollPrompt("Close"))
    }
    @Test fun unityWebEndCardWithoutCloseIsAProbeSurface(){
        val u="com.unity3d.ads.adplayer.FullScreenWebViewDisplay"
        val ids=setOf("SnlCanvas_4","SnlCanvas_5","SnlCanvas_9","content","pixiview","root","soundButton") // real 11/11 04:43 shape
        assertTrue(OpaqueRecoveryPolicy.stallSurface(u,ids))
        assertNull(OpaqueRecoveryPolicy.stallProbeFailure(34,"a","a",u,ids))
        assertFalse(OpaqueRecoveryPolicy.stallSurface(u,setOf("root")))
        assertFalse(OpaqueRecoveryPolicy.stallSurface("x.Other",ids))
    }
    @Test fun interactiveHtmlAdIsAProbeSurfaceButPartialShapeIsNot(){
        val ids=setOf("root","interactive-html-stage")
        assertTrue(OpaqueRecoveryPolicy.stallSurface("x.Other",ids))
        assertNull(OpaqueRecoveryPolicy.stallProbeFailure(34,"a","a","x.Other",ids))
        assertFalse(OpaqueRecoveryPolicy.stallSurface("x.Other",setOf("root")))
        assertFalse(OpaqueRecoveryPolicy.stallSurface("x.Other",setOf("interactive-html-stage")))
    }
    @Test fun reviewNeedsClosedAdUnchangedCounterNoReceiptAndCooldown(){
        val a=listOf(blank(1,0))
        assertTrue(OfficialLoadFailurePolicy.mayReviewBlank(a,date,date,0,true,false,60_000,emptySet()))
        assertFalse(OfficialLoadFailurePolicy.mayReviewBlank(a,date,date,0,false,false,60_000,emptySet()))
        assertFalse(OfficialLoadFailurePolicy.mayReviewBlank(a,date,date,1,true,false,60_000,emptySet()))
        assertFalse(OfficialLoadFailurePolicy.mayReviewBlank(a,date,date,0,true,true,60_000,emptySet()))
        assertFalse(OfficialLoadFailurePolicy.mayReviewBlank(a,date,date,0,true,false,59_999,emptySet()))
        assertFalse(OfficialLoadFailurePolicy.mayReviewBlank(a,date,"2026-11-09",0,true,false,60_000,emptySet()))
    }
    @Test fun rewardObservedOrOtherFailuresAreNeverBlankStalls(){
        val a=blank(1,0)
        assertTrue(OfficialLoadFailurePolicy.blankStall(a))
        assertFalse(OfficialLoadFailurePolicy.blankStall(a.copy(rewardConfirmed=true)))
        assertFalse(OfficialLoadFailurePolicy.blankStall(a.copy(failureReason="completion_not_confirmed")))
        assertFalse(OfficialLoadFailurePolicy.blankStall(a.copy(adDepartureObserved=false)))
        assertFalse(OfficialLoadFailurePolicy.blankStall(a.copy(dispatchPhase="ARMED")))
        assertFalse(OfficialLoadFailurePolicy.blankStall(a.copy(finishedAt=0)))
    }
    @Test fun unreviewedBlankKeepsBlockingAndReviewCapStaysTwo(){
        val a=blank(1,0);val k=OfficialLoadFailurePolicy.key(a)
        assertFalse(OfficialLoadFailurePolicy.canReserve(0,0,true,false,listOf(a),emptySet()))
        assertTrue(OfficialLoadFailurePolicy.canReserve(0,0,true,false,listOf(a),setOf(k)))
        assertFalse(OfficialLoadFailurePolicy.canReserve(0,0,false,false,listOf(a),setOf(k)))
        val b=blank(2,0);val c=blank(3,0)
        assertFalse(OfficialLoadFailurePolicy.mayReviewBlank(listOf(a,b,c),date,date,0,true,false,60_000,setOf(k,OfficialLoadFailurePolicy.key(b))))
        assertFalse(OfficialLoadFailurePolicy.mayReviewBlank(listOf(a),date,date,0,true,false,60_000,setOf(k)))
    }
    @Test fun laterAttemptAfterReviewedBlankResolvesNormally(){
        val a=blank(1,0);val k=OfficialLoadFailurePolicy.key(a)
        val rows=listOf(a,good(2,0))
        assertTrue(rows.all{OfficialLoadFailurePolicy.resolved(it,setOf(k))})
        assertTrue(OfficialLoadFailurePolicy.canReserve(1,1,true,false,rows,setOf(k)))
        assertFalse(OfficialLoadFailurePolicy.resolved(a,emptySet()))
    }
}
