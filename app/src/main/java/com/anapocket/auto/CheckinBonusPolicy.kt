package com.anapocket.auto

/** Rules from the current real bonus page. Neither marketing copy nor a game click is a receipt. */
object CheckinBonusPolicy {
    const val TITLE="今日のボーナスくじ"
    private fun plain(text:String)=text.replace(Regex("\\s"),"")
    fun loadingError(text:String):Boolean {
        val t=plain(text)
        return t.contains("読み込みに時間がかかっています")&&t.contains("しばらく時間を置いてから")&&t.contains("再度お試しください")
    }
    fun stopReady(text:String,boundSubmitted:Boolean,stopVisible:Boolean,stopSubmitted:Boolean):Boolean {
        val t=plain(text)
        return boundSubmitted&&stopVisible&&!stopSubmitted&&t.contains("ストップ")&&t.contains("今日のカードを選ぼう")
    }
    enum class Entry { BLOCKED, KEEP_UNCONFIRMED, REVIEW_SUBMITTED, NEW_ENTRY }
    /**
     * What the earn page may do with today's bonus row. A submitted draw is only ever reopened to read its
     * result (Start is never sent again); when the daily cards have not loaded it stays unconfirmed instead
     * of being downgraded to blocked.
     */
    fun entry(prerequisitesDone:Boolean,reserved:Boolean,phase:String):Entry = when {
        reserved&&phase=="START_SUBMITTED" -> if(prerequisitesDone)Entry.REVIEW_SUBMITTED else Entry.KEEP_UNCONFIRMED
        !prerequisitesDone -> Entry.BLOCKED
        reserved&&phase!="ENTRY_RESERVED" -> Entry.KEEP_UNCONFIRMED
        else -> Entry.NEW_ENTRY
    }
    /** Earn page, Nov 8: under "今日のボーナスくじに進もう" the entry is replaced by "本日分は抽選済みです". */
    fun drawnToday(lines:List<CheckinLine>):Boolean {
        val heading=lines.firstOrNull{plain(it.text).let{t->t.contains("今日")&&t.contains("くじ")}}?:return false
        return lines.any{it.top>heading.bottom&&it.top-heading.bottom<250&&plain(it.text).contains("本日分は抽選済み")}
    }
    /**
     * The official drawn marker settles the day's bonus without opening it: a draw Tuffy submitted finished,
     * and a draw it never started was made by hand. Any other reserved state stays for review.
     */
    fun drawnState(reserved:Boolean,phase:String):CheckinState? = when {
        reserved&&phase=="START_SUBMITTED" -> CheckinState.SUCCESS
        !reserved||phase=="ENTRY_RESERVED" -> CheckinState.ALREADY_DONE
        else -> null
    }
    /** The rules section and its banners ("1ポイントミッション", "最大1,000ポイント") sit below this header. */
    fun aboveFooter(lines:List<CheckinLine>):List<CheckinLine> {
        val footer=lines.firstOrNull{plain(it.text).contains("今日のボーナスくじの楽しみ方")}?.top?:Int.MAX_VALUE
        return lines.filter{it.top<footer}
    }
    fun outcome(lines:List<CheckinLine>,gameControlVisible:Boolean):String? {
        val result=aboveFooter(lines)
        return CheckinLotteryReview.explicitResult(result,gameControlVisible)?:creditNotice(result,gameControlVisible)
    }
    /**
     * Result page seen on 2026-11-08: the card art reads "1ポイント GET!" and below it the page says
     * "ポイントは翌日中に進呈されます。" and "明日またきてね". Both texts together, with no game control
     * and no loss text, prove the submitted draw finished with a prize.
     */
    private fun creditNotice(lines:List<CheckinLine>,gameControlVisible:Boolean):String? {
        if(gameControlVisible)return null
        val t=lines.map{plain(it.text)}
        val credit=t.any{it.contains("ポイントは翌日中に進呈されます")}
        val comeBack=t.any{it.contains("明日またきてね")}
        val lost=t.any{Regex("はずれ|ハズレ").containsMatchIn(it)}
        // 11/13 07:03: the no-prize card reads 「ポイントGETならず.」 with 明日またきてね and no credit notice.
        val noPrize=t.any{it.contains("GETならず")}
        return when {credit&&comeBack&&!lost&&!noPrize->"WON";noPrize&&comeBack&&!credit->"LOST";else->null}
    }
}
