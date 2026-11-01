package com.anapocket.auto

/** One normal Back after a timed-out, structurally empty Google ad. Never a reward receipt. */
object BlankGoogleAdPolicy {
    private val containers=setOf("android.widget.FrameLayout","android.widget.LinearLayout","android.widget.RelativeLayout","android.view.View")
    fun mayReturn(activity:String,timedOut:Boolean,elapsed:Long,alreadyReturned:Boolean,labels:List<String>,
        classes:List<String>,usableControls:Int,blackPixels:Boolean):Boolean =
        activity=="com.google.android.gms.ads.AdActivity"&&timedOut&&elapsed>=95_000&&!alreadyReturned&&
            labels.all{it.isBlank()}&&classes.isNotEmpty()&&classes.all{it in containers}&&usableControls==0&&blackPixels
}
