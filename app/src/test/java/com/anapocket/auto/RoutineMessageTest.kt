package com.anapocket.auto
import org.junit.Assert.*
import org.junit.Test
class RoutineMessageTest {
 @Test fun incompleteListsWhatWasDoneAndWhatWasNot(){val text=RoutineMessage.render("2026-10-28",listOf("签到","徒步"),listOf("汽车"),listOf("飞机"),emptyList(),"官方8/25","已领取","网络失败",true);assertTrue(text.contains("已完成：签到、徒步"));assertTrue(text.contains("待完成：汽车"));assertTrue(text.contains("未开启：飞机"));assertFalse(text.contains("今日任务完成"));assertTrue(text.contains('\n'));assertFalse(text.contains("\\n"))}
 @Test fun unknownNeverBecomesCompleteOrPendingWork(){val t=RoutineMessage.render("D",listOf("徒步"),emptyList(),emptyList(),listOf("广告4次"),"官方25/25","已领取","",false);assertTrue(t.contains("结果待核对"));assertFalse(t.contains("今日任务完成"))}
 @Test fun missingSigninIsNotClaimed(){val t=RoutineMessage.render("D",listOf("徒步"),emptyList(),emptyList(),emptyList(),"官方25/25","本次未出现弹窗","",false);assertFalse(t.contains("今日任务完成"));assertTrue(t.contains("本次未出现弹窗"))}
 @Test fun onlyAllConfirmedReportsComplete(){assertTrue(RoutineMessage.render("D",listOf("签到","徒步"),emptyList(),listOf("飞机"),emptyList(),"官方25/25","已领取","",false).contains("今日任务完成"))}
}
