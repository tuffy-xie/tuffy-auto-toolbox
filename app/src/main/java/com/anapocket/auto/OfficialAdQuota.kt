package com.anapocket.auto

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** R43 user-approved rule: official account quota is the target. Old attempts are immutable history. */
object OfficialAdQuota {
    fun canReserve(counter:Int?, previousCounter:Int?, sameDate:Boolean, stopped:Boolean, rows:List<AdAttempt>):Boolean =
        sameDate && !stopped && counter!=null && counter in 0 until DAILY_AD_LIMIT &&
        (previousCounter==null || counter>=previousCounter) && rows.size<DAILY_AD_LIMIT &&
        rows.all{it.terminal && it.completed && it.rewardConfirmed && it.state==AdState.REWARD_CONFIRMED}
    fun isComplete(counter:Int?)=counter==DAILY_AD_LIMIT
}

/** Separate journal: do not rewrite or recycle R32-R42 attempt identities to hide unsuccessful starts. */
object OfficialAdStore {
    private fun sp(c:Context)=c.getSharedPreferences("ana_official_ads_v1",Context.MODE_PRIVATE)
    @Synchronized fun attempts(c:Context,date:String):List<AdAttempt>{
        val a=JSONArray(sp(c).getString("day_"+date,"[]"));val list=(0 until a.length()).map{AdAttempt.fromJson(a.getJSONObject(it))}
        check(list.size<=DAILY_AD_LIMIT && list.map{it.index}.distinct().size==list.size && list.all{it.date==date && it.runId.isNotBlank() && it.index>0}){"official_journal_corrupt"}
        return list
    }
    private fun save(c:Context,date:String,rows:List<AdAttempt>){val a=JSONArray();rows.forEach{a.put(it.toJson())};check(sp(c).edit().putString("day_"+date,a.toString()).commit()){"official_journal_write_failed"}}
    @Synchronized fun observation(c:Context,date:String):JSONObject?=sp(c).getString("counter_"+date,null)?.let{JSONObject(it)}
    fun counter(c:Context,date:String=TokyoDate.of()):Int?=observation(c,date)?.optInt("watched",-1)?.takeIf{it in 0..25}
    @Synchronized fun observe(c:Context,date:String,counter:Int,run:String,source:String){
        check(TokyoDate.of()==date && counter in 0..25){"official_counter_wrong_date_or_range"}
        val row=JSONObject().put("date",date).put("watched",counter).put("runId",run).put("checkedAt",System.currentTimeMillis()).put("source",source)
        check(sp(c).edit().putString("counter_"+date,row.toString()).commit())
    }
    @Synchronized fun reserve(c:Context,date:String,run:String,before:Int):AdAttempt?{
        val rows=attempts(c,date)
        if(!OfficialAdQuota.canReserve(before,counter(c,date),TokyoDate.of()==date,AutoAccessibilityService.stopRequested,rows))return null
        val index=DailyAdStore.attempts(c,date).size+rows.size+1
        val a=AdAttempt(run,date,index,System.currentTimeMillis(),officialBefore=before,startState="OFFICIAL_QUOTA_RESERVED")
        save(c,date,rows+a);return a
    }
    @Synchronized fun transition(c:Context,date:String,run:String,index:Int,state:AdState){save(c,date,attempts(c,date).map{if(it.runId==run&&it.index==index&&!it.terminal)it.copy(state=state) else it})}
    @Synchronized fun finish(c:Context,date:String,run:String,index:Int,state:AdState,completed:Boolean,reward:Boolean,reason:String,before:Int,after:Int,evidence:String){
        save(c,date,AdDayLogic.finish(attempts(c,date),run,index,state,completed,reward,reason,before,after,evidence))
    }
    @Synchronized fun reconcileInterruptedDays(c:Context){sp(c).all.keys.filter{it.startsWith("day_")}.forEach{reconcile(c,it.removePrefix("day_"))}}
    @Synchronized fun reconcile(c:Context,date:String){val old=attempts(c,date);val fresh=AdDayLogic.reconcileInterrupted(old,System.currentTimeMillis());if(old!=fresh)save(c,date,fresh)}
}
