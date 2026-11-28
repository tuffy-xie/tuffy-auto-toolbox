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
    // 11/28 19:26 manual run, the exact OCR after three swipes: ツ read as ッ, 毎日 and チャレンジ missing.
    @Test fun findsTheBannerFromTheLiveOcr(){
        fun r(t:String,top:Int,bottom:Int,left:Int,right:Int)=CheckinLine(t,left,top,right,bottom)
        val lines=listOf(r("2026.",153,168,39,78),r("10/28wed",168,193,42,136),r("Aきなリダーリ/",362,385,51,189),
            r("IKJNARI DARTs",384,398,67,184),r("いきなりダーッで当たる肉マークーポン",413,443,163,630),
            r("P肉マイレーシランキング",479,563,27,685),r("オシラインショッピング|",721,778,158,706),
            r("肉マイレージカード",1294,1321,52,222),r("ホーム",1523,1542,37,104))
        assertEquals("いきなりダーッで当たる肉マークーポン",CheckinReceiptPolicy.homeDartsEntry(lines,h)?.text)
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
