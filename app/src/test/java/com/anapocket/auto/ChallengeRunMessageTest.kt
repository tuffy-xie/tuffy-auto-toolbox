package com.anapocket.auto
import org.junit.Assert.*
import org.junit.Test
class ChallengeRunMessageTest {
 @Test fun bothNewCategoriesAreNamedInTheirOwnResult(){val t=ChallengeRunMessage.render("D",listOf(ChallengeRunItem("新干线100km",5,true,false),ChallengeRunItem("飞机200km",5,true,false)));assertTrue(t.contains("已接取并升满：新干线100km、飞机200km"));assertFalse(t.contains("积分到账"))}
 @Test fun unknownOrIncompleteCannotBeReportedAsFullyBoosted(){val t=ChallengeRunMessage.render("D",listOf(ChallengeRunItem("飞机200km",5,true,true),ChallengeRunItem("新干线100km",2,true,false)));assertFalse(t.contains("✅"));assertTrue(t.contains("待核对：飞机200km"));assertTrue(t.contains("Boost 2/5"))}
 @Test fun homeFailureNeverContainsRawCountersOrSecrets(){assertFalse(TaskFailureText.short("今日启动26/25，未确认5，官方次数跳变").contains("26/25"));assertEquals("任务已暂停，原因详见执行记录",TaskFailureText.short("https://private.invalid/secret"))}
}
