package com.anapocket.auto
import org.junit.Assert.*
import org.junit.Test
class IndependentDailyIsolationTest {
 @Test fun safelyIsolatedCarDoesNotWaitForUnscannedMovementCategory(){
  assertTrue(PendingInitialJoinPolicy.independentDailyAllowed(listOf(true),true))
  assertFalse(PendingInitialJoinPolicy.blocksDailyNavigation(true,PendingInitialJoinPolicy.independentDailyAllowed(listOf(true),true)))
 }
 @Test fun oneUnisolatedUnknownOrDifferentDateStillBlocksBeforeDispatch(){
  for(proof in listOf(listOf(false),listOf(true,false),emptyList()))assertFalse(PendingInitialJoinPolicy.independentDailyAllowed(proof,true))
  assertFalse(PendingInitialJoinPolicy.independentDailyAllowed(listOf(true),false))
 }
 @Test fun newBoostModalAndSdkCannotBeUsedForTheReadOnlyIsolationReview(){
  assertFalse(SubmittedUnknownIsolationPolicy.cleanNativePage("us.ana_pocket.ANAPocket","us.ana_pocket.ANAPocket.views.home.HomeActivity",setOf("us.ana_pocket.ANAPocket:id/title_count"),listOf("チャレンジ","3回目のブーストをしますか？"),false))
  assertFalse(SubmittedUnknownIsolationPolicy.cleanNativePage("us.ana_pocket.ANAPocket","com.applovin.adview.AppLovinFullscreenActivity",emptySet(),listOf("参加中"),true))
 }
}
