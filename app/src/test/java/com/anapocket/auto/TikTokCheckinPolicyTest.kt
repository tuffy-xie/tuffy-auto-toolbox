package com.anapocket.auto

import org.junit.Assert.*
import org.junit.Test
import java.util.zip.GZIPInputStream

class TikTokCheckinPolicyTest {
    private fun l(t:String,left:Int,top:Int,right:Int,bottom:Int)=CheckinLine(t,left,top,right,bottom)
    // Positions from the real 720x1650 gift page on 2026-11-10.
    private val title=l("14日間連続チェックインで",60,585,550,625)
    private val checkin=l("チェックイン",270,980,450,1020)
    private val claimTitle=l("今日の報酬を受け取る",60,1160,465,1205)
    private val reward=l("報酬 608",150,1255,325,1295)
    private val claim=l("獲得する",300,1360,420,1400)

    /** 11/13 07:31 feed popup OCR: the ✕ was not found and the ギフト tab stayed covered. */
    private val nov13Popup=listOf(l("4日間チェックインしました",255,256,623,289),l("14日連続でチェックインし",139,489,582,525),
        l("て獲得しよう",252,535,465,571),l("P 40,320",224,605,500,662),l("今日",163,858,194,875),
        l("チェックイン",272,1134,420,1160),l("TikTok Japan",39,1321,239,1356),l("ギフト",188,1518,244,1540),l("首貢",53,1521,91,1541))
    @Test fun feedStreakPopupMayBeDismissedWithBackButNotTheGiftPage(){
        assertTrue(TikTokCheckinPolicy.popup(nov13Popup))
        assertTrue(TikTokCheckinPolicy.backDismissible(nov13Popup))
        assertFalse(TikTokCheckinPolicy.backDismissible(nov13Popup+l("獲得ポイント",40,200,300,240)+l("ギフトをもらおう",40,300,300,340)))
        assertFalse(TikTokCheckinPolicy.backDismissible(listOf(l("おめでとう",200,400,500,440),l("OK",300,900,420,940))))
    }
    /** 11/13 20:49 manual run: same popup, OCR put a 、 inside the sentence. */
    @Test fun streakPopupWithCommaIsStillThePopup(){
        val comma=nov13Popup.map{if(it.text=="14日連続でチェックインし")l("14日連続でチェックインし、",124,488,582,525) else it}
        assertTrue(TikTokCheckinPolicy.popup(comma))
        assertTrue(TikTokCheckinPolicy.backDismissible(comma))
    }
    /** 21:42 manual run: no popup capture at all; OCR had handed the two halves back in block order. */
    @Test fun streakPopupIsReadInReadingOrder(){
        val shuffled=nov13Popup.reversed()
        assertTrue(TikTokCheckinPolicy.popup(shuffled))
        assertTrue(TikTokCheckinPolicy.backDismissible(shuffled))
    }
    @Test fun checkinButtonIsTheExactButtonUnderTheStreakCard(){
        assertEquals(checkin,TikTokCheckinPolicy.checkinButton(listOf(title,checkin,claimTitle,claim)))
        assertFalse(TikTokCheckinPolicy.checkinDone(listOf(title,checkin)))
        assertNull(TikTokCheckinPolicy.checkinButton(listOf(checkin)))
    }
    @Test fun checkinDoneForGreyButtonAndForTheOptionalAdOffer(){
        assertTrue(TikTokCheckinPolicy.checkinDone(listOf(title,l("明日チェックインしてP2,880を獲得",100,975,620,1015))))
        val ad=l("300ポイントを獲得 AD",200,980,520,1020)
        assertTrue(TikTokCheckinPolicy.checkinDone(listOf(title,ad)))
        assertFalse(TikTokCheckinPolicy.safeTarget(ad))
    }
    @Test fun claimNeedsItsOwnCardAndReadsTheAmount(){
        val lines=listOf(title,claimTitle,reward,claim)
        assertEquals(claim,TikTokCheckinPolicy.claimButton(lines))
        assertEquals("608",TikTokCheckinPolicy.claimAmount(lines))
        assertNull(TikTokCheckinPolicy.claimButton(listOf(claim)))
    }
    @Test fun unlockTapsTheButtonNotTheYellowTag(){
        val t=l("翌日報酬が受け取れます",60,840,505,885)
        val tag=l("アンロック",105,935,220,975)
        val button=l("アンロック",285,1200,435,1245)
        assertEquals(button,TikTokCheckinPolicy.unlockButton(listOf(t,tag,button)))
        assertNull(TikTokCheckinPolicy.unlockButton(listOf(t,tag)))
        assertTrue(TikTokCheckinPolicy.unlockDone(listOf(l("明日受け取りに来てください",160,820,550,860))))
        assertFalse(TikTokCheckinPolicy.unlockDone(listOf(l("報酬が明日再度受け取りに来てください!",90,415,630,500))))
        assertTrue(TikTokCheckinPolicy.unlockPopup(listOf(l("リマインドする",255,940,465,985))))
    }
    @Test fun receiptAndBalanceAmounts(){
        val r=listOf(l("おめでとうございます!獲",140,500,585,545),l("得しました:",250,545,450,590),l("P 1,440",235,610,485,675),l("1日目",155,730,205,755),l("1,440",160,815,205,835))
        assertTrue(TikTokCheckinPolicy.checkinReceipt(r))
        assertEquals("1440",TikTokCheckinPolicy.receiptAmount(r))
        val head=listOf(l("獲得ポイント",55,488,225,520),l("1,304円分=",60,540,345,600),l("P130,478",55,605,240,645),l("交換",530,550,600,585))
        assertEquals(130478L,TikTokCheckinPolicy.balance(head))
        assertTrue(TikTokCheckinPolicy.giftPage(head+l("クーポンと交換",55,700,255,730)))
    }
    @Test fun neverTargetsCampaignReminderInviteOrExchange(){
        for(t in listOf("リマインドする","参加する","始める","招待する","交換","今すぐ回す","300ポイントを獲得 AD","スタート","受け取る"))
            assertFalse(t,TikTokCheckinPolicy.safeTarget(l(t,0,0,10,10)))
        for(t in listOf("チェックイン","獲得する","アンロック","OK","ギフト"))assertTrue(t,TikTokCheckinPolicy.safeTarget(l(t,0,0,10,10)))
    }
    @Test fun giftTabOnlyInBottomNavigation(){
        assertNotNull(TikTokCheckinPolicy.giftTab(listOf(l("ギフト",185,1515,245,1540)),1650))
        assertNull(TikTokCheckinPolicy.giftTab(listOf(l("ギフト",185,600,245,640)),1650))
        // Real OCR of the dimmed feed under the campaign popup, 2026-11-10 08:32.
        val dim=listOf(l("首頁",40,1515,100,1540),l("参加する",290,900,430,940),l("ギラト",185,1515,245,1540),l("攻件箱",470,1515,540,1540))
        assertTrue(TikTokCheckinPolicy.loaded(dim))
        assertNotNull(TikTokCheckinPolicy.giftTab(dim,1650))
        assertFalse(TikTokCheckinPolicy.loaded(listOf(l("参加する",290,900,430,940))))
    }

    private fun close(name:String,from:Int=300,avoid:List<CheckinLine> = emptyList()):Pair<Int,Int>?{
        // Luminance of columns 300..419 from the real 720x1650 screenshots (the detector only reads 321..399).
        val raw=GZIPInputStream(javaClass.classLoader!!.getResourceAsStream("tiktok/$name.lum.gz")).readBytes()
        val w=(raw[0].toInt() and 255 shl 8) or (raw[1].toInt() and 255);val h=(raw[2].toInt() and 255 shl 8) or (raw[3].toInt() and 255)
        val lum={x:Int,y:Int->if(x<300||x>=420||y<0||y>=h)0 else raw[4+y*120+(x-300)].toInt() and 255}
        return TikTokCheckinPolicy.closeButton(lum,w,h,from,h-120,avoid)
    }
    private fun near(p:Pair<Int,Int>?,x:Int,y:Int)=p!=null&&kotlin.math.abs(p.first-x)<=8&&kotlin.math.abs(p.second-y)<=10
    @Test fun closeButtonFoundUnderRealPopups(){
        assertTrue(close("d2f").toString(),near(close("d2f"),360,1269))   // check-in receipt with ad offer
        assertTrue(close("d2j").toString(),near(close("d2j"),360,1092))   // unlock reminder popup
        assertTrue(close("d3a").toString(),near(close("d3a"),360,1109))   // feed campaign popup
        assertTrue(close("tk24").toString(),near(close("tk24"),360,1309)) // day-1 receipt: lowest is the round close
    }
    @Test fun cardAfterTapProvesCheckinWhenReceiptWasMissed(){
        // Real 11/12 11:41 card after the 07:30 tap whose receipt popup was not recognised.
        val card=listOf(l("14日間連続チェックインで",60,840,560,890),l("P40,320を獲得",60,895,330,935),
            l("明日",380,1095,440,1125),l("1,440",370,1160,450,1190),l("明日チェックインしてP1,440を獲得",110,1270,610,1310))
        assertTrue(TikTokCheckinPolicy.checkinDone(card))
        assertNull(TikTokCheckinPolicy.checkinButton(card))
    }
    @Test fun feedCheckinPopupIsClosedNotTapped(){
        // Real OCR of the feed at the 11/11 07:30 run (gift page never loaded behind it).
        val lines=listOf(l("首頁",40,1515,100,1540),l("14日連続でチェックインし",120,480,600,530),l("て獲得しよう",200,540,520,590),
            l("今日",380,700,440,730),l("2,880",380,740,450,770),l("チェックイン",260,1125,460,1170),l("ギフト",185,1515,245,1540))
        assertTrue(TikTokCheckinPolicy.popup(lines))
        assertNull("feed popup is not the gift page check-in card",TikTokCheckinPolicy.checkinButton(lines))
        assertTrue(close("feedcheckin").toString(),near(close("feedcheckin"),360,1332))
    }
    @Test fun noCloseButtonOnPlainPagesOrVideo(){
        assertNull(close("d2e"));assertNull(close("d2b"))
    }
    @Test fun closeButtonInsideForbiddenTextIsRejected(){
        assertNull(close("d2j",1000,listOf(l("x",330,1060,390,1125))))
    }
}
