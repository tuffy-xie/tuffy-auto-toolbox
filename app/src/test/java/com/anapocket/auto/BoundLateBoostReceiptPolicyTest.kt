package com.anapocket.auto
import org.junit.Assert.*
import org.junit.Test

class BoundLateBoostReceiptPolicyTest {
    private val actual=listOf("3回目のブーストをしますか？","現在のチャレンジ達成後の報酬：","200pt","+50pt","+50pt","+50pt","+100pt","+100pt")
    private fun matches(initial:Boolean=false,confirmed:Boolean=false,index:Int=3,pending:Int=1,sameDate:Boolean=true,
        departed:Boolean=true,panel:Boolean=true,expected:Int=200,steps:List<Int> = listOf(50,50,50,100,100),
        reason:String="ad_timeout_95s",labels:List<String> = actual)=
        BoundLateBoostReceiptPolicy.matches("UNCONFIRMED","UNCONFIRMED",reason,confirmed,initial,index,3,pending,sameDate,
            departed,1,150,expected,steps,panel,labels)
    @Test fun realBikeTwoKmLateReceiptReconcilesExistingAttemptOnly(){assertTrue(matches())}
    @Test fun noDepartureMultiplePendingOrDifferentDateNeverReconciles(){assertFalse(matches(departed=false));assertFalse(matches(pending=2));assertFalse(matches(sameDate=false));assertFalse(matches(index=2))}
    @Test fun initialConfirmedOrAmbiguousFailuresRemainProtected(){assertFalse(matches(initial=true));assertFalse(matches(confirmed=true));assertFalse(matches(reason="counter_jump_ambiguous"))}
    @Test fun nativePanelOrdinalRewardAndAllFiveIncrementsMustMatch(){assertFalse(matches(panel=false));assertFalse(matches(expected=201));assertFalse(matches(steps=listOf(50,50)));assertFalse(matches(labels=actual.map{if(it=="200pt")"150pt" else it}));assertFalse(matches(labels=actual.map{if(it.startsWith("3回目"))"4回目のブーストをしますか？" else it}))}
    @Test fun unlabelledNumberCannotBeAReceipt(){assertFalse(matches(labels=actual.filterNot{it.startsWith("現在のチャレンジ")}))}
}
