package com.anapocket.auto

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class RoutineResumeRegressionTest {
    private fun reuse(day:Boolean=true,today:Boolean=true,scan:Boolean=true,rows:Boolean=true,pending:Boolean=false)=
        RoutinePolicy.reuseChallenges(day,today,scan&&rows,pending)
    @Test fun completedDayWithOnlyDailyUnknownDoesNotRequireChallengeUI()=runBlocking {
        var challengeUICalls=0;var adsCalls=0;val savedReceipts=mutableListOf("confirmed-initial","confirmed-boost5","daily-unknown")
        val before=savedReceipts.toList();val events=mutableListOf<String>()
        val p=object:RoutinePort {
            override fun stopped()=false;override fun sameDate()=true
            override suspend fun progress(stage:String,result:RoutineStepResult){events.add(stage+":"+result.state)}
            override suspend fun startup()=RoutineStepResult("DEFERRED","original daily receipt first")
            override suspend fun challenges():RoutineStepResult {
                if(reuse())return RoutineStepResult("ALREADY_DONE","saved complete collection")
                challengeUICalls++;return RoutineStepResult("DONE","scanned")
            }
            override suspend fun ads():RoutineStepResult {adsCalls++;return RoutineStepResult("BLOCKED",TelegramText.reason("recorded_daily_receipt_counter_unconfirmed_no_replay"),false)}
        }
        val result=RoutineSequence(p).run()
        assertEquals(0,challengeUICalls);assertEquals(1,adsCalls);assertEquals("ALREADY_DONE",result.steps["CHALLENGES"]!!.state)
        assertEquals("BLOCKED",result.state);assertEquals("ADS:BLOCKED",events.last());assertEquals(before,savedReceipts)
        assertTrue(TaskFailureText.short(result.detail).contains("已停止且未重播"));assertFalse(result.detail.contains("执行中止"))
    }
    @Test fun completeCategoryProofSurvivesAnUnrelatedAggregateBlock(){assertTrue(reuse());assertFalse(reuse(day=false));assertFalse(reuse(scan=false));assertFalse(reuse(rows=false))}
    @Test fun afterIsolationVerifiedScopeStaysPartialAndOtherTasksContinue()=runBlocking {
        val before=listOf("done","isolated","pending");val proof=mapOf("done" to "ALREADY_DONE","isolated" to "PARTIALLY_COMPLETED")
        assertEquals(listOf("pending"),before.filterNot{reuse(rows=proof[it]!=null)})
        assertEquals(before,before.filterNot{reuse(rows=proof[it]!=null,pending=true)})
        var adsCalls=0
        val port=object:RoutinePort {
            override fun stopped()=false;override fun sameDate()=true
            override suspend fun progress(stage:String,result:RoutineStepResult){}
            override suspend fun startup()=RoutineStepResult("DEFERRED","")
            override suspend fun challenges()=RoutineStepResult("PARTIALLY_COMPLETED","original UNKNOWN retained")
            override suspend fun ads():RoutineStepResult {adsCalls++;return RoutineStepResult("LIMIT_REACHED","25/25")}
        }
        val result=RoutineSequence(port).run();assertEquals(1,adsCalls);assertEquals("PARTIALLY_COMPLETED",result.state)
        assertEquals("PARTIALLY_COMPLETED",result.steps["CHALLENGES"]!!.state)
    }
    @Test fun resetChangedRangeIncompleteScanAndTomorrowSelectionInvalidateReuse(){assertFalse(reuse(scan=false));assertFalse(reuse(today=false));assertFalse(reuse(rows=false))}
    @Test fun currentChallengeUnknownAndPendingMaxDialogCannotBeSkipped(){assertFalse(reuse(pending=true));assertTrue(reuse())}
    @Test fun onlyPriorOfficialCappedDayWithNoPendingAdsCanSkipAdController(){assertTrue(RoutinePolicy.reuseAds(true,"LIMIT_REACHED",25,false));assertTrue(RoutinePolicy.reuseAds(true,"ALREADY_DONE",25,false));assertFalse(RoutinePolicy.reuseAds(false,"DONE",25,false));assertFalse(RoutinePolicy.reuseAds(true,"DONE",24,false));assertFalse(RoutinePolicy.reuseAds(true,"DONE",25,true));assertFalse(RoutinePolicy.reuseAds(true,null,25,false))}
    @Test fun wrappedDailyReceiptFailureKeepsSpecificHomeAndBotReason(){val reason=TelegramText.reason("recorded_daily_receipt_counter_unconfirmed_no_replay");val wrapped="官方 1/25 · 本机确认1；"+reason;assertTrue(reason.contains("UNKNOWN"));assertTrue(TaskFailureText.short(wrapped).contains("原观看"));assertTrue(TaskFailureText.short(wrapped).contains("已停止"))}
}
