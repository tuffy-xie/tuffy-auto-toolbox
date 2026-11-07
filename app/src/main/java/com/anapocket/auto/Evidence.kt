package com.anapocket.auto

import android.content.Context
import java.io.File

/**
 * 证据落盘：把设置快照、队列快照、运行结果写成 JSON 文本，
 * 便于 adb pull 后在验收报告里逐项核对（不依赖日志措辞）。
 */
object Evidence {
    private fun dir(ctx: Context): File {
        val d = File(ctx.getExternalFilesDir(null) ?: ctx.filesDir, "evidence")
        if (!d.exists()) d.mkdirs()
        return d
    }

    fun write(ctx: Context, name: String, content: String) {
        try {
            File(dir(ctx), name).writeText(content)
        } catch (t: Throwable) {
            Logger.w("EVID", "write $name failed: ${t.message}")
        }
    }

    fun writeBytes(ctx: Context, name: String, content: ByteArray) {
        if(name.endsWith(".png",true) && (!ScreenshotDiagnostics.reserve(ctx)||!ScreenshotDiagnostics.charge(ctx,content.size)))return
        try {
            File(dir(ctx), name).writeBytes(content)
        } catch (t: Throwable) {
            Logger.w("EVID", "write bytes $name failed: ${t.message}")
        }
    }

    suspend fun capture(ctx:Context,ui:Ui,name:String) {
        if(!ScreenshotDiagnostics.reserve(ctx))return
        val png=ui.captureVisual(includePng=true).png?:return
        if(ScreenshotDiagnostics.charge(ctx,png.size))runCatching{File(dir(ctx),name).writeBytes(png)}
    }
    fun path(ctx: Context, name: String): String = File(dir(ctx), name).absolutePath
}
