package com.anapocket.auto

import android.content.Context
import org.json.JSONObject

data class SubmittedUnknown(val date:String,val key:String,val run:String,val startedAt:Long,val index:Int,val count:Int,
    val confirmed:Boolean,val departed:Boolean,val phase:String,val pendingPerTask:Int)
object SubmittedUnknownIsolationPolicy {
    const val KIND="today_native_participating_after_dispatched_unknown"
    private fun pending(a:SubmittedUnknown,today:String)=a.date==today&&a.key.isNotBlank()&&a.run.startsWith("CH")&&
        a.startedAt>0&&a.index==a.count&&a.index>0&&!a.confirmed&&a.departed&&a.phase=="DISPATCHED"&&a.pendingPerTask==1
    fun mayIsolate(a:SubmittedUnknown,today:String,uniqueOfficialToday:Boolean,cleanNative:Boolean,stableMs:Long,
        dailyPending:Boolean):Boolean = pending(a,today)&&uniqueOfficialToday&&cleanNative&&stableMs>=15_000&&!dailyPending
    const val KIND_ABSENT="today_native_absent_after_timed_out_initial_unknown"
    /**
     * A first-join ad that timed out and is missing from a complete read-only scan of ANA's participating list. It is set
     * aside so the rest of the day can run. The recorded attempt still blocks any re-join, so nothing is replayed.
     */
    fun mayIsolateAbsent(a:SubmittedUnknown,today:String,initial:Boolean,reason:String,cleanNative:Boolean,stableMs:Long,dailyPending:Boolean):Boolean =
        initial&&ChallengeRecoveryPolicy.reviewableFailure(reason,true)&&mayIsolate(a,today,true,cleanNative,stableMs,dailyPending)
    /** Reuse native navigation classifiers; a Boost modal still owns the screen. */
    fun cleanNativePage(pkg:String?,activity:String?,ids:Set<String>,labels:List<String>,adSurface:Boolean):Boolean =
        pkg=="us.ana_pocket.ANAPocket"&&activity=="us.ana_pocket.ANAPocket.views.home.HomeActivity"&&!adSurface&&
            !ChallengeSignals.dialog(labels)&&ids.none{it.substringAfterLast('/') in setOf("title_join","title_count","button_see_ad") }&&
            (labels.any{it in setOf("チャレンジ","参加中","進行中","参加可能")}||
                HomeNavigationPolicy.nativeHome(pkg,labels)||
                HomeNavigationPolicy.nativeDetail(pkg,labels,ids.map{it.substringAfterLast('/')}.toSet()))
    /** Identity fence inside the existing FULL transaction, not permission to navigate or start an ad. */
    fun commitOnce(expected:SubmittedUnknown,current:SubmittedUnknown?,today:String,alreadyIsolated:Boolean,persist:()->Unit):Boolean {
        if(current!=expected||!pending(expected,today))return false
        if(!alreadyIsolated)persist()
        return true
    }
    fun bound(a:SubmittedUnknown,today:String,key:String,run:String,start:Long,index:Int,count:Int,kind:String):Boolean =
        a.date==today&&a.key==key&&a.run==run&&a.startedAt==start&&a.index==index&&a.count==count&&
            !a.confirmed&&a.departed&&a.phase=="DISPATCHED"&&a.pendingPerTask==1&&kind in setOf(KIND,KIND_ABSENT)
}
object SubmittedUnknownIsolation {
    fun binding(row:JSONObject):SubmittedUnknown? {
        val ads=row.optJSONArray("ads")?:return null;val a=ads.optJSONObject(ads.length()-1)?:return null
        val unknown=(0 until ads.length()).count{val x=ads.getJSONObject(it);!x.optBoolean("confirmed")&&!(x.optString("state")=="FAILED"&&x.optBoolean("officialNoGain"))&&!InitialRejoinPolicy.resolved(x)}
        return SubmittedUnknown(row.optString("date"),row.optString("key"),a.optString("runId"),a.optLong("startedAt"),a.optInt("index"),
            ads.length(),a.optBoolean("confirmed"),a.optBoolean("adDepartureObserved"),a.optString("dispatchPhase"),unknown)
    }
    fun isolated(row:JSONObject,date:String):Boolean {
        val a=binding(row)?:return false;val p=row.optJSONObject("submittedUnknownIsolation")?:return false
        return p.optString("date")==date&&p.optLong("observedAt")>=a.startedAt&&p.optString("officialKey")==a.key&&
            p.optString("receiptState")=="UNKNOWN"&&SubmittedUnknownIsolationPolicy.bound(a,date,p.optString("key"),p.optString("runId"),
                p.optLong("startedAt"),p.optInt("index"),p.optInt("count"),p.optString("kind"))
    }
    fun save(c:Context,date:String,row:JSONObject,kind:String=SubmittedUnknownIsolationPolicy.KIND,stillSafe:()->Boolean):Boolean {
        val expected=binding(row)?:return false
        val saved=ReliableLedger.updateChallenge(c,date,expected.key){current->
            if(!stillSafe())return@updateChallenge null
            val accepted=SubmittedUnknownIsolationPolicy.commitOnce(expected,binding(current),date,isolated(current,date)){
                check(stillSafe()){ "isolation_page_or_daily_pending_changed" }
                current.put("submittedUnknownIsolation",JSONObject().put("date",date).put("key",expected.key).put("runId",expected.run)
                    .put("startedAt",expected.startedAt).put("index",expected.index).put("count",expected.count).put("observedAt",System.currentTimeMillis())
                    .put("officialKey",expected.key).put("kind",kind).put("receiptState","UNKNOWN"))
            }
            if(accepted)current else null
        }?:return false
        saved.keys().forEach{key->row.put(key,saved.get(key))}
        return isolated(saved,date)
    }
}
