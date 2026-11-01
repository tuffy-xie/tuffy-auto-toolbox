package com.anapocket.auto
import org.junit.Test
import org.junit.Assert.*

class NavigationOcclusionRegressionTest {
    @Test fun loadedNativeBrowserKeepsContainerWhenWebChildLosesResourceId(){
        val ids=setOf("toolbar","close","back","forward","webView_container")
        assertTrue(StartupPolicy.nativeBrowser(StartupPolicy.ANA,StartupPolicy.ANA+".views.common.WebViewActivity",ids))
        assertFalse(StartupPolicy.nativeBrowser(StartupPolicy.ANA,StartupPolicy.ANA+".views.common.WebViewActivity",ids-"webView_container"))
    }
    @Test fun foregroundWebWindowWinsOverRetainedBackgroundCategoryList(){
        val p=StartupPolicy.ANA
        val windows=listOf(NavigationWindow(0,p,false,false,1),NavigationWindow(1,p,true,true,3))
        assertEquals(1,NavigationWindowPolicy.choose(p,p,windows))
    }
    @Test fun foreignForegroundNeverAdoptsHiddenAnaWindow(){
        assertNull(NavigationWindowPolicy.choose(StartupPolicy.ANA,"com.android.vending",listOf(NavigationWindow(0,StartupPolicy.ANA,false,false,1))))
    }
    @Test fun noActiveOrFocusedAnaWindowDoesNotAuthorizeBackgroundRoot(){
        assertNull(NavigationWindowPolicy.choose(StartupPolicy.ANA,StartupPolicy.ANA,listOf(NavigationWindow(0,StartupPolicy.ANA,false,false,1))))
    }
    @Test fun dimmedWhiteTileIsNotNavigationEvidence(){
        assertTrue(CategoryChipVision.unobscured(IntArray(96*96){0xffffffff.toInt()}))
        assertFalse(CategoryChipVision.unobscured(IntArray(96*96){0xff999999.toInt()}))
        assertFalse(CategoryChipVision.unobscured(intArrayOf()))
    }
    @Test fun ordinaryIconAndSelectedBackgroundRemainUsable(){
        val icon=IntArray(96*96){if(it<1000)0xff00aff0.toInt() else 0xffffffff.toInt()}
        assertTrue(CategoryChipVision.matches(Cat.CAR,96,96,icon))
        assertTrue(CategoryChipVision.unobscured(icon))
        assertTrue(CategoryChipVision.unobscured(IntArray(96*96){0xff00aff0.toInt()}))
    }
    @Test fun onlyObservedNativeBrowserToolbarCanBeDismissed(){
        val ids=setOf("toolbar","close","back","forward","web_view")
        assertTrue(StartupPolicy.nativeBrowser(StartupPolicy.ANA,StartupPolicy.ANA+".views.common.WebViewActivity",ids))
        assertFalse(StartupPolicy.nativeBrowser(StartupPolicy.ANA,StartupPolicy.ANA+".views.home.HomeActivity",ids))
        assertFalse(StartupPolicy.nativeBrowser("third.party",StartupPolicy.ANA+".views.common.WebViewActivity",ids))
        assertFalse(StartupPolicy.nativeBrowser(StartupPolicy.ANA,StartupPolicy.ANA+".views.common.WebViewActivity",ids-"toolbar"))
    }
}
