package com.anapocket.auto

/** Active native receipt fields outrank a stale SDK Activity event, never an actual SDK tree. */
object NativeChallengeReceiptPolicy {
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
    fun boundInitial(state:String,adState:String,initial:Boolean,index:Int,count:Int,pending:Int,sameDate:Boolean,
        departed:Boolean,confirmed:Boolean,originalStart:Boolean,originalReceipt:Boolean,nativePanel:Boolean,
        reward:Int,steps:List<Int>,labels:List<String>):Boolean = state=="UNCONFIRMED" && adState=="UNCONFIRMED" &&
        initial && index==1 && count==1 && pending==1 && sameDate && departed && !confirmed && originalStart &&
        originalReceipt && nativePanel && InitialJoinReceiptPolicy.matches(reward,steps,labels)
}
