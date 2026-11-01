package com.anapocket.auto

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import kotlinx.coroutines.delay

/** Resume the actual existing ANA task through the observed system card, without launching a new ad. */
object AdTaskReturn {
    private fun anaCard(s:String)=s.trim() in setOf("ANA Pocket,未加锁","ANA Pocket,Unlocked","ANA Pocket,ロック解除",
        "ANA Pocket,已加锁","ANA Pocket,Locked","ANA Pocket,ロック")
    suspend fun restore(svc:AutoAccessibilityService,date:String,run:String):Boolean {
        val ui=Ui(svc)
        val owner=CheckinRuntime.ownership.owner();val ownerRun=AutoAccessibilityService.currentRunId
        fun safe()=!AutoAccessibilityService.stopRequested&&TokyoDate.of()==date&&AutoAccessibilityService.isConnected()&&
            owner!=null&&owner.app=="ANA"&&owner.run==ownerRun&&AutoAccessibilityService.currentRunId==ownerRun&&
            CheckinRuntime.ownership.valid(owner,android.os.SystemClock.elapsedRealtime())
        ui.refreshCache();if(ui.currentPackage()==StartupPolicy.ANA)return true
        if(!safe()||!svc.performGlobalAction(AccessibilityService.GLOBAL_ACTION_RECENTS))return false
        var searches=0;var partialMoves=0;var previousCards=""
        repeat(36){
            if(!safe())return false
            delay(250);ui.refreshCache()
            val focused=svc.windows.any{(it.isActive||it.isFocused)&&it.root?.packageName?.toString()=="com.miui.home"}
            if(focused){
                val nodes=ui.activeNodes(pkg="com.miui.home").filter{it.isVisibleToUser}
                if(nodes.none{it.viewIdResourceName=="com.miui.home:id/recents_view"})return@repeat
                val cards=nodes.filter{it.isClickable&&anaCard(it.contentDescription?.toString().orEmpty())}
                if(cards.size>1)return false
                if(cards.isEmpty()){
                    val container=nodes.singleOrNull{it.viewIdResourceName=="com.miui.home:id/recents_view"}?:return false
                    val signature=nodes.filter{it.isClickable}.mapNotNull{it.contentDescription?.toString()?.takeIf{v->v.isNotBlank()}}.sorted().joinToString("|")
                    if(signature.isBlank()||signature==previousCards||!AdTaskReturnPolicy.maySearch(true,cards.size,searches,safe()))return false
                    previousCards=signature;val area=Rect();container.getBoundsInScreen(area)
                    val w=svc.resources.displayMetrics.widthPixels;val h=svc.resources.displayMetrics.heightPixels
                    if(area.width()<w*.8||area.height()<h*.4)return false
                    Evidence.write(svc,"ad_"+run+"_search_existing_task_"+searches+".txt","Observed system recents horizontal list; search only; no task removal.\n"+ui.dump())
                    searches++
                    if(!safe()||!ui.swipe(w/4,area.centerY().coerceIn(h/3,h*2/3),w*3/4,area.centerY().coerceIn(h/3,h*2/3),350))return false
                    delay(650);return@repeat
                }
                val card=cards.single();val b=Rect();card.getBoundsInScreen(b);val w=svc.resources.displayMetrics.widthPixels
                if(b.width()>=w*.45&&b.left>=0&&b.right<=w&&card.refresh()&&card.isVisibleToUser){
                    Evidence.write(svc,"ad_"+run+"_restore_existing_task.txt","Observed unique ANA system recent-task card="+card.contentDescription+" bounds="+b+"; tap only; no task removal or launcher reset.")
                    if(!safe()||!ui.tap(b.centerX(),b.centerY()))return false
                    repeat(24){if(!safe())return false;delay(250);ui.refreshCache();if(ui.currentPackage()==StartupPolicy.ANA)return true}
                    return false
                }
                // Only move towards a currently observed partial card; never swipe a task away.
                val direction=if(b.centerX()>w/2)-1 else 1
                if(partialMoves++<3)ui.swipe(b.centerX().coerceIn(w/5,w*4/5),b.centerY(),
                    (b.centerX()+direction*w*.52).toInt().coerceIn(w/8,w*7/8),b.centerY(),350)
            }
        }
        return false
    }
}

/** Search is allowed only within the observed native recents list, with a finite page budget. */
object AdTaskReturnPolicy {
    fun maySearch(recents:Boolean,matchingCards:Int,pages:Int,live:Boolean)=recents&&matchingCards==0&&pages in 0..3&&live
}
