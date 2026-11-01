package com.anapocket.auto

/** A missing original SDK can only authorize a counter inspection, never another viewing. */
object BoundAdReviewPolicy {
    fun mayInspect(connected:Boolean,sameDate:Boolean,ownsUi:Boolean,stopped:Boolean,
        anaForeground:Boolean,activity:String,labels:List<String>):Boolean =
        connected&&sameDate&&ownsUi&&!stopped&&anaForeground&&
        activity=="us.ana_pocket.ANAPocket.views.home.HomeActivity"&&
        labels.contains("広告視聴")&&!AdSignals.receipt(labels)&&!AdSignals.error(labels)

    fun missingSurfaceReason(before:Int,fresh:Int?):String = when {
        fresh==null -> "original_ad_surface_missing_counter_unavailable"
        fresh<before -> "official_counter_regressed"
        fresh==before -> "original_ad_surface_missing_counter_unchanged"
        fresh==before+1 -> "original_ad_surface_missing_receipt_unproven"
        else -> "counter_jump_ambiguous"
    }
}
