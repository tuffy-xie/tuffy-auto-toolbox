package com.anapocket.auto

/** Rules from the current real bonus page. Neither marketing copy nor a game click is a receipt. */
object CheckinBonusPolicy {
    const val TITLE="今日のボーナスくじ"
    fun loadingError(text:String):Boolean {
        val t=text.replace(Regex("\\s"),"")
        return t.contains("読み込みに時間がかかっています")&&t.contains("しばらく時間を置いてから")&&t.contains("再度お試しください")
    }
    fun stopReady(text:String,boundSubmitted:Boolean,stopVisible:Boolean,stopSubmitted:Boolean):Boolean {
        val t=text.replace(Regex("\\s"),"")
        return boundSubmitted&&stopVisible&&!stopSubmitted&&t.contains("ストップ")&&t.contains("今日のカードを選ぼう")
    }
    fun outcome(lines:List<CheckinLine>,gameControlVisible:Boolean):String? {
        val footer=lines.firstOrNull{it.text.replace(Regex("\\s"),"").contains("今日のボーナスくじの楽しみ方")}?.top?:Int.MAX_VALUE
        return CheckinLotteryReview.explicitResult(lines.filter{it.top< footer},gameControlVisible)
    }
}
