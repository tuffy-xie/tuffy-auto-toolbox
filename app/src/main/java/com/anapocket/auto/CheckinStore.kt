package com.anapocket.auto

import android.content.Context
import org.json.JSONObject
import java.io.File

data class CheckinConfig(val enabled:Boolean=false,val hour:Int,val minute:Int,val generation:Long=0)

/** Never opens an ANA ledger, settings file or account safety preference. */
class CheckinStore(private val ctx:Context,val app:CheckinApp) {
    companion object {
        private val lock=Any()
        fun taskLabel(task:String)=when(task){"points"->"1ポイントミッション";"garagara_daily"->"ガラガラポン日课";"pickup"->"PICKUP";"bonus"->"三项达成额外抽奖";"lottery1"->"ガラガラポン①";"lottery2"->"ガラガラポン②";"checkin"->"14日连续签到";"claim"->"领取昨日解锁奖励";"unlock"->"解锁明日奖励";else->"每日飞镖"}
        fun stateLabel(state:String?)=when(state){"SUCCESS"->"完成";"ALREADY_DONE"->"今日已完成";"UNCONFIRMED"->"待核对";"PREEMPTED"->"已让出给ANA";"NEEDS_USER_ACTION"->"需人工处理";"RUNNING"->"执行中";"BLOCKED"->"受阻";null->"未执行";else->"未完成"}
    }
    private val prefs=ctx.getSharedPreferences("checkin_v1_"+app.name,Context.MODE_PRIVATE)
    fun config()=CheckinConfig(prefs.getBoolean("enabled",false),prefs.getInt("hour",app.defaultHour),prefs.getInt("minute",app.defaultMinute),prefs.getLong("generation",0))
    fun configure(c:CheckinConfig)=synchronized(lock){require(c.hour in 0..23&&c.minute in 0..59);val old=config();val changed=old.enabled!=c.enabled||old.hour!=c.hour||old.minute!=c.minute
        check(prefs.edit().putBoolean("enabled",c.enabled).putInt("hour",c.hour).putInt("minute",c.minute).putLong("generation",old.generation+if(changed)1 else 0).commit())
    }
    private fun key(task:String,date:String)="task:$date:$task"
    fun read(task:String,date:String=TokyoDate.of()):JSONObject?=prefs.getString(key(task,date),null)?.let{JSONObject(it).also{r->r.put("rewardStage",RewardVerificationPolicy.stage(r.optString("state") in setOf("SUCCESS","ALREADY_DONE"),r.optString("rewardAmount").takeUnless{it=="null"},r.optBoolean("rewardConfirmed"))).put("creditState",RewardVerificationPolicy.credit(r.optBoolean("rewardConfirmed"),r.optString("failureReason").contains("due_")||r.optString("failureReason").contains("pending_credit")))}}
    fun update(task:String,run:String,state:CheckinState,reason:String="",rewardType:String="",amount:String?=null,confirmed:Boolean=false,evidence:String="",reserved:Boolean?=null,date:String=TokyoDate.of()):JSONObject = synchronized(lock) {
        val old=read(task,date)
        // Preserve already proved results, including during cancellation and crash reconciliation.
        if(old?.optString("state") in setOf("SUCCESS","ALREADY_DONE"))return@synchronized old!!
        if(state==CheckinState.PREEMPTED&&old!=null&&old.optString("runId")!=run)return@synchronized old
        val row=old?:JSONObject().put("appId",app.pkg).put("businessDate",date).put("taskId",task).put("startedAt",System.currentTimeMillis()).put("rewardType",when(app){CheckinApp.RAKUTEN->"楽天ポイント";CheckinApp.TIKTOK->"TikTokポイント";else->"肉マネークーポン（円）"}).put("rewardAmount",JSONObject.NULL).put("evidenceId","")
        val history=row.optJSONArray("history")?:org.json.JSONArray()
        history.put(JSONObject().put("runId",run).put("at",System.currentTimeMillis()).put("state",state.name).put("reason",reason))
        row.put("history",org.json.JSONArray().also{a->for(i in maxOf(0,history.length()-80) until history.length())a.put(history.get(i))})
        row.put("runId",run).put("state",state.name).put("failureReason",reason)
        if(state==CheckinState.RUNNING)row.put("configurationGeneration",config().generation)
        if(state!=CheckinState.RUNNING)row.put("completedAt",System.currentTimeMillis()) else row.remove("completedAt")
        if(rewardType.isNotEmpty())row.put("rewardType",rewardType)
        if(amount!=null)row.put("rewardAmount",amount)
        if(confirmed)row.put("rewardConfirmed",true) else if(!row.has("rewardConfirmed"))row.put("rewardConfirmed",false)
        if(evidence.isNotEmpty())row.put("evidenceId",evidence)
        if(reserved!=null)row.put("reserved",reserved)
        check(prefs.edit().putString(key(task,date),row.toString()).putLong("lastAt",System.currentTimeMillis()).commit())
        log(run,"TASK "+task+" "+state+" "+reason)
        row
    }
    fun tasks()=when(app){CheckinApp.RAKUTEN->listOf("points","garagara_daily","pickup","bonus","lottery1","lottery2");CheckinApp.TIKTOK->TikTokCheckinPolicy.TASKS;else->listOf("darts")}
    fun olderTaskExists(task:String,date:String)=prefs.all.keys.any{k->
        k.startsWith("task:")&&k.endsWith(":"+task)&&k.substringAfter("task:").substringBefore(":").let{it.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))&&it<date}
    }
    fun pickupStep(run:String,date:String,remaining:Int):Boolean=synchronized(lock){
        val row=read("pickup",date)?:return@synchronized false
        val previous=if(row.has("submittedRemaining"))row.optInt("submittedRemaining") else null
        if(!row.optBoolean("reserved")||!CheckinReceiptPolicy.canAdvancePickup(remaining,previous))return@synchronized false
        row.put("submittedRemaining",remaining).put("lastChoiceAt",System.currentTimeMillis()).put("phase","BROWSING_SUBMITTED")
        check(prefs.edit().putString(key("pickup",date),row.toString()).commit());log(run,"PICKUP_CHOICE remainingBefore="+remaining+" neutralSkip=true");true
    }
    fun pickupNext(run:String,date:String,remaining:Int):Boolean=synchronized(lock){
        val row=read("pickup",date)?:return@synchronized false
        val key="PR_NEXT:"+remaining
        if(!row.optBoolean("reserved")||row.optString("submittedNavigation")==key)return@synchronized false
        row.put("submittedNavigation",key).put("navigationAt",System.currentTimeMillis())
        check(prefs.edit().putString(this.key("pickup",date),row.toString()).commit());log(run,"PICKUP_PR_NEXT remaining="+remaining);true
    }
    fun submitDartsPull(run:String,date:String):Boolean=synchronized(lock){
        val row=read("darts",date)?:return@synchronized false
        if(row.optString("phase")!="START_SUBMITTED"||row.optBoolean("pullSubmitted"))return@synchronized false
        row.put("pullSubmitted",true).put("pullSubmittedAt",System.currentTimeMillis()).put("pullRunId",run).put("pullSubmissionSchemaVersion",2)
        check(prefs.edit().putString(key("darts",date),row.toString()).commit());log(run,"DARTS_PULL_SUBMITTED sameStartedGame=true");true
    }
    fun submitBonusStop(run:String,date:String):Boolean=synchronized(lock){
        val row=read("bonus",date)?:return@synchronized false
        if(row.optString("phase")!="START_SUBMITTED"||!row.optBoolean("reserved")||row.optBoolean("stopSubmitted")||row.optString("state") in setOf("SUCCESS","ALREADY_DONE"))return@synchronized false
        row.put("stopSubmitted",true).put("stopSubmittedAt",System.currentTimeMillis()).put("stopRunId",run)
        check(prefs.edit().putString(key("bonus",date),row.toString()).commit());log(run,"BONUS_STOP_SUBMITTED sameStartedDraw=true");true
    }
    /** One known R46 preflight bug: screenshot_3 was thrown before dispatchGesture was reachable. */
    fun reconcileUnissuedDartsPull(run:String,date:String,actualArrowStillReady:Boolean):Boolean=synchronized(lock){
        val row=read("darts",date)?:return@synchronized false
        val history=row.optJSONArray("history")
        val knownFailure=row.optString("failureReason")=="screenshot_3"||(history!=null&&(0 until history.length()).any{val h=history.getJSONObject(it);h.optString("reason")=="screenshot_3"&&h.optString("runId")==row.optString("pullRunId")})
        if(!actualArrowStillReady||row.optInt("pullSubmissionSchemaVersion",1)!=1||!knownFailure||!row.optBoolean("pullSubmitted"))return@synchronized false
        row.put("pullSubmitted",false).put("preflightSnapshotFailureReconciledAt",System.currentTimeMillis())
        check(prefs.edit().putString(key("darts",date),row.toString()).commit());log(run,"RECONCILED screenshot_3_before_dispatch sameGameActualArrowReady=true no_gesture_had_been_issued");true
    }
    fun all(date:String=TokyoDate.of())=tasks().mapNotNull{read(it,date)}
    fun reserve(task:String,run:String,date:String):Boolean {
        synchronized(lock){val r=read(task,date);val state=r?.optString("state")?.let{runCatching{CheckinState.valueOf(it)}.getOrNull()};if(!CheckinReceiptPolicy.maySubmit(state,r?.optBoolean("reserved")==true))return false
            val row=update(task,run,CheckinState.RUNNING,reserved=true,date=date)
            row.put("phase","ENTRY_RESERVED").put("entryRunId",run)
            check(prefs.edit().putString(key(task,date),row.toString()).commit());return true}
    }
    fun submitted(task:String,run:String,date:String,legacyEntry:Boolean=false,preflightVerified:Boolean=false):Boolean=synchronized(lock){
        val row=read(task,date)?:return@synchronized false
        if(row.optString("state") in setOf("SUCCESS","ALREADY_DONE")||!row.optBoolean("reserved")||row.optString("phase")=="START_SUBMITTED")return@synchronized false
        val knownLegacy=legacyEntry&&row.optString("phase").isBlank()&&row.optString("failureReason")=="official_remaining_chances_not_readable"
        if(row.optString("phase")!="ENTRY_RESERVED"&&!knownLegacy)return@synchronized false
        row.put("phase","START_SUBMITTED").put("submittedAt",System.currentTimeMillis()).put("submissionRunId",run)
            .put("submissionSchemaVersion",if(preflightVerified)2 else 1)
        check(prefs.edit().putString(key(task,date),row.toString()).commit());log(run,"SUBMITTED task="+task+" sameReservedOpportunity=true");true
    }
    /** Known R46 bug returned false from click's preflight before any gesture was reachable. */
    private fun unissuedDartsStart(row:JSONObject):Boolean {
        val history=row.optJSONArray("history")
        val why="darts_start_changed_after_reservation"
        val known=row.optString("failureReason")==why&&row.optString("runId")==row.optString("submissionRunId")||
            history!=null&&(0 until history.length()).any{val h=history.getJSONObject(it);h.optString("reason")==why&&h.optString("runId")==row.optString("submissionRunId")}
        return known&&row.optString("phase")=="START_SUBMITTED"&&row.optInt("submissionSchemaVersion",1)==1&&!row.optBoolean("pullSubmitted")
    }
    fun mayLocateUnissuedDartsStart(date:String)=read("darts",date)?.let{unissuedDartsStart(it)}==true
    fun reconcileUnissuedDartsStart(run:String,date:String,actualStartStillReady:Boolean):Boolean=synchronized(lock){
        val row=read("darts",date)?:return@synchronized false
        if(!actualStartStillReady||!unissuedDartsStart(row))return@synchronized false
        row.put("phase","ENTRY_RESERVED").put("unissuedStartSubmittedAt",row.optLong("submittedAt"))
            .put("unissuedStartSubmissionRunId",row.optString("submissionRunId")).put("unissuedStartReconciledAt",System.currentTimeMillis())
        check(prefs.edit().putString(key("darts",date),row.toString()).commit())
        log(run,"RECONCILED_DARTS_START known_preflight_rejection_before_any_gesture actual_same_start_page=true")
        true
    }
    fun markDispatch(date:String,generation:Long?=null):Boolean=synchronized(lock){val k="dispatch:$date"+(generation?.let{":generation:$it"}?:"");if(prefs.getBoolean(k,false))false else {check(prefs.edit().putBoolean(k,true).commit());true}}
    fun acceptDispatch(date:String,scheduledAt:Long,source:String,generation:Long):Boolean=synchronized(lock){
        val c=config();if(!CheckinDispatchPolicy.accepts(c.enabled,c.generation,generation,scheduledAt==0L||CheckinClock.date(scheduledAt)==date))return@synchronized false
        if(!markDispatch(date,generation))return@synchronized false
        noteDispatch(date,scheduledAt,source);pending(date);true
    }
    /** Startup is journaled before clearing pending, even before the first business task exists. */
    fun beginRun(run:String,date:String)=synchronized(lock){
        check(prefs.edit().putString("inflightRun",run).putString("inflightDate",date).putLong("inflightGeneration",config().generation).putLong("lastAt",System.currentTimeMillis()).remove("pending").commit())
    }
    /** Serialize the run check with a business mutation; old callbacks cannot write a newer run. */
    fun <T> withActiveRun(run:String,block:()->T):T?=synchronized(lock){
        if(prefs.getString("inflightRun",null)!=run)return@synchronized null
        block()
    }
    /** A cancelled old controller must never clear a newer controller's startup journal. */
    fun finishRun(run:String,resumeReason:String?=null):Boolean=synchronized(lock){
        if(prefs.getString("inflightRun",null)!=run)return@synchronized false
        val date=prefs.getString("inflightDate",null)
        val runGeneration=prefs.getLong("inflightGeneration",0)
        val edit=prefs.edit().remove("inflightRun").remove("inflightDate").remove("inflightGeneration")
        if(resumeReason!=null&&date!=null&&date==TokyoDate.of()&&config().enabled&&config().generation==runGeneration&&hold().isBlank()){
            val info=dispatchInfo(date)?:JSONObject().put("businessDate",date).put("scheduledAt",0).put("actualTriggerAt",System.currentTimeMillis())
            info.put("waitReason",resumeReason).put("waitingSince",System.currentTimeMillis())
            edit.putString("pending",date).putLong("pendingGeneration",runGeneration).putString("dispatchInfo:$date",info.toString())
        }
        check(edit.commit());true
    }
    fun recover(activeRun:()->String?={null}):Unit=synchronized(lock){
        // Read the live lease inside the same lock as beginRun, rather than using an earlier snapshot.
        // TIME_CHANGED / reconnect restoration must not reconcile a controller that is still alive.
        val live=activeRun()
        for((k,v)in prefs.all){if(k.startsWith("task:")&&v is String){val r=JSONObject(v);if(r.optString("state")=="RUNNING"&&r.optString("runId")!=live){
            val date=r.getString("businessDate")
            update(r.getString("taskId"),r.optString("runId"),CheckinState.UNCONFIRMED,"process_or_service_interrupted",date=date)
            if(date==TokyoDate.of()&&config().enabled&&config().generation==r.optLong("configurationGeneration",0)&&hold().isBlank())pending(date)
        }}}
        val interrupted=prefs.getString("inflightRun",null)
        val date=prefs.getString("inflightDate",null)
        if(interrupted!=null&&interrupted!=live){
            val resume=date==TokyoDate.of()&&config().enabled&&config().generation==prefs.getLong("inflightGeneration",0)&&hold().isBlank()
            val edit=prefs.edit().remove("inflightRun").remove("inflightDate")
            if(resume)edit.putString("pending",date).putLong("pendingGeneration",config().generation)
            check(edit.commit())
            log(interrupted,"RECOVER_STARTUP interruptedDate=$date pendingRestored=$resume no_reward_submission_inferred=true")
        }
    }
    fun pendingDate()=prefs.getString("pending",null)?.takeIf{prefs.getLong("pendingGeneration",0)==config().generation}
    fun dispatchInfo(date:String)=prefs.getString("dispatchInfo:$date",null)?.let{JSONObject(it)}
    fun noteDispatch(date:String,scheduledAt:Long,source:String)=synchronized(lock){
        val info=JSONObject().put("businessDate",date).put("scheduledAt",scheduledAt).put("actualTriggerAt",System.currentTimeMillis()).put("triggerSource",source)
        check(prefs.edit().putString("dispatchInfo:$date",info.toString()).commit())
    }
    fun waiting(date:String,reason:String)=synchronized(lock){
        val info=dispatchInfo(date)?:JSONObject().put("businessDate",date).put("scheduledAt",0).put("actualTriggerAt",System.currentTimeMillis())
        info.put("waitReason",reason).put("waitingSince",System.currentTimeMillis())
        check(prefs.edit().putString("dispatchInfo:$date",info.toString()).putString("pending",date).putLong("pendingGeneration",config().generation).commit())
    }
    fun pending(date:String?){check(prefs.edit().apply{if(date==null){remove("pending");remove("pendingGeneration")} else {putString("pending",date);putLong("pendingGeneration",config().generation)}}.commit())}
    fun hold(reason:String){check(prefs.edit().putString("humanHold",reason).commit())}
    fun hold()=prefs.getString("humanHold","").orEmpty()
    fun clearHold(){check(prefs.edit().remove("humanHold").commit())}
    fun lastAt()=prefs.getLong("lastAt",0)
    fun dir(run:String)=File(ctx.filesDir,"checkins/"+app.name+"/"+run.replace(Regex("[^a-zA-Z0-9_-]"),"_")).apply{mkdirs()}
    fun log(run:String,message:String){File(dir(run),"events.log").appendText(TokyoDate.time()+" "+message+"\n")}
    fun summary(date:String=TokyoDate.of()):String {
        val rows=all(date);if(rows.isEmpty())return "今日尚未执行"
        return tasks().joinToString("\n"){task->val r=read(task,date);taskLabel(task)+"："+stateLabel(r?.optString("state"))+(if(r?.optString("failureReason")?.endsWith("_no_prize")==true)" · 未中奖" else if(r!=null&&r.has("rewardAmount")&&!r.isNull("rewardAmount"))" · "+r.optString("rewardAmount")+" "+r.optString("rewardType")+(if(r.optBoolean("rewardConfirmed"))" 已到账" else " 待入账核对") else "")}
    }
}
