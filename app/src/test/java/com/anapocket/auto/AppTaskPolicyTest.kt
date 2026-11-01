package com.anapocket.auto

import org.junit.Assert.*
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.TimeZone

class AppTaskPolicyTest {
    @Test fun observedBonusHeadingMissingOneGlyphStillNeedsTodayLotteryAndRealBanner(){val h=CheckinLine("3つの達成!今日のーナスくじに進もう3",59,978,656,1009);val b=CheckinLine("抽選で",42,1051,158,1113);assertEquals(b,CheckinReceiptPolicy.bonusEntry(listOf(h,b)));assertNull(CheckinReceiptPolicy.bonusEntry(listOf(b)))}
    @Test fun greenStatusCandidateStillRequiresImageProofAndRejectsNegative(){assertTrue(CheckinReceiptPolicy.greenCompletionCandidate("う達成"));assertTrue(CheckinReceiptPolicy.greenCompletionCandidate("の達成"));assertFalse(CheckinReceiptPolicy.greenCompletionCandidate("未達成"));assertFalse(CheckinReceiptPolicy.greenCompletionCandidate("不達成"));assertFalse(CheckinReceiptPolicy.greenCompletionCandidate("3つ達成で1000ポイント"))}
    @Test fun bonusUsesRealBannerBelowHeadingNotExplanatoryHeading(){val h=CheckinLine("今日のボーナスくじに進もう",59,978,656,1009);val b=CheckinLine("抽選で",43,1052,157,1114);assertEquals(b,CheckinReceiptPolicy.bonusEntry(listOf(h,b)));assertNull(CheckinReceiptPolicy.bonusEntry(listOf(h,CheckinLine("最大1000ポイント",10,300,600,340))))}
    @Test fun observedCheckGlyphIsAcceptedOnlyUnderItsOwnCard(){val p=CheckinLine("1ポイント",43,687,215,726);val m=CheckinLine("ミッション、",42,728,216,774);val s=CheckinLine("の達成",89,836,165,864);assertTrue(CheckinReceiptPolicy.pointsDone(listOf(p,m,s)));assertFalse(CheckinReceiptPolicy.pointsDone(listOf(p,m,s.copy(text="未達成"))));assertFalse(CheckinReceiptPolicy.pointsDone(listOf(p,m,s.copy(left=356,right=400))))}
    @Test fun pickupTomorrowClosingCopyIsNotRewardEvidence(){val s="今日のピックアップ あなたが気になるしたくなるように、明日も頑張ってピックアップするね";assertTrue(CheckinReceiptPolicy.pickupEnd(s));assertFalse(CheckinReceiptPolicy.alreadyDone(s));assertFalse(CheckinReceiptPolicy.realReceipt(true,false,true,s));assertFalse(CheckinReceiptPolicy.pickupEnd("今日のピックアップ 最大1000ポイント"))}
    @Test fun observedDartsInstructionsNeedBoundDailyEntryAndNoCharge(){val s="ダーツの矢を引くと的に飛んでいくよ！\n的にあてて肉マネーをGETしよう！ スタート";assertTrue(CheckinReceiptPolicy.boundDartsStartPage(s,true));assertFalse(CheckinReceiptPolicy.boundDartsStartPage(s,false));assertFalse(CheckinReceiptPolicy.boundDartsStartPage(s+" 購入",true));assertFalse(CheckinReceiptPolicy.boundDartsStartPage("スタート 最大50円",true))}
    @Test fun pickupReadsCurrentProgressNeverHardcodesTen(){assertEquals(9,CheckinReceiptPolicy.pickupRemaining("残り 9件"));assertEquals(3,CheckinReceiptPolicy.pickupRemaining("残り3件"));assertNull(CheckinReceiptPolicy.pickupRemaining("最大1000ポイント"))}
    @Test fun pickupUncertainClickCannotBeRepeatedAtSameProgress(){assertTrue(CheckinReceiptPolicy.canAdvancePickup(9,null));assertTrue(CheckinReceiptPolicy.canAdvancePickup(8,9));assertFalse(CheckinReceiptPolicy.canAdvancePickup(9,9));assertFalse(CheckinReceiptPolicy.canAdvancePickup(10,9));assertFalse(CheckinReceiptPolicy.canAdvancePickup(null,9))}
    @Test fun threeCardsAreIndependentAndNotMarketingText(){val a=CheckinLine("PICKUP",490,625,690,665);val daily=CheckinLine("達成",540,840,650,875);val other=CheckinLine("未達成",80,840,180,875);assertTrue(CheckinReceiptPolicy.cardDone(listOf(a,daily,other),a));assertFalse(CheckinReceiptPolicy.cardDone(listOf(a,other),a));assertFalse(CheckinReceiptPolicy.cardDone(listOf(a,daily.copy(text="3つ達成でボーナス")),a))}
    private fun at(s:String)=SimpleDateFormat("yyyy-MM-dd HH:mm:ss").apply{timeZone=TimeZone.getTimeZone("Asia/Tokyo")}.parse(s)!!.time
    @Test fun a_normalCompletionDoesNotChangeAnaLeaseOrSchedule(){
        val q=UiOwnership();val anaTime=at("2026-10-30 00:03:00")
        val r=q.acquire("RAKUTEN","r",0,100,false)!!;assertTrue(q.release(r))
        assertEquals(anaTime,CheckinClock.next(at("2026-10-29 23:00:00"),0,3))
        assertNotNull(q.acquire("ANA","a",10,Long.MAX_VALUE,false))
    }
    @Test fun b_stalledOwnerCannotBlockAna(){
        val q=UiOwnership();val r=q.acquire("RAKUTEN","stalled",0,Long.MAX_VALUE,false)!!
        assertEquals(r,q.revokeOrdinary());val ana=q.acquire("ANA","due",1,Long.MAX_VALUE,false)!!
        var clicks=0;assertFalse(q.action(r,2,false){clicks++});assertEquals(0,clicks);assertEquals(ana,q.owner())
    }
    @Test fun c_simultaneousSteakAndAnaAnaWins(){
        val q=UiOwnership();val steak=q.acquire("STEAK","s",0,100,false)!!
        val ana=q.acquire("ANA","a",0,100,false)!!
        assertFalse(q.valid(steak,1));assertTrue(q.valid(ana,1));assertNull(q.acquire("STEAK","s2",1,100,false))
    }
    @Test fun d_crashedOrdinaryOwnerCannotRetainUiForever(){
        val q=UiOwnership();val old=q.acquire("RAKUTEN","dead",0,10,false)!!
        assertFalse(q.valid(old,11));assertNotNull(q.acquire("ANA","a",11,100,false))
        assertFalse(q.release(old));assertEquals("ANA",q.owner()?.app)
    }
    @Test fun e_ordinaryScheduleDuringAnaCannotAct(){
        val q=UiOwnership();val ana=q.acquire("ANA","a",0,100,false)!!
        assertNull(q.acquire("RAKUTEN","r",1,100,false));assertNull(q.revokeOrdinary());assertTrue(q.valid(ana,2))
    }
    @Test fun f_resumeNeedsNewTokenAndNeverReplaysReservedReward(){
        val q=UiOwnership();val r=q.acquire("RAKUTEN","r",0,100,false)!!;val a=q.acquire("ANA","a",1,100,false)!!
        q.release(a);val resumed=q.acquire("RAKUTEN","r2",2,100,false)!!
        assertFalse(q.valid(r,3));assertTrue(q.valid(resumed,3))
        assertFalse(CheckinReceiptPolicy.maySubmit(CheckinState.PREEMPTED,true))
        assertFalse(CheckinReceiptPolicy.maySubmit(CheckinState.SUCCESS,false))
        assertFalse(CheckinReceiptPolicy.maySubmit(CheckinState.UNCONFIRMED,true))
    }
    @Test fun g_restartInvalidatesOldProcessTokensSchedulesStayIndependent(){
        val old=UiOwnership().acquire("RAKUTEN","old",0,100,false)!!
        val restarted=UiOwnership();assertFalse(restarted.valid(old,1))
        val now=at("2026-10-29 21:00:00")
        assertEquals(at("2026-10-30 00:03:00"),CheckinClock.next(now,0,3))
        assertEquals(at("2026-10-30 09:15:00"),CheckinClock.next(now,9,15))
        assertEquals(at("2026-10-30 09:30:00"),CheckinClock.next(now,9,30))
        assertEquals(2,CheckinApp.entries.map{it.pkg}.toSet().size)
    }
    @Test fun midnightProtectionStartsOnPreviousDate(){
        assertFalse(CheckinClock.protected(at("2026-10-29 23:57:59"),0,3,true,true))
        assertTrue(CheckinClock.protected(at("2026-10-29 23:58:00"),0,3,true,true))
        assertTrue(CheckinClock.protected(at("2026-10-30 00:02:59"),0,3,true,false))
        assertTrue(CheckinClock.protected(at("2026-10-30 00:03:00"),0,3,true,false))
        assertFalse(CheckinClock.protected(at("2026-10-30 00:04:00"),0,3,true,true))
    }
    @Test fun disabledAnaDoesNotCreateProtectionWindow(){assertFalse(CheckinClock.protected(at("2026-10-30 00:02:00"),0,3,false,false))}
    @Test fun expiredCallbacksCannotOperate(){val q=UiOwnership();val t=q.acquire("RAKUTEN","r",0,10,false)!!;var n=0;assertFalse(q.action(t,10,false){n++});assertEquals(0,n)}
    @Test fun protectionRejectsNewAndExistingOrdinaryActions(){val q=UiOwnership();val t=q.acquire("STEAK","s",0,100,false)!!;assertFalse(q.action(t,1,true){fail()});assertNull(UiOwnership().acquire("STEAK","s",0,100,true))}
    @Test fun guideDemonstrationIsNeverAReceipt(){assertFalse(CheckinReceiptPolicy.realReceipt(true,true,true,"1ポイントゲット！"));assertFalse(CheckinReceiptPolicy.realReceipt(false,false,true,"1ポイントゲット！"))}
    @Test fun clickAndCloseAreNeverRewardEvidence(){assertFalse(CheckinReceiptPolicy.realReceipt(true,false,true,"閉じる"));assertFalse(CheckinReceiptPolicy.realReceipt(true,false,true,"スタート"))}
    @Test fun couponAndPointsKeepSeparateAssets(){assertNotEquals(CheckinApp.RAKUTEN.pkg,CheckinApp.STEAK.pkg)}
    @Test fun onlyRealFreeDailyDartsBannerAccepted(){assertTrue(CheckinReceiptPolicy.freeDartsBanner("1日1回 毎日 チャレンジ いきなりダーツ"));assertFalse(CheckinReceiptPolicy.freeDartsBanner("スクラッチ祭り 500円以上 毎日"));assertFalse(CheckinReceiptPolicy.freeDartsBanner("肉の日29日 2倍"))}
    @Test fun tokyoBusinessDateUsesRealInstant(){assertEquals("2026-10-30",CheckinClock.date(at("2026-10-30 00:00:00")));assertEquals("2026-10-29",CheckinClock.date(at("2026-10-29 23:59:59")))}
    @Test fun actualEarnCardSplitOverTwoLinesIsRecognized(){val p=CheckinLine("1ポイント",40,690,215,728);val m=CheckinLine("ミッション",40,733,215,770);val unrelated=CheckinLine("PICKUP",500,690,680,728);assertEquals(p,CheckinReceiptPolicy.pointsEntry(listOf(p,m,unrelated)))}
    @Test fun separateCardsCannotBeCombinedToInventAnEntry(){val p=CheckinLine("1ポイント",40,690,215,728);val m=CheckinLine("ミッション",500,733,680,770);assertNull(CheckinReceiptPolicy.pointsEntry(listOf(p,m)))}
    @Test fun stylizedLotteryHasCurrentReadableDailyTwoChanceCaption(){val l=CheckinLine("1日2回引ける!",270,620,450,650);assertEquals(l,CheckinReceiptPolicy.lotteryEntry(listOf(l)));assertNull(CheckinReceiptPolicy.lotteryEntry(listOf(l.copy(text="購入して最大1000ポイント"))))}
    @Test fun onboardingAfterPastTimeDoesNotBlockOrdinaryAllDay(){val now=at("2026-10-29 16:00:00");val due=CheckinClock.next(now,0,3);assertFalse(CheckinClock.registeredProtection(now,due,0,true));assertTrue(CheckinClock.registeredProtection(at("2026-10-29 23:58:00"),due,0,true))}
    @Test fun registeredWindowSurvivesDelayedTriggerPastMidnight(){val due=at("2026-10-30 00:03:00");assertTrue(CheckinClock.registeredProtection(at("2026-10-30 00:10:00"),due,0,true));assertFalse(CheckinClock.registeredProtection(at("2026-10-30 00:10:00"),due,due,true))}
    @Test fun actualBannerOcrFindsEntryWithoutInventingFreeEligibility(){val actual="アスリで ャレンン! いきなり!ダーツで当たる TKINA RI DARTS 家ネークーポン";assertTrue(CheckinReceiptPolicy.dartsEntryCandidate(actual));assertFalse(CheckinReceiptPolicy.freeDartsBanner(actual));assertFalse(CheckinReceiptPolicy.dartsEntryCandidate(actual+" スクラッチ500円以上購入"))}
    @Test fun dailyMissionMustHaveOfficialCardCompletion(){val p=CheckinLine("1ポイント",40,690,215,728);val m=CheckinLine("ミッション、",40,733,215,770);val s=CheckinLine("未達成",76,840,185,875);val bonus=CheckinLine("3つ達成でボーナス",100,940,680,985);assertFalse(CheckinReceiptPolicy.pointsDone(listOf(p,m,s,bonus)));assertTrue(CheckinReceiptPolicy.pointsDone(listOf(p,m,s.copy(text="達成"),bonus)))}
    @Test fun marketingMaximumIsNeverDrawPrize(){val ad=CheckinLine("最大1,000ポイント",20,100,600,145);val prize=CheckinLine("3ポイント獲得",150,600,550,655);assertEquals("3",CheckinReceiptPolicy.rewardAmount(listOf(ad,prize),"ポイント"))}
    @Test fun couponUsageUnitsAreNotWinningAmount(){val prize=CheckinLine("1円",200,400,450,470);val use=CheckinLine("100円",150,900,300,940);val rule=CheckinLine("単位でご利用",150,945,480,985);assertEquals("1",CheckinReceiptPolicy.rewardAmount(listOf(prize,use,rule),"円"))}
    @Test fun ambiguousAwardStaysUnknown(){assertNull(CheckinReceiptPolicy.rewardAmount(listOf(CheckinLine("1円",100,400,300,450),CheckinLine("50円",100,700,300,750)),"円"))}
    @Test fun invitationAndTomorrowCopyAreNeverAlreadyDone(){assertFalse(CheckinReceiptPolicy.alreadyDone("本日は無料で参加できます"));assertFalse(CheckinReceiptPolicy.alreadyDone("また明日もチャレンジしよう"));assertFalse(CheckinReceiptPolicy.alreadyDone("本日分は参加済みです",guide=true))}
    @Test fun explicitTodayCompletionIsRequired(){assertTrue(CheckinReceiptPolicy.alreadyDone("本日はすでに参加済みです"));assertTrue(CheckinReceiptPolicy.alreadyDone("本日のプレイは完了しました"));assertFalse(CheckinReceiptPolicy.alreadyDone("イベントは終了しました"))}
    @Test fun loadingPlaceholderIsNotAnAvailableDailyMission(){val p=CheckinLine("1ポイント",40,690,215,728);val m=CheckinLine("ミッション",40,733,215,770);assertFalse(CheckinReceiptPolicy.pointsStatusReady(listOf(p,m)));assertTrue(CheckinReceiptPolicy.pointsStatusReady(listOf(p,m,CheckinLine("未達成",76,840,185,875))))}
}
