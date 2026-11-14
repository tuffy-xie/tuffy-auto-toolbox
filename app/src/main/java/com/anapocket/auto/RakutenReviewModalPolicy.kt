package com.anapocket.auto

/**
 * 11/14 07:02: after the first ガラガラ draw the result page (id winpage) was covered by the app-review modal
 * 「一緒に楽天PointClubを応援しよう」 with 「5つ星で応援する」; the result was never read and the second draw never ran.
 * Only the modal's own close (js-btn_close-app-review-modal) is pressed; 「5つ星で応援する」 (js-btn-conversion) never.
 */
object RakutenReviewModalPolicy {
    const val CLOSE_ID="js-btn_close-app-review-modal"
    const val MAX_CLOSES=2
    fun mayClose(ids:Set<String>,labels:List<String>,closesSoFar:Int):Boolean =
        closesSoFar<MAX_CLOSES&&"js-modal_app-review" in ids&&CLOSE_ID in ids&&
            labels.any{it.replace(Regex("\\s"),"").contains("一緒に楽天PointClubを応援しよう")}
}
