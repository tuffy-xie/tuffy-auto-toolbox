package com.anapocket.auto

/** User-authorized recovery of one timed-out viewing; never authorizes a new viewing. */
object StalledAdRestartPolicy {
    /** Persistence acknowledgment is the fence before any system-stop action. */
    fun reserve(used:Boolean,dailyRestarts:Int,commit:()->Boolean)=!used&&dailyRestarts in 0..2&&commit()
    fun eligible(date:String,recordedDate:String,stage:String,state:String,reason:String,startedAt:Long,
        now:Long,confirmed:Boolean,used:Boolean,dailyRestarts:Int,live:Boolean,sdk:Boolean):Boolean =
        live && sdk && date==recordedDate && stage in setOf("STARTUP","CHALLENGES","ADS") &&
        state=="UNCONFIRMED" && !confirmed && !used && dailyRestarts<3 && startedAt>0 &&
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
