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
}
/** HyperOS observed recents UI only. Never calls force-stop, root, shell, or removes Tuffy. */
object ManagedAppCleanup {
    suspend fun run(svc:AutoAccessibilityService,pkg:String,safe:()->Boolean,dispatch:((()->Unit)->Boolean)?=null):JSONObject {
        val out=JSONObject().put("appId",pkg).put("returnedHome",false).put("recentTaskRemoved",false)
            .put("processState","PROCESS_NOT_VERIFIED").put("closeState","CLOSE_LIMITED").put("forceStopped",false)
        if(pkg==svc.packageName||pkg !in setOf(StartupPolicy.ANA,CheckinApp.RAKUTEN.pkg,CheckinApp.STEAK.pkg)||!safe())return out
        val ui=Ui(svc)
        val appLabel=runCatching{svc.packageManager.getApplicationLabel(svc.packageManager.getApplicationInfo(pkg,0)).toString()}.getOrDefault("")
        val aliases=setOf(appLabel,when(pkg){StartupPolicy.ANA->"ANA Pocket";CheckinApp.RAKUTEN.pkg->"楽天PointClub";else->"いきなり！ステーキ"}).filter{it.isNotBlank()}.toSet()
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
}
