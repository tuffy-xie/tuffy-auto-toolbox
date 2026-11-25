package com.anapocket.auto

/**
 * TikTok Lite gift page (Lynx-rendered: no accessibility text, so everything is read from screenshots).
 * Only the four daily controls are ever tapped: the 14-day チェックイン, the 翌日報酬 獲得する claim, アンロック and the bonus 受け取る
 * (since the 11/25 感謝祭 layout the first two read 受け取る too, always taken beside their own card title).
 * Invite, campaign, ad, reminder and exchange controls are never targets.
 */
object TikTokCheckinPolicy {
    val TASKS=listOf("checkin","claim","unlock","bonus_reward")
    /**
     * Frames read per pass while scrolling down the gift page. 11/16-11/17 the 14日連続 card came into reach after ~8 swipes;
     * 11/18 a 「新規ユーザーを招待して 2,800円分」 card above it pushed it past 10 and all four tasks read not found.
     * The step stays the same (shorter than a card), so only the reach grows; scanning still stops at the first match.
     */
    const val SEEK_SWIPES=18
    private fun n(t:String)=t.replace(Regex("\\s"),"")
    /** Words that must never be inside the box of anything Tuffy taps on this app. */
    private val forbidden=Regex("AD|広告|リマインド|参加|始める|開始|招待|交換|換金|シェア|スタート|回す|受け取る$")
    fun safeTarget(line:CheckinLine)=!forbidden.containsMatchIn(n(line.text))
    /** A 受け取る that one of the daily cards' own button finders returned (never any other 受け取る on the page). */
    fun cardReceive(line:CheckinLine)=n(line.text)=="受け取る"
    /**
     * 11/25 感謝祭 layout: the daily cards (「14日間連続チェックインで獲得」, 「今日の報酬を受け取ろう」) carry a red 受け取る on the
     * title's own row, right of it, where the old ones had チェックイン / 獲得する under the title.
     */
    private fun receiveBeside(lines:List<CheckinLine>,a:CheckinLine)=lines.firstOrNull{n(it.text)=="受け取る"&&it.left>=a.right-20&&it.top>=a.top-60&&it.top<=a.bottom+80}
    private fun doneBeside(lines:List<CheckinLine>,a:CheckinLine)=lines.firstOrNull{it!==a&&it.left>=a.right-20&&it.top>=a.top-60&&it.top<=a.bottom+80&&
        Regex("済み|獲得しました|明日").containsMatchIn(n(it.text))}
    /**
     * Header of the red points card. 11/25 12:38 (感謝祭 layout) it read 「あなたのポイント」 · 「1,337円分 =133,723」 ·
     * 「換金可能」 instead of 「獲得ポイント」 · 「クーポンと交換」, so the loaded page was never recognised and all four
     * tasks timed out at 90 s.
     */
    private val pointsHeader=Regex("獲得ポイント|あなたのポイント")
    fun giftPage(lines:List<CheckinLine>):Boolean {val t=lines.joinToString(""){n(it.text)}
        return pointsHeader.containsMatchIn(t)&&(t.contains("ギフトをもらおう")||t.contains("クーポンと交換")||t.contains("換金可能"))}
    /** The bottom navigation entry of the feed. */
    // Under a dimming popup OCR reads ギフト as ギラト and 首页 as 首頁, so the middle glyph is free.
    private val giftLabel=Regex("^ギ.ト$")
    fun giftTab(lines:List<CheckinLine>,height:Int)=lines.firstOrNull{giftLabel.matches(n(it.text))&&it.top>height*.86}
    /** Feed or gift page is on screen (possibly under a popup). */
    fun loaded(lines:List<CheckinLine>)=lines.any{val t=n(it.text);giftLabel.matches(t)||pointsHeader.containsMatchIn(t)||Regex("^首[页頁]$").matches(t)}
    /** OCR returns lines in block order, not reading order; a sentence split over two lines is only whole when sorted. */
    private fun reading(lines:List<CheckinLine>)=lines.sortedWith(compareBy({it.top},{it.left})).joinToString(""){n(it.text)}
    fun popup(lines:List<CheckinLine>):Boolean {val kept=claimedButton(lines);val t=reading(if(kept==null)lines else lines-kept)
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
    fun checkinButton(lines:List<CheckinLine>):CheckinLine?{val a=checkinTitle(lines)?:return null
        return below(lines,a,650).firstOrNull{n(it.text)=="チェックイン"}?:receiveBeside(lines,a)}
    /** After today's check-in the button turns grey (明日チェックインして…) or becomes an optional ad offer. */
    fun checkinDone(lines:List<CheckinLine>):Boolean{val a=checkinTitle(lines)?:return false
        if(receiveBeside(lines,a)==null&&doneBeside(lines,a)!=null)return true
        // 11/25 16:20 感謝祭 layout after the check-in: the 受け取る became a grey ✓ with no text, and the day strip under the
        // title read 16日目 ✓ · 17日目 ✓ · 明日 where it had read 16日目 ✓ · 今日 before.
        val strip=below(lines,a,650).map{n(it.text)}
        if(receiveBeside(lines,a)==null&&strip.none{it=="チェックイン"}&&strip.contains("明日")&&!strip.contains("今日"))return true
        return below(lines,a,650).any{val t=n(it.text);t.startsWith("明日チェックインして")||Regex("^[0-9,]+ポイントを獲得").containsMatchIn(t)}}
    fun claimTitle(lines:List<CheckinLine>)=lines.firstOrNull{val t=n(it.text);t.contains("今日の報酬を受け取る")||t.contains("今日の報酬を受け取ろう")}
    /**
     * 「報酬 607」 inside the claim card. 11/15 07:32 a floating 「300ポイントを獲得 AD」 offer covered the card title and OCR
     * read the amount as 「報酬D607×」; this line still anchors the card. Ad offers never match.
     */
    fun claimReward(lines:List<CheckinLine>)=lines.firstOrNull{val t=n(it.text)
        Regex("^報酬\\D{0,3}[0-9][0-9,]*\\D{0,2}$").matches(t)&&!Regex("AD|広告|ボーナス").containsMatchIn(t)}
    /** The claim card is on screen: its title, or its 報酬 line when the title is covered. */
    fun claimPresent(lines:List<CheckinLine>)=claimTitle(lines)!=null||claimReward(lines)!=null
    fun claimButton(lines:List<CheckinLine>):CheckinLine?{
        claimTitle(lines)?.let{a->return below(lines,a,360).firstOrNull{n(it.text)=="獲得する"}?:receiveBeside(lines,a)}
        val r=claimReward(lines)?:return null
        return below(lines,r,200).firstOrNull{n(it.text)=="獲得する"}
    }
    /**
     * 11/20 12:31: after 獲得する the claim card stayed and its button read 「獲得しました」; 20 s later Tuffy still called it
     * unconfirmed. The page reopened at 13:12 showed the same card (報酬 830 · 獲得しました). That button is the official
     * claimed state. It sits inside the card, so it is not a popup and is never tapped.
     */
    fun claimedButton(lines:List<CheckinLine>):CheckinLine?{
        claimTitle(lines)?.let{a->return below(lines,a,360).firstOrNull{n(it.text)=="獲得しました"}?:if(receiveBeside(lines,a)==null)doneBeside(lines,a) else null}
        val r=claimReward(lines)?:return null
        return below(lines,r,200).firstOrNull{n(it.text)=="獲得しました"}
    }
    fun claimClaimed(lines:List<CheckinLine>)=claimedButton(lines)!=null&&claimButton(lines)==null
    /**
     * Until 11/19 the claimed card turned into 翌日報酬が受け取れます / アンロック at once. On 11/20 the claimed card stayed
     * in that place, also after the page was reopened, and no アンロック card appeared anywhere: nothing to unlock today.
     */
    const val UNLOCK_NOT_OFFERED="official_unlock_not_offered_claimed_card_kept"
    fun unlockNotOffered(lines:List<CheckinLine>)=claimClaimed(lines)&&unlockTitle(lines)==null&&!unlockDone(lines)
    /** 「報酬 608」 between the claim title and its button. */
    fun claimAmount(lines:List<CheckinLine>):String?{val b=claimButton(lines)?:return null
        val a=claimTitle(lines);val r=claimReward(lines)
        val rows=if(a!=null&&b.top<a.bottom)lines.filter{it.top>a.bottom&&it.top-a.bottom<120}.sortedBy{it.top}.take(1)
            else if(a!=null)lines.filter{it.top>a.bottom&&it.bottom<=b.top} else listOfNotNull(r)
        return rows.mapNotNull{Regex("([0-9][0-9,]*)\\D{0,2}$").find(n(it.text))?.groupValues?.get(1)?.replace(",","")}.singleOrNull()}
    /**
     * 「ボーナス報酬をGET！最大2,500」 card (seen 11/15): its 受け取る credits at once, shows 「N/10回 受け取り済み」 and turns into a
     * cooldown timer. The user asked for one press a day (11/15); 受け取る is allowed on this card only.
     */
    // 11/25 感謝祭 layout: the same card reads 「報酬を受け取ろう 2,400 · 0/10回 受け取り済み」. 「今日の報酬を受け取ろう」 is the claim card.
    private fun bonusTitleText(t:String)=t.contains("ボーナス報酬を")||t.startsWith("報酬を受け取ろう")
    private fun counter(lines:List<CheckinLine>,a:CheckinLine)=
        below(lines,a,330).firstNotNullOfOrNull{Regex("([0-9]+)/10回受け取り済み").find(n(it.text))?.groupValues?.get(1)?.toInt()}
    fun bonusTitle(lines:List<CheckinLine>):CheckinLine?{val all=lines.filter{bonusTitleText(n(it.text))}
        return all.firstOrNull{counter(lines,it)!=null}?:all.firstOrNull()}
    fun bonusCount(lines:List<CheckinLine>):Int?{val a=bonusTitle(lines)?:return null;return counter(lines,a)}
    fun bonusButton(lines:List<CheckinLine>,width:Int):CheckinLine?{val a=bonusTitle(lines)?:return null
        return lines.firstOrNull{n(it.text)=="受け取る"&&it.left>width*.5&&it.top>=a.top-60&&it.top<=a.bottom+80}}
    /** 11/25 16:08 感謝祭 layout: after the claim, 「報酬をアンロックして、明日 / 受け取ろう ・988」 with アンロック right of the title. */
    private fun newUnlockTitle(lines:List<CheckinLine>)=lines.firstOrNull{n(it.text).contains("報酬をアンロックして")}
    fun unlockTitle(lines:List<CheckinLine>)=lines.firstOrNull{n(it.text).contains("翌日報酬が受け取れます")}?:newUnlockTitle(lines)
    private fun unlockBeside(lines:List<CheckinLine>,a:CheckinLine)=
        lines.firstOrNull{n(it.text)=="アンロック"&&it.left>=a.right-20&&it.top>=a.top-60&&it.top<=a.bottom+80}
    /** The yellow アンロック tag sits under the title; the button is the lowest one in the card. */
    fun unlockButton(lines:List<CheckinLine>):CheckinLine?{val a=unlockTitle(lines)?:return null
        return below(lines,a,560).filter{n(it.text)=="アンロック"&&it.top-a.bottom>200}.maxByOrNull{it.top}?:unlockBeside(lines,a)}
    /**
     * Old card: 「明日受け取りに来てください」. 感謝祭 card: its title and amount are in view and the red アンロック beside the title is
     * gone (the check-in card turned the same button into a grey ✓ with no text).
     */
    fun unlockDone(lines:List<CheckinLine>):Boolean {
        if(lines.any{n(it.text).contains("明日受け取りに来てください")})return true
        val a=newUnlockTitle(lines)?:return false
        val amount=lines.any{it.top>a.bottom&&it.top-a.bottom<140&&Regex("^\\D{0,2}[0-9][0-9,]*$").matches(n(it.text))}
        return amount&&unlockBeside(lines,a)==null&&below(lines,a,560).none{n(it.text)=="アンロック"}
    }
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
        val head=lines.firstOrNull{pointsHeader.containsMatchIn(n(it.text))}?:return null
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
