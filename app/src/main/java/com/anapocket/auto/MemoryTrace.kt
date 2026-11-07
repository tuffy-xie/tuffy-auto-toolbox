package com.anapocket.auto

import android.app.ActivityManager
import android.content.Context
import android.os.Debug
import android.os.Process
import java.io.File
import java.util.concurrent.atomic.AtomicLong
import org.json.JSONObject

/** Bounded structured self/system samples, no screenshots or cross-app privilege. No historical file deletion. */
object MemoryTrace {
    private val captures=AtomicLong();private val encodes=AtomicLong();private val bitmapBytes=AtomicLong()
    @Volatile private var last=0L
    fun imageRequested(png:Boolean){captures.incrementAndGet();if(png)encodes.incrementAndGet()}
    fun bitmapAllocated(bytes:Int){bitmapBytes.addAndGet(bytes.toLong())}
    @Synchronized fun record(c:Context,phase:String,force:Boolean=false){
        val now=System.currentTimeMillis();if(!force&&now-last<60_000)return;last=now
        runCatching{
            val file=File(c.filesDir,"h14_memory.jsonl");if(file.length()>2L*1024*1024)return
            val info=Debug.MemoryInfo();Debug.getMemoryInfo(info)
            val system=ActivityManager.MemoryInfo();(c.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(system)
            val runtime=Runtime.getRuntime();val gc=Debug.getRuntimeStats()
            val row=JSONObject().put("phoneTime",TokyoDate.time()).put("epoch",now).put("pid",Process.myPid()).put("phase",phase.take(80))
                .put("pssKiB",info.totalPss).put("rssKiB",info.getMemoryStat("summary.total-rss"))
                .put("javaHeapPssKiB",info.getMemoryStat("summary.java-heap")).put("nativePssKiB",info.getMemoryStat("summary.native-heap"))
                .put("graphicsKiB",info.getMemoryStat("summary.graphics")).put("javaAllocatedBytes",runtime.totalMemory()-runtime.freeMemory())
                .put("nativeAllocatedBytes",Debug.getNativeHeapAllocatedSize()).put("systemAvailableBytes",system.availMem)
                .put("systemTotalBytes",system.totalMem).put("systemLowMemory",system.lowMemory).put("gcCount",gc["art.gc.gc-count"])
                .put("gcTime",gc["art.gc.gc-time"]).put("visualRequests",captures.get()).put("pngEncodes",encodes.get())
                .put("softwareScreenshotBitmapAllocatedBytes",bitmapBytes.get())
            file.appendText(row.toString()+"\n")
        }.onFailure{Logger.w("MEMORY","sample unavailable: "+it.javaClass.simpleName)}
    }
}
