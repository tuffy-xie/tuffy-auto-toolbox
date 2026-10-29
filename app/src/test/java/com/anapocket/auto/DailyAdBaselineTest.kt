package com.anapocket.auto
import org.junit.Assert.*
import org.junit.Test
class DailyAdBaselineTest {
    private fun jump() = AdAttempt("OLD", "2026-10-28", 4, 1, AdState.UNCONFIRMED, false, true, 5, "counter_jump_ambiguous", 0, 2)
    private fun AdAttempt.confirmedForTest() = completed && rewardConfirmed
    @Test fun stablePageWaitsThreeSeconds() { val b=DailyAdBaseline(); assertNull(b.observe(0,2,true));assertNull(b.observe(750,2,true));assertNull(b.observe(1500,2,true));assertNull(b.observe(2250,2,true));assertEquals(2,b.observe(3000,2,true)) }
    @Test fun actualOldZeroThenTwoResetsStability() { val b=DailyAdBaseline();b.observe(0,0,true);b.observe(750,0,true);b.observe(1500,2,true);assertNull(b.observe(3000,2,true));b.observe(3750,2,true);assertEquals(2,b.observe(4500,2,true)) }
    @Test fun missingPageAndReceiptResetSamples() {val b=DailyAdBaseline();b.observe(0,2,true);b.observe(3000,2,true);assertNull(b.observe(4000,2,false));assertNull(b.observe(7000,2,true))}
    @Test fun invalidCountersNeverAuthorizeStart() {val b=DailyAdBaseline();for(v in listOf(null,-1,26))assertNull(b.observe(9000,v,true))}
    @Test fun genericJumpStillBlocksLegacyManualAndAutomatic() {val a=listOf(jump());assertFalse(AdDayLogic.canStart(a));assertFalse(AdDayLogic.canStartFreshManual(a,"NEW","counter_jump_ambiguous"))}
    @Test fun refreshedActualTwoAllowsNewRunOnly() {val a=listOf(jump());assertTrue(DailyAdCounterRefreshPolicy.candidate(a,"NEW","counter_jump_ambiguous"));assertTrue(DailyAdCounterRefreshPolicy.canContinue(a,"NEW","counter_jump_ambiguous",2));assertFalse(DailyAdCounterRefreshPolicy.candidate(a,"OLD","counter_jump_ambiguous"))}
    @Test fun noFreshCounterOrRegressionCannotResume() {val a=listOf(jump());for(v in listOf(null,-1,0,1,25,26))assertFalse(DailyAdCounterRefreshPolicy.canContinue(a,"NEW","counter_jump_ambiguous",v))}
    @Test fun otherHaltsNeverBypassed() {for(h in listOf("official_error_dialog","official_limit","unknown_halt",""))assertFalse(DailyAdCounterRefreshPolicy.canContinue(listOf(jump()),"NEW",h,2))}
    @Test fun unboundOldCounterCannotAuthorizeRefresh() {for(a in listOf(jump().copy(officialBefore=-1),jump().copy(officialAfter=-1),jump().copy(officialAfter=1),jump().copy(failureReason="ad_timeout_95s")))assertFalse(DailyAdCounterRefreshPolicy.candidate(listOf(a),"NEW","counter_jump_ambiguous"))}
    @Test fun inflightOrFailedOldRecordBlocksReview() {for(a in listOf(jump().copy(finishedAt=0),jump().copy(state=AdState.FAILED)))assertFalse(DailyAdCounterRefreshPolicy.candidate(listOf(a),"NEW","counter_jump_ambiguous"))}
    @Test fun ownUnconfirmedNeverContinues() {for(a in listOf(jump().copy(runId="NEW",index=5),jump().copy(runId="NEW",index=5,state=AdState.AD_PLAYING,finishedAt=0)))assertFalse(DailyAdCounterRefreshPolicy.canContinue(listOf(jump(),a),"NEW","counter_jump_ambiguous",2))}
    @Test fun freshConfirmedBatchStopsAt25AndOldRecordImmutable() {val original=(1..3).map { jump().copy(index=it,failureReason="process_interrupted",officialBefore=-1,officialAfter=-1) } + jump();var rows=original;while(DailyAdCounterRefreshPolicy.canContinue(rows,"NEW","counter_jump_ambiguous",2)) {rows=rows+AdAttempt("NEW","2026-10-28",rows.size+1,10,AdState.REWARD_CONFIRMED,true,true,20)};assertEquals(25,rows.size);assertEquals(original,rows.take(4));assertEquals(21,rows.count { it.confirmedForTest() });assertFalse(AdDayLogic.canStart(rows));assertFalse(DailyAdCounterRefreshPolicy.canContinue(rows,"NEW","counter_jump_ambiguous",2))}
}
