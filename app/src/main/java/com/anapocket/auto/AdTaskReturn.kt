package com.anapocket.auto

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import kotlinx.coroutines.delay

/** Resume the actual existing ANA task through the observed system card, without launching a new ad. */
object AdTaskReturn {
    private fun anaCard(s:String)=s.trim() in setOf("ANA Pocket,未加锁","ANA Pocket,Unlocked","ANA Pocket,ロック解除",
        "ANA Pocket,已加锁","ANA Pocket,Locked","ANA Pocket,ロック")
    suspend fun restore(svc:AutoAccessibilityService,date:String,run:String):Boolean=restoreDetail(svc,date,run)==Outcome.RESTORED
    enum class Outcome{RESTORED,ABSENT,FAILED}

    /**
     * 11/12: if the user swipes ANA away while an ad result is still unknown, every later run stopped with
     * original_ana_task_unavailable for the rest of the day. Only when the whole Recents list was searched and has no ANA
     * card is ANA launched again and its startup popups cleared without any submission. What follows is the existing
     * "ad gone, ANA alive" review (participating scan / official counter): no new ad, no reward inferred.
     * Any other restore failure keeps its old meaning.
     */
    suspend fun restoreOrRelaunchIfGone(svc:AutoAccessibilityService,date:String,run:String):Boolean {
        when(restoreDetail(svc,date,run)){
            Outcome.RESTORED->return true
            Outcome.FAILED->return false
            Outcome.ABSENT->{}
        }
        val ui=Ui(svc)
        fun live()=!AutoAccessibilityService.stopRequested&&TokyoDate.of()==date&&AutoAccessibilityService.isConnected()
        if(!live())return false
        Evidence.write(svc,"ad_"+run+"_ana_task_absent_relaunch.txt","Whole native Recents list searched; no ANA card. ANA launched again; no new ad; unknown kept.\n"+ui.dump())
        Logger.w("ADRETURN","run=$run ana_task_absent fresh_launch=true no_new_ad=true unknown_kept=true")
        if(!ui.launchApp(StartupPolicy.ANA))return false
        var up=false
        repeat(30){if(!up&&live()){delay(400);ui.refreshCache();up=ui.currentPackage()==StartupPolicy.ANA}}
        if(!up||!live())return false
        // Same startup clearing as category navigation: startup ads closed, no login submission.
        val cleared=StartupFlow(svc).prepare("RELAUNCH"+System.currentTimeMillis(),launch=false,allowLoginSubmission=false).ok
        Logger.i("ADRETURN","run=$run relaunched_startup_cleared=$cleared")
        return cleared
    }

    private suspend fun restoreDetail(svc:AutoAccessibilityService,date:String,run:String):Outcome {
        val ui=Ui(svc)
        val owner=CheckinRuntime.ownership.owner();val ownerRun=AutoAccessibilityService.currentRunId
        fun safe()=!AutoAccessibilityService.stopRequested&&TokyoDate.of()==date&&AutoAccessibilityService.isConnected()&&
            owner!=null&&owner.app=="ANA"&&owner.run==ownerRun&&AutoAccessibilityService.currentRunId==ownerRun&&
            CheckinRuntime.ownership.valid(owner,android.os.SystemClock.elapsedRealtime())
        val ok=Outcome.RESTORED;val no=Outcome.FAILED
        ui.refreshCache();if(ui.currentPackage()==StartupPolicy.ANA)return ok
        if(!safe()||!svc.performGlobalAction(AccessibilityService.GLOBAL_ACTION_RECENTS))return no
        var searches=0;var partialMoves=0;var previousCards=""
        repeat(36){
            if(!safe())return no
            delay(250);ui.refreshCache()
            val focused=svc.windows.any{(it.isActive||it.isFocused)&&it.root?.packageName?.toString()=="com.miui.home"}
            if(focused){
                val nodes=ui.activeNodes(pkg="com.miui.home").filter{it.isVisibleToUser}
                if(nodes.none{it.viewIdResourceName=="com.miui.home:id/recents_view"})return@repeat
                val cards=nodes.filter{it.isClickable&&anaCard(it.contentDescription?.toString().orEmpty())}
                if(cards.size>1)return no
                if(cards.isEmpty()){
                    val container=nodes.singleOrNull{it.viewIdResourceName=="com.miui.home:id/recents_view"}?:return no
                    val signature=nodes.filter{it.isClickable}.mapNotNull{it.contentDescription?.toString()?.takeIf{v->v.isNotBlank()}}.sorted().joinToString("|")
                    // The native Recents container can precede its cards; retain the existing 36-poll budget.
                    if(signature.isBlank()){if(it==0)Logger.i("ADRETURN","await native recents first frame; no task action");return@repeat}
                    if(signature==previousCards||!AdTaskReturnPolicy.maySearch(true,cards.size,searches,safe()))
                        return if(AdTaskReturnPolicy.searchedWholeList(signature==previousCards,searches,safe()))Outcome.ABSENT else no
                    previousCards=signature;val area=Rect();container.getBoundsInScreen(area)
                    val w=svc.resources.displayMetrics.widthPixels;val h=svc.resources.displayMetrics.heightPixels
                    if(area.width()<w*.8||area.height()<h*.4)return no
                    Evidence.write(svc,"ad_"+run+"_search_existing_task_"+searches+".txt","Observed system recents horizontal list; search only; no task removal.\n"+ui.dump())
                    searches++
                    if(!safe()||!ui.swipe(w/4,area.centerY().coerceIn(h/3,h*2/3),w*3/4,area.centerY().coerceIn(h/3,h*2/3),350))return no
                    delay(650);return@repeat
                }
                val card=cards.single();val b=Rect();card.getBoundsInScreen(b);val w=svc.resources.displayMetrics.widthPixels
                if(b.width()>=w*.45&&b.left>=0&&b.right<=w&&card.refresh()&&card.isVisibleToUser){
                    Evidence.write(svc,"ad_"+run+"_restore_existing_task.txt","Observed unique ANA system recent-task card="+card.contentDescription+" bounds="+b+"; tap only; no task removal or launcher reset.")
                    if(!safe()||!ui.tap(b.centerX(),b.centerY()))return no
                    repeat(24){if(!safe())return no;delay(250);ui.refreshCache();if(ui.currentPackage()==StartupPolicy.ANA)return ok}
                    return no
                }
                // Only move towards a currently observed partial card; never swipe a task away.
                val direction=if(b.centerX()>w/2)-1 else 1
                if(partialMoves++<3)ui.swipe(b.centerX().coerceIn(w/5,w*4/5),b.centerY(),
                    (b.centerX()+direction*w*.52).toInt().coerceIn(w/8,w*7/8),b.centerY(),350)
            }
        }
        return no
    }
}

/** Search is allowed only within the observed native recents list, with a finite page budget. */
object AdTaskReturnPolicy {
    fun maySearch(recents:Boolean,matchingCards:Int,pages:Int,live:Boolean)=recents&&matchingCards==0&&pages in 0..3&&live
    /**
     * ANA is gone only when at least one scroll was made through a non-empty Recents list and it then stopped moving
     * (same cards seen twice) or the page budget ran out, with the run still live. Anything else is an ordinary failure.
     */
    fun searchedWholeList(sameCardsAgain:Boolean,pages:Int,live:Boolean)=live&&pages>=1&&(sameCardsAgain||pages>3)
}
