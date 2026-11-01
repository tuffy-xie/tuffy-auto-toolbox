package com.anapocket.auto
import org.junit.Assert.*
import org.junit.Test
class PlayStoreReturnPolicyTest {
    private val actual=listOf("想要随时了解最新动态\n吗？","我们会在有新的特惠和动态时通知您。","立即启用","以后再说")
    @Test fun observedIntroCanBeDismissedOnlyInBoundStoreReturn(){assertTrue(PlayStoreReturnPolicy.mayBackFromIntro("com.android.vending",actual,true,0));assertFalse(PlayStoreReturnPolicy.mayBackFromIntro("com.android.vending",actual,false,0));assertFalse(PlayStoreReturnPolicy.mayBackFromIntro("com.android.vending",actual,true,2))}
    @Test fun otherPackagesOrGenericNotificationsNeverAuthorizeActions(){assertFalse(PlayStoreReturnPolicy.notificationIntro("us.ana_pocket.ANAPocket",actual));assertFalse(PlayStoreReturnPolicy.notificationIntro("com.android.vending",listOf("立即启用","以后再说")))}
    @Test fun introIsNotACompletionOrAStorePurchase(){assertFalse(AdSignals.receipt(actual));assertFalse(AdSignals.playSheet("com.android.vending",actual));assertFalse(AdSignals.playHome("com.android.vending",actual))}
}
