package com.anapocket.auto

import org.junit.Assert.*
import org.junit.Test

/** 11/11 CAR 5km: the join ad timed out, 参加中 did not list it, 参加可能 still did; the user then joined it by hand. */
class InitialRejoinPolicyTest {
    private val t="2026-11-11"
    private fun may(rowDate:String=t,joined:Boolean=false,ads:Int=1,initial:Boolean=true,confirmed:Boolean=false,noGain:Boolean=false,
        absent:Boolean=true,joinable:Boolean=true,already:Boolean=false)=
        InitialRejoinPolicy.mayRejoin(rowDate,t,joined,ads,initial,confirmed,noGain,absent,joinable,already)
    @Test fun officiallyAbsentAndStillJoinableMayRejoinOnce(){
        assertTrue(may())
        assertFalse("never twice",may(already=true))
        assertFalse("a second ad already exists",may(ads=2))
    }
    @Test fun withoutBothOfficialProofsNoRejoin(){
        assertFalse("not in fresh 参加可能 list",may(joinable=false))
        assertFalse("参加中 scan did not prove it absent",may(absent=false))
        assertFalse("other day",may(rowDate="2026-11-10"))
    }
    @Test fun joinedConfirmedOrBoostAttemptsAreNeverRejoined(){
        assertFalse(may(joined=true))
        assertFalse(may(confirmed=true))
        assertFalse(may(noGain=true))
        assertFalse("a Boost ad, not a join ad",may(initial=false))
    }
}
