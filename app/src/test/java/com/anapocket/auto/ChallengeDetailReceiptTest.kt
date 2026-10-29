package com.anapocket.auto
import org.junit.Assert.*
import org.junit.Test
class ChallengeDetailReceiptTest {
    private fun matches(title: String? = "自転車で500m移動しよう！", date: String? = "2026/10/28", attended: String? = "参加日：2026/10/28", boost: String? = "ブースト5回", reward: Int? = 200) =
        ChallengeDetailReceipt.maxMatches("自転車で500m移動しよう！", "2026-10-28", title, date, attended, boost, reward)
    @Test fun actualNativeJoinedDetailConfirmsTaskMax() { assertTrue(matches()) }
    @Test fun wrongTaskOrDateCannotResolvePendingAd() {
        assertFalse(matches(title = "徒歩で500m移動しよう！"))
        assertFalse(matches(date = "2026/10/27"))
        assertFalse(matches(attended = "参加日：2026/10/27"))
    }
    @Test fun OfferedMaxWithoutFiveBoostsOrMissingReceiptIsRejected() {
        assertFalse(matches(boost = "ブースト1回"))
        assertFalse(matches(boost = null)); assertFalse(matches(reward = null))
    }
}
