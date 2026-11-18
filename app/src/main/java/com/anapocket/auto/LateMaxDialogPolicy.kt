package com.anapocket.auto

/** Value snapshot of one saved task row; no JSON or UI objects. */
data class LateMaxRow(val key:String,val date:String,val state:String,val joined:Boolean,val boost:Int,val isolated:Boolean,
    val lastIndex:Int,val adsCount:Int,val lastInitial:Boolean,val lastConfirmed:Boolean,val departed:Boolean,
    val dispatchPhase:String,val reason:String,val expectedAfter:Int,val offeredReward:Int)

/**
 * 11/18 04:28: the 5th Boost of CAR 10km timed out at 300 s (UNCONFIRMED, boost 4) and later succeeded; ANA then showed
 * its own "報酬が最大値に到達しました！ 400pt" dialog. With no Tuffy journal the dialog was refused and every retry stopped
 * on it until a person intervened. User-approved 11/19: the official dialog confirms that attempt only when it can belong
 * to nothing else — exactly one unresolved row today, which is that row's 5th Boost, and the dialog's amount equals the
 * amount that Boost was expected to reach. Anything else is never guessed.
 */
object LateMaxDialogPolicy {
    val LATE_REASONS=OpaqueRecoveryPolicy.IN_CARD_TIMEOUTS+"completion_not_confirmed"
    fun pick(rows:List<LateMaxRow>,today:String,dialogReward:Int?):String? {
        if(dialogReward==null||dialogReward<=0)return null
        val pending=rows.filter{it.state in setOf("UNCONFIRMED","AD_OPENING")&&!it.isolated}
        if(pending.size!=1)return null
        // A task already at max with the same amount could own a stale dialog.
        if(rows.any{it.state in setOf("BOOST_MAX_CONFIRMED","BOOST_MAX_OBSERVED")&&it.offeredReward==dialogReward})return null
        val r=pending.single()
        return r.key.takeIf{r.state=="UNCONFIRMED"&&r.date==today&&r.joined&&r.boost==4&&r.adsCount>0&&r.lastIndex==r.adsCount&&
            !r.lastInitial&&!r.lastConfirmed&&r.departed&&r.dispatchPhase=="DISPATCHED"&&r.reason in LATE_REASONS&&
            r.expectedAfter==dialogReward}
    }
}
