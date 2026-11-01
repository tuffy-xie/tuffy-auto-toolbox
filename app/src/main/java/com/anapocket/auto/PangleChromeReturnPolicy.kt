package com.anapocket.auto

/** One normal Back to the same observed Pangle ad, never a new ad start or reward inference. */
class PangleChromeReturnPolicy {
    private var lastNative:Long?=null
    private var browserSince:Long?=null
    private var used=false
    fun shouldBack(now:Long,nativePangle:Boolean,chromePage:Boolean,live:Boolean):Boolean {
        if(!live){lastNative=null;browserSince=null;return false}
        if(nativePangle){lastNative=now;browserSince=null;return false}
        if(!chromePage){lastNative=null;browserSince=null;return false}
        if(used)return false
        val last=lastNative?:return false
        if(browserSince==null){
            if(now-last !in 0L..5_000L){lastNative=null;return false}
            browserSince=now
        }
        val elapsed=now-browserSince!!
        if(elapsed !in 2_000L..30_000L)return false
        used=true
        return true
    }
}
