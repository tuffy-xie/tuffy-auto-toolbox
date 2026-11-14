package com.anapocket.auto

import org.junit.Assert.*
import org.junit.Test

/** Node ids and texts of CI_RAKUTEN_ac31ce45 lottery_unknown (2026-11-14 07:02:04). */
class RakutenReviewModalPolicyTest {
    private val ids=setOf("winpage","win_advertisement","js-modal_app-review","js-modal-title","js-btn_close-app-review-modal","js-btn-conversion")
    private val labels=listOf("一緒に楽天PointClubを応援しよう","ポイ活がもっと楽しくなるように\nアプリレビューでみんなの声を届けよう！","5つ星で応援する")

    @Test fun theReviewModalOverTheResultIsClosedWithItsOwnClose(){assertTrue(RakutenReviewModalPolicy.mayClose(ids,labels,0))}
    @Test fun atMostTwice(){assertTrue(RakutenReviewModalPolicy.mayClose(ids,labels,1));assertFalse(RakutenReviewModalPolicy.mayClose(ids,labels,2))}
    @Test fun needsTheModalItsCloseAndItsTitle(){
        assertFalse(RakutenReviewModalPolicy.mayClose(ids-"js-btn_close-app-review-modal",labels,0))
        assertFalse(RakutenReviewModalPolicy.mayClose(ids-"js-modal_app-review",labels,0))
        assertFalse(RakutenReviewModalPolicy.mayClose(ids,labels.drop(1),0))
    }
    @Test fun theTargetIsNeverTheFiveStarButton(){assertNotEquals("js-btn-conversion",RakutenReviewModalPolicy.CLOSE_ID)}
}
