package com.anapocket.auto

/** Fresh native load-failure proof grants a bounded replacement; original rows remain FAILED. */
object OfficialLoadFailurePolicy {
    fun key(a:AdAttempt)=a.runId+":"+a.index+":"+a.startedAt
    fun failedLoad(a:AdAttempt)=a.terminal&&a.state==AdState.FAILED&&!a.completed&&!a.rewardConfirmed&&
        a.failureReason=="official_error_dialog"&&a.officialBefore in 0..24
    fun resolved(a:AdAttempt,reviewed:Set<String>)=
        a.terminal&&a.state==AdState.REWARD_CONFIRMED&&a.completed&&a.rewardConfirmed||failedLoad(a)&&key(a) in reviewed
    fun mayReview(rows:List<AdAttempt>,date:String,today:String,counter:Int?,nativeProof:Boolean,elapsed:Long,reviewed:Set<String>):Boolean {
        val a=rows.lastOrNull()?:return false
        return date==today&&a.date==date&&nativeProof&&elapsed>=30_000&&reviewed.size<2&&key(a) !in reviewed&&
            failedLoad(a)&&counter==a.officialBefore&&rows.dropLast(1).all{it.date==date&&resolved(it,reviewed)}
    }
    fun runCap(base:Int,initial:Int,current:Int)=base+(current-initial).coerceIn(0,2)
    fun nativeAlert(activity:String,lines:List<String>)=activity.endsWith("AlertDialog")&&
        lines.any{it.contains("id=us.ana_pocket.ANAPocket:id/alertTitle ")&&it.endsWith("label=広告表示に失敗しました。")}&&
        lines.any{it.contains("id=android:id/message ")&&it.contains("アプリの再起動をお試しください")}
    fun startCap(reviewed:Int)=25+if(reviewed in 0..2)reviewed else 0
    fun canReserve(counter:Int?,previous:Int?,sameDate:Boolean,stopped:Boolean,rows:List<AdAttempt>,reviewed:Set<String>)=
        sameDate&&!stopped&&counter!=null&&counter in 0..24&&(previous==null||counter>=previous)&&
        reviewed.size in 1..2&&reviewed.all{k->rows.any{key(it)==k&&failedLoad(it)}}&&
        rows.size<startCap(reviewed.size)&&rows.all{resolved(it,reviewed)}
    fun routineAllowed(limit:Boolean,blocked:String,own:List<AdAttempt>,reviewed:Set<String>)=
        reviewed.size<=2&&RoutineAdCompletion.allowed(limit,blocked,own.filterNot{failedLoad(it)&&key(it) in reviewed}.map{it.completed to it.rewardConfirmed})
}
