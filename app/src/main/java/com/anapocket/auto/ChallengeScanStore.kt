package com.anapocket.auto

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Separate, disposable scan projection. No submission/reward/settings store is written here. */
object ChallengeScanStore {
    private fun sp(c:Context)=c.getSharedPreferences("ana_challenge_scans_v1",0)
    fun read(c:Context,date:String,cat:Cat):JSONObject?=sp(c).getString(date+":"+cat.name,null)?.let{JSONObject(it)}
    fun status(c:Context,date:String,cat:Cat,km:Int):ChallengeScanStatus {
        val r=read(c,date,cat)?:return ChallengeScanStatus.NOT_SCANNED
        if(r.optInt("maxKm")!=km)return ChallengeScanStatus.NOT_SCANNED
        val state=runCatching{ChallengeScanStatus.valueOf(r.getString("state"))}.getOrDefault(ChallengeScanStatus.SCAN_FAILED)
        return if(state==ChallengeScanStatus.SCANNING&&!AutoAccessibilityService.isRunning)ChallengeScanStatus.SCAN_FAILED else state
    }
    fun complete(c:Context,date:String,cat:Cat,km:Int):Boolean {
        val r=read(c,date,cat)?:return false
        val rows=ChallengeStore.rows(c,date)
        val pending=(0 until rows.length()).map{rows.getJSONObject(it)}.any{it.optString("cat")==cat.name&&it.optString("state") !in setOf("BOOST_MAX_CONFIRMED","BOOST_MAX_OBSERVED")}
        return ChallengeScanPolicy.reusable(status(c,date,cat,km),r.optInt("maxKm"),km,r.optBoolean("bothLists"),pending)
    }
    @Synchronized fun record(c:Context,date:String,cat:Cat,km:Int,run:String,state:ChallengeScanStatus,both:Boolean=false,eligible:Int=0,reason:String="") {
        val row=JSONObject().put("date",date).put("cat",cat.name).put("maxKm",km).put("runId",run).put("state",state.name)
            .put("bothLists",both).put("eligible",eligible).put("reason",reason).put("at",System.currentTimeMillis())
        val a=JSONArray(sp(c).getString("history_"+date,"[]"));a.put(row)
        check(sp(c).edit().putString(date+":"+cat.name,row.toString()).putString("history_"+date,a.toString()).commit()){"scan_state_write_failed"}
        Evidence.write(c,"scan_"+run+"_"+cat.name+"_"+row.getLong("at")+".json",row.toString(2))
        Logger.i("CHSCAN",row.toString())
    }
    fun audit(c:Context,date:String)=JSONArray(sp(c).getString("history_"+date,"[]"))
    @Synchronized fun reset(c:Context,date:String,run:String) {
        val st=Prefs.loadBlocking(c)
        for(cat in TravelSelectionPolicy.queue(TravelSelectionPolicy.categoryOrder,st)){
            record(c,date,cat,st.kmOf(cat),run,ChallengeScanStatus.NOT_SCANNED,reason="manual_scan_projection_reset_history_retained")
            ChallengeStore.setCoverage(c,date,cat,0)
        }
    }
    fun resetBlock(c:Context):String? {
        if(AutoAccessibilityService.isRunning||CheckinRuntime.activeApp!=null)return "请等待当前任务停止后再扫描"
        val date=TokyoDate.of()
        if(TravelSelection.hasUnresolvedAd(c,date)||OfficialAdStore.attempts(c,date).any{!it.terminal||it.state==AdState.UNCONFIRMED})return "当前仍有未确认的广告提交，请先核对原观看"
        val sdk=AutoAccessibilityService.instance?.let{AdUiClose(it).isRecoverableSurface()}==true
        if(sdk)return "当前仍在广告窗口，不能重置扫描"
        if(AccountSafetyStore.pending(c)!=null)return "请先处理账号保护"
        return null
    }
}
