package com.anapocket.auto

enum class ChallengeScanStatus { NOT_SCANNED, SCANNING, SCAN_FAILED, NO_ELIGIBLE_TASK, TASKS_AVAILABLE, PARTIALLY_COMPLETED, COMPLETED, BLOCKED }
object ChallengeScanPolicy {
    fun verifiedJoinableEnd(stable:Int,canForward:Boolean,loading:Boolean,observedTitles:Int,officialEmpty:Boolean)=
        stable>=3&&!canForward&&!loading&&(observedTitles>0||officialEmpty)
    fun reusable(status: ChallengeScanStatus, scannedKm: Int, currentKm: Int, bothLists: Boolean, pending: Boolean): Boolean =
        status in setOf(ChallengeScanStatus.COMPLETED, ChallengeScanStatus.NO_ELIGIBLE_TASK) && bothLists &&
            currentKm > 0 && scannedKm == currentKm && !pending
    fun finish(exhausted: Boolean, participating: Boolean, stopped: Boolean, sameDate: Boolean, eligible: Int, pending: Boolean): ChallengeScanStatus = when {
        stopped || !sameDate || !exhausted || !participating -> ChallengeScanStatus.SCAN_FAILED
        pending -> ChallengeScanStatus.PARTIALLY_COMPLETED
        eligible == 0 -> ChallengeScanStatus.NO_ELIGIBLE_TASK
        else -> ChallengeScanStatus.COMPLETED
    }
    fun mayReset(running: Boolean, unresolved: Boolean, sdk: Boolean) = !running && !unresolved && !sdk
    fun label(status: ChallengeScanStatus) = when(status) {
        ChallengeScanStatus.NOT_SCANNED -> "尚未扫描"
        ChallengeScanStatus.SCANNING -> "正在扫描"
        ChallengeScanStatus.SCAN_FAILED -> "扫描失败，可重试"
        ChallengeScanStatus.NO_ELIGIBLE_TASK -> "当前官方暂无符合所选条件的可接取任务"
        ChallengeScanStatus.TASKS_AVAILABLE -> "存在可接任务"
        ChallengeScanStatus.PARTIALLY_COMPLETED -> "部分完成"
        ChallengeScanStatus.COMPLETED -> "可接取列表扫描完成，Boost另行核对"
        ChallengeScanStatus.BLOCKED -> "扫描受阻，需核对"
    }
}

/** Equality to today's native task date, never <= today or the in-progress total. */
object ParticipatingDayPolicy {
    fun current(nativeTaskDate:String?,date:String)=DailyCardDeadline.matches(nativeTaskDate,date)
    /** Required keys expand read-only recovery inspection, never the user's acquisition selection. */
    fun inspect(nativeTaskDate:String?,date:String,key:String,selected:Boolean,meters:Int?,maxMeters:Int,requiredKeys:Set<String>) =
        current(nativeTaskDate,date)&&meters!=null&&(key in requiredKeys||selected&&meters<=maxMeters)
}

/** Preserving reconciliation is distinct from resetting a disposable scan projection. */
object ChallengeScanEntryPolicy {
    fun preservingReview(challengePending:Boolean,running:Boolean,dailyPending:Boolean,sdk:Boolean,risk:Boolean)=
        challengePending&&!running&&!dailyPending&&!sdk&&!risk
}
