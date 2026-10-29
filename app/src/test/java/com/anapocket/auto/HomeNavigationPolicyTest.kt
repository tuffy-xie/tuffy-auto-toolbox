package com.anapocket.auto
import org.junit.Assert.*
import org.junit.Test
class HomeNavigationPolicyTest {
 @Test fun actualJoinedDetailAfterBoostIsANavigationBoundary(){assertTrue(HomeNavigationPolicy.nativeDetail(StartupPolicy.ANA,listOf("新幹線で100km移動しよう！","ブースト5回"),setOf("design_bottom_sheet","text_name","text_date","close","text_attended","boost_count_text")))}
 @Test fun badgeAndSdkCannotPassAsNativeDetail(){val ids=setOf("design_bottom_sheet","text_name","text_date","close");assertFalse(HomeNavigationPolicy.nativeDetail("com.android.vending",listOf("新幹線で100km移動しよう！"),ids));assertFalse(HomeNavigationPolicy.nativeDetail(StartupPolicy.ANA,listOf("バッジを獲得しました！"),ids+"image_close"));assertFalse(HomeNavigationPolicy.nativeDetail(StartupPolicy.ANA,listOf("新幹線で100km移動しよう！","報酬をゲット！"),ids))}
 @Test fun actualHomeMustGoToEarnInsteadOfBackExitingApp(){assertTrue(HomeNavigationPolicy.nativeHome(StartupPolicy.ANA,listOf("ホーム","ためる","ガチャ","おトク","移動記録")))}
 @Test fun otherAppsOrPartialLabelsAreNotAnaHome(){assertFalse(HomeNavigationPolicy.nativeHome("com.miui.home",listOf("ホーム","ためる")));assertFalse(HomeNavigationPolicy.nativeHome(StartupPolicy.ANA,listOf("ホーム")))}
 @Test fun rewardOverlayIsNeverDismissedAsPlainHome(){assertFalse(HomeNavigationPolicy.nativeHome(StartupPolicy.ANA,listOf("ホーム","ためる","報酬をゲット！")))}
}
