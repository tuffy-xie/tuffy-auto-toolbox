package com.anapocket.auto

import org.junit.Assert.*
import org.junit.Test

class ChallengeSignalsTest {
    @Test fun lateExactOfficialReceiptCanBeReviewedButUnchangedOrJumpedCannot() {
        val l = listOf("4回目のブーストをしますか？", "現在のチャレンジ達成後の報酬：", "100pt", "+25", "+25", "+25", "+50", "+50")
        assertTrue(ChallengeSignals.reviewedAdvance(2, 75, 100, l))
        assertFalse(ChallengeSignals.reviewedAdvance(2, 75, 150, l))
        assertFalse(ChallengeSignals.reviewedAdvance(3, 75, 100, l))
        assertFalse(ChallengeSignals.reviewedAdvance(2, 75, 100, l.map { if (it == "100pt") "75pt" else it }))
    }
    @Test fun realOfficialResumedPanelConfirmsNoGainRatherThanReward() {
        val l = listOf("4回目のブーストをしますか？", "現在のチャレンジ達成後の報酬：", "85pt", "+25", "+25", "+25", "+30", "+35")
        assertTrue(ChallengeSignals.reviewedNoGain(3, 85, 115, l))
        assertFalse(ChallengeSignals.reviewedNoGain(3, 85, 150, l))
        assertFalse(ChallengeSignals.reviewedNoGain(4, 85, 115, l))
        assertFalse(AdSignals.receipt(l))
        assertFalse(ChallengeSignals.reviewedNoGain(3, 85, 115, listOf("5回目のブーストをしますか？", "現在のチャレンジ達成後の報酬：", "115pt", "+25", "+25", "+25", "+30", "+35")))
    }
    @Test fun officialFirstOrdinal() {
        assertEquals(1, ChallengeSignals.nextBoost(listOf("チャレンジをはじめました！", "1回目のブーストをしますか？")))
    }
    @Test fun nextOrdinalIsBounded() {
        assertEquals(5, ChallengeSignals.nextBoost(listOf("5回目のブーストをしますか？")))
        assertNull(ChallengeSignals.nextBoost(listOf("今日の視聴済み回数 25/25")))
    }
    @Test fun closeButtonIsNotJoinEvidence() {
        assertFalse(ChallengeSignals.joined(listOf("Close", "Google Play")))
    }
    @Test fun taskAcceptanceIsNotAccountReward() {
        val l = listOf("チャレンジをはじめました！", "1回目のブーストをしますか？")
        assertTrue(ChallengeSignals.joined(l))
        assertFalse(AdSignals.receipt(l))
    }
    @Test fun exactIncrementOnly() {
        assertTrue(ChallengeSignals.verifiedAdvance(2, 3))
        assertFalse(ChallengeSignals.verifiedAdvance(2, 2))
        assertFalse(ChallengeSignals.verifiedAdvance(2, 4))
        assertFalse(ChallengeSignals.verifiedAdvance(2, null))
    }
    @Test fun realPilotRewardCaptionExcludesPotentialMaxAndBanner() {
        val labels = listOf("チャレンジをはじめました！", "1回目のブーストをしますか？",
            "現在のチャレンジ達成後の報酬：", "25pt", "1回", "+25", "pt", "2回", "+25", "pt",
            "3回", "+25", "pt", "4回", "+50", "pt", "5回", "+50", "pt", "動画を見て報酬をブースト")
        assertEquals(25, ChallengeSignals.offeredReward(labels))
        assertEquals(listOf(25, 25, 25, 50, 50), ChallengeSignals.increments(labels))
        assertNull(ChallengeSignals.offeredReward(listOf("広告ブーストで最大", "200", "pt")))
    }
}
