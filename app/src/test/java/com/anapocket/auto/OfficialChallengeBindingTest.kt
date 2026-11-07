package com.anapocket.auto

import org.junit.Assert.*
import org.junit.Test

class OfficialChallengeBindingTest {
    // Field names, lifecycle and argument layout replay the current ANA HomeActivity dump.
    private fun fragment(kind:String,arguments:String,state:Int=7)="""
    $kind{abc123} (fixture)
        mState=$state mWho=fixture mBackStackNesting=0
        mAdded=true mRemoving=false mFromLayout=false mInLayout=false
        mHidden=false mDetached=false mMenuVisible=true mHasMenu=false
        mArguments=Bundle[{$arguments}]
    """.trimIndent().prependIndent("    ")
    private fun dump(user:String="1776612789",boost:Boolean=true)=
        "ACTIVITY us.ana_pocket.ANAPocket/.views.home.HomeActivity fixture pid=1\n    mResumed=true mStopped=false mFinished=false\n"+
        fragment("ChallengeDetailBottomSheetDialogFragment","deep_link=0, with_boost=true, mission_id=3625, user_mission_id=$user")+"\n"+
        fragment("ChallengeDetailFragment","join_mode=0, mission_id=3625, user_mission_id=$user, start_at=null")+"\n"+
        if(boost)fragment("ChallengeBoostDialogFragment","ADDITIONAL_BOOST_COUNT_KEY=4, IS_RESERVED_KEY=false, MISSION_ID_KEY=3625, BOOST_REWARD_KEY=BoostReward(rewards=[Item(count=5, point=300)]), USER_MISSION_ID_KEY=$user") else ""
    @Test fun realLayoutRequiresAgreementOfParentDetailAndBoost(){
        assertEquals(OfficialChallengeIdentity(3625,1776612789,4),OfficialChallengeBinding.parse(dump()))
        assertNull(OfficialChallengeBinding.parse(dump().replace("MISSION_ID_KEY=3625","MISSION_ID_KEY=9999")))
        assertNull(OfficialChallengeBinding.parse(dump().replace("USER_MISSION_ID_KEY=1776612789","USER_MISSION_ID_KEY=9999")))
    }
    @Test fun staleSavedFragmentsAndDuplicateActiveDetailsCannotSupplyIdentity(){
        val stale=fragment("ChallengeDetailFragment","mission_id=9999, user_mission_id=5555",4)
        assertNotNull(OfficialChallengeBinding.parse(dump()+"\n"+stale))
        assertNull(OfficialChallengeBinding.parse(dump()+"\n"+stale.replace("mState=4","mState=7")))
        assertNull(OfficialChallengeBinding.parse(dump().replace("mHidden=false","mHidden=true")))
    }
    @Test fun timeoutBackgroundWrongComponentAndOverflowAreNotAnIdentity(){
        assertNull(OfficialChallengeBinding.parse(dump()+"\n*** SERVICE 'activity' DUMP TIMEOUT (8000ms) EXPIRED ***"))
        assertNull(OfficialChallengeBinding.parse(dump().replace("mResumed=true","mResumed=false")))
        assertNull(OfficialChallengeBinding.parse(dump().replace(".views.home.HomeActivity ",".other.Activity ")))
        assertNull(OfficialChallengeBinding.parse(dump()+" ".repeat(524288)))
    }
    @Test fun unjoinedMissionIsNotInventedParticipationOrBoostIdentity(){
        assertEquals(OfficialChallengeIdentity(3625,null,null),OfficialChallengeBinding.parse(dump("null",false)))
        assertNull(OfficialChallengeBinding.parse(dump("-1",false)))
        assertFalse(OfficialChallengeIdentity(3625,null,null).sameParticipation(OfficialChallengeIdentity(3625,1,0)))
        assertFalse(OfficialChallengeIdentity(3625,1,0).sameParticipation(OfficialChallengeIdentity(3625,2,0)))
    }
    @Test fun realUnjoinedParentMayLackParticipationButBoostCannot(){
        val unjoined=dump("null",false).replace("with_boost=true, mission_id=3625, user_mission_id=null", "with_boost=false, mission_id=3625, start_at=1793890800000")
        assertEquals(OfficialChallengeIdentity(3625,null,null),OfficialChallengeBinding.parse(unjoined))
        assertNull(OfficialChallengeBinding.parse(dump().replace("USER_MISSION_ID_KEY=1776612789", "NO_USER_ID_KEY=1776612789")))
    }
    @Test fun recoveryUsesExactOriginalDurableContextAndOneNavigationBudget(){
        fun allowed(bound:Boolean=true,date:Boolean=true,pending:Int=1,stage:String="REMOVE_ONLY_OBSERVED_ANA_TASK",run:Boolean=true,requests:Int=0,dispatched:Boolean=true)=
            ChallengeRecoveryPolicy.recordedRestartNavigation(bound,date,pending,stage,run,requests,dispatched)
        assertTrue(allowed());assertFalse(allowed(bound=false));assertFalse(allowed(date=false));assertFalse(allowed(pending=2))
        assertFalse(allowed(run=false));assertFalse(allowed(requests=1));assertFalse(allowed(dispatched=false))
        assertFalse(allowed(stage="OFFICIAL_RESUME_OBSERVED"));assertFalse(allowed(stage=""))
    }
}
