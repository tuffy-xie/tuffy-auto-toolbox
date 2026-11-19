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
    @Test fun nov19OfficialResultPageWithImageOnlyPrizeEndsTheDraw(){
        // Visible WebView ids captured at 11/19 12:01:35 (lottery_unknown) after the review modal was closed twice.
        val page=setOf("winpage","win_advertisement","win_advertisement_button")
        assertTrue(CheckinLotteryReview.resultPage(page))
        // 12:01:05: the same page still covered by the review modal is not yet the result.
        assertFalse(CheckinLotteryReview.resultPage(page+setOf("js-modal_app-review","js-modal-title","js-btn_close-app-review-modal")))
        // The ready page (12:00:43) never counts, nor any page still offering Start or showing an error/already panel.
        assertFalse(CheckinLotteryReview.resultPage(setOf("animation_container","animation_cp1_box","cp1_bg","cp_btn_start","loading_box")))
        for(x in listOf("cp_btn_start","animation_err_box","cp_link_err","animation_already_box","cp_link_already"))
            assertFalse(CheckinLotteryReview.resultPage(page+x))
        assertFalse(CheckinLotteryReview.resultPage(setOf("win_advertisement")))
        assertEquals(2,CheckinLotteryReview.RESULT_PAGE_POLLS)
    }
}
