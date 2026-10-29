package com.anapocket.auto

/** Observation only: never authorizes a click, a retry or a reward. */
class DiagnosticCapturePolicy {
    private var captures = 0
    private var last = -100_000L
    fun shouldCapture(elapsed: Long, adSurface: Boolean, waiting: Boolean): Boolean {
        if (!waiting || captures >= 3 || elapsed < (if (adSurface) 45_000L else 15_000L) || elapsed-last < 30_000L) return false
        captures++; last=elapsed; return true
    }
}

object DiagnosticPrivacy {
    fun redact(value:String):String = value.lineSequence().filterNot {
        it.contains("generated",true) && it.contains("token",true)
    }.joinToString("\n")
        .replace(Regex("[0-9]{5,16}:[A-Za-z0-9_-]{20,}"),"[REDACTED_BOT_TOKEN]")
        .replace(Regex("(?i)((?:token|password|authorization|chat_?id)\\s*[=:]\\s*\\\"?)[^\\s\\\",;]+"),"$1[REDACTED]")
    fun canCapture(pkg:String?,locked:Boolean)=!locked && pkg in setOf(StartupPolicy.ANA,"com.android.vending")
    fun safeName(value:String)=value.replace(Regex("[^A-Za-z0-9_.-]"),"_").take(100).trim('.') .ifBlank{"unknown"}
}

object TaskExitPolicy {
    fun allowed(finished:Boolean,stopped:Boolean,pending:Boolean,adSurface:Boolean,anaForeground:Boolean)=
        finished&&!stopped&&!pending&&!adSurface&&anaForeground
}
