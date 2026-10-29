package com.anapocket.auto

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.view.Display
import android.view.accessibility.AccessibilityNodeInfo
import androidx.annotation.RequiresApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.io.ByteArrayOutputStream
import kotlin.coroutines.resume

data class UiVisualCapture(val visual: DetailVisual, val png: ByteArray? = null)

/**
 * 无障碍 UI 操作封装：节点查找、点击（含父节点回溯与手势兜底）、滑动、启动应用、UI 树导出。
 */
class Ui(private val svc: AutoAccessibilityService) {

    companion object {
        /** 只打印一次窗口诊断，避免刷屏 */
        private var DEBUG_WINDOWS = true
    }


    /** Compose may retain virtual child bounds after a gesture; reacquire instead of reusing the stale cache. */
    fun refreshCache() {
        if (Build.VERSION.SDK_INT >= 33) runCatching { svc.clearCache() }
        else runCatching { svc.rootInActiveWindow?.refresh() }
    }
    fun root(): AccessibilityNodeInfo? = svc.rootInActiveWindow

    /**
     * 所有窗口的根节点。
     *
     * 为什么需要：ANA Pocket 的挑战详情是 BottomSheet，真机实测
     * rootInActiveWindow 只返回了说明文字所在的那一棵，
     * 参加按钮所在的 WebView 在另一个窗口里，导致「按钮不存在」。
     * 必须遍历 windows 才能拿全。
     */
    fun roots(): List<AccessibilityNodeInfo> {
        val out = ArrayList<AccessibilityNodeInfo>(4)
        try {
            for (w in svc.windows) {
                val r = try { w.root } catch (t: Throwable) { null } ?: continue
                out.add(r)
            }
        } catch (t: Throwable) {
            Logger.w("UI", "roots() failed: ${t.message}")
        }
        if (out.isEmpty()) root()?.let { out.add(it) }
        return out
    }

    fun snapshot(maxNodes: Int = 2000): List<AccessibilityNodeInfo> {
        val out = ArrayList<AccessibilityNodeInfo>(256)
        val rs = roots()
        // 注意：这里**不能**用 Thread.sleep 重试。
        // 无障碍服务的回调跑在主线程，阻塞会让 dispatchGesture 的滑动全部失效
        // （R12 实测：加了 sleep 后列表完全滚不动，discovered 从 20 掉到 0）。
        // 空树重试交给协程调用方用 delay() 处理。
        if (rs.isEmpty()) return out

        fun walk(n: AccessibilityNodeInfo?, depth: Int) {
            if (n == null || out.size >= maxNodes || depth > 40) return
            out.add(n)
            var i = 0
            while (i < n.childCount) {
                walk(n.getChild(i), depth + 1)
                i++
            }
        }
        for (r in rs) walk(r, 0)
        return out
    }

    /**
     * 逐窗口快照：详情识别必须保留 active/focused/layer，
     * 不能像 snapshot() 一样把前台详情和后台列表混成一个标签池。
     */
    fun detailWindows(maxNodesPerWindow: Int = 1200): List<DetailWindowData> {
        val out = ArrayList<DetailWindowData>(4)
        val windows = try { svc.windows } catch (t: Throwable) { emptyList() }
        for (w in windows) {
            val r = try { w.root } catch (t: Throwable) { null } ?: continue
            val labels = ArrayList<String>(64)
            var count = 0
            fun walk(n: AccessibilityNodeInfo?, depth: Int) {
                if (n == null || count >= maxNodesPerWindow || depth > 40) return
                count++
                val text = label(n)
                if (text.isNotEmpty()) labels.add(text)
                var i = 0
                while (i < n.childCount) {
                    walk(n.getChild(i), depth + 1)
                    i++
                }
            }
            walk(r, 0)
            out.add(
                DetailWindowData(
                    packageName = try { r.packageName?.toString().orEmpty() } catch (t: Throwable) { "" },
                    active = try { w.isActive } catch (t: Throwable) { false },
                    focused = try { w.isFocused } catch (t: Throwable) { false },
                    layer = try { w.layer } catch (t: Throwable) { 0 },
                    type = try { w.type } catch (t: Throwable) { 0 },
                    nodeCount = count,
                    labels = labels
                )
            )
        }
        return out
    }

    /** API 30+ 屏幕亮度探针；includePng 仅在诊断证据落盘时开启。 */
    suspend fun captureVisual(
        baseline: DetailVisual? = null,
        includePng: Boolean = false
    ): UiVisualCapture {
        if (Build.VERSION.SDK_INT < 30) {
            return UiVisualCapture(DetailVisual(error = "api<30"))
        }
        return captureVisualApi30(baseline, includePng)
    }

    @RequiresApi(30)
    private suspend fun captureVisualApi30(
        baseline: DetailVisual?,
        includePng: Boolean
    ): UiVisualCapture {
        return withTimeoutOrNull(1800L) {
            suspendCancellableCoroutine<UiVisualCapture> { cont ->
                try {
                    svc.takeScreenshot(
                        Display.DEFAULT_DISPLAY,
                        svc.mainExecutor,
                        object : AccessibilityService.TakeScreenshotCallback {
                            override fun onSuccess(result: AccessibilityService.ScreenshotResult) {
                                if (!cont.isActive) return
                                var bitmap: Bitmap? = null
                                var hardware: Bitmap? = null
                                try {
                                    val buffer = result.hardwareBuffer
                                    hardware = Bitmap.wrapHardwareBuffer(buffer, result.colorSpace)
                                    bitmap = hardware?.copy(Bitmap.Config.ARGB_8888, false)
                                    buffer.close()
                                    val bmp = bitmap
                                    if (bmp == null) {
                                        cont.resume(UiVisualCapture(DetailVisual(error = "bitmapCopyFailed")))
                                        return
                                    }
                                    val visual = analyzeVisual(bmp, baseline)
                                    val png = if (includePng) {
                                        val stream = ByteArrayOutputStream()
                                        bmp.compress(Bitmap.CompressFormat.PNG, 100, stream)
                                        stream.toByteArray()
                                    } else null
                                    cont.resume(UiVisualCapture(visual, png))
                                } catch (t: Throwable) {
                                    if (cont.isActive) {
                                        cont.resume(UiVisualCapture(DetailVisual(error = t.javaClass.simpleName)))
                                    }
                                } finally {
                                    bitmap?.recycle()
                                    if (hardware != null && hardware != bitmap && !hardware.isRecycled) hardware.recycle()
                                }
                            }

                            override fun onFailure(errorCode: Int) {
                                if (cont.isActive) {
                                    cont.resume(UiVisualCapture(DetailVisual(error = "screenshotError:$errorCode")))
                                }
                            }
                        }
                    )
                } catch (t: Throwable) {
                    if (cont.isActive) {
                        cont.resume(UiVisualCapture(DetailVisual(error = t.javaClass.simpleName)))
                    }
                }
            }
        } ?: UiVisualCapture(DetailVisual(error = "screenshotTimeout"))
    }

    private fun analyzeVisual(bitmap: Bitmap, baseline: DetailVisual?): DetailVisual {
        val w = bitmap.width
        val h = bitmap.height
        val step = 8
        var allSum = 0.0
        var allCount = 0
        var topSum = 0.0
        var topCount = 0
        var bottomSum = 0.0
        var bottomCount = 0
        var y = 0
        while (y < h) {
            var x = 0
            while (x < w) {
                val c = bitmap.getPixel(x, y)
                val luma = 0.2126 * ((c shr 16) and 0xff) +
                    0.7152 * ((c shr 8) and 0xff) + 0.0722 * (c and 0xff)
                allSum += luma
                allCount++
                if (y < h * 0.45) {
                    topSum += luma
                    topCount++
                } else if (y > h * 0.55) {
                    bottomSum += luma
                    bottomCount++
                }
                x += step
            }
            y += step
        }
        val mean = if (allCount > 0) allSum / allCount else 0.0
        val top = if (topCount > 0) topSum / topCount else 0.0
        val bottom = if (bottomCount > 0) bottomSum / bottomCount else 0.0
        val dimmed = baseline?.available == true && baseline.meanLuma - mean > 30.0
        val contrastSheet = bottom > 200.0 && bottom - top > 45.0
        val blankLightSheet = top > 210.0 && bottom > 225.0
        return DetailVisual(
            available = true,
            meanLuma = mean,
            topLuma = top,
            bottomLuma = bottom,
            dimmed = dimmed,
            sheetVisible = contrastSheet || blankLightSheet || (dimmed && bottom > 200.0)
        )
    }

    /** 当前 active/focused 的最高层窗口；没有焦点信息时取 layer 最高者。 */
    fun foregroundWindow(): DetailWindowData? {
        val windows = detailWindows()
        return windows.filter { it.active || it.focused }.maxByOrNull { it.layer }
            ?: windows.maxByOrNull { it.layer }
    }

    /** Exact active root; never fall back to a hidden window of the requested package. */
    fun foregroundNodes(): List<AccessibilityNodeInfo> {
        val out = ArrayList<AccessibilityNodeInfo>()
        fun visit(n: AccessibilityNodeInfo?, depth: Int) {
            if (n == null || depth > 40 || out.size >= 2000) return
            out.add(n)
            for (i in 0 until n.childCount) visit(n.getChild(i), depth + 1)
        }
        visit(root(), 0)
        return out
    }

    fun foregroundLabels(): List<String> = foregroundWindow()?.labels ?: emptyList()

    fun label(n: AccessibilityNodeInfo): String {
        val t = n.text?.toString()?.trim().orEmpty()
        if (t.isNotEmpty()) return t
        return n.contentDescription?.toString()?.trim().orEmpty()
    }

    private fun boundsOf(n: AccessibilityNodeInfo): Rect {
        val r = Rect()
        n.getBoundsInScreen(r)
        return r
    }

    fun find(
        candidates: List<String>,
        exact: Boolean = false,
        minTop: Int = 0,
        maxTop: Int = Int.MAX_VALUE
    ): AccessibilityNodeInfo? {
        if (candidates.isEmpty()) return null
        val nodes = snapshot()
        for (c in candidates) {
            if (c.isBlank()) continue
            val hit = nodes.firstOrNull { n ->
                val l = label(n)
                if (l.isEmpty()) {
                    false
                } else {
                    val matched = if (exact) l == c else l.contains(c, ignoreCase = true)
                    if (!matched) {
                        false
                    } else {
                        val r = boundsOf(n)
                        r.top >= minTop && r.top <= maxTop
                    }
                }
            }
            if (hit != null) return hit
        }
        return null
    }

    fun findByRegex(pattern: Regex, minTop: Int = 0, maxTop: Int = Int.MAX_VALUE): AccessibilityNodeInfo? {
        return snapshot().firstOrNull { n ->
            val l = label(n)
            if (l.isEmpty() || !pattern.containsMatchIn(l)) {
                false
            } else {
                val r = boundsOf(n)
                r.top >= minTop && r.top <= maxTop
            }
        }
    }

    /**
     * 用系统 API 在整棵树里按文本查找（大小写不敏感的包含匹配）。
     *
     * 为什么需要它：ANA Pocket 的挑战详情页是 WebView 弹层，节点数量极大，
     * 自建的 snapshot 递归会因上限被截断，导致底部按钮（「または 10pt に参加」）
     * 根本不在返回结果里。findAccessibilityNodeInfosByText 由系统遍历，
     * 不受我们自己的数量/深度限制。
     */
    fun findByText(text: String): List<AccessibilityNodeInfo> {
        val out = ArrayList<AccessibilityNodeInfo>()
        for (r in roots()) {
            try {
                r.findAccessibilityNodeInfosByText(text)?.let { out.addAll(it) }
            } catch (t: Throwable) {
                Logger.w("UI", "findByText('$text') failed: ${t.message}")
            }
        }
        return out
    }

    /**
     * 标准无障碍滚动。
     *
     * 真机实测：ANA Pocket 详情页 BottomSheet 对 dispatchGesture 合成滑动**不响应**
     * （树里 bounds 完全不变），但系统 input swipe 能滚。因此优先用
     * ACTION_SCROLL_FORWARD，让目标 View 自己处理滚动。
     */
    fun scrollForward(pkg: String? = null): Boolean {
        val nodes = if (pkg != null) activeNodes(pkg = pkg) else snapshot()
        val scrollables = nodes.filter { it.isScrollable && it.isVisibleToUser && it.childCount > 0 }
        if (scrollables.isEmpty()) {
            Logger.w("UI", "scrollForward: no scrollable node")
            return false
        }
        // 优先 RecyclerView（挑战列表主体），否则取可视面积最大的那个，
        // 避免滚到外层容器或小尺寸横向 chip 列表。
        val target = scrollables.firstOrNull {
            it.className?.toString()?.contains("RecyclerView") == true
        } ?: scrollables.maxByOrNull {
            val r = boundsOf(it)
            r.width().toLong() * r.height()
        } ?: return false
        val cls = target.className?.toString()?.substringAfterLast('.') ?: "?"
        val r = boundsOf(target)
        val ok = target.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
        Logger.i("UI", "scrollForward on $cls [${r.top}..${r.bottom}] -> $ok")
        return ok
    }

    /**
     * 选「真正要操作的那个窗口」的根节点。
     *
     * rootInActiveWindow 并不可靠：真机实测（2026-10-26）在 ANA 挑战页上
     * 它只返回了 80-183 个节点（状态栏/overlay），而目标 App 的窗口在
     * windows 里另有 456 个节点。传入 pkg 时优先取包名匹配的窗口。
     */
    private fun pickRoot(pkg: String?): AccessibilityNodeInfo? {
        if (pkg != null) {
            val hit = try {
                svc.windows.firstOrNull { w ->
                    val p = try { w.root?.packageName?.toString() } catch (t: Throwable) { null }
                    p == pkg
                }?.root
            } catch (t: Throwable) {
                null
            }
            if (hit != null) return hit
        }
        return root()
    }

    /**
     * 只读**前台窗口**的文本标签。
     *
     * 为什么需要：roots() 会把所有窗口都遍历进来，其中包含未销毁的后台窗口。
     * 真机实测（2026-10-26）：停在「所有ポイント」明细页时，roots() 仍能读到
     * 后台チャレンジ页的「参加可能」，导致 hasJoinableTab() 误判成「已在列表页」，
     * 于是扫描的是积分明细页 → discovered=0。
     * 页面类型判断必须只看前台窗口。
     */
    /**
     * 只读**前台窗口**的节点对象（含坐标，可用于点击）。
     *
     * 列表扫描必须用它：snapshot() 会把后台残留窗口（例如误触打开的
     * 「所有ポイント」明细）的节点也带进来，实测导致扫描到的是别的页面内容。
     */
    fun activeNodes(
        maxNodes: Int = 1200,
        maxDepth: Int = 40,
        pkg: String? = null
    ): List<AccessibilityNodeInfo> {
        val out = ArrayList<AccessibilityNodeInfo>(256)
        val r = pickRoot(pkg) ?: return out
        if (DEBUG_WINDOWS) {
            DEBUG_WINDOWS = false
            try {
                Logger.i("UI", "windows=${svc.windows.size} want=$pkg")
                for ((i, w) in svc.windows.withIndex()) {
                    val rr = try { w.root } catch (t: Throwable) { null }
                    val pn = try { rr?.packageName?.toString() } catch (t: Throwable) { null }
                    var cnt = 0
                    fun count(n: AccessibilityNodeInfo?, d: Int) {
                        if (n == null || d > 12) return
                        cnt++
                        var j = 0
                        while (j < n.childCount) { count(n.getChild(j), d + 1); j++ }
                    }
                    count(rr, 0)
                    Logger.i("UI", "  win[$i] pkg=$pn nodes~$cnt")
                }
            } catch (t: Throwable) {
                Logger.w("UI", "window dump failed: ${t.message}")
            }
        }
        fun walk(n: AccessibilityNodeInfo?, d: Int) {
            if (n == null || out.size >= maxNodes || d > maxDepth) return
            out.add(n)
            var i = 0
            while (i < n.childCount) {
                walk(n.getChild(i), d + 1)
                i++
            }
        }
        walk(r, 0)
        return out
    }

    fun activeLabels(maxDepth: Int = 40, pkg: String? = null): List<String> {
        val r = pickRoot(pkg) ?: return emptyList()
        val out = ArrayList<String>(128)
        fun walk(n: AccessibilityNodeInfo?, d: Int) {
            if (n == null || d > maxDepth) return
            val l = label(n)
            if (l.isNotEmpty()) out.add(l)
            var i = 0
            while (i < n.childCount) {
                walk(n.getChild(i), d + 1)
                i++
            }
        }
        walk(r, 0)
        return out
    }

    fun currentPackage(): String? = root()?.packageName?.toString()

    /**
     * 屏幕尺寸。
     *
     * 必须用 displayMetrics，**不能**用 rootInActiveWindow 的 bounds：
     * 真机实测（2026-10-26）rootInActiveWindow 有时返回状态栏等小窗口，
     * 高度只有几十像素，按它算出的滑动/点击坐标全错，
     * 会把页面误切到顶部别的 tab（チャレンジ → ボーナス）。
     */
    fun screenSize(): Pair<Int, Int> {
        val dm = svc.resources.displayMetrics
        return dm.widthPixels to dm.heightPixels
    }

    suspend fun waitFor(
        candidates: List<String>,
        timeoutMs: Long,
        intervalMs: Long = 300,
        minTop: Int = 0,
        maxTop: Int = Int.MAX_VALUE
    ): AccessibilityNodeInfo? {
        val end = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < end) {
            val n = find(candidates, false, minTop, maxTop)
            if (n != null) return n
            delay(intervalMs)
        }
        return null
    }

    suspend fun click(n: AccessibilityNodeInfo): Boolean {
        if(!AccountSafety.authorize(svc))return false
        var cur: AccessibilityNodeInfo? = n
        var guard = 0
        while (cur != null && guard < 10) {
            if (cur.isClickable && cur.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                return true
            }
            cur = cur.parent
            guard++
        }
        val r = boundsOf(n)
        if (r.width() <= 0 || r.height() <= 0) return false
        return tap(r.centerX(), r.centerY())
    }

    /**
     * 只做语义点击（performAction(ACTION_CLICK)），不做坐标兜底。
     *
     * 用途：详情页的「Npt に参加」被广告横幅盖住，坐标点击必然落到广告上。
     * 语义点击不经过屏幕坐标，可绕过遮挡。
     */
    fun clickSemantic(n: AccessibilityNodeInfo): Boolean {
        if(!AccountSafety.authorize(svc))return false
        var cur: AccessibilityNodeInfo? = n
        var guard = 0
        while (cur != null && guard < 8) {
            if (cur.isClickable) {
                val ok = cur.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                if (ok) {
                    Logger.i("UI", "semantic click ok on '${label(cur).take(20)}'")
                    return true
                }
            }
            cur = cur.parent
            guard++
        }
        return false
    }

    suspend fun tap(x: Int, y: Int): Boolean {
        if(!AccountSafety.authorize(svc))return false
        val path = Path().apply { moveTo(x.toFloat(), y.toFloat()) }
        val gd = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 60))
            .build()
        return withTimeoutOrNull(3000L) {
            suspendCancellableCoroutine<Boolean> { cont ->
                val ok = svc.dispatchGesture(
                    gd,
                    object : AccessibilityService.GestureResultCallback() {
                        override fun onCompleted(d: GestureDescription?) {
                            refreshCache()
                            if (cont.isActive) cont.resume(true)
                        }

                        override fun onCancelled(d: GestureDescription?) {
                            if (cont.isActive) cont.resume(false)
                        }
                    },
                    null
                )
                if (!ok && cont.isActive) cont.resume(false)
            }
        } ?: false
    }

    suspend fun swipe(x1: Int, y1: Int, x2: Int, y2: Int, durationMs: Long = 320): Boolean {
        if(!AccountSafety.authorize(svc))return false
        val path = Path().apply {
            moveTo(x1.toFloat(), y1.toFloat())
            lineTo(x2.toFloat(), y2.toFloat())
        }
        val gd = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, durationMs))
            .build()
        return withTimeoutOrNull(4000L) {
            suspendCancellableCoroutine<Boolean> { cont ->
                val ok = svc.dispatchGesture(
                    gd,
                    object : AccessibilityService.GestureResultCallback() {
                        override fun onCompleted(d: GestureDescription?) {
                            refreshCache()
                            if (cont.isActive) cont.resume(true)
                        }

                        override fun onCancelled(d: GestureDescription?) {
                            if (cont.isActive) cont.resume(false)
                        }
                    },
                    null
                )
                if (!ok && cont.isActive) cont.resume(false)
            }
        } ?: false
    }

    /**
     * 慢速多点滑动手势。
     *
     * 真机实测：320-480ms 的直线 swipe 在 ANA Pocket 的 BottomSheet 上
     * 完全不生效（树里 bounds 一点不变），而系统 `input swipe` 能滚。
     * 差异在于合成手势的采样点太少、太快，WebView 判定为 fling 后丢弃。
     * 这里把 duration 拉到 1.5s，并在路径上插 24 个中间点，
     * 尽量贴近真实手指拖动。
     */
    suspend fun swipeSlow(x1: Int, y1: Int, x2: Int, y2: Int, durationMs: Long = 1500): Boolean {
        if(!AccountSafety.authorize(svc))return false
        val steps = 24
        val path = Path().apply {
            moveTo(x1.toFloat(), y1.toFloat())
            for (i in 1..steps) {
                lineTo(
                    x1 + (x2 - x1) * i / steps.toFloat(),
                    y1 + (y2 - y1) * i / steps.toFloat()
                )
            }
        }
        val gd = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, durationMs))
            .build()
        val ok = withTimeoutOrNull(durationMs + 3000) {
            suspendCancellableCoroutine<Boolean> { cont ->
                val dispatched = svc.dispatchGesture(
                    gd,
                    object : AccessibilityService.GestureResultCallback() {
                        override fun onCompleted(d: GestureDescription?) {
                            refreshCache()
                            if (cont.isActive) cont.resume(true)
                        }

                        override fun onCancelled(d: GestureDescription?) {
                            if (cont.isActive) cont.resume(false)
                        }
                    },
                    null
                )
                if (!dispatched && cont.isActive) cont.resume(false)
            }
        } ?: false
        Logger.i("UI", "swipeSlow ($x1,$y1)->($x2,$y2) ${durationMs}ms -> $ok")
        return ok
    }

    suspend fun clickText(candidates: List<String>, timeoutMs: Long = 3000L): Boolean {
        val n = waitFor(candidates, timeoutMs) ?: return false
        return click(n)
    }

    fun back(): Boolean = AccountSafety.authorize(svc) && svc.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)

    fun home(): Boolean = svc.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)

    fun launchApp(pkg: String): Boolean {
        return try {
            val i = svc.packageManager.getLaunchIntentForPackage(pkg) ?: return false
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            svc.startActivity(i)
            true
        } catch (t: Throwable) {
            Logger.e("UI", "launchApp failed: ${t.message}", t)
            false
        }
    }

    /** 导出当前窗口可读节点，用于真机调试与证据留存 */
    fun dump(depth: Int = 30): String {
        val sb = StringBuilder()
        val rs = roots()
        if (rs.isEmpty()) return "(no root)"
        fun walk(n: AccessibilityNodeInfo, d: Int) {
            if (d > depth) return
            val l = label(n)
            if (l.isNotEmpty()) {
                val cls = n.className?.toString()?.substringAfterLast('.') ?: "?"
                val b = boundsOf(n)
                sb.append("  ".repeat(d))
                    .append(cls).append(" '").append(l).append("' ")
                    .append('[').append(b.left).append(',').append(b.top).append("][")
                    .append(b.right).append(',').append(b.bottom).append("]\n")
            }
            var i = 0
            while (i < n.childCount) {
                val c = n.getChild(i)
                if (c != null) walk(c, d + 1)
                i++
            }
        }
        for ((idx, r) in rs.withIndex()) {
            sb.append("--- window #").append(idx).append(" ---\n")
            walk(r, 0)
        }
        return sb.toString()
    }
}
