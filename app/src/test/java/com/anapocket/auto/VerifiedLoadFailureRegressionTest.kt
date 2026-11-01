package com.anapocket.auto
import org.junit.Assert.*
import org.junit.Test

class VerifiedLoadFailureRegressionTest {
    private val steps=listOf(50,50,50,100,100)
    @Test fun secondDistinctLoadErrorCanBeReviewedWithPriorOfficialNoGain(){
        assertTrue(VerifiedLoadFailurePolicy.mayReviewSecond(false,true,true,false,1,1,4,steps))
    }
    @Test fun unknownOpaqueAndUnboundErrorsDoNotQualify(){
        assertFalse(VerifiedLoadFailurePolicy.mayReviewSecond(false,true,true,false,1,0,4,steps))
        assertFalse(VerifiedLoadFailurePolicy.mayReviewSecond(false,false,true,false,1,1,4,steps))
        assertFalse(VerifiedLoadFailurePolicy.mayReviewSecond(false,true,false,false,1,1,4,steps))
    }
    @Test fun sameAdCannotRestartReviewAndThirdFailureIsRejected(){
        assertFalse(VerifiedLoadFailurePolicy.mayReviewSecond(false,true,true,true,1,1,4,steps))
        assertFalse(VerifiedLoadFailurePolicy.mayReviewSecond(false,true,true,false,2,2,4,steps))
    }
    @Test fun initialCompletedAndMalformedBaselinesAreRejected(){
        assertFalse(VerifiedLoadFailurePolicy.mayReviewSecond(true,true,true,false,1,1,4,steps))
        assertFalse(VerifiedLoadFailurePolicy.mayReviewSecond(false,true,true,false,1,1,5,steps))
        assertFalse(VerifiedLoadFailurePolicy.mayReviewSecond(false,true,true,false,1,1,4,steps.drop(1)))
    }
    @Test fun extraStartOnlyFollowsTwoSeparateProvenLoadFailures(){
        assertEquals(6,VerifiedLoadFailurePolicy.startLimit(0,0))
        assertEquals(7,VerifiedLoadFailurePolicy.startLimit(1,0))
        assertEquals(7,VerifiedLoadFailurePolicy.startLimit(1,1))
        assertEquals(6,VerifiedLoadFailurePolicy.startLimit(2,0))
        assertEquals(6,VerifiedLoadFailurePolicy.startLimit(2,1))
        assertEquals(8,VerifiedLoadFailurePolicy.startLimit(2,2))
        assertEquals(6,VerifiedLoadFailurePolicy.startLimit(3,3))
    }
}
