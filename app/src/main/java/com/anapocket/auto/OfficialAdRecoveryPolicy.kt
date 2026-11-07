package com.anapocket.auto

/** This only binds an already-started last attempt. It never permits a replacement advertisement. */
object OfficialAdRecoveryPolicy {
    fun mayBind(rows:List<AdAttempt>,date:String,today:String,counter:Int?,departureProof:Boolean):Boolean {
        val last=rows.lastOrNull()?:return false
        return date==today&&departureProof&&last.date==date&&last.state==AdState.UNCONFIRMED&&
            !last.completed&&!last.rewardConfirmed&&last.officialBefore in 0..24&&counter==last.officialBefore&&
            last.failureReason in setOf("ad_timeout_95s","ad_part_timeout_95s","ad_timeout_300s","process_or_run_interrupted","user_stopped")&&
            rows.dropLast(1).all{it.date==date&&it.completed&&it.rewardConfirmed&&it.state==AdState.REWARD_CONFIRMED}
    }
    /** A recorded native receipt plus a freshly reloaded exact counter is not a counter-only inference. */
    fun recordedReceipt(rows:List<AdAttempt>,date:String,today:String,fresh:Int?,proofBound:Boolean):Boolean {
        val a=rows.lastOrNull()?:return false
        return proofBound&&date==today&&a.date==date&&a.dispatchPhase=="DISPATCHED"&&a.adDepartureObserved&&
            a.officialBinding.isNotBlank()&&!a.completed&&a.officialBefore in 0..24&&fresh==a.officialBefore+1&&
            (a.state in setOf(AdState.AD_RETURNED,AdState.REWARD_CONFIRMED)||
                a.state==AdState.UNCONFIRMED&&a.failureReason=="completion_not_confirmed")&&
            rows.dropLast(1).all{it.date==date&&it.completed&&it.rewardConfirmed&&it.state==AdState.REWARD_CONFIRMED}
    }
    fun confirms(original:AdAttempt,receipt:AdReceipt)=receipt.completed&&receipt.reward&&
        receipt.state==AdState.REWARD_CONFIRMED&&receipt.after==original.officialBefore+1
}
