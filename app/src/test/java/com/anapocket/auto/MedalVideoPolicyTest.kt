package com.anapocket.auto

import org.junit.Assert.*
import org.junit.Test

/** Labels as seen on the phone, Nov 8 19:14–19:22. */
class MedalVideoPolicyTest {
    @Test fun readsTheHomeChipAndThePage(){
        assertEquals(13,MedalVideoPolicy.counter(listOf("ANA Pocket プラン","13/50","Pro")))
        assertEquals(14,MedalVideoPolicy.counter(listOf("１４ / ５０")))
        assertNull(MedalVideoPolicy.counter(listOf("50点貯めてバッジを獲得","11/15まで有効：7,166pt")))
        assertTrue(MedalVideoPolicy.badgePage(listOf("マンスリーバッジ","今月のバッジ")))
    }
    @Test fun readsTheChipWhenItsTextIsSplit(){
        assertEquals(15,MedalVideoPolicy.counter(listOf("ANA Pocket 抽選会","15","/50","ヘルスケア情報")))
        assertNull(MedalVideoPolicy.counter(listOf("/50","ヘルスケア情報")))
        assertNull(MedalVideoPolicy.counter(listOf("ANA Pocket","/50")))
        assertTrue(MedalVideoPolicy.chipLabel("/50"))
        assertTrue(MedalVideoPolicy.chipLabel("15/50"))
        assertFalse(MedalVideoPolicy.chipLabel("15"))
    }
    @Test fun findsTheButtonButNotTheSentenceAboveIt(){
        assertTrue(MedalVideoPolicy.offerButton("動画を見て 1点"))
        assertTrue(MedalVideoPolicy.offerButton("動画を見て１点"))
        assertFalse(MedalVideoPolicy.offerButton("動画を見て、昨日の1点を獲得しましょう。"))
        assertTrue(MedalVideoPolicy.claimed(listOf("動画視聴で、昨日の●1点を獲得しました！")))
        assertFalse(MedalVideoPolicy.claimed(listOf("動画を見て、昨日の1点を獲得しましょう。")))
    }
    @Test fun findsTheButtonWhenItsTextIsSplit(){
        assertTrue(MedalVideoPolicy.offerParts("動画を見て","1点"))
        assertTrue(MedalVideoPolicy.offerParts("動画を見て","１点"))
        assertFalse(MedalVideoPolicy.offerParts("動画を見て、昨日の1点を獲得しましょう。","動画を見て"))
        assertFalse(MedalVideoPolicy.offerParts("動画を見て","2点"))
    }
    @Test fun waitsOutTheRewardCountdownAndKeepsWatching(){
        assertTrue(MedalVideoPolicy.rewardPending(listOf("Ad 2 of 3 • Reward in 5")))
        assertFalse(MedalVideoPolicy.rewardPending(listOf("Ad 1 of 3","Pre-register")))
        assertTrue(MedalVideoPolicy.resumeLabel("Resume video"))
        assertFalse(MedalVideoPolicy.resumeLabel("Close video"))
    }
    @Test fun onceADayAndNeverReplayedAfterTheTap(){
        assertTrue(MedalVideoPolicy.mayStart(null))
        assertTrue(MedalVideoPolicy.mayStart("FAILED_BEFORE_TAP"))
        assertTrue(MedalVideoPolicy.mayStart("NOT_OFFERED"))
        for(s in listOf("DISPATCHED","UNCONFIRMED","CONFIRMED","ALREADY_CLAIMED","FAILED"))assertFalse(s,MedalVideoPolicy.mayStart(s))
    }
    @Test fun rewardNeedsTheChipToMoveByOneOrTheClaimedText(){
        assertTrue(MedalVideoPolicy.confirmed(13,14,false))
        assertTrue(MedalVideoPolicy.confirmed(13,null,true))
        assertFalse(MedalVideoPolicy.confirmed(13,13,false))
        assertFalse(MedalVideoPolicy.confirmed(13,15,false))
        assertFalse(MedalVideoPolicy.confirmed(null,14,false))
    }
    @Test fun reportNamesTheMedal(){
        val a=DigestAna(true,24,24,25,true,"签到已领",0,0,"","奖牌已领")
        assertEquals("✅ ANA：挑战 24/24 · 广告 25/25 · 签到已领 · 奖牌已领",DigestPolicy.anaLine(a))
    }
}
