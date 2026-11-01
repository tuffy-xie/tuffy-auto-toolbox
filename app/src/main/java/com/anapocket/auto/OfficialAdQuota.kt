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
        check(list.size<=DAILY_AD_LIMIT+2 && list.map{it.index}.distinct().size==list.size && list.all{it.date==date && it.runId.isNotBlank() && it.index>0}){"official_journal_corrupt"}
        check(list.size<=DAILY_AD_LIMIT+reviewKeys(c,date,list).size){"official_verified_replacement_limit"}
        return list
    }
    private fun save(c:Context,date:String,rows:List<AdAttempt>){val a=JSONArray();rows.forEach{a.put(it.toJson())};check(sp(c).edit().putString("day_"+date,a.toString()).commit()){"official_journal_write_failed"}}
    @Synchronized fun observation(c:Context,date:String):JSONObject?=sp(c).getString("counter_"+date,null)?.let{JSONObject(it)}
    fun counter(c:Context,date:String=TokyoDate.of()):Int?=observation(c,date)?.optInt("watched",-1)?.takeIf{it in 0..25}
    private fun recoveryKey(a:AdAttempt)="recovery_"+a.date+"_"+a.runId+"_"+a.index
    @Synchronized fun beginBoundRecovery(c:Context,date:String,a:AdAttempt,departureProof:Boolean):Boolean {
        val rows=attempts(c,date);val last=rows.lastOrNull()?:return false
        val count=sp(c).getInt(recoveryKey(a),0)
        if(last.runId!=a.runId||last.index!=a.index||last.startedAt!=a.startedAt||count>=2||
            !(OfficialAdRecoveryPolicy.mayBind(rows,date,TokyoDate.of(),counter(c,date),departureProof)||
                ReviewedAdRecoveryPolicy.mayBind(rows,date,TokyoDate.of(),counter(c,date),departureProof,reviewKeys(c,date,rows))))return false
        check(sp(c).edit().putInt(recoveryKey(a),count+1).commit());return true
    }
    @Synchronized fun finishBoundRecovery(c:Context,date:String,a:AdAttempt,receipt:AdReceipt,evidence:String):Boolean {
        val rows=attempts(c,date);val last=rows.lastOrNull()?:return false
        if(sp(c).getInt(recoveryKey(a),0) !in 1..2||last.runId!=a.runId||last.index!=a.index||last.startedAt!=a.startedAt||
            !(OfficialAdRecoveryPolicy.mayBind(rows,date,TokyoDate.of(),counter(c,date),true)||
                ReviewedAdRecoveryPolicy.mayBind(rows,date,TokyoDate.of(),counter(c,date),true,reviewKeys(c,date,rows)))||!OfficialAdRecoveryPolicy.confirms(last,receipt))return false
        val reviewed=last.copy(state=AdState.REWARD_CONFIRMED,completed=true,rewardConfirmed=true,finishedAt=System.currentTimeMillis(),
            officialAfter=receipt.after,failureReason="bound_late_reward_receipt;previous="+last.failureReason,evidence=last.evidence+";"+evidence)
        save(c,date,rows.dropLast(1)+reviewed);return true
    }
    @Synchronized fun observe(c:Context,date:String,counter:Int,run:String,source:String){
        check(TokyoDate.of()==date && counter in 0..25){"official_counter_wrong_date_or_range"}
        val row=JSONObject().put("date",date).put("watched",counter).put("runId",run).put("checkedAt",System.currentTimeMillis()).put("source",source)
        check(sp(c).edit().putString("counter_"+date,row.toString()).commit())
    }
    private fun reviewKeys(c:Context,date:String,rows:List<AdAttempt>):Set<String>{
        val a=JSONArray(sp(c).getString("load_reviews_"+date,"[]"));check(a.length()<=2){"load_review_limit"}
        val keys=(0 until a.length()).map{
            val p=a.getJSONObject(it);val row=rows.singleOrNull{r->OfficialLoadFailurePolicy.key(r)==p.getString("key")}
                ?:error("load_review_binding_missing")
            check(p.getString("date")==date&&p.getInt("counter")==row.officialBefore&&
                p.getString("source")=="native_load_failure_and_fresh_unchanged_counter"&&OfficialLoadFailurePolicy.failedLoad(row)){"load_review_invalid"}
            p.getString("key")
        };check(keys.distinct().size==keys.size){"duplicate_load_review"};return keys.toSet()
    }
    @Synchronized fun reviewedKeys(c:Context,date:String)=reviewKeys(c,date,attempts(c,date))
    fun allResolved(c:Context,date:String):Boolean {val rows=attempts(c,date);val keys=reviewKeys(c,date,rows);return rows.all{OfficialLoadFailurePolicy.resolved(it,keys)}}
    fun startCap(c:Context,date:String)=OfficialLoadFailurePolicy.startCap(reviewedKeys(c,date).size)
    fun nativeLoadFailureProof(c:Context,date:String,a:AdAttempt):String?{
        if(!OfficialLoadFailurePolicy.failedLoad(a)||a.date!=date)return null
        val base=java.io.File(c.getExternalFilesDir(null)?:c.filesDir,"faults")
        return base.listFiles()?.filter{it.isDirectory&&it.name.startsWith(a.runId+"_daily_ads_"+a.index+"_")}?.sortedByDescending{it.name}?.firstOrNull{d->
            runCatching{
                val m=JSONObject(java.io.File(d,"manifest.json").readText());val lines=java.io.File(d,"nodes.txt").readLines()
                m.optString("date")==date&&m.optString("runId")==a.runId&&m.optString("attempt")==a.index.toString()&&
                    m.optString("module")=="daily_ads"&&m.optString("reason")=="official_error_dialog"&&
                    OfficialLoadFailurePolicy.nativeAlert(m.optString("foregroundActivity"),lines)
            }.getOrDefault(false)
        }?.name
    }
    @Synchronized fun reviewLoadFailure(c:Context,date:String,run:String,counter:Int):Boolean{
        val rows=attempts(c,date);val a=rows.lastOrNull()?:return false;val keys=reviewKeys(c,date,rows)
        val proof=nativeLoadFailureProof(c,date,a)?:return false
        if(!OfficialLoadFailurePolicy.mayReview(rows,date,TokyoDate.of(),counter,true,System.currentTimeMillis()-a.finishedAt,keys))return false
        val list=JSONArray(sp(c).getString("load_reviews_"+date,"[]"));list.put(JSONObject().put("key",OfficialLoadFailurePolicy.key(a))
            .put("date",date).put("counter",counter).put("source","native_load_failure_and_fresh_unchanged_counter")
            .put("nativeFault",proof).put("reviewRunId",run).put("checkedAt",System.currentTimeMillis()))
        check(sp(c).edit().putString("load_reviews_"+date,list.toString()).commit()){"load_review_write_failed"}
        return true
    }
    @Synchronized fun reserve(c:Context,date:String,run:String,before:Int):AdAttempt?{
        val rows=attempts(c,date)
        if(!OfficialAdQuota.canReserve(before,counter(c,date),TokyoDate.of()==date,AutoAccessibilityService.stopRequested,rows)&&
            !OfficialLoadFailurePolicy.canReserve(before,counter(c,date),TokyoDate.of()==date,AutoAccessibilityService.stopRequested,rows,reviewKeys(c,date,rows)))return null
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
