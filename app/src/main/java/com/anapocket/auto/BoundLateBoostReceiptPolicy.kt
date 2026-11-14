package com.anapocket.auto

/** Reconciles an existing attempt from an exact native receipt; never authorizes an ad start. */
object BoundLateBoostReceiptPolicy {
    /** A dispatched attempt with an official binding is checked against the official identity before it commits. */
    fun departed(observed:Boolean,officialBinding:Boolean,dispatchPhase:String)=observed||officialBinding&&dispatchPhase=="DISPATCHED"
    fun matches(taskState:String,adState:String,reason:String,confirmed:Boolean,initial:Boolean,
        index:Int,attempts:Int,pendingCount:Int,sameDate:Boolean,departed:Boolean,
        boost:Int,before:Int,expected:Int,steps:List<Int>,nativePanel:Boolean,labels:List<String>):Boolean =
        nativePanel&&sameDate&&pendingCount==1&&departed&&!initial&&boost in 0..4&&
            before>0&&steps.size==5&&steps.all{it>0}&&expected==before+steps[boost]&&
            ChallengeRecoveryPolicy.canResume(taskState,adState,reason,confirmed,index,attempts,true)&&
            OpaqueRecoveryPolicy.outcome(boost,before,expected,steps,labels)=="ADVANCED"
}
