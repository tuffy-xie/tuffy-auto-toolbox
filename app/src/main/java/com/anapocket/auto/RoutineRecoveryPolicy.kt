package com.anapocket.auto

/** A system low-memory exit can resume a recent running routine, never a stopped/finished one. */
object RoutineRecoveryPolicy {
    fun eligible(today:String,date:String,state:String,run:String,stage:String,updatedAt:Long,
        now:Long,exitAt:Long,lowMemory:Boolean,enabled:Boolean,stopped:Boolean,risk:Boolean,used:Int):Boolean =
        today==date&&state=="RUNNING"&&run.startsWith("RT")&&
            stage in setOf("STARTUP","CHALLENGES","ADS")&&updatedAt>0&&
            now>=updatedAt&&now-updatedAt<=8*60*60*1000L&&
            lowMemory&&exitAt>=updatedAt&&now>=exitAt&&now-exitAt<=120_000L&&
            enabled&&!stopped&&!risk&&used in 0..2
}
