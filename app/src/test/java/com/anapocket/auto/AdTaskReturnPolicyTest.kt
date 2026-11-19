package com.anapocket.auto

import org.junit.Assert.*
import org.junit.Test

/** 11/12: ANA swiped away by the user while an ad result was unknown must not stop the rest of the day. */
class AdTaskReturnPolicyTest {
    @Test fun anaIsGoneOnlyAfterARealSearchOfRecents(){
        assertTrue("scrolled once, list stopped moving",AdTaskReturnPolicy.searchedWholeList(true,1,true))
        assertTrue("page budget used up",AdTaskReturnPolicy.searchedWholeList(false,4,true))
    }
    @Test fun anythingLessIsAnOrdinaryFailure(){
        assertFalse("never scrolled",AdTaskReturnPolicy.searchedWholeList(true,0,true))
        assertFalse("run stopped or date changed",AdTaskReturnPolicy.searchedWholeList(true,2,false))
        assertFalse("still searching",AdTaskReturnPolicy.searchedWholeList(false,2,true))
    }
    @Test fun nov20EmptyRecentsMeansAnaIsGone(){
        // 03:39 and 04:39: the #16 ad task had been removed; Recents showed only its empty-state text.
        assertTrue(AdTaskReturnPolicy.emptyRecents("",listOf("近期没有任何内容")))
        assertTrue(AdTaskReturnPolicy.emptyRecents("",listOf(""," No recent items ")))
        assertFalse("first frame, nothing drawn yet",AdTaskReturnPolicy.emptyRecents("",listOf("","")))
        assertFalse("cards are present",AdTaskReturnPolicy.emptyRecents("Play 商店,未加锁",listOf("近期没有任何内容")))
    }
    @Test fun nov20LauncherInFrontMeansTheRecordedAdWindowIsGone(){
        val home="com.miui.home"
        assertFalse(OfficialLoadFailurePolicy.adWindowInFront(true,home,home))
        assertTrue(OfficialLoadFailurePolicy.adWindowInFront(true,StartupPolicy.ANA,home))
        assertTrue("ad handed off to the store",OfficialLoadFailurePolicy.adWindowInFront(true,"com.android.vending",home))
        assertTrue("package unknown",OfficialLoadFailurePolicy.adWindowInFront(true,null,home))
        assertTrue("home unresolved",OfficialLoadFailurePolicy.adWindowInFront(true,home,null))
        assertFalse(OfficialLoadFailurePolicy.adWindowInFront(false,StartupPolicy.ANA,home))
    }
    @Test fun searchBudgetUnchanged(){
        assertTrue(AdTaskReturnPolicy.maySearch(true,0,3,true))
        assertFalse(AdTaskReturnPolicy.maySearch(true,0,4,true))
        assertFalse(AdTaskReturnPolicy.maySearch(true,1,0,true))
    }
}
