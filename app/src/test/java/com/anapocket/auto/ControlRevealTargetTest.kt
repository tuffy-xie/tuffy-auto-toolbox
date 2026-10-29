package com.anapocket.auto
import org.junit.Assert.*
import org.junit.Test

class ControlRevealTargetTest {
    @Test fun actualUnnamedFullCanvasWrapperDoesNotBlockBoundedReveal() { assertFalse(ControlRevealTarget.blocks(true,true,"",1.0)) }
    @Test fun explicitInstallCtaAlwaysBlocksTheTouch() { assertTrue(ControlRevealTarget.blocks(true,true,"Google Play",1.0));assertTrue(ControlRevealTarget.blocks(true,true,"インストール",.1)) }
    @Test fun smallUnlabelledActionIsNotTouched() { assertTrue(ControlRevealTarget.blocks(true,true,"",.05)) }
    @Test fun bottomCtaOutsideCanvasCentreDoesNotBlockReveal() { assertFalse(ControlRevealTarget.blocks(true,false,"Play Free",.1));assertFalse(ControlRevealTarget.blocks(false,true,"",1.0)) }
}
