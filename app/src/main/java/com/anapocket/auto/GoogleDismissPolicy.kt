package com.anapocket.auto

/** Actual Google responsive end card: dismiss-button has a separate NO THANKS text child. */
object GoogleDismissPolicy {
    /** Reuse existing stage budgets: prefer a unique SDK advance over a creative's Close label. */
    fun <T> stageControl(nodes:List<T>,dismiss:(T)->Boolean,advance:(T)->Boolean,close:(T)->Boolean):T? =
        nodes.filter(dismiss).singleOrNull()?:nodes.filter(advance).singleOrNull()?:nodes.firstOrNull(close)
    /** Veto only the observed central close-btn in this click-through creative, not a corner SDK X. */
    fun creativeClose(google:Boolean,ids:Set<String>,id:String?,top:Int,screenHeight:Int)=
        google&&screenHeight>0&&ids.containsAll(setOf("app-interstitial-slot","playable-area-och-anchor"))&&
            id=="close-btn"&&top>=screenHeight*.25

    /** The tracked window can be ANA's ComponentDialog while the Google interstitial is on screen; its slot id proves the ad. */
    fun interstitialActivity(activity:String)=activity in setOf("com.google.android.gms.ads.AdActivity","androidx.activity.ComponentDialog")
    fun advanceControl(pkg:String?,activity:String,ids:Set<String>,nodeClass:String,clickable:Boolean,label:String)=
        pkg=="us.ana_pocket.ANAPocket"&&interstitialActivity(activity)&&
            "app-interstitial-slot" in ids&&nodeClass=="android.widget.Button"&&clickable&&label.trim()=="Skip"
    fun noThanks(pkg:String?,activity:String,visibleIds:Set<String>,id:String?,nodeClass:String,
        clickable:Boolean,childLabels:List<String>):Boolean =
        pkg=="us.ana_pocket.ANAPocket"&&interstitialActivity(activity)&&
            visibleIds.containsAll(setOf("app-interstitial-slot","mys-wrapper","dismiss-button"))&&
            id=="dismiss-button"&&nodeClass=="android.view.View"&&clickable&&
            childLabels.map{it.trim()}.filter{it.isNotEmpty()}==listOf("NO THANKS")
}
