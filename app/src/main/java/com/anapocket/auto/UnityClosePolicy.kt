package com.anapocket.auto

/** Actual CAR40 failure: bottom HTML close_button_svg overlapped the Unity Privacy button. */
object UnityClosePolicy {
    fun privacyOverlay(labels:List<String>) = labels.contains("button:privacy-back") &&
        labels.any{it=="button:privacy-request-data"||it=="button:privacy-report-ad"}
    fun endControl(left:Int,top:Int,width:Int,height:Int,screenWidth:Int,screenHeight:Int,density:Float):Boolean =
        density>0 && width/density in 12f..64f && height/density in 12f..64f &&
            left>=screenWidth*.60 && top>=0 && top<screenHeight*.30 && left+width<=screenWidth && top+height<=screenHeight
}
