package com.anapocket.auto
object CheckinDispatchPolicy {
    fun accepts(enabled:Boolean,currentGeneration:Long,callbackGeneration:Long,dateMatches:Boolean=true)=enabled&&currentGeneration==callbackGeneration&&dateMatches
}
