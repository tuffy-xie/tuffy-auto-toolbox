package com.anapocket.auto
import org.junit.Assert.*
import org.junit.Test

class DailyCardDeadlineTest {
    @Test fun actualSameDayCardIsEligible() { assertTrue(DailyCardDeadline.matches("2026/10/28まで", "2026-10-28")) }
    @Test fun weeklyExpiredAndUnreadableAreExcluded() {
        for (d in listOf("2026/11/1まで", "2026/10/27まで", "", null)) assertFalse(DailyCardDeadline.matches(d,"2026-10-28"))
    }
    @Test fun observedNonstandardDailyWalkTitleIsRecognized() {
        val c = Cards.fromTitle("元気にお出かけ💪1日5km歩こう🚶")!!
        assertEquals(Cat.WALK,c.cat); assertEquals(5000,c.meters)
    }
    @Test fun nonstandardTitleObeysInclusiveKilometreLimit() {
        val c = Cards.fromTitle("元気にお出かけ💪1日5km歩こう🚶")!!
        assertEquals(1,Cards.select(listOf(c),Cat.WALK,5000).matched.size)
        assertEquals(0,Cards.select(listOf(c),Cat.WALK,4000).matched.size)
    }
    @Test fun weeklyAndMixedTitlesAreStillRejected() {
        for (t in listOf("ONEコラボ記念！1週間で徒歩7km移動しよう！", "徒歩や自転車で合計2kmのエコ移動をしよう！", "元気にお出かけ1週間で5km歩こう")) assertNull(Cards.fromTitle(t))
    }
    @Test fun unknownSingleModeDailyDistanceCannotBeSilentlySkipped() { assertTrue(UnknownDailyCardPolicy.mustReview("新しい毎日5kmチャレンジ",true,1)) }
    @Test fun knownDailyAndNonDistanceAreNotUnknownDistanceTasks() { assertFalse(UnknownDailyCardPolicy.mustReview("徒歩で5km移動しよう！",true,1));assertFalse(UnknownDailyCardPolicy.mustReview("店舗にチェックイン",false,1)) }
    @Test fun weeklyAndMixedRemainOutsideSingleModeDailyScope() { assertFalse(UnknownDailyCardPolicy.mustReview("1週間で徒歩7km",true,1));assertFalse(UnknownDailyCardPolicy.mustReview("徒歩や自転車で合計2kmのエコ移動をしよう！",true,2)) }
}
