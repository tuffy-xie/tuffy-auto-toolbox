package com.anapocket.auto
import org.junit.Assert.*
import org.junit.Test
class CategoryCompletionPolicyTest {
    @Test fun fullListExhaustionIsRequiredBeforeCompletedNotification() {
        assertTrue(CategoryCompletionPolicy.mayComplete(true,false,true))
        assertFalse(CategoryCompletionPolicy.mayComplete(false,false,true))
    }
    @Test fun stopAndRealDateRolloverCannotClaimTheCategoryCompleted() {
        assertFalse(CategoryCompletionPolicy.mayComplete(true,true,true))
        assertFalse(CategoryCompletionPolicy.mayComplete(true,false,false))
    }
    @Test fun increasingTheKmLimitRequiresAnotherScan() {
        assertTrue(CategoryCompletionPolicy.covers(40,10)); assertTrue(CategoryCompletionPolicy.covers(40,40))
        assertFalse(CategoryCompletionPolicy.covers(10,40)); assertFalse(CategoryCompletionPolicy.covers(0,5))
    }
}
