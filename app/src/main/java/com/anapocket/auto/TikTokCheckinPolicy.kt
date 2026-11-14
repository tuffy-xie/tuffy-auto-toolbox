package com.anapocket.auto

/**
 * TikTok Lite gift page (Lynx-rendered: no accessibility text, so everything is read from screenshots).
 * Only three daily controls are ever tapped: the 14-day チェックイン, the 翌日報酬 獲得する claim and アンロック.
 * Invite, campaign, ad, reminder and exchange controls are never targets.
 */
object TikTokCheckinPolicy {
    val TASKS=listOf("checkin","claim","unlock")
    private fun n(t:String)=t.replace(Regex("\\s"),"")
    /** Words that must never be inside the box of anything Tuffy taps on this app. */
    private val forbidden=Regex("AD|広告|リマインド|参加|始める|開始|招待|交換|シェア|スタート|回す|受け取る$")
    fun safeTarget(line:CheckinLine)=!forbidden.containsMatchIn(n(line.text))
    fun giftPage(lines:List<CheckinLine>):Boolean {val t=lines.joinToString(""){n(it.text)};return t.contains("獲得ポイント")&&(t.contains("ギフトをもらおう")||t.contains("クーポンと交換"))}
    /** The bottom navigation entry of the feed. */
    // Under a dimming popup OCR reads ギフト as ギラト and 首页 as 首頁, so the middle glyph is free.
    private val giftLabel=Regex("^ギ.ト$")
    fun giftTab(lines:List<CheckinLine>,height:Int)=lines.firstOrNull{giftLabel.matches(n(it.text))&&it.top>height*.86}
    /** Feed or gift page is on screen (possibly under a popup). */
    fun loaded(lines:List<CheckinLine>)=lines.any{val t=n(it.text);giftLabel.matches(t)||t.contains("獲得ポイント")||Regex("^首[页頁]$").matches(t)}
    /** OCR returns lines in block order, not reading order; a sentence split over two lines is only whole when sorted. */
    private fun reading(lines:List<CheckinLine>)=lines.sortedWith(compareBy({it.top},{it.left})).joinToString(""){n(it.text)}
    fun popup(lines:List<CheckinLine>):Boolean {val t=reading(lines)
        // 11/11 07:30: on launch the feed showed 「14日連続でチェックインして獲得しよう」 over the ギフト tab; it is closed with
        // its round ✕ and the check-in is then done on the gift page like any other day.
        return Regex("参加する|始める|リマインドする|おめでとう|獲得しました|受け取りに来て|チャンス|連続チェックインに失敗|$STREAK_INVITE").containsMatchIn(t)}
    // 11/13 20:49 OCR split the invitation as 「14日連続でチェックインし、」+「て獲得しよう」.
    private const val STREAK_INVITE="連続でチェックインし[、,，]?て"
    /** Feed only (never the gift page, whose card has the same words): the 14日連続 invitation may be dismissed with system Back. */
    fun backDismissible(lines:List<CheckinLine>):Boolean {val t=reading(lines)
        return Regex(STREAK_INVITE).containsMatchIn(t)&&!giftPage(lines)}
    fun okButton(lines:List<CheckinLine>)=lines.lastOrNull{n(it.text).equals("OK",true)}
    /** Card anchors. */
    fun checkinTitle(lines:List<CheckinLine>)=lines.firstOrNull{n(it.text).contains("連続チェックイン")&&!n(it.text).contains("失敗")}
    private fun below(lines:List<CheckinLine>,anchor:CheckinLine,max:Int)=lines.filter{it.top>anchor.bottom&&it.top-anchor.bottom<max}
    fun checkinButton(lines:List<CheckinLine>):CheckinLine?{val a=checkinTitle(lines)?:return null;return below(lines,a,650).firstOrNull{n(it.text)=="チェックイン"}}
    /** After today's check-in the button turns grey (明日チェックインして…) or becomes an optional ad offer. */
    fun checkinDone(lines:List<CheckinLine>):Boolean{val a=checkinTitle(lines)?:return false
        return below(lines,a,650).any{val t=n(it.text);t.startsWith("明日チェックインして")||Regex("^[0-9,]+ポイントを獲得").containsMatchIn(t)}}
    fun claimTitle(lines:List<CheckinLine>)=lines.firstOrNull{n(it.text).contains("今日の報酬を受け取る")}
    fun claimButton(lines:List<CheckinLine>):CheckinLine?{val a=claimTitle(lines)?:return null;return below(lines,a,360).firstOrNull{n(it.text)=="獲得する"}}
    /** 「報酬 608」 between the claim title and its button. */
    fun claimAmount(lines:List<CheckinLine>):String?{val a=claimTitle(lines)?:return null;val b=claimButton(lines)?:return null
        return lines.filter{it.top>a.bottom&&it.bottom<=b.top}.mapNotNull{Regex("([0-9][0-9,]*)$").find(n(it.text))?.groupValues?.get(1)?.replace(",","")}.singleOrNull()}
    fun unlockTitle(lines:List<CheckinLine>)=lines.firstOrNull{n(it.text).contains("翌日報酬が受け取れます")}
    /** The yellow アンロック tag sits under the title; the button is the lowest one in the card. */
    fun unlockButton(lines:List<CheckinLine>):CheckinLine?{val a=unlockTitle(lines)?:return null;return below(lines,a,560).filter{n(it.text)=="アンロック"&&it.top-a.bottom>200}.maxByOrNull{it.top}}
    fun unlockDone(lines:List<CheckinLine>)=lines.any{n(it.text).contains("明日受け取りに来てください")}
    fun unlockPopup(lines:List<CheckinLine>)=lines.any{val t=n(it.text);t.contains("明日再度受け取りに来て")||t=="リマインドする"}
    fun checkinReceipt(lines:List<CheckinLine>):Boolean{val t=lines.joinToString(""){n(it.text)};return t.contains("おめでとう")&&t.contains("獲得しました")}
    /** The amount printed under 獲得しました in the receipt popup; anything ambiguous stays unknown. */
    fun receiptAmount(lines:List<CheckinLine>):String?{
        // The title wraps (「…獲」/「得しました:」), so anchor on the last title fragment.
        val head=lines.lastOrNull{Regex("しました|おめでとう").containsMatchIn(n(it.text))}?:return null
        return lines.filter{it.top>head.top&&it.top-head.bottom<220}.mapNotNull{Regex("^[^0-9]{0,2}([0-9]{1,2},?[0-9]{3})$").find(n(it.text))?.groupValues?.get(1)?.replace(",","")}.firstOrNull()
    }
    /** Points balance in the red header card. */
    fun balance(lines:List<CheckinLine>):Long?{
        val head=lines.firstOrNull{n(it.text).contains("獲得ポイント")}?:return null
        return lines.filter{it.top>head.top&&it.top-head.bottom<260}.mapNotNull{Regex("([0-9]{1,3}(?:,[0-9]{3})+)$").find(n(it.text))?.groupValues?.get(1)}
            .map{it.replace(",","").toLong()}.maxOrNull()
    }
    /**
     * Popup close button: a light ✕ inside a dark round button under the popup.
     * Strokes cross at the centre, the axes and a ring around it are dark. Returns the lowest such button.
     */
    fun closeButton(lum:(Int,Int)->Int,width:Int,height:Int,fromY:Int,toY:Int,avoid:List<CheckinLine> = emptyList()):Pair<Int,Int>?{
        val ring=(0 until 16).map{a->Pair(Math.round(28*Math.cos(a*Math.PI/8)).toInt(),Math.round(28*Math.sin(a*Math.PI/8)).toInt())}
        fun bright(x:Int,y:Int)=(-3..3).any{i->(-3..3).any{j->lum(x+i,y+j)>150}}
        val hits=mutableListOf<Pair<Int,Int>>()
        val y0=maxOf(fromY,40);val y1=minOf(toY,height-40)
        for(cx in width/2-8..width/2+8 step 2)for(y in y0 until y1){
            if(lum(cx,y)<150)continue
            if(listOf(-12 to 0,12 to 0,0 to -12,0 to 12).all{(dx,dy)->lum(cx+dx,y+dy)<110}&&ring.all{(dx,dy)->lum(cx+dx,y+dy)<110}&&
                listOf(-9 to -9,9 to -9,-9 to 9,9 to 9).all{(dx,dy)->bright(cx+dx,y+dy)})hits.add(cx to y)
        }
        // Group by vertical position; a real button is one compact cluster.
        val clusters=mutableListOf<MutableList<Pair<Int,Int>>>()
        for(h in hits.sortedBy{it.second}){val c=clusters.lastOrNull();if(c!=null&&h.second-c.last().second<=6)c.add(h) else clusters.add(mutableListOf(h))}
        return clusters.filter{it.size>=8&&it.maxOf{p->p.second}-it.minOf{p->p.second}<=24}
            .map{c->c.sumOf{it.first}/c.size to c.sumOf{it.second}/c.size}
            .filter{(x,y)->avoid.none{it.left-6<=x&&x<=it.right+6&&it.top-6<=y&&y<=it.bottom+6}}
            .maxByOrNull{it.second}
    }
}
