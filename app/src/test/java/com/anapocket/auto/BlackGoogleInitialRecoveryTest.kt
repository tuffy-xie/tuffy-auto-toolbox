package com.anapocket.auto
import org.junit.Assert.*
import org.junit.Test

class BlackGoogleInitialRecoveryTest {
 private val day="2026-11-05";private val start=1793814761375L
 private val original=PendingInitialJoin(day,"WALK|徒歩で1km移動しよう！","CH-original",start,1,1,true,false,0,"UNCONFIRMED","UNCONFIRMED",false,true,"ad_timeout_95s","DISPATCHED")
 private val steps=listOf(50,50,50,100,100)
 private val labels=listOf("チャレンジをはじめました！","1回目のブーストをしますか？","現在のチャレンジ達成後の報酬：50pt")+steps.map{"+$it"}
 private fun live(a:PendingInitialJoin=original,task:String=a.runId,run:String=a.runId,back:String=run,date:String=day,
   installed:Long=start-1,owner:Boolean=true,requested:Boolean=true,cold:Boolean=false)=
   NativeChallengeReceiptPolicy.liveBlankInitial(a,task,run,back,date,installed,owner,requested,cold)
 @Test fun originalLiveReturnRequiresTheSameDateRunDispatchInstallAndOwner(){
  assertTrue(live())
  for(a in listOf(original.copy(dispatchPhase="ARMED"),original.copy(departed=false),original.copy(initial=false),original.copy(attempts=2)))assertFalse(live(a))
  assertFalse(live(task="CH-other"));assertFalse(live(run="CH-restarted"));assertFalse(live(back="CH-other"))
  assertFalse(live(date="2026-11-06"));assertFalse(live(installed=start+1));assertFalse(live(owner=false));assertFalse(live(requested=false));assertFalse(live(cold=true))
 }
 @Test fun directBackIsNotAReceiptAndCannotConfirmWithoutOriginalStartOrFreshOfficialPanel(){
  var writes=0
  assertFalse(NativeChallengeReceiptPolicy.commitInitialOnce(original,original,day,1,false,false,false,true,50,steps,labels,liveIdentity=live()){writes++})
  assertFalse(NativeChallengeReceiptPolicy.commitInitialOnce(original,original,day,1,true,false,false,false,50,steps,labels,liveIdentity=live()){writes++})
  assertFalse(NativeChallengeReceiptPolicy.commitInitialOnce(original,original,day,1,true,false,false,true,50,steps,emptyList(),liveIdentity=live()){writes++})
  assertEquals(0,writes);assertFalse(original.confirmed)
 }
 @Test fun officialReceiptConfirmsOnlyOriginalAttemptAndAnIdempotentReviewNeverWritesTwice(){
  var row=original;var writes=0
  assertTrue(NativeChallengeReceiptPolicy.commitInitialOnce(original,row,day,1,true,false,false,true,50,steps,labels,liveIdentity=live()){row=row.copy(joined=true,confirmed=true,taskState="JOINED",adState="REWARD_CONFIRMED");writes++})
  assertEquals(original.runId,row.runId);assertEquals(start,row.startedAt);assertEquals(1,row.index);assertEquals(1,row.attempts)
  assertTrue(NativeChallengeReceiptPolicy.commitInitialOnce(original,row,day,1,true,false,false,true,50,steps,labels,liveIdentity=true){fail("duplicate confirmation")})
  assertEquals(1,writes)
 }
 @Test fun isolatedOldUnknownRemainsUntouchedAndDoesNotHideTheOnlyLiveInitialReview(){
  val old=original.copy(key="WALK|徒歩で500m移動しよう！",initial=false,joined=true,index=2,attempts=2)
  val candidates=listOf(old to true,original to false).filter{NativeChallengeReceiptPolicy.pendingForReview(it.first.taskState,it.second)}
  assertEquals(listOf(original),candidates.map{it.first});assertFalse(old.confirmed);assertEquals("UNCONFIRMED",old.adState)
  val actualTwo=listOf(old to false,original to false).count{NativeChallengeReceiptPolicy.pendingForReview(it.first.taskState,it.second)}
  assertFalse(NativeChallengeReceiptPolicy.commitInitialOnce(original,original,day,actualTwo,true,false,false,true,50,steps,labels,liveIdentity=live()){fail("two unisolated candidates")})
 }
}
