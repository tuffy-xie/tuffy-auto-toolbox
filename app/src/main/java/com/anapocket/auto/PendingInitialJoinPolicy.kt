package com.anapocket.auto

/** Isolation is permission to skip this key, never evidence of a reward or a new watching. */
data class PendingInitialJoin(
    val date:String,val key:String,val runId:String,val startedAt:Long,val index:Int,val attempts:Int,
    val initial:Boolean,val joined:Boolean,val boost:Int,val taskState:String,val adState:String,
    val confirmed:Boolean,val departed:Boolean,val reason:String,val dispatchPhase:String
)
data class InitialJoinIsolationProof(val date:String,val key:String,val runId:String,val startedAt:Long,
    val index:Int,val observedAt:Long,val kind:String)
object PendingInitialJoinPolicy {
    const val ISOLATION_KIND="native_unjoined_initial_no_sdk"
    private fun bound(a:PendingInitialJoin,date:String)=a.date==date&&a.key.isNotBlank()&&a.runId.startsWith("CH")&&
        a.startedAt>0&&a.index==1&&a.attempts==1&&a.initial&&!a.joined&&a.boost==0&&
        a.taskState=="AD_OPENING"&&a.adState=="AD_OPENING"&&!a.confirmed&&!a.departed&&a.reason.isBlank()
    fun mayInspect(a:PendingInitialJoin,date:String,pending:Int,nativeUnjoined:Boolean)=
        bound(a,date)&&pending==1&&nativeUnjoined&&a.dispatchPhase in setOf("","RESERVED")
    fun mayIsolate(a:PendingInitialJoin,date:String,pending:Int,nativeUnjoined:Boolean,stableMs:Long)=
        mayInspect(a,date,pending,nativeUnjoined)&&stableMs>=15_000&&a.dispatchPhase.isBlank()
    fun mayDispatchReserved(a:PendingInitialJoin,date:String,nativeUnjoined:Boolean)=
        bound(a,date)&&a.dispatchPhase=="RESERVED"&&nativeUnjoined
    fun isolated(a:PendingInitialJoin,p:InitialJoinIsolationProof?,date:String):Boolean =p!=null&&bound(a,date)&&
        a.dispatchPhase.isBlank()&&p.kind==ISOLATION_KIND&&p.date==a.date&&p.key==a.key&&p.runId==a.runId&&
        p.startedAt==a.startedAt&&p.index==a.index&&p.observedAt>=a.startedAt
    fun scopedPending(categories:List<String>,current:String)=categories.any{it==current}
    /** Unscanned categories are not pending ads. Every recorded pending row still needs a bound isolation. */
    fun independentDailyAllowed(pendingIsolation:List<Boolean>,sameDay:Boolean)=
        sameDay&&pendingIsolation.isNotEmpty()&&pendingIsolation.all{it}
    fun blocksDailyNavigation(hasPending:Boolean,onlySafelyIsolated:Boolean)=hasPending&&!onlySafelyIsolated
}
