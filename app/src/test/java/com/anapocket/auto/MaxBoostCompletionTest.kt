package com.anapocket.auto

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class MaxBoostCompletionTest {
    private val binding=MaxBoostBinding("2026-11-03","BIKE|自転車で500m移動しよう！","BIKE","自転車で500m移動しよう！",500,"CH-original",6,100,200,"unchanged-ads")
    private class Killed:RuntimeException()
    private class Journal { var attempts=0;var phase="PENDING";val events=mutableListOf<String>() }
    private inner class Port(val j:Journal=Journal()):MaxBoostCompletionPort {
        var time=0L;var page=MaxBoostPage.MAX_DIALOG;var post=MaxBoostPage.LIST;var succeedOn=1
        var trusted=true;var controls=1;var invalidReads=0;var reads=0;var position="A";var moveAfterFirst=false
        var containerClickable=true;var textClickable=false;var routeOk=true;var routeCat="";var currentCategory="WALK"
        var stopAfterRoute=false;var allowedNow=true;var durable=true;var failReserve=false;var killAt=0;var callback:Boolean?=true
        val actions=mutableListOf<Pair<String,String>>();val failures=mutableListOf<String>()
        val confirmedAds=listOf("join-confirmed","boost1-confirmed","boost2-confirmed","boost3-confirmed","boost4-confirmed","boost5-confirmed")
        val oldUnknown=listOf("2026-11-02 BIKE4 UNKNOWN","2026-11-02 MOV30 UNKNOWN")
        override fun now()=time
        override fun allowed()=allowedNow
        override suspend fun ensureDurableBinding(){check(durable){"write_failed"};j.events.add("binding_committed")}
        override fun persistedAttempts()=j.attempts
        override suspend fun frame():MaxBoostFrame {
            reads++;val ready=reads>invalidReads
            return MaxBoostFrame(page,7,"$page:$position:$ready",trusted,if(ready)controls else 0,ready,200)
        }
        override suspend fun reserveAttempt(number:Int){check(!failReserve);assertEquals(j.attempts+1,number);j.attempts=number;j.events.add("intent_$number")}
        override suspend fun click(frame:MaxBoostFrame,number:Int):MaxBoostClick {
            assertTrue(j.events.contains("binding_committed"));assertTrue(j.events.contains("intent_$number"))
            assertTrue(frame.signature.contains(position))
            val action=MaxBoostDialogPolicy.action(number,containerClickable,textClickable);actions.add(action to position)
            if(moveAfterFirst&&number==1)position="B"
            if(succeedOn>0&&number>=succeedOn)page=post
            if(killAt==number)throw Killed()
            return MaxBoostClick(action,true,if(action=="GESTURE")callback else null,"fixture_callback")
        }
        override suspend fun actionResult(number:Int,result:MaxBoostClick){j.events.add("result_$number")}
        override suspend fun dismissed(page:MaxBoostPage){assertTrue(MaxBoostDialogPolicy.safePost(page));j.phase="DISMISSED";j.events.add("post_verified")}
        override suspend fun restoreCategory(cat:String,page:MaxBoostPage):Boolean {assertEquals("DISMISSED",j.phase);routeCat=cat;if(routeOk)currentCategory=cat;if(stopAfterRoute)allowedNow=false;return routeOk}
        override suspend fun routed(){j.phase="ROUTED";j.events.add("category_today_verified")}
        override suspend fun failure(code:String,detail:String){j.phase="FAILED";failures.add("$code:$detail")}
        override suspend fun pause(ms:Long){time+=ms}
    }
    private fun fails(p:Port,code:String="MAX_BOOST_DIALOG_DISMISS_FAILED")=runBlocking {
        try{MaxBoostCompletionFlow(binding,p).run();fail("must stop")}catch(e:MaxBoostCompletionFailure){assertEquals(code,e.code)}
    }
    @Test fun officialMaxCopyIsSemanticAndNotSpecificTo200Points(){
        val ids=listOf("title_count","current_point","boost_list","button_no","button_no_text").map{MaxBoostDialogPolicy.PACKAGE+":id/"+it}.toSet()
        for(amount in listOf(200,450,1350))assertTrue(MaxBoostDialogPolicy.nativeShape(MaxBoostDialogPolicy.PACKAGE,MaxBoostDialogPolicy.ACTIVITY,true,ids,
            listOf("チャレンジ達成後の報酬が","最大値に到達しました！","${amount}pt","OK")))
        assertTrue(MaxBoostDialogPolicy.hasCopy(listOf("チャレンジ達成後の報酬が\n最大値に到達しました！")))
    }
    @Test fun ordinaryAdvertisingOkOrAnonymousAmountDoesNotIdentifyTheFinalDialog(){
        val ids=listOf("title_count","current_point","boost_list","button_no","button_no_text").map{MaxBoostDialogPolicy.PACKAGE+":id/"+it}.toSet()
        assertFalse(MaxBoostDialogPolicy.nativeShape(MaxBoostDialogPolicy.PACKAGE,MaxBoostDialogPolicy.ACTIVITY,true,ids,listOf("報酬をゲット！","200pt","OK")))
        assertFalse(MaxBoostDialogPolicy.nativeShape(MaxBoostDialogPolicy.PACKAGE,"com.bytedance.sdk.openadsdk.activity.TTFullScreenVideoActivity",true,ids,listOf("チャレンジ達成後の報酬が最大値に到達しました！","OK")))
        assertFalse(MaxBoostDialogPolicy.nativeShape("foreign",MaxBoostDialogPolicy.ACTIVITY,true,ids,listOf("チャレンジ達成後の報酬が最大値に到達しました！")))
        assertFalse(MaxBoostDialogPolicy.nativeShape(MaxBoostDialogPolicy.PACKAGE,MaxBoostDialogPolicy.ACTIVITY,false,ids,listOf("チャレンジ達成後の報酬が最大値に到達しました！")))
    }
    @Test fun boundTaskMustHaveDurableBoostFiveAndOriginalConfirmedLastAttempt(){
        fun valid(boost:Int=5,confirmed:Boolean=true,today:String="2026-11-03")=MaxBoostDialogPolicy.savedTask(binding.date,today,binding.key,binding.cat,binding.title,500,true,boost,"BOOST_MAX_CONFIRMED","CH-original",6,100,confirmed,200)
        assertTrue(valid());assertFalse(valid(4));assertFalse(valid(confirmed=false));assertFalse(valid(today="2026-11-04"))
    }
    @Test fun normalDialogClosesThenVerifiesPageAndCategoryInOrder()=runBlocking {
        val p=Port();val result=MaxBoostCompletionFlow(binding,p).run()
        assertEquals(1,result.attempts);assertEquals("ROUTED",p.j.phase);assertEquals("BIKE",p.routeCat)
        assertTrue(p.j.events.indexOf("binding_committed")<p.j.events.indexOf("intent_1"))
        assertTrue(p.j.events.indexOf("post_verified")<p.j.events.indexOf("category_today_verified"))
    }
    @Test fun nonClickableOkUsesItsNativeClickableContainerFirst()=runBlocking {
        val p=Port();p.textClickable=false;p.containerClickable=true;MaxBoostCompletionFlow(binding,p).run()
        assertEquals(listOf("NATIVE_CONTAINER"),p.actions.map{it.first})
    }
    @Test fun nonClickableContainerUsesFreshBoundedGesture()=runBlocking {
        val p=Port();p.textClickable=false;p.containerClickable=false;MaxBoostCompletionFlow(binding,p).run()
        assertEquals(listOf("GESTURE"),p.actions.map{it.first})
    }
    @Test fun temporarilyInvalidNodesAreReacquiredBeforeAnyAttempt()=runBlocking {
        val p=Port();p.invalidReads=3;MaxBoostCompletionFlow(binding,p).run()
        assertEquals(1,p.j.attempts);assertTrue(p.reads>=5)
    }
    @Test fun firstClickWithoutEffectReacquiresMovedBoundsThenSucceeds()=runBlocking {
        val p=Port();p.succeedOn=2;p.moveAfterFirst=true;MaxBoostCompletionFlow(binding,p).run()
        assertEquals(listOf("NATIVE_CONTAINER" to "A","GESTURE" to "B"),p.actions)
    }
    @Test fun successfulCallbackDoesNotCountAsDismissalWhenDialogRemains(){
        val p=Port();p.succeedOn=0;p.callback=true;fails(p)
        assertEquals(3,p.actions.size);assertFalse(p.j.events.contains("post_verified"));assertEquals("",p.routeCat)
        assertTrue(p.time<=MaxBoostDialogPolicy.DISMISS_MS)
    }
    @Test fun cancelledGestureDoesNotAdvanceToTheNextChallenge(){
        val p=Port();p.containerClickable=false;p.succeedOn=0;p.callback=false;fails(p)
        assertEquals(3,p.actions.size);assertEquals("",p.routeCat)
    }
    private fun classifiedPost(page:MaxBoostPage):MaxBoostPage {
        val prefix=MaxBoostDialogPolicy.PACKAGE+":id/"
        val ids=when(page){MaxBoostPage.DETAIL->setOf("design_bottom_sheet","text_name","text_date","close");MaxBoostPage.LIST->setOf("list");else->emptySet()}.map{prefix+it}.toSet()
        val labels=when(page){MaxBoostPage.DETAIL->listOf(binding.title,"2026/11/3");MaxBoostPage.LIST->listOf("ホーム","ためる","参加可能");else->listOf("ホーム","ためる")}
        val fields=mapOf("text_name" to binding.title,"text_date" to "2026/11/3")
        assertEquals(MaxBoostPage.OTHER,MaxBoostDialogPolicy.classify("foreign",MaxBoostDialogPolicy.ACTIVITY,true,ids,labels,fields,binding))
        if(page==MaxBoostPage.DETAIL)assertEquals(MaxBoostPage.OTHER,MaxBoostDialogPolicy.classify(MaxBoostDialogPolicy.PACKAGE,MaxBoostDialogPolicy.ACTIVITY,true,ids,labels,fields+("text_date" to "2026/11/2"),binding))
        assertEquals(MaxBoostPage.OTHER,MaxBoostDialogPolicy.classify(MaxBoostDialogPolicy.PACKAGE,MaxBoostDialogPolicy.ACTIVITY,true,ids+"android:id/message",listOf("ホーム","ためる","OK"),fields,binding))
        return MaxBoostDialogPolicy.classify(MaxBoostDialogPolicy.PACKAGE,MaxBoostDialogPolicy.ACTIVITY,true,ids,labels,fields,binding)
    }
    @Test fun returnedDetailIsVerifiedBeforeRestoringCategory()=runBlocking {
        val p=Port();p.post=classifiedPost(MaxBoostPage.DETAIL);val r=MaxBoostCompletionFlow(binding,p).run()
        assertEquals(MaxBoostPage.DETAIL,r.page);assertEquals("BIKE",p.currentCategory)
    }
    @Test fun returnedHomeIsVerifiedBeforeRestoringCategory()=runBlocking {
        val p=Port();p.post=classifiedPost(MaxBoostPage.HOME);val r=MaxBoostCompletionFlow(binding,p).run()
        assertEquals(MaxBoostPage.HOME,r.page);assertEquals("BIKE",p.currentCategory)
    }
    @Test fun returningToListStillRestoresOriginalCategoryBeforeSelectingNext()=runBlocking {
        val p=Port();p.post=classifiedPost(MaxBoostPage.LIST);assertEquals(MaxBoostPage.LIST,p.post);MaxBoostCompletionFlow(binding,p).run()
        val done=ChallengeCard(Cat.BIKE,"自転車で500m移動しよう！",500,"")
        val next=ChallengeCard(Cat.BIKE,"自転車で1km移動しよう！",1000,"")
        assertEquals(Cat.BIKE.name,p.currentCategory)
        assertEquals(next,CategoryRunSelection.next(listOf(done,next),Cat.valueOf(p.currentCategory),5000,setOf(done.key)))
    }
    @Test fun lastWalkCompletionCanThenSwitchToBikeWithoutReplayingWalk()=runBlocking {
        val walk=binding.copy(key="WALK|徒歩で5km移動しよう！",cat="WALK",title="徒歩で5km移動しよう！",meters=5000)
        val p=Port();p.post=MaxBoostPage.HOME;MaxBoostCompletionFlow(walk,p).run();assertEquals("WALK",p.routeCat)
        val bike=ChallengeCard(Cat.BIKE,"自転車で500m移動しよう！",500,"")
        assertEquals(bike,CategoryRunSelection.next(listOf(bike),Cat.BIKE,5000,setOf(walk.key)))
    }
    @Test fun processDeathAfterDurableIntentResumesWithoutAnotherFinalAdvertisement()=runBlocking {
        val p=Port();p.killAt=1;p.succeedOn=0
        try{MaxBoostCompletionFlow(binding,p).run();fail()}catch(_:Killed){}
        val resumed=Port(p.j);val originalAds=p.confirmedAds.toList();MaxBoostCompletionFlow(binding,resumed).run()
        assertEquals(2,resumed.j.attempts);assertEquals(listOf("GESTURE"),resumed.actions.map{it.first});assertEquals(originalAds,resumed.confirmedAds)
    }
    @Test fun processDeathAfterOkButBeforePostSaveDoesNotClickAgain()=runBlocking {
        val p=Port();p.killAt=1;p.post=MaxBoostPage.DETAIL
        try{MaxBoostCompletionFlow(binding,p).run();fail()}catch(_:Killed){}
        val resumed=Port(p.j);resumed.page=MaxBoostPage.DETAIL;MaxBoostCompletionFlow(binding,resumed).run()
        assertTrue(resumed.actions.isEmpty());assertEquals(1,resumed.j.attempts)
    }
    @Test fun completedAdRecordsAndHistoricalUnknownsStayUntouchedOnFailure(){
        val p=Port();val original=p.confirmedAds.toList();val unknown=p.oldUnknown.toList();p.succeedOn=0;fails(p)
        assertEquals(original,p.confirmedAds);assertEquals(unknown,p.oldUnknown)
        val restarted=Port(p.j);fails(restarted);assertTrue(restarted.actions.isEmpty())
    }
    @Test fun failedJournalOrIntentWriteCannotDispatchAnOk()=runBlocking {
        for(bindingFailure in listOf(true,false)){
            val p=Port();p.durable=!bindingFailure;p.failReserve=!bindingFailure
            try{MaxBoostCompletionFlow(binding,p).run();fail()}catch(_:IllegalStateException){}
            assertTrue(p.actions.isEmpty())
        }
    }
    @Test fun failedCategoryRestoreStopsAfterConfirmedDismissalWithoutReplaying() {
        val p=Port();p.routeOk=false;fails(p,"MAX_BOOST_CATEGORY_RESTORE_FAILED")
        assertTrue(p.j.events.contains("post_verified"));assertEquals(1,p.actions.size);assertFalse(p.j.events.contains("category_today_verified"))
        val dateChanged=Port();dateChanged.stopAfterRoute=true;fails(dateChanged,"MAX_BOOST_CATEGORY_RESTORE_FAILED")
        assertFalse(dateChanged.j.events.contains("category_today_verified"))
    }
    @Test fun foreignWindowAmbiguityAndChangedDateNeverClickAndMinimalLogIsBounded(){
        val foreign=Port();foreign.trusted=false;fails(foreign);assertTrue(foreign.actions.isEmpty())
        val duplicate=Port();duplicate.controls=2;fails(duplicate);assertTrue(duplicate.actions.isEmpty())
        val stopped=Port();stopped.allowedNow=false;fails(stopped);assertTrue(stopped.actions.isEmpty())
        assertTrue(MaxBoostDialogPolicy.minimalFits(0,4096));assertFalse(MaxBoostDialogPolicy.minimalFits(65536,1));assertFalse(MaxBoostDialogPolicy.minimalFits(0,8193))
    }
    @Test fun completionFailureBlocksTheRoutineEvenIfOldCategoryCoverageSaysDone()=runBlocking {
        for(pending in listOf(false,true)){
            var adStarts=0
            val p=object:RoutinePort {
                override fun stopped()=false
                override fun sameDate()=true
                override suspend fun progress(stage:String,result:RoutineStepResult){}
                override suspend fun startup()=RoutineStepResult("DEFERRED","completion first")
                override suspend fun challenges():RoutineStepResult {
                    val blocked=MaxBoostDialogPolicy.blocksRoutine(if(pending)"old scans all complete" else "MAX_BOOST_DIALOG_DISMISS_FAILED",pending)
                    return RoutineStepResult(if(blocked)"BLOCKED" else "DONE","MAX_BOOST_DIALOG_DISMISS_FAILED",!blocked)
                }
                override suspend fun ads():RoutineStepResult {adStarts++;return RoutineStepResult("DONE","")}
            }
            assertEquals("BLOCKED",RoutineSequence(p).run().state);assertEquals(0,adStarts)
        }
    }

}
