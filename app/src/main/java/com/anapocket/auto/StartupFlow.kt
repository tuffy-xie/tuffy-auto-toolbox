package com.anapocket.auto

import android.content.Context
import android.graphics.Rect
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.delay
import org.json.JSONObject

object LoginBonusStore {
    private fun sp(c:Context)=c.getSharedPreferences("ana_login_bonus_v1",Context.MODE_PRIVATE)
    @Synchronized fun read(c:Context,date:String):JSONObject?=sp(c).getString("day_"+date,null)?.let{JSONObject(it)}
    @Synchronized fun save(c:Context,date:String,row:JSONObject){check(sp(c).edit().putString("day_"+date,row.toString()).commit()){ "login_ledger_write_failed" }}
    @Synchronized fun reserve(c:Context,date:String,run:String):JSONObject? {
        val old=read(c,date)
        if(TokyoDate.of()!=date || !StartupPolicy.canReserveLogin(date,old?.optString("date"),old?.optString("state")))return null
        return JSONObject().put("runId",run).put("date",date).put("state","AD_OPENING").put("startedAt",System.currentTimeMillis())
            .put("completed",false).put("rewardConfirmed",false).also{save(c,date,it)}
    }
    fun label(c:Context,date:String):String=when(read(c,date)?.optString("state")){
        "REWARD_CONFIRMED"->"领奖广告回执已确认"
        "AD_OPENING","AD_PLAYING"->"领奖广告处理中"
        "UNCONFIRMED","FAILED"->"领奖结果待核对"
        "NOT_PRESENT"->"本次未出现签到弹窗"
        else->"首次打开时自动检查"
    }
}

data class StartupResult(val ok:Boolean,val loginState:String,val closedPopups:Int,val reason:String="")

/** Called only at navigation boundaries, never in the middle of a reserved challenge/daily ad. */
class StartupFlow(private val svc:AutoAccessibilityService) {
    private val ui=Ui(svc)
    private val close=AdUiClose(svc)
    private val pkg=StartupPolicy.ANA
    private fun nodes()=ui.foregroundNodes().filter{it.isVisibleToUser}
    private fun labels()=nodes().map{ui.label(it)}
    private fun ids()=nodes().mapNotNull{it.viewIdResourceName?.takeIf{v->v.startsWith(pkg+":id/")}?.substringAfterLast('/')}.toSet()
    private fun blocked()=AutoAccessibilityService.stopRequested || !AutoAccessibilityService.isConnected() ||
        (svc.getSystemService(Context.KEYGUARD_SERVICE) as android.app.KeyguardManager).isKeyguardLocked
    private suspend fun proof(run:String,tag:String,detail:String){
        val name="startup_"+run+"_"+tag+"_"+System.currentTimeMillis()
        Evidence.write(svc,name+".txt",detail+"\nJST="+TokyoDate.time()+"\n"+ui.dump())
        ui.captureVisual(includePng=true).png?.let{Evidence.writeBytes(svc,name+".png",it)}
    }
    private fun node(id:String)=nodes().singleOrNull{it.isEnabled && it.viewIdResourceName==pkg+":id/"+id}
    private suspend fun tap(n:AccessibilityNodeInfo):Boolean{val r=Rect();n.getBoundsInScreen(r);return n.isVisibleToUser&&n.isEnabled&&r.width()>0&&r.height()>0&&ui.tap(r.centerX(),r.centerY())}
    private fun sdkSurface():Boolean {
        if(StartupPolicy.loginOffer(ui.currentPackage(),labels(),ids()) || StartupPolicy.rewardReceipt(ui.currentPackage(),labels(),ids(),false))return false
        return ui.currentPackage()==pkg && (AdSdkWindows.contains(AutoAccessibilityService.foregroundActivity) || close.isAdSurface() && !normal())
    }
    private fun normal():Boolean=ui.currentPackage()==pkg && labels().let{ls->ls.any{it in setOf("ホーム","ためる","参加可能","広告視聴")} && !AdSignals.receipt(ls) || HomeNavigationPolicy.nativeDetail(ui.currentPackage(),ls,ids())}
    suspend fun prepare(run:String, launch:Boolean=true):StartupResult {
        val date=TokyoDate.of()
        if(!AccountSafety.authorize(svc,"startup"))return StartupResult(false,"BLOCKED",0,AccountSafety.message(svc))
        if(blocked())return StartupResult(false,"BLOCKED",0,"请亮屏解锁并开启无障碍")
        if(launch){ui.launchApp(pkg);delay(1800)}
        var until=SystemClock.elapsedRealtime()+35_000L
        var closed=0;var stable=0
        while(SystemClock.elapsedRealtime()<until){
            if(blocked()||TokyoDate.of()!=date)return StartupResult(false,"BLOCKED",closed,"已停止、锁屏或日期变化")
            ui.refreshCache()
            if(!AccountSafety.authorize(svc,"startup"))return StartupResult(false,"BLOCKED",closed,AccountSafety.message(svc))
            val ls=labels();val ii=ids();val login=StartupPolicy.loginOffer(ui.currentPackage(),ls,ii)
            if(login){
                val prior=LoginBonusStore.read(svc,date)
                if(prior?.optString("state")=="REWARD_CONFIRMED"){
                    proof(run,"already_confirmed_login",prior.toString())
                    val dismiss=node("button_ok")?.takeIf{ui.label(it)=="閉じる"}?:return StartupResult(false,"BLOCKED",closed,"已确认签到窗口无法关闭")
                    if(!tap(dismiss))return StartupResult(false,"BLOCKED",closed,"签到窗口关闭失败")
                    delay(1000);stable=0;continue
                }
                if(prior!=null && !StartupPolicy.canReserveLogin(date,prior.optString("date"),prior.optString("state")))
                    return StartupResult(false,prior.optString("state"),closed,"此前签到广告未确认，未重复提交")
                val button=node("button_get_point")?:return StartupResult(false,"BLOCKED",closed,"签到领奖按钮不可用")
                val row=LoginBonusStore.reserve(svc,date,run)?:return StartupResult(false,"BLOCKED",closed,"签到记录不允许重复启动")
                proof(run,"login_before_click",row.toString())
                if(!tap(button)){row.put("state","FAILED").put("reason","login_button_click_failed");LoginBonusStore.save(svc,date,row);return StartupResult(false,"FAILED",closed,"签到广告未能打开")}
                val watched=observeAd(run,date,row)
                if(!watched)return StartupResult(false,row.optString("state"),closed,row.optString("reason"))
                stable=0;until=SystemClock.elapsedRealtime()+15_000L;continue
            }
            if(sdkSurface() || AdSignals.playSheet(ui.currentPackage(),ls)){
                proof(run,"startup_ad_observed","unrequested_startup_ad;not_a_daily_viewing;no_reward_inferred")
                if(!dismissAd(run,date))return StartupResult(false,"BLOCKED",closed,"启动广告无法安全关闭")
                closed++;stable=0;until=SystemClock.elapsedRealtime()+15_000L;if(closed>=6)return StartupResult(false,"BLOCKED",closed,"启动广告超过有界关闭次数");continue
            }
            if(StartupPolicy.rewardReceipt(ui.currentPackage(),ls,ii,sdkSurface())){
                proof(run,"preexisting_receipt","not_attributed_to_new_login_or_daily_viewing")
                if(!tap(node("button_ok")?:return StartupResult(false,"BLOCKED",closed,"既有回执无法关闭")))return StartupResult(false,"BLOCKED",closed,"回执关闭失败")
                delay(900);stable=0;continue
            }
            if(StartupPolicy.nativePopup(ui.currentPackage(),ii,login,false,false)){
                val x=node("image_close")
                if(x!=null){val r=Rect();x.getBoundsInScreen(r);val d=svc.resources.displayMetrics.density
                    if(r.width()/d in 12f..64f && r.height()/d in 12f..64f){proof(run,"native_popup_before_close","observed_native_image_close");if(!tap(x))return StartupResult(false,"BLOCKED",closed,"宣传弹窗关闭失败");closed++;stable=0;delay(800);if(closed>=6)return StartupResult(false,"BLOCKED",closed,"启动弹窗超过有界关闭次数");continue}}
            }
            if(ui.currentPackage()==pkg && AdSignals.error(ls)){proof(run,"startup_error","official_error");return StartupResult(false,"BLOCKED",closed,"ANA Pocket显示网络或广告错误")}
            if(normal()){stable++;if(stable>=6){
                val old=LoginBonusStore.read(svc,date)
                if(old==null||old.optString("state")=="NOT_PRESENT")LoginBonusStore.save(svc,date,JSONObject().put("date",date).put("runId",run).put("state","NOT_PRESENT").put("checkedAt",System.currentTimeMillis()).put("completed",false).put("rewardConfirmed",false))
                return StartupResult(true,LoginBonusStore.read(svc,date)?.optString("state")?:"NOT_PRESENT",closed)
            }}else stable=0
            delay(650)
        }
        proof(run,"startup_blocked","unknown_or_unstable_foreground")
        FaultStore.capture(svc,run,"startup","unknown_or_unstable_foreground")
        return StartupResult(false,"BLOCKED",closed,"启动页面未稳定，已保存证据")
    }
    private suspend fun session(run:String,date:String,row:JSONObject?):StartupAdOutcome {
        close.beginFlow();var closeNode:AccessibilityNodeInfo?=null
        val port=object:StartupAdPort{
            override fun now()=SystemClock.elapsedRealtime()
            override fun stopped()=blocked()
            override fun sameDate()=TokyoDate.of()==date
            override suspend fun frame():StartupAdFrame{
                if(!AccountSafety.authorize(svc,"startup_ad"))return StartupAdFrame(manualRequired=true)
                close.restoreAdAfterStore();ui.refreshCache()
                val ls=labels();val ii=ids();val ad=sdkSurface()
                val login=StartupPolicy.loginOffer(ui.currentPackage(),ls,ii)
                val receipt=StartupPolicy.rewardReceipt(ui.currentPackage(),ls,ii,ad)
                val popup=StartupPolicy.nativePopup(ui.currentPackage(),ii,login,receipt,AdSdkWindows.contains(AutoAccessibilityService.foregroundActivity))
                closeNode=if(login||receipt||popup)null else close.identifyClose()
                if(closeNode==null&&!login&&!receipt&&!popup&&close.revealIfNeeded())closeNode=close.identifyClose()
                return StartupAdFrame(ad&&!popup,receipt,normal(),login,closeNode!=null,close.observedStoreOverlay,AdSignals.part(ls),ui.currentPackage()==pkg && AdSignals.error(ls),popup)
            }
            override suspend fun dismissPopup():Boolean{
                val x=node("image_close")?:return false;val r=Rect();x.getBoundsInScreen(r);val d=svc.resources.displayMetrics.density
                if(r.width()/d !in 12f..64f||r.height()/d !in 12f..64f)return false
                proof(run,"intermediate_native_popup_before_close","native_image_close_and_content;not_completion_or_reward")
                val ok=tap(x);delay(650);return ok
            }
            override suspend fun diagnostic(reason:String){FaultStore.capture(svc,run,"startup",reason,row?.optString("startedAt").orEmpty())}
            override suspend fun observedAd(){
                if(row!=null){row.put("adDepartureObserved",true).put("state","AD_PLAYING");LoginBonusStore.save(svc,date,row)}
                proof(run,if(row==null)"startup_ad_playing" else "login_ad_playing",row?.toString()?:"navigation_blocker_only")
            }
            override suspend fun close(){
                val n=closeNode?:return
                val b=close.controlBounds(n)?:return
                proof(run,if(row==null)"startup_sdk_close" else "login_ad_close_available","actual_observed_close;not_completion")
                ui.tap(b.centerX(),b.centerY())
            }
        }
        return StartupAdSequence(port).run(row!=null)
    }
    private suspend fun observeAd(run:String,date:String,row:JSONObject):Boolean{
        try{
            val result=session(run,date,row)
            if(result.state=="REWARD_CONFIRMED"){
                row.put("state","REWARD_CONFIRMED").put("completed",true).put("rewardConfirmed",true).put("finishedAt",System.currentTimeMillis())
                    .put("evidence","fresh_foreground_ANA_reward_receipt_after_reserved_login_ad")
                LoginBonusStore.save(svc,date,row);proof(run,"login_reward_confirmed",row.toString())
                node("button_ok")?.let{tap(it)};delay(1000);return true
            }
            row.put("reason",result.reason)
            return false
        }finally{
            if(row.optString("state")!="REWARD_CONFIRMED"){
                row.put("state","UNCONFIRMED").put("finishedAt",System.currentTimeMillis())
                if(!row.has("reason"))row.put("reason","login_reward_not_confirmed")
                LoginBonusStore.save(svc,date,row);proof(run,"login_unconfirmed",row.toString())
                FaultStore.capture(svc,run,"login",row.optString("reason"))
            }
        }
    }
    private suspend fun dismissAd(run:String,date:String):Boolean{
        val result=session(run,date,null)
        proof(run,if(result.state=="DISMISSED")"startup_ad_dismissed" else "startup_ad_timeout","state="+result.state+";no_reward_claim")
        return result.state=="DISMISSED"
    }
}
