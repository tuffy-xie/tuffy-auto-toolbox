package com.anapocket.auto

import org.junit.Assert.*
import org.junit.Test

/** 11/18 04:28 regression: ANA's own max dialog after a late 5th Boost. User-approved binding rule (11/19). */
class LateMaxDialogPolicyTest {
    private val today="2026-11-18"
    private fun late(key:String="CAR|車、電車、モノレールで10km移動しよう！",state:String="UNCONFIRMED",boost:Int=4,expected:Int=400,
        reason:String="ad_timeout_300s",isolated:Boolean=false,initial:Boolean=false,confirmed:Boolean=false,departed:Boolean=true,
        phase:String="DISPATCHED",index:Int=6,ads:Int=6,date:String=today,offered:Int=300)=
        LateMaxRow(key,date,state,true,boost,isolated,index,ads,initial,confirmed,departed,phase,reason,expected,offered)
    private fun maxed(key:String,reward:Int)=LateMaxRow(key,today,"BOOST_MAX_CONFIRMED",true,5,false,6,6,false,true,true,"DISPATCHED","",reward,reward)

    @Test fun nov18DialogBindsTheOnlyLateFifthBoost() {
        assertEquals("CAR|車、電車、モノレールで10km移動しよう！",LateMaxDialogPolicy.pick(listOf(maxed("WALK|a",250),late()),today,400))
        for(r in listOf("ad_timeout_95s","ad_part_timeout_95s","completion_not_confirmed"))assertNotNull(LateMaxDialogPolicy.pick(listOf(late(reason=r)),today,400))
    }
    @Test fun amountMustMatchExactly() {
        assertNull(LateMaxDialogPolicy.pick(listOf(late()),today,350))
        assertNull(LateMaxDialogPolicy.pick(listOf(late()),today,null))
        assertNull(LateMaxDialogPolicy.pick(listOf(late(expected=0)),today,0))
    }
    @Test fun anyAmbiguityIsNeverGuessed() {
        assertNull(LateMaxDialogPolicy.pick(listOf(late(),late(key="WALK|b")),today,400))
        assertNull(LateMaxDialogPolicy.pick(listOf(late(),maxed("WALK|c",400)),today,400))
        assertNull(LateMaxDialogPolicy.pick(listOf(late(state="AD_OPENING")),today,400))
    }
    @Test fun onlyTheFifthBoostOfAJoinedDispatchedAttemptQualifies() {
        assertNull(LateMaxDialogPolicy.pick(listOf(late(boost=3)),today,400))
        assertNull(LateMaxDialogPolicy.pick(listOf(late(initial=true)),today,400))
        assertNull(LateMaxDialogPolicy.pick(listOf(late(confirmed=true)),today,400))
        assertNull(LateMaxDialogPolicy.pick(listOf(late(departed=false)),today,400))
        assertNull(LateMaxDialogPolicy.pick(listOf(late(phase="ARMED")),today,400))
        assertNull(LateMaxDialogPolicy.pick(listOf(late(index=5)),today,400))
        assertNull(LateMaxDialogPolicy.pick(listOf(late(reason="counter_jump_ambiguous")),today,400))
        assertNull(LateMaxDialogPolicy.pick(listOf(late(reason="user_stopped")),today,400))
        assertNull(LateMaxDialogPolicy.pick(listOf(late(date="2026-11-17")),today,400))
    }
    @Test fun isolatedUnknownIsNotReopenedByADialog() {
        assertNull(LateMaxDialogPolicy.pick(listOf(late(isolated=true)),today,400))
    }
}
