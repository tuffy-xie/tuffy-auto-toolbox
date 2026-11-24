package com.anapocket.auto

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

/** 11/23 22:00, ANA 参加中 → 報酬未受取 on the phone; the user chose the claim deadline (not 参加日) as the filter. */
class RewardClaimPolicyTest {
    private val nov23=LocalDate.of(2026,11,23)

    @Test fun readsTheCardDeadline() {
        assertEquals(LocalDate.of(2026,11,24),RewardClaimPolicy.cardDeadline("11/24まで報酬を受け取りましょう！",nov23))
        assertEquals(LocalDate.of(2026,12,23),RewardClaimPolicy.cardDeadline("12/23まで報酬を受け取りましょう！",nov23))
        // A December card read in January belongs to the year before, a January card read in December to the next.
        assertEquals(LocalDate.of(2026,12,30),RewardClaimPolicy.cardDeadline("12/30まで報酬を受け取りましょう！",LocalDate.of(2027,1,2)))
        assertEquals(LocalDate.of(2027,1,5),RewardClaimPolicy.cardDeadline("1/5まで報酬を受け取りましょう！",LocalDate.of(2026,12,20)))
        assertNull(RewardClaimPolicy.cardDeadline("参加日：2026/10/25",nov23))
        assertNull(RewardClaimPolicy.cardDeadline("2026/11/30まで",nov23))
    }
    @Test fun readsTheSheetDeadline() {
        assertEquals(LocalDate.of(2026,11,24),RewardClaimPolicy.detailDeadline("受取期限：2026/11/24"))
        assertNull(RewardClaimPolicy.detailDeadline("参加日時：2026/10/25 00:38"))
    }
    @Test fun rangesFollowTheDeadline() {
        val today=nov23;val d={n:Long->today.plusDays(n)}
        assertTrue(RewardClaimPolicy.inRange(d(0),today,RewardRange.TODAY))
        assertFalse(RewardClaimPolicy.inRange(d(1),today,RewardRange.TODAY))
        assertTrue(RewardClaimPolicy.inRange(d(7),today,RewardRange.WEEK))
        assertFalse(RewardClaimPolicy.inRange(d(8),today,RewardRange.WEEK))
        assertTrue(RewardClaimPolicy.inRange(d(30),today,RewardRange.MONTH))
        assertFalse(RewardClaimPolicy.inRange(d(31),today,RewardRange.MONTH))
        assertTrue(RewardClaimPolicy.inRange(d(40),today,RewardRange.ALL))
        assertFalse(RewardClaimPolicy.inRange(d(-1),today,RewardRange.ALL))   // already expired
        assertTrue(RewardClaimPolicy.beyond(d(1),today,RewardRange.TODAY))
        assertFalse(RewardClaimPolicy.beyond(d(400),today,RewardRange.ALL))
    }
    @Test fun sheetTexts() {
        assertTrue(RewardClaimPolicy.claimed("報酬受け取り済み"))
        assertFalse(RewardClaimPolicy.claimed("チャレンジ達成！報酬を受け取りましょう！"))
        assertTrue(RewardClaimPolicy.receiptDialog(listOf("報酬を受け取りました\nおつかれさまでした!","OK")))
        assertFalse(RewardClaimPolicy.receiptDialog(listOf("11/24まで報酬を受け取りましょう！")))
        assertTrue(RewardClaimPolicy.choiceSheet(listOf("受け取りたい報酬を選んでください","動画を見て","250pt","または","200pt")))
        assertTrue(RewardClaimPolicy.receiveButton("報酬を受け取る"))
        assertEquals(250,RewardClaimPolicy.points("250pt"))
        assertEquals(1000,RewardClaimPolicy.points("1,000pt"))
        assertNull(RewardClaimPolicy.points("ブースト5回"))
    }
    @Test fun plainPointsOnlyToSaveAnExpiringReward() {
        assertFalse(RewardClaimPolicy.plainFallback(true,0,0))
        assertTrue(RewardClaimPolicy.plainFallback(true,RewardClaimPolicy.MAX_AD_ATTEMPTS,0))
        assertTrue(RewardClaimPolicy.plainFallback(false,0,0))
        assertFalse(RewardClaimPolicy.plainFallback(false,0,1))
        assertFalse(RewardClaimPolicy.plainFallback(true,5,3))
    }
    @Test fun rewardsOnlyMeansNothingElseIsSelected() {
        val none=AppSettings().let{s->s.copy(adEnabled=false,catEnabled=Cat.values().associate{it.name to false})}
        assertFalse(RewardClaimPolicy.rewardsOnly(none))
        assertTrue(RewardClaimPolicy.rewardsOnly(none.copy(rewardEnabled=true)))
        assertFalse(RewardClaimPolicy.rewardsOnly(none.copy(rewardEnabled=true,adEnabled=true)))
        assertFalse(RewardClaimPolicy.rewardsOnly(none.copy(rewardEnabled=true,catEnabled=none.catEnabled+("CAR" to true))))
    }
    @Test fun rewardsAreOffAndDueTodayByDefault() {
        // org.json is not available to plain unit tests; the JSON keys are covered by the device install.
        assertFalse(AppSettings().rewardEnabled);assertEquals(RewardRange.TODAY,RewardRange.of(AppSettings().rewardRange))
        assertEquals(RewardRange.WEEK,RewardRange.of("WEEK"));assertEquals(RewardRange.TODAY,RewardRange.of("bogus"));assertEquals(RewardRange.TODAY,RewardRange.of(null))
    }

    @Test fun rewardsNeverLeaveSomethingOpen() {
        assertTrue(RewardClaimPolicy.mayRun(false,false,false,false,false))
        assertFalse(RewardClaimPolicy.mayRun(true,false,false,false,false))
        assertFalse(RewardClaimPolicy.mayRun(false,true,false,false,false))
        assertFalse(RewardClaimPolicy.mayRun(false,false,true,false,false))
        assertFalse(RewardClaimPolicy.mayRun(false,false,false,true,false))
        assertFalse(RewardClaimPolicy.mayRun(false,false,false,false,true))
    }
    @Test fun aLateLoginOfferIsHandledOnceBeforeTheScanReruns() {
        // 11/24 00:03: STARTUP NOT_PRESENT at 00:03:23, the ログインボーナス sheet at 00:03:24, every category SCAN_FAILED.
        val seen="挑战执行中止：startup_popup_blocks_category_navigation"
        assertTrue(LateLoginPolicy.retry(seen,"NOT_PRESENT",false,false))
        assertTrue(LateLoginPolicy.retry("category_popup_blocks_navigation",null,false,false))
        assertFalse(LateLoginPolicy.retry(seen,"REWARD_CONFIRMED",false,false))   // the offer is not a login one
        assertFalse(LateLoginPolicy.retry(seen,"UNCONFIRMED",false,false))        // never resubmit an unconfirmed login
        assertFalse(LateLoginPolicy.retry(seen,"NOT_PRESENT",true,false))         // a daily viewing is still open
        assertFalse(LateLoginPolicy.retry(seen,"NOT_PRESENT",false,true))         // travel review only
        assertFalse(LateLoginPolicy.retry("join_not_confirmed_CAR","NOT_PRESENT",false,false))
    }

    private class Port(val blockChallenges:Boolean=false,val adsContinue:Boolean=true,val startupContinue:Boolean=true):RoutinePort {
        val calls=mutableListOf<String>();var blockedSeen:Boolean?=null
        override fun stopped()=false
        override fun sameDate()=true
        override suspend fun progress(stage:String,result:RoutineStepResult){}
        override suspend fun startup():RoutineStepResult{calls.add("startup");return RoutineStepResult(if(startupContinue)"DONE" else "BLOCKED","login",startupContinue)}
        override suspend fun challenges():RoutineStepResult{calls.add("challenges")
            return if(blockChallenges)RoutineStepResult("BLOCKED","x",true) else RoutineStepResult("DONE","")}
        override suspend fun ads():RoutineStepResult{calls.add("ads");return if(adsContinue)RoutineStepResult("DONE","") else RoutineStepResult("BLOCKED","unknown kept",false)}
        override suspend fun rewards(blockedBefore:Boolean):RoutineStepResult{calls.add("rewards");blockedSeen=blockedBefore
            return RoutineStepResult("PARTIALLY_COMPLETED","left",false)}
    }
    @Test fun rewardsRunLastAndNeverBlockTheDay() = runBlocking {
        val p=Port();val r=RoutineSequence(p).run()
        assertEquals(listOf("startup","challenges","ads","rewards"),p.calls)
        assertEquals(false,p.blockedSeen)
        assertEquals("PARTIALLY_COMPLETED",r.state)
        assertTrue(r.steps.getValue("REWARDS").continueRun)
    }
    @Test fun anEarlierBlockReachesTheRewardStep() = runBlocking {
        val p=Port(blockChallenges=true);val r=RoutineSequence(p).run()
        assertEquals(true,p.blockedSeen);assertEquals("BLOCKED",r.state)
        // 11/24: the daily-ad stop (UNKNOWN kept) lasted all day; the rewards still run and the day stays BLOCKED.
        val ads=Port(adsContinue=false);val r2=RoutineSequence(ads).run()
        assertEquals(listOf("startup","challenges","ads","rewards"),ads.calls);assertEquals(true,ads.blockedSeen)
        assertEquals("BLOCKED",r2.state);assertEquals("unknown kept",r2.detail)
        // Any earlier stop still ends the run before the rewards.
        val login=Port(startupContinue=false);assertEquals("BLOCKED",RoutineSequence(login).run().state)
        assertEquals(listOf("startup"),login.calls)
    }
}
