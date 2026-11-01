package com.anapocket.auto
import org.junit.Assert.*
import org.junit.Test

class PangleChromeReturnRegressionTest {
    @Test fun returnsOnlyAfterRecentPangleAndStableChrome(){
        val p=PangleChromeReturnPolicy()
        assertFalse(p.shouldBack(0,true,false,true))
        assertFalse(p.shouldBack(800,false,true,true))
        assertTrue(p.shouldBack(2800,false,true,true))
    }
    @Test fun unrelatedChromeNeverAuthorizesBack(){
        val p=PangleChromeReturnPolicy()
        assertFalse(p.shouldBack(0,false,true,true))
        assertFalse(p.shouldBack(9000,false,true,true))
    }
    @Test fun anotherAppBreaksTheObservedChain(){
        val p=PangleChromeReturnPolicy()
        p.shouldBack(0,true,false,true);p.shouldBack(300,false,false,true)
        assertFalse(p.shouldBack(800,false,true,true))
        assertFalse(p.shouldBack(2800,false,true,true))
    }
    @Test fun stalePangleDoesNotAdoptLaterBrowser(){
        val p=PangleChromeReturnPolicy()
        p.shouldBack(0,true,false,true)
        assertFalse(p.shouldBack(5001,false,true,true))
        assertFalse(p.shouldBack(8000,false,true,true))
    }
    @Test fun cancelledOrRevokedLeaseCannotReturn(){
        val p=PangleChromeReturnPolicy()
        p.shouldBack(0,true,false,true);p.shouldBack(800,false,true,true)
        assertFalse(p.shouldBack(2800,false,true,false))
        assertFalse(p.shouldBack(4000,false,true,true))
    }
    @Test fun onlyOneBackEvenAfterNativeReappears(){
        val p=PangleChromeReturnPolicy()
        p.shouldBack(0,true,false,true);p.shouldBack(800,false,true,true)
        assertTrue(p.shouldBack(2800,false,true,true))
        p.shouldBack(3000,true,false,true);p.shouldBack(3800,false,true,true)
        assertFalse(p.shouldBack(5800,false,true,true))
    }
    @Test fun excessiveWaitAndReverseClockDoNotDispatch(){
        val p=PangleChromeReturnPolicy()
        p.shouldBack(0,true,false,true);p.shouldBack(800,false,true,true)
        assertFalse(p.shouldBack(31000,false,true,true))
        val q=PangleChromeReturnPolicy();q.shouldBack(1000,true,false,true)
        assertFalse(q.shouldBack(500,false,true,true))
    }
}
