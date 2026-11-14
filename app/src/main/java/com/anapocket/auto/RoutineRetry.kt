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
    fun nextDelay(state:String,detail:String,watchdog:Boolean,attempts:Int,stalls:Int,nowMinute:Int,safetyPending:Boolean,scheduleOn:Boolean):Int? {
        val retryable=state=="BLOCKED"||(state=="STOPPED"&&watchdog)
        if(!scheduleOn||!retryable||humanRequired(detail,safetyPending))return null
        if(attempts>=delaysMinutes.size||stalls>=STALL_LIMIT)return null
        val delay=delaysMinutes[attempts]
        return if(nowMinute+delay>LAST_START_MINUTE)null else delay
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
        return listOf(maxed,a.length(),isolated,scans,OfficialAdStore.counter(c,date)?:-1,LoginBonusStore.read(c,date)?.optString("state").orEmpty()).joinToString("|")
    }

    /** Called when a routine run ends; schedules the next retry or records why there is none. */
    @Synchronized fun afterRun(c:Context,run:String,state:String,detail:String) {
        try {
            val date=TokyoDate.of();val p=sp(c)
            val fp=progress(c,date);val last=p.getString("fp_$date",null)
            val attempts=p.getInt("count_$date",0)
            // The first stop of the day has nothing to compare with; later ones count only when nothing moved.
            val stalls=if(last!=null&&last==fp)p.getInt("stall_$date",0)+1 else 0
            check(p.edit().putString("fp_$date",fp).putInt("stall_$date",stalls).commit())
            val delay=RoutineRetryPolicy.nextDelay(state,detail,watchdogRun==run,attempts,stalls,nowMinute(),
                AccountSafetyStore.pending(c)!=null,Prefs.loadBlocking(c).enabled)
            if(delay==null){p.edit().remove("pending_$date").commit();Logger.i("RETRY","run=$run state=$state no_retry attempts=$attempts stalls=$stalls");return}
            val at=System.currentTimeMillis()+delay*60_000L
            check(p.edit().putString("pending_$date",run).commit())
            Scheduler.scheduleRoutineRetry(c,at)
            Logger.i("RETRY","run=$run state=$state next_in=${delay}min attempt=${attempts+1} stalls=$stalls")
        }catch(e:Exception){Logger.w("RETRY","schedule failed: "+e.javaClass.simpleName)}
    }

    /** The retry alarm: start the same continue a person would, unless something else holds the phone. */
    @Synchronized fun fire(c:Context) {
        val date=TokyoDate.of();val p=sp(c)
        if(p.getString("pending_$date",null)==null){Logger.i("RETRY","alarm without pending retry for $date");return}
        if(AutoAccessibilityService.isRunning)return  // a run is already active; it schedules its own follow-up
        if(RoutineStore.read(c,date)?.optString("state") in setOf("DONE","PARTIALLY_COMPLETED")||nowMinute()>RoutineRetryPolicy.LAST_START_MINUTE){
            p.edit().remove("pending_$date").commit();Logger.i("RETRY","dropped: day finished or too late");return
        }
        if(CheckinRuntime.activeApp!=null||!AutoAccessibilityService.isConnected()){
            Scheduler.scheduleRoutineRetry(c,System.currentTimeMillis()+10*60_000L)
            Logger.i("RETRY","deferred 10min checkin="+CheckinRuntime.activeApp+" connected="+AutoAccessibilityService.isConnected());return
        }
        check(p.edit().remove("pending_$date").putInt("count_$date",p.getInt("count_$date",0)+1).commit())
        Logger.i("RETRY","start attempt="+p.getInt("count_$date",0))
        AutoAccessibilityService.triggerRoutine("auto_retry")
    }
}
