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
    @Test fun searchBudgetUnchanged(){
        assertTrue(AdTaskReturnPolicy.maySearch(true,0,3,true))
        assertFalse(AdTaskReturnPolicy.maySearch(true,0,4,true))
        assertFalse(AdTaskReturnPolicy.maySearch(true,1,0,true))
    }
}
