package com.anapocket.auto

/** Actual CAR40 failure: bottom HTML close_button_svg overlapped the Unity Privacy button. */
object UnityClosePolicy {
    const val ACTIVITY="com.unity3d.ads.adplayer.FullScreenWebViewDisplay"
    const val MAX_STORE_SKIPS=2
    const val STORE_SKIP_AFTER_MS=30_000L
    fun storeSkipDue(shownAloneMs:Long)=shownAloneMs>=STORE_SKIP_AFTER_MS
    /**
     * 11/14 01:31 CAR 5km join: a Unity playable (Cat Sudoku) showed only a top-left Button 「打开商店」 and bottom Privacy, no
     * close, and every retry timed out. The user observed that pressing 「打开商店」 makes the close appear; the Play sheet it
     * opens is closed by the existing sheet path. Only used when no close was found, at most MAX_STORE_SKIPS per ad.
     */
    fun storeSkip(activity:String?,nodeClass:String,label:String,clickable:Boolean,left:Int,top:Int,width:Int,height:Int,
        screenWidth:Int,screenHeight:Int,skipsSoFar:Int):Boolean =
        activity==ACTIVITY&&skipsSoFar<MAX_STORE_SKIPS&&clickable&&nodeClass=="android.widget.Button"&&
            label.trim() in setOf("打开商店","Open Store","ストアを開く")&&width>0&&height>0&&
            left>=0&&left+width<=screenWidth*.40&&top>=0&&top+height<=screenHeight*.15
    fun privacyOverlay(labels:List<String>) = labels.contains("button:privacy-back") &&
        labels.any{it=="button:privacy-request-data"||it=="button:privacy-report-ad"}
    fun endControl(left:Int,top:Int,width:Int,height:Int,screenWidth:Int,screenHeight:Int,density:Float):Boolean =
        density>0 && width/density in 12f..64f && height/density in 12f..64f &&
            left>=screenWidth*.60 && top>=0 && top<screenHeight*.30 && left+width<=screenWidth && top+height<=screenHeight

    /** Observed Unity HTML end card has an explicit top-left Close button, separate from bottom Privacy. */
    fun labelledEndControl(label:String, nodeClass:String, clickable:Boolean, labels:List<String>,
        left:Int,top:Int,width:Int,height:Int,screenWidth:Int,screenHeight:Int,density:Float):Boolean =
        label=="Close" && nodeClass=="android.widget.Button" && clickable && AdSignals.unityEndCard(labels) &&
            density>0 && width/density in 12f..64f && height/density in 12f..64f &&
            left>=0 && left<screenWidth*.25 && top>=0 && top<screenHeight*.20 &&
            left+width<=screenWidth && top+height<=screenHeight
}
