package com.anapocket.auto
object OguryClosePolicy {
 fun surface(pkg:String?,activity:String,ids:Set<String>,labels:List<String>):Boolean = pkg==StartupPolicy.ANA &&
  (AdSdkWindows.contains(activity)&&activity.startsWith("com.ogury.ad.interstitial.ui.") || ids.containsAll(setOf("root","format","optout","close-btn"))&&labels.any{it.trim()=="ogury_close_button"})
 fun control(pkg:String?,activity:String,ids:Set<String>,labels:List<String>,id:String?,label:String,clazz:String,widthDp:Float,heightDp:Float):Boolean =
  surface(pkg,activity,ids,labels)&&id=="close-btn"&&label.trim()=="ogury_close_button"&&clazz=="android.widget.Button"&&widthDp in 12f..64f&&heightDp in 12f..64f
}
