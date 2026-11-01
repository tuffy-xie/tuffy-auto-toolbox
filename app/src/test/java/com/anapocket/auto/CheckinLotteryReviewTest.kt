package com.anapocket.auto

import org.junit.Assert.*
import org.junit.Test

class CheckinLotteryReviewTest {
    private fun lines(vararg text:String)=text.mapIndexed{i,t->CheckinLine(t,10,100+i*60,650,150+i*60)}
    @Test fun onlySameDateSubmittedUnfinishedReservationCanBeReviewed(){
        assertTrue(CheckinLotteryReview.mayReview(true,"START_SUBMITTED","UNCONFIRMED","2026-10-30","2026-10-30"))
        assertFalse(CheckinLotteryReview.mayReview(true,"START_SUBMITTED","UNCONFIRMED","2026-10-29","2026-10-30"))
        assertFalse(CheckinLotteryReview.mayReview(true,"ENTRY_RESERVED","UNCONFIRMED","2026-10-30","2026-10-30"))
        assertFalse(CheckinLotteryReview.mayReview(false,"START_SUBMITTED","UNCONFIRMED","2026-10-30","2026-10-30"))
        assertFalse(CheckinLotteryReview.mayReview(true,"START_SUBMITTED","SUCCESS","2026-10-30","2026-10-30"))
    }
    @Test fun marketingMaximumAndRulesCannotBecomeResult(){
        assertNull(CheckinLotteryReview.explicitResult(lines("当たってうれしい！","最大1,000ポイント","当選結果"),false))
        assertNull(CheckinLotteryReview.explicitResult(lines("はずれの場合は参加賞","ご利用ガイド"),false))
        assertNull(CheckinLotteryReview.explicitResult(lines("当選結果","1ポイントゲット","応募要項"),false))
    }
    @Test fun visibleStartCannotProvePreviouslySubmittedDrawFinished(){
        assertNull(CheckinLotteryReview.explicitResult(lines("ハズレ"),true))
        assertNull(CheckinLotteryReview.explicitResult(lines("当選結果","1ポイントゲット"),true))
    }
    @Test fun explicitResultFixturesDoNotClaimCredit(){
        assertEquals("LOST",CheckinLotteryReview.explicitResult(lines("ハズレ！"),false))
        assertEquals("WON",CheckinLotteryReview.explicitResult(lines("当選結果","1ポイントゲット！"),false))
        assertNull(CheckinLotteryReview.explicitResult(lines("1ポイントゲット！"),false))
        assertNull(CheckinLotteryReview.explicitResult(lines("ハズレ","当選結果","1ポイントゲット！"),false))
    }
}
