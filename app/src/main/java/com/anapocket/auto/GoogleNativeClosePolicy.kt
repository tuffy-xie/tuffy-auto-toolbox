package com.anapocket.auto

/** Exact native Google ImageButton observed on the real black-screen interstitial. */
object GoogleNativeClosePolicy {
    fun matches(activity:String,className:String,label:String,visible:Boolean,enabled:Boolean,clickable:Boolean)=
        activity=="com.google.android.gms.ads.AdActivity"&&className=="android.widget.ImageButton"&&
        label=="Interstitial close button"&&visible&&enabled&&clickable
    /** 11/12 00:20: the hint text itself, never a close control (it was tapped for 95 s). */
    fun scrollPrompt(label:String)=label.trim().equals("Scroll to bottom of ad to close",true)
    const val MAX_SCROLLS=6
    fun scrollToClose(activity:String,labels:List<String>)=
        activity=="com.google.android.gms.ads.AdActivity"&&labels.any{scrollPrompt(it)}
}
