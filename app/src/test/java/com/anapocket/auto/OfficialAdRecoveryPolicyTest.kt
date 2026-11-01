package com.anapocket.auto
import org.junit.Assert.*
import org.junit.Test
class OfficialAdRecoveryPolicyTest {
    private val today="2026-10-30"
    private val original=AdAttempt("original",today,24,100,state=AdState.UNCONFIRMED,finishedAt=200,failureReason="ad_timeout_95s",officialBefore=23)
    private fun allowed(rows:List<AdAttempt> = listOf(original),date:String=today,counter:Int?=23,proof:Boolean=true)=OfficialAdRecoveryPolicy.mayBind(rows,date,today,counter,proof)
    @Test fun sameOriginalUnknownIsEligibleWithoutAReplacementReservation(){assertTrue(allowed())}
    @Test fun dateDepartureAndOriginalOfficialBaselineAreMandatory(){assertFalse(allowed(date="2026-10-29"));assertFalse(allowed(proof=false));assertFalse(allowed(counter=24));assertFalse(allowed(counter=null))}
    @Test fun ambiguousMultipleAndAlreadyCompletedAttemptsCannotBeRecovered(){assertFalse(allowed(rows=listOf(original.copy(index=23),original)));assertFalse(allowed(rows=listOf(original.copy(completed=true))));assertFalse(allowed(rows=listOf(original.copy(failureReason="counter_jump_ambiguous"))))}
    @Test fun bothFreshReceiptAndExactPlusOneAreRequired(){assertTrue(OfficialAdRecoveryPolicy.confirms(original,AdReceipt(AdState.REWARD_CONFIRMED,true,true,24)));assertFalse(OfficialAdRecoveryPolicy.confirms(original,AdReceipt(AdState.UNCONFIRMED,true,false,24)));assertFalse(OfficialAdRecoveryPolicy.confirms(original,AdReceipt(AdState.REWARD_CONFIRMED,true,true,25)));assertFalse(OfficialAdRecoveryPolicy.confirms(original,AdReceipt(AdState.REWARD_CONFIRMED,false,true,24)))}
}
