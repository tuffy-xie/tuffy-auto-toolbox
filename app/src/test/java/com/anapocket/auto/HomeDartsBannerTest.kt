package com.anapocket.auto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeDartsBannerTest {
    private val h=1650
    private fun l(t:String,top:Int)=CheckinLine(t,160,top,630,top+30)

    // 11/28: the banner sat below 年末クーポン祭 and above 肉マイレージランキング after three swipes.
    @Test fun findsTheFixedBannerBelowTheCarousel(){
        val lines=listOf(l("年末クーポン祭",170),l("1日",280),l("毎日",300),l("ャレンン!",370),
            l("いきなり!ダーツで当たる♪肉マネークーポン",430),l("肉マイレージランキング",520),l("ホーム",1523))
        assertEquals("いきなり!ダーツで当たる♪肉マネークーポン",CheckinReceiptPolicy.homeDartsEntry(lines,h)?.text)
    }
    @Test fun theBannerTextAloneIsEnough(){
        assertTrue(CheckinReceiptPolicy.homeDartsBanner("いきなり!ダーツで当たる 肉マネークーポン"))
        assertTrue(CheckinReceiptPolicy.homeDartsBanner("毎日 ダーツ"))
        assertFalse(CheckinReceiptPolicy.homeDartsBanner("肉マネークーポン 毎日"))
    }
    @Test fun neverAPaidOrScratchBanner(){
        assertFalse(CheckinReceiptPolicy.homeDartsBanner("ダーツで当たる スクラッチ祭り"))
        assertFalse(CheckinReceiptPolicy.homeDartsBanner("毎日ダーツ 500円以上 購入"))
        val lines=listOf(l("2026スクラッチ祭り!",400),l("いきなり!ダーツで当たる",430))
        assertNull(CheckinReceiptPolicy.homeDartsEntry(lines,h))
    }
    @Test fun notUnderTheHeaderOrTheMileageCard(){
        assertNull(CheckinReceiptPolicy.homeDartsEntry(listOf(l("いきなり!ダーツで当たる",100)),h))
        assertNull(CheckinReceiptPolicy.homeDartsEntry(listOf(l("いきなり!ダーツで当たる",1250)),h))
    }
}
