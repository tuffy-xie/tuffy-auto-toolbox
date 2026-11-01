package com.anapocket.auto

/** Result delivery has the same date/configuration fence as execution. Waiting is not failure. */
object CheckinNoticePolicy {
    fun mayPublish(enabled:Boolean,generation:Long,expectedGeneration:Long,date:String,today:String,
        status:String,anaProtected:Boolean):Boolean = enabled&&generation==expectedGeneration&&date==today&&
        !anaProtected&&status !in setOf("WAITING_FOR_ANA","PREEMPTED_BY_ANA")
    fun status(states:List<String>,reason:String=""):String = when {
        reason.contains("ANA",true)||states.any{it=="PREEMPTED"} -> "PREEMPTED_BY_ANA"
        reason.contains("accessibility")||reason.contains("requires_user")||states.any{it in setOf("NEEDS_USER_ACTION","UNCONFIRMED")} -> "NEEDS_USER_ACTION"
        reason=="user_stopped" -> "STOPPED"
        states.any{it=="FAILED"} -> "FAILED"
        states.any{it=="BLOCKED"}||reason in setOf("execution_deadline","execution_timeout") -> "ACTUAL_BLOCKED"
        else -> "COMPLETED"
    }
}
