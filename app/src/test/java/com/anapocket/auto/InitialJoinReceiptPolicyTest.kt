package com.anapocket.auto
import org.junit.Assert.*
import org.junit.Test
class InitialJoinReceiptPolicyTest {
    private val labels=listOf("チャレンジをはじめました！", "1回目のブーストをしますか？", "現在のチャレンジ達成後の報酬：", "100pt", "+50", "+50", "+50", "+100", "+100")
    private val steps=listOf(50,50,50,100,100)
    @Test fun actualLateJoinReceiptMatchesOriginalAdJoinAmountAndFullBoostTable() { assertTrue(InitialJoinReceiptPolicy.matches(100,steps,labels)) }
    @Test fun freeJoinAmountAndMaximumCannotSubstituteForAdJoinBaseline() {
        assertFalse(InitialJoinReceiptPolicy.matches(10,steps,labels)); assertFalse(InitialJoinReceiptPolicy.matches(450,steps,labels))
    }
    @Test fun missingJoinMessageOrChangedOrdinalOrTableCannotResolveUnknownAttempt() {
        assertFalse(InitialJoinReceiptPolicy.matches(100,steps,labels.drop(1)))
        assertFalse(InitialJoinReceiptPolicy.matches(100,steps,labels.map { if(it.startsWith("1回目")) "2回目のブーストをしますか？" else it }))
        assertFalse(InitialJoinReceiptPolicy.matches(100,listOf(25,25,25,50,50),labels))
    }
}
