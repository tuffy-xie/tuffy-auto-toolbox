package com.anapocket.auto

/** Observed early-close warning pauses playback; Continue resumes this same ad, never a new one. */
object MBridgePlaybackPolicy {
    fun remaining(labels:List<String>):Int?=labels.mapNotNull{
        Regex("^(\\d{1,3})s left to be rewarded$").matchEntire(it.trim())?.groupValues?.get(1)?.toIntOrNull()
    }.maxOrNull()
    fun warning(labels:List<String>)=labels.contains("Confirm to close?")&&
        labels.contains("You will not be rewarded after closing the window")&&labels.contains("Close it")&&labels.contains("Continue")
    fun mustWait(labels:List<String>)=warning(labels)||(remaining(labels)?:0)>0
    fun mayContinue(labels:List<String>,requests:Int)=warning(labels)&&requests in 0..1
    fun playableControl(id:String?,nodeClass:String,clickable:Boolean,left:Int,top:Int,width:Int,height:Int,
        screenWidth:Int,screenHeight:Int,density:Float,labels:List<String>):Boolean =
        // "close" is the end-card X (Nov 9, 630,78-690,140), a blank TextView beside the left-hand "privacyIcon".
        id in setOf("m-playable-skip","m-playable-close","close")&&nodeClass=="android.widget.TextView"&&clickable&&
            !mustWait(labels)&&density>0&&width/density in 12f..64f&&height/density in 12f..64f&&
            left>=screenWidth*.6&&top>=0&&top<screenHeight*.2&&left+width<=screenWidth&&top+height<=screenHeight
}
