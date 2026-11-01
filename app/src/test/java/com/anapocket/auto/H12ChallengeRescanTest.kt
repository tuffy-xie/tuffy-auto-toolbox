package com.anapocket.auto
import org.junit.Assert.*
import org.junit.Test
class H12ChallengeRescanTest {
 @Test fun actualNovemberFirstDeadlineMustNotDisappear(){ assertTrue(DailyCardDeadline.matches("2026/11/1まで","2026-11-01")); assertTrue(DailyCardDeadline.matches("2026/11/01まで","2026-11-01")); assertTrue(DailyCardDeadline.matches("2026/1/1まで","2026-01-01")) }
 @Test fun deadlineStillRejectsOtherDayAndExtraText(){assertFalse(DailyCardDeadline.matches("2026/11/30まで","2026-11-01"));assertFalse(DailyCardDeadline.matches("週2026/11/1まで","2026-11-01"));assertFalse(DailyCardDeadline.matches(null,"2026-11-01"))}
 @Test fun sameDateCardIdentityStillRequiresExactAttendance(){assertTrue(ChallengeDetailReceipt.maxMatches("徒歩で500m移動しよう！","2026-11-01","徒歩で500m移動しよう！","2026/11/1","参加日：2026/11/1","ブースト5回",200));assertFalse(ChallengeDetailReceipt.maxMatches("x","2026-11-01","x","2026/11/1","2026/11/1","ブースト5回",200))}
 @Test fun emptyResultNeedsBothOfficialLists(){assertEquals(ChallengeScanStatus.SCAN_FAILED,ChallengeScanPolicy.finish(true,false,false,true,0,false));assertEquals(ChallengeScanStatus.NO_ELIGIBLE_TASK,ChallengeScanPolicy.finish(true,true,false,true,0,false))}
 @Test fun unscannedFailedAndBlockedCannotBecomeCompleted(){for(s in listOf(ChallengeScanStatus.NOT_SCANNED,ChallengeScanStatus.SCAN_FAILED,ChallengeScanStatus.BLOCKED,ChallengeScanStatus.SCANNING,ChallengeScanStatus.TASKS_AVAILABLE))assertFalse(ChallengeScanPolicy.reusable(s,40,40,true,false))}
 @Test fun scanMustEndAndStayOnRealBusinessDay(){assertEquals(ChallengeScanStatus.SCAN_FAILED,ChallengeScanPolicy.finish(false,true,false,true,4,false));assertEquals(ChallengeScanStatus.SCAN_FAILED,ChallengeScanPolicy.finish(true,true,true,true,4,false));assertEquals(ChallengeScanStatus.SCAN_FAILED,ChallengeScanPolicy.finish(true,true,false,false,4,false))}
 @Test fun pendingTasksKeepPartialStatus(){assertEquals(ChallengeScanStatus.PARTIALLY_COMPLETED,ChallengeScanPolicy.finish(true,true,false,true,4,true));assertFalse(ChallengeScanPolicy.reusable(ChallengeScanStatus.COMPLETED,5,5,true,true))}
 @Test fun currentKmChangesInvalidateDerivedScope(){assertFalse(ChallengeScanPolicy.reusable(ChallengeScanStatus.COMPLETED,50,40,true,false));assertTrue(ChallengeScanPolicy.reusable(ChallengeScanStatus.COMPLETED,40,40,true,false))}
 @Test fun dailyQuotaDoesNotEnterChallengeEligibility(){val c=Cards.fromTitle("徒歩で500m移動しよう！")!!;assertEquals(1,Cards.select(listOf(c),Cat.WALK,5000).matched.size);assertEquals(ChallengeScanStatus.COMPLETED,ChallengeScanPolicy.finish(true,true,false,true,1,false))}
 @Test fun resetIsForbiddenDuringUnknownOrSdk(){assertFalse(ChallengeScanPolicy.mayReset(true,false,false));assertFalse(ChallengeScanPolicy.mayReset(false,true,false));assertFalse(ChallengeScanPolicy.mayReset(false,false,true));assertTrue(ChallengeScanPolicy.mayReset(false,false,false))}
}
