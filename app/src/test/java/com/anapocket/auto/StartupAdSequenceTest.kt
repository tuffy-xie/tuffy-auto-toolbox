package com.anapocket.auto
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
class StartupAdSequenceTest{
 private class P(val frames:List<StartupAdFrame>):StartupAdPort{
  var time=0L;var index=0;var stops=false;var day=true;var closeCount=0;var observed=0;var changeInFrame=false
  override fun now()=time;override fun stopped()=stops;override fun sameDate()=day
  override suspend fun frame():StartupAdFrame{if(changeInFrame)day=false;return frames[index.coerceAtMost(frames.lastIndex)].also{index++}}
  override suspend fun close(){closeCount++};override suspend fun observedAd(){observed++}
  override suspend fun pause(ms:Long){time+=ms}
 }
 @Test fun genuineAdCloseThenNewOfficialReceiptConfirms()=runBlocking{val p=P(listOf(StartupAdFrame(ad=true),StartupAdFrame(ad=true,close=true),StartupAdFrame(receipt=true)));assertEquals("REWARD_CONFIRMED",StartupAdSequence(p).run(true).state);assertEquals(1,p.closeCount);assertEquals(1,p.observed)}
 @Test fun closeAndHomeWithoutReceiptRemainUnknown()=runBlocking{val p=P(listOf(StartupAdFrame(ad=true,close=true),StartupAdFrame(normal=true)));val r=StartupAdSequence(p).run(true);assertEquals("UNCONFIRMED",r.state);assertEquals("login_reward_not_confirmed",r.reason)}
 @Test fun preexistingReceiptNeverAttributedToNewAd()=runBlocking{val p=P(listOf(StartupAdFrame(receipt=true)));assertEquals("UNCONFIRMED",StartupAdSequence(p).run(true).state);assertEquals(0,p.observed);assertEquals(0,p.closeCount)}
 @Test fun startupCloseReturnsNavigationDismissalOnly()=runBlocking{val p=P(listOf(StartupAdFrame(ad=true,close=true),StartupAdFrame(normal=true)));assertEquals("DISMISSED",StartupAdSequence(p).run(false).state)}
 @Test fun returnToLoginPopupMayFollowStartupAd()=runBlocking{val p=P(listOf(StartupAdFrame(ad=true,close=true),StartupAdFrame(login=true)));assertEquals("DISMISSED",StartupAdSequence(p).run(false).state)}
 @Test fun loginCloseButtonNeverClickedAsSdkX()=runBlocking{val p=P(listOf(StartupAdFrame(login=true,close=true)));StartupAdSequence(p).run(true);assertEquals(0,p.closeCount)}
 @Test fun boundedSdkCloseAttempts()=runBlocking{val p=P(listOf(StartupAdFrame(ad=true,close=true)));assertEquals("UNCONFIRMED",StartupAdSequence(p).run(true).state);assertEquals(3,p.closeCount);assertTrue(p.time<=300750)}
 @Test fun storeAndSdkBudgetsSeparate()=runBlocking{val p=P(List(9){StartupAdFrame(store=true,close=true)}+List(12){StartupAdFrame(ad=true,close=true)}+listOf(StartupAdFrame(receipt=true)));assertEquals("REWARD_CONFIRMED",StartupAdSequence(p).run(true).state);assertEquals(5,p.closeCount)}
 @Test fun explicitPartsResetOnlyTheirBudgets()=runBlocking{val p=P(List(12){StartupAdFrame(ad=true,close=true,part=AdPart(1,2))}+List(12){StartupAdFrame(ad=true,close=true,part=AdPart(2,2))}+listOf(StartupAdFrame(receipt=true)));assertEquals("REWARD_CONFIRMED",StartupAdSequence(p).run(true).state);assertEquals(6,p.closeCount)}
 @Test fun stopPreservesUnknownWithoutClosing()=runBlocking{val p=P(listOf(StartupAdFrame(ad=true,close=true)));p.stops=true;assertEquals("user_stopped",StartupAdSequence(p).run(true).reason);assertEquals(0,p.closeCount)}
 @Test fun dateChangeInsideFrameCannotConfirmOldDay()=runBlocking{val p=P(listOf(StartupAdFrame(ad=true,receipt=true)));p.changeInFrame=true;assertEquals("UNCONFIRMED",StartupAdSequence(p).run(true).state)}
 @Test fun explicitErrorStopsPromptly()=runBlocking{val p=P(listOf(StartupAdFrame(error=true)));assertEquals("FAILED",StartupAdSequence(p).run(true).state);assertEquals(0,p.closeCount)}
}
