package com.anapocket.auto

/**
 * 11/12-11/14: 18 ads could not be closed (8 AppLovin playables, 6 Unity store-only cards, ...). Their node trees
 * show a bare WebView canvas, so the close could not be designed from text. A scene saved for one of these
 * reasons keeps a screenshot inside the same 256 MB fault store; nothing else changes.
 */
object FaultScreenshotPolicy {
    const val MAX_BYTES=3*1024*1024
    private val adEnd=setOf("ad_timeout_95s","ad_part_timeout_95s","ad_timeout_300s","startup_ad_timeout_300s","completion_not_confirmed")
    fun always(module:String,reason:String):Boolean = reason in adEnd || module=="login_ad"&&reason=="before_close"
}
