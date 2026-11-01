package com.anapocket.auto

/** Exact native Google ImageButton observed on the real black-screen interstitial. */
object GoogleNativeClosePolicy {
    fun matches(activity:String,className:String,label:String,visible:Boolean,enabled:Boolean,clickable:Boolean)=
        activity=="com.google.android.gms.ads.AdActivity"&&className=="android.widget.ImageButton"&&
        label=="Interstitial close button"&&visible&&enabled&&clickable
}
