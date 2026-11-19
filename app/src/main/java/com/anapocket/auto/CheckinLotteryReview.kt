package com.anapocket.auto

/** Read-only reconciliation of the same submitted opportunity. Never authorizes another draw. */
object CheckinLotteryReview {
    const val TITLE="楽天PointClubアプリラッキーくじ"
    fun mayReview(reserved:Boolean,phase:String,state:String,businessDate:String,today:String)=
        reserved&&phase=="START_SUBMITTED"&&businessDate==today&&
            state in setOf("RUNNING","UNCONFIRMED","PREEMPTED")

    fun explicitResult(lines:List<CheckinLine>,startVisible:Boolean):String? {
        if(startVisible)return null
        val text=lines.map{it.text.replace(Regex("\\s"),"")}
        if(text.any{Regex("ご利用ガイド|STEP[0-9]|遊び方|参加方法|ルール|応募要項|注意事項|当選確率|最大").containsMatchIn(it)})return null
        val lost=text.any{Regex("^(?:はずれ|ハズレ)(?:でした)?[!！。…]*$").matches(it)}
        val won=text.any{it=="当選結果"}&&text.any{Regex("^[0-9][0-9,]*ポイント(?:を)?(?:ゲット|獲得)(?:しました)?[!！。]*$").matches(it)}
        return when {lost&&!won->"LOST";won&&!lost->"WON";else->null}
    }

    /**
     * 11/19 12:01 (and 11/14 07:02): after Start the official result page (WebView id winpage) stayed up once the review
     * modal was closed, but its prize is drawn as an image, so OCR found no 当選/はずれ text; lottery1 was left unconfirmed
     * and lottery2 never ran. The page itself is the official end of the same submitted draw. The amount is not inferred.
     * Not while the modal, a Start control or an error/already panel is visible.
     */
    val NOT_RESULT=setOf("js-modal_app-review","cp_btn_start","animation_err_box","cp_link_err","animation_already_box","cp_link_already")
    fun resultPage(ids:Set<String>)="winpage" in ids&&NOT_RESULT.none{it in ids}
    /** Seen on this many consecutive polls (about 1 s apart) before it counts. */
    const val RESULT_PAGE_POLLS=2
}
