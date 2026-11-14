package com.anapocket.auto

/**
 * Which ガラガラ opportunity a new run may work on. 11/14 12:36: lottery1 was START_SUBMITTED with its result hidden
 * (review modal), so every later run stopped at the read-only home review and the second daily draw never ran.
 * A submitted draw has used its own chance; the second chance is a different opportunity and the official page
 * still decides whether it is available. lottery1 itself is never reopened or redrawn.
 */
object RakutenLotteryPolicy {
    fun next(l1Done:Boolean,l1Reserved:Boolean,l1Phase:String,l2Done:Boolean):String? {
        val l1Consumed=l1Done||l1Reserved&&l1Phase=="START_SUBMITTED"
        return when {
            !l1Consumed->"lottery1"
            !l2Done->"lottery2"
            else->null
        }
    }
}
