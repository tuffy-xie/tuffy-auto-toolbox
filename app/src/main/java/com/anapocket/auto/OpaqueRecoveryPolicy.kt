package com.anapocket.auto

/** Finite recovery of one locally bound Boost ad; an unknown outcome never permits a new ad. */
object OpaqueRecoveryPolicy {
    /** Exact SDK + node shape grants observation, never a click or receipt. */
    fun stallSurface(activity:String,visibleIds:Set<String>):Boolean =
        setOf("playable","application-canvas").all{it in visibleIds} ||
            setOf("root","interactive-html-stage").all{it in visibleIds} ||
            activity=="com.applovin.adview.AppLovinFullscreenActivity"&&setOf("root","image").all{it in visibleIds} ||
            // 11/11 03:55: Unity web end card with only store CTAs, no close, ignoring Back for 50+ min.
            activity=="com.unity3d.ads.adplayer.FullScreenWebViewDisplay"&&setOf("root","pixiview").all{it in visibleIds}
    /** First failed probe precondition, for the log only. Same gate as before; it authorizes nothing. */
    fun stallProbeFailure(sdk:Int,foreground:String?,expected:String,activity:String,visibleIds:Set<String>):String? = when {
        sdk<30->"sdk_below_30"
        foreground==null->"foreground_root_missing"
        foreground!=expected->"foreground_not_ana"
        !stallSurface(activity,visibleIds)->"surface_shape_mismatch"
        else->null
    }
    /** One bounded line of ids and window flags, never labels. Unrelated apps and locked screens stay anonymous. */
    fun stallShape(foreground:String?,capturable:Boolean,activity:String,visibleIds:Set<String>,windows:List<String>):String {
        val w="windows="+windows.size+"["+windows.take(6).joinToString(",")+"]"
        if(!capturable)return "fg=unrelated_or_locked "+w
        val ids=visibleIds.map{it.substringAfterLast('/')}.toSet()
        val required=listOf("playable","application-canvas","root","image").joinToString(","){it+"="+(it in ids)}
        return "fg="+foreground+" activity="+activity.take(80)+" required["+required+"] ids="+ids.size+"["+
            ids.sorted().take(12).joinToString(","){it.take(32)}+"] "+w
    }
    // Android rejects screenshot requests <=333ms apart. Only the exception probe waits.
    fun stallCaptureDelay(lastVisualRequestAt:Long,now:Long):Long =
        if(lastVisualRequestAt<=0||now<lastVisualRequestAt)0L else (350L-(now-lastVisualRequestAt)).coerceAtLeast(0L)
    const val STALL_QUIET_MS=30_000L
    const val STALL_SAMPLE_MS=15_000L
    fun freshStallReview(quietMs:Long,finishedAt:Long,now:Long)=
        quietMs>=STALL_QUIET_MS&&finishedAt>0&&now>=finishedAt&&now-finishedAt<=60_000L
    /** Short stable digest of a node-tree description; labels are never stored. */
    fun treeDigest(text:String):String=java.security.MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
        .take(12).joinToString(""){"%02x".format(it)}
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
    /**
     * 11/19 (Gemini review B/G): the in-card restart used to need one of four canvas shapes, so every AppLovin playable
     * (ids playable/canvas/animation_container, or ab/bg/overlay-portrait) fell through to the whole-step BLOCKED path.
     * Any timeout of this attempt qualifies when the same attempt carries frozen proof (canvas sample or node-tree digest),
     * or when the 300 s absolute cap was reached with the ad window still open (that cap never needed a review).
     */
    val IN_CARD_TIMEOUTS=setOf("ad_timeout_95s","ad_part_timeout_95s","ad_timeout_300s")
    fun inCardTimeout(reason:String)=reason in IN_CARD_TIMEOUTS
    fun reviewRequired(reason:String)=reason!="ad_timeout_300s"
    fun stallEvidence(canvasSurface:Boolean,frozenReviewed:Boolean,reason:String,adWindowOpen:Boolean)=
        canvasSurface||frozenReviewed||reason=="ad_timeout_300s"&&adWindowOpen
    /**
     * Tier 4 (user-approved 11/19): one system Back for one attempt, only after the segment has played at least 65 s and
     * the canvas or node tree has stayed unchanged for 30 s while an ad window is still in front. Returning is never a
     * receipt; only ANA's own official receipt can confirm the attempt, and the restart path stays behind it.
     */
    const val STALL_BACK_MIN_PLAY_MS=65_000L
    fun mayStallBack(partElapsedMs:Long,quietMs:Long,adSurface:Boolean,used:Boolean)=
        !used&&adSurface&&partElapsedMs>=STALL_BACK_MIN_PLAY_MS&&quietMs>=STALL_QUIET_MS
    const val STALL_BACK_GRACE_MS=10_000L
    /**
     * 11/27 00:29, 02:24, 02:35: three first joins on one AppLovin playable (full-screen game canvas, a small corner video
     * with 「Close video」, no ad close ever shown) waited the 300 s cap; the canvas kept moving so no quiet proof came.
     * User-approved: for this shape only, the single Back after 65 s of play without the 30 s quiet requirement.
     */
    fun animatedVideoPlayable(ids:Set<String>,buttonLabels:List<String>)=
        setOf("playable","application-canvas","ad_video").all{it in ids}&&buttonLabels.any{it.trim()=="Close video"}
    fun mayAnimatedPlayableBack(partElapsedMs:Long,adSurface:Boolean,animated:Boolean,used:Boolean)=
        !used&&adSurface&&animated&&partElapsedMs>=STALL_BACK_MIN_PLAY_MS
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
