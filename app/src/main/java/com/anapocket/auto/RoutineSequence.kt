package com.anapocket.auto

data class RoutineStepResult(val state:String,val detail:String,val continueRun:Boolean=true)
data class RoutineOutcome(val state:String,val detail:String,val steps:Map<String,RoutineStepResult>)
interface RoutinePort {
    fun connected():Boolean = true
    fun stopped():Boolean
    fun sameDate():Boolean
    suspend fun progress(stage:String,result:RoutineStepResult)
    suspend fun startup():RoutineStepResult
    suspend fun challenges():RoutineStepResult
    suspend fun ads():RoutineStepResult
}
class RoutineSequence(private val port:RoutinePort) {
    suspend fun run():RoutineOutcome {
        val results=linkedMapOf<String,RoutineStepResult>()
        for(stage in listOf("STARTUP","CHALLENGES","ADS")){
            if(!port.connected())return RoutineOutcome("BLOCKED","accessibility_disconnected",results)
            if(port.stopped())return RoutineOutcome("STOPPED","已按请求停止",results)
            if(!port.sameDate())return RoutineOutcome("BLOCKED","手机任务日期已变化，下次使用新日期",results)
            port.progress(stage,RoutineStepResult("RUNNING",RoutinePolicy.stageLabel(stage)))
            val result=when(stage){"STARTUP"->port.startup();"CHALLENGES"->port.challenges();else->port.ads()}
            results[stage]=result;port.progress(stage,result)
            if(!port.connected())return RoutineOutcome("BLOCKED","accessibility_disconnected",results)
            if(!result.continueRun)return RoutineOutcome(if(port.stopped())"STOPPED" else "BLOCKED",result.detail,results)
        }
        if(port.stopped())return RoutineOutcome("STOPPED","已按请求停止",results)
        if(!port.sameDate())return RoutineOutcome("BLOCKED","手机任务日期已变化，下次使用新日期",results)
        if(results.values.any{it.state=="PARTIALLY_COMPLETED"})return RoutineOutcome("PARTIALLY_COMPLETED","独立任务已处理；部分接取或Boost仍待核对，原回执保留",results)
        return RoutineOutcome("DONE","今日流程已处理",results)
    }
}

/** Old unknown rows do not block a capped day; unknowns created by this run do. */
object RoutineAdCompletion {
    fun allowed(limitReached:Boolean, blockedReason:String, own:List<Pair<Boolean,Boolean>>):Boolean =
        own.all{it.first && it.second} && (limitReached || (blockedReason.isBlank() && own.isNotEmpty()))
}
