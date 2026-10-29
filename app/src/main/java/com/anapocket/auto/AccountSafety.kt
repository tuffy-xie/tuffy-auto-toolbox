package com.anapocket.auto

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/** This latch is deliberately independent of day counters and survives reboot/date rollover. */
object AccountSafetyStore {
    private fun sp(c:Context)=c.getSharedPreferences("ana_account_safety_v1",Context.MODE_PRIVATE)
    @Synchronized fun pending(c:Context):JSONObject? = try{sp(c).getString("pending",null)?.let{JSONObject(it)}}catch(e:Exception){JSONObject().put("id","storage_error").put("kind",RiskKind.STORAGE_ERROR.name).put("title",RiskKind.STORAGE_ERROR.title)}
    @Synchronized fun latch(c:Context,kind:RiskKind,run:String,module:String):Pair<JSONObject,Boolean>{
        pending(c)?.let{return it to false}
        val row=JSONObject().put("id",java.util.UUID.randomUUID().toString()).put("kind",kind.name).put("title",kind.title)
            .put("runId",run).put("module",module).put("deviceTime",TokyoDate.time()).put("at",System.currentTimeMillis()).put("state","HUMAN_REQUIRED")
        check(sp(c).edit().putString("pending",row.toString()).commit()){"safety_latch_write_failed"};return row to true
    }
    @Synchronized fun acknowledge(c:Context,id:String,cleanNative:Boolean,detected:RiskKind?):Boolean{
        val row=pending(c)?:return false
        if(!AccountRiskPolicy.mayAcknowledge(id,row.optString("id"),cleanNative,detected)||id=="storage_error")return false
        val history=JSONArray(sp(c).getString("history","[]"));history.put(row.put("reviewedAt",System.currentTimeMillis()).put("state","MANUALLY_REVIEWED"))
        val keep=JSONArray();for(i in (history.length()-30).coerceAtLeast(0) until history.length())keep.put(history.getJSONObject(i))
        check(sp(c).edit().remove("pending").putString("history",keep.toString()).commit()){"safety_ack_write_failed"};return true
    }
}

object AccountSafety {
    private val evidenceScope=CoroutineScope(SupervisorJob()+Dispatchers.Default)
    fun frame(svc:AutoAccessibilityService):RiskFrame {
        val ui=Ui(svc);val nodes=ui.foregroundNodes().filter{it.isVisibleToUser}
        return RiskFrame(ui.currentPackage(),AdSdkWindows.contains(AutoAccessibilityService.foregroundActivity),nodes.map{ui.label(it)},nodes.mapNotNull{it.viewIdResourceName}.toSet(),nodes.any{it.isEditable})
    }
    /** Called again at gesture/click boundaries, so an event race cannot authorize the next tap. */
    fun authorize(svc:AutoAccessibilityService,module:String="ui"):Boolean {
        if(AccountSafetyStore.pending(svc)!=null)return false
        val kind=runCatching{AccountRiskPolicy.detect(frame(svc))}.getOrNull()?:return true
        val (row,created)=AccountSafetyStore.latch(svc,kind,AutoAccessibilityService.currentRunId,module)
        svc.requestStop("account_safety_"+kind.name)
        if(created){
            Logger.w("SAFETY","incident="+row.optString("id")+" kind="+kind.name+" run="+row.optString("runId"))
            Notifier.safetyPause(svc,row)
            evidenceScope.launch{FaultStore.capture(svc,row.optString("runId"),"account_safety",kind.name,row.optString("id"))}
        }
        return false
    }
    fun message(c:Context)=AccountSafetyStore.pending(c)?.let{"任务已暂停："+it.optString("title")+"；请手动处理后回Tuffy复核"}.orEmpty()
    fun cleanNative(svc:AutoAccessibilityService,f:RiskFrame):Boolean {
        if(f.pkg!=StartupPolicy.ANA||f.sdk||AccountRiskPolicy.detect(f)!=null||AdSignals.receipt(f.labels)||AdSignals.error(f.labels))return false
        return HomeNavigationPolicy.nativeHome(f.pkg,f.labels)||HomeNavigationPolicy.nativeDetail(f.pkg,f.labels,f.ids.map{it.substringAfterLast('/')}.toSet())||
            f.labels.contains("参加可能")&&f.labels.contains("参加中")||
            f.labels.contains("広告視聴")&&AdSignals.watched(f.labels)!=null
    }
    suspend fun review(svc:AutoAccessibilityService,run:String):Boolean {
        val incident=AccountSafetyStore.pending(svc)?:return true
        if(!DeviceReady.ensure(svc,run,forReview=true).ready)return false
        val ui=Ui(svc);if(!ui.launchApp(StartupPolicy.ANA))return false
        var stable=0
        repeat(20){
            if(AutoAccessibilityService.stopRequested)return false
            delay(600);ui.refreshCache();val f=frame(svc)
            if(AccountRiskPolicy.detect(f)!=null)return false
            if(cleanNative(svc,f))stable++ else stable=0
            if(stable>=4){
                val ok=AccountSafetyStore.acknowledge(svc,incident.optString("id"),true,null)
                Evidence.write(svc,"safety_review_"+run+".json",JSONObject().put("incidentId",incident.optString("id")).put("cleared",ok).put("nativeStableFrames",stable).put("noTasksAutoStarted",true).toString(2))
                return ok
            }
        }
        return false
    }
}
