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
    @Test fun MaximumAndStartAreNeverAWonReceipt(){
        assertNull(CheckinBonusPolicy.outcome(listOf(line("最大1,000ポイント",200),line("スタート",900)),true))
        assertNull(CheckinBonusPolicy.outcome(listOf(line("最大1,000ポイント",200)),false))
    }
}
