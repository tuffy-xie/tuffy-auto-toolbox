package com.anapocket.auto

import android.content.Context

/** Explicit ten-minute diagnostic session. Never enables itself on a task or service restart. */
object ScreenshotBudget {
    const val DURATION = 10 * 60 * 1000L
    const val MAX_IMAGES = 12
    const val MAX_BYTES = 32L * 1024 * 1024
    fun allowed(now:Long,until:Long,images:Int,bytes:Long):Boolean =
        until > now && until-now <= DURATION && images in 0 until MAX_IMAGES && bytes in 0 until MAX_BYTES
}
object ScreenshotDiagnostics {
    private fun prefs(c:Context)=c.applicationContext.getSharedPreferences("h14_screenshot_diagnostics",0)
    @Synchronized fun start(c:Context) {
        check(prefs(c).edit().putLong("until",System.currentTimeMillis()+ScreenshotBudget.DURATION)
            .putInt("images",0).putLong("bytes",0).commit()) { "diagnostics_write_failed" }
    }
    @Synchronized fun stop(c:Context) { check(prefs(c).edit().putLong("until",0).commit()) }
    @Synchronized fun enabled(c:Context):Boolean {val p=prefs(c);return ScreenshotBudget.allowed(System.currentTimeMillis(),
        p.getLong("until",0),p.getInt("images",0),p.getLong("bytes",0))}
    @Synchronized fun reserve(c:Context):Boolean {
        if(!enabled(c))return false
        val p=prefs(c);check(p.edit().putInt("images",p.getInt("images",0)+1).commit());return true
    }
    @Synchronized fun charge(c:Context,bytes:Int):Boolean {
        val p=prefs(c);val total=p.getLong("bytes",0)+bytes
        if(bytes<0 || total>ScreenshotBudget.MAX_BYTES)return false
        check(p.edit().putLong("bytes",total).commit());return true
    }
}
