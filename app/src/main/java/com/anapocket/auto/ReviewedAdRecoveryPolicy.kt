package com.anapocket.auto

/** Ignore only already-proved native load failures when binding the same final unknown attempt. */
object ReviewedAdRecoveryPolicy {
    fun mayBind(rows:List<AdAttempt>,date:String,today:String,counter:Int?,departure:Boolean,reviewed:Set<String>):Boolean {
        val last=rows.lastOrNull()?:return false
        val prior=rows.dropLast(1)
        if(reviewed.size !in 1..2||reviewed.any{k->prior.none{it.date==date&&OfficialLoadFailurePolicy.key(it)==k&&OfficialLoadFailurePolicy.failedLoad(it)}})return false
        val remaining=prior.filterNot{OfficialLoadFailurePolicy.failedLoad(it)&&OfficialLoadFailurePolicy.key(it) in reviewed}+last
        return OfficialAdRecoveryPolicy.mayBind(remaining,date,today,counter,departure)
    }
}
