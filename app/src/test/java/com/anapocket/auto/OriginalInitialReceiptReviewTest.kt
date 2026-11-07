package com.anapocket.auto
import org.junit.Assert.*
import org.junit.Test
class OriginalInitialReceiptReviewTest {
 private val d="2026-11-04";private val start=1793718980474L;private val install=start+90_000
 private val a=PendingInitialJoin(d,"WALK|徒歩で3km移動しよう！","CH-original",start,1,1,true,false,0,"UNCONFIRMED","UNCONFIRMED",false,true,"ad_timeout_95s","DISPATCHED")
 private val r=InitialReceiptReview(d,a.key,a.runId,start,1,start+100_000,"cc50b558d0c849f37dde9fe7225867f7e194475808ea661c66311c21e1e0a5b0",install)
 private fun valid(x:PendingInitialJoin=a,p:InitialReceiptReview=r,day:String=d,now:Long=start+101_000,version:Long=install)=NativeChallengeReceiptPolicy.reviewedIdentity(x,p,day,now,version)
 @Test fun reviewPinsOriginalTaskDateRunIndexStartAndInstall(){assertTrue(valid());assertTrue(NativeChallengeReceiptPolicy.reviewableInitial(a,d))}
 @Test fun changedIdentityCannotReuseReview(){for(p in listOf(r.copy(key="other"),r.copy(runId="CH-other"),r.copy(startedAt=1),r.copy(index=2),r.copy(date="2026-11-03")))assertFalse(valid(p=p))}
 @Test fun expiredFutureCrossDateAndPackageReplacementInvalidateReview(){assertFalse(valid(now=r.reviewedAt-1));assertFalse(valid(now=r.reviewedAt+600_001));assertFalse(valid(day="2026-11-05"));assertFalse(valid(version=install+1));assertFalse(valid(p=r.copy(evidenceHash="missing")))}
 @Test fun reservedArmedUnknownDepartureAndMultipleAttemptsCannotReview(){for(x in listOf(a.copy(dispatchPhase="ARMED"),a.copy(dispatchPhase="RESERVED"),a.copy(departed=false),a.copy(attempts=2),a.copy(initial=false)))assertFalse(valid(x));assertFalse(NativeChallengeReceiptPolicy.reviewableInitial(a.copy(confirmed=true),d))}
 private val steps=listOf(50,50,50,100,100)
 private val labels=listOf("チャレンジをはじめました！","1回目のブーストをしますか？","現在のチャレンジ達成後の報酬：100pt")+steps.map{"+$it"}
 @Test fun reviewAloneCannotConfirmWithoutOriginalStartAndFreshOfficialReceipt(){var writes=0;for(native in listOf(false,true))assertFalse(NativeChallengeReceiptPolicy.commitInitialOnce(a,a,d,1,false,false,false,native,100,steps,labels,operatorIdentity=valid()){writes++});assertFalse(NativeChallengeReceiptPolicy.commitInitialOnce(a,a,d,1,true,false,false,false,100,steps,labels,operatorIdentity=valid()){writes++});assertEquals(0,writes)}
 @Test fun reviewedOriginalAndNativeReceiptUseSameCommitAndRemainIdempotent(){var writes=0;assertTrue(NativeChallengeReceiptPolicy.commitInitialOnce(a,a,d,1,true,false,false,true,100,steps,labels,operatorIdentity=valid()){writes++});val saved=a.copy(joined=true,confirmed=true,boost=0,taskState="JOINED",adState="REWARD_CONFIRMED");assertTrue(valid(saved));assertTrue(NativeChallengeReceiptPolicy.commitInitialOnce(a,saved,d,1,true,false,false,true,100,steps,labels,operatorIdentity=valid(saved)){writes++});assertEquals(1,writes)}
 @Test fun failedCommitCannotGrantContinuation(){var continued=false;try{continued=NativeChallengeReceiptPolicy.commitInitialOnce(a,a,d,1,true,false,false,true,100,steps,labels,operatorIdentity=valid()){throw IllegalStateException("commit failed")};fail()}catch(_:IllegalStateException){};assertFalse(continued);assertFalse(a.confirmed)}
}
