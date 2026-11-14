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
    fun refused(stage:String,sdkWindowOpen:Boolean,unresolvedTravelAd:Boolean,unresolvedDailyAd:Boolean)=
        unresolvedDailyAd||(sdkWindowOpen&&stage !in setOf("STARTUP","CHALLENGES"))||(unresolvedTravelAd&&stage!="CHALLENGES")
    fun eligible(date:String,recordedDate:String,stage:String,state:String,reason:String,startedAt:Long,
        now:Long,confirmed:Boolean,used:Boolean,dailyRestarts:Int,live:Boolean,sdk:Boolean,limit:Int=3):Boolean =
        live && sdk && date==recordedDate && stage in setOf("STARTUP","CHALLENGES","ADS") &&
        state=="UNCONFIRMED" && !confirmed && !used && dailyRestarts<limit && startedAt>0 &&
        now>=startedAt && now-startedAt>=(if(stage=="STARTUP")300_000L else 95_000L) &&
        reason in setOf("startup_ad_timeout_300s","ad_timeout_95s","ad_part_timeout_95s","ad_timeout_300s","completion_not_confirmed")
    fun mayRelaunch(date:String,recordedDate:String,state:String,marker:String) =
        date==recordedDate && state=="UNCONFIRMED" && marker in setOf("SYSTEM_STOP_CONFIRMED","RELAUNCHED_REVIEW_REQUIRED")
    fun appInfo(pkg:String,activity:String,labels:List<String>) =
        pkg=="com.miui.securitycenter" && activity=="com.miui.appmanager.ApplicationsDetailsActivity" &&
        "ANA Pocket" in labels && "应用信息" in labels && "结束运行" in labels
    fun confirmation(pkg:String,labels:List<String>,targetVerified:Boolean) = targetVerified &&
        pkg=="com.miui.securitycenter" && "要强行停止吗？" in labels &&
        labels.any{it=="强行停止某个应用可能会导致其出现异常。"} && "确定" in labels
}
