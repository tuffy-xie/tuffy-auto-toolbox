package com.anapocket.auto
import org.junit.Assert.*
import org.junit.Test
class OfficialAdQuotaTest {
 private val d="2026-10-28"
 private fun row(state:AdState=AdState.REWARD_CONFIRMED)=AdAttempt("NEW",d,26,1000,state,state==AdState.REWARD_CONFIRMED,state==AdState.REWARD_CONFIRMED,2000,officialBefore=23,officialAfter=24)
 @Test fun legacy25StartsDoNotMeanOfficialQuotaComplete(){assertFalse(OfficialAdQuota.isComplete(23));assertFalse(OfficialAdQuota.isComplete(24));assertTrue(OfficialAdQuota.canReserve(24,23,true,false,emptyList()))}
 @Test fun official25StopsRegardlessOfLocalHistory(){assertTrue(OfficialAdQuota.isComplete(25));assertFalse(OfficialAdQuota.canReserve(25,24,true,false,emptyList()))}
 @Test fun anotherPhonesSuccessChangesQuotaButNeverCreatesLocalRows(){val local=listOf(row());assertTrue(OfficialAdQuota.canReserve(24,23,true,false,local));assertEquals(1,local.size)}
 @Test fun unknownBlockedFailedAndInFlightCannotRepeat(){for(s in listOf(AdState.UNCONFIRMED,AdState.BLOCKED,AdState.FAILED,AdState.AD_PLAYING))assertFalse(OfficialAdQuota.canReserve(24,23,true,false,listOf(row(s))))}
 @Test fun countRegressionMissingCountWrongDateAndStopBlock(){assertFalse(OfficialAdQuota.canReserve(23,24,true,false,emptyList()));assertFalse(OfficialAdQuota.canReserve(null,null,true,false,emptyList()));assertFalse(OfficialAdQuota.canReserve(24,24,false,false,emptyList()));assertFalse(OfficialAdQuota.canReserve(24,24,true,true,emptyList()))}
 @Test fun dailyRetryBudgetIsFinite(){assertFalse(OfficialAdQuota.canReserve(24,24,true,false,(1..25).map{row().copy(index=it)}))}
 @Test fun confirmedReceiptWithoutEffectiveCompletionStillBlocks(){assertFalse(OfficialAdQuota.canReserve(24,23,true,false,listOf(row().copy(completed=false))))}
 @Test fun newDayRequiresNewBucketNotYesterdayFailures(){assertTrue(OfficialAdQuota.canReserve(0,null,true,false,emptyList()))}
}
