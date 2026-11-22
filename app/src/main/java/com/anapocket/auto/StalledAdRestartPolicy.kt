package com.anapocket.auto

/** User-authorized recovery of one timed-out viewing; never authorizes a new viewing. */
object StalledAdRestartPolicy {
    /** Persistence acknowledgment is the fence before any system-stop action. */
    /** Removing the ANA task only ends one timed-out ad, once per attempt; Nov 9 needed three before the travel list was done. */
    const val OPEN_AD_RESTART_LIMIT=12
    fun reserve(used:Boolean,dailyRestarts:Int,limit:Int=3,commit:()->Boolean)=!used&&dailyRestarts in 0 until limit&&commit()
    /**
     * What stops a restart before [eligible] looks at it. A login or travel ad that timed out and is still open is what
     * this restart is for, so those stages pass; [eligible] then limits it to that one attempt. A daily ad has its own path.
     */
    /**
     * 11/11: one daily ad ended UNCONFIRMED at 03:07 and from then on every stuck travel Boost ad was refused its restart,
     * so each retry stopped in front of the same open ad. A finished (terminal) daily record is not touched by
     * removing ANA's task for a travel ad; only a daily ad that is still open holds the CHALLENGES stage.
     */
    fun dailyHolds(stage:String,openDailyAd:Boolean,terminalUnconfirmedDailyAd:Boolean)=
        openDailyAd||(terminalUnconfirmedDailyAd&&stage!="CHALLENGES")
    /**
     * 11/22 03:31: daily ad #2 (an AppLovin end card) timed out and stayed open. Travel Boosts earlier that night had ended
     * UNCONFIRMED long before it started, and any such row refused the blank-daily restart, so every retry stopped in front
     * of the daily ad until the day was given up at 04:01 with 1/25. The window in front belongs to the last ad dispatched:
     * only a travel ad that has not ended, or that started or ended after this daily ad started, holds the restart.
     * Finished earlier travel unknowns stay UNKNOWN and are not touched by removing the daily ad's task.
     */
    fun travelHoldsDailyRestart(dailyStartedAt:Long,travel:List<Pair<Long,Long>>)=
        dailyStartedAt<=0L||travel.any{(started,finished)->started<=0L||finished<=0L||started>=dailyStartedAt||finished>=dailyStartedAt}
    fun refused(stage:String,sdkWindowOpen:Boolean,unresolvedTravelAd:Boolean,unresolvedDailyAd:Boolean)=
        unresolvedDailyAd||(sdkWindowOpen&&stage !in setOf("STARTUP","CHALLENGES"))||(unresolvedTravelAd&&stage!="CHALLENGES")
    /**
     * Segment timeouts only say "no progress was seen in 95 s", which includes "nothing could be read". Before any
     * destructive restart these need a no-progress review of the same attempt; the 300 s absolute caps do not.
     */
    val SEGMENT_TIMEOUTS=setOf("ad_timeout_95s","ad_part_timeout_95s")
    fun needsStallReview(reason:String)=reason in SEGMENT_TIMEOUTS
    /**
     * 11/19 01:05-01:40 and 03:16: an AppLovin playable kept animating with no close for 30+ minutes, so every review saw
     * "progress" and every retry stopped in front of it. An ad window still open well past the 300 s absolute cap is the
     * same case that cap already covers (no frozen proof needed); the same attempt identity and every other gate still apply.
     */
    const val ABSOLUTE_CAP_OPEN_MS=330_000L
    fun pastAbsoluteCap(startedAt:Long,now:Long,adWindowOpen:Boolean)=adWindowOpen&&startedAt>0&&now>=startedAt&&now-startedAt>=ABSOLUTE_CAP_OPEN_MS
    const val REVIEW_WINDOW_MS=45_000L
    fun eligible(date:String,recordedDate:String,stage:String,state:String,reason:String,startedAt:Long,
        now:Long,confirmed:Boolean,used:Boolean,dailyRestarts:Int,live:Boolean,sdk:Boolean,limit:Int=3):Boolean =
        live && sdk && date==recordedDate && stage in setOf("STARTUP","CHALLENGES","ADS") &&
        state=="UNCONFIRMED" && !confirmed && !used && dailyRestarts<limit && startedAt>0 &&
        now>=startedAt && now-startedAt>=(if(stage=="STARTUP")300_000L else 95_000L) &&
        reason in setOf("startup_ad_timeout_300s","ad_timeout_95s","ad_part_timeout_95s","ad_timeout_300s","completion_not_confirmed")
    /**
     * 11/18 00:05 (and 11/10): the login video was watched and closed, ANA's dialog still offered 「動画を見てさらに100ptGET」
     * and the whole run stopped until the 00:15 retry, which then got the 100pt. The user asked (11/18) to restart ANA at
     * once in that case. Only that recorded reason, with ANA itself in front and no ad window open, once per login attempt.
     * Nothing is marked watched or rewarded; the relaunched startup reads the official dialog like any retry would.
     */
    const val LOGIN_OFFER_RETURN="login_reward_not_confirmed_after_offer_return"
    fun loginOfferRestart(date:String,recordedDate:String,stage:String,state:String,reason:String,confirmed:Boolean,
        used:Boolean,dailyRestarts:Int,live:Boolean,anaForeground:Boolean,sdk:Boolean,limit:Int=OPEN_AD_RESTART_LIMIT)=
        live&&anaForeground&&!sdk&&stage=="STARTUP"&&date==recordedDate&&state=="UNCONFIRMED"&&reason==LOGIN_OFFER_RETURN&&
            !confirmed&&!used&&dailyRestarts in 0 until limit
    fun mayRelaunch(date:String,recordedDate:String,state:String,marker:String) =
        date==recordedDate && state=="UNCONFIRMED" && marker in setOf("SYSTEM_STOP_CONFIRMED","RELAUNCHED_REVIEW_REQUIRED")
    fun appInfo(pkg:String,activity:String,labels:List<String>) =
        pkg=="com.miui.securitycenter" && activity=="com.miui.appmanager.ApplicationsDetailsActivity" &&
        "ANA Pocket" in labels && "应用信息" in labels && "结束运行" in labels
    fun confirmation(pkg:String,labels:List<String>,targetVerified:Boolean) = targetVerified &&
        pkg=="com.miui.securitycenter" && "要强行停止吗？" in labels &&
        labels.any{it=="强行停止某个应用可能会导致其出现异常。"} && "确定" in labels
}

/**
 * Read-only stall review: verified only after the same evidence key stayed unchanged for [quietMs]. A changed key is
 * progress (the quiet clock restarts); a missing key never counts toward quiet.
 */
class StallQuietTracker(private val quietMs:Long=OpaqueRecoveryPolicy.STALL_QUIET_MS) {
    private var key:String?=null;private var since=0L
    var evidenceSeen=false;private set
    var changes=0;private set
    fun observe(now:Long,k:String?):Boolean {
        if(k==null){key=null;return false}
        evidenceSeen=true
        if(k!=key){if(key!=null)changes++;key=k;since=now}
        return now-since>=quietMs
    }
    fun quiet(now:Long)=if(key==null)0L else now-since
    /** Why a review ended without proof: progress seen, or nothing readable. */
    fun outcome()=if(changes>0)"progress_observed" else if(!evidenceSeen)"no_evidence" else "quiet_window_not_reached"
}
