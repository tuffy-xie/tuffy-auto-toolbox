package com.anapocket.auto

import android.content.Context
import java.util.Calendar
import java.util.TimeZone

/**
 * From Oct 29 to Nov 8 every 00:03 run ended BLOCKED on a one-off fault (ad timeout, page not ready,
 * category strip missing) and the rest of each day waited for a person to press continue. A retry is the
 * same continue: every persisted record, and every no-replay rule, is evaluated again inside the run.
 */
object RoutineRetryPolicy {
    val delaysMinutes=listOf(10,20,30,45,60,90,120,120)
    const val LAST_START_MINUTE=23*60
    const val STALL_LIMIT=2
    /** Faults only a person can clear; retrying cannot change them. */
    fun humanRequired(detail:String,safetyPending:Boolean)=safetyPending||
        Regex("account_safety|secure_lock|账号保护|安全锁屏|本人解锁").containsMatchIn(detail)
    /**
     * Minutes until the next retry, or null for none. [attempts] retries already made today; [stalls] retries
     * in a row that changed nothing; [watchdog] a stop the run watchdog requested rather than the user.
     */
    fun nextDelay(state:String,detail:String,watchdog:Boolean,attempts:Int,stalls:Int,nowMinute:Int,safetyPending:Boolean,scheduleOn:Boolean,userStopped:Boolean=false):Int? {
        val retryable=state=="BLOCKED"||(state=="STOPPED"&&watchdog)
        // A run the user stopped can still end BLOCKED (a step refused its next tap); the stop decides, not the step.
        if(userStopped||!scheduleOn||!retryable||humanRequired(detail,safetyPending))return null
        if(maintenance(detail))return if(nowMinute+MAINTENANCE_DELAY>LAST_START_MINUTE)null else MAINTENANCE_DELAY
        if(attempts>=delaysMinutes.size||stalls>=STALL_LIMIT)return null
        val delay=delaysMinutes[attempts]
        return if(nowMinute+delay>LAST_START_MINUTE)null else delay
    }
    /**
     * 11/20: ANA's own maintenance (resume time not announced). Waiting is not a stall and not one of the day's
     * retries: check every [MAINTENANCE_DELAY] minutes until [LAST_START_MINUTE], then the normal budget applies again.
     */
    const val MAINTENANCE_DELAY=60
    fun maintenance(detail:String)=detail.contains(StartupPolicy.MAINTENANCE_TAG)
    /**
     * 11/27 (user-approved): the reward step paused because no reward ad would load. A later run picks the rewards up again;
     * the rest of the day is already finished, so this is its own small budget and never one of the day's normal retries.
     */
    val noFillDelaysMinutes=listOf(45,60,90)
    fun rewardNoFillDelay(state:String,rewardState:String?,rewardDetail:String?,noFillRetries:Int,nowMinute:Int,
        safetyPending:Boolean,scheduleOn:Boolean,userStopped:Boolean):Int? {
        if(userStopped||!scheduleOn||safetyPending||state!="PARTIALLY_COMPLETED")return null
        // 11/28: the unclaimed list not reached is the same kind of pause and shares this budget.
        if(rewardState!="PARTIAL"||!RewardClaimPolicy.laterRetry(rewardDetail))return null
        if(noFillRetries !in noFillDelaysMinutes.indices)return null
        val delay=noFillDelaysMinutes[noFillRetries]
        return if(nowMinute+delay>LAST_START_MINUTE)null else delay
    }
    /** A finished day still lets its queued no-fill retry fire; any other retry of a finished day is dropped. */
    fun dayFinished(routineState:String?,noFillPending:Boolean)=routineState in setOf("DONE","PARTIALLY_COMPLETED")&&!noFillPending
    /**
     * How a routine actually ended, for the retry decision. A coroutine cancellation is not a business BLOCKED:
     * the watchdog's cancel stays a watchdog stop (retryable), a service teardown or user stop is a plain stop.
     */
    fun terminalState(state:String,stopReason:String,cancelled:Boolean)=when{
        stopReason=="user"->"STOPPED"
        cancelled->"STOPPED"
        else->state
    }
    /**
     * Whether a queued retry may start now. Queued is not authorized: the schedule switch, a user stop of the
     * run that queued it, and the business day are read again when the alarm fires. Null means fire.
     */
    fun dropReason(pendingRun:String?,scheduleOn:Boolean,userStoppedRun:String?,dayFinished:Boolean,nowMinute:Int):String?=when{
        pendingRun==null->"no_pending_retry_for_today"
        !scheduleOn->"schedule_disabled"
        userStoppedRun!=null&&userStoppedRun==pendingRun->"source_run_stopped_by_user"
        dayFinished->"day_finished"
        nowMinute>LAST_START_MINUTE->"too_late"
        else->null
    }
}

object RoutineRetry {
    private fun sp(c:Context)=c.getSharedPreferences("ana_routine_retry_v1",Context.MODE_PRIVATE)
    @Volatile var watchdogRun=""
    private fun nowMinute()=Calendar.getInstance(TimeZone.getTimeZone("Asia/Tokyo")).let{it.get(Calendar.HOUR_OF_DAY)*60+it.get(Calendar.MINUTE)}

    /** What a retry could change: travel rows, set-aside attempts, scans complete, official ads, login. */
    fun progress(c:Context,date:String):String {
        val s=Prefs.loadBlocking(c);val a=ChallengeStore.rows(c,date)
        val maxed=(0 until a.length()).count{a.getJSONObject(it).optString("state") in setOf("BOOST_MAX_CONFIRMED","BOOST_MAX_OBSERVED")}
        val scans=Cat.values().filter{s.catOn(it)}.count{ChallengeScanStore.complete(c,date,it,s.kmOf(it))}
        // A new task row or a set-aside attempt is progress even when nothing reached max yet.
        val isolated=(0 until a.length()).count{PendingInitialJoinStore.isolated(a.getJSONObject(it),date)}
        // 11/19 (Gemini review D): a retry that moved a task from Boost 1/5 to 4/5 is progress too.
        val boosts=(0 until a.length()).sumOf{a.getJSONObject(it).optInt("boost",0).coerceIn(0,5)}
        return listOf(maxed,a.length(),isolated,scans,OfficialAdStore.counter(c,date)?:-1,LoginBonusStore.read(c,date)?.optString("state").orEmpty(),boosts).joinToString("|")
    }

    /** Called when a routine run ends; schedules the next retry or records why there is none. */
    @Synchronized fun afterRun(c:Context,run:String,state:String,detail:String) {
        try {
            val date=TokyoDate.of();val p=sp(c)
            val fp=progress(c,date);val last=p.getString("fp_$date",null)
            val maintenance=RoutineRetryPolicy.maintenance(detail)
            // The retry that follows a maintenance stop does not take one of the day's attempts (see fire).
            check(p.edit().putBoolean("maint_$date",maintenance).commit())
            val attempts=p.getInt("count_$date",0)
            // The first stop of the day has nothing to compare with; later ones count only when nothing moved.
            // Maintenance never counts as a stall, and resets the run of stalls before it.
            val stalls=if(maintenance)0 else if(last!=null&&last==fp)p.getInt("stall_$date",0)+1 else 0
            check(p.edit().putString("fp_$date",fp).putInt("stall_$date",stalls).commit())
            val safety=AccountSafetyStore.pending(c)!=null;val on=Prefs.loadBlocking(c).enabled;val userStopped=AnaProcessRecovery.userStoppedRun(c)==run
            val normal=RoutineRetryPolicy.nextDelay(state,detail,watchdogRun==run,attempts,stalls,nowMinute(),safety,on,userStopped)
            val reward=RewardClaimStore.read(c,date)
            val noFill=if(normal!=null)null else RoutineRetryPolicy.rewardNoFillDelay(state,reward?.optString("state"),
                reward?.optString("detail"),p.getInt("nofill_count_$date",0),nowMinute(),safety,on,userStopped)
            // A finished day waiting for reward ads is not a stall of the normal retries.
            check(p.edit().putBoolean("nofill_$date",noFill!=null).apply{if(noFill!=null)putInt("stall_$date",0)}.commit())
            val delay=normal?:noFill
            if(delay==null){p.edit().remove("pending_$date").commit();Logger.i("RETRY","run=$run state=$state no_retry attempts=$attempts stalls=$stalls");return}
            if(noFill!=null)Logger.i("RETRY","run=$run reward_no_fill retry="+(p.getInt("nofill_count_$date",0)+1))
            val at=System.currentTimeMillis()+delay*60_000L
            check(p.edit().putString("pending_$date",run).commit())
            Scheduler.scheduleRoutineRetry(c,at)
            Logger.i("RETRY","run=$run state=$state next_in=${delay}min attempt=${attempts+1} stalls=$stalls")
        }catch(e:Exception){Logger.w("RETRY","schedule failed: "+e.javaClass.simpleName)}
    }

    /** Turning the schedule off withdraws today's queued retry at once, so turning it back on cannot revive it. */
    @Synchronized fun revoke(c:Context,reason:String){
        val date=TokyoDate.of();val p=sp(c);val pending=p.getString("pending_$date",null)?:return
        p.edit().remove("pending_$date").commit();Logger.i("RETRY","revoked: $reason sourceRun=$pending")
    }

    /** The retry alarm: start the same continue a person would, unless something else holds the phone. */
    @Synchronized fun fire(c:Context) {
        val date=TokyoDate.of();val p=sp(c);val pending=p.getString("pending_$date",null)
        if(pending==null){Logger.i("RETRY","alarm without pending retry for $date");return}
        if(AutoAccessibilityService.isRunning)return  // a run is already active; it schedules its own follow-up
        val drop=RoutineRetryPolicy.dropReason(pending,Prefs.loadBlocking(c).enabled,AnaProcessRecovery.userStoppedRun(c),
            RoutineRetryPolicy.dayFinished(RoutineStore.read(c,date)?.optString("state"),p.getBoolean("nofill_$date",false)),nowMinute())
        if(drop!=null){p.edit().remove("pending_$date").commit();Logger.i("RETRY","dropped: $drop sourceRun=$pending");return}
        if(CheckinRuntime.activeApp!=null||!AutoAccessibilityService.isConnected()){
            Scheduler.scheduleRoutineRetry(c,System.currentTimeMillis()+10*60_000L)
            Logger.i("RETRY","deferred 10min checkin="+CheckinRuntime.activeApp+" connected="+AutoAccessibilityService.isConnected());return
        }
        val waitingForService=p.getBoolean("maint_$date",false)
        // A no-fill retry takes one of its own three, never one of the day's normal attempts.
        val noFill=p.getBoolean("nofill_$date",false)
        check(p.edit().remove("pending_$date").putInt("count_$date",p.getInt("count_$date",0)+if(waitingForService||noFill)0 else 1)
            .putInt("nofill_count_$date",p.getInt("nofill_count_$date",0)+if(noFill)1 else 0).putBoolean("nofill_$date",false).commit())
        Logger.i("RETRY","start attempt="+p.getInt("count_$date",0)+if(noFill)" reward_no_fill="+p.getInt("nofill_count_$date",0) else "")
        AutoAccessibilityService.triggerRoutine("auto_retry")
    }
}
