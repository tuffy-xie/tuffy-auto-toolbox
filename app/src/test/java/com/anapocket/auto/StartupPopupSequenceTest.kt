package com.anapocket.auto
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
class StartupPopupSequenceTest {
 private class P(val frames:List<StartupAdFrame>):StartupAdPort{
  var time=0L;var n=0;var dismissed=0;var closes=0;var ads=0;var popupOk=true
  override fun now()=time;override fun stopped()=false;override fun sameDate()=true
  override suspend fun frame()=frames[minOf(n++,frames.lastIndex)]
  override suspend fun close(){closes++};override suspend fun observedAd(){ads++}
  override suspend fun dismissPopup():Boolean{dismissed++;return popupOk};override suspend fun pause(ms:Long){time+=ms}
 }
 @Test fun realBadgeStyleBlockerCanBeDismissedThenFreshReceiptConfirms()=runBlocking{val p=P(listOf(StartupAdFrame(ad=true),StartupAdFrame(popup=true),StartupAdFrame(receipt=true)));assertEquals("REWARD_CONFIRMED",StartupAdSequence(p).run(true).state);assertEquals(1,p.dismissed);assertEquals(0,p.closes)}
 @Test fun popupClosureWithoutReceiptNeverCountsAsCompletion()=runBlocking{val p=P(listOf(StartupAdFrame(ad=true),StartupAdFrame(popup=true),StartupAdFrame(normal=true)));assertEquals("UNCONFIRMED",StartupAdSequence(p).run(true).state);assertEquals(1,p.dismissed)}
 @Test fun nativePopupCannotBeMistakenForAnAdDeparture()=runBlocking{val p=P(listOf(StartupAdFrame(ad=true,popup=true),StartupAdFrame(receipt=true)));assertEquals("UNCONFIRMED",StartupAdSequence(p).run(true).state);assertEquals(0,p.ads)}
 @Test fun repetitiveNativePopupsAreBounded()=runBlocking{val p=P(listOf(StartupAdFrame(popup=true)));assertEquals("UNCONFIRMED",StartupAdSequence(p).run(true).state);assertEquals(6,p.dismissed)}
 @Test fun officialErrorStopsWithoutDismissingTheError()=runBlocking{val p=P(listOf(StartupAdFrame(error=true,popup=true)));assertEquals("FAILED",StartupAdSequence(p).run(true).state);assertEquals(0,p.dismissed)}
 @Test fun unclickablePopupStopsWithoutRepeatedClicks()=runBlocking{val p=P(listOf(StartupAdFrame(popup=true)));p.popupOk=false;assertEquals("UNCONFIRMED",StartupAdSequence(p).run(true).state);assertEquals(1,p.dismissed)}
}
