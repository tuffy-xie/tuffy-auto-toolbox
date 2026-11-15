package com.anapocket.auto
object OguryClosePolicy {
 // 11/16 BIKE_2: after the video Ogury showed id=end_screen with a 「继续」 Button id=continue_button whose label is
 // the same vendor "ogury_close_button"; only close-btn was accepted, so the finished ad sat 95 s and ANA was restarted.
 private const val CLOSE="close-btn"; private const val END_CONTINUE="continue_button"
 fun surface(pkg:String?,activity:String,ids:Set<String>,labels:List<String>):Boolean = pkg==StartupPolicy.ANA &&
  (AdSdkWindows.contains(activity)&&activity.startsWith("com.ogury.ad.interstitial.ui.") ||
   (ids.containsAll(setOf("root","format","optout",CLOSE))||ids.containsAll(setOf("root","format","optout","end_screen",END_CONTINUE)))&&
   labels.any{it.trim()=="ogury_close_button"})
 fun control(pkg:String?,activity:String,ids:Set<String>,labels:List<String>,id:String?,label:String,clazz:String,widthDp:Float,heightDp:Float):Boolean =
  surface(pkg,activity,ids,labels)&&(id==CLOSE||id==END_CONTINUE&&"end_screen" in ids)&&label.trim()=="ogury_close_button"&&
   clazz=="android.widget.Button"&&widthDp in 12f..64f&&heightDp in 12f..64f
}
