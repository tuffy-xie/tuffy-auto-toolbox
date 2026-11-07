package com.anapocket.auto

/** Finite recovery of one locally bound Boost ad; an unknown outcome never permits a new ad. */
object OpaqueRecoveryPolicy {
    /** Exact SDK + node shape grants observation, never a click or receipt. */
    fun stallSurface(activity:String,visibleIds:Set<String>):Boolean =
        setOf("playable","application-canvas").all{it in visibleIds} ||
            activity=="com.applovin.adview.AppLovinFullscreenActivity"&&setOf("root","image").all{it in visibleIds}
    // Android rejects screenshot requests <=333ms apart. Only the exception probe waits.
    fun stallCaptureDelay(lastVisualRequestAt:Long,now:Long):Long =
        if(lastVisualRequestAt<=0||now<lastVisualRequestAt)0L else (350L-(now-lastVisualRequestAt)).coerceAtLeast(0L)
    const val STALL_QUIET_MS=30_000L
    const val STALL_SAMPLE_MS=15_000L
    fun freshStallReview(quietMs:Long,finishedAt:Long,now:Long)=
        quietMs>=STALL_QUIET_MS&&finishedAt>0&&now>=finishedAt&&now-finishedAt<=60_000L
    /** Small sampled image values, never retained Bitmaps. Minor pixel noise is not progress. */
    fun visualProgress(previous:IntArray,current:IntArray):Boolean {
        if(previous.size!=256||current.size!=256)return true
        return previous.indices.count { i->
            val a=previous[i];val b=current[i]
            kotlin.math.abs((a shr 16 and 255)-(b shr 16 and 255))>=24||
                kotlin.math.abs((a shr 8 and 255)-(b shr 8 and 255))>=24||
                kotlin.math.abs((a and 255)-(b and 255))>=24
        }>=8
    }
    fun recoveryAlreadyUsed(stage: String, attempts: Int): Boolean = stage.isNotBlank() || attempts >= 2
    fun mayRecoverLoadFailure(initial: Boolean, observedFailure: Boolean, bound: Boolean, alreadyTried: Boolean,
        verifiedFailures: Int, boost: Int, steps: List<Int>): Boolean =
        !initial && observedFailure && bound && !alreadyTried && verifiedFailures == 0 &&
            boost in 0..4 && steps.size == 5 && steps.all { it > 0 }
    fun mayRecover(initial: Boolean, timedOut: Boolean, departed: Boolean, sameRun: Boolean,
        opaque: Boolean, alreadyTried: Boolean, verifiedFailures: Int, boost: Int, steps: List<Int>): Boolean =
        !initial && timedOut && departed && sameRun && opaque && !alreadyTried && verifiedFailures == 0 &&
            boost in 0..4 && steps.size == 5 && steps.all { it > 0 }
    fun unlockedAnaCard(description: String): Boolean = description.trim() in setOf(
        "ANA Pocket,未加锁", "ANA Pocket,Unlocked", "ANA Pocket,ロック解除")
    fun outcome(boost: Int, before: Int, expected: Int, originalSteps: List<Int>, labels: List<String>): String? {
        if (boost !in 0..4 || originalSteps.size != 5 || ChallengeSignals.increments(labels) != originalSteps ||
            before + originalSteps[boost] != expected) return null
        val next = ChallengeSignals.nextBoost(labels)
        val offered = ChallengeSignals.offeredReward(labels)
        return when {
            next == boost + 1 && offered == before -> "NO_GAIN"
            offered == expected && (next == boost + 2 || boost == 4 && next == null) -> "ADVANCED"
            else -> null
        }
    }
}
