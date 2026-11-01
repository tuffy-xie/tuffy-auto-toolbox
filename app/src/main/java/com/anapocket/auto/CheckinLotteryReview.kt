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
}
