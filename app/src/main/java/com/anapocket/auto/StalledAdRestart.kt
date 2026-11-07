package com.anapocket.auto

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.net.Uri
import android.os.SystemClock
import android.provider.Settings
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject

/** Normal, observed HyperOS app-info controls. No shell, clearing data, or ledger rewriting. */
object StalledAdRestart {
    private const val PKG=StartupPolicy.ANA
    private data class Bound(val key:String,val date:String,val stage:String,val state:String,val reason:String,val startedAt:Long,val confirmed:Boolean)
    private fun prefs(c:Context)=c.getSharedPreferences("ana_stalled_restart_v1",Context.MODE_PRIVATE)
    private fun bound(c:Context,date:String,stage:String):Bound? = when(stage){
        "HOME" -> Bound("HOME|"+date+"|"+AutoAccessibilityService.currentRunId,date,stage,"NAVIGATION_STALLED","home_navigation_stalled",System.currentTimeMillis(),false)
        "STARTUP" -> LoginBonusStore.read(c,date)?.takeIf{it.optBoolean("adDepartureObserved")}?.let{
            Bound("LOGIN|"+date+"|"+it.optString("runId")+"|"+it.optLong("startedAt"),it.optString("date"),stage,it.optString("state"),it.optString("reason"),it.optLong("startedAt"),it.optBoolean("rewardConfirmed")||it.optBoolean("completed"))
        }
        "ADS" -> OfficialAdStore.attempts(c,date).lastOrNull()?.takeIf{it.terminal}?.let{
            Bound("DAILY|"+date+"|"+it.runId+"|"+it.index+"|"+it.startedAt,it.date,stage,it.state.name,it.failureReason,it.startedAt,it.rewardConfirmed||it.completed)
        }
        "MAX_BOOST" -> MaxBoostCompletionStore.pending(c,date)?.takeIf{MaxBoostCompletionStore.valid(c,it)}?.let{v->
            Bound("MAX_BOOST|"+date+"|"+v.key+"|"+v.adRun+"|"+v.adIndex+"|"+v.adStarted,date,stage,"BOOST_MAX_CONFIRMED",
                MaxBoostCompletionStore.read(c,v)?.optString("failure").orEmpty(),v.adStarted,true)
        }
        "CHALLENGES" -> {
            val a=ChallengeStore.rows(c,date)
            val pending=(0 until a.length()).map{a.getJSONObject(it)}.filter{it.optString("state")=="UNCONFIRMED"}
            pending.singleOrNull()?.let{row->val ads=row.optJSONArray("ads");val ad=ads?.takeIf{it.length()>0}?.optJSONObject(ads.length()-1)
                ad?.let{Bound("CHALLENGE|"+date+"|"+row.optString("key")+"|"+it.optString("runId")+"|"+it.optInt("index")+"|"+it.optLong("startedAt"),row.optString("date"),stage,it.optString("state"),it.optString("reason"),it.optLong("startedAt"),it.optBoolean("confirmed"))}
            }
        }
        else -> null
    }
    @Synchronized private fun reserve(c:Context,date:String,b:Bound,run:String):Boolean {
        val p=prefs(c)
        val marker=JSONObject().put("date",date).put("identity",b.key).put("controllerRun",run).put("requestedAt",System.currentTimeMillis()).put("state","REQUESTED").put("reason",b.reason).put("noRewardInferred",true)
        return StalledAdRestartPolicy.reserve(p.contains(b.key),p.getInt("count_"+date,0)){
            p.edit().putString(b.key,marker.toString()).putInt("count_"+date,p.getInt("count_"+date,0)+1).commit()
        }
    }
    /** A previous claim survives process/update interruptions; it never grants another stop attempt. */
    private fun mark(c:Context,b:Bound,state:String){val p=prefs(c);val o=JSONObject(p.getString(b.key,"{}")!!).put("state",state).put("updatedAt",System.currentTimeMillis());check(p.edit().putString(b.key,o.toString()).commit())}
    fun mayRelaunchLogin(c:Context,date:String):Boolean {
        val b=bound(c,date,"STARTUP")?:return false
        val marker=prefs(c).getString(b.key,null)?.let{JSONObject(it)}?:return false
        return StalledAdRestartPolicy.mayRelaunch(date,b.date,b.state,marker.optString("state"))
    }
    suspend fun recover(svc:AutoAccessibilityService,date:String,run:String,stage:String):Boolean {
        val b=bound(svc,date,stage)?:return false
        // A timeout is not evidence that an SDK viewing or irreversible submission has ended.
        if(AdSdkWindows.contains(AutoAccessibilityService.foregroundActivity)||TravelSelection.hasUnresolvedAd(svc,date)||
            OfficialAdStore.attempts(svc,date).any{!it.terminal||it.state==AdState.UNCONFIRMED}){
            Logger.w("ADRESTART","run=$run restart blocked: unresolved advertisement or SDK; original identity preserved")
            return false
        }
        val ui=Ui(svc);val owner=CheckinRuntime.ownership.owner()
        fun live()=!AutoAccessibilityService.stopRequested&&AutoAccessibilityService.isConnected()&&AutoAccessibilityService.currentRunId==run&&TokyoDate.of()==date&&
            owner!=null&&owner.app=="ANA"&&owner.run==run&&CheckinRuntime.ownership.valid(owner,SystemClock.elapsedRealtime())&&!(svc.getSystemService(Context.KEYGUARD_SERVICE) as android.app.KeyguardManager).isKeyguardLocked&&AccountSafety.authorize(svc,"stalled_ad_restart")
        ui.refreshCache()
        val homeNodes=if(stage=="HOME")ui.foregroundNodes().filter{it.isVisibleToUser} else emptyList()
        val pureHome=stage=="HOME"&&HomeNavigationPolicy.nativeHome(ui.currentPackage(),homeNodes.map{ui.label(it)})&&
            AutoAccessibilityService.foregroundActivity==MaxBoostDialogPolicy.ACTIVITY&&
            !AdUiClose(svc).isAdSurface()&&homeNodes.none{it.viewIdResourceName in setOf("android:id/message","android:id/button1",PKG+":id/design_bottom_sheet",PKG+":id/title_count")}
        val allowed=if(stage=="HOME")HomeNavigationPolicy.restartAllowed(pureHome,
            TravelSelection.hasUnresolvedAd(svc,date)||OfficialAdStore.attempts(svc,date).any{!it.terminal||it.state==AdState.UNCONFIRMED}||
                LoginBonusStore.read(svc,date)?.optString("state") in setOf("AD_OPENING","AD_PLAYING"),
            AdSdkWindows.contains(AutoAccessibilityService.foregroundActivity),date==b.date,prefs(svc).contains(b.key),prefs(svc).getInt("count_"+date,0),live())
        else if(stage=="MAX_BOOST")MaxBoostDialogPolicy.completedRestartAllowed(date==b.date,b.confirmed&&b.reason.startsWith("MAX_BOOST_"),
            TravelSelection.hasUnresolvedAd(svc,date)||OfficialAdStore.attempts(svc,date).any{!it.terminal||it.state==AdState.UNCONFIRMED},
            AdSdkWindows.contains(AutoAccessibilityService.foregroundActivity),prefs(svc).contains(b.key),prefs(svc).getInt("count_"+date,0),live()&&ui.currentPackage()==PKG&&MaxBoostCoordinator.visible(svc))
        else StalledAdRestartPolicy.eligible(date,b.date,b.stage,b.state,b.reason,b.startedAt,System.currentTimeMillis(),b.confirmed,prefs(svc).contains(b.key),prefs(svc).getInt("count_"+date,0),live(),ui.currentPackage()==PKG&&AdSdkWindows.contains(AutoAccessibilityService.foregroundActivity))
        if(!allowed)return false
        // The initial navigation stage may not interrupt a separately reserved challenge or daily viewing.
        if(stage=="STARTUP"&&(TravelSelection.hasUnresolvedAd(svc,date)||OfficialAdStore.attempts(svc,date).any{!it.terminal||it.state==AdState.UNCONFIRMED}))return false
        fun act(block:()->Unit):Boolean = live()&&owner!=null&&CheckinRuntime.ownership.action(owner,SystemClock.elapsedRealtime(),false){if(live())block()}
        fun nodes()=ui.foregroundNodes().filter{it.isVisibleToUser}
        fun labels()=nodes().map{ui.label(it)}
        fun exact(text:String)=nodes().filter{ui.label(it)==text}.singleOrNull()
        fun usable(n:AccessibilityNodeInfo?):Boolean {var p=n;repeat(8){val node=p?:return false;if(!node.isEnabled)return false;if(node.isClickable)return true;p=node.parent};return false}
        suspend fun proof(tag:String){Evidence.write(svc,"stalled_restart_"+run+"_"+tag+".txt","identity="+b.key+";stage="+stage+";JST="+TokyoDate.time()+";no_new_viewing;no_reward_inferred\n"+ui.dump());Evidence.capture(svc,ui,"stalled_restart_"+run+"_"+tag+".png")}
        FaultStore.capture(svc,run,"stalled_restart","user_authorized_timeout_restart",b.startedAt.toString())
        if(!live()||!reserve(svc,date,b,run))return false
        Logger.w("ADRESTART","run="+run+" stage="+stage+" bound="+b.key+" one system restart requested; unknown preserved")
        val ok=withTimeoutOrNull(55_000L){
            if(!act{svc.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+PKG)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))})return@withTimeoutOrNull false
            var info=false
            for(i in 0 until 25){if(!live())return@withTimeoutOrNull false;delay(300);ui.refreshCache();if(StalledAdRestartPolicy.appInfo(ui.currentPackage().orEmpty(),AutoAccessibilityService.foregroundActivity,labels())){info=true;break}}
            if(!info||!live()||!StalledAdRestartPolicy.appInfo(ui.currentPackage().orEmpty(),AutoAccessibilityService.foregroundActivity,labels()))return@withTimeoutOrNull false
            val stop=exact("结束运行")?:return@withTimeoutOrNull false
            if(!usable(stop))return@withTimeoutOrNull false
            proof("observed_ana_app_info")
            var clicked=false;if(!act{clicked=ui.clickSemantic(stop)}||!clicked)return@withTimeoutOrNull false
            var dialog=false
            for(i in 0 until 15){if(!live())return@withTimeoutOrNull false;delay(300);ui.refreshCache();if(StalledAdRestartPolicy.confirmation(ui.currentPackage().orEmpty(),labels(),info)){dialog=true;break}}
            if(!dialog||!live()||!StalledAdRestartPolicy.confirmation(ui.currentPackage().orEmpty(),labels(),info))return@withTimeoutOrNull false
            val confirm=exact("确定")?:return@withTimeoutOrNull false
            if(!usable(confirm))return@withTimeoutOrNull false
            proof("observed_stop_confirmation")
            clicked=false;if(!act{clicked=ui.clickSemantic(confirm)}||!clicked)return@withTimeoutOrNull false
            delay(1400);ui.refreshCache()
            val stopped=runCatching{svc.packageManager.getApplicationInfo(PKG,0).flags and ApplicationInfo.FLAG_STOPPED!=0}.getOrDefault(false)
            val disabled=ui.currentPackage()=="com.miui.securitycenter"&&exact("结束运行")?.let{!usable(it)}==true
            if(!live()||(!stopped&&!disabled))return@withTimeoutOrNull false
            mark(svc,b,"SYSTEM_STOP_CONFIRMED");proof("system_stop_confirmed")
            if(!act{ui.home()})return@withTimeoutOrNull false
            delay(650)
            var launched=false;if(!act{launched=ui.launchApp(PKG)}||!launched)return@withTimeoutOrNull false
            var foreground=false
            for(i in 0 until 50){if(!live())return@withTimeoutOrNull false;delay(400);ui.refreshCache();if(ui.currentPackage()==PKG){foreground=true;break}}
            if(!foreground||!live())return@withTimeoutOrNull false
            proof("normal_relaunch_observed");true
        }?:false
        mark(svc,b,if(ok)"RELAUNCHED_REVIEW_REQUIRED" else "BLOCKED_NO_REPEAT")
        Logger.w("ADRESTART","run="+run+" success="+ok+"; ledger unchanged; official review still required")
        return ok
    }
}
