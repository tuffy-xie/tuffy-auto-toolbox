package com.anapocket.auto
import org.junit.Assert.*
import org.junit.Test

class ControlRevealTargetTest {
    @Test fun unnamedFullScreenClickableWrapperIsNotSafeBlankSpace() { assertTrue(ControlRevealTarget.blocks(true,true)) }
    @Test fun controlsOutsideThePointAndNonClickableViewsDoNotBlock() { assertFalse(ControlRevealTarget.blocks(true,false));assertFalse(ControlRevealTarget.blocks(false,true)) }
}
