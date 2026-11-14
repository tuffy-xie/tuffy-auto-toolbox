package com.anapocket.auto
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
class StartupAdSequenceTest{
 private class P(val frames:List<StartupAdFrame>):StartupAdPort{
  var time=0L;var index=0;var stops=false;var day=true;var closeCount=0;var observed=0;var changeInFrame=false
  override fun now()=time;override fun stopped()=stops;override fun sameDate()=day
  override suspend fun frame():StartupAdFrame{if(changeInFrame)day=false;return frames[index.coerceAtMost(frames.lastIndex)].also{index++}}
  var firstClose=-1L
  override suspend fun close(){if(closeCount==0)firstClose=time;closeCount++};override suspend fun observedAd(){observed++}
  override suspend fun pause(ms:Long){time+=ms}
 }
 @Test fun genuineAdCloseThenNewOfficialReceiptConfirms()=runBlocking{val p=P(List(125){StartupAdFrame(ad=true)}+StartupAdFrame(ad=true,close=true)+StartupAdFrame(receipt=true));assertEquals("REWARD_CONFIRMED",StartupAdSequence(p).run(true).state);assertEquals(1,p.closeCount);assertEquals(1,p.observed)}
 // 2026-11-10 00:03: Ogury's close was visible at once; tapping it 7 s in forfeited the 100pt login video.
 @Test fun rewardVideoPlainCloseIsHeldUntilAFullVideoCouldHavePlayed()=runBlocking{val p=P(List(130){StartupAdFrame(ad=true,close=true)}+StartupAdFrame(receipt=true));assertEquals("REWARD_CONFIRMED",StartupAdSequence(p).run(true).state);assertTrue(p.closeCount>=1);assertTrue(p.firstClose>=StartupAdSequence.REWARD_VIEW_MS)}
 // 11/12 00:03: Ogury closed at 35 s still forfeited the bonus, so a close seen 35-50 s in is still held.
 @Test fun ogury35sCloseIsStillHeld()=runBlocking{val p=P(List(66){StartupAdFrame(ad=true,close=true)}+StartupAdFrame(receipt=true));StartupAdSequence(p).run(true);assertEquals(0,p.closeCount)}
 @Test fun finishedUnconfirmedLoginMayRelaunchAnaButOpenOneMayNot(){
  assertTrue(StartupPolicy.mayFreshLaunchAfterFinishedLogin("2026-11-12","2026-11-12","UNCONFIRMED",1794409454076))
  assertFalse(StartupPolicy.mayFreshLaunchAfterFinishedLogin("2026-11-12","2026-11-12","UNCONFIRMED",0))
  assertFalse(StartupPolicy.mayFreshLaunchAfterFinishedLogin("2026-11-12","2026-11-11","UNCONFIRMED",1L))
  assertFalse(StartupPolicy.mayFreshLaunchAfterFinishedLogin("2026-11-12","2026-11-12","AD_PLAYING",1L))
  assertFalse(StartupPolicy.mayFreshLaunchAfterFinishedLogin("2026-11-12","2026-11-12","REWARD_CONFIRMED",1L))
 }
 @Test fun earlyPlainCloseNotTappedBeforeHold()=runBlocking{val p=P(List(9){StartupAdFrame(ad=true,close=true)}+StartupAdFrame(receipt=true));assertEquals("REWARD_CONFIRMED",StartupAdSequence(p).run(true).state);assertEquals(0,p.closeCount)}
 @Test fun storeSheetAndPartsAndStartupDismissalAreNotHeld()=runBlocking{
  val s=P(listOf(StartupAdFrame(ad=true),StartupAdFrame(ad=true,store=true,close=true),StartupAdFrame(receipt=true)));StartupAdSequence(s).run(true);assertEquals(1,s.closeCount)
  val part=P(listOf(StartupAdFrame(ad=true,close=true,part=AdPart(1,3)),StartupAdFrame(receipt=true)));StartupAdSequence(part).run(true);assertEquals(1,part.closeCount)
  val nav=P(listOf(StartupAdFrame(ad=true,close=true),StartupAdFrame(normal=true)));StartupAdSequence(nav).run(false);assertEquals(1,nav.closeCount)}
 @Test fun closeAndHomeWithoutReceiptRemainUnknown()=runBlocking{val p=P(listOf(StartupAdFrame(ad=true,close=true),StartupAdFrame(normal=true)));val r=StartupAdSequence(p).run(true);assertEquals("UNCONFIRMED",r.state);assertEquals("login_reward_not_confirmed",r.reason)}
 @Test fun preexistingReceiptNeverAttributedToNewAd()=runBlocking{val p=P(listOf(StartupAdFrame(receipt=true)));assertEquals("UNCONFIRMED",StartupAdSequence(p).run(true).state);assertEquals(0,p.observed);assertEquals(0,p.closeCount)}
 @Test fun startupCloseReturnsNavigationDismissalOnly()=runBlocking{val p=P(listOf(StartupAdFrame(ad=true,close=true),StartupAdFrame(normal=true)));assertEquals("DISMISSED",StartupAdSequence(p).run(false).state)}
 @Test fun returnToLoginPopupMayFollowStartupAd()=runBlocking{val p=P(listOf(StartupAdFrame(ad=true,close=true),StartupAdFrame(login=true)));assertEquals("DISMISSED",StartupAdSequence(p).run(false).state)}
 @Test fun loginCloseButtonNeverClickedAsSdkX()=runBlocking{val p=P(listOf(StartupAdFrame(login=true,close=true)));StartupAdSequence(p).run(true);assertEquals(0,p.closeCount)}
 @Test fun boundedSdkCloseAttempts()=runBlocking{val p=P(listOf(StartupAdFrame(ad=true,close=true)));assertEquals("UNCONFIRMED",StartupAdSequence(p).run(true).state);assertEquals(3,p.closeCount);assertTrue(p.time<=300750)}
 @Test fun storeAndSdkBudgetsSeparate()=runBlocking{val p=P(List(9){StartupAdFrame(store=true,close=true)}+List(140){StartupAdFrame(ad=true,close=true)}+listOf(StartupAdFrame(receipt=true)));assertEquals("REWARD_CONFIRMED",StartupAdSequence(p).run(true).state);assertEquals(5,p.closeCount)}
 @Test fun explicitPartsResetOnlyTheirBudgets()=runBlocking{val p=P(List(12){StartupAdFrame(ad=true,close=true,part=AdPart(1,2))}+List(12){StartupAdFrame(ad=true,close=true,part=AdPart(2,2))}+listOf(StartupAdFrame(receipt=true)));assertEquals("REWARD_CONFIRMED",StartupAdSequence(p).run(true).state);assertEquals(6,p.closeCount)}
 @Test fun stopPreservesUnknownWithoutClosing()=runBlocking{val p=P(listOf(StartupAdFrame(ad=true,close=true)));p.stops=true;assertEquals("user_stopped",StartupAdSequence(p).run(true).reason);assertEquals(0,p.closeCount)}
 @Test fun dateChangeInsideFrameCannotConfirmOldDay()=runBlocking{val p=P(listOf(StartupAdFrame(ad=true,receipt=true)));p.changeInFrame=true;assertEquals("UNCONFIRMED",StartupAdSequence(p).run(true).state)}
 @Test fun explicitErrorStopsPromptly()=runBlocking{val p=P(listOf(StartupAdFrame(error=true)));assertEquals("FAILED",StartupAdSequence(p).run(true).state);assertEquals(0,p.closeCount)}
}
