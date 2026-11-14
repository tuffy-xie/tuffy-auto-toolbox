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

data class FaultDir(val name:String,val date:String,val modified:Long,val bytes:Long)

/**
 * One busy day must not silence later days. Total size stays bounded; when it is full the oldest fault
 * folders make room (11/10: the folder had been full since earlier days, so the 40km stall left no evidence).
 */
object FaultBudgetPolicy {
    /**
     * 11/11: the cap of 60 was used up by 02:56 and every later failure of the night (walk stalls, TikTok) left
     * nothing. The cap is now a ring: at the cap today's own oldest folder makes room, so the newest failure is
     * always kept and earlier days are not touched by one busy night.
     */
    const val MAX_DIRS_PER_DAY=200
    const val MAX_TOTAL_BYTES=256L*1024*1024
    /** Room kept free for the capture about to be written (log tail, nodes, manifest, one screenshot). */
    const val CAPTURE_RESERVE_BYTES=8L*1024*1024
    /** Log tail per fault folder; the full 512 KB tail is still in every export and on the phone's own log. */
    const val CAPTURE_LOG_BYTES=192L*1024
    fun hasRoom(dirDates:List<String>,today:String,totalBytes:Long)=
        dirDates.count{it==today}<MAX_DIRS_PER_DAY&&totalBytes<MAX_TOTAL_BYTES
    /**
     * Folders to delete so a new capture fits. First today's oldest ones while today is at its cap, then by total
     * size: oldest first, today's folders only after every older day's.
     */
    fun evict(dirs:List<FaultDir>,today:String,looseBytes:Long=0):List<FaultDir> {
        val out=mutableListOf<FaultDir>()
        val todays=dirs.filter{it.date==today}.sortedWith(compareBy<FaultDir>{it.modified}.thenBy{it.name})
        val over=todays.size-(MAX_DIRS_PER_DAY-1)
        if(over>0)out.addAll(todays.take(over))
        var total=looseBytes+dirs.sumOf{it.bytes}-out.sumOf{it.bytes}
        for(d in dirs.filter{it !in out}.sortedWith(compareBy<FaultDir>{it.date==today}.thenBy{it.modified}.thenBy{it.name})){
            if(total<=MAX_TOTAL_BYTES-CAPTURE_RESERVE_BYTES)break
            out.add(d);total-=d.bytes
        }
        return out
    }
}

object TaskExitPolicy {
    fun canLock(returnedHome:Boolean,stopped:Boolean,pending:Boolean,sameRun:Boolean)=returnedHome&&!stopped&&!pending&&sameRun
    fun lockConfirmed(screenOff:Boolean,keyguardLocked:Boolean)=screenOff&&keyguardLocked
    fun allowed(finished:Boolean,stopped:Boolean,pending:Boolean,adSurface:Boolean,anaForeground:Boolean)=
        finished&&!stopped&&!pending&&!adSurface&&anaForeground
}

/** A notification can wake a locked phone shortly after the system lock action. */
class LockScreenConfirmation(private val stableMillis:Long=2500L) {
    private var since:Long?=null
    fun observe(now:Long,screenOff:Boolean,keyguardLocked:Boolean):Boolean {
        if(!TaskExitPolicy.lockConfirmed(screenOff,keyguardLocked)){since=null;return false}
        if(since==null||now<since!!)since=now
        return now-since!!>=stableMillis
    }
}
