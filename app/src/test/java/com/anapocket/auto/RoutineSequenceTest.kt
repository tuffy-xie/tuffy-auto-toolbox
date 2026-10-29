package com.anapocket.auto
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
class RoutineSequenceTest{
 private class P:RoutinePort{
  val calls=mutableListOf<String>();val events=mutableListOf<String>();var stop=false;var day=true
  var afterStartup:()->Unit={};var afterChallenge:()->Unit={};var afterAds:()->Unit={}
  var a=RoutineStepResult("NOT_PRESENT","not_seen");var c=RoutineStepResult("DONE","four_max");var d=RoutineStepResult("LIMIT_REACHED","25starts")
  override fun stopped()=stop;override fun sameDate()=day
  override suspend fun progress(stage:String,result:RoutineStepResult){events.add(stage+":"+result.state)}
  override suspend fun startup():RoutineStepResult{calls.add("startup");afterStartup();return a}
  override suspend fun challenges():RoutineStepResult{calls.add("challenges");afterChallenge();return c}
  override suspend fun ads():RoutineStepResult{calls.add("ads");afterAds();return d}
 }
 @Test fun unifiedSequenceUsesRequiredOrder()=runBlocking{val p=P();val r=RoutineSequence(p).run();assertEquals(listOf("startup","challenges","ads"),p.calls);assertEquals("DONE",r.state);assertEquals(listOf("STARTUP","CHALLENGES","ADS"),r.steps.keys.toList())}
 @Test fun loginUnknownStopsBeforeAnyChallengeOrDailyAd()=runBlocking{val p=P();p.a=RoutineStepResult("UNCONFIRMED","receipt_missing",false);assertEquals("BLOCKED",RoutineSequence(p).run().state);assertEquals(listOf("startup"),p.calls)}
 @Test fun challengeUnknownNeverNavigatesToDailyAd()=runBlocking{val p=P();p.c=RoutineStepResult("BLOCKED","unknown",false);assertEquals("BLOCKED",RoutineSequence(p).run().state);assertEquals(listOf("startup","challenges"),p.calls)}
 @Test fun userStopBetweenStagesPreventsNewViewings()=runBlocking{val p=P();p.afterStartup={p.stop=true};assertEquals("STOPPED",RoutineSequence(p).run().state);assertEquals(listOf("startup"),p.calls)}
 @Test fun phoneDateRolloverStopsBeforeNextModule()=runBlocking{val p=P();p.afterChallenge={p.day=false};assertEquals("BLOCKED",RoutineSequence(p).run().state);assertEquals(listOf("startup","challenges"),p.calls)}
 @Test fun blockedBeforeStartMakesNoCalls()=runBlocking{val p=P();p.stop=true;assertEquals("STOPPED",RoutineSequence(p).run().state);assertTrue(p.calls.isEmpty())}
 @Test fun skippedOrAlreadyCappedStagesDoNotClaim25Rewards()=runBlocking{val p=P();p.d=RoutineStepResult("LIMIT_REACHED","25starts21complete4unknown");val r=RoutineSequence(p).run();assertEquals("NOT_PRESENT",r.steps["STARTUP"]!!.state);assertEquals("25starts21complete4unknown",r.steps["ADS"]!!.detail)}
 @Test fun progressPersistsBeforeAndAfterEachStage()=runBlocking{val p=P();RoutineSequence(p).run();assertEquals(listOf("STARTUP:RUNNING","STARTUP:NOT_PRESENT","CHALLENGES:RUNNING","CHALLENGES:DONE","ADS:RUNNING","ADS:LIMIT_REACHED"),p.events)}
 @Test fun stopDuringFinalStageIsNeverReportedDone()=runBlocking{val p=P();p.afterAds={p.stop=true};assertEquals("STOPPED",RoutineSequence(p).run().state)}
 @Test fun dateChangesDuringFinalStageCannotFinishOldDay()=runBlocking{val p=P();p.afterAds={p.day=false};assertEquals("BLOCKED",RoutineSequence(p).run().state)}
 @Test fun cappedRunWithNewUnknownRequiresReview(){assertFalse(RoutineAdCompletion.allowed(true,"",listOf(false to false)))}
 @Test fun preexistingCapWithNoNewStartsCanFinish(){assertTrue(RoutineAdCompletion.allowed(true,"",emptyList()))}
 @Test fun freshReceiptsCompleteAndNoStartsWithoutCapDoNot(){assertTrue(RoutineAdCompletion.allowed(false,"",listOf(true to true)));assertFalse(RoutineAdCompletion.allowed(false,"",emptyList()));assertFalse(RoutineAdCompletion.allowed(false,"network",listOf(true to true)))}
}
