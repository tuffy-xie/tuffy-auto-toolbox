package com.anapocket.auto

/** Observed early-close warning pauses playback; Continue resumes this same ad, never a new one. */
object MBridgePlaybackPolicy {
    // 11/21 #19 showed the same countdown in Chinese: "29s后即可获得奖励".
    private val countdowns=listOf(Regex("^(\\d{1,3})s left to be rewarded$"),Regex("^(\\d{1,3})s后即可获得奖励$"))
    fun remaining(labels:List<String>):Int?=labels.mapNotNull{l->
        countdowns.firstNotNullOfOrNull{it.matchEntire(l.trim())}?.groupValues?.get(1)?.toIntOrNull()
    }.maxOrNull()
    fun warning(labels:List<String>)=labels.contains("Confirm to close?")&&
        labels.contains("You will not be rewarded after closing the window")&&labels.contains("Close it")&&labels.contains("Continue")
    fun mustWait(labels:List<String>)=warning(labels)||(remaining(labels)?:0)>0
    fun mayContinue(labels:List<String>,requests:Int)=warning(labels)&&requests in 0..1
    /**
     * 11/21 02:30: the warning's Continue is an android.widget.Button
     * (mbridge_video_common_alertview_cancel_button, 390,916-600,972); a TextView-only match never found it and the
     * dialog stayed up through two retries. Close it (the confirm_button) is never accepted.
     */
    fun continueControl(id:String?,nodeClass:String,label:String,clickable:Boolean)=clickable&&label.trim()=="Continue"&&
        nodeClass in setOf("android.widget.Button","android.widget.TextView")&&
        (id==null||id.substringAfterLast('/')=="mbridge_video_common_alertview_cancel_button")
    fun playableControl(id:String?,nodeClass:String,clickable:Boolean,left:Int,top:Int,width:Int,height:Int,
        screenWidth:Int,screenHeight:Int,density:Float,labels:List<String>):Boolean =
        // "close" is the end-card X (Nov 9, 630,78-690,140), a blank TextView beside the left-hand "privacyIcon".
        id in setOf("m-playable-skip","m-playable-close","close")&&nodeClass=="android.widget.TextView"&&clickable&&
            !mustWait(labels)&&density>0&&width/density in 12f..64f&&height/density in 12f..64f&&
            left>=screenWidth*.6&&top>=0&&top<screenHeight*.2&&left+width<=screenWidth&&top+height<=screenHeight

    /**
     * 11/16 WALK_4: the video layer's "close" (640,20) stays in the tree under the end card's "close" (630,78), so two
     * nodes qualified and singleOrNull() returned nothing for 88 s. Several matches are accepted only when every one
     * is the same "close" id; tree order is drawing order, so the last one is the topmost, visible end-card X.
     */
    fun pick(ids:List<String?>):Int? = when {
        ids.size==1 -> 0
        ids.size>1&&ids.all{it=="close"} -> ids.lastIndex
        else -> null
    }

    /** The native reward end card: only it carries the "猜你喜欢" more-offer row under the store button. */
    fun nativeEndCard(ids:Set<String>)=ids.any{it.endsWith(":id/mbridge_reward_end_card_more_offer_rl")}
    /**
     * 11/23 01:07, 01:26 and 01:44: the native end card's X is an id-less, unlabelled ImageView in the top bar
     * (650,40-700,90), opposite the "!" feedback icon (20,40-70,90). Over a bright blurred backdrop the white-pixel cross
     * test saw the backdrop as part of the icon, so the X was never found and three Boost ads ran to the 300 s cap; the
     * last of them left 汽车 15km at Boost 3. Only the top-right one of these small corner icons is the close, and only
     * on the end card, where no countdown or warning is showing.
     */
    fun endCardCornerClose(ids:Set<String>,id:String?,nodeClass:String,clickable:Boolean,label:String,childCount:Int,
        left:Int,top:Int,width:Int,height:Int,screenWidth:Int,screenHeight:Int,density:Float,labels:List<String>):Boolean =
        nativeEndCard(ids)&&!mustWait(labels)&&id==null&&nodeClass=="android.widget.ImageView"&&clickable&&label.isBlank()&&
            childCount==0&&density>0&&width/density in 16f..48f&&height/density in 16f..48f&&
            width.toFloat()/height in .8f..1.25f&&left>=screenWidth*.75&&left+width<=screenWidth&&top>=0&&top<screenHeight*.1
}
