package com.anapocket.auto
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class H10ResumeAndNoticeRegressionTest {
    private val date="2026-10-31"
    @Test fun disabledAndOldGenerationNoticesAreRejected(){
        assertFalse(CheckinNoticePolicy.mayPublish(false,8,8,date,date,"ACTUAL_BLOCKED",false))
        assertFalse(CheckinNoticePolicy.mayPublish(true,9,8,date,date,"ACTUAL_BLOCKED",false))
        assertFalse(CheckinNoticePolicy.mayPublish(true,8,8,date,"2026-11-01","ACTUAL_BLOCKED",false))
    }
    @Test fun waitingAndPreemptionNeverBecomeBlockedNotifications(){
        for(status in listOf("WAITING_FOR_ANA","PREEMPTED_BY_ANA"))assertFalse(CheckinNoticePolicy.mayPublish(true,8,8,date,date,status,false))
        assertFalse(CheckinNoticePolicy.mayPublish(true,8,8,date,date,"ACTUAL_BLOCKED",true))
        assertEquals("PREEMPTED_BY_ANA",CheckinNoticePolicy.status(listOf("BLOCKED"),"ANA_preempted"))
    }
    @Test fun actualLaunchFailureAndPermissionNeedRemainDistinct(){
        assertEquals("ACTUAL_BLOCKED",CheckinNoticePolicy.status(listOf("BLOCKED")))
        assertEquals("NEEDS_USER_ACTION",CheckinNoticePolicy.status(emptyList(),"accessibility_disconnected"))
        assertEquals("FAILED",CheckinNoticePolicy.status(listOf("FAILED")))
        assertEquals("ACTUAL_BLOCKED",CheckinNoticePolicy.status(emptyList(),"execution_timeout"))
        assertEquals("NEEDS_USER_ACTION",CheckinNoticePolicy.status(emptyList(),"permission_requires_user"))
        assertEquals("STOPPED",CheckinNoticePolicy.status(emptyList(),"user_stopped"))
        assertTrue(CheckinNoticePolicy.mayPublish(true,8,8,date,date,"ACTUAL_BLOCKED",false))
    }
    private fun inspect(activity:String="us.ana_pocket.ANAPocket.views.home.HomeActivity",labels:List<String> = listOf("広告視聴"),connected:Boolean=true,owns:Boolean=true,stop:Boolean=false,sameDate:Boolean=true)=
        BoundAdReviewPolicy.mayInspect(connected,sameDate,owns,stop,true,activity,labels)
    @Test fun retainedChallengeHomeAndAdEntryAllowOnlyInspection(){
        assertTrue(inspect(labels=listOf("チャレンジ","参加可能","広告視聴")))
        assertTrue(inspect(labels=listOf("ホーム","広告視聴")))
        assertTrue(inspect(labels=listOf("広告視聴","今日の視聴済み回数：5/25")))
    }
    @Test fun sdkReceiptUnknownPageMustNotNavigateAway(){
        assertFalse(inspect(activity="com.google.android.gms.ads.AdActivity"))
        assertFalse(inspect(labels=listOf("広告視聴","報酬をゲット！")))
        assertFalse(inspect(labels=listOf("unknown")))
    }
    @Test fun lostConnectionStopDateOrOwnerForbidsInspection(){
        assertFalse(inspect(connected=false));assertFalse(inspect(owns=false))
        assertFalse(inspect(stop=true));assertFalse(inspect(sameDate=false))
    }
    @Test fun unchangedCounterIsNotNoGainOrPermissionToReserve(){
        assertEquals("original_ad_surface_missing_counter_unchanged",BoundAdReviewPolicy.missingSurfaceReason(5,5))
        val a=AdAttempt("original",date,7,1793395251195,AdState.UNCONFIRMED,false,false,100,"ad_timeout_95s",5)
        assertFalse(OfficialAdQuota.canReserve(5,5,true,false,listOf(a)))
        assertFalse(OfficialAdRecoveryPolicy.confirms(a,AdReceipt(AdState.UNCONFIRMED,false,false,5)))
    }
    @Test fun counterAdvanceAloneStillNeedsSameAttemptReceipt(){
        assertEquals("original_ad_surface_missing_receipt_unproven",BoundAdReviewPolicy.missingSurfaceReason(5,6))
        assertEquals("counter_jump_ambiguous",BoundAdReviewPolicy.missingSurfaceReason(5,7))
        assertEquals("official_counter_regressed",BoundAdReviewPolicy.missingSurfaceReason(5,4))
        assertEquals("original_ad_surface_missing_counter_unavailable",BoundAdReviewPolicy.missingSurfaceReason(5,null))
    }
    @Test fun reasonSurvivesRoutineAndHomeSummary(){
        val message=TelegramText.reason("original_ad_surface_missing_counter_unchanged")
        assertTrue(message.contains("原广告界面"));assertTrue(message.contains("未重看"))
        assertTrue(TaskFailureText.short("官方5/25；"+message).contains("原广告界面"))
        assertTrue(TelegramText.reason("accessibility_disconnected").contains("无障碍"))
    }
    private class Port:RoutinePort {
        var connected=true;var stop=false;var afterChallenge:()->Unit={};val calls=mutableListOf<String>()
        override fun connected()=connected;override fun stopped()=stop;override fun sameDate()=true
        override suspend fun progress(stage:String,result:RoutineStepResult){}
        override suspend fun startup():RoutineStepResult {calls.add("login_review");return RoutineStepResult("DEFERRED","existing_unknown_login_kept")}
        override suspend fun challenges():RoutineStepResult {calls.add("skip_four_complete_long_distance_off");afterChallenge();return RoutineStepResult("ALREADY_DONE","coverage_proved")}
        override suspend fun ads():RoutineStepResult {calls.add("review_original7");return RoutineStepResult("BLOCKED","original_ad_surface_missing_counter_unchanged",false)}
    }
    @Test fun completedChallengesAndLoginUnknownReachActualUnfinishedStage()=runBlocking {
        val p=Port();val result=RoutineSequence(p).run()
        assertEquals(listOf("login_review","skip_four_complete_long_distance_off","review_original7"),p.calls)
        assertEquals("ALREADY_DONE",result.steps["CHALLENGES"]!!.state)
        assertEquals("BLOCKED",result.state);assertEquals("original_ad_surface_missing_counter_unchanged",result.detail)
    }
    @Test fun disconnectBeforeStartAndBetweenStagesIsFinite()=runBlocking {
        val first=Port();first.connected=false;assertEquals("accessibility_disconnected",RoutineSequence(first).run().detail);assertTrue(first.calls.isEmpty())
        val later=Port();later.afterChallenge={later.connected=false};assertEquals("accessibility_disconnected",RoutineSequence(later).run().detail);assertFalse(later.calls.contains("review_original7"))
    }
    @Test fun userStopDoesNotReachNextViewing()=runBlocking {
        val p=Port();p.afterChallenge={p.stop=true};assertEquals("STOPPED",RoutineSequence(p).run().state);assertFalse(p.calls.contains("review_original7"))
    }
}
