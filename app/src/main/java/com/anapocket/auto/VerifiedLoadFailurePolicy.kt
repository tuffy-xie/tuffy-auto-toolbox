package com.anapocket.auto

/** A second distinct official load failure may be reviewed only after the first has native no-gain proof. */
object VerifiedLoadFailurePolicy {
    fun mayReviewSecond(initial:Boolean,observedLoadError:Boolean,bound:Boolean,alreadyReviewed:Boolean,
        verifiedFailures:Int,verifiedLoadFailures:Int,boost:Int,steps:List<Int>):Boolean =
        !initial&&observedLoadError&&bound&&!alreadyReviewed&&verifiedFailures==1&&verifiedLoadFailures==1&&
            boost in 0..4&&steps.size==5&&steps.all{it>0}

    /** Legacy opaque/unknown handling stays capped; only two proved load failures earn two replacements. */
    fun startLimit(verifiedFailures:Int,verifiedLoadFailures:Int):Int =
        if(verifiedFailures==2&&verifiedLoadFailures==2)8 else if(verifiedFailures==1)7 else 6
}
