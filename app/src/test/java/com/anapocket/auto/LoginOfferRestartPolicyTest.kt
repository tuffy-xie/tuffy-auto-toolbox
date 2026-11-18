package com.anapocket.auto

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 11/18 00:05 RTb6b6fba0: Five login video closed, 「動画を見てさらに100ptGET」 still shown, run stopped until 00:15. */
class LoginOfferRestartPolicyTest {
    private val d = "2026-11-18"
    private fun ok(
        date: String = d, recorded: String = d, stage: String = "STARTUP", state: String = "UNCONFIRMED",
        reason: String = StalledAdRestartPolicy.LOGIN_OFFER_RETURN, confirmed: Boolean = false, used: Boolean = false,
        restarts: Int = 0, live: Boolean = true, ana: Boolean = true, sdk: Boolean = false,
    ) = StalledAdRestartPolicy.loginOfferRestart(date, recorded, stage, state, reason, confirmed, used, restarts, live, ana, sdk)

    @Test fun theObservedOfferReturnRestartsAnaOnce() {
        assertTrue(ok())
        assertFalse("one restart per login attempt", ok(used = true))
        assertFalse(ok(restarts = StalledAdRestartPolicy.OPEN_AD_RESTART_LIMIT))
    }

    @Test fun otherLoginOutcomesKeepTheirExistingPaths() {
        assertFalse(ok(reason = "login_reward_not_confirmed"))
        assertFalse(ok(reason = StartupAdSequence.NEVER_OPENED))
        assertFalse(ok(reason = "startup_ad_timeout_300s"))
        assertFalse(ok(state = "REWARD_CONFIRMED"))
        assertFalse(ok(state = "AD_PLAYING"))
        assertFalse(ok(confirmed = true))
    }

    @Test fun onlyStartupWithAnaInFrontAndNoAdWindow() {
        assertFalse(ok(stage = "CHALLENGES"))
        assertFalse(ok(stage = "ADS"))
        assertFalse(ok(sdk = true))
        assertFalse(ok(ana = false))
        assertFalse(ok(live = false))
        assertFalse(ok(recorded = "2026-11-17"))
    }

    @Test fun theOpenAdRestartRulesAreUnchanged() {
        // The same row was never eligible for the open-ad restart: no SDK window, and not one of its timeout reasons.
        assertFalse(StalledAdRestartPolicy.eligible(d, d, "STARTUP", "UNCONFIRMED", StalledAdRestartPolicy.LOGIN_OFFER_RETURN,
            1L, 400_000L, false, false, 0, true, false))
        assertTrue(StalledAdRestartPolicy.eligible(d, d, "STARTUP", "UNCONFIRMED", "startup_ad_timeout_300s",
            1L, 400_000L, false, false, 0, true, true))
    }
}
