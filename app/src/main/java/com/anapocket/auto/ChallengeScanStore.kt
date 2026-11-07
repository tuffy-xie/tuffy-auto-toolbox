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
    fun sourceComplete(row:JSONObject?):Boolean = row!=null&&
        (if(row.has("scanBasis"))row.optString("scanBasis")=="JOINABLE" else row.optBoolean("bothLists"))&&
        row.optString("state") in setOf("COMPLETED","NO_ELIGIBLE_TASK","PARTIALLY_COMPLETED")
    fun complete(c:Context,date:String,cat:Cat,km:Int):Boolean {
        val r=read(c,date,cat)?:return false
        val rows=ChallengeStore.rows(c,date)
        val pending=(0 until rows.length()).map{rows.getJSONObject(it)}.any{it.optString("cat")==cat.name&&it.optString("state") !in setOf("BOOST_MAX_CONFIRMED","BOOST_MAX_OBSERVED")}
        return ChallengeScanPolicy.reusable(status(c,date,cat,km),r.optInt("maxKm"),km,sourceComplete(r),pending)
    }
    fun confirmedRows(rows:JSONArray,date:String,cat:Cat,maxMeters:Int):Boolean {
        for(i in 0 until rows.length()){
            val row=rows.getJSONObject(i)
            if(row.optString("cat")!=cat.name)continue
            if(row.optString("date")!=date||row.optInt("meters")<=0)return false
            if(row.optInt("meters")>maxMeters)continue
            if(!row.optBoolean("joined")||row.optInt("boost")!=5||row.optString("state")!="BOOST_MAX_CONFIRMED")return false
            val ads=row.optJSONArray("ads")?:return false
            var confirmed=0
            for(j in 0 until ads.length()){
                val ad=ads.getJSONObject(j)
                if(ad.optBoolean("confirmed"))confirmed++ else if(!ad.optBoolean("officialNoGain"))return false
            }
            if(confirmed!=row.optInt("boost")+1)return false
        }
        return true
    }

    /** Reuse only a complete matching scan. An isolated UNKNOWN stays PARTIALLY_COMPLETED. */
    fun resumeState(c:Context,date:String,cat:Cat,km:Int):String? {
        val scan=read(c,date,cat)?:return null
        if(scan.optString("date")!=date||scan.optString("cat")!=cat.name||scan.optInt("maxKm")!=km||!sourceComplete(scan))return null
        if(scan.optString("state")=="PARTIALLY_COMPLETED"&&scan.optString("reason").isNotBlank())return null
        val rows=ChallengeStore.rows(c,date);val active=JSONArray();var isolated=false
        for(i in 0 until rows.length()){
            val row=rows.getJSONObject(i)
            if(row.optString("cat")!=cat.name||row.optInt("meters")>km*1000)continue
            if(row.optString("date")==date&&row.optInt("meters")>0&&PendingInitialJoinStore.isolated(row,date))isolated=true else active.put(row)
        }
        if(!confirmedRows(active,date,cat,km*1000))return null
        return if(isolated)"PARTIALLY_COMPLETED" else if(complete(c,date,cat,km))"ALREADY_DONE" else null
    }

    @Synchronized fun record(c:Context,date:String,cat:Cat,km:Int,run:String,state:ChallengeScanStatus,both:Boolean=false,eligible:Int=0,reason:String="",basis:String="") {
        val row=JSONObject().put("date",date).put("cat",cat.name).put("maxKm",km).put("runId",run).put("state",state.name)
            .put("eligible",eligible).put("reason",reason).put("at",System.currentTimeMillis())
        if(basis.isBlank())row.put("bothLists",both) else row.put("scanBasis",basis)
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
    fun pendingReviewAllowed(c:Context):Boolean = ChallengeScanEntryPolicy.preservingReview(
        TravelSelection.hasUnresolvedAd(c,TokyoDate.of()),
        AutoAccessibilityService.isRunning||CheckinRuntime.activeApp!=null,
        OfficialAdStore.attempts(c,TokyoDate.of()).any{!it.terminal||it.state==AdState.UNCONFIRMED},
        AutoAccessibilityService.instance?.let{AdUiClose(it).isRecoverableSurface()}!=false,
        AccountSafetyStore.pending(c)!=null)
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
