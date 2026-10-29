package com.anapocket.auto

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager

/**
 * 运行期间挂一个 1×1 透明悬浮窗。
 *
 * 目的：Android 10+ 限制后台应用启动 Activity（本项目需要在定时任务里把 ANA Pocket
 * 带到前台）。拥有「可见窗口」的应用属于系统豁免情形，因此这里只保留一个像素的窗口，
 * 运行结束立即移除，不做任何绘制或遮挡。
 */
class OverlayKeeper(private val ctx: Context) {
    private var view: View? = null
    private var wm: WindowManager? = null
    private val main = android.os.Handler(android.os.Looper.getMainLooper())

    fun show() {
        if (view != null) return
        if (!Settings.canDrawOverlays(ctx)) {
            Logger.w("OVL", "overlay permission missing; background launch may fail")
            return
        }
        // WindowManager.addView 必须在有 Looper 的线程（主线程）执行，
        // 否则会抛 "Can't create handler inside thread that has not called Looper.prepare()"
        main.post {
            if (view != null) return@post
            try {
                val m = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
                val v = View(ctx)
                val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                } else {
                    @Suppress("DEPRECATION")
                    WindowManager.LayoutParams.TYPE_PHONE
                }
                val lp = WindowManager.LayoutParams(
                    1, 1, type,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                    PixelFormat.TRANSLUCENT
                )
                lp.gravity = Gravity.TOP or Gravity.START
                lp.x = 0
                lp.y = 0
                m.addView(v, lp)
                view = v
                wm = m
                Logger.i("OVL", "1px overlay attached")
            } catch (t: Throwable) {
                Logger.w("OVL", "attach failed: ${t.message}")
            }
        }
    }

    fun hide() {
        main.post {
            try {
                view?.let { wm?.removeView(it) }
                Logger.i("OVL", "overlay removed")
            } catch (_: Throwable) {
            }
            view = null
            wm = null
        }
    }
}
