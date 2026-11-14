package com.anapocket.auto

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject

object ManagedCleanupPolicy {
    fun card(description:String,aliases:Set<String>):Boolean {
        val t=description.trim()
        return aliases.any{t.startsWith(it+",")||t.startsWith(it+"，")}&&
            (t.contains("未锁定")||t.contains("未加锁")||t.contains("Unlocked",true))&&!t.contains("已锁定")&&!t.contains("已加锁")
    }
    /** App label of an unlocked recents card that is not one of [keep]; null for Tuffy's own card, locked cards and non-cards. */
    fun otherCard(description:String,keep:Set<String>):String? {
        val t=description.trim();val cut=t.indexOfFirst{it==','||it=='，'}
        if(cut<=0)return null
        val label=t.substring(0,cut).trim()
        if(label.isBlank()||label in keep||!card(t,setOf(label)))return null
        return label
    }
}
/** HyperOS observed recents UI only. Never calls force-stop, root, shell, or removes Tuffy. */
object ManagedAppCleanup {
    suspend fun run(svc:AutoAccessibilityService,pkg:String,safe:()->Boolean,dispatch:((()->Unit)->Boolean)?=null):JSONObject {
        val out=JSONObject().put("appId",pkg).put("returnedHome",false).put("recentTaskRemoved",false)
            .put("processState","PROCESS_NOT_VERIFIED").put("closeState","CLOSE_LIMITED").put("forceStopped",false)
        if(pkg==svc.packageName||pkg !in setOf(StartupPolicy.ANA,CheckinApp.RAKUTEN.pkg,CheckinApp.STEAK.pkg,CheckinApp.TIKTOK.pkg)||!safe())return out
        val ui=Ui(svc)
        val appLabel=runCatching{svc.packageManager.getApplicationLabel(svc.packageManager.getApplicationInfo(pkg,0)).toString()}.getOrDefault("")
        val aliases=setOf(appLabel,when(pkg){StartupPolicy.ANA->"ANA Pocket";CheckinApp.RAKUTEN.pkg->"楽天PointClub";CheckinApp.TIKTOK.pkg->"TikTok Lite";else->"いきなり！ステーキ"}).filter{it.isNotBlank()}.toSet()
        fun act(block:()->Unit):Boolean {if(!safe())return false;return dispatch?.invoke(block)?:run{block();true}}
        suspend fun swipe(x:Int,y:Int,ex:Int,ey:Int,duration:Long){
            kotlinx.coroutines.suspendCancellableCoroutine<Unit>{c->
                val path=android.graphics.Path().apply{moveTo(x.toFloat(),y.toFloat());lineTo(ex.toFloat(),ey.toFloat())}
                val gesture=android.accessibilityservice.GestureDescription.Builder().addStroke(android.accessibilityservice.GestureDescription.StrokeDescription(path,0,duration)).build()
                val accepted=act{svc.dispatchGesture(gesture,object:AccessibilityService.GestureResultCallback(){override fun onCompleted(g:android.accessibilityservice.GestureDescription){if(c.isActive)c.resumeWith(Result.success(Unit))};override fun onCancelled(g:android.accessibilityservice.GestureDescription){if(c.isActive)c.resumeWith(Result.success(Unit))}},null)}
                if(!accepted&&c.isActive)c.resumeWith(Result.success(Unit))
            }
        }
        withTimeoutOrNull(7000){
            if(!safe())return@withTimeoutOrNull
            act{svc.performGlobalAction(AccessibilityService.GLOBAL_ACTION_RECENTS)};delay(600)
            repeat(3){
                if(!safe())return@withTimeoutOrNull
                ui.refreshCache();val nodes=ui.activeNodes(pkg="com.miui.home")
                if(nodes.none{it.viewIdResourceName=="com.miui.home:id/recents_view"})return@withTimeoutOrNull
                val cards=nodes.filter{it.isClickable&&ManagedCleanupPolicy.card(it.contentDescription?.toString().orEmpty(),aliases)}
                if(cards.size!=1)return@withTimeoutOrNull
                val card=cards.single();val b=Rect();card.getBoundsInScreen(b);val width=svc.resources.displayMetrics.widthPixels
                if(b.width()<width*.45||b.left<=0||b.right>=width){
                    val direction=if(b.centerX()>width/2)-1 else 1
                    if(safe())swipe(b.centerX().coerceIn(width/5,width*4/5),b.centerY(),(b.centerX()+direction*width*.52).toInt().coerceIn(width/8,width*7/8),b.centerY(),350)
                    delay(450)
                }else{
                    if(!safe()||!card.refresh()||!ManagedCleanupPolicy.card(card.contentDescription?.toString().orEmpty(),aliases))return@withTimeoutOrNull
                    swipe(b.centerX(),b.centerY(),b.centerX(),(b.top-b.height()/4).coerceAtLeast(80),450);delay(650);ui.refreshCache()
                    val after=ui.activeNodes(pkg="com.miui.home")
                    if(after.any{it.viewIdResourceName=="com.miui.home:id/recents_view"}&&after.none{ManagedCleanupPolicy.card(it.contentDescription?.toString().orEmpty(),aliases)})
                        out.put("recentTaskRemoved",true).put("closeState","RECENT_TASK_REMOVED")
                    return@withTimeoutOrNull
                }
            }
        }
        if(safe()){act{svc.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)};delay(650);ui.refreshCache();
            val home=android.content.Intent(android.content.Intent.ACTION_MAIN).addCategory(android.content.Intent.CATEGORY_HOME)
            val homePkg=svc.packageManager.resolveActivity(home,0)?.activityInfo?.packageName
            out.put("returnedHome",ui.currentPackage()==homePkg)
        }
        return out
    }

    /**
     * End of a finished task: swipe away every unlocked recents card except Tuffy's own, then go home.
     * Uses the recents UI only, like [run]; cards the user locked are left alone.
     */
    suspend fun clearOthers(svc:AutoAccessibilityService,safe:()->Boolean,dispatch:((()->Unit)->Boolean)?=null):JSONObject {
        val keep=setOf(runCatching{svc.packageManager.getApplicationLabel(svc.applicationInfo).toString()}.getOrDefault(""),"Tuffy","Tuffy的自动百宝箱").filter{it.isNotBlank()}.toSet()
        val removed=org.json.JSONArray()
        val out=JSONObject().put("mode","CLEAR_ALL_EXCEPT_TUFFY").put("kept",org.json.JSONArray(keep.toList())).put("removed",removed).put("returnedHome",false).put("forceStopped",false)
        if(!safe())return out.put("closeState","NOT_SAFE")
        val ui=Ui(svc)
        fun act(block:()->Unit):Boolean {if(!safe())return false;return dispatch?.invoke(block)?:run{block();true}}
        suspend fun swipe(x:Int,y:Int,ex:Int,ey:Int,duration:Long){
            kotlinx.coroutines.suspendCancellableCoroutine<Unit>{c->
                val path=android.graphics.Path().apply{moveTo(x.toFloat(),y.toFloat());lineTo(ex.toFloat(),ey.toFloat())}
                val gesture=android.accessibilityservice.GestureDescription.Builder().addStroke(android.accessibilityservice.GestureDescription.StrokeDescription(path,0,duration)).build()
                val accepted=act{svc.dispatchGesture(gesture,object:AccessibilityService.GestureResultCallback(){override fun onCompleted(g:android.accessibilityservice.GestureDescription){if(c.isActive)c.resumeWith(Result.success(Unit))};override fun onCancelled(g:android.accessibilityservice.GestureDescription){if(c.isActive)c.resumeWith(Result.success(Unit))}},null)}
                if(!accepted&&c.isActive)c.resumeWith(Result.success(Unit))
            }
        }
        var state="RECENTS_NOT_OPENED"
        withTimeoutOrNull(60_000){
            if(!safe())return@withTimeoutOrNull
            act{svc.performGlobalAction(AccessibilityService.GLOBAL_ACTION_RECENTS)};delay(700)
            var idle=0
            repeat(30){
                if(!safe()){state="STOPPED";return@withTimeoutOrNull}
                ui.refreshCache();val nodes=ui.activeNodes(pkg="com.miui.home")
                if(nodes.none{it.viewIdResourceName=="com.miui.home:id/recents_view"}){if(state=="RECENTS_NOT_OPENED")return@withTimeoutOrNull;state="RECENTS_LEFT";return@withTimeoutOrNull}
                state="RECENTS_OPEN"
                val width=svc.resources.displayMetrics.widthPixels
                val cards=nodes.filter{it.isClickable}.mapNotNull{n->ManagedCleanupPolicy.otherCard(n.contentDescription?.toString().orEmpty(),keep)?.let{n to it}}
                if(cards.isEmpty()){state="ONLY_KEPT_OR_LOCKED_LEFT";return@withTimeoutOrNull}
                val full=cards.firstOrNull{(n,_)->val b=Rect();n.getBoundsInScreen(b);b.width()>=width*.45&&b.left>0&&b.right<width}
                if(full!=null){
                    val (card,label)=full;val b=Rect();card.getBoundsInScreen(b)
                    if(!card.refresh()||ManagedCleanupPolicy.otherCard(card.contentDescription?.toString().orEmpty(),keep)!=label)return@repeat
                    swipe(b.centerX(),b.centerY(),b.centerX(),(b.top-b.height()/4).coerceAtLeast(80),450);delay(650)
                    removed.put(label);idle=0
                }else{
                    if(++idle>6){state="CARD_NOT_REACHABLE";return@withTimeoutOrNull}
                    val (card,_)=cards.minByOrNull{(n,_)->val b=Rect();n.getBoundsInScreen(b);kotlin.math.abs(b.centerX()-width/2)}!!
                    val b=Rect();card.getBoundsInScreen(b)
                    val direction=if(b.centerX()>width/2)-1 else 1
                    swipe(b.centerX().coerceIn(width/5,width*4/5),b.centerY(),(b.centerX()+direction*width*.52).toInt().coerceIn(width/8,width*7/8),b.centerY(),350)
                    delay(450)
                }
            }
            state="ATTEMPT_LIMIT"
        }
        out.put("closeState",state)
        if(safe()){act{svc.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)};delay(650);ui.refreshCache()
            val home=android.content.Intent(android.content.Intent.ACTION_MAIN).addCategory(android.content.Intent.CATEGORY_HOME)
            val homePkg=svc.packageManager.resolveActivity(home,0)?.activityInfo?.packageName
            out.put("returnedHome",ui.currentPackage()==homePkg)
        }
        Logger.i("CLEANUP","clear others state=$state removed=${removed.length()} home=${out.optBoolean("returnedHome")}")
        return out
    }
}
