package com.anapocket.auto

data class RoutineRunMode(val manual:Boolean,val challengesOnly:Boolean)
object RoutineRunModes {
    fun choose(trigger:String,original:RoutineRunMode?=null):RoutineRunMode = original?:RoutineRunMode(trigger.contains("manual"),trigger=="manual-challenge-rescan")
}

data class RecoveryPlan(val allowed:Boolean,val due:Long,val burst:Int,val total:Int,val cooldown:Boolean,val reason:String)
/** Bounded by durable progress, an eight-hour business window and a finite daily start budget. */
object PersistentRecoveryPolicy {
    const val WINDOW=8*60*60*1000L
    fun activeSnapshotState(state:String,ownedActive:Boolean):String =
        if(ownedActive&&state !in setOf("BLOCKED","STOPPED","FAILED","CANCELLED"))"RUNNING" else state
    fun activeSnapshotStage(stage:String,ownedActive:Boolean):String = if(ownedActive&&stage=="DONE")"ADS" else stage
    fun canRegisterFromPhase(phase:String?)=phase==null||phase in setOf("PENDING","CLAIMED")
    fun exitStillMatches(sourceAt:Long,latestAt:Long,latestLowMemory:Boolean):Boolean = sourceAt>0&&sourceAt==latestAt&&latestLowMemory
    fun mayRegister(today:String,date:String,state:String,run:String,stage:String,updated:Long,now:Long,exitAt:Long,
        lowMemory:Boolean,enabled:Boolean,stopped:Boolean,risk:Boolean,sameBoot:Boolean=true,sameVersion:Boolean=true):Boolean =
        today==date&&state=="RUNNING"&&run.startsWith("RT")&&stage in setOf("STARTUP","CHALLENGES","ADS")&&updated>0&&
            now>=updated&&now-updated<=WINDOW&&exitAt>=updated&&now>=exitAt&&now-exitAt<=WINDOW&&lowMemory&&enabled&&!stopped&&!risk&&sameBoot&&sameVersion
    fun plan(now:Long,first:Long,burst:Int,total:Int,cooldownUsed:Boolean,progressChanged:Boolean):RecoveryPlan {
        if(now<first||now-first>WINDOW)return RecoveryPlan(false,0,burst,total,cooldownUsed,"recovery_window_expired")
        if(total>=12)return RecoveryPlan(false,0,burst,total,cooldownUsed,"recovery_daily_budget_exhausted")
        val n=if(progressChanged)0 else burst
        val cooled=if(progressChanged)false else cooldownUsed
        if(n>=3){
            if(cooled)return RecoveryPlan(false,0,n,total,true,"repeated_memory_exit_without_durable_progress")
            return RecoveryPlan(true,now+15*60*1000L,0,total,true,"memory_pressure_cooldown")
        }
        val delay=when(n){0->10_000L;1->30_000L;else->120_000L}
        return RecoveryPlan(true,now+delay,n,total,cooled,"bounded_low_memory_recovery")
    }
    fun mayDispatch(today:String,date:String,enabled:Boolean,service:Boolean,risk:Boolean,userStopped:Boolean,
        sameBoot:Boolean,sameVersion:Boolean,now:Long,first:Long,sourceLowMemory:Boolean,unstartedIntent:Boolean=false):Boolean =
        today==date&&enabled&&service&&!risk&&!userStopped&&sameBoot&&sameVersion&&(sourceLowMemory||unstartedIntent)&&now>=first&&now-first<=WINDOW
}

/** Existing submitted travel is reconciled before generic startup, in both full and travel-only runs. */
object RoutineRecoveryNavigation {
    fun reviewChallengeFirst(challengesOnly:Boolean,pendingChallenge:Boolean):Boolean = when {
        pendingChallenge -> true
        challengesOnly -> false
        else -> false
    }
}
