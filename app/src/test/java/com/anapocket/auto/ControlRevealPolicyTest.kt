package com.anapocket.auto

import org.junit.Assert.*
import org.junit.Test

class ControlRevealPolicyTest {
    @Test fun waitForPlaybackThenNeverDoubleTapControls() {
        assertFalse(ControlRevealPolicy.canReveal(29_999, 0, 100_000, false))
        assertTrue(ControlRevealPolicy.canReveal(30_000, 0, 100_000, false))
        assertFalse(ControlRevealPolicy.canReveal(30_100, 1, 100, false))
        assertTrue(ControlRevealPolicy.canReveal(55_000, 1, 25_000, false))
        assertTrue(ControlRevealPolicy.canReveal(80_000, 2, 25_000, false))
        assertFalse(ControlRevealPolicy.canReveal(110_000, 3, 30_000, false))
    }
    @Test fun visibleCloseMustNotBeHiddenByAnotherTap() {
        assertFalse(ControlRevealPolicy.canReveal(90_000, 0, 90_000, true))
    }
}
