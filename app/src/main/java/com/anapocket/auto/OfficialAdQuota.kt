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
        val a=JSONArray(ReliableLedger.read(c,"officialAds",date,"day"){sp(c).getString("day_"+date,"[]")});val list=(0 until a.length()).map{AdAttempt.fromJson(a.getJSONObject(it))}
        check(list.size<=DAILY_AD_LIMIT+2 && list.map{it.index}.distinct().size==list.size && list.all{it.date==date && it.runId.isNotBlank() && it.index>0}){"official_journal_corrupt"}
        check(list.size<=DAILY_AD_LIMIT+reviewKeys(c,date,list).size){"official_verified_replacement_limit"}
        return list
    }
    private fun save(c:Context,date:String,rows:List<AdAttempt>){
        val previous=attempts(c,date)
        check(rows.size>=previous.size&&previous.indices.all{i->val old=previous[i];val next=rows[i]
            old.runId==next.runId&&old.index==next.index&&old.startedAt==next.startedAt&&old.officialBinding==next.officialBinding}){"daily_original_binding_changed"}
        val a=JSONArray();rows.forEach{a.put(it.toJson())};ReliableLedger.put(c,"officialAds",date,"day",a.toString())}
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
        if(last.runId==a.runId&&last.index==a.index&&last.startedAt==a.startedAt&&last.completed&&last.rewardConfirmed&&last.state==AdState.REWARD_CONFIRMED)
            return receipt.completed&&receipt.reward&&receipt.after==last.officialAfter
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
        return (base.listFiles().orEmpty().toList()+java.io.File(c.getExternalFilesDir(null)?:c.filesDir,"faults_h14").listFiles().orEmpty().toList()).filter{it.isDirectory&&it.name.startsWith(a.runId+"_daily_ads_"+a.index+"_")}?.sortedByDescending{it.name}?.firstOrNull{d->
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
        val started=System.currentTimeMillis()
        // Daily ads have no mission id: bind the observed native counter and original attempt in the same FULL write.
        val binding=JSONObject().put("source","ANA_native_daily_counter_v1").put("date",date).put("runId",run).put("index",index)
            .put("startedAt",started).put("controllerRun",AutoAccessibilityService.currentRunId?:run).put("officialBefore",before).put("observedAt",started)
        val a=AdAttempt(run,date,index,started,officialBefore=before,startState="OFFICIAL_QUOTA_RESERVED",dispatchPhase="RESERVED",officialBinding=binding.toString())
        save(c,date,rows+a);return a
    }
    @Synchronized fun transition(c:Context,date:String,run:String,index:Int,state:AdState){save(c,date,attempts(c,date).map{if(it.runId==run&&it.index==index&&!it.terminal)it.copy(state=state,adDepartureObserved=it.adDepartureObserved||state==AdState.AD_PLAYING) else it})}
    @Synchronized fun finish(c:Context,date:String,run:String,index:Int,state:AdState,completed:Boolean,reward:Boolean,reason:String,before:Int,after:Int,evidence:String){
        val existing=attempts(c,date).singleOrNull{it.runId==run&&it.index==index}?:error("official_attempt_missing")
        if(existing.completed&&existing.rewardConfirmed&&existing.state==AdState.REWARD_CONFIRMED){
            if(completed&&reward)check(after==existing.officialAfter){"confirmed_counter_changed"}
            return // A post-commit interruption must never downgrade a durable receipt.
        }
        save(c,date,AdDayLogic.finish(attempts(c,date),run,index,state,completed,reward,reason,before,after,evidence))
    }
    /** Read-only check of the original durable receipt. Legacy counter-only UNKNOWN remains unresolved. */
    @Synchronized fun hasRecordedReceipt(c:Context,date:String,a:AdAttempt):Boolean {
        val saved=attempts(c,date).lastOrNull()?:return false
        if(saved.runId!=a.runId||saved.index!=a.index||saved.startedAt!=a.startedAt||saved.date!=date||
            saved.dispatchPhase!="DISPATCHED"||!saved.adDepartureObserved||saved.officialBinding.isBlank())return false
        return runCatching{
            val b=JSONObject(saved.officialBinding)
            val raw=ReliableLedger.read(c,"dailyReceipt",date,a.runId+":"+a.index+":"+a.startedAt){null}?:return false
            val receipt=JSONObject(raw)
            b.getString("source")=="ANA_native_daily_counter_v1"&&b.getString("date")==date&&b.getString("runId")==a.runId&&
                b.getInt("index")==a.index&&b.getLong("startedAt")==a.startedAt&&b.getInt("officialBefore")==saved.officialBefore&&
                receipt.getString("date")==date&&receipt.getString("runId")==a.runId&&receipt.getInt("index")==a.index&&
                receipt.getLong("startedAt")==a.startedAt&&receipt.getInt("before")==saved.officialBefore&&
                saved.dispatchCompletedAt>0&&receipt.getLong("observedAt")>=saved.dispatchCompletedAt&&
                (saved.finishedAt==0L||receipt.getLong("observedAt")<=saved.finishedAt)
        }.getOrDefault(false)
    }
    @Synchronized fun reconcileRecordedReceipt(c:Context,date:String,a:AdAttempt,fresh:Int):Boolean {
        val rows=attempts(c,date);val last=rows.lastOrNull()?:return false
        if(!hasRecordedReceipt(c,date,a)||TokyoDate.of()!=date)return false
        if(last.completed&&last.rewardConfirmed&&last.state==AdState.REWARD_CONFIRMED)return last.officialAfter==fresh
        if(!OfficialAdRecoveryPolicy.recordedReceipt(rows,date,TokyoDate.of(),fresh,true))return false
        val next=last.copy(state=AdState.REWARD_CONFIRMED,completed=true,rewardConfirmed=true,finishedAt=System.currentTimeMillis(),
            officialAfter=fresh,failureReason="",evidence=last.evidence+";original_durable_native_receipt_and_fresh_exact_counter")
        save(c,date,rows.dropLast(1)+next) // Existing FULL transaction; original identity and all other attempts unchanged.
        return true
    }
    @Synchronized fun recordReceipt(c:Context,date:String,a:AdAttempt,before:Int){
        val last=attempts(c,date).lastOrNull()?:error("receipt_attempt_missing")
        check(TokyoDate.of()==date&&last.runId==a.runId&&last.index==a.index&&last.startedAt==a.startedAt&&last.officialBefore==before){"receipt_attempt_changed"}
        ReliableLedger.put(c,"dailyReceipt",date,a.runId+":"+a.index+":"+a.startedAt,
            JSONObject().put("date",date).put("runId",a.runId).put("index",a.index).put("startedAt",a.startedAt).put("before",before).put("observedAt",System.currentTimeMillis()).toString())
    }
    @Synchronized fun commitReceipt(c:Context,date:String,a:AdAttempt,before:Int,after:Int){
        check(after==before+1&&before==a.officialBefore&&TokyoDate.of()==date){"receipt_counter_not_exact"}
        val proof=ReliableLedger.read(c,"dailyReceipt",date,a.runId+":"+a.index+":"+a.startedAt){null}?:error("receipt_observation_missing")
        check(JSONObject(proof).getInt("before")==before){"receipt_baseline_changed"}
        val last=attempts(c,date).lastOrNull()?:error("receipt_attempt_missing")
        if(last.terminal&&!last.rewardConfirmed){
            check(finishBoundRecovery(c,date,a,AdReceipt(AdState.REWARD_CONFIRMED,true,true,after),"durable_bound_late_receipt_and_exact_counter")){"bound_receipt_commit_rejected"}
        }else finish(c,date,a.runId,a.index,AdState.REWARD_CONFIRMED,true,true,"",before,after,"durable_native_receipt_and_exact_counter")
    }
    @Synchronized fun fence(c:Context,date:String,a:AdAttempt,phase:String){
        val rows=attempts(c,date);val last=rows.lastOrNull()?:error("dispatch_attempt_missing")
        check(last.runId==a.runId&&last.index==a.index&&last.startedAt==a.startedAt&&!last.terminal&&TokyoDate.of()==date){"dispatch_attempt_changed"}
        val now=System.currentTimeMillis()
        val next=when(phase){
            "ARMED"->{check(last.dispatchPhase=="RESERVED"){"daily_dispatch_already_armed"};last.copy(dispatchPhase=phase,dispatchArmedAt=now)}
            "DISPATCHED"->{check(last.dispatchPhase=="ARMED"){"daily_dispatch_phase_invalid"};last.copy(dispatchPhase=phase,dispatchCompletedAt=now)}
            else->error("daily_dispatch_phase_unknown")
        }
        save(c,date,rows.dropLast(1)+next)
    }
    @Synchronized fun reconcileInterruptedDays(c:Context){(sp(c).all.keys.filter{it.startsWith("day_")}.map{it.removePrefix("day_")}+ReliableLedger.scopes(c,"officialAds")).distinct().forEach{reconcile(c,it)}}
    @Synchronized fun reconcile(c:Context,date:String){val old=attempts(c,date);val fresh=AdDayLogic.reconcileInterrupted(old,System.currentTimeMillis());if(old!=fresh)save(c,date,fresh)}
}
