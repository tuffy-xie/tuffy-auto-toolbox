package com.anapocket.auto

import org.junit.Assert.*
import org.junit.Test

/** 11/22: the approved once-a-day login press runs on the same dialog instead of being lost to the ANA restart. */
class LoginRepressInPlaceTest {
    private val offer="login_reward_not_confirmed_after_offer_return"
    @Test fun offerStillShownAfterTheFirstVideoIsPressedAtOnce() {
        // Real 11/22 00:05: Google ad watched and closed, the dialog still offered 「動画を見てさらに100ptをGET」.
        assertTrue(StartupPolicy.repressInPlace(offer,0,true))
        // The loop then finds the same dialog, where the 11/14 rule allows the one press.
        assertTrue(StartupPolicy.mayRepressUnrewardedLogin("2026-11-22","2026-11-22","UNCONFIRMED",offer,true,0,false))
    }
    @Test fun secondReturnWithTheOfferFallsBackToTheRestartAndNeverAThirdVideo() {
        assertFalse(StartupPolicy.repressInPlace(offer,1,true))
        assertFalse(StartupPolicy.mayRepressUnrewardedLogin("2026-11-22","2026-11-22","UNCONFIRMED",offer,true,1,false))
        assertTrue(StalledAdRestartPolicy.loginOfferRestart("2026-11-22","2026-11-22","STARTUP","UNCONFIRMED",offer,false,false,0,true,true,false))
    }
    @Test fun otherOutcomesAreNeverPressedAgain() {
        // 11/21: no offer on return (plain not-confirmed), a timeout, or an ad that was never seen.
        assertFalse(StartupPolicy.repressInPlace("login_reward_not_confirmed",0,true))
        assertFalse(StartupPolicy.repressInPlace("startup_ad_timeout_300s",0,true))
        assertFalse(StartupPolicy.repressInPlace(offer,0,false))
        assertFalse(StartupPolicy.repressInPlace(null,0,true))
    }
}
