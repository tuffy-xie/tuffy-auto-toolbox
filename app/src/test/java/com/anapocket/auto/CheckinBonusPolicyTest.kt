package com.anapocket.auto

import org.junit.Assert.*
import org.junit.Test

class CheckinBonusPolicyTest {
    private fun line(t:String,y:Int)=CheckinLine(t,30,y,680,y+40)
    @Test fun officialLoadingFailureIsNeverAResult(){
        val error="読み込みに時間がかかっています\nしばらく時間を置いてから\n再度お試しください。"
        assertTrue(CheckinBonusPolicy.loadingError(error))
        assertNull(CheckinBonusPolicy.outcome(listOf(line(error,200)),false))
        assertFalse(CheckinBonusPolicy.loadingError("最大1,000ポイント 読み込み中"))
    }
    @Test fun actualStopInstructionRequiresTheSameSubmittedDraw(){
        val t="ストップ を押して 今日のカードを選ぼう"
        assertTrue(CheckinBonusPolicy.stopReady(t,true,true,false))
        assertFalse(CheckinBonusPolicy.stopReady(t,false,true,false))
        assertFalse(CheckinBonusPolicy.stopReady(t,true,true,true))
        assertFalse(CheckinBonusPolicy.stopReady("今日のボーナスくじ 最大1,000ポイント",true,true,false))
    }
    @Test fun footerMarketingDoesNotReplaceAnExplicitLoss(){
        val result=listOf(line("ハズレ",400),line("今日のボーナスくじの楽しみ方",1191),line("最大1,000ポイントが当たる抽選を毎日楽しめます",1305))
        assertEquals("LOST",CheckinBonusPolicy.outcome(result,false))
        assertNull(CheckinBonusPolicy.outcome(result,true))
    }
    /** OCR of the real result page from CI_RAKUTEN_f51a54f7 (2026-11-08 16:22). */
    private val nov8Result=listOf(
        CheckinLine("今日のボーナスくじ",218,92,500,122),CheckinLine("GET!",300,393,415,444),
        CheckinLine("ポイントは翌日中に進呈されます。",194,786,513,805),
        CheckinLine("ポイント実績ページの「ポイント獲得·活用実績」よりご確認ください。",13,815,695,835),
        CheckinLine("明日またきてね",251,934,471,963),
        CheckinLine("進呈する楽天ポイント(通常ポイント)には上限·条件がありま",65,1045,670,1069),
        CheckinLine("今日のボーナスくじの楽しみ方",109,1218,610,1252),
        CheckinLine("下記3つを達成することで、最大1,000ポイントが当",34,1310,672,1339),
        CheckinLine("1ポイント",32,1500,183,1537),CheckinLine("ミッション」",35,1537,179,1559))
    @Test fun currentResultPageWithNextDayCreditIsAWin(){
        assertEquals("WON",CheckinBonusPolicy.outcome(nov8Result,false))
        assertNull(CheckinBonusPolicy.outcome(nov8Result,true))
    }
    @Test fun bannerBelowTheRulesIsNotThePrize(){
        // "1ポイント" here belongs to the 1ポイントミッション banner, so the amount stays unknown.
        assertNull(CheckinReceiptPolicy.rewardAmount(CheckinBonusPolicy.aboveFooter(nov8Result),"ポイント"))
        val readCard=nov8Result+CheckinLine("1ポイント",250,330,470,385)
        assertEquals("1",CheckinReceiptPolicy.rewardAmount(CheckinBonusPolicy.aboveFooter(readCard),"ポイント"))
    }
    @Test fun creditNoticeNeedsBothTextsAndNoLoss(){
        val credit=line("ポイントは翌日中に進呈されます。",780);val again=line("明日またきてね",930)
        assertNull(CheckinBonusPolicy.outcome(listOf(credit),false))
        assertNull(CheckinBonusPolicy.outcome(listOf(again),false))
        assertNotEquals("WON",CheckinBonusPolicy.outcome(listOf(line("ハズレ…",400),credit,again),false))
        // The same texts inside the rules section are not a result.
        assertNull(CheckinBonusPolicy.outcome(listOf(line("今日のボーナスくじの楽しみ方",500),credit,again),false))
    }
    @Test fun submittedDrawIsReopenedOnlyToReadItsResult(){
        // Nov 8 17:01: the reopened app still showed loading placeholders; the submitted draw was downgraded to BLOCKED.
        assertEquals(CheckinBonusPolicy.Entry.KEEP_UNCONFIRMED,CheckinBonusPolicy.entry(false,true,"START_SUBMITTED"))
        assertEquals(CheckinBonusPolicy.Entry.REVIEW_SUBMITTED,CheckinBonusPolicy.entry(true,true,"START_SUBMITTED"))
        assertEquals(CheckinBonusPolicy.Entry.NEW_ENTRY,CheckinBonusPolicy.entry(true,true,"ENTRY_RESERVED"))
        assertEquals(CheckinBonusPolicy.Entry.NEW_ENTRY,CheckinBonusPolicy.entry(true,false,""))
        assertEquals(CheckinBonusPolicy.Entry.BLOCKED,CheckinBonusPolicy.entry(false,false,""))
        assertEquals(CheckinBonusPolicy.Entry.KEEP_UNCONFIRMED,CheckinBonusPolicy.entry(true,true,""))
    }
    /** Earn page OCR from CI_RAKUTEN_4c4ba078 (2026-11-08 17:26) after the day's draw. */
    private val nov8Earn=listOf(
        CheckinLine("1ポイント",43,687,200,720),CheckinLine("達成",89,836,140,860),CheckinLine("(1日2回引ける!リ",273,617,450,640),
        CheckinLine("3つ0達成!今日のボーナスくじに進もう@",59,926,660,955),CheckinLine("本日分は抽選済みです",221,1002,500,1030),
        CheckinLine("·進呈する楽天ポイント(通常ポイント)には上限·条件がありま",41,1079,680,1100))
    @Test fun drawnMarkerSettlesTheDayWithoutOpeningTheBonus(){
        assertTrue(CheckinBonusPolicy.drawnToday(nov8Earn))
        // 17:26: this line was tapped as the entry and the bonus page never opened.
        assertNull(CheckinReceiptPolicy.bonusEntry(nov8Earn))
        assertEquals(CheckinState.SUCCESS,CheckinBonusPolicy.drawnState(true,"START_SUBMITTED"))
        assertEquals(CheckinState.ALREADY_DONE,CheckinBonusPolicy.drawnState(false,""))
        assertEquals(CheckinState.ALREADY_DONE,CheckinBonusPolicy.drawnState(true,"ENTRY_RESERVED"))
        assertNull(CheckinBonusPolicy.drawnState(true,""))
    }
    @Test fun openEntryIsNotADrawnMarker(){
        val open=listOf(CheckinLine("3つ達成!今日のボーナスくじに進もう",59,926,660,955),CheckinLine("最大1,000ポイントが当たる抽選に挑戦",60,1002,660,1030))
        assertFalse(CheckinBonusPolicy.drawnToday(open))
        assertNotNull(CheckinReceiptPolicy.bonusEntry(open))
    }
    /** OCR of the real result page from CI_RAKUTEN_ea80f4e0 (2026-11-13 07:03). */
    private val nov13NoPrize=listOf(
        CheckinLine("今日のボーナスくじ",218,92,500,122),CheckinLine("ポイントGETならず.",232,269,487,295),
        CheckinLine("\\今日1日が/",298,339,415,362),CheckinLine("ハッピーになる一言",232,378,480,421),
        CheckinLine("湯船に浸かって",289,673,430,693),CheckinLine("疲れをとろう!",289,703,424,723),
        CheckinLine("明日またきてね",251,934,471,963),
        CheckinLine("進呈する楽天ポイント(通常ポイント)には上限·条件がありま",65,1045,670,1069),
        CheckinLine("今日のボーナスくじの楽しみ方",109,1218,610,1252),
        CheckinLine("1ポイント",32,1500,183,1537),CheckinLine("ミッション」",35,1537,179,1559))
    @Test fun getNarazuPageIsANoPrizeResult(){
        assertEquals("LOST",CheckinBonusPolicy.outcome(nov13NoPrize,false))
        assertNull(CheckinBonusPolicy.outcome(nov13NoPrize,true))
        assertNull(CheckinBonusPolicy.outcome(listOf(line("ポイントGETならず.",270)),false))
        assertNull(CheckinBonusPolicy.outcome(nov13NoPrize+line("ポイントは翌日中に進呈されます。",786),false))
    }
    @Test fun MaximumAndStartAreNeverAWonReceipt(){
        assertNull(CheckinBonusPolicy.outcome(listOf(line("最大1,000ポイント",200),line("スタート",900)),true))
        assertNull(CheckinBonusPolicy.outcome(listOf(line("最大1,000ポイント",200)),false))
    }
}
