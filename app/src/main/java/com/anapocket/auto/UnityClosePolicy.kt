package com.anapocket.auto

/** Actual CAR40 failure: bottom HTML close_button_svg overlapped the Unity Privacy button. */
object UnityClosePolicy {
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
