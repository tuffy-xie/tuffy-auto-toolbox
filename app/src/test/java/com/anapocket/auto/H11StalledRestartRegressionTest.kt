package com.anapocket.auto

import org.junit.Assert.*
import org.junit.Test

class H11StalledRestartRegressionTest {
    private val date="2026-11-01"
    private fun eligible(stage:String="STARTUP",state:String="UNCONFIRMED",reason:String="startup_ad_timeout_300s",
        recordedDate:String=date,now:Long=301_000L,started:Long=1_000L,confirmed:Boolean=false,used:Boolean=false,count:Int=0,live:Boolean=true,sdk:Boolean=true) =
        StalledAdRestartPolicy.eligible(date,recordedDate,stage,state,reason,started,now,confirmed,used,count,live,sdk)
    @Test fun loginMustActuallyReachFiveMinuteTimeout(){
        assertFalse(eligible(now=300_999));assertTrue(eligible());assertFalse(eligible(live=false));assertFalse(eligible(sdk=false))
    }
    @Test fun otherViewingStillRequiresItsFullTimeout(){
        assertFalse(eligible(stage="ADS",reason="ad_timeout_95s",now=95_999));assertTrue(eligible(stage="ADS",reason="ad_timeout_95s",now=96_000))
        assertTrue(eligible(stage="CHALLENGES",reason="ad_part_timeout_95s",now=96_000))
    }
    @Test fun dateAndOriginalStartCannotBeAdoptedFromAnotherViewing(){
        assertFalse(eligible(recordedDate="2026-10-31"));assertFalse(eligible(started=0));assertFalse(eligible(started=302_000))
    }
    @Test fun confirmedAndNonterminalStatesNeverAuthorizeClosing(){
        for(state in listOf("AD_OPENING","AD_PLAYING","FAILED","REWARD_CONFIRMED"))assertFalse(eligible(state=state))
        assertFalse(eligible(confirmed=true));assertFalse(eligible(reason="user_stopped"));assertFalse(eligible(reason="account_safety_human_required"))
    }
    @Test fun oneViewingAndDailyBudgetsSurviveFurtherRetries(){
        assertFalse(eligible(used=true));assertFalse(eligible(count=3));assertTrue(eligible(count=2));assertFalse(eligible(stage="NOTIFY"))
    }
    @Test fun appInfoMustBeTheObservedAnaSystemPage(){
        val labels=listOf("应用信息","ANA Pocket","结束运行")
        assertTrue(StalledAdRestartPolicy.appInfo("com.miui.securitycenter","com.miui.appmanager.ApplicationsDetailsActivity",labels))
        assertFalse(StalledAdRestartPolicy.appInfo("com.android.vending","com.miui.appmanager.ApplicationsDetailsActivity",labels))
        assertFalse(StalledAdRestartPolicy.appInfo("com.miui.securitycenter","unknown",labels))
        assertFalse(StalledAdRestartPolicy.appInfo("com.miui.securitycenter","com.miui.appmanager.ApplicationsDetailsActivity",labels-listOf("ANA Pocket")))
    }
    @Test fun confirmationRequiresPriorAnaTargetAndExactNativePrompt(){
        val labels=listOf("要强行停止吗？","强行停止某个应用可能会导致其出现异常。","确定")
        assertTrue(StalledAdRestartPolicy.confirmation("com.miui.securitycenter",labels,true))
        assertFalse(StalledAdRestartPolicy.confirmation("com.miui.securitycenter",labels,false))
        assertFalse(StalledAdRestartPolicy.confirmation("us.ana_pocket.ANAPocket",labels,true))
        assertFalse(StalledAdRestartPolicy.confirmation("com.miui.securitycenter",listOf("确定"),true))
    }
    @Test fun persistedStopCanOnlyRelaunchSameUnknownWithoutAnotherStop(){
        for(marker in listOf("SYSTEM_STOP_CONFIRMED","RELAUNCHED_REVIEW_REQUIRED"))assertTrue(StalledAdRestartPolicy.mayRelaunch(date,date,"UNCONFIRMED",marker))
        for(marker in listOf("REQUESTED","BLOCKED_NO_REPEAT",""))assertFalse(StalledAdRestartPolicy.mayRelaunch(date,date,"UNCONFIRMED",marker))
        assertFalse(StalledAdRestartPolicy.mayRelaunch(date,"2026-10-31","UNCONFIRMED","SYSTEM_STOP_CONFIRMED"))
        assertFalse(StalledAdRestartPolicy.mayRelaunch(date,date,"REWARD_CONFIRMED","SYSTEM_STOP_CONFIRMED"))
    }
    @Test fun hiddenOriginalCardSearchHasFiniteNativePageBudget(){
        assertTrue(AdTaskReturnPolicy.maySearch(true,0,0,true));assertTrue(AdTaskReturnPolicy.maySearch(true,0,3,true))
        assertFalse(AdTaskReturnPolicy.maySearch(true,0,4,true));assertFalse(AdTaskReturnPolicy.maySearch(true,0,-1,true))
    }
    @Test fun searchNeverRunsOutsideRecentsOrWhenCardExistsOrLeaseIsLost(){
        assertFalse(AdTaskReturnPolicy.maySearch(false,0,0,true));assertFalse(AdTaskReturnPolicy.maySearch(true,1,0,true))
        assertFalse(AdTaskReturnPolicy.maySearch(true,2,0,true));assertFalse(AdTaskReturnPolicy.maySearch(true,0,0,false))
    }
}
