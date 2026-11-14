package com.anapocket.auto

import org.junit.Assert.*
import org.junit.Test

class RakutenLotteryPolicyTest {
    @Test fun firstDrawFirst(){assertEquals("lottery1",RakutenLotteryPolicy.next(false,false,"",false))}
    @Test fun afterAConfirmedFirstDrawTheSecond(){assertEquals("lottery2",RakutenLotteryPolicy.next(true,true,"START_SUBMITTED",false))}
    /** 11/14: lottery1 submitted, result hidden by the review modal. */
    @Test fun aSubmittedUnknownFirstDrawStillLetsTheSecondRun(){assertEquals("lottery2",RakutenLotteryPolicy.next(false,true,"START_SUBMITTED",false))}
    @Test fun anUnsubmittedReservationIsStillTheFirstDraw(){assertEquals("lottery1",RakutenLotteryPolicy.next(false,true,"ENTRY_RESERVED",false))}
    @Test fun nothingAfterBoth(){
        assertNull(RakutenLotteryPolicy.next(true,true,"START_SUBMITTED",true))
        assertNull(RakutenLotteryPolicy.next(false,true,"START_SUBMITTED",true))
    }
}
