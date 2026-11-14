package com.anapocket.auto

import org.junit.Assert.*
import org.junit.Test

class DigestPolicyTest {
    private val anaDone=DigestAna(true,24,24,25,true,"签到已领",0,1,"")
    private val steak=DigestApp("いきなり！ステーキ",true,1,1,emptyList(),"5 肉マネークーポン（円）（次日入账）",false)
    private val rakuten=DigestApp("楽天PointClub",true,5,6,listOf("三项达成额外抽奖待核对"),"",false)

    @Test fun nov8ReadsAsOneShortReport(){
        val t=DigestPolicy.render("2026-11-08",anaDone,listOf(rakuten,steak),null)
        assertEquals("""
            Tuffy｜11/08 日报
            ✅ ANA：挑战 24/24 · 广告 25/25 · 签到已领
            ⚠️ 楽天PointClub：5/6 完成 · 三项达成额外抽奖待核对
            ✅ いきなり！ステーキ：1/1 完成 · 5 肉マネークーポン（円）（次日入账）
            无需处理
        """.trimIndent(),t)
        assertFalse(t.contains("RUN_ID"));assertFalse(t.contains("manual"));assertFalse(Regex("[A-Z]{4,}_[A-Z]").containsMatchIn(t))
        assertTrue(t.length<1000)
    }
    @Test fun waitsForAnaAndEveryEnabledCheckin(){
        assertTrue(DigestPolicy.ready(anaDone,listOf(rakuten,steak)))
        assertFalse(DigestPolicy.ready(anaDone.copy(done=false),listOf(steak)))
        assertFalse(DigestPolicy.ready(anaDone,listOf(steak.copy(finished=false))))
        assertTrue(DigestPolicy.ready(null,listOf(steak)))
        assertFalse(DigestPolicy.ready(null,emptyList()))
    }
    @Test fun deadlineFollowsTheLatestCheckin(){
        assertEquals(10*60,DigestPolicy.deadlineMinutes(listOf(7*60,7*60+15)))
        assertEquals(10*60,DigestPolicy.deadlineMinutes(emptyList()))
        assertEquals(22*60+30,DigestPolicy.deadlineMinutes(listOf(21*60+30)))
        assertEquals(23*60,DigestPolicy.deadlineMinutes(listOf(23*60+30)))
    }
    @Test fun unfinishedDayAsksForActionOnce(){
        val stuck=anaDone.copy(done=false,challengesDone=10,official=0,reason="出行分类页未能打开，进度已保存")
        val t=DigestPolicy.render("2026-11-08",stuck,listOf(steak.copy(finished=false)),null)
        assertTrue(t.contains("⚠️ ANA：挑战 10/24 · 广告 0/25"))
        assertTrue(t.contains("出行分类页未能打开"))
        assertTrue(t.contains("➖ いきなり！ステーキ：今天还没执行"))
        assertTrue(t.endsWith("需要你处理：ANA未完成，打开Tuffy查看"))
        assertEquals("Tuffy｜11/08 补充\n✅ ANA：挑战 24/24 · 广告 25/25 · 签到已领",DigestPolicy.followUp("2026-11-08",anaDone))
    }
    @Test fun unconfirmedAdIsNamedOnlyWhileItCanStillMatter(){
        // Nov 8: one black-screen ad unconfirmed, official 25/25 — nothing to say about it.
        assertFalse(DigestPolicy.anaLine(anaDone).contains("未确认"))
        assertTrue(DigestPolicy.anaLine(anaDone.copy(done=false,official=21)).contains("广告 1 次结果未确认"))
        assertTrue(DigestPolicy.anaLine(anaDone.copy(travelUnknown=2)).contains("出行 2 次结果未确认"))
        assertFalse(DigestPolicy.anaLine(anaDone).contains("待核对"))
    }
    @Test fun safetyAndHoldsAreNamed(){
        val t=DigestPolicy.render("2026-11-08",anaDone.copy(done=false),listOf(rakuten.copy(hold=true)),"登录提示")
        assertTrue(t.endsWith("需要你处理：ANA账号保护（登录提示）；楽天PointClub需要人工验证"))
    }
    @Test fun loginLabels(){
        assertEquals("签到已领",DigestPolicy.loginLabel("REWARD_CONFIRMED"))
        assertEquals("签到待核对",DigestPolicy.loginLabel("FAILED"))
        assertEquals("签到未检查",DigestPolicy.loginLabel(""))
    }
}
