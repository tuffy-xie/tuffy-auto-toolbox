package com.anapocket.auto

/** Active native receipt fields outrank a stale SDK Activity event, never an actual SDK tree. */
data class InitialReceiptReview(val date:String,val key:String,val runId:String,val startedAt:Long,val index:Int,
    val reviewedAt:Long,val evidenceHash:String,val installedAt:Long)
object NativeChallengeReceiptPolicy {
    fun pendingForReview(state:String,isolated:Boolean)=state in setOf("AD_OPENING","UNCONFIRMED","JOINED")&&!isolated
    /** Only an uninterrupted original controller may attribute its own direct empty-SDK return. */
    fun liveBlankInitial(a:PendingInitialJoin,taskRun:String,controllerChild:String,backRun:String,date:String,
        installedAt:Long,ownerValid:Boolean,backRequested:Boolean,coldRecovery:Boolean)=
        reviewableInitial(a,date)&&a.runId==taskRun&&a.runId==controllerChild&&backRun==controllerChild&&
            installedAt in 1..a.startedAt&&ownerValid&&backRequested&&!coldRecovery
    const val REVIEW_KIND="operator_verified_original_dispatch_and_direct_sdk_return"
    fun reviewableInitial(a:PendingInitialJoin,date:String)=a.date==date&&a.key.isNotBlank()&&a.runId.startsWith("CH")&&
        a.startedAt>0&&a.index==1&&a.attempts==1&&a.initial&&!a.joined&&a.boost==0&&!a.confirmed&&a.departed&&
        a.dispatchPhase=="DISPATCHED"&&a.taskState=="UNCONFIRMED"&&a.adState=="UNCONFIRMED"&&a.reason=="ad_timeout_95s"
    fun reviewedIdentity(a:PendingInitialJoin,r:InitialReceiptReview,date:String,now:Long,installedAt:Long)=
        a.date==date&&r.date==date&&a.key==r.key&&a.runId==r.runId&&a.startedAt==r.startedAt&&a.index==r.index&&
        a.index==1&&a.attempts==1&&a.initial&&a.departed&&a.dispatchPhase=="DISPATCHED"&&
        r.evidenceHash.matches(Regex("[0-9a-f]{64}"))&&r.installedAt==installedAt&&installedAt>0&&
        r.reviewedAt>=maxOf(a.startedAt,installedAt)&&now>=r.reviewedAt&&now-r.reviewedAt<=600_000&&
        (reviewableInitial(a,date)||(a.confirmed&&a.joined&&a.boost==0&&a.taskState=="JOINED"&&a.adState=="REWARD_CONFIRMED"))

    /** Identity fence only; the existing caller must first prove a stable official outcome. */
    fun samePendingBoost(expected:PendingInitialJoin,current:PendingInitialJoin?,date:String):Boolean =
        current==expected&&expected.date==date&&expected.key.isNotBlank()&&expected.runId.startsWith("CH")&&
            expected.startedAt>0&&expected.joined&&!expected.initial&&!expected.confirmed&&
            expected.boost in 0..4&&expected.index==expected.attempts&&expected.index>0

    /** A pre-dispatch official binding is required; legacy UNKNOWN never qualifies by reward alone. */
    fun boundOfficialInitial(a:PendingInitialJoin,pending:Int,date:String,identityMatched:Boolean,nativePanel:Boolean,
        reward:Int,steps:List<Int>,labels:List<String>):Boolean =
        identityMatched&&nativePanel&&a.date==date&&a.key.isNotBlank()&&a.runId.startsWith("CH")&&
            a.startedAt>0&&a.index==1&&a.attempts==1&&a.initial&&!a.joined&&a.boost==0&&!a.confirmed&&
            a.departed&&a.dispatchPhase=="DISPATCHED"&&pending==1&&
            a.taskState in setOf("AD_OPENING","UNCONFIRMED")&&
            a.adState in setOf("AD_OPENING","AD_PLAYING","AD_CLOSE_AVAILABLE","UNCONFIRMED")&&
            InitialJoinReceiptPolicy.matches(reward,steps,labels)

    private const val PKG="us.ana_pocket.ANAPocket"
    fun panel(pkg:String?,ids:Set<String>,labels:List<String>):Boolean = pkg==PKG &&
        listOf("title_count","current_point","boost_list").all{PKG+":id/"+it in ids} &&
        ids.none{it=="__root_view__"||it.endsWith(":id/ia_endcard_video_overlay")||it.contains(":id/mbridge_")} &&
        ChallengeSignals.dialog(labels) && ChallengeSignals.offeredReward(labels)!=null && ChallengeSignals.increments(labels).size==5
    fun originalStartFields(text:String,title:String,date:String,reward:Int):Boolean {
        val lines=text.substringAfter("NODE_DETAILS","").lineSequence().filter{it.contains("visible=true")}.toList()
        fun field(id:String)=lines.filter{it.contains("id="+PKG+":id/"+id+" ")}.singleOrNull()?.substringAfterLast("label=")
        return field("text_name")==title && AnaCalendarDate.same(field("text_date"),date) &&
            field("button_join_boost_point_text")==reward.toString()+"pt"
    }
    fun recordedInitialReceipt(text:String,reward:Int,steps:List<Int>):Boolean {
        val lines=text.substringAfter("NODE_DETAILS","").lineSequence().filter{it.contains("visible=true")}.toList()
        fun fields(id:String)=lines.filter{it.contains("id="+PKG+":id/"+id+" ")}.map{it.substringAfterLast("label=")}
        return fields("title_join")==listOf("チャレンジをはじめました！") &&
            fields("title_count")==listOf("1回目のブーストをしますか？") && fields("current_point")==listOf(reward.toString()+"pt") &&
            fields("point").mapNotNull{it.removePrefix("+").toIntOrNull()}==steps && steps.size==5 && steps.all{it>0}
    }
    /** No new ad dispatch. The caller holds the ledger lock and persists before returning to navigation. */
    fun commitInitialOnce(expected:PendingInitialJoin,current:PendingInitialJoin?,date:String,pending:Int,
        originalStart:Boolean,recordedReceipt:Boolean,exactNativeIdentity:Boolean,nativePanel:Boolean,
        reward:Int,steps:List<Int>,labels:List<String>,operatorIdentity:Boolean=false,liveIdentity:Boolean=false,persist:()->Unit):Boolean {
        if(current==null||expected.date!=date||current.date!=date||expected.key.isBlank()||
            !expected.runId.startsWith("CH")||expected.startedAt<=0||expected.index!=1||expected.attempts!=1||!expected.initial||
            current.key!=expected.key||current.runId!=expected.runId||current.startedAt!=expected.startedAt||
            current.index!=expected.index||current.attempts!=1||!current.initial||current.dispatchPhase!=expected.dispatchPhase)return false
        val attributed=recordedReceipt||(current.dispatchPhase=="DISPATCHED"&&(exactNativeIdentity||operatorIdentity||liveIdentity))
        if(!originalStart||!attributed||!nativePanel||pending!=1||!InitialJoinReceiptPolicy.matches(reward,steps,labels))return false
        if(current.confirmed)return current.joined&&current.boost==0&&current.taskState=="JOINED"&&current.adState=="REWARD_CONFIRMED"
        if(!boundInitial(current.taskState,current.adState,current.initial,current.index,current.attempts,pending,true,
            current.departed,current.confirmed,originalStart,attributed,nativePanel,reward,steps,labels))return false
        persist()
        return true
    }
    fun boundInitial(state:String,adState:String,initial:Boolean,index:Int,count:Int,pending:Int,sameDate:Boolean,
        departed:Boolean,confirmed:Boolean,originalStart:Boolean,originalReceipt:Boolean,nativePanel:Boolean,
        reward:Int,steps:List<Int>,labels:List<String>):Boolean = state=="UNCONFIRMED" && adState=="UNCONFIRMED" &&
        initial && index==1 && count==1 && pending==1 && sameDate && departed && !confirmed && originalStart &&
        originalReceipt && nativePanel && InitialJoinReceiptPolicy.matches(reward,steps,labels)
}
