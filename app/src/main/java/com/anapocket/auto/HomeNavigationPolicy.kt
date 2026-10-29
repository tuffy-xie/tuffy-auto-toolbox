package com.anapocket.auto
object HomeNavigationPolicy {
 fun nativeHome(pkg:String?,labels:List<String>):Boolean = pkg==StartupPolicy.ANA && labels.contains("ホーム") && labels.contains("ためる") && !AdSignals.receipt(labels)
 fun nativeDetail(pkg:String?,labels:List<String>,ids:Set<String>):Boolean = pkg==StartupPolicy.ANA && ids.containsAll(setOf("design_bottom_sheet","text_name","text_date","close")) && labels.any{Cards.fromTitle(it)!=null} && !AdSignals.receipt(labels) && !ids.contains("image_close")
}
