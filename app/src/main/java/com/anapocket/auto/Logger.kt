package com.anapocket.auto

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 结构化运行日志：同时写入 logcat 与 App 私有外部目录，便于 adb 拉取作为真机证据。
 */
object Logger {
    private val fmt = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US)
    /** In-memory tail for the diagnostics card and the file-less fallback only; the file keeps everything. */
    private const val MEMORY_LINES = 500
    private const val MEMORY_LINE_CHARS = 600
    private val lines = ArrayDeque<String>(MEMORY_LINES)

    @Volatile
    private var file: File? = null

    @Volatile
    private var inited = false

    fun init(ctx: Context) {
        if (inited) return
        synchronized(this) {
            if (inited) return
            val dir = File(ctx.getExternalFilesDir(null) ?: ctx.filesDir, "logs")
            if (!dir.exists()) dir.mkdirs()
            file = File(dir, "ana_auto.log")
            inited = true
            write("I", "LOG", "logger ready: ${file?.absolutePath}")
        }
    }

    fun i(tag: String, msg: String) = write("I", tag, msg)
    fun w(tag: String, msg: String) = write("W", tag, msg)
    fun e(tag: String, msg: String, t: Throwable? = null) = write("E", tag, msg, t)

    private fun write(level: String, tag: String, msg: String, t: Throwable? = null) {
        val line = synchronized(this) { "${fmt.format(Date())} $level/$tag $msg" }
        when (level) {
            "E" -> Log.e("AnaAuto", "$tag $msg", t)
            "W" -> Log.w("AnaAuto", "$tag $msg")
            else -> Log.i("AnaAuto", "$tag $msg")
        }
        synchronized(this) {
            lines.addLast(if (line.length > MEMORY_LINE_CHARS) line.take(MEMORY_LINE_CHARS) + "…" else line)
            while (lines.size > MEMORY_LINES) lines.removeFirst()
        }
        try {
            file?.appendText(line + "\n")
        } catch (_: Throwable) {
            // 忽略磁盘写入异常，logcat 仍然可用
        }
    }

    fun tail(n: Int): String = synchronized(this) { lines.takeLast(n).joinToString("\n") }

    fun path(): String = file?.absolutePath ?: "(未初始化)"

    fun clear() {
        synchronized(this) { lines.clear() }
        try {
            file?.writeText("")
        } catch (_: Throwable) {
        }
    }
}
